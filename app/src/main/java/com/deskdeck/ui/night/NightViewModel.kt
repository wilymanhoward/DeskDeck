package com.deskdeck.ui.night

import androidx.lifecycle.ViewModel
import com.deskdeck.domain.NowPlaying
import com.deskdeck.domain.night.NightState
import com.deskdeck.media.MediaRepository
import com.deskdeck.media.NightModeController
import com.deskdeck.media.NotificationCounter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** The night screen's music, notification count and Night Mode state, on the same plumbing as the dashboard. */
@HiltViewModel
class NightViewModel @Inject constructor(
    private val media: MediaRepository,
    private val night: NightModeController,
    notifications: NotificationCounter,
) : ViewModel() {
    val state: StateFlow<NightState> = night.state
    val nowPlaying: StateFlow<NowPlaying?> = media.nowPlaying
    val notificationCount: StateFlow<Int> = notifications.count

    fun playPause() = media.playPause()
    fun skipNext() = media.skipNext()
    fun skipPrevious() = media.skipPrevious()
    fun exitNight() = night.endNow()
}
