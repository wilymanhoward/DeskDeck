package com.deskdeck.ui.night

import androidx.lifecycle.ViewModel
import com.deskdeck.domain.NowPlaying
import com.deskdeck.domain.night.NightState
import com.deskdeck.media.MediaRepository
import com.deskdeck.media.NightModeController
import com.deskdeck.media.NotificationCounter
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import com.deskdeck.data.brief.BriefRepository
import com.deskdeck.domain.brief.WeatherBrief
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.deskdeck.data.settings.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** The night screen's music, notification count and Night Mode state, on the same plumbing as the dashboard. */
@HiltViewModel
class NightViewModel @Inject constructor(
    private val media: MediaRepository,
    private val night: NightModeController,
    notifications: NotificationCounter,
    private val brief: BriefRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    /** The World face marks the Morning Brief's weather place. */
    val weather: StateFlow<WeatherBrief?> = brief.brief.map { it?.weather }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val state: StateFlow<NightState> = night.state
    val nowPlaying: StateFlow<NowPlaying?> = media.nowPlaying
    val notificationCount: StateFlow<Int> = notifications.count

    fun playPause() = media.playPause()
    fun skipNext() = media.skipNext()
    fun skipPrevious() = media.skipPrevious()
    fun exitNight() = night.endNow()

    /**
     * While the night screen is up (call from its composition): keeps the weather and air quality
     * under [WEATHER_MAX_AGE_MS] old, checking every [CHECK_EVERY_MS]. Two small requests an hour.
     */
    suspend fun keepWeatherFresh() {
        while (true) {
            val nightSettings = settings.settings.first().night
            val age = System.currentTimeMillis() - (brief.brief.first()?.weatherFetchedAtMs ?: 0L)
            if (nightSettings.nightWeather && age > WEATHER_MAX_AGE_MS) brief.refreshWeather(nightSettings)
            delay(CHECK_EVERY_MS)
        }
    }

    private companion object {
        const val WEATHER_MAX_AGE_MS = 60 * 60_000L
        const val CHECK_EVERY_MS = 10 * 60_000L
    }
}
