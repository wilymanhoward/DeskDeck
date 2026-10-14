package com.deskdeck.domain.night

import com.deskdeck.data.settings.NightOverride
import com.deskdeck.data.settings.NightSettings
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Whether Night Mode is on at a moment, and when that next changes. */
data class NightState(
    val active: Boolean,
    /** When [active] next flips (bedtime, wake-up or an override's end); null if never (disabled). */
    val nextChangeAt: Instant?,
    /** The most recent scheduled wake-up at or before now, for the Morning Brief; null if none. */
    val lastWakeAt: Instant?,
    val overridden: Boolean = false,
)

/** One scheduled night: [start] (bedtime) until [end] (wake-up), as real instants. */
data class NightInterval(val start: Instant, val end: Instant)

/**
 * Night Mode's schedule, pure and testable. A night is identified by the day you wake up: the
 * night ending on morning W runs from the latest bedtime before W's wake-up time. So 23:30 → 07:00
 * for a Tuesday wake-up starts Monday 23:30, and 00:30 → 09:00 starts Tuesday 00:30.
 *
 * Times are local wall-clock times, so they follow DST: a wake-up at 07:00 stays 07:00 on the day
 * clocks change, and a bedtime that falls in the skipped spring-forward hour moves to just after
 * it (java.time's gap rule). A manual [NightOverride] wins until it expires.
 */
class NightScheduleCalculator(private val zone: ZoneId) {

    fun stateAt(now: Instant, settings: NightSettings, override: NightOverride? = null): NightState {
        val nights = if (settings.enabled) nightsAround(now, settings) else emptyList()
        val lastWake = nights.map { it.end }.filter { it <= now }.maxOrNull()
        val activeOverride = override?.takeIf { it.untilEpochMs > now.toEpochMilli() }
        if (activeOverride != null) {
            return NightState(activeOverride.on, Instant.ofEpochMilli(activeOverride.untilEpochMs), lastWake, overridden = true)
        }
        val current = nights.firstOrNull { now >= it.start && now < it.end }
        val next = current?.end ?: nights.map { it.start }.filter { it > now }.minOrNull()
        return NightState(active = current != null, nextChangeAt = next, lastWakeAt = lastWake)
    }

    /** "Start Night Mode now": on until the next wake-up time (or the next 07:00-style wake if off). */
    fun startNow(now: Instant, settings: NightSettings): NightOverride {
        val today = now.atZone(zone).toLocalDate()
        val wake = listOf(today, today.plusDays(1))
            .map { day -> at(day, settings.windowFor(day.dayOfWeek).wakeMin) }
            .first { it.isAfter(now) }
        return NightOverride(on = true, untilEpochMs = wake.toEpochMilli())
    }

    /** "End Night Mode now": off until the next scheduled bedtime (or 12 h if none is scheduled). */
    fun endNow(now: Instant, settings: NightSettings): NightOverride {
        val nextStart = if (settings.enabled) nightsAround(now, settings).map { it.start }.filter { it > now }.minOrNull() else null
        return NightOverride(on = false, untilEpochMs = (nextStart ?: now.plusSeconds(12 * 3600)).toEpochMilli())
    }

    /** Scheduled nights whose wake-up day is from yesterday to a week ahead, in order. */
    fun nightsAround(now: Instant, settings: NightSettings): List<NightInterval> {
        val today = now.atZone(zone).toLocalDate()
        return (-1L..8L).mapNotNull { offset ->
            val wakeDay = today.plusDays(offset)
            if (wakeDay.dayOfWeek !in settings.wakeDays) return@mapNotNull null
            val window = settings.windowFor(wakeDay.dayOfWeek)
            val end = at(wakeDay, window.wakeMin)
            // Bedtime on the wake day if it's earlier that day (00:30 → 09:00), else the evening before.
            val startDay = if (window.bedtimeMin < window.wakeMin) wakeDay else wakeDay.minusDays(1)
            val start = at(startDay, window.bedtimeMin)
            if (start < end) NightInterval(start, end) else null
        }
    }

    private fun at(day: LocalDate, minutes: Int): Instant =
        ZonedDateTime.of(day, LocalTime.of(minutes / 60 % 24, minutes % 60), zone).toInstant()
}
