package com.deskdeck.data.lyrics

import com.deskdeck.domain.LyricLine

/**
 * Parser for LRC synced lyrics.
 *
 * Handles:
 * - several timestamps on one line (`[00:12.00][01:30.50]Chorus`), one [LyricLine] per timestamp;
 * - `[mm:ss]`, `[mm:ss.x]`, `[mm:ss.xx]`, `[mm:ss.xxx]` and the `[mm:ss:xx]` variant, minutes > 59;
 * - the `[offset:±ms]` tag (positive = lyrics shown earlier, per the LRC convention);
 * - metadata tags (`[ar:…]`, `[ti:…]`, `[length:…]` …), which are ignored;
 * - enhanced/word-level `<mm:ss.xx>` stamps inside the text, which are stripped;
 * - blank text, kept as an instrumental-break marker.
 * Lines without a valid timestamp are skipped. The result is sorted by time (stable).
 */
object LrcParser {

    private val TIMESTAMP = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val LEADING_TAG = Regex("""^\[([a-zA-Z#]+):([^\]]*)]""")
    /** U+FEFF, written as a char code so no invisible character lives in the source. */
    private val BYTE_ORDER_MARK = 0xFEFF.toChar().toString()
    private val WORD_STAMP = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

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

            val text = rest.replace(WORD_STAMP, "").replace(Regex("\\s+"), " ").trim()
            times.forEach { lines += LyricLine(it, text) }
        }

        return lines
            .map { it.copy(timeMs = (it.timeMs - offsetMs).coerceAtLeast(0)) }
            .sortedBy { it.timeMs }
    }

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
