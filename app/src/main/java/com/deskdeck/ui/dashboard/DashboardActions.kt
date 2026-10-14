package com.deskdeck.ui.dashboard

import com.deskdeck.domain.RailApp

/** Callbacks for transport controls. Defaults are no-ops so previews can pass `object : PlayerActions {}`. */
interface PlayerActions {
    fun onPlayPause() {}
    fun onSkipNext() {}
    fun onSkipPrevious() {}
    fun onSeek(positionMs: Long) {}
    fun onToggleShuffle() {}
    fun onCycleRepeat() {}
}

interface DashboardActions : PlayerActions {
    fun onRetryLyrics() {}
    /** [deltaMs] > 0 shows lyrics earlier. */
    fun onAdjustLyricsOffset(deltaMs: Long) {}
    fun onResetLyricsOffset() {}
    /** Selects the app as the shown source. Never launches it. */
    fun onRailAppClick(app: RailApp) {}

    /** Opens the action sheet; [anchorY] is the tile's vertical center in root coordinates. */
    fun onRailAppLongClick(app: RailApp, anchorY: Float) {}
    fun onPinSource(packageName: String) {}
    fun onUnpinSource() {}
    fun onRemoveFromRail(packageName: String) {}

    /** The only ways DeskDeck leaves the screen, always from an explicit button. */
    fun onOpenApp(packageName: String) {}
    fun onGetApp(packageName: String) {}
    fun onAddRailApp() {}
    fun onAllApps() {}
    fun onSettings() {}

    /** Opens the notification panel (the bell). */
    fun onOpenNotifications() {}

    /** Long-press on the rail clock: start or end Night Mode now. */
    fun onToggleNight() {}

    /** The Now Playing card was dragged to the other side (lyrics take the old side). */
    fun onMoveNowPlaying(toRight: Boolean) {}

    /** Opens the Spotify library (rail long-press on Spotify, or the card's Library button). */
    fun onOpenLibrary() {}
}
