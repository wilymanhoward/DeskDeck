package com.deskdeck.data.lyrics

import com.deskdeck.domain.LyricLine
import com.deskdeck.domain.LyricWord

/**
 * Completes line and word timings. Lines that came with word stamps (enhanced LRC) keep them;
 * every other line gets estimated words: its text is split into words (characters for CJK), and
 * the time it is sung is shared out in proportion to each word's syllables.
 *
 * How long a line is sung: from its timestamp plus a short lead-in, until the next line starts,
 * but capped at roughly [MS_PER_SYLLABLE] per syllable. The cap keeps a short line before a long
 * solo from crawling across the whole solo; the rest of the wait becomes an instrumental gap.
 */
object WordTiming {

    /** Generous for ballads; fast songs are limited by the next line's start anyway. */
    const val MS_PER_SYLLABLE = 550L
    const val MIN_LINE_MS = 1_200L
    /** How long the last line is assumed to last when nothing follows it. */
    const val LAST_LINE_MS = 6_000L
    private const val MAX_LEAD_IN_MS = 120L

    fun complete(lines: List<LyricLine>): List<LyricLine> = lines.mapIndexed { i, line ->
        val nextStart = lines.drop(i + 1).firstOrNull { it.startMs > line.startMs }?.startMs
        val available = (nextStart ?: (line.startMs + LAST_LINE_MS)) - line.startMs
        when {
            line.text.isBlank() -> line.copy(endMs = line.startMs, words = emptyList())
            line.words.isNotEmpty() -> completeGiven(line, nextStart)
            else -> estimate(line, available)
        }
    }

    /** Word stamps from the source; only a missing end on the last word is filled in. */
    private fun completeGiven(line: LyricLine, nextStart: Long?): LyricLine {
        val words = line.words.mapIndexed { i, w ->
            if (w.endMs > w.startMs) return@mapIndexed w
            val fallback = line.words.getOrNull(i + 1)?.startMs
                ?: minOf(nextStart ?: Long.MAX_VALUE, w.startMs + syllables(w.text) * MS_PER_SYLLABLE)
            w.copy(endMs = fallback.coerceAtLeast(w.startMs + 1))
        }
        return line.copy(endMs = words.last().endMs, words = words, isEstimated = false)
    }

    internal fun estimate(line: LyricLine, availableMs: Long): LyricLine {
        val tokens = tokenize(line.text)
        val weights = tokens.map { syllables(it).toLong() }
        val total = weights.sum().coerceAtLeast(1)
        val leadIn = minOf(MAX_LEAD_IN_MS, availableMs / 10)
        val natural = maxOf(total * MS_PER_SYLLABLE, MIN_LINE_MS)
        val span = minOf(availableMs - leadIn, natural).coerceAtLeast(1)

        var cursor = line.startMs + leadIn
        var acc = 0L
        val words = tokens.mapIndexed { i, token ->
            acc += weights[i]
            // Cumulative rounding so the last word ends exactly at the end of the span.
            val end = line.startMs + leadIn + span * acc / total
            LyricWord(cursor, end, token).also { cursor = end }
        }
        return line.copy(endMs = words.lastOrNull()?.endMs ?: line.startMs, words = words, isEstimated = true)
    }

    /**
     * Splits [text] into timing units that concatenate back to it: words with their trailing
     * space, and single characters for CJK scripts, which don't separate words with spaces.
     */
    fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty()) tokens += current.toString().also { current.clear() }
        }
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val chars = String(Character.toChars(cp))
            when {
                // Spaces stay attached to whatever comes before them.
                Character.isWhitespace(cp) ->
                    if (current.isEmpty() && tokens.isNotEmpty()) tokens[tokens.lastIndex] += chars else current.append(chars)
                isCjk(cp) -> {
                    flush()
                    tokens += chars
                }
                else -> {
                    // A letter after a space starts the next word.
                    if (current.isNotEmpty() && current.last().isWhitespace()) flush()
                    current.append(chars)
                }
            }
            i += chars.length
        }
        flush()
        return tokens
    }

    /** Rough sung-syllable count: vowel groups for alphabetic words, 1 per CJK character. */
    fun syllables(token: String): Int {
        // Punctuation isn't sung: "love," is "love".
        val word = token.lowercase().trim { !it.isLetterOrDigit() }
        if (word.isEmpty()) return 0
        if (word.codePoints().anyMatch(::isCjk)) return 1
        var groups = 0
        var inVowel = false
        for (c in word) {
            val vowel = c in VOWELS
            if (vowel && !inVowel) groups++
            inVowel = vowel
        }
        // A silent final "e" ("love", "time") isn't sung, unless it's the only vowel ("the").
        if (groups > 1 && word.endsWith("e") && !word.endsWith("le") && word[word.length - 2] !in VOWELS) groups--
        val letters = word.count { it.isLetter() }
        return if (groups > 0) groups else maxOf(1, letters / 3)
    }

    private const val VOWELS = "aeiouyàáâãäåæèéêëìíîïòóôõöøùúûüýÿœаеёиоуыэюяіїє"

    private fun isCjk(cp: Int): Boolean {
        val block = Character.UnicodeScript.of(cp)
        return block == Character.UnicodeScript.HAN ||
            block == Character.UnicodeScript.HIRAGANA ||
            block == Character.UnicodeScript.KATAKANA
    }
}
