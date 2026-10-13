package com.deskdeck.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    @Test
    fun `formats minutes and seconds`() {
        assertEquals("0:00", formatPlaybackTime(0))
        assertEquals("0:09", formatPlaybackTime(9_999))
        assertEquals("3:05", formatPlaybackTime(185_000))
        assertEquals("59:59", formatPlaybackTime(3_599_000))
    }

    @Test
    fun `switches to hours from one hour`() {
        assertEquals("1:00:00", formatPlaybackTime(3_600_000))
        assertEquals("2:03:04", formatPlaybackTime(7_384_000))
    }

    @Test
    fun `negative values clamp to zero`() {
        assertEquals("0:00", formatPlaybackTime(-5_000))
    }
}
