package com.deskdeck.data.settings

import java.time.DayOfWeek

/** Night Mode clock faces, StandBy style. */
enum class NightClockStyle { BOLD, THIN, ANALOG, LAYERED, SERIF, CALENDAR, WORLD }

/** The face color (Apple's dark-mode system colors). Red tint at night overrides it. */
enum class NightClockColor(val argb: Long) {
    WHITE(0xFFBDBDBD),
    RED(0xFFFF453A),
    ORANGE(0xFFFF9F0A),
    YELLOW(0xFFFFD60A),
    GREEN(0xFF32D74B),
    TEAL(0xFF64D2FF),
    BLUE(0xFF0A84FF),
    PURPLE(0xFFBF5AF2),
    PINK(0xFFFF375F),
}

/** The music on the night screen: beside the clock, a slim bar along the bottom, or not at all. */
enum class NightMusicStyle { PANEL, BAR, HIDDEN }

/** Deep red night screen: always, never, or when the room is dark (light sensor; else the schedule). */
enum class RedTint { OFF, ON, AUTO }

/** How long the Morning Brief stays; null minutes = until dismissed. */
enum class BriefDuration(val minutes: Int?) { MIN_15(15), MIN_30(30), MIN_60(60), UNTIL_DISMISSED(null) }

enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

/** One sleep window, as minutes after midnight. Bedtime may be later than wake-up (crosses midnight). */
data class SleepWindow(val bedtimeMin: Int, val wakeMin: Int)

data class NightSettings(
    val enabled: Boolean = false,
    val weekdays: SleepWindow = SleepWindow(bedtimeMin = 23 * 60, wakeMin = 7 * 60),
    /** Separate window for Saturday and Sunday mornings. */
    val weekendDifferent: Boolean = false,
    val weekend: SleepWindow = SleepWindow(bedtimeMin = 0, wakeMin = 9 * 60),
    /** The mornings Night Mode ends on: a night belongs to the day you wake up. */
    val wakeDays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val clockFormat: ClockFormat = ClockFormat.SYSTEM,
    val clockStyle: NightClockStyle = NightClockStyle.BOLD,
    val clockColor: NightClockColor = NightClockColor.WHITE,
    val musicStyle: NightMusicStyle = NightMusicStyle.PANEL,
    /** The temperature and air quality on the clock face. */
    val nightWeather: Boolean = true,
    val redTint: RedTint = RedTint.AUTO,
    /** Window brightness at night, 0.01..0.5. */
    val brightness: Float = 0.05f,
    val brief: Boolean = true,
    val briefDuration: BriefDuration = BriefDuration.MIN_30,
    val briefWeather: Boolean = true,
    /** The air quality index on the weather card. */
    val briefAirQuality: Boolean = true,
    val briefMarket: Boolean = true,
    val briefHeadlines: Boolean = true,
    /** Weather for the device's coarse location; otherwise [city]. */
    val useDeviceLocation: Boolean = false,
    val city: String = "",
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    /** Display names of watched indices/symbols, in order; mapped to a data source's symbols in N1d. */
    val watchlist: List<String> = DEFAULT_WATCHLIST,
) {
    fun windowFor(wakeDay: DayOfWeek): SleepWindow =
        if (weekendDifferent && (wakeDay == DayOfWeek.SATURDAY || wakeDay == DayOfWeek.SUNDAY)) weekend else weekdays

    companion object {
        // The KLCI has no free official data source (N1c), so only the S&P 500 by default.
        val DEFAULT_WATCHLIST = listOf("S&P 500")
    }
}

/** "Start Night Mode now" / "End now": holds Night Mode on or off until [untilEpochMs]. */
data class NightOverride(val on: Boolean, val untilEpochMs: Long)
