package com.deskdeck.domain.night

import com.deskdeck.data.settings.NightOverride
import com.deskdeck.data.settings.NightSettings
import com.deskdeck.data.settings.SleepWindow
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightScheduleCalculatorTest {

    private val kl = ZoneId.of("Asia/Kuala_Lumpur")
    private val ny = ZoneId.of("America/New_York")
    private val calc = NightScheduleCalculator(kl)

    private fun t(text: String, zone: ZoneId = kl): Instant = LocalDateTime.parse(text).atZone(zone).toInstant()
    private fun hm(h: Int, m: Int = 0) = h * 60 + m

    private val lateNights = NightSettings(enabled = true, weekdays = SleepWindow(hm(23, 30), hm(7)))

    // 2026-10-13 is a Tuesday.

    @Test
    fun `a window crossing midnight is active overnight`() {
        val night = calc.stateAt(t("2026-10-14T02:00"), lateNights)
        assertTrue(night.active)
        assertEquals(t("2026-10-14T07:00"), night.nextChangeAt)

        val evening = calc.stateAt(t("2026-10-13T22:00"), lateNights)
        assertFalse(evening.active)
        assertEquals(t("2026-10-13T23:30"), evening.nextChangeAt)

        val morning = calc.stateAt(t("2026-10-14T07:00"), lateNights)
        assertFalse(morning.active)
        assertEquals(t("2026-10-14T07:00"), morning.lastWakeAt)
    }

    @Test
    fun `a window after midnight starts on the wake-up day`() {
        val s = NightSettings(enabled = true, weekdays = SleepWindow(hm(0, 30), hm(9)))
        assertFalse(calc.stateAt(t("2026-10-13T23:59"), s).active)
        assertTrue(calc.stateAt(t("2026-10-14T00:30"), s).active)
        assertTrue(calc.stateAt(t("2026-10-14T08:59"), s).active)
    }

    @Test
    fun `weekend mornings use the weekend window`() {
        val s = lateNights.copy(weekendDifferent = true, weekend = SleepWindow(hm(0, 30), hm(9, 30)))
        // Friday night into Saturday 2026-10-17: weekend window, so not yet at 23:45 Friday.
        assertFalse(calc.stateAt(t("2026-10-16T23:45"), s).active)
        assertTrue(calc.stateAt(t("2026-10-17T09:00"), s).active)
        // Sunday night into Monday: back to weekdays.
        assertTrue(calc.stateAt(t("2026-10-18T23:45"), s).active)
        assertFalse(calc.stateAt(t("2026-10-19T07:30"), s).active)
    }

    @Test
    fun `nights only for chosen wake-up days`() {
        val s = lateNights.copy(wakeDays = DayOfWeek.entries.toSet() - DayOfWeek.SATURDAY)
        assertFalse(calc.stateAt(t("2026-10-17T02:00"), s).active)
        assertTrue(calc.stateAt(t("2026-10-18T02:00"), s).active)
    }

    @Test
    fun `disabled means never, unless started by hand`() {
        val off = lateNights.copy(enabled = false)
        val state = calc.stateAt(t("2026-10-14T02:00"), off)
        assertFalse(state.active)
        assertNull(state.nextChangeAt)

        val now = t("2026-10-13T20:00")
        val manual = calc.startNow(now, off)
        assertEquals(t("2026-10-14T07:00").toEpochMilli(), manual.untilEpochMs)
        assertTrue(calc.stateAt(now, off, manual).active)
        assertTrue(calc.stateAt(now, off, manual).overridden)
    }

    @Test
    fun `end now holds Night Mode off until the next bedtime, then the schedule resumes`() {
        val now = t("2026-10-14T01:00")
        val override = calc.endNow(now, lateNights)
        assertEquals(t("2026-10-14T23:30").toEpochMilli(), override.untilEpochMs)
        assertFalse(calc.stateAt(now, lateNights, override).active)
        assertTrue(calc.stateAt(t("2026-10-15T01:00"), lateNights, override).active)
    }

    @Test
    fun `an expired override is ignored`() {
        val stale = NightOverride(on = false, untilEpochMs = t("2026-10-13T12:00").toEpochMilli())
        assertTrue(calc.stateAt(t("2026-10-14T02:00"), lateNights, stale).active)
    }

    @Test
    fun `spring forward keeps wall-clock times and shortens the night`() {
        // New York, 2026-03-08: 02:00 jumps to 03:00.
        val nyCalc = NightScheduleCalculator(ny)
        val night = nyCalc.nightsAround(t("2026-03-08T01:00", ny), NightSettings(enabled = true))
            .first { it.end == t("2026-03-08T07:00", ny) }
        assertEquals(t("2026-03-07T23:00", ny), night.start)
        assertEquals(Duration.ofHours(7), Duration.between(night.start, night.end))
    }

    @Test
    fun `a bedtime in the skipped hour moves to just after it`() {
        val nyCalc = NightScheduleCalculator(ny)
        val s = NightSettings(enabled = true, weekdays = SleepWindow(hm(2, 30), hm(7)))
        val night = nyCalc.nightsAround(t("2026-03-08T01:00", ny), s).first { it.end == t("2026-03-08T07:00", ny) }
        assertEquals(t("2026-03-08T03:30", ny), night.start)
    }

    @Test
    fun `fall back keeps wall-clock times and lengthens the night`() {
        // New York, 2026-11-01: 02:00 goes back to 01:00.
        val nyCalc = NightScheduleCalculator(ny)
        val night = nyCalc.nightsAround(t("2026-11-01T03:00", ny), NightSettings(enabled = true))
            .first { it.end == t("2026-11-01T07:00", ny) }
        assertEquals(Duration.ofHours(9), Duration.between(night.start, night.end))
    }
}
