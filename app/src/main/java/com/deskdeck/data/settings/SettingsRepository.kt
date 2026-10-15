package com.deskdeck.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.time.DayOfWeek
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

    suspend fun updateNight(transform: (NightSettings) -> NightSettings) = update { it.copy(night = transform(it.night)) }

    /** "Start/End Night Mode now", kept across restarts until it expires. */
    val nightOverride: Flow<NightOverride?> = dataStore.data.map { prefs ->
        val until = prefs[Keys.nightOverrideUntil] ?: return@map null
        NightOverride(on = prefs[Keys.nightOverrideOn] ?: false, untilEpochMs = until)
    }.distinctUntilChanged()

    /** The wake-up whose Morning Brief was closed with Done (epoch ms), so it stays closed after a restart. */
    val briefDismissedWakeMs: Flow<Long> = dataStore.data.map { it[Keys.briefDismissedWake] ?: 0L }.distinctUntilChanged()

    suspend fun setBriefDismissedWake(epochMs: Long) {
        dataStore.edit { it[Keys.briefDismissedWake] = epochMs }
    }

    suspend fun setNightOverride(override: NightOverride?) {
        dataStore.edit { prefs ->
            if (override == null) {
                prefs.remove(Keys.nightOverrideOn)
                prefs.remove(Keys.nightOverrideUntil)
            } else {
                prefs[Keys.nightOverrideOn] = override.on
                prefs[Keys.nightOverrideUntil] = override.untilEpochMs
            }
        }
    }
}

private object Keys {
    val nightEnabled = booleanPreferencesKey("night_enabled")
    val nightWeekdayBed = intPreferencesKey("night_weekday_bedtime_min")
    val nightWeekdayWake = intPreferencesKey("night_weekday_wake_min")
    val nightWeekendDifferent = booleanPreferencesKey("night_weekend_different")
    val nightWeekendBed = intPreferencesKey("night_weekend_bedtime_min")
    val nightWeekendWake = intPreferencesKey("night_weekend_wake_min")
    val nightWakeDays = stringSetPreferencesKey("night_wake_days")
    val nightClockFormat = stringPreferencesKey("night_clock_format")
    val nightClockStyle = stringPreferencesKey("night_clock_style")
    val nightClockColor = stringPreferencesKey("night_clock_color")
    val nightMusicStyle = stringPreferencesKey("night_music_style")
    val briefAirQuality = booleanPreferencesKey("brief_air_quality")
    val nightWeather = booleanPreferencesKey("night_weather")
    val nightRedTint = stringPreferencesKey("night_red_tint")
    val nightBrightness = floatPreferencesKey("night_brightness")
    val brief = booleanPreferencesKey("brief_enabled")
    val briefDuration = stringPreferencesKey("brief_duration")
    val briefWeather = booleanPreferencesKey("brief_weather")
    val briefMarket = booleanPreferencesKey("brief_market")
    val briefHeadlines = booleanPreferencesKey("brief_headlines")
    val weatherDeviceLocation = booleanPreferencesKey("weather_device_location")
    val weatherCity = stringPreferencesKey("weather_city")
    val temperatureUnit = stringPreferencesKey("temperature_unit")
    /** Newline-separated, so the order is kept (a string set would lose it). */
    val watchlist = stringPreferencesKey("market_watchlist")
    val nightOverrideOn = booleanPreferencesKey("night_override_on")
    val nightOverrideUntil = longPreferencesKey("night_override_until")
    val briefDismissedWake = longPreferencesKey("brief_dismissed_wake")
    val clockFormat = stringPreferencesKey("clock_format")
    val clockSeconds = booleanPreferencesKey("clock_seconds")
    val lyricsFontSize = intPreferencesKey("lyrics_font_size")
    val romanization = booleanPreferencesKey("lyrics_romanization")
    val latencyCompensation = booleanPreferencesKey("lyrics_latency_compensation")
    val routeAdjust = stringSetPreferencesKey("lyrics_route_adjust_ms")
    val dynamicColors = booleanPreferencesKey("dynamic_colors")
    val accent = stringPreferencesKey("accent")
    val keepScreenOn = stringPreferencesKey("keep_screen_on")
    val sourceMode = stringPreferencesKey("source_mode")
    val mediaBrowserResume = booleanPreferencesKey("source_media_browser")
    val spotifyLibrary = booleanPreferencesKey("spotify_library")
    val nowPlayingOnRight = booleanPreferencesKey("layout_now_playing_right")
    val smoothMotion = booleanPreferencesKey("smooth_motion")
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
    val banners = booleanPreferencesKey("notif_banners")
    val bannerMuted = stringSetPreferencesKey("notif_muted_packages")
    val bannerHideContent = booleanPreferencesKey("notif_hide_content")
    val bannerRespectDnd = booleanPreferencesKey("notif_respect_dnd")
    val bannerDuration = intPreferencesKey("notif_duration_sec")
    val callsEnabled = booleanPreferencesKey("calls_enabled")
    val callBanner = booleanPreferencesKey("calls_banner")
    val callLayout = booleanPreferencesKey("calls_layout")
    val callPauseMusic = booleanPreferencesKey("calls_pause_music")
    val callResumeMusic = booleanPreferencesKey("calls_resume_music")
}

