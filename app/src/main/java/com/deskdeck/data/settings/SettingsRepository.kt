package com.deskdeck.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** [AppSettings] in the same Preferences DataStore as the rail pins (different keys). */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = dataStore.data.map(::readSettings).distinctUntilChanged()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs -> writeSettings(prefs, transform(readSettings(prefs))) }
    }
}

private object Keys {
    val clockFormat = stringPreferencesKey("clock_format")
    val clockSeconds = booleanPreferencesKey("clock_seconds")
    val lyricsFontSize = intPreferencesKey("lyrics_font_size")
    val romanization = booleanPreferencesKey("lyrics_romanization")
    val dynamicColors = booleanPreferencesKey("dynamic_colors")
    val accent = stringPreferencesKey("accent")
    val keepScreenOn = stringPreferencesKey("keep_screen_on")
    val pixelShift = booleanPreferencesKey("burnin_pixel_shift")
    val pixelShiftDp = intPreferencesKey("burnin_pixel_shift_dp")
    val idleDim = booleanPreferencesKey("burnin_idle_dim")
    val dimAfter = intPreferencesKey("burnin_dim_after_min")
    val dimLevel = floatPreferencesKey("burnin_dim_level")
    val ambient = booleanPreferencesKey("burnin_ambient")
    val ambientAfter = intPreferencesKey("burnin_ambient_after_min")
    val pausedClock = booleanPreferencesKey("burnin_paused_clock")
    val pausedClockAfter = intPreferencesKey("burnin_paused_clock_after_min")
    val screenOffWhenPaused = booleanPreferencesKey("burnin_screen_off_when_paused")
    val brightnessCap = floatPreferencesKey("burnin_brightness_cap")
    val railOpacityDrift = booleanPreferencesKey("burnin_rail_opacity_drift")
}

private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
    name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default

/** Missing or out-of-range values fall back to defaults, so old or corrupt data can't break the UI. */
internal fun readSettings(prefs: Preferences): AppSettings {
    val d = AppSettings()
    val b = d.burnIn
    return AppSettings(
        clockFormat = enumOr(prefs[Keys.clockFormat], d.clockFormat),
        clockShowSeconds = prefs[Keys.clockSeconds] ?: d.clockShowSeconds,
        lyricsFontSizeSp = (prefs[Keys.lyricsFontSize] ?: d.lyricsFontSizeSp).coerceIn(SettingsRanges.lyricsFontSizeSp),
        showRomanization = prefs[Keys.romanization] ?: d.showRomanization,
        dynamicColors = prefs[Keys.dynamicColors] ?: d.dynamicColors,
        accent = enumOr(prefs[Keys.accent], d.accent),
        keepScreenOn = enumOr(prefs[Keys.keepScreenOn], d.keepScreenOn),
        burnIn = BurnInSettings(
            pixelShift = prefs[Keys.pixelShift] ?: b.pixelShift,
            pixelShiftDp = (prefs[Keys.pixelShiftDp] ?: b.pixelShiftDp).coerceIn(SettingsRanges.pixelShiftDp),
            idleDim = prefs[Keys.idleDim] ?: b.idleDim,
            dimAfterMin = (prefs[Keys.dimAfter] ?: b.dimAfterMin).coerceAtLeast(1),
            dimLevel = (prefs[Keys.dimLevel] ?: b.dimLevel).coerceIn(SettingsRanges.dimLevel),
            ambient = prefs[Keys.ambient] ?: b.ambient,
            ambientAfterMin = (prefs[Keys.ambientAfter] ?: b.ambientAfterMin).coerceAtLeast(1),
            pausedClock = prefs[Keys.pausedClock] ?: b.pausedClock,
            pausedClockAfterMin = (prefs[Keys.pausedClockAfter] ?: b.pausedClockAfterMin).coerceAtLeast(1),
            screenOffWhenPaused = prefs[Keys.screenOffWhenPaused] ?: b.screenOffWhenPaused,
            brightnessCap = (prefs[Keys.brightnessCap] ?: b.brightnessCap).coerceIn(SettingsRanges.brightnessCap),
            railOpacityDrift = prefs[Keys.railOpacityDrift] ?: b.railOpacityDrift,
        ),
    )
}

internal fun writeSettings(prefs: MutablePreferences, s: AppSettings) {
    prefs[Keys.clockFormat] = s.clockFormat.name
    prefs[Keys.clockSeconds] = s.clockShowSeconds
    prefs[Keys.lyricsFontSize] = s.lyricsFontSizeSp
    prefs[Keys.romanization] = s.showRomanization
    prefs[Keys.dynamicColors] = s.dynamicColors
    prefs[Keys.accent] = s.accent.name
    prefs[Keys.keepScreenOn] = s.keepScreenOn.name
    val b = s.burnIn
    prefs[Keys.pixelShift] = b.pixelShift
    prefs[Keys.pixelShiftDp] = b.pixelShiftDp
    prefs[Keys.idleDim] = b.idleDim
    prefs[Keys.dimAfter] = b.dimAfterMin
    prefs[Keys.dimLevel] = b.dimLevel
    prefs[Keys.ambient] = b.ambient
    prefs[Keys.ambientAfter] = b.ambientAfterMin
    prefs[Keys.pausedClock] = b.pausedClock
    prefs[Keys.pausedClockAfter] = b.pausedClockAfterMin
    prefs[Keys.screenOffWhenPaused] = b.screenOffWhenPaused
    prefs[Keys.brightnessCap] = b.brightnessCap
    prefs[Keys.railOpacityDrift] = b.railOpacityDrift
}
