package com.deskdeck.media

import com.deskdeck.domain.NowPlaying
import kotlinx.coroutines.flow.StateFlow

/** The currently shown media session and the commands that can be sent to it. */
interface MediaRepository {
    /** Null when no session is active. */
    val nowPlaying: StateFlow<NowPlaying?>

    fun playPause()
    fun skipNext()
    fun skipPrevious()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeat()
}
