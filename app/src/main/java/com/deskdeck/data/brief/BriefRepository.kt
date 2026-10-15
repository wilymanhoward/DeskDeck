package com.deskdeck.data.brief

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.deskdeck.BuildConfig
import com.deskdeck.data.db.BriefCacheEntity
import com.deskdeck.data.db.BriefDao
import com.deskdeck.data.settings.NightSettings
import com.deskdeck.data.settings.TemperatureUnit
import com.deskdeck.domain.brief.Headline
import com.deskdeck.domain.brief.MarketBrief
import com.deskdeck.domain.brief.MorningBrief
import com.deskdeck.domain.brief.Watchlist
import com.deskdeck.domain.brief.WeatherBrief
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Publisher RSS feeds for market headlines (no key; each headline names its source). */
internal val HEADLINE_FEEDS = listOf(
    "CNBC" to "https://search.cnbc.com/rs/search/combinedcms/view.xml?partnerId=wrss01&id=20910258",
)

/**
 * The Morning Brief's data: weather (Open-Meteo), markets (Twelve Data) and headlines (RSS),
 * fetched together and cached in Room. Each part refreshes on its own: if one source fails, that
 * part keeps its last good copy, so the brief always shows something, with its age.
 *
 * Calls per refresh: weather 1–2 (geocoding only for a typed city), 2 per watchlist symbol (quote +
 * intraday), 1 per feed: a handful a day, far inside every free tier.
 */
