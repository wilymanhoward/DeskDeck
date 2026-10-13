package com.deskdeck.ui.common

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import com.deskdeck.domain.PlaybackInfo

/**
 * The playback position, extrapolated from the latest [PlaybackInfo] snapshot on every frame
 * while playing. The player is never polled: a new snapshot (play, pause, seek, track change)
 * restarts the loop from fresh values.
 *
 * Read the returned state only in leaf composables or draw lambdas, so that a per-frame update
 * redraws the seek bar instead of recomposing the whole screen.
 */
@Composable
fun rememberPlaybackPosition(playback: PlaybackInfo?, durationMs: Long?): State<Long> {
    val position = remember { mutableLongStateOf(0L) }
    LaunchedEffect(playback, durationMs) {
        if (playback == null) {
            position.longValue = 0L
            return@LaunchedEffect
        }
        position.longValue = playback.positionAt(SystemClock.elapsedRealtime(), durationMs)
        if (!playback.isPlaying) return@LaunchedEffect
        while (true) {
            withFrameMillis { }
            position.longValue = playback.positionAt(SystemClock.elapsedRealtime(), durationMs)
        }
    }
    return position
}
