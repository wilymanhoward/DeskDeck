package com.deskdeck.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveLyricIndexTest {

    private val lines = listOf(
        LyricLine(1_000, "a"),
        LyricLine(5_000, "b"),
        LyricLine(5_000, "b duplicate time"),
        LyricLine(9_000, "c"),
    )

    @Test
    fun `before first line is -1`() {
        assertEquals(-1, lines.activeIndexAt(0))
        assertEquals(-1, lines.activeIndexAt(999))
    }

    @Test
    fun `line becomes active exactly at its timestamp`() {
        assertEquals(0, lines.activeIndexAt(1_000))
        assertEquals(3, lines.activeIndexAt(9_000))
    }

    @Test
    fun `between lines the earlier line stays active`() {
        assertEquals(0, lines.activeIndexAt(4_999))
        assertEquals(2, lines.activeIndexAt(8_000))
    }

    @Test
    fun `equal timestamps resolve to the last of them`() {
        assertEquals(2, lines.activeIndexAt(5_000))
    }

    @Test
    fun `after last line stays on last line`() {
        assertEquals(3, lines.activeIndexAt(1_000_000))
    }

    @Test
    fun `empty list is always -1`() {
        assertEquals(-1, emptyList<LyricLine>().activeIndexAt(5_000))
    }
}
