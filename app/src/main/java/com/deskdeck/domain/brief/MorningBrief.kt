package com.deskdeck.domain.brief

import com.squareup.moshi.JsonClass

/** What the Morning Brief shows, as last fetched (cached in Room so it also works offline). */
@JsonClass(generateAdapter = true)
data class MorningBrief(
    val weather: WeatherBrief? = null,
    val markets: List<MarketBrief> = emptyList(),
    val headlines: List<Headline> = emptyList(),
    /** Epoch ms of the last successful fetch of any part. */
    val fetchedAtMs: Long = 0,
)

@JsonClass(generateAdapter = true)
data class WeatherBrief(
    val place: String,
    val temperature: Double,
    val high: Double,
    val low: Double,
    /** WMO weather code (Open-Meteo); see [WeatherCondition.fromWmo]. */
    val code: Int,
    val rainChancePercent: Int?,
    /** Local time "HH:mm", or null. */
    val sunrise: String?,
    val fahrenheit: Boolean,
)

@JsonClass(generateAdapter = true)
data class MarketBrief(
    /** As the user named it in the watchlist ("S&P 500"). */
    val name: String,
    /** What was actually fetched ("SPY"); differs when an ETF stands in for an index. */
    val symbol: String,
    val price: Double,
    val change: Double,
    val changePercent: Double,
    /** Intraday closes, oldest first, for the small chart. */
    val points: List<Double>,
    val currency: String?,
    val marketOpen: Boolean,
)

@JsonClass(generateAdapter = true)
data class Headline(
    val title: String,
    val source: String,
    val url: String,
    val publishedAtMs: Long?,
)

/** Plain weather groups for an icon and a word, from the WMO codes Open-Meteo uses. */
enum class WeatherCondition {
    CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, SHOWERS, THUNDERSTORM;

    companion object {
        fun fromWmo(code: Int): WeatherCondition = when (code) {
            0, 1 -> CLEAR
            2 -> PARTLY_CLOUDY
            3 -> CLOUDY
            45, 48 -> FOG
            in 51..57 -> DRIZZLE
            in 61..67 -> RAIN
            in 71..77, 85, 86 -> SNOW
            in 80..82 -> SHOWERS
            in 95..99 -> THUNDERSTORM
            else -> CLOUDY
        }
    }
}

/** Watchlist names that need a different symbol on the free data source. */
object Watchlist {
    /** Index → the ETF that tracks it (indices themselves aren't on Twelve Data's free plan). */
    private val PROXIES = mapOf(
        "S&P 500" to "SPY",
        "SPX" to "SPY",
        "NASDAQ 100" to "QQQ",
        "NASDAQ-100" to "QQQ",
        "DOW JONES" to "DIA",
        "DOW" to "DIA",
    )

    fun symbolFor(name: String): String = PROXIES[name.trim().uppercase()] ?: name.trim().uppercase().replace(' ', '.')
}
