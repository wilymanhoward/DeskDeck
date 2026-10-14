package com.deskdeck.domain.brief

import com.deskdeck.data.settings.BriefDuration
import java.time.Duration
import java.time.Instant

/** "Until dismissed" still ends eventually, so a forgotten brief doesn't sit on screen all day. */
val BRIEF_MAX_SHOWN: Duration = Duration.ofHours(4)

/**
 * Whether the Morning Brief is up at [now]: from the last scheduled wake-up [lastWake] for
 * [duration], unless it was dismissed for that same wake-up, the brief is off, or it's night.
 */
fun briefVisible(
    now: Instant,
    enabled: Boolean,
    nightActive: Boolean,
    lastWake: Instant?,
    duration: BriefDuration,
    dismissedWake: Instant?,
): Boolean {
    if (!enabled || nightActive || lastWake == null || now < lastWake) return false
    if (dismissedWake == lastWake) return false
    val shownFor = duration.minutes?.let { Duration.ofMinutes(it.toLong()) } ?: BRIEF_MAX_SHOWN
    return now < lastWake.plus(shownFor)
}
