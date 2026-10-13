package com.deskdeck.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeStateTest {

    @Test
    fun `fraction is level over max, and zero when muted`() {
        assertEquals(0.5f, VolumeState(level = 8, max = 16).fraction)
        assertEquals(0f, VolumeState(level = 8, max = 16, muted = true).fraction)
        assertEquals(0f, VolumeState(level = 3, max = 0).fraction)
    }

    @Test
    fun `speaker level bands`() {
        assertEquals(SpeakerLevel.MUTE, SpeakerLevel.of(0f, muted = false))
        assertEquals(SpeakerLevel.MUTE, SpeakerLevel.of(0.9f, muted = true))
        assertEquals(SpeakerLevel.LOW, SpeakerLevel.of(0.1f, muted = false))
        assertEquals(SpeakerLevel.MEDIUM, SpeakerLevel.of(0.5f, muted = false))
        assertEquals(SpeakerLevel.HIGH, SpeakerLevel.of(0.67f, muted = false))
        assertEquals(SpeakerLevel.HIGH, SpeakerLevel.of(1f, muted = false))
    }

    @Test
    fun `levelFor rounds to the nearest step and clamps`() {
        val state = VolumeState(level = 0, max = 15)
        assertEquals(8, state.levelFor(0.5f))
        assertEquals(15, state.levelFor(1.4f))
        assertEquals(0, state.levelFor(-0.2f))
    }

    @Test
    fun `atLimit depends on the direction pushed`() {
        val top = VolumeState(level = 15, max = 15)
        assertTrue(top.atLimit(+1))
        assertFalse(top.atLimit(-1))

        val bottom = VolumeState(level = 0, max = 15)
        assertTrue(bottom.atLimit(-1))
        assertFalse(bottom.atLimit(+1))

        // Muted counts as the bottom even with a remembered level; raising unmutes.
        val muted = VolumeState(level = 15, max = 15, muted = true)
        assertTrue(muted.atLimit(-1))
        assertFalse(muted.atLimit(+1))
        assertFalse(VolumeState(level = 5, max = 15).atLimit(0))
    }
}
