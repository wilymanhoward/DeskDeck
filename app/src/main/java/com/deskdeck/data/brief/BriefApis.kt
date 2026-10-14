package com.deskdeck.data.brief

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

/** Open-Meteo forecast (https://open-meteo.com): free, no key, CC BY 4.0 (credited on the card). */
interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("temperature_unit") unit: String,
        @Query("current") current: String = "temperature_2m,weather_code",
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") days: Int = 1,
    ): ForecastJson
}

/** Open-Meteo geocoding, for a city typed in Settings. */
interface OpenMeteoGeocodingApi {
    @GET("v1/search")
    suspend fun search(@Query("name") name: String, @Query("count") count: Int = 1): GeocodingJson
}

/** Twelve Data (https://twelvedata.com), free key: 800 credits/day, 8/min; 1 credit per call here. */
interface TwelveDataApi {
    @GET("quote")
    suspend fun quote(@Query("symbol") symbol: String, @Query("apikey") key: String): QuoteJson

    @GET("time_series")
    suspend fun timeSeries(
        @Query("symbol") symbol: String,
        @Query("apikey") key: String,
        @Query("interval") interval: String = "5min",
        // One US session is 78 five-minute bars.
        @Query("outputsize") size: Int = 78,
    ): TimeSeriesJson
}

@JsonClass(generateAdapter = true)
data class ForecastJson(val current: CurrentJson?, val daily: DailyJson?)

@JsonClass(generateAdapter = true)
data class CurrentJson(
    @Json(name = "temperature_2m") val temperature: Double?,
    @Json(name = "weather_code") val weatherCode: Int?,
)

@JsonClass(generateAdapter = true)
data class DailyJson(
    @Json(name = "temperature_2m_max") val max: List<Double?>?,
    @Json(name = "temperature_2m_min") val min: List<Double?>?,
    @Json(name = "precipitation_probability_max") val rainChance: List<Int?>?,
    val sunrise: List<String?>?,
)

@JsonClass(generateAdapter = true)
data class GeocodingJson(val results: List<PlaceJson>?)

@JsonClass(generateAdapter = true)
data class PlaceJson(val name: String, val latitude: Double, val longitude: Double, val country: String?)

/** Twelve Data sends numbers as strings, and errors as `{"status":"error","code":…,"message":…}` with HTTP 200. */
@JsonClass(generateAdapter = true)
data class QuoteJson(
    val symbol: String?,
    val name: String?,
    val currency: String?,
    val close: String?,
    val change: String?,
    @Json(name = "percent_change") val percentChange: String?,
    @Json(name = "is_market_open") val marketOpen: Boolean?,
    /** Percent change over the last 24 hours; only meaningful for crypto. */
    @Json(name = "rolling_1d_change") val rolling1dChange: String? = null,
    val status: String?,
    val code: Int?,
    val message: String?,
)

@JsonClass(generateAdapter = true)
data class TimeSeriesJson(val values: List<BarJson>?, val status: String?, val code: Int?, val message: String?)

@JsonClass(generateAdapter = true)
data class BarJson(val datetime: String, val close: String)
