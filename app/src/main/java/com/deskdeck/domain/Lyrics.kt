package com.deskdeck.domain

/**
 * One timed unit of a line: a word, or a syllable/character where the source or script splits
 * finer. Concatenating a line's [LyricLine.words] gives its [LyricLine.text], spaces included.
 */
data class LyricWord(
    val startMs: Long,
    val endMs: Long,
    val text: String,
) {
    /** 0 before the word, 1 after it, linear in between. */
    fun progressAt(positionMs: Long): Float = when {
        positionMs <= startMs -> 0f
        positionMs >= endMs -> 1f
        else -> (positionMs - startMs).toFloat() / (endMs - startMs)
    }
}

data class LyricLine(
    val startMs: Long,
    val text: String,
    /** When the last word ends; equals [startMs] until timings are completed. */
    val endMs: Long = startMs,
    val words: List<LyricWord> = emptyList(),
    /** True when [words] were estimated rather than given by the source (enhanced LRC). */
    val isEstimated: Boolean = false,
)

sealed interface Lyrics {
    /** Lines sorted by [LyricLine.startMs]. */
    data class Synced(val lines: List<LyricLine>) : Lyrics
    data class Plain(val text: String) : Lyrics
    data object Instrumental : Lyrics
}

sealed interface LyricsResult {
    data class Found(val lyrics: Lyrics, val source: String) : LyricsResult
    data object NotFound : LyricsResult
    data class Error(val message: String?) : LyricsResult
}

/** What the synced lyrics view shows, one entry per row: a sung line or an instrumental gap. */
sealed interface LyricRow {
    val startMs: Long
    val endMs: Long

    data class Line(val lineIndex: Int, val line: LyricLine) : LyricRow {
        override val startMs get() = line.startMs
        override val endMs get() = line.endMs
    }

    /** No singing for a while (an intro, a solo, or an empty LRC line): breathing dots. */
    data class Gap(override val startMs: Long, override val endMs: Long) : LyricRow {
        fun progressAt(positionMs: Long): Float =
            ((positionMs - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
    }
}

/** Gaps shorter than this between one line's end and the next line's start stay plain silence. */
const val INSTRUMENTAL_GAP_MS = 4_000L

/**
 * Turns timed lines into rows: an intro gap before a late first line, a gap wherever a line ends
 * more than [gapMs] before the next starts, and empty LRC lines (instrumental markers) as gaps
 * lasting until the next line.
 */
fun buildLyricRows(lines: List<LyricLine>, gapMs: Long = INSTRUMENTAL_GAP_MS): List<LyricRow> {
    val rows = mutableListOf<LyricRow>()
    val first = lines.firstOrNull() ?: return rows
    if (first.startMs > gapMs) rows += LyricRow.Gap(0, first.startMs)
    lines.forEachIndexed { i, line ->
        val nextStart = lines.getOrNull(i + 1)?.startMs
        if (line.text.isBlank()) {
            // An explicit break: dots until the next line (or a few seconds at the very end).
            val end = nextStart ?: (line.startMs + gapMs)
            val previous = rows.lastOrNull()
            when {
                end <= line.startMs -> Unit
                // Several empty lines in a row are one break.
                previous is LyricRow.Gap -> rows[rows.lastIndex] = LyricRow.Gap(previous.startMs, end)
                else -> rows += LyricRow.Gap(line.startMs, end)
            }
            return@forEachIndexed
        }
        rows += LyricRow.Line(i, line)
        if (nextStart != null && nextStart - line.endMs > gapMs && lines[i + 1].text.isNotBlank()) {
            rows += LyricRow.Gap(line.endMs, nextStart)
        }
    }
    return rows
}

/**
 * Index of the line that is active at [positionMs]: the last line whose timestamp is at or before
 * the position, or -1 before the first line. Expects lines sorted by time; uses binary search
 * because it runs on every frame.
 */
fun List<LyricLine>.activeIndexAt(positionMs: Long): Int = lastIndexAtOrBefore(positionMs) { it.startMs }

/** Same as [activeIndexAt], for rows. */
fun List<LyricRow>.activeRowAt(positionMs: Long): Int = lastIndexAtOrBefore(positionMs) { it.startMs }

private inline fun <T> List<T>.lastIndexAtOrBefore(positionMs: Long, start: (T) -> Long): Int {
    var low = 0
    var high = size - 1
    var result = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (start(this[mid]) <= positionMs) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}
