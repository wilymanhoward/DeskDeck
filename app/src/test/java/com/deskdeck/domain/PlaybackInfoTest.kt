package com.deskdeck.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackInfoTest {

    private fun info(
        status: PlaybackStatus = PlaybackStatus.PLAYING,
        position: Long = 10_000,
        updatedAt: Long = 1_000,
        speed: Float = 1f,
    ) = PlaybackInfo(status = status, positionMs = position, updatedAtElapsedMs = updatedAt, speed = speed)

    @Test
    fun `playing position advances with elapsed time`() {
        assertEquals(12_500, info().positionAt(nowElapsedMs = 3_500, durationMs = 200_000))
    }

    @Test
    fun `playback speed scales the advance`() {
        assertEquals(14_000, info(speed = 2f).positionAt(nowElapsedMs = 3_000, durationMs = 200_000))
        assertEquals(11_000, info(speed = 0.5f).positionAt(nowElapsedMs = 3_000, durationMs = 200_000))
    }

    @Test
    fun `paused position does not advance`() {
        val paused = info(status = PlaybackStatus.PAUSED)
        assertEquals(10_000, paused.positionAt(nowElapsedMs = 60_000, durationMs = 200_000))
    }

    @Test
    fun `buffering position does not advance`() {
        val buffering = info(status = PlaybackStatus.BUFFERING)
        assertEquals(10_000, buffering.positionAt(nowElapsedMs = 60_000, durationMs = 200_000))
    }

    @Test
    fun `position is clamped to duration`() {
        assertEquals(15_000, info().positionAt(nowElapsedMs = 100_000, durationMs = 15_000))
    }

    @Test
    fun `unknown duration does not clamp`() {
        assertEquals(109_000, info().positionAt(nowElapsedMs = 100_000, durationMs = null))
        assertEquals(109_000, info().positionAt(nowElapsedMs = 100_000, durationMs = 0))
    }

    @Test
    fun `update timestamp in the future does not move position backwards`() {
        assertEquals(10_000, info(updatedAt = 5_000).positionAt(nowElapsedMs = 4_000, durationMs = 200_000))
    }

    @Test
    fun `negative reported position is treated as zero`() {
        assertEquals(1_000, info(position = -500).positionAt(nowElapsedMs = 2_000, durationMs = 200_000))
    }

    @Test
    fun `zero speed while playing is treated as normal speed`() {
        assertEquals(12_000, info(speed = 0f).positionAt(nowElapsedMs = 3_000, durationMs = 200_000))
    }
}
