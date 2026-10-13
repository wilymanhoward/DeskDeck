package com.deskdeck.data.lyrics

import com.deskdeck.domain.LyricLine
import com.deskdeck.domain.LyricWord

/**
 * Parser for LRC synced lyrics.
 *
 * Handles:
 * - several timestamps on one line (`[00:12.00][01:30.50]Chorus`), one [LyricLine] per timestamp;
 * - `[mm:ss]`, `[mm:ss.x]`, `[mm:ss.xx]`, `[mm:ss.xxx]` and the `[mm:ss:xx]` variant, minutes > 59;
 * - the `[offset:±ms]` tag (positive = lyrics shown earlier, per the LRC convention);
 * - metadata tags (`[ar:…]`, `[ti:…]`, `[length:…]` …), which are ignored;
 * - enhanced (A2) word stamps, `[00:12.00]<00:12.00>Hello <00:12.40>world<00:13.10>`: each stamp
 *   starts the text after it and the next stamp ends it; they become [LyricLine.words];
 * - blank text, kept as an instrumental-break marker.
 * Lines without a valid timestamp are skipped. The result is sorted by time (stable). Line ends
 * and estimated words are added afterwards by [WordTiming].
 */
object LrcParser {

    private val TIMESTAMP = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val LEADING_TAG = Regex("""^\[([a-zA-Z#]+):([^\]]*)]""")
    /** U+FEFF, written as a char code so no invisible character lives in the source. */
    private val BYTE_ORDER_MARK = 0xFEFF.toChar().toString()
    private val WORD_STAMP = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val WHITESPACE = Regex("""\s+""")

    fun parse(lrc: String): List<LyricLine> {
        var offsetMs = 0L
        val lines = mutableListOf<LyricLine>()

        for (rawLine in lrc.lineSequence()) {
            val line = rawLine.trim().removePrefix(BYTE_ORDER_MARK)
            if (line.isEmpty()) continue

            val tag = LEADING_TAG.find(line)
            if (tag != null) {
                if (tag.groupValues[1].equals("offset", ignoreCase = true)) {
                    offsetMs = tag.groupValues[2].trim().toLongOrNull() ?: offsetMs
                }
                continue
            }

            // Collect every timestamp at the start of the line, e.g. "[00:12.00][01:30.50]text".
            val times = mutableListOf<Long>()
            var rest = line
            while (true) {
                val match = TIMESTAMP.matchAt(rest, 0) ?: break
                times += match.toMillis()
                rest = rest.substring(match.range.last + 1)
            }
            if (times.isEmpty()) continue

            val words = parseWords(rest)
            if (words != null) {
                // Word stamps are absolute; a line repeated under several timestamps shifts them.
                val text = words.joinToString("") { it.text }
                times.forEach { t -> lines += LyricLine(t, text, words = words.map { it.shiftedBy(t - times.first()) }) }
            } else {
                // Stamps with no words between them leave nothing to time.
                val text = rest.replace(WORD_STAMP, "").replace(WHITESPACE, " ").trim()
                times.forEach { lines += LyricLine(it, text) }
            }
        }

        return lines
            .map { line ->
                line.copy(
                    startMs = (line.startMs - offsetMs).coerceAtLeast(0),
                    endMs = (line.endMs - offsetMs).coerceAtLeast(0),
                    words = line.words.map { it.shiftedBy(-offsetMs) },
                )
            }
            .sortedBy { it.startMs }
    }

    /** Timed words from enhanced-LRC text, or null when it has no word stamps. */
    private fun parseWords(raw: String): List<LyricWord>? {
        val stamps = WORD_STAMP.findAll(raw).toList()
        if (stamps.isEmpty()) return null
        val words = mutableListOf<LyricWord>()
        // Text before the first stamp (unusual) is sung with the first word.
        var carry = raw.substring(0, stamps.first().range.first)
        stamps.forEachIndexed { k, stamp ->
            val segmentEnd = stamps.getOrNull(k + 1)?.range?.first ?: raw.length
            var segment = (carry + raw.substring(stamp.range.last + 1, segmentEnd)).replace(WHITESPACE, " ")
            carry = ""
            if (segment.isBlank()) {
                // An end marker, or a space between two stamped words: keep the space.
                val last = words.lastOrNull()
                if (segment.isNotEmpty() && last != null && !last.text.endsWith(" ")) {
                    words[words.lastIndex] = last.copy(text = last.text + " ")
                }
                return@forEachIndexed
            }
            if (words.isEmpty() || words.last().text.endsWith(" ")) segment = segment.trimStart()
            val start = stamp.toMillis()
            // No stamp after the last word: its end is unknown (= start) until WordTiming fills it.
            val end = stamps.getOrNull(k + 1)?.toMillis() ?: start
            words += LyricWord(start, end, segment)
        }
        if (words.isEmpty()) return null
        words[words.lastIndex] = words.last().copy(text = words.last().text.trimEnd())
        return words
    }

    private fun LyricWord.shiftedBy(deltaMs: Long) =
        copy(startMs = (startMs + deltaMs).coerceAtLeast(0), endMs = (endMs + deltaMs).coerceAtLeast(0))

    private fun MatchResult.toMillis(): Long {
        val minutes = groupValues[1].toLong()
        val seconds = groupValues[2].toLong()
        val fraction = groupValues[3]
        // ".5" is 500 ms, ".50" is 500 ms, ".500" is 500 ms.
        val fractionMs = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            else -> fraction.toLong()
        }
        return (minutes * 60 + seconds) * 1000 + fractionMs
    }
}
