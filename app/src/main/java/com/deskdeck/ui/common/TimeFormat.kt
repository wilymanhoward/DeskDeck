package com.deskdeck.ui.common

/** Formats a playback position as `m:ss`, or `h:mm:ss` from one hour. Negative values show as 0:00. */
fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    fun Long.twoDigits() = toString().padStart(2, '0')
    return if (hours > 0) {
        "$hours:${minutes.twoDigits()}:${seconds.twoDigits()}"
    } else {
        "$minutes:${seconds.twoDigits()}"
    }
}
