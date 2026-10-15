package com.deskdeck.ui.night.faces

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.deskdeck.R
import com.deskdeck.data.settings.NightClockColor
import com.deskdeck.data.settings.NightClockStyle
import com.deskdeck.domain.night.WorldMap
import com.deskdeck.domain.brief.AirQualityLevel
import com.deskdeck.domain.brief.WeatherCondition
import com.deskdeck.ui.common.WeatherGlyph
import com.deskdeck.ui.common.airQualityColor
import com.deskdeck.ui.theme.Inter
import com.deskdeck.ui.theme.NunitoRounded
import com.deskdeck.ui.theme.SerifDisplay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The colors a face draws with: the chosen color, or deep red with the night's red tint. */
data class FacePalette(
    val main: Color,
    /** A lighter tone of [main]: the second layer of the Layered digits. */
    val second: Color,
    val soft: Color,
    val faint: Color,
    /** Complications that stand out (the analog face's date, today in the calendar). */
    val accent: Color,
    /** Red tint: everything in one color (the air quality dot too). */
    val monochrome: Boolean = false,
)

private val NightRed = Color(0xFFD7261E)
private val AppleRed = Color(0xFFFF453A)

fun facePalette(color: NightClockColor, red: Boolean): FacePalette {
    if (red) return FacePalette(NightRed, NightRed.copy(alpha = 0.7f), NightRed.copy(alpha = 0.6f), NightRed.copy(alpha = 0.28f), NightRed, monochrome = true)
    val c = Color(color.argb)
    return FacePalette(
        main = c,
        // White pairs with Apple's system gray; colors with a lighter tint of themselves.
        second = if (color == NightClockColor.WHITE) Color(0xFF8E8E93) else lerp(c, Color.White, 0.38f).copy(alpha = 0.92f),
        soft = c.copy(alpha = 0.6f),
        faint = c.copy(alpha = 0.26f),
        // White faces keep Apple's red for the date, like StandBy.
        accent = if (color == NightClockColor.WHITE) AppleRed.copy(alpha = 0.9f) else c,
    )
}

/** What a face shows. */
data class FaceInfo(
    val now: LocalDateTime,
    val is24: Boolean,
    /** The next alarm, formatted, or null. */
    val alarm: String?,
    /** World face: where the weather is for, and its position if known. */
    val place: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Temperature and air quality, or null when hidden or unknown. */
    val weather: FaceWeather? = null,
)

data class FaceWeather(val temperature: Int, val condition: WeatherCondition, val airQuality: Int?)

/** One clock face, drawn to fill [modifier]'s size: the night screen, or a small preview in Settings. */
@Composable
fun ClockFace(style: NightClockStyle, palette: FacePalette, info: FaceInfo, modifier: Modifier = Modifier) {
    when (style) {
        NightClockStyle.BOLD -> DigitalFace(info, palette, thin = false, modifier)
        NightClockStyle.THIN -> DigitalFace(info, palette, thin = true, modifier)
        NightClockStyle.ANALOG -> AnalogFace(info, palette, modifier)
        NightClockStyle.LAYERED -> LayeredFace(info, palette, modifier)
        NightClockStyle.SERIF -> SerifFace(info, palette, modifier)
        NightClockStyle.CALENDAR -> CalendarFace(info, palette, modifier)
        NightClockStyle.WORLD -> WorldFace(info, palette, modifier)
    }
}

/** Text that's exactly [size] tall on screen, whatever the font size setting (faces scale with their box). */
@Composable
private fun px(size: Dp): TextUnit = with(LocalDensity.current) { size.toSp() }

private fun hoursMinutes(info: FaceInfo): Pair<String, String> {
    val h = if (info.is24) info.now.hour else (info.now.hour % 12).let { if (it == 0) 12 else it }
    return (if (info.is24) "%02d".format(h) else h.toString()) to "%02d".format(info.now.minute)
}

private fun shortDate(now: LocalDateTime): String =
    now.format(DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())).uppercase(Locale.getDefault())

/** Next alarm: a small alarm glyph and the time. */
@Composable
private fun AlarmLabel(alarm: String, color: Color, size: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_alarm), null, tint = color, modifier = Modifier.size(size * 0.9f))
        Spacer(Modifier.width(size * 0.25f))
        Text(alarm, style = TextStyle(fontFamily = NunitoRounded, fontSize = px(size), fontWeight = FontWeight.W800, fontFeatureSettings = "tnum"), color = color, maxLines = 1)
    }
}

