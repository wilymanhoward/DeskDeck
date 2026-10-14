package com.deskdeck.ui.adaptive

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.window.core.layout.WindowSizeClass

/**
 * How much room DeskDeck has, by the window's size class (not by device):
 * - [EXPANDED]: the whole tablet, or a wide split: rail | Now Playing | lyrics.
 * - [MEDIUM]: half the tablet in split screen: rail, Now Playing, a few lyric lines.
 * - [COMPACT]: a narrow split or a pop-up window: the mini player only.
 */
enum class DeskSize { COMPACT, MEDIUM, EXPANDED }

/** By width with Material's breakpoints (600 / 840 dp); a short window (under 480 dp) is compact too. */
fun WindowSizeClass.toDeskSize(): DeskSize = when {
    !isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) -> DeskSize.COMPACT
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> DeskSize.EXPANDED
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> DeskSize.MEDIUM
    else -> DeskSize.COMPACT
}

/** The current window's [DeskSize]; follows split-screen and pop-up resizes live. */
@Composable
fun currentDeskSize(): DeskSize = currentWindowAdaptiveInfo().windowSizeClass.toDeskSize()

/** Provided once at the root; previews set it directly. */
val LocalDeskSize = staticCompositionLocalOf { DeskSize.EXPANDED }
