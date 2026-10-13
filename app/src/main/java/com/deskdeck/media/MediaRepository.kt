package com.deskdeck.media

import com.deskdeck.domain.NowPlaying
import kotlinx.coroutines.flow.StateFlow

/** The media sessions of other apps: the one currently shown, source selection, and commands. */
interface MediaRepository {
    /** Null when nothing is shown (no session, or the selected app has none yet). */
    val nowPlaying: StateFlow<NowPlaying?>

    /** The app chosen in the rail, or null while following whatever plays. */
    val selection: StateFlow<SourceSelection?>

    /** Packages that currently have a media session with something to show. */
    val sessionPackages: StateFlow<Set<String>>

    /** True when any app is playing, shown or not (burn-in uses this, not just the shown session). */
    val anyPlaying: StateFlow<Boolean>

    /** Shows [packageName]'s session (or its empty state) from now on. Never launches the app. */
    fun select(packageName: String, pinned: Boolean)

    fun setPinned(pinned: Boolean)

    /**
     * Sends a MEDIA_PLAY key to [packageName]'s session, if it has one.
     * @return false when the app has no session to send it to.
     */
    fun requestPlay(packageName: String): Boolean

    /**
     * Asks [packageName]'s session to load its queue *without* playing (`prepare`), so a player
     * started in the background shows its last song, as if opened by hand.
     * @return false when the app has no session yet.
     */
    fun requestPrepare(packageName: String): Boolean

    fun playPause()
    fun skipNext()
    fun skipPrevious()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeat()
}
