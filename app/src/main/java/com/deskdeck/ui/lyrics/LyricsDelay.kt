package com.deskdeck.ui.lyrics

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How many ms the lyrics lag the player's reported position: the audio output latency (when
 * compensation is on) plus the global fine-tune. Lyric time = position + per-track offset − this.
 */
val LocalLyricsDelay = staticCompositionLocalOf { 0L }
