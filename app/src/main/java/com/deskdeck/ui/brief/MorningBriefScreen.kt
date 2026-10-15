package com.deskdeck.ui.brief

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.FlowRow
import com.deskdeck.ui.common.cappedSp
import com.deskdeck.ui.common.rememberTouchExploration
import com.deskdeck.ui.adaptive.DeskSize
import com.deskdeck.ui.adaptive.LocalDeskSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import com.deskdeck.ui.theme.DeskDeckTheme
import com.deskdeck.domain.brief.AirQualityLevel
import androidx.compose.ui.unit.sp
import com.deskdeck.ui.common.airQualityLabel
import com.deskdeck.ui.common.airQualityColor
import com.deskdeck.ui.common.WeatherGlyph
import androidx.core.net.toUri
import com.deskdeck.R
import com.deskdeck.burnin.KeepScreenOn
import com.deskdeck.data.settings.NightSettings
import com.deskdeck.domain.brief.Headline
import com.deskdeck.domain.brief.MarketBrief
import com.deskdeck.domain.brief.MorningBrief
import com.deskdeck.domain.brief.WeatherBrief
import com.deskdeck.domain.brief.WeatherCondition
import com.deskdeck.ui.night.rememberNightDrift
import com.deskdeck.ui.theme.Inter
import com.deskdeck.ui.theme.LocalMotion
import com.deskdeck.ui.theme.MotionSpec
import com.deskdeck.ui.theme.NunitoRounded
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay

private val CardColor = Color(0xFF161618)
private val Primary = Color(0xFFE8E8E8)
private val Secondary = Color(0xFF9A9AA0)
private val Up = Color(0xFF30D158)
private val Down = Color(0xFFFF453A)
private const val CYCLE_MS = 8_000L

/**
 * The Morning Brief, StandBy-widget style: a greeting and the date, then two large cards on black,
 * weather and markets (with headlines). It stays for the configured time or until Done; the layout
 * drifts slowly like the night screen since it can be up for a while.
 */
@Composable
fun MorningBriefScreen(
    brief: MorningBrief?,
    settings: NightSettings,
    hasMarketKey: Boolean,
    refreshing: Boolean,
    onDone: () -> Unit,
) {
    KeepScreenOn(enabled = true)
    val motion = LocalMotion.current
    val drift by rememberNightDrift(enabled = !motion.reduced)
    val today = LocalDate.now()
    val hour = LocalTime.now().hour
    val greeting = stringResource(
        when {
            hour < 12 -> R.string.brief_good_morning
            hour < 18 -> R.string.brief_good_afternoon
            else -> R.string.brief_good_evening
        },
    )
    val date = today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
    val showWeather = settings.briefWeather
    val showMarket = settings.briefMarket || settings.briefHeadlines

    val deskSize = LocalDeskSize.current
    // Split screen and pop-up view: the cards stack and scroll instead of sitting side by side.
    val stacked = deskSize != DeskSize.EXPANDED
    val compact = deskSize == DeskSize.COMPACT
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            Modifier
                .fillMaxSize()
                .offset { drift }
                .padding(horizontal = if (stacked) 20.dp else 48.dp, vertical = if (stacked) 20.dp else 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting, style = TextStyle(fontFamily = NunitoRounded, fontSize = cappedSp(if (compact) 32f else 44f, 1.3f), fontWeight = FontWeight.W800), color = Primary, modifier = Modifier.semantics { heading() })
                    Text(date, style = TextStyle(fontFamily = Inter, fontSize = 20.sp, fontWeight = FontWeight.W500), color = Secondary)
                }
                TextButton(onClick = onDone, modifier = Modifier.height(56.dp)) {
                    Text(stringResource(R.string.brief_done), fontSize = 20.sp, color = Primary)
                }
            }
            Spacer(Modifier.height(if (stacked) 16.dp else 28.dp))
            val weather: @Composable (Modifier) -> Unit = { m ->
                Appear(0, m) { WeatherCard(brief?.weather, settings.briefAirQuality, Modifier.fillMaxHeight()) }
            }
            val market: @Composable (Modifier) -> Unit = { m ->
                Appear(1, m) {
                    MarketCard(
                        markets = if (settings.briefMarket) brief?.markets.orEmpty() else emptyList(),
                        headlines = if (settings.briefHeadlines) brief?.headlines.orEmpty() else emptyList(),
                        showMarket = settings.briefMarket,
                        hasKey = hasMarketKey,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (stacked) {
                // Fixed card heights (growing with the font size) so their layout works in a scroll.
                val scale = LocalDensity.current.fontScale.coerceIn(1f, 1.6f)
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (showWeather) weather(Modifier.fillMaxWidth().height(StackedWeatherHeight * scale))
                    if (showMarket) market(Modifier.fillMaxWidth().height(StackedMarketHeight * scale))
                }
            } else {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    if (showWeather) weather(Modifier.weight(1f).fillMaxHeight())
                    if (showMarket) market(Modifier.weight(1.25f).fillMaxHeight())
                }
            }
            Spacer(Modifier.height(16.dp))
            Updated(brief?.fetchedAtMs, refreshing)
        }
    }
}

