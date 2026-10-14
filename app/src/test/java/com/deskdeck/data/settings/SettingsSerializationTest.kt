package com.deskdeck.data.settings

import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSerializationTest {

    @Test
    fun `empty preferences give defaults`() {
        assertEquals(AppSettings(), readSettings(mutablePreferencesOf()))
    }

    @Test
    fun `everything round trips`() {
        val custom = AppSettings(
            clockFormat = ClockFormat.H24,
            clockShowSeconds = true,
            lyricsFontSizeSp = 36,
            showRomanization = true,
            lyricsLatencyCompensation = false,
            lyricsRouteAdjustMs = mapOf("8:Bedroom speaker" to 600, "speaker" to -100, "8:Odd|name" to 50),
            dynamicColors = false,
            accent = AccentColor.AMBER,
            keepScreenOn = KeepScreenOn.ALWAYS,
            sourceMode = SourceMode.PIN,
            mediaBrowserResume = true,
            spotifyLibrary = true,
            nowPlayingOnRight = true,
            night = NightSettings(
                enabled = true,
                weekdays = SleepWindow(23 * 60 + 30, 6 * 60 + 45),
                weekendDifferent = true,
                weekend = SleepWindow(30, 9 * 60),
                wakeDays = setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.FRIDAY),
                clockFormat = ClockFormat.H24,
                clockStyle = NightClockStyle.ANALOG,
                redTint = RedTint.ON,
                brightness = 0.1f,
                brief = false,
                briefDuration = BriefDuration.UNTIL_DISMISSED,
                briefWeather = false,
                briefMarket = true,
                briefHeadlines = false,
                useDeviceLocation = true,
                city = "Kuala Lumpur",
                temperatureUnit = TemperatureUnit.FAHRENHEIT,
                watchlist = listOf("FTSE Bursa Malaysia KLCI", "S&P 500", "AAPL"),
            ),
            burnIn = BurnInSettings(
                pixelShift = false,
                pixelShiftDp = 9,
                idleDim = false,
                dimAfterMin = 10,
                dimLevel = 0.7f,
                ambient = false,
                ambientAfterMin = 1,
                pausedClock = false,
                pausedClockAfterMin = 15,
                screenOffWhenPaused = true,
                brightnessCap = 0.6f,
                railOpacityDrift = false,
            ),
            notifications = NotificationSettings(
                banners = false,
                mutedPackages = setOf("com.whatsapp", "com.google.android.gm"),
                hideContent = true,
                respectDnd = false,
                durationSec = 8,
            ),
        )
        val prefs = mutablePreferencesOf()
        writeSettings(prefs, custom)
        assertEquals(custom, readSettings(prefs))
    }

    @Test
    fun `out of range and unknown values fall back safely`() {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("accent") to "NEON_PINK",
            intPreferencesKey("lyrics_font_size") to 500,
            floatPreferencesKey("burnin_brightness_cap") to 0.01f,
            intPreferencesKey("burnin_dim_after_min") to -3,
            intPreferencesKey("notif_duration_sec") to 60,
        )
        val s = readSettings(prefs)
        assertEquals(AccentColor.BLUE, s.accent)
        assertEquals(SettingsRanges.lyricsFontSizeSp.last, s.lyricsFontSizeSp)
        assertEquals(SettingsRanges.brightnessCap.start, s.burnIn.brightnessCap)
        assertEquals(1, s.burnIn.dimAfterMin)
        assertEquals(SettingsRanges.bannerDurationSec.last, s.notifications.durationSec)
    }
}
