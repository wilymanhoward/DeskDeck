package com.deskdeck.domain

import android.graphics.Bitmap

/** Everything the dashboard needs about the session that is currently shown. */
data class NowPlaying(
    val track: Track,
    val playback: PlaybackInfo,
    /** Package of the app that owns the media session, e.g. `com.spotify.music`. */
    val sourcePackage: String,
    val sourceLabel: String,
    /** Null when the player sent no art (or it couldn't be loaded); the UI shows a placeholder. */
    val artwork: Bitmap? = null,
    val colors: ArtworkColors? = null,
)