private val StackedWeatherHeight = 360.dp
private val StackedMarketHeight = 480.dp

/** Cards rise in one after another; just fade with reduced motion. */
@Composable
private fun Appear(index: Int, modifier: Modifier, content: @Composable () -> Unit) {
    val motion = LocalMotion.current
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = state,
        enter = if (motion.reduced) fadeIn(motion.fade()) else fadeIn(motion.fade(MotionSpec.LONG_MS, index * 120)) +
            slideInVertically(motion.fade(MotionSpec.LONG_MS, index * 120)) { it / 8 },
        modifier = modifier,
    ) { content() }
}

@Composable
private fun Card(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(36.dp))
            .background(CardColor)
            .padding(28.dp),
    ) { content() }
}

@Composable
private fun WeatherCard(weather: WeatherBrief?, showAirQuality: Boolean, modifier: Modifier) {
    Card(modifier.fillMaxWidth()) {
        if (weather == null) {
            Placeholder(stringResource(R.string.brief_weather_missing))
            return@Card
        }
        val condition = WeatherCondition.fromWmo(weather.code)
        val conditionText = stringResource(conditionLabel(condition))
        val unit = if (weather.fahrenheit) "°F" else "°C"
        val aqi = weather.airQuality.takeIf { showAirQuality }
        val aqiLevel = aqi?.let { AirQualityLevel.of(it) }
        val aqiText = aqiLevel?.let { stringResource(R.string.brief_aqi, aqi, stringResource(airQualityLabel(it))) }
        val spoken = listOfNotNull(
            stringResource(R.string.brief_weather_description, weather.place, weather.temperature.toInt(), unit, conditionText, weather.high.toInt(), weather.low.toInt()),
            aqiText,
        ).joinToString(". ")
        Column(Modifier.fillMaxSize().semantics(mergeDescendants = true) { contentDescription = spoken }) {
            Text(weather.place, style = TextStyle(fontFamily = Inter, fontSize = 22.sp, fontWeight = FontWeight.W600), color = Primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${weather.temperature.toInt()}°", style = TextStyle(fontFamily = NunitoRounded, fontSize = cappedSp(112f), lineHeight = cappedSp(112f), fontWeight = FontWeight.W800), color = Primary)
                Spacer(Modifier.width(16.dp))
                WeatherGlyph(condition, Modifier.size(72.dp))
            }
            Text(conditionText, style = TextStyle(fontFamily = Inter, fontSize = 24.sp, fontWeight = FontWeight.W600), color = Primary)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.brief_high_low, weather.high.toInt(), weather.low.toInt()), style = TextStyle(fontFamily = Inter, fontSize = 20.sp), color = Secondary)
            val extras = listOfNotNull(
                weather.rainChancePercent?.let { stringResource(R.string.brief_rain_chance, it) },
                weather.sunrise?.let { stringResource(R.string.brief_sunrise, it) },
            ).joinToString("  ·  ")
            if (extras.isNotEmpty()) Text(extras, style = TextStyle(fontFamily = Inter, fontSize = 18.sp), color = Secondary)
            if (aqiText != null && aqiLevel != null) {
                Spacer(Modifier.height(8.dp))
                // A dot in the level's color, like the AQI in Apple's Weather.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(airQualityColor(aqiLevel)))
                    Spacer(Modifier.width(8.dp))
                    Text(aqiText, style = TextStyle(fontFamily = Inter, fontSize = 18.sp, fontWeight = FontWeight.W600), color = Primary)
                }
            }
            Spacer(Modifier.weight(1f))
            // Open-Meteo's CC BY 4.0 licence asks for credit.
            Text(stringResource(R.string.brief_weather_credit), style = TextStyle(fontFamily = Inter, fontSize = 12.sp), color = Secondary.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun MarketCard(markets: List<MarketBrief>, headlines: List<Headline>, showMarket: Boolean, hasKey: Boolean, modifier: Modifier) {
    var confirm by remember { mutableStateOf<Headline?>(null) }
    Card(modifier) {
        Column(Modifier.fillMaxSize()) {
            if (showMarket) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        !hasKey -> Placeholder(stringResource(R.string.brief_market_no_key))
                        markets.isEmpty() -> Placeholder(stringResource(R.string.brief_market_missing))
                        else -> MarketPager(markets)
                    }
                }
            }
            if (headlines.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                // Fewer headlines with large text, so they still fit under the chart.
                val count = if (LocalDensity.current.fontScale > 1.3f) 1 else 3
                headlines.take(count).forEach { h ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClickLabel = stringResource(R.string.brief_open_headline)) { confirm = h }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(h.title, style = TextStyle(fontFamily = Inter, fontSize = 17.sp, fontWeight = FontWeight.W600), color = Primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(h.source, style = TextStyle(fontFamily = Inter, fontSize = 13.sp), color = Secondary)
                    }
                }
            }
        }
    }
    confirm?.let { h -> OpenHeadlineDialog(h) { confirm = null } }
}

