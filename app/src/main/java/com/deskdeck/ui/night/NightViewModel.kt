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
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** The night screen's music, notification count and Night Mode state, on the same plumbing as the dashboard. */
@HiltViewModel
class NightViewModel @Inject constructor(
    private val media: MediaRepository,
    private val night: NightModeController,
    notifications: NotificationCounter,
    brief: BriefRepository,
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
}
