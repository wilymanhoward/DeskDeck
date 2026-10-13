package com.deskdeck.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

val TrueBlack = Color(0xFF000000)

/**
 * Text and icons that stay on screen for hours are capped below pure white (~85%) to slow
 * AMOLED wear. Only the active lyric line, which keeps moving, goes brighter.
 */
val CappedWhite = Color(0xFFD9D9D9)
val ActiveLyricWhite = Color(0xFFF0F0F0)
val MutedText = Color(0xFF9AA0A6)

// Tonal surfaces on black, in the spirit of Android Auto's "Coolwalk" cards.
val SurfaceLowest = Color(0xFF0A0B0D)
val SurfaceLow = Color(0xFF111215)
val SurfaceMid = Color(0xFF17191C)
val SurfaceHigh = Color(0xFF1F2125)
val SurfaceHighest = Color(0xFF292C31)
val Outline = Color(0xFF3A3D43)

val DefaultAccent = Color(0xFF8AB4F8)

/**
 * Returns this color with its HSL lightness replaced and saturation capped. Used to turn an
 * arbitrary album-art color into a predictable tone (dark card backgrounds, light buttons) so
 * contrast doesn't depend on the artwork.
 */
fun Color.withTone(lightness: Float, maxSaturation: Float = 0.55f): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = hsl[1].coerceAtMost(maxSaturation)
    hsl[2] = lightness.coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}