/** Weather icon and temperature, then the air quality dot and index, in one quiet line. */
@Composable
private fun WeatherLabel(w: FaceWeather, p: FacePalette, size: Dp, color: Color = p.soft) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        WeatherGlyph(w.condition, Modifier.size(size * 1.3f), tint = color)
        Spacer(Modifier.width(size * 0.2f))
        Text("${w.temperature}°", style = TextStyle(fontFamily = NunitoRounded, fontSize = px(size), fontWeight = FontWeight.W800, fontFeatureSettings = "tnum"), color = color, maxLines = 1)
        if (w.airQuality != null) {
            val level = AirQualityLevel.of(w.airQuality)
            Spacer(Modifier.width(size * 0.6f))
            Box(
                Modifier
                    .size(size * 0.45f)
                    .clip(CircleShape)
                    .background(if (p.monochrome) color else airQualityColor(level).copy(alpha = 0.85f)),
            )
            Spacer(Modifier.width(size * 0.25f))
            Text("AQI ${w.airQuality}", style = TextStyle(fontFamily = NunitoRounded, fontSize = px(size), fontWeight = FontWeight.W800, fontFeatureSettings = "tnum"), color = color, maxLines = 1)
        }
    }
}

// region Bold / Thin

@Composable
private fun DigitalFace(info: FaceInfo, p: FacePalette, thin: Boolean, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val (h, m) = hoursMinutes(info)
        val digits = minOf(maxWidth.value / if (info.is24) 2.9f else 3.6f, maxHeight.value / 1.4f, 220f).dp
        val small = digits * 0.15f
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$h:$m",
                    style = TextStyle(
                        fontFamily = NunitoRounded,
                        fontSize = px(digits),
                        lineHeight = px(digits),
                        fontWeight = if (thin) FontWeight.W300 else FontWeight.W900,
                        letterSpacing = px(digits * -0.02f),
                        fontFeatureSettings = "tnum",
                    ),
                    color = p.main,
                    maxLines = 1,
                )
                if (!info.is24) {
                    Spacer(Modifier.width(digits * 0.05f))
                    Text(
                        info.now.format(DateTimeFormatter.ofPattern("a", Locale.getDefault())),
                        style = TextStyle(fontFamily = NunitoRounded, fontSize = px(digits * 0.16f), fontWeight = FontWeight.W700),
                        color = p.soft,
                        modifier = Modifier.padding(bottom = digits * 0.16f),
                    )
                }
            }
            Spacer(Modifier.height(small * 0.4f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(shortDate(info.now), style = TextStyle(fontFamily = NunitoRounded, fontSize = px(small), fontWeight = FontWeight.W800, letterSpacing = px(small * 0.03f)), color = p.soft)
                if (info.alarm != null) {
                    Spacer(Modifier.width(small * 0.9f))
                    AlarmLabel(info.alarm, p.soft, small)
                }
                if (info.weather != null) {
                    Spacer(Modifier.width(small * 0.9f))
                    WeatherLabel(info.weather, p, small)
                }
            }
        }
    }
}

// endregion

// region Analog (StandBy)

/**
 * StandBy's analog face: long thin hour lines running out to the edge of the screen, 12 / 3 / 6 /
 * 9 as numerals, short minute ticks at the edge, the next alarm on the left and the date on the
 * right. No second hand: it would be the brightest moving thing in a dark room.
 */