private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
    name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default

/** Missing or out-of-range values fall back to defaults, so old or corrupt data can't break the UI. */
internal fun readSettings(prefs: Preferences): AppSettings {
    val d = AppSettings()
    val b = d.burnIn
    val n = d.notifications
    return AppSettings(
        clockFormat = enumOr(prefs[Keys.clockFormat], d.clockFormat),
        clockShowSeconds = prefs[Keys.clockSeconds] ?: d.clockShowSeconds,
        lyricsFontSizeSp = (prefs[Keys.lyricsFontSize] ?: d.lyricsFontSizeSp).coerceIn(SettingsRanges.lyricsFontSizeSp),
        showRomanization = prefs[Keys.romanization] ?: d.showRomanization,
        lyricsLatencyCompensation = prefs[Keys.latencyCompensation] ?: d.lyricsLatencyCompensation,
        lyricsRouteAdjustMs = prefs[Keys.routeAdjust]?.let(::decodeRouteAdjust) ?: d.lyricsRouteAdjustMs,
        night = readNight(prefs),
        dynamicColors = prefs[Keys.dynamicColors] ?: d.dynamicColors,
        accent = enumOr(prefs[Keys.accent], d.accent),
        keepScreenOn = enumOr(prefs[Keys.keepScreenOn], d.keepScreenOn),
        sourceMode = enumOr(prefs[Keys.sourceMode], d.sourceMode),
        mediaBrowserResume = prefs[Keys.mediaBrowserResume] ?: d.mediaBrowserResume,
        spotifyLibrary = prefs[Keys.spotifyLibrary] ?: d.spotifyLibrary,
        nowPlayingOnRight = prefs[Keys.nowPlayingOnRight] ?: d.nowPlayingOnRight,
        smoothMotion = prefs[Keys.smoothMotion] ?: d.smoothMotion,
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
        notifications = NotificationSettings(
            banners = prefs[Keys.banners] ?: n.banners,
            mutedPackages = prefs[Keys.bannerMuted] ?: n.mutedPackages,
            hideContent = prefs[Keys.bannerHideContent] ?: n.hideContent,
            respectDnd = prefs[Keys.bannerRespectDnd] ?: n.respectDnd,
            durationSec = (prefs[Keys.bannerDuration] ?: n.durationSec).coerceIn(SettingsRanges.bannerDurationSec),
        ),
        calls = CallSettings().let { c ->
            CallSettings(
                enabled = prefs[Keys.callsEnabled] ?: c.enabled,
                incomingBanner = prefs[Keys.callBanner] ?: c.incomingBanner,
                callLayout = prefs[Keys.callLayout] ?: c.callLayout,
                pauseMusic = prefs[Keys.callPauseMusic] ?: c.pauseMusic,
                resumeMusic = prefs[Keys.callResumeMusic] ?: c.resumeMusic,
            )
        },
    )
}

internal fun writeSettings(prefs: MutablePreferences, s: AppSettings) {
    prefs[Keys.clockFormat] = s.clockFormat.name
    prefs[Keys.clockSeconds] = s.clockShowSeconds
    prefs[Keys.lyricsFontSize] = s.lyricsFontSizeSp
    prefs[Keys.romanization] = s.showRomanization
    prefs[Keys.latencyCompensation] = s.lyricsLatencyCompensation
    prefs[Keys.routeAdjust] = s.lyricsRouteAdjustMs.map { (route, ms) -> "$ms|$route" }.toSet()
    writeNight(prefs, s.night)
    prefs[Keys.dynamicColors] = s.dynamicColors
    prefs[Keys.accent] = s.accent.name
    prefs[Keys.keepScreenOn] = s.keepScreenOn.name
    prefs[Keys.sourceMode] = s.sourceMode.name
    prefs[Keys.mediaBrowserResume] = s.mediaBrowserResume
    prefs[Keys.spotifyLibrary] = s.spotifyLibrary
    prefs[Keys.nowPlayingOnRight] = s.nowPlayingOnRight
    prefs[Keys.smoothMotion] = s.smoothMotion
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
    val n = s.notifications
    prefs[Keys.banners] = n.banners
    prefs[Keys.bannerMuted] = n.mutedPackages
    prefs[Keys.bannerHideContent] = n.hideContent
    prefs[Keys.bannerRespectDnd] = n.respectDnd
    prefs[Keys.bannerDuration] = n.durationSec
    val c = s.calls
    prefs[Keys.callsEnabled] = c.enabled
    prefs[Keys.callBanner] = c.incomingBanner
    prefs[Keys.callLayout] = c.callLayout
    prefs[Keys.callPauseMusic] = c.pauseMusic
    prefs[Keys.callResumeMusic] = c.resumeMusic
}