/** One watchlist item per page; cycles every [CYCLE_MS] unless you're swiping. */
@Composable
private fun MarketPager(markets: List<MarketBrief>) {
    val pager = rememberPagerState { markets.size }
    val motion = LocalMotion.current
    // No automatic page turns under TalkBack: content mustn't move while it's being read.
    val touchExploration by rememberTouchExploration()
    LaunchedEffect(pager.currentPage, markets.size, pager.isScrollInProgress, touchExploration) {
        if (markets.size < 2 || pager.isScrollInProgress || touchExploration) return@LaunchedEffect
        delay(CYCLE_MS)
        val next = (pager.currentPage + 1) % markets.size
        if (motion.reduced) pager.scrollToPage(next) else pager.animateScrollToPage(next)
    }
    Column(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.weight(1f)) { page -> MarketPage(markets[page]) }
        if (markets.size > 1) {
            val position = stringResource(R.string.brief_market_position, pager.currentPage + 1, markets.size)
            Row(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp)
                    .clearAndSetSemantics { contentDescription = position },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(markets.size) { i ->
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (i == pager.currentPage) Primary else Secondary.copy(alpha = 0.4f)))
                }
            }
        }
    }
}

@Composable
private fun MarketPage(m: MarketBrief) {
    val up = m.change >= 0
    val color = if (up) Up else Down
    val arrow = if (up) "▲" else "▼"
    val sign = if (up) "+" else "−"
    val viaEtf = !m.crypto && !m.symbol.equals(m.name, ignoreCase = true)
    val spoken = stringResource(
        if (up) R.string.brief_market_up else R.string.brief_market_down,
        m.name, String.format(Locale.getDefault(), "%,.2f", m.price), String.format(Locale.getDefault(), "%.2f", abs(m.changePercent)),
    )
    Column(Modifier.fillMaxSize().semantics(mergeDescendants = true) { contentDescription = spoken }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.name, style = TextStyle(fontFamily = Inter, fontSize = 22.sp, fontWeight = FontWeight.W600), color = Primary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(
                    when {
                        m.crypto -> R.string.brief_market_24h
                        m.marketOpen -> R.string.brief_market_open
                        else -> R.string.brief_market_closed
                    },
                ),
                style = TextStyle(fontFamily = Inter, fontSize = 14.sp),
                color = Secondary,
            )
        }
        if (viaEtf) Text(stringResource(R.string.brief_market_via, m.symbol), style = TextStyle(fontFamily = Inter, fontSize = 13.sp), color = Secondary)
        Spacer(Modifier.height(10.dp))
        FlowRow(itemVerticalAlignment = Alignment.Bottom) {
            Text(String.format(Locale.getDefault(), "%,.2f", m.price), style = TextStyle(fontFamily = NunitoRounded, fontSize = cappedSp(52f, 1.2f), lineHeight = cappedSp(56f, 1.2f), fontWeight = FontWeight.W800, fontFeatureSettings = "tnum"), color = Primary)
            Spacer(Modifier.width(14.dp))
            Text(
                "$arrow $sign${String.format(Locale.getDefault(), "%.2f", abs(m.change))} ($sign${String.format(Locale.getDefault(), "%.2f", abs(m.changePercent))}%)",
                style = TextStyle(fontFamily = Inter, fontSize = 20.sp, fontWeight = FontWeight.W600, fontFeatureSettings = "tnum"),
                color = color,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        IntradayChart(m.points, previousClose = m.price - m.change, color = color, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}

/** The day's line with a soft fill, and the previous close as a dotted baseline. */
@Composable
private fun IntradayChart(points: List<Double>, previousClose: Double, color: Color, modifier: Modifier) {
    if (points.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) { Text(stringResource(R.string.brief_chart_missing), color = Secondary, fontSize = 14.sp) }
        return
    }
    Canvas(modifier) {
        val lo = minOf(points.min(), previousClose)
        val hi = maxOf(points.max(), previousClose)
        val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        fun y(v: Double) = (size.height * (1 - (v - lo) / span)).toFloat()
        val step = size.width / (points.size - 1)
        val line = Path().apply {
            points.forEachIndexed { i, v -> if (i == 0) moveTo(0f, y(v)) else lineTo(i * step, y(v)) }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
        drawLine(Secondary.copy(alpha = 0.5f), Offset(0f, y(previousClose)), Offset(size.width, y(previousClose)), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
        drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Opening a headline leaves DeskDeck, so it only happens after an explicit yes. */
@Composable
private fun OpenHeadlineDialog(headline: Headline, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.brief_open_title)) },
        text = { Text(stringResource(R.string.brief_open_text, headline.title, headline.source)) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, headline.url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: ActivityNotFoundException) {
                    // No browser installed.
                }
            }) { Text(stringResource(R.string.brief_open_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.night_time_cancel)) } },
    )
}

