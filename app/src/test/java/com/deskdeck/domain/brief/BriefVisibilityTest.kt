package com.deskdeck.domain.brief

import com.deskdeck.data.settings.BriefDuration
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BriefVisibilityTest {
    private val wake = Instant.parse("2026-10-14T23:00:00Z")
    private fun at(minutes: Long) = wake.plus(Duration.ofMinutes(minutes))

    @Test
    fun `shown from wake-up for the chosen duration`() {
        assertTrue(briefVisible(at(0), true, false, wake, BriefDuration.MIN_30, null))
        assertTrue(briefVisible(at(29), true, false, wake, BriefDuration.MIN_30, null))
        assertFalse(briefVisible(at(30), true, false, wake, BriefDuration.MIN_30, null))
        assertFalse(briefVisible(at(-1), true, false, wake, BriefDuration.MIN_30, null))
    }

    @Test
    fun `until dismissed still ends after a few hours`() {
        assertTrue(briefVisible(at(180), true, false, wake, BriefDuration.UNTIL_DISMISSED, null))
        assertFalse(briefVisible(at(4 * 60), true, false, wake, BriefDuration.UNTIL_DISMISSED, null))
    }

    @Test
    fun `done hides it for that morning only`() {
        assertFalse(briefVisible(at(5), true, false, wake, BriefDuration.MIN_60, dismissedWake = wake))
        val tomorrow = wake.plus(Duration.ofDays(1))
        assertTrue(briefVisible(tomorrow.plusSeconds(60), true, false, tomorrow, BriefDuration.MIN_60, dismissedWake = wake))
    }

    @Test
    fun `off, at night, or never woken`() {
        assertFalse(briefVisible(at(1), false, false, wake, BriefDuration.MIN_30, null))
        assertFalse(briefVisible(at(1), true, true, wake, BriefDuration.MIN_30, null))
        assertFalse(briefVisible(at(1), true, false, null, BriefDuration.MIN_30, null))
    }
}