@Singleton
class BriefRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: BriefDao,
    private val weatherApi: OpenMeteoApi,
    private val geocodingApi: OpenMeteoGeocodingApi,
    private val airQualityApi: OpenMeteoAirQualityApi,
    private val marketApi: TwelveDataApi,
    private val http: OkHttpClient,
) {
    private val adapter = Moshi.Builder().build().adapter(MorningBrief::class.java)

    val brief: Flow<MorningBrief?> = dao.observe(KEY).map { it?.let { e -> runCatching { adapter.fromJson(e.json) }.getOrNull() } }

    val hasMarketKey: Boolean get() = BuildConfig.TWELVE_DATA_API_KEY.isNotBlank()

    /** Fetches what [settings] asks for and merges it into the cache. @return true if anything new arrived. */
    suspend fun refresh(settings: NightSettings): Boolean = withContext(Dispatchers.IO) {
        val old = dao.get(KEY)?.let { runCatching { adapter.fromJson(it.json) }.getOrNull() } ?: MorningBrief()
        val weather = if (settings.briefWeather) attempt { fetchWeather(settings, settings.briefAirQuality) } else null
        val markets = if (settings.briefMarket && hasMarketKey) attempt { fetchMarkets(settings.watchlist) } else null
        val headlines = if (settings.briefHeadlines) attempt { fetchHeadlines() } else null
        if (weather == null && markets == null && headlines == null) return@withContext false
        val merged = MorningBrief(
            weather = weather ?: old.weather,
            markets = markets ?: old.markets,
            headlines = headlines ?: old.headlines,
            fetchedAtMs = System.currentTimeMillis(),
            weatherFetchedAtMs = if (weather != null) System.currentTimeMillis() else old.weatherFetchedAtMs,
        )
        dao.put(BriefCacheEntity(KEY, adapter.toJson(merged), merged.fetchedAtMs))
        true
    }

    /**
     * Just the weather and air quality (two small Open-Meteo requests, no market quota), for the
     * night screen. Leaves the brief's own "updated" time alone.
     */
    suspend fun refreshWeather(settings: NightSettings): Boolean = withContext(Dispatchers.IO) {
        val old = dao.get(KEY)?.let { runCatching { adapter.fromJson(it.json) }.getOrNull() } ?: MorningBrief()
        val weather = attempt { fetchWeather(settings, withAirQuality = true) } ?: return@withContext false
        val merged = old.copy(weather = weather, weatherFetchedAtMs = System.currentTimeMillis())
        dao.put(BriefCacheEntity(KEY, adapter.toJson(merged), merged.fetchedAtMs))
        true
    }

    private suspend fun fetchWeather(settings: NightSettings, withAirQuality: Boolean): WeatherBrief? {
        val (place, lat, lon) = location(settings) ?: return null
        val fahrenheit = settings.temperatureUnit == TemperatureUnit.FAHRENHEIT
        val forecast = weatherApi.forecast(lat, lon, if (fahrenheit) "fahrenheit" else "celsius")
        val now = forecast.current ?: return null
        val daily = forecast.daily
        return WeatherBrief(
            place = place,
            temperature = now.temperature ?: return null,
            high = daily?.max?.firstOrNull() ?: now.temperature,
            low = daily?.min?.firstOrNull() ?: now.temperature,
            code = now.weatherCode ?: 3,
            rainChancePercent = daily?.rainChance?.firstOrNull(),
            sunrise = daily?.sunrise?.firstOrNull()?.substringAfter('T')?.take(5),
            fahrenheit = fahrenheit,
            // Rounded to about 1 km: enough for a dot on a world map.
            latitude = Math.round(lat * 100) / 100.0,
            longitude = Math.round(lon * 100) / 100.0,
        ).let { weather ->
            if (!withAirQuality) return@let weather
            // Its own request: without it the weather still shows.
            val air = runCatching { airQualityApi.current(lat, lon).current }.getOrNull()
            weather.copy(airQuality = air?.usAqi?.toInt(), pm25 = air?.pm25)
        }
    }

    /** The device's coarse location (if allowed and chosen) or the typed city, geocoded. */
    @SuppressLint("MissingPermission")
    private suspend fun location(settings: NightSettings): Triple<String, Double, Double>? {
        if (settings.useDeviceLocation &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            val manager = context.getSystemService(LocationManager::class.java)
            val last: Location? = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .mapNotNull { runCatching { manager?.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
            if (last != null) {
                // Android's built-in geocoder names the area; without it the card says "Your area".
                @Suppress("DEPRECATION")
                val name = runCatching {
                    Geocoder(context).getFromLocation(last.latitude, last.longitude, 1)?.firstOrNull()?.locality
                }.getOrNull()
                return Triple(name ?: context.getString(com.deskdeck.R.string.brief_your_area), last.latitude, last.longitude)
            }
        }
        val city = settings.city.trim().ifEmpty { return null }
        val place = geocodingApi.search(city).results?.firstOrNull() ?: return null
        return Triple(place.name, place.latitude, place.longitude)
    }

    private suspend fun fetchMarkets(watchlist: List<String>): List<MarketBrief> = watchlist.mapNotNull { name ->
        val symbol = Watchlist.symbolFor(name)
        val key = BuildConfig.TWELVE_DATA_API_KEY
        val quote = marketApi.quote(symbol, key)
        if (quote.status == "error") return@mapNotNull null
        val price = quote.close?.toDoubleOrNull() ?: return@mapNotNull null
        if (Watchlist.isCrypto(symbol)) {
            // No sessions: the last 24 hours, 96 fifteen-minute bars (still one credit).
            val series = runCatching { marketApi.timeSeries(symbol, key, interval = "15min", size = 96) }.getOrNull()
            val percent = quote.rolling1dChange?.toDoubleOrNull() ?: quote.percentChange?.toDoubleOrNull() ?: 0.0
            MarketBrief(
                name = name,
                symbol = symbol,
                price = price,
                change = price - price / (1 + percent / 100),
                changePercent = percent,
                points = series?.values?.mapNotNull { it.close.toDoubleOrNull() }?.reversed().orEmpty(),
                currency = quote.currency,
                marketOpen = true,
                crypto = true,
            )
        } else {
            val series = runCatching { marketApi.timeSeries(symbol, key) }.getOrNull()
            MarketBrief(
                name = name,
                symbol = symbol,
                price = price,
                change = quote.change?.toDoubleOrNull() ?: 0.0,
                changePercent = quote.percentChange?.toDoubleOrNull() ?: 0.0,
                points = series?.values?.let(::lastSession).orEmpty(),
                currency = quote.currency,
                marketOpen = quote.marketOpen ?: false,
            )
        }
    }

    private fun fetchHeadlines(): List<Headline> = HEADLINE_FEEDS.flatMap { (source, url) ->
        val request = Request.Builder().url(url).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) emptyList() else RssParser.parse(response.body?.string().orEmpty(), source, limit = 5)
        }
    }.sortedByDescending { it.publishedAtMs ?: 0 }.take(3)

    private suspend fun <T> attempt(block: suspend () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: IOException) {
        null
    } catch (_: RuntimeException) {
        // Unexpected JSON etc.: keep the cached copy rather than failing the brief.
        null
    }

    companion object {
        private const val KEY = "latest"
    }
}

/** The most recent trading day's closes, oldest first (Twelve Data lists newest first). */
internal fun lastSession(bars: List<BarJson>): List<Double> {
    val day = bars.firstOrNull()?.datetime?.take(10) ?: return emptyList()
    return bars.filter { it.datetime.startsWith(day) }.mapNotNull { it.close.toDoubleOrNull() }.reversed()
}
