package com.deskdeck.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Sized for reading at arm's length on a 11"+ tablet. All sizes are sp so system font scaling applies.
val DeskDeckTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.Light, fontSize = 88.sp, lineHeight = 96.sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 38.sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
        bodyLarge = bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
        labelLarge = labelLarge.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    )
}

val LyricTextStyle = TextStyle(
    fontSize = 30.sp,
    lineHeight = 40.sp,
    fontWeight = FontWeight.SemiBold,
)
