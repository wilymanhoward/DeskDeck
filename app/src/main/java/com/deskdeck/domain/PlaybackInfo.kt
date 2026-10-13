package com.deskdeck.domain

enum class PlaybackStatus { PLAYING, PAUSED, BUFFERING, STOPPED }

enum class RepeatMode { OFF, ALL, ONE }

/**
 * A snapshot of the player's state. Position is reported as "[positionMs] at [updatedAtElapsedMs]"
 * (an `elapsedRealtime` timestamp), exactly like `android.media.session.PlaybackState`, so the
 * current position can be extrapolated with [positionAt] without asking the player again.
 */
data class PlaybackInfo(
    val status: PlaybackStatus,
    val positionMs: Long,
    val updatedAtElapsedMs: Long,
    val speed: Float = 1f,
    val canSeek: Boolean = true,
    val canSkipNext: Boolean = true,
    val canSkipPrevious: Boolean = true,
    val shuffleSupported: Boolean = false,
    val shuffleOn: Boolean = false,
    val repeatSupported: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
) {
    val isPlaying: Boolean get() = status == PlaybackStatus.PLAYING

    /**
     * Extrapolated position at [nowElapsedMs]: `position + (now - lastUpdate) * speed`, clamped to
     * `[0, durationMs]`. Only advances while playing. Some players report a speed of 0 while in the
     * PLAYING state; that is treated as normal speed so the seek bar doesn't freeze.
     */
    fun positionAt(nowElapsedMs: Long, durationMs: Long?): Long {
        val base = positionMs.coerceAtLeast(0L)
        val position = if (isPlaying) {
            val effectiveSpeed = if (speed > 0f) speed else 1f
            val elapsed = (nowElapsedMs - updatedAtElapsedMs).coerceAtLeast(0L)
            base + (elapsed * effectiveSpeed).toLong()
        } else {
            base
        }
        return if (durationMs != null && durationMs > 0) position.coerceAtMost(durationMs) else position
    }
}
