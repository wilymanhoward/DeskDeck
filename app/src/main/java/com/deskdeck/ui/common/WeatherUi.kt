package com.deskdeck.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
        val stroke = size.minDimension * 0.042f
        when (condition) {
            WeatherCondition.CLEAR -> drawSun(center, size.minDimension * 0.22f, sun, stroke)
            WeatherCondition.PARTLY_CLOUDY -> {
                drawSun(Offset(size.width * 0.38f, size.height * 0.36f), size.minDimension * 0.15f, sun, stroke)
                drawCloud(cloud, dy = 0.08f)
            }
            WeatherCondition.CLOUDY, WeatherCondition.FOG -> drawCloud(cloud, dy = 0f)
            WeatherCondition.DRIZZLE, WeatherCondition.RAIN, WeatherCondition.SHOWERS -> {
                drawCloud(cloud, dy = -0.1f)
                val n = if (condition == WeatherCondition.DRIZZLE) 2 else 3
                repeat(n) { i ->
                    val x = size.width * (0.35f + i * 0.15f)
                    drawLine(drop, Offset(x, size.height * 0.72f), Offset(x - size.width * 0.05f, size.height * 0.9f), stroke, StrokeCap.Round)
                }
            }
            WeatherCondition.SNOW -> {
                drawCloud(cloud, dy = -0.1f)
                repeat(3) { i -> drawCircle(tint ?: Color.White, stroke, Offset(size.width * (0.35f + i * 0.15f), size.height * 0.82f)) }
            }
            WeatherCondition.THUNDERSTORM -> {
                drawCloud(cloud, dy = -0.1f)
                val bolt = Path().apply {
                    moveTo(size.width * 0.52f, size.height * 0.62f)
                    lineTo(size.width * 0.42f, size.height * 0.8f)
                    lineTo(size.width * 0.52f, size.height * 0.8f)
                    lineTo(size.width * 0.46f, size.height * 0.96f)
                }
                drawPath(bolt, sun, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
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

private fun DrawScope.drawCloud(color: Color, dy: Float) {
    val w = size.width
    val h = size.height
    val y = h * (0.55f + dy)
    drawCircle(color, w * 0.17f, Offset(w * 0.38f, y))
    drawCircle(color, w * 0.22f, Offset(w * 0.56f, y - h * 0.07f))
    drawCircle(color, w * 0.15f, Offset(w * 0.73f, y + h * 0.02f))
    drawRect(color, Offset(w * 0.24f, y), Size(w * 0.6f, w * 0.15f))
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