@Composable
private fun Placeholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(fontFamily = Inter, fontSize = 18.sp), color = Secondary)
    }
}

@Composable
private fun Updated(fetchedAtMs: Long?, refreshing: Boolean) {
    val text = when {
        refreshing -> stringResource(R.string.brief_updating)
        fetchedAtMs == null || fetchedAtMs == 0L -> stringResource(R.string.brief_never_updated)
        else -> {
            val at = Instant.ofEpochMilli(fetchedAtMs).atZone(ZoneId.systemDefault())
            val sameDay = at.toLocalDate() == LocalDate.now()
            val time = at.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
            if (sameDay) stringResource(R.string.brief_updated, time) else stringResource(R.string.brief_updated_day, at.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())), time)
        }
    }
    Text(text, style = TextStyle(fontFamily = Inter, fontSize = 14.sp), color = Secondary)
}

private fun conditionLabel(c: WeatherCondition): Int = when (c) {
    WeatherCondition.CLEAR -> R.string.weather_clear
    WeatherCondition.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
    WeatherCondition.CLOUDY -> R.string.weather_cloudy
    WeatherCondition.FOG -> R.string.weather_fog
    WeatherCondition.DRIZZLE -> R.string.weather_drizzle
    WeatherCondition.RAIN -> R.string.weather_rain
    WeatherCondition.SNOW -> R.string.weather_snow
    WeatherCondition.SHOWERS -> R.string.weather_showers
    WeatherCondition.THUNDERSTORM -> R.string.weather_thunderstorm
}

/** Simple drawn weather icons in the app's outline style, with a little color like StandBy's. */
// region Previews

private val previewBrief = MorningBrief(
    weather = WeatherBrief("Dengkil", 30.0, 32.0, 24.0, code = 0, rainChancePercent = 100, sunrise = "06:57", fahrenheit = false),
    markets = listOf(
        MarketBrief("S&P 500", "SPY", 778.01, -0.09, -0.01, List(78) { 778 + kotlin.math.sin(it / 9.0) * 1.5 }, "USD", marketOpen = false),
        MarketBrief("Bitcoin", "BTC/USD", 83_730.24, 561.24, 0.67, List(96) { 83_200 + it * 5.5 }, "USD", marketOpen = true, crypto = true),
    ),
    headlines = listOf(
        Headline("Stocks drift as investors await earnings season", "CNBC", "https://www.cnbc.com/", null),
        Headline("Oil slips after a three-day rally", "CNBC", "https://www.cnbc.com/", null),
        Headline("Treasury yields hold steady ahead of Fed minutes", "CNBC", "https://www.cnbc.com/", null),
    ),
    fetchedAtMs = 1_791_936_000_000,
)

@Composable
private fun BriefPreview(size: DeskSize) {
    DeskDeckTheme {
        CompositionLocalProvider(LocalDeskSize provides size) {
            MorningBriefScreen(brief = previewBrief, settings = NightSettings(), hasMarketKey = true, refreshing = false, onDone = {})
        }
    }
}

@Preview(name = "Brief – expanded", widthDp = 1317, heightDp = 823)
@Composable
private fun BriefExpandedPreview() = BriefPreview(DeskSize.EXPANDED)

@Preview(name = "Brief – medium (half split)", widthDp = 650, heightDp = 823)
@Composable
private fun BriefMediumPreview() = BriefPreview(DeskSize.MEDIUM)

@Preview(name = "Brief – compact", widthDp = 430, heightDp = 823)
@Composable
private fun BriefCompactPreview() = BriefPreview(DeskSize.COMPACT)

// endregion