@Composable
private fun AnalogFace(info: FaceInfo, p: FacePalette, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val unit = min(maxWidth.value, maxHeight.value * 1.6f).dp / 100
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val inset = unit.toPx() * 3
            val halfW = size.width / 2 - inset
            val halfH = size.height / 2 - inset
            for (i in 0 until 60) {
                val a = Math.toRadians(i * 6.0 - 90)
                val dx = cos(a).toFloat()
                val dy = sin(a).toFloat()
                // Distance from the center to the edge of the face along this direction.
                val edge = min(if (abs(dx) > 1e-4f) halfW / abs(dx) else Float.MAX_VALUE, if (abs(dy) > 1e-4f) halfH / abs(dy) else Float.MAX_VALUE)
                val hour = i % 5 == 0
                if (hour && i % 15 == 0) continue // 12, 3, 6, 9 are numerals
                val start = if (hour) edge - min(halfW, halfH) * 0.62f else edge - unit.toPx() * 2.2f
                drawLine(
                    color = if (hour) p.soft else p.faint,
                    start = Offset(c.x + dx * start, c.y + dy * start),
                    end = Offset(c.x + dx * edge, c.y + dy * edge),
                    strokeWidth = (if (hour) 0.55f else 0.4f) * unit.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            // Hands, then a small hub.
            val hourAngle = (info.now.hour % 12) * 30f + info.now.minute * 0.5f
            val minuteAngle = info.now.minute * 6f
            rotate(hourAngle, c) { drawLine(p.main, c, Offset(c.x, c.y - halfH * 0.5f), unit.toPx() * 2.2f, StrokeCap.Round) }
            rotate(minuteAngle, c) { drawLine(p.main, c, Offset(c.x, c.y - halfH * 0.86f), unit.toPx() * 1.4f, StrokeCap.Round) }
            drawCircle(p.accent, unit.toPx() * 1.6f, c)
            drawCircle(Color.Black, unit.toPx() * 0.6f, c)
        }
        val numeral = TextStyle(fontFamily = Inter, fontSize = px(unit * 8), fontWeight = FontWeight.W500, textAlign = TextAlign.Center)
        Text("12", style = numeral, color = p.main, modifier = Modifier.align(BiasAlignment(0f, -0.74f)))
        Text("6", style = numeral, color = p.main, modifier = Modifier.align(BiasAlignment(0f, 0.74f)))
        Text("9", style = numeral, color = p.main, modifier = Modifier.align(BiasAlignment(-0.6f, 0f)))
        Text("3", style = numeral, color = p.main, modifier = Modifier.align(BiasAlignment(0.6f, 0f)))
        if (info.alarm != null) {
            Box(Modifier.align(BiasAlignment(-0.3f, 0f))) { AlarmLabel(info.alarm, p.soft, unit * 3.2f) }
        }
        Column(Modifier.align(BiasAlignment(0.32f, if (info.weather != null) 0.06f else 0f))) {
            Text(
                shortDate(info.now),
                style = TextStyle(fontFamily = Inter, fontSize = px(unit * 3.4f), fontWeight = FontWeight.W700),
                color = p.accent,
            )
            if (info.weather != null) WeatherLabel(info.weather, p, unit * 2.8f)
        }
    }
}

// endregion

// region Layered (StandBy "Digital")

/** Huge rounded digits that overlap, in two tones of the color, with a dotted colon. */
@Composable
private fun LayeredFace(info: FaceInfo, p: FacePalette, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        if (info.weather != null) {
            val unit = min(maxWidth.value, maxHeight.value * 1.6f).dp / 100
            Box(Modifier.align(Alignment.TopEnd).padding(unit * 2)) { WeatherLabel(info.weather, p, unit * 3.4f) }
        }
        val (h, m) = hoursMinutes(info)
        val chars = (h + m).length
        val size = minOf(maxWidth.value / (0.52f * chars + 0.6f), maxHeight.value * 0.92f).dp
        val style = TextStyle(fontFamily = NunitoRounded, fontSize = px(size), lineHeight = px(size), fontWeight = FontWeight.W900)
        // Neighbours overlap a little; alternate tones so each digit reads on its own.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(-size * 0.1f)) {
            h.forEachIndexed { i, ch -> Text(ch.toString(), style = style, color = if (i % 2 == 0) p.main else p.second) }
            Column(
                verticalArrangement = Arrangement.spacedBy(size * 0.16f),
                modifier = Modifier.padding(horizontal = size * 0.12f),
            ) {
                repeat(2) { Box(Modifier.size(size * 0.13f).clip(CircleShape).background(lerp(p.second, Color.White, 0.5f).copy(alpha = p.main.alpha))) }
            }
            m.forEachIndexed { i, ch -> Text(ch.toString(), style = style, color = if (i % 2 == 0) p.second else p.main) }
        }
    }
}

// endregion

// region Serif

