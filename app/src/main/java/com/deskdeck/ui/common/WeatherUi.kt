package com.deskdeck.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.deskdeck.R
import com.deskdeck.domain.brief.AirQualityLevel
import com.deskdeck.domain.brief.WeatherCondition

/**
 * A small drawn weather icon (no icon pack): sun, clouds, rain, snow, storm. In color for the
 * Morning Brief, or in one [tint] for the night screen.
 */
@Composable
fun WeatherGlyph(condition: WeatherCondition, modifier: Modifier, tint: Color? = null) {
    Canvas(modifier) {
        val sun = tint ?: Color(0xFFFFD60A)
        val cloud = tint ?: Color(0xFFD1D1D6)
        val drop = tint ?: Color(0xFF64D2FF)
        val stroke = size.minDimension * 0.05f
        when (condition) {
            WeatherCondition.CLEAR -> drawSun(center, size.minDimension * 0.2f, sun, stroke)
            WeatherCondition.PARTLY_CLOUDY -> {
                val cloudShape = cloudPath(dy = 0.1f)
                // The sun peeks out from behind: cut around the cloud, never drawn under it.
                clipPath(cloudShape.grown(stroke * 1.4f), ClipOp.Difference) {
                    drawSun(Offset(size.width * 0.36f, size.height * 0.34f), size.minDimension * 0.14f, sun, stroke)
                }
                drawPath(cloudShape, cloud)
            }
            WeatherCondition.CLOUDY, WeatherCondition.FOG -> drawPath(cloudPath(dy = 0f), cloud)
            WeatherCondition.DRIZZLE, WeatherCondition.RAIN, WeatherCondition.SHOWERS -> {
                drawPath(cloudPath(dy = -0.12f), cloud)
                val n = if (condition == WeatherCondition.DRIZZLE) 2 else 3
                repeat(n) { i ->
                    val x = size.width * (if (n == 2) 0.42f + i * 0.16f else 0.34f + i * 0.16f)
                    drawLine(drop, Offset(x, size.height * 0.74f), Offset(x - size.width * 0.05f, size.height * 0.9f), stroke, StrokeCap.Round)
                }
            }
            WeatherCondition.SNOW -> {
                drawPath(cloudPath(dy = -0.12f), cloud)
                repeat(3) { i -> drawCircle(tint ?: Color.White, stroke * 0.9f, Offset(size.width * (0.34f + i * 0.16f), size.height * 0.82f)) }
            }
            WeatherCondition.THUNDERSTORM -> {
                val cloudShape = cloudPath(dy = -0.12f)
                drawPath(cloudShape, cloud)
                val bolt = Path().apply {
                    moveTo(size.width * 0.55f, size.height * 0.6f)
                    lineTo(size.width * 0.43f, size.height * 0.79f)
                    lineTo(size.width * 0.53f, size.height * 0.79f)
                    lineTo(size.width * 0.46f, size.height * 0.96f)
                }
                clipPath(cloudShape.grown(stroke), ClipOp.Difference) {
                    drawPath(bolt, sun, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

/**
 * Apple's cloud as one outline: a small bump on the left, a bigger one on the right, and a flat
 * bottom with round ends. One path, so a translucent color fills it evenly (stacked circles would
 * show their overlaps darker). [dy] moves it up (−) or down as a fraction of the height.
 */
private fun DrawScope.cloudPath(dy: Float): Path {
    val w = size.width
    val h = size.height
    fun y(f: Float) = h * (f + dy)
    val base = Path().apply {
        addRoundRect(RoundRect(left = w * 0.14f, top = y(0.5f), right = w * 0.86f, bottom = y(0.74f), cornerRadius = CornerRadius(h * 0.12f)))
    }
    val left = Path().apply { addOval(Rect(center = Offset(w * 0.34f, y(0.52f)), radius = w * 0.15f)) }
    val right = Path().apply { addOval(Rect(center = Offset(w * 0.57f, y(0.43f)), radius = w * 0.22f)) }
    val out = Path()
    out.op(base, left, PathOperation.Union)
    val cloud = Path()
    cloud.op(out, right, PathOperation.Union)
    return cloud
}

/** The same outline, [by] px bigger all round: the gap between the cloud and what's behind it. */
private fun Path.grown(by: Float): Path {
    val b = getBounds()
    val sx = (b.width + 2 * by) / b.width
    val sy = (b.height + 2 * by) / b.height
    val c = b.center
    return Path().apply {
        addPath(this@grown)
        transform(Matrix().apply {
            translate(c.x, c.y)
            scale(sx, sy)
            translate(-c.x, -c.y)
        })
    }
}

private fun DrawScope.drawSun(c: Offset, r: Float, color: Color, stroke: Float) {
    drawCircle(color, r, c)
    repeat(8) { i ->
        rotate(i * 45f, c) {
            drawLine(color, Offset(c.x, c.y - r * 1.45f), Offset(c.x, c.y - r * 1.85f), stroke, StrokeCap.Round)
        }
    }
}

fun airQualityLabel(level: AirQualityLevel): Int = when (level) {
    AirQualityLevel.GOOD -> R.string.aqi_good
    AirQualityLevel.MODERATE -> R.string.aqi_moderate
    AirQualityLevel.SENSITIVE -> R.string.aqi_sensitive
    AirQualityLevel.UNHEALTHY -> R.string.aqi_unhealthy
    AirQualityLevel.VERY_UNHEALTHY -> R.string.aqi_very_unhealthy
    AirQualityLevel.HAZARDOUS -> R.string.aqi_hazardous
}

/** Apple's dark-mode colors for the EPA's green / yellow / orange / red / purple / maroon. */
fun airQualityColor(level: AirQualityLevel): Color = when (level) {
    AirQualityLevel.GOOD -> Color(0xFF32D74B)
    AirQualityLevel.MODERATE -> Color(0xFFFFD60A)
    AirQualityLevel.SENSITIVE -> Color(0xFFFF9F0A)
    AirQualityLevel.UNHEALTHY -> Color(0xFFFF453A)
    AirQualityLevel.VERY_UNHEALTHY -> Color(0xFFBF5AF2)
    AirQualityLevel.HAZARDOUS -> Color(0xFFA2132E)
}
