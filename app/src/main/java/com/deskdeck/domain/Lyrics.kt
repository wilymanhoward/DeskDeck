package com.deskdeck.domain

data class LyricLine(
    val timeMs: Long,
    val text: String,
)

sealed interface Lyrics {
    /** Lines sorted by [LyricLine.timeMs]. */
    data class Synced(val lines: List<LyricLine>) : Lyrics
    data class Plain(val text: String) : Lyrics
    data object Instrumental : Lyrics
}

sealed interface LyricsResult {
    data class Found(val lyrics: Lyrics, val source: String) : LyricsResult
    data object NotFound : LyricsResult
    data class Error(val message: String?) : LyricsResult
}

/**
 * Index of the line that is active at [positionMs]: the last line whose timestamp is at or before
 * the position, or -1 before the first line. Expects lines sorted by time; uses binary search
 * because it runs on every frame.
 */
fun List<LyricLine>.activeIndexAt(positionMs: Long): Int {
    var low = 0
    var high = size - 1
    var result = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (this[mid].timeMs <= positionMs) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}
