package com.deskdeck.domain

/** Everything the dashboard needs about the session that is currently shown. */
data class NowPlaying(
    val track: Track,
    val playback: PlaybackInfo,
    /** Package of the app that owns the media session, e.g. `com.spotify.music`. */
    val sourcePackage: String,
    val sourceLabel: String,
    /** Any model the image loader understands (Bitmap, Uri, URL). Null means "no art". */
    val artwork: Any? = null,
    val colors: ArtworkColors? = null,
)
