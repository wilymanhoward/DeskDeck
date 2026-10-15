package com.deskdeck.domain.brief

/**
 * The US EPA Air Quality Index bands (0–500). Malaysia's Air Pollutant Index uses the same
 * breakpoints for its categories, so the words read the same there.
 */
enum class AirQualityLevel(val max: Int) {
    GOOD(50),
    MODERATE(100),
    SENSITIVE(150),
    UNHEALTHY(200),
    VERY_UNHEALTHY(300),
    HAZARDOUS(Int.MAX_VALUE);

    companion object {
        fun of(aqi: Int): AirQualityLevel = entries.first { aqi <= it.max }
    }
}
