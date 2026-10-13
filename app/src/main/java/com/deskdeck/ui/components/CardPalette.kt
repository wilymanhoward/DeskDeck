package com.deskdeck.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.deskdeck.domain.ArtworkColors
import com.deskdeck.ui.theme.DefaultAccent
import com.deskdeck.ui.theme.SurfaceHigh
import com.deskdeck.ui.theme.SurfaceLow
import com.deskdeck.ui.theme.withTone

/** Colors for the now-playing card, derived from album art with fixed tones so contrast is predictable. */
@Immutable
data class CardPalette(
    val top: Color,
    val bottom: Color,
    val accent: Color,
    val onAccent: Color,
) {
    companion object {
        val Default = CardPalette(
            top = SurfaceHigh,
            bottom = SurfaceLow,
            accent = DefaultAccent,
            onAccent = DefaultAccent.withTone(0.12f),
        )

        fun from(colors: ArtworkColors?): CardPalette {
            if (colors == null) return Default
            val primary = Color(colors.primary)
            val secondary = Color(colors.secondary)
            return CardPalette(
                top = primary.withTone(0.22f),
                bottom = secondary.withTone(0.08f),
                accent = primary.withTone(0.80f, maxSaturation = 0.65f),
                onAccent = primary.withTone(0.12f),
            )
        }
    }
}

/** [CardPalette] for [colors], animating smoothly from the previous track's palette. */
@Composable
fun animatedCardPalette(colors: ArtworkColors?): CardPalette {
    val target = remember(colors) { CardPalette.from(colors) }
    val spec = tween<Color>(durationMillis = 900)
    return CardPalette(
        top = animateColorAsState(target.top, spec, label = "paletteTop").value,
        bottom = animateColorAsState(target.bottom, spec, label = "paletteBottom").value,
        accent = animateColorAsState(target.accent, spec, label = "paletteAccent").value,
        onAccent = animateColorAsState(target.onAccent, spec, label = "paletteOnAccent").value,
    )
}
