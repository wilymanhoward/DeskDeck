package com.deskdeck.burnin

import com.deskdeck.data.settings.BurnInSettings
import com.deskdeck.data.settings.KeepScreenOn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BurnInPolicyTest {

    private val min = 60_000L
    private val defaults = BurnInSettings(
        idleDim = true, dimAfterMin = 3,
        ambient = true, ambientAfterMin = 5,
        pausedClock = true, pausedClockAfterMin = 5,
        screenOffWhenPaused = false,
    )

    private fun decide(
        idleMin: Double,
        playing: Boolean = true,
        notPlayingForMin: Double? = null,
        settings: BurnInSettings = defaults,
        keep: KeepScreenOn = KeepScreenOn.WHILE_PLAYING,
    ): BurnInDecision {
        val now = 100 * min
        return BurnInPolicy.decide(
            BurnInInput(
                nowMs = now,
                lastInteractionMs = now - (idleMin * min).toLong(),
                isPlaying = playing,
                notPlayingSinceMs = notPlayingForMin?.let { now - (it * min).toLong() },
                settings = settings,
                keepScreenOn = keep,
            ),
        )
    }

    @Test
    fun `recent touch while playing is active`() {
        val d = decide(idleMin = 0.5)
        assertEquals(DisplayMode.ACTIVE, d.mode)
        assertTrue(d.keepScreenOn)
    }

    @Test
    fun `dims after the idle delay, then ambient after the extra delay`() {
        assertEquals(DisplayMode.DIMMED, decide(idleMin = 3.0).mode)
        assertEquals(DisplayMode.DIMMED, decide(idleMin = 7.9).mode)
        assertEquals(DisplayMode.AMBIENT, decide(idleMin = 8.0).mode)
    }

    @Test
    fun `ambient without dimming counts from idle start`() {
        val noDim = defaults.copy(idleDim = false)
        assertEquals(DisplayMode.ACTIVE, decide(idleMin = 4.0, settings = noDim).mode)
        assertEquals(DisplayMode.AMBIENT, decide(idleMin = 5.0, settings = noDim).mode)
    }

    @Test
    fun `everything off stays active forever`() {
        val off = defaults.copy(idleDim = false, ambient = false, pausedClock = false)
        val d = decide(idleMin = 600.0, playing = false, notPlayingForMin = 600.0, settings = off)
        assertEquals(DisplayMode.ACTIVE, d.mode)
        assertNull(d.nextChangeAtMs)
    }

    @Test
    fun `paused clock needs both no playback and no touch for the delay`() {
        assertEquals(DisplayMode.PAUSED_CLOCK, decide(idleMin = 6.0, playing = false, notPlayingForMin = 6.0).mode)
        // Paused long ago but touched recently: not yet.
        assertEquals(DisplayMode.ACTIVE, decide(idleMin = 1.0, playing = false, notPlayingForMin = 30.0).mode)
        // Idle long but only just paused: dims instead.
        assertEquals(DisplayMode.DIMMED, decide(idleMin = 4.0, playing = false, notPlayingForMin = 1.0).mode)
    }

    @Test
    fun `next change is the nearest upcoming threshold`() {
        val now = 100 * min
        val d = decide(idleMin = 1.0)
        assertEquals(now + 2 * min, d.nextChangeAtMs)
        val dimmed = decide(idleMin = 4.0)
        assertEquals(now + 4 * min, dimmed.nextChangeAtMs)
    }

    @Test
    fun `next change includes the paused clock threshold`() {
        val now = 100 * min
        val d = decide(idleMin = 0.0, playing = false, notPlayingForMin = 0.0)
        // Dim at +3 comes before paused clock at +5.
        assertEquals(now + 3 * min, d.nextChangeAtMs)
    }

    @Test
    fun `while playing mode holds the screen through the paused grace period only`() {
        assertTrue(decide(idleMin = 1.0, playing = false, notPlayingForMin = 1.0).keepScreenOn)
        assertFalse(decide(idleMin = 6.0, playing = false, notPlayingForMin = 6.0).keepScreenOn)
    }

    @Test
    fun `while playing mode without paused clock releases as soon as playback stops`() {
        val noClock = defaults.copy(pausedClock = false)
        assertFalse(decide(idleMin = 0.0, playing = false, notPlayingForMin = 0.0, settings = noClock).keepScreenOn)
        assertTrue(decide(idleMin = 0.0, playing = true, settings = noClock).keepScreenOn)
    }

    @Test
    fun `always mode keeps the screen on unless told to turn off with the clock`() {
        val paused = { s: BurnInSettings -> decide(idleMin = 6.0, playing = false, notPlayingForMin = 6.0, settings = s, keep = KeepScreenOn.ALWAYS) }
        assertTrue(paused(defaults).keepScreenOn)
        assertFalse(paused(defaults.copy(screenOffWhenPaused = true)).keepScreenOn)
        assertTrue(decide(idleMin = 60.0, keep = KeepScreenOn.ALWAYS).keepScreenOn)
    }
}
