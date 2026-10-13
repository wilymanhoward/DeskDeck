package com.deskdeck.ui.settings

import androidx.compose.runtime.staticCompositionLocalOf
import com.deskdeck.data.settings.AppSettings

/**
 * Display preferences for leaf composables (clock format, lyric size, romanization …), provided
 * once at the app root so they don't have to be threaded through every screen's parameters.
 * Defaults keep previews working.
 */
val LocalAppSettings = staticCompositionLocalOf { AppSettings() }
