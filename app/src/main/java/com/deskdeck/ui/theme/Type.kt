package com.deskdeck.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.deskdeck.R

/**
 * Inter (variable), the closest freely licensed match for SF Pro, which can't be bundled on
 * Android. One file covers every weight through the `wght` axis. SIL OFL: see
 * assets/licenses/Inter-OFL.txt.
 */
@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    resId = R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Inter = FontFamily(inter(300), inter(400), inter(500), inter(600), inter(700))

/** Tighter tracking for large text, as Apple does; body text keeps Inter's default. */
private val TightLarge = (-0.025).em
private val TightTitle = (-0.015).em

// Sized for reading at arm's length on a 11"+ tablet. All sizes are sp so system font scaling applies.
val DeskDeckTypography = Typography().run {
    fun TextStyle.inter() = copy(fontFamily = Inter)
    copy(
        displayLarge = displayLarge.inter().copy(fontWeight = FontWeight.W300, fontSize = 88.sp, lineHeight = 96.sp, letterSpacing = TightLarge),
        displayMedium = displayMedium.inter().copy(letterSpacing = TightLarge),
        displaySmall = displaySmall.inter().copy(letterSpacing = TightLarge),
        headlineLarge = headlineLarge.inter().copy(fontWeight = FontWeight.W700, letterSpacing = TightLarge),
        headlineMedium = headlineMedium.inter().copy(fontWeight = FontWeight.W700, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = TightLarge),
        headlineSmall = headlineSmall.inter().copy(fontWeight = FontWeight.W600, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = TightTitle),
        titleLarge = titleLarge.inter().copy(fontWeight = FontWeight.W600, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = TightTitle),
        titleMedium = titleMedium.inter().copy(fontWeight = FontWeight.W500, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = TightTitle),
        titleSmall = titleSmall.inter().copy(fontWeight = FontWeight.W500),
        bodyLarge = bodyLarge.inter().copy(fontSize = 18.sp, lineHeight = 26.sp),
        bodyMedium = bodyMedium.inter(),
        bodySmall = bodySmall.inter(),
        labelLarge = labelLarge.inter().copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.W500),
        labelMedium = labelMedium.inter(),
        labelSmall = labelSmall.inter(),
    )
}

/** For times and counters: tabular figures so digits don't shift as they change. */
const val TabularNumbers = "tnum"

val LyricTextStyle = TextStyle(
    fontFamily = Inter,
    fontSize = 30.sp,
    lineHeight = 40.sp,
    fontWeight = FontWeight.W800,
    letterSpacing = TightTitle,
)
