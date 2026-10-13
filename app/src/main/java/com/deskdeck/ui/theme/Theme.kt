package com.deskdeck.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

@Composable
fun DeskDeckTheme(
    accent: Color = DefaultAccent,
    content: @Composable () -> Unit,
) {
    val colorScheme = darkColorScheme(
        primary = accent,
        onPrimary = accent.withTone(0.12f),
        primaryContainer = accent.withTone(0.28f),
        onPrimaryContainer = accent.withTone(0.88f),
        secondaryContainer = SurfaceHighest,
        onSecondaryContainer = CappedWhite,
        background = TrueBlack,
        onBackground = CappedWhite,
        surface = TrueBlack,
        onSurface = CappedWhite,
        onSurfaceVariant = MutedText,
        surfaceContainerLowest = SurfaceLowest,
        surfaceContainerLow = SurfaceLow,
        surfaceContainer = SurfaceMid,
        surfaceContainerHigh = SurfaceHigh,
        surfaceContainerHighest = SurfaceHighest,
        outline = Outline,
        outlineVariant = SurfaceHighest,
    )
    MaterialTheme(colorScheme = colorScheme, typography = DeskDeckTypography) {
        // Nothing draws a Surface behind the screens (true black), so set the default content color
        // here; otherwise un-tinted icons and text fall back to black-on-black.
        CompositionLocalProvider(LocalContentColor provides CappedWhite, content = content)
    }
}
