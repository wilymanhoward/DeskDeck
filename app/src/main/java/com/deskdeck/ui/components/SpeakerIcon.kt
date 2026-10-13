package com.deskdeck.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.deskdeck.domain.SpeakerLevel
import com.deskdeck.ui.theme.LocalMotion

/**
 * Outline speaker drawn to match the icon set (24-unit grid, 1.6 stroke). Instead of swapping
 * glyphs, each sound wave fades and grows in on its own spring and the mute slash draws itself
 * across, so the icon morphs between [SpeakerLevel]s.
 */
@Composable
fun SpeakerIcon(level: SpeakerLevel, color: Color, modifier: Modifier = Modifier, fillColor: Color = color, fill: Float = 0f) {
    val motion = LocalMotion.current
    val waves = (1..3).map { i ->
        animateFloatAsState(if (level.waves >= i) 1f else 0f, motion.snappy(), label = "speakerWave$i")
    }
    val slash by animateFloatAsState(if (level == SpeakerLevel.MUTE) 1f else 0f, motion.snappy(), label = "speakerSlash")

    Canvas(modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(width = 1.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // [fill] is how far the HUD's level fill reaches up this icon (0..1): there it turns dark.
        val tint = lerp(color, fillColor, fill.coerceIn(0f, 1f))
        val body = Path().apply {
            moveTo(3f * u, 9.5f * u)
            lineTo(6.5f * u, 9.5f * u)
            lineTo(11f * u, 5.5f * u)
            lineTo(11f * u, 18.5f * u)
            lineTo(6.5f * u, 14.5f * u)
            lineTo(3f * u, 14.5f * u)
            close()
        }
        drawPath(body, tint, style = stroke)

        val center = Offset(11f * u, 12f * u)
        listOf(3.5f, 6.5f, 9.5f).forEachIndexed { i, radius ->
            val t = waves[i].value
            if (t <= 0.01f) return@forEachIndexed
            // Waves grow outward from the speaker as they appear.
            val r = radius * u * (0.7f + 0.3f * t)
            val sweep = 90f * (0.6f + 0.4f * t)
            drawArc(
                color = tint.copy(alpha = tint.alpha * t),
                startAngle = -sweep / 2,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - r, center.y - r),
                size = Size(2 * r, 2 * r),
                style = stroke,
            )
        }

        if (slash > 0.01f) {
            val start = Offset(14.5f * u, 9f * u)
            val end = Offset(20.5f * u, 15f * u)
            drawLine(tint, start, start + (end - start) * slash, stroke.width, StrokeCap.Round)
            val start2 = Offset(20.5f * u, 9f * u)
            val end2 = Offset(14.5f * u, 15f * u)
            drawLine(tint, start2, start2 + (end2 - start2) * slash, stroke.width, StrokeCap.Round)
        }
    }
}
