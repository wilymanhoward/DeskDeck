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
    fun onRailAppClick(app: RailApp) {}
    fun onRailAppLongClick(app: RailApp) {}
    fun onAddRailApp() {}
    fun onAllApps() {}
    fun onSettings() {}
}
