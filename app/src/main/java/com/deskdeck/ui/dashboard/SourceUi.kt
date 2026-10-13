package com.deskdeck.ui.dashboard

/** What the rail needs to draw each app's state. */
data class RailState(
    /** Gets the selection ring: the chosen source, or whatever is shown while following. */
    val highlightPackage: String? = null,
    /** Shows the equalizer: the app that is playing in the card. */
    val playingPackage: String? = null,
    /** Apps with a media session; installed apps outside this set read "no media session yet". */
    val sessionPackages: Set<String> = emptySet(),
    /** The pinned source, if any (stays selected when other apps start playing). */
    val pinnedPackage: String? = null,
)

/** The selected app has nothing to show; the card explains why and offers the explicit way out. */
data class EmptySourceUi(
    val packageName: String,
    val label: String,
    val reason: Reason,
) {
    enum class Reason {
        /** Trying to get it playing in the background: "Starting Spotify…". */
        STARTING,

        /** Nothing worked: "Start playing from Spotify, then it appears here" + "Open Spotify". */
        NO_SESSION,

        /** "Spotify isn't installed" + "Get it on Google Play". */
        NOT_INSTALLED,
    }
}
