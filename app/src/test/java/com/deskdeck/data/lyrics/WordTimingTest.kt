package com.deskdeck.data.lyrics

import com.deskdeck.domain.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordTimingTest {

    @Test
    fun `tokens keep their spaces and join back to the text`() {
        val text = "Hold on, I'm  still here"
        val tokens = WordTiming.tokenize(text)
        assertEquals(listOf("Hold ", "on, ", "I'm  ", "still ", "here"), tokens)
        assertEquals(text, tokens.joinToString(""))
    }

    @Test
    fun `cjk splits per character`() {
        assertEquals(listOf("夜", "に", "駆", "け", "る"), WordTiming.tokenize("夜に駆ける"))
        assertEquals(listOf("君", "の ", "名"), WordTiming.tokenize("君の 名"))
    }

    @Test
    fun `syllable counts`() {
        assertEquals(1, WordTiming.syllables("love,"))
        assertEquals(1, WordTiming.syllables("the"))
        assertEquals(2, WordTiming.syllables("little"))
        assertEquals(3, WordTiming.syllables("beautiful"))
        assertEquals(1, WordTiming.syllables("nth"))
        assertEquals(0, WordTiming.syllables("—"))
        assertEquals(1, WordTiming.syllables("夜"))
    }

    @Test
    fun `estimated words fill the line in order, weighted by syllables`() {
        val line = WordTiming.complete(listOf(LyricLine(10_000, "I beautiful"), LyricLine(30_000, "next"))).first()
        assertTrue(line.isEstimated)
        val (short, long) = line.words
        // Contiguous, starting after a short lead-in.
        assertTrue(short.startMs in 10_001..10_120)
        assertEquals(short.endMs, long.startMs)
        assertEquals(line.endMs, long.endMs)
        // "beautiful" (3 syllables) takes three times as long as "I" (1).
        assertEquals(3.0, (long.endMs - long.startMs).toDouble() / (short.endMs - short.startMs), 0.05)
    }

    @Test
    fun `a short line before a long wait is capped, leaving a gap`() {
        val line = WordTiming.complete(listOf(LyricLine(0, "Yeah"), LyricLine(20_000, "next"))).first()
        assertTrue(line.endMs <= WordTiming.MIN_LINE_MS + 120)
    }

    @Test
    fun `a fast line ends by the time the next one starts`() {
        val line = WordTiming.complete(listOf(LyricLine(0, "one two three four five six seven"), LyricLine(1_500, "next"))).first()
        assertTrue(line.endMs <= 1_500)
        assertEquals(1_500L, line.endMs)
    }

    @Test
    fun `blank lines get no words`() {
        val line = WordTiming.complete(listOf(LyricLine(5_000, ""))).single()
        assertTrue(line.words.isEmpty())
        assertFalse(line.isEstimated)
    }

    @Test
    fun `lines repeated at the same time use the next later line as their end`() {
        val lines = WordTiming.complete(listOf(LyricLine(1_000, "a"), LyricLine(1_000, "b"), LyricLine(3_000, "c")))
        assertTrue(lines[0].endMs <= 3_000)
        assertTrue(lines[0].endMs > 1_000)
    }
}
