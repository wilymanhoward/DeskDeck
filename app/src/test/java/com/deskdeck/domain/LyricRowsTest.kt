package com.deskdeck.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricRowsTest {

    private fun line(start: Long, end: Long, text: String = "la") = LyricLine(start, text, endMs = end)

    @Test
    fun `close lines have no gaps`() {
        val rows = buildLyricRows(listOf(line(1_000, 3_000), line(4_000, 6_000)))
        assertEquals(2, rows.size)
        assertTrue(rows.all { it is LyricRow.Line })
    }

    @Test
    fun `a late first line gets an intro gap`() {
        val rows = buildLyricRows(listOf(line(12_000, 14_000)))
        assertEquals(LyricRow.Gap(0, 12_000), rows.first())
    }

    @Test
    fun `a long wait after a line's end becomes a gap`() {
        val rows = buildLyricRows(listOf(line(1_000, 3_000), line(9_000, 10_000)))
        assertEquals(LyricRow.Gap(3_000, 9_000), rows[1])
    }

    @Test
    fun `an empty line is a gap until the next line, and runs of them merge`() {
        val rows = buildLyricRows(listOf(line(1_000, 2_000), line(2_500, 2_500, ""), line(4_000, 4_000, ""), line(5_000, 6_000)))
        assertEquals(listOf(LyricRow.Line(0, line(1_000, 2_000)), LyricRow.Gap(2_500, 5_000), LyricRow.Line(3, line(5_000, 6_000))), rows)
    }

    @Test
    fun `active row lookup and gap progress`() {
        val rows = buildLyricRows(listOf(line(1_000, 3_000), line(9_000, 10_000)))
        assertEquals(-1, rows.activeRowAt(500))
        assertEquals(0, rows.activeRowAt(2_000))
        assertEquals(1, rows.activeRowAt(6_000))
        assertEquals(0.5f, (rows[1] as LyricRow.Gap).progressAt(6_000))
        assertEquals(2, rows.activeRowAt(9_500))
    }

    @Test
    fun `word progress is linear within the word`() {
        val word = LyricWord(1_000, 2_000, "hey")
        assertEquals(0f, word.progressAt(900))
        assertEquals(0.25f, word.progressAt(1_250))
        assertEquals(1f, word.progressAt(2_100))
    }
}