/** A large serif time with the weekday and date above it. */
@Composable
private fun SerifFace(info: FaceInfo, p: FacePalette, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val (h, m) = hoursMinutes(info)
        val size = minOf(maxWidth.value / ((h + m).length * 0.62f + 0.4f), maxHeight.value * 0.7f).dp
        Column {
            Text(
                shortDate(info.now),
                style = TextStyle(fontFamily = Inter, fontSize = px(size * 0.13f), fontWeight = FontWeight.W700, letterSpacing = px(size * 0.005f)),
                color = p.soft,
                modifier = Modifier.padding(start = size * 0.04f),
            )
            Text(
                "$h:$m",
                style = TextStyle(fontFamily = SerifDisplay, fontSize = px(size), lineHeight = px(size * 1.02f), fontWeight = FontWeight.W800, letterSpacing = px(size * -0.02f)),
                color = p.main,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (info.alarm != null) {
                    AlarmLabel(info.alarm, p.faint, size * 0.1f)
                    Spacer(Modifier.width(size * 0.08f))
                }
                if (info.weather != null) WeatherLabel(info.weather, p, size * 0.1f, color = p.faint)
            }
        }
    }
}

// endregion

// region Calendar

/** This month as a small grid with today marked, today's date huge, and the time in the corner. */
@Composable
private fun CalendarFace(info: FaceInfo, p: FacePalette, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val unit = min(maxWidth.value, maxHeight.value * 1.6f).dp / 100
        val today = info.now.toLocalDate()
        val locale = Locale.getDefault()
        val firstDow = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
        val (h, m) = hoursMinutes(info)
        Column(Modifier.align(Alignment.TopEnd).padding(unit * 3), horizontalAlignment = Alignment.End) {
            Text(
                "$h:$m",
                style = TextStyle(fontFamily = NunitoRounded, fontSize = px(unit * 4.4f), fontWeight = FontWeight.W800, fontFeatureSettings = "tnum"),
                color = p.soft,
            )
            if (info.weather != null) WeatherLabel(info.weather, p, unit * 2.6f)
        }
        Row(Modifier.fillMaxSize().padding(unit * 4), verticalAlignment = Alignment.CenterVertically) {
            MonthGrid(today, firstDow, p, unit, Modifier.weight(1f))
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                // As big as the half allows: "15" needs about 1.25 em of width.
                val day = today.dayOfMonth.toString()
                val size = minOf(unit.value * 44, maxWidth.value / (day.length * 0.62f + 0.05f), maxHeight.value * 0.95f).dp
                Text(
                    day,
                    style = TextStyle(fontFamily = SerifDisplay, fontSize = px(size), lineHeight = px(size * 1.05f), fontWeight = FontWeight.W800),
                    color = p.main,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(today: LocalDate, firstDow: java.time.DayOfWeek, p: FacePalette, unit: Dp, modifier: Modifier) {
    val locale = Locale.getDefault()
    val first = today.withDayOfMonth(1)
    val lead = (first.dayOfWeek.value - firstDow.value + 7) % 7
    val cells = List(42) { i -> (i - lead + 1).takeIf { it in 1..today.lengthOfMonth() } }
    val rows = cells.chunked(7).filter { row -> row.any { it != null } }
    val cell = unit * 6.2f
    Column(modifier) {
        Text(
            today.month.getDisplayName(JavaTextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) } + " " + today.year,
            style = TextStyle(fontFamily = Inter, fontSize = px(unit * 3.6f), fontWeight = FontWeight.W700),
            color = p.accent,
            modifier = Modifier.padding(bottom = unit * 1.5f),
        )
        Row {
            repeat(7) { i ->
                val dow = firstDow.plus(i.toLong())
                Text(
                    dow.getDisplayName(JavaTextStyle.NARROW, locale),
                    style = TextStyle(fontFamily = Inter, fontSize = px(unit * 2.6f), fontWeight = FontWeight.W600, textAlign = TextAlign.Center),
                    color = p.faint,
                    modifier = Modifier.width(cell),
                )
            }
        }
        rows.forEach { row ->
            Row {
                row.forEach { day ->
                    Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                        if (day == today.dayOfMonth) Box(Modifier.size(cell * 0.86f).clip(CircleShape).background(p.accent))
                        if (day != null) {
                            Text(
                                day.toString(),
                                style = TextStyle(fontFamily = Inter, fontSize = px(unit * 2.9f), fontWeight = FontWeight.W600, textAlign = TextAlign.Center, fontFeatureSettings = "tnum"),
                                color = if (day == today.dayOfMonth) Color.Black else p.soft,
                            )
                        }
                    }
                }
            }
        }
    }
}

// endregion

// region World

/**
 * A dotted world map lit by the sun: dots on the night side are dim, a soft line follows the
 * day/night boundary, a few world cities are larger dots, and a ring marks your location. Your
 * place and the time sit in the bottom-left corner.
 */
@Composable
private fun WorldFace(info: FaceInfo, p: FacePalette, modifier: Modifier) {
    val dots = remember { WorldMap.landDots(COLS) }
    BoxWithConstraints(modifier) {
        val unit = min(maxWidth.value, maxHeight.value * 1.6f).dp / 100
        val sun = remember(info.now) { WorldMap.subsolarPoint(info.now.atZone(ZoneId.systemDefault()).toInstant()) }
        Canvas(Modifier.fillMaxSize()) {
            val mapW = size.width
            val mapH = mapW * ((WorldMap.TOP_LAT - WorldMap.BOTTOM_LAT) / 360.0).toFloat()
            val top = ((size.height - mapH) / 2).coerceAtLeast(0f)
            fun x(lon: Double) = ((lon + 180) / 360 * mapW).toFloat()
            fun y(lat: Double) = top + ((WorldMap.TOP_LAT - lat) / (WorldMap.TOP_LAT - WorldMap.BOTTOM_LAT) * mapH).toFloat()
            val cellPx = mapW / COLS
            val (day, night) = dots.partition { WorldMap.isDay(it.lon, it.lat, sun) }
            drawPoints(day.map { Offset(x(it.lon), y(it.lat)) }, PointMode.Points, p.main.copy(alpha = p.main.alpha * 0.85f), cellPx * 0.62f, StrokeCap.Round)
            drawPoints(night.map { Offset(x(it.lon), y(it.lat)) }, PointMode.Points, p.main.copy(alpha = p.main.alpha * 0.28f), cellPx * 0.62f, StrokeCap.Round)
            // The day/night line.
            val line = Path()
            for (i in 0..120) {
                val lon = -180.0 + i * 3
                val px = x(lon)
                val py = y(WorldMap.terminatorLat(lon, sun))
                if (i == 0) line.moveTo(px, py) else line.lineTo(px, py)
            }
            drawPath(line, p.main.copy(alpha = p.main.alpha * 0.3f), style = Stroke(cellPx * 0.22f, cap = StrokeCap.Round))
            WorldMap.CITIES.forEach { city ->
                val lit = WorldMap.isDay(city.lon, city.lat, sun)
                drawCircle(p.main.copy(alpha = p.main.alpha * if (lit) 0.7f else 0.35f), cellPx * 0.75f, Offset(x(city.lon), y(city.lat)))
            }
            if (info.latitude != null && info.longitude != null) {
                val here = Offset(x(info.longitude), y(info.latitude))
                drawCircle(Color.Black, cellPx * 1.3f, here)
                drawCircle(p.accent, cellPx * 1.05f, here, style = Stroke(cellPx * 0.4f))
                drawCircle(p.accent.copy(alpha = p.accent.alpha * 0.6f), cellPx * 0.55f, here)
            }
        }
        val (h, m) = hoursMinutes(info)
        Column(
            Modifier
                .align(Alignment.BottomStart)
                // A soft dark pool behind the text, so map dots under it don't fight the words.
                .drawBehind {
                    val r = size.maxDimension * 0.75f
                    drawCircle(
                        Brush.radialGradient(
                            0f to Color.Black.copy(alpha = 0.92f),
                            0.6f to Color.Black.copy(alpha = 0.7f),
                            1f to Color.Transparent,
                            center = center,
                            radius = r,
                        ),
                        radius = r,
                        center = center,
                    )
                }
                .padding(start = unit * 4, bottom = unit * 3),
        ) {
            if (info.place != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_location_arrow), null, tint = p.soft, modifier = Modifier.size(unit * 3.6f))
                    Spacer(Modifier.width(unit))
                    Text(info.place, style = TextStyle(fontFamily = Inter, fontSize = px(unit * 3.8f), fontWeight = FontWeight.W600), color = p.soft, maxLines = 1)
                    if (info.weather != null) {
                        Spacer(Modifier.width(unit * 2.5f))
                        WeatherLabel(info.weather, p, unit * 3.6f)
                    }
                }
            } else if (info.weather != null) {
                WeatherLabel(info.weather, p, unit * 3.6f)
            }
            Text(
                "$h:$m",
                style = TextStyle(fontFamily = Inter, fontSize = px(unit * 15), lineHeight = px(unit * 16), fontWeight = FontWeight.W600, fontFeatureSettings = "tnum", letterSpacing = px(unit * -0.4f)),
                color = p.main,
                modifier = Modifier.offset(x = -unit * 0.6f),
            )
        }
    }
}

private const val COLS = 90

// endregion