/** "ms|route" entries; the route id may itself contain '|', so split at the first one only. */
private fun decodeRouteAdjust(entries: Set<String>): Map<String, Int> = entries.mapNotNull { entry ->
    val ms = entry.substringBefore('|').toIntOrNull() ?: return@mapNotNull null
    val route = entry.substringAfter('|', missingDelimiterValue = "").ifEmpty { return@mapNotNull null }
    route to ms.coerceIn(SettingsRanges.lyricsSyncAdjustMs)
}.toMap()

private const val MINUTES_PER_DAY = 24 * 60

private fun readNight(prefs: Preferences): NightSettings {
    val d = NightSettings()
    fun minutes(key: Preferences.Key<Int>, default: Int) = (prefs[key] ?: default).let { if (it in 0 until MINUTES_PER_DAY) it else default }
    return NightSettings(
        enabled = prefs[Keys.nightEnabled] ?: d.enabled,
        weekdays = SleepWindow(minutes(Keys.nightWeekdayBed, d.weekdays.bedtimeMin), minutes(Keys.nightWeekdayWake, d.weekdays.wakeMin)),
        weekendDifferent = prefs[Keys.nightWeekendDifferent] ?: d.weekendDifferent,
        weekend = SleepWindow(minutes(Keys.nightWeekendBed, d.weekend.bedtimeMin), minutes(Keys.nightWeekendWake, d.weekend.wakeMin)),
        wakeDays = prefs[Keys.nightWakeDays]?.mapNotNull { n -> DayOfWeek.entries.firstOrNull { it.name == n } }?.toSet() ?: d.wakeDays,
        clockFormat = enumOr(prefs[Keys.nightClockFormat], d.clockFormat),
        clockStyle = enumOr(prefs[Keys.nightClockStyle], d.clockStyle),
        clockColor = enumOr(prefs[Keys.nightClockColor], d.clockColor),
        musicStyle = enumOr(prefs[Keys.nightMusicStyle], d.musicStyle),
        briefAirQuality = prefs[Keys.briefAirQuality] ?: d.briefAirQuality,
        nightWeather = prefs[Keys.nightWeather] ?: d.nightWeather,
        redTint = enumOr(prefs[Keys.nightRedTint], d.redTint),
        brightness = (prefs[Keys.nightBrightness] ?: d.brightness).coerceIn(SettingsRanges.nightBrightness),
        brief = prefs[Keys.brief] ?: d.brief,
        briefDuration = enumOr(prefs[Keys.briefDuration], d.briefDuration),
        briefWeather = prefs[Keys.briefWeather] ?: d.briefWeather,
        briefMarket = prefs[Keys.briefMarket] ?: d.briefMarket,
        briefHeadlines = prefs[Keys.briefHeadlines] ?: d.briefHeadlines,
        useDeviceLocation = prefs[Keys.weatherDeviceLocation] ?: d.useDeviceLocation,
        city = prefs[Keys.weatherCity] ?: d.city,
        temperatureUnit = enumOr(prefs[Keys.temperatureUnit], d.temperatureUnit),
        watchlist = prefs[Keys.watchlist]?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: d.watchlist,
    )
}

private fun writeNight(prefs: MutablePreferences, n: NightSettings) {
    prefs[Keys.nightEnabled] = n.enabled
    prefs[Keys.nightWeekdayBed] = n.weekdays.bedtimeMin
    prefs[Keys.nightWeekdayWake] = n.weekdays.wakeMin
    prefs[Keys.nightWeekendDifferent] = n.weekendDifferent
    prefs[Keys.nightWeekendBed] = n.weekend.bedtimeMin
    prefs[Keys.nightWeekendWake] = n.weekend.wakeMin
    prefs[Keys.nightWakeDays] = n.wakeDays.map { it.name }.toSet()
    prefs[Keys.nightClockFormat] = n.clockFormat.name
    prefs[Keys.nightClockStyle] = n.clockStyle.name
    prefs[Keys.nightClockColor] = n.clockColor.name
    prefs[Keys.nightMusicStyle] = n.musicStyle.name
    prefs[Keys.briefAirQuality] = n.briefAirQuality
    prefs[Keys.nightWeather] = n.nightWeather
    prefs[Keys.nightRedTint] = n.redTint.name
    prefs[Keys.nightBrightness] = n.brightness
    prefs[Keys.brief] = n.brief
    prefs[Keys.briefDuration] = n.briefDuration.name
    prefs[Keys.briefWeather] = n.briefWeather
    prefs[Keys.briefMarket] = n.briefMarket
    prefs[Keys.briefHeadlines] = n.briefHeadlines
    prefs[Keys.weatherDeviceLocation] = n.useDeviceLocation
    prefs[Keys.weatherCity] = n.city
    prefs[Keys.temperatureUnit] = n.temperatureUnit.name
    prefs[Keys.watchlist] = n.watchlist.joinToString("\n")
}
