package com.deskdeck.domain

/** What kind of item the player reports. Lyrics are only looked up for [MUSIC]. */
enum class MediaKind { MUSIC, AD, PODCAST, LIVE }

data class Track(
    /** Stable identity for "is this the same track?" checks (lyrics lookup, art crossfade). */
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    /** Null or non-positive when the player doesn't report a duration (live streams, some videos). */
    val durationMs: Long?,
    val kind: MediaKind = MediaKind.MUSIC,
) {
    val hasDuration: Boolean get() = durationMs != null && durationMs > 0
}

/** Colors sampled from album art, as ARGB ints so the domain layer stays free of Compose. */
data class ArtworkColors(
    val primary: Int,
    val secondary: Int,
)
