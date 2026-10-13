package com.deskdeck.domain

import kotlin.math.roundToInt

/**
 * The volume DeskDeck controls: the shown session's remote volume (e.g. a Spotify Connect speaker)
 * when it plays remotely, otherwise the tablet's music stream.
 */
data class VolumeState(
    val level: Int,
    val max: Int,
    val muted: Boolean = false,
    /** True for a remote session (Cast, Spotify Connect); false for the tablet's own speaker/output. */
    val remote: Boolean = false,
    /** The volume can't be changed from here (fixed-volume output). */
    val fixed: Boolean = false,
) {
    val fraction: Float get() = if (muted || max <= 0) 0f else (level.toFloat() / max).coerceIn(0f, 1f)

    val speaker: SpeakerLevel get() = SpeakerLevel.of(fraction, muted)

    fun levelFor(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * max).roundToInt()

    /** At the end the user is pushing toward: max when raising, silent when lowering. */
    fun atLimit(direction: Int): Boolean = when {
        direction > 0 -> !muted && level >= max
        direction < 0 -> muted || level <= 0
        else -> false
    }
}

/** The speaker glyph's state; the HUD icon morphs between these. */
enum class SpeakerLevel(val waves: Int) {
    MUTE(0), LOW(1), MEDIUM(2), HIGH(3);

    companion object {
        fun of(fraction: Float, muted: Boolean): SpeakerLevel = when {
            muted || fraction <= 0f -> MUTE
            fraction < 0.34f -> LOW
            fraction < 0.67f -> MEDIUM
            else -> HIGH
        }
    }
}

/** A volume change worth showing the HUD for. */
data class VolumeEvent(
    val state: VolumeState,
    /** From DeskDeck's keys or controls (wakes the screen), rather than from another device or app. */
    val userInitiated: Boolean,
    /** The change reached the min or max, or pushed against it: play a subtle haptic tick. */
    val hitLimit: Boolean,
)
