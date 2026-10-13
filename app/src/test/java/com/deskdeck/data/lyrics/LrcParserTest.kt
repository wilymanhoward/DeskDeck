package com.deskdeck.data.lyrics

import com.deskdeck.domain.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun `parses two and three digit fractions`() {
        val lines = LrcParser.parse(
            """
            [00:01.50]two digits
            [00:02.250]three digits
            [00:03.5]one digit
            [00:04]no fraction
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                LyricLine(1_500, "two digits"),
                LyricLine(2_250, "three digits"),
                LyricLine(3_500, "one digit"),
                LyricLine(4_000, "no fraction"),
            ),
            lines,
        )
    }

    @Test
    fun `colon separated fraction and long minutes`() {
        val lines = LrcParser.parse("[01:02:30]colon\n[123:00.00]long")
        assertEquals(LyricLine(62_300, "colon"), lines[0])
        assertEquals(LyricLine(123 * 60_000L, "long"), lines[1])
    }

    @Test
    fun `multiple timestamps on one line produce one line each, sorted`() {
        val lines = LrcParser.parse("[00:30.00][00:10.00]Chorus\n[00:20.00]Verse")
        assertEquals(
            listOf(LyricLine(10_000, "Chorus"), LyricLine(20_000, "Verse"), LyricLine(30_000, "Chorus")),
            lines,
        )
    }

    @Test
    fun `metadata tags are ignored`() {
        val lines = LrcParser.parse(
            """
            [ar:Artist]
            [ti:Title]
            [al:Album]
            [by:someone]
            [length: 03:20]
            [re:Editor]
            [ve:1.0]
            [00:05.00]Only line
            """.trimIndent(),
        )
        assertEquals(listOf(LyricLine(5_000, "Only line")), lines)
    }

    @Test
    fun `positive offset shows lyrics earlier and clamps at zero`() {
        val lines = LrcParser.parse("[offset:+500]\n[00:00.20]a\n[00:10.00]b")
        assertEquals(listOf(LyricLine(0, "a"), LyricLine(9_500, "b")), lines)
    }

    @Test
    fun `negative offset shows lyrics later`() {
        val lines = LrcParser.parse("[offset:-250]\n[00:10.00]b")
        assertEquals(listOf(LyricLine(10_250, "b")), lines)
    }

    @Test
    fun `offset tag after lines still applies`() {
        val lines = LrcParser.parse("[00:10.00]b\n[offset:1000]")
        assertEquals(listOf(LyricLine(9_000, "b")), lines)
    }

    @Test
    fun `blank text is kept as an instrumental marker`() {
        val lines = LrcParser.parse("[00:10.00]\n[00:12.00]   ")
        assertEquals(listOf(LyricLine(10_000, ""), LyricLine(12_000, "")), lines)
    }

    @Test
    fun `word level stamps are stripped`() {
        val lines = LrcParser.parse("[00:01.00]<00:01.00>Hello <00:01.50>there <00:02.00>world")
        assertEquals(listOf(LyricLine(1_000, "Hello there world")), lines)
    }

    @Test
    fun `lines without timestamps and garbage are skipped`() {
        val lines = LrcParser.parse("Plain line\n[xx:yy]bad\n\n[00:01.00]good\r\n")
        assertEquals(listOf(LyricLine(1_000, "good")), lines)
    }

    @Test
    fun `brackets inside lyric text are preserved`() {
        val lines = LrcParser.parse("[00:01.00]Oh [yeah] baby")
        assertEquals("Oh [yeah] baby", lines.single().text)
    }

    @Test
    fun `windows line endings and BOM are handled`() {
        val bom = 0xFEFF.toChar()
        val lines = LrcParser.parse("$bom[00:01.00]a\r\n[00:02.00]b\r\n")
        assertEquals(listOf(LyricLine(1_000, "a"), LyricLine(2_000, "b")), lines)
    }

    @Test
    fun `empty input gives no lines`() {
        assertTrue(LrcParser.parse("").isEmpty())
    }
}
