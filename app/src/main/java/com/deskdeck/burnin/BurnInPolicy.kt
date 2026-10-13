package com.deskdeck.burnin

import com.deskdeck.data.settings.BurnInSettings
import com.deskdeck.data.settings.KeepScreenOn
import kotlin.math.max

enum class DisplayMode {
    ACTIVE,

    /** Same UI under a dark scrim; first touch only wakes. */
    DIMMED,

    /** Screensaver: small art, title, current lyric and clock drifting on black. */
    AMBIENT,

    /** Nothing playing for a while: a very dim drifting clock only. */
    PAUSED_CLOCK,
}

data class BurnInInput(
    val nowMs: Long,
    val lastInteractionMs: Long,
    val isPlaying: Boolean,
    /** When playback last stopped (or the app started without playback); null while playing. */
    val notPlayingSinceMs: Long?,
    val settings: BurnInSettings,
    val keepScreenOn: KeepScreenOn,
)

data class BurnInDecision(
    val mode: DisplayMode,
    val keepScreenOn: Boolean,
    /** Earliest time the decision can change without new input; null if it can't. */
    val nextChangeAtMs: Long?,
)

/**
 * Decides what the dashboard shows and whether the screen is held on. Pure, so the whole policy is
 * unit-tested; the UI just re-evaluates on input changes and at [BurnInDecision.nextChangeAtMs].
 *
 * - Paused clock: nothing has played *and* nobody has touched the screen for `pausedClockAfterMin`.
 * - Otherwise by idle time: dimmed after `dimAfterMin`, ambient `ambientAfterMin` later.
 * - Screen-on: [KeepScreenOn.ALWAYS] holds it except in the paused clock when `screenOffWhenPaused`;
 *   [KeepScreenOn.WHILE_PLAYING] holds it while playing and through the paused grace period.
 */
object BurnInPolicy {

    private const val MINUTE_MS = 60_000L

    fun decide(input: BurnInInput): BurnInDecision {
        val s = input.settings
        val now = input.nowMs
        val upcoming = mutableListOf<Long>()

        var mode: DisplayMode? = null
        val notPlayingSince = input.notPlayingSinceMs
        if (!input.isPlaying && s.pausedClock && notPlayingSince != null) {
            val clockAt = max(notPlayingSince, input.lastInteractionMs) + s.pausedClockAfterMin * MINUTE_MS
            if (now >= clockAt) mode = DisplayMode.PAUSED_CLOCK else upcoming += clockAt
        }

        if (mode == null) {
            val dimAt = if (s.idleDim) input.lastInteractionMs + s.dimAfterMin * MINUTE_MS else null
            val ambientAt = if (s.ambient) {
                val delayMin = (if (s.idleDim) s.dimAfterMin else 0) + s.ambientAfterMin
                input.lastInteractionMs + delayMin * MINUTE_MS
            } else {
                null
            }
            mode = when {
                ambientAt != null && now >= ambientAt -> DisplayMode.AMBIENT
                dimAt != null && now >= dimAt -> DisplayMode.DIMMED
                else -> DisplayMode.ACTIVE
            }
            listOfNotNull(dimAt, ambientAt).filterTo(upcoming) { it > now }
        }

        val keepOn = when (input.keepScreenOn) {
            KeepScreenOn.ALWAYS -> !(mode == DisplayMode.PAUSED_CLOCK && s.screenOffWhenPaused)
            KeepScreenOn.WHILE_PLAYING -> input.isPlaying || (s.pausedClock && mode != DisplayMode.PAUSED_CLOCK)
        }
        return BurnInDecision(mode, keepOn, upcoming.filter { it > now }.minOrNull())
    }
}
