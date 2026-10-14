package com.deskdeck.ui.night

import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deskdeck.R
import com.deskdeck.burnin.KeepScreenOn
import com.deskdeck.data.settings.ClockFormat
import com.deskdeck.data.settings.NightClockStyle
import com.deskdeck.data.settings.NightSettings
import com.deskdeck.data.settings.RedTint
import com.deskdeck.domain.NowPlaying
import com.deskdeck.ui.common.rememberCurrentTime
import com.deskdeck.ui.common.rememberPlaybackPosition
import com.deskdeck.ui.settings.LocalAppSettings
import com.deskdeck.ui.theme.Inter
import com.deskdeck.ui.theme.NunitoRounded
import com.deskdeck.ui.theme.LocalMotion
import com.deskdeck.ui.common.cappedSp
import com.deskdeck.ui.common.rememberTouchExploration
import com.deskdeck.ui.adaptive.DeskSize
import com.deskdeck.ui.adaptive.LocalDeskSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import com.deskdeck.data.fake.FakeData
import com.deskdeck.domain.PlaybackInfo
import com.deskdeck.domain.PlaybackStatus
import com.deskdeck.ui.theme.DeskDeckTheme
import com.deskdeck.ui.theme.MotionSpec
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay

/** True while the night screen is up: the volume HUD and others switch to their dim night look. */
val LocalNightMode = staticCompositionLocalOf { false }

/** Night ink: everything on the night screen is drawn in one dim color, red in red-tint mode. */
private data class Ink(val main: Color, val soft: Color, val faint: Color)

private val WhiteInk = Ink(Color(0xFFBDBDBD), Color(0xFFBDBDBD).copy(alpha = 0.55f), Color(0xFFBDBDBD).copy(alpha = 0.28f))
private val RedInk = Ink(Color(0xFFD7261E), Color(0xFFD7261E).copy(alpha = 0.6f), Color(0xFFD7261E).copy(alpha = 0.3f))

/** Below this the room counts as dark for the automatic red tint. */
private const val DARK_ROOM_LUX = 8f
private const val WAKE_MS = 10_000L
private const val DRIFT_EVERY_MS = 90_000L
private val DRIFT_RANGE = 14.dp

/**
 * The StandBy-style night screen: true black, a compact music panel on the left and a very large
 * clock on the right with the date and next alarm. Built to stay on for hours: everything is dim
 * and thin, the layout drifts a little every 90 s (on top of the app-wide pixel shift), the
 * window brightness is held at the night level, and a tap only brightens it for [WAKE_MS] and
 * shows a small way out.
 */
@Composable
fun NightScreen(onOpenSettings: () -> Unit, viewModel: NightViewModel = hiltViewModel()) {
    val night = LocalAppSettings.current.night
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val notifications by viewModel.notificationCount.collectAsStateWithLifecycle()
    val motion = LocalMotion.current

    KeepScreenOn(enabled = true)

    var awakeUntil by remember { mutableLongStateOf(0L) }
    var awake by remember { mutableStateOf(false) }
    LaunchedEffect(awakeUntil) {
        if (awakeUntil == 0L) return@LaunchedEffect
        awake = true
        delay(WAKE_MS)
        awake = false
    }
    WindowBrightness(if (awake) maxOf(night.brightness * 6, 0.35f) else night.brightness)
    // With TalkBack the way out stays available; it can't be found by a sighted first tap.
    val touchExploration by rememberTouchExploration()

    val lux by rememberAmbientLux()
    val red = when (night.redTint) {
        RedTint.ON -> true
        RedTint.OFF -> false
        // No sensor: Night Mode is on because it's night, so red.
        RedTint.AUTO -> lux?.let { it < DARK_ROOM_LUX } ?: true
    }
    val target = if (red) RedInk else WhiteInk
    val main by animateColorAsState(target.main, motion.color(), label = "inkMain")
    val soft by animateColorAsState(target.soft, motion.color(), label = "inkSoft")
    val faint by animateColorAsState(target.faint, motion.color(), label = "inkFaint")
    val ink = Ink(main, soft, faint)

    // New notifications since the night began: a tiny dim dot instead of banners.
    val startCount = remember { notifications }
    val hasNew = notifications > startCount

    val drift = rememberNightDrift(enabled = !motion.reduced)
    val exitLabel = stringResource(R.string.night_exit)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = stringResource(R.string.night_show_controls),
            ) {
                awakeUntil = System.currentTimeMillis()
            },
    ) {
        NightLayout(
            nowPlaying = nowPlaying,
            settings = night,
            ink = ink,
            red = red,
            hasNewNotifications = hasNew,
            onPlayPause = viewModel::playPause,
            onNext = viewModel::skipNext,
            onPrevious = viewModel::skipPrevious,
            modifier = Modifier.fillMaxSize().offset { drift.value },
        )

        AnimatedVisibility(
            visible = awake || touchExploration,
            enter = fadeIn(motion.fade(MotionSpec.LONG_MS)),
            exit = fadeOut(motion.fade(MotionSpec.LONG_MS)),
            modifier = Modifier.align(Alignment.TopEnd).padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = viewModel::exitNight) { Text(exitLabel, color = ink.main) }
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(56.dp)) {
                    Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.rail_settings), tint = ink.soft)
                }
            }
        }
    }
}

/**
 * Full tablet: music on the left, the big clock on the right. Split screen and pop-up view: the
 * clock on top (sized to the room there is) with a one-row player under it.
 */
@Composable
private fun NightLayout(
    nowPlaying: NowPlaying?,
    settings: NightSettings,
    ink: Ink,
    red: Boolean,
    hasNewNotifications: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val np = nowPlaying
    if (LocalDeskSize.current == DeskSize.EXPANDED) {
        Row(
            modifier = modifier.padding(horizontal = 56.dp, vertical = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (np != null) {
                NightMusic(np, ink, red, onPlayPause, onNext, onPrevious, Modifier.weight(0.36f))
                Spacer(Modifier.width(56.dp))
            }
            NightClock(settings, ink, hasNewNotifications, Modifier.weight(if (np != null) 0.64f else 1f).fillMaxHeight())
        }
    } else {
        Column(
            modifier = modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NightClock(settings, ink, hasNewNotifications, Modifier.weight(1f).fillMaxWidth())
            if (np != null) {
                Spacer(Modifier.height(16.dp))
                NightMiniMusic(np, ink, red, onPlayPause, onNext, onPrevious, Modifier.widthIn(max = 520.dp).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun NightClock(settings: NightSettings, ink: Ink, hasNewNotifications: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val now by rememberCurrentTime(withSeconds = false)
    val is24 = when (settings.clockFormat) {
        ClockFormat.H24 -> true
        ClockFormat.H12 -> false
        ClockFormat.SYSTEM -> DateFormat.is24HourFormat(context)
    }
    val alarm = remember(now.hour, now.minute) { nextAlarm(context, is24) }
    val date = now.format(DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())).uppercase(Locale.getDefault())
    val time = now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a"))
    val fullDate = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
    val spoken = listOfNotNull(
        stringResource(R.string.night_clock_description, time, fullDate),
        alarm?.let { stringResource(R.string.night_alarm_description, it) },
        if (hasNewNotifications) stringResource(R.string.night_new_notifications) else null,
    ).joinToString(". ")

    BoxWithConstraints(modifier.clearAndSetSemantics { contentDescription = spoken }, contentAlignment = Alignment.Center) {
    // As large as the room allows, up to the full-tablet size: digits by width, everything by height.
    val roomForClock = maxHeight - 64.dp
    val digitalDp = minOf(220f, maxWidth.value / if (is24) 2.9f else 3.6f, roomForClock.value / 1.05f).coerceAtLeast(48f)
    val analogSize = minOf(340.dp, maxWidth * 0.8f, roomForClock).coerceAtLeast(96.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (settings.clockStyle) {
            NightClockStyle.ANALOG -> AnalogClock(now, ink, Modifier.size(analogSize))
            else -> DigitalClock(now, is24, settings.clockStyle == NightClockStyle.THIN, ink, digitalDp)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(date, style = TextStyle(fontFamily = NunitoRounded, fontSize = cappedSp(32f, 1.3f), fontWeight = FontWeight.W800, letterSpacing = 1.sp), color = ink.soft)
            if (alarm != null) {
                Spacer(Modifier.width(28.dp))
                Icon(painterResource(R.drawable.ic_alarm), null, tint = ink.soft, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(8.dp))
                Text(alarm, style = TextStyle(fontFamily = NunitoRounded, fontSize = cappedSp(32f, 1.3f), fontWeight = FontWeight.W800, fontFeatureSettings = "tnum"), color = ink.soft)
            }
            if (hasNewNotifications) {
                Spacer(Modifier.width(24.dp))
                Box(Modifier.size(10.dp).clip(CircleShape).background(ink.faint))
            }
        }
    }
    }
}

@Composable
private fun DigitalClock(now: LocalDateTime, is24: Boolean, thin: Boolean, ink: Ink, sizeDp: Float) {
    val digits = now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm"))
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = digits,
            // StandBy's look: rounded digits, heavy by default, light for the thin style.
            style = TextStyle(
                fontFamily = NunitoRounded,
                // Sized to the window, so it doesn't grow with the font size.
                fontSize = cappedSp(sizeDp),
                lineHeight = cappedSp(sizeDp),
                fontWeight = if (thin) FontWeight.W300 else FontWeight.W900,
                letterSpacing = (-4).sp,
                fontFeatureSettings = "tnum",
            ),
            color = ink.main,
            maxLines = 1,
        )
        if (!is24) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = now.format(DateTimeFormatter.ofPattern("a", Locale.getDefault())),
                style = TextStyle(fontFamily = NunitoRounded, fontSize = cappedSp(sizeDp * 0.16f), fontWeight = FontWeight.W700),
                color = ink.soft,
                modifier = Modifier.padding(bottom = (sizeDp * 0.16f).dp),
            )
        }
    }
}

/** Thin dial, hour and minute hands; no second hand (it would be the brightest moving thing at night). */
@Composable
private fun AnalogClock(now: LocalDateTime, ink: Ink, modifier: Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = center
        drawCircle(ink.faint, r - 2.dp.toPx(), c, style = Stroke(2.dp.toPx()))
        for (i in 0 until 12) {
            rotate(i * 30f, c) {
                val long = i % 3 == 0
                drawLine(ink.soft, Offset(c.x, c.y - r + 14.dp.toPx()), Offset(c.x, c.y - r + (if (long) 40 else 26).dp.toPx()), (if (long) 5 else 3).dp.toPx(), StrokeCap.Round)
            }
        }
        val minuteAngle = now.minute * 6f
        val hourAngle = (now.hour % 12) * 30f + now.minute * 0.5f
        rotate(hourAngle, c) { drawLine(ink.main, c, Offset(c.x, c.y - r * 0.5f), 12.dp.toPx(), StrokeCap.Round) }
        rotate(minuteAngle, c) { drawLine(ink.main, c, Offset(c.x, c.y - r * 0.78f), 7.dp.toPx(), StrokeCap.Round) }
        drawCircle(ink.main, 9.dp.toPx(), c)
    }
}

@Composable
private fun NightMusic(
    np: NowPlaying,
    ink: Ink,
    red: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier,
) {
    // A few updates a second are plenty for a thin line at night.
    val position by rememberPlaybackPosition(np.playback, np.track.durationMs, everyFrame = false)
    val duration = np.track.durationMs?.takeIf { it > 0 }
    Column(modifier.widthIn(max = 360.dp), horizontalAlignment = Alignment.Start) {
        val art = np.artwork
        if (art != null) {
            val image = remember(art) { art.asImageBitmap() }
            androidx.compose.foundation.Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // Dim; in red mode only its brightness survives, as red.
                colorFilter = if (red) RedOnly else null,
                modifier = Modifier
                    .size(132.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .graphicsLayer { alpha = 0.55f },
            )
            Spacer(Modifier.height(20.dp))
        }
        Text(np.track.title, style = TextStyle(fontFamily = Inter, fontSize = 26.sp, fontWeight = FontWeight.W600), color = ink.main, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(np.track.artist.orEmpty(), style = TextStyle(fontFamily = Inter, fontSize = 20.sp), color = ink.soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(18.dp))
        Canvas(Modifier.fillMaxWidth().height(3.dp)) {
            val f = duration?.let { (position.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
            drawLine(ink.faint, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height, StrokeCap.Round)
            drawLine(ink.soft, Offset(0f, size.height / 2), Offset(size.width * f, size.height / 2), size.height, StrokeCap.Round)
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(R.drawable.ic_skip_previous), stringResource(R.string.np_previous), tint = ink.soft, modifier = Modifier.size(28.dp))
            }
            PlayPauseGlyph(np.playback.isPlaying, ink, onPlayPause)
            IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(R.drawable.ic_skip_next), stringResource(R.string.np_next), tint = ink.soft, modifier = Modifier.size(28.dp))
            }
        }
    }
}

/** Split screen: one row of art, title and controls, with the thin progress line under it. */
@Composable
private fun NightMiniMusic(
    np: NowPlaying,
    ink: Ink,
    red: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier,
) {
    val position by rememberPlaybackPosition(np.playback, np.track.durationMs, everyFrame = false)
    val duration = np.track.durationMs?.takeIf { it > 0 }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val art = np.artwork
            if (art != null) {
                val image = remember(art) { art.asImageBitmap() }
                androidx.compose.foundation.Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = if (red) RedOnly else null,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).graphicsLayer { alpha = 0.55f },
                )
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(np.track.title, style = TextStyle(fontFamily = Inter, fontSize = 20.sp, fontWeight = FontWeight.W600), color = ink.main, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(np.track.artist.orEmpty(), style = TextStyle(fontFamily = Inter, fontSize = 16.sp), color = ink.soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(R.drawable.ic_skip_previous), stringResource(R.string.np_previous), tint = ink.soft, modifier = Modifier.size(24.dp))
            }
            PlayPauseGlyph(np.playback.isPlaying, ink, onPlayPause)
            IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(R.drawable.ic_skip_next), stringResource(R.string.np_next), tint = ink.soft, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().height(3.dp)) {
            val f = duration?.let { (position.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
            drawLine(ink.faint, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height, StrokeCap.Round)
            drawLine(ink.soft, Offset(0f, size.height / 2), Offset(size.width * f, size.height / 2), size.height, StrokeCap.Round)
        }
    }
}

@Composable
private fun PlayPauseGlyph(playing: Boolean, ink: Ink, onClick: () -> Unit) {
    val label = stringResource(if (playing) R.string.np_pause else R.string.np_play)
    IconButton(onClick = onClick, modifier = Modifier.size(64.dp).semantics { contentDescription = label }) {
        Canvas(Modifier.size(26.dp)) {
            if (playing) {
                val w = size.width * 0.28f
                drawRect(ink.main, Offset(size.width * 0.12f, 0f), androidx.compose.ui.geometry.Size(w, size.height))
                drawRect(ink.main, Offset(size.width * 0.6f, 0f), androidx.compose.ui.geometry.Size(w, size.height))
            } else {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.15f, 0f)
                    lineTo(size.width * 0.95f, size.height / 2)
                    lineTo(size.width * 0.15f, size.height)
                    close()
                }
                drawPath(path, ink.main)
            }
        }
    }
}

/** Keeps only brightness, in the red channel. */
private val RedOnly = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.3f, 0.59f, 0.11f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

/** The next alarm set in any clock app, formatted, or null if none. */
private fun nextAlarm(context: Context, is24: Boolean): String? {
    val info = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock ?: return null
    val time = Instant.ofEpochMilli(info.triggerTime).atZone(ZoneId.systemDefault())
    return time.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", Locale.getDefault()))
}

/**
 * Holds the window brightness at [level] (0..1), easing between levels over a couple of seconds,
 * and hands brightness back to the system when the night screen goes away.
 */
@Composable
private fun WindowBrightness(level: Float) {
    val activity = LocalContext.current as? Activity ?: return
    val animated = remember { mutableFloatStateOf(level) }
    val eased by animateFloatAsState(level, tween(1_500), label = "nightBrightness")
    animated.floatValue = eased
    LaunchedEffect(activity) {
        snapshotFlow { animated.floatValue }.collect { value ->
            activity.window.attributes = activity.window.attributes.apply { screenBrightness = value.coerceIn(0.01f, 1f) }
        }
    }
    DisposableEffect(activity) {
        onDispose {
            activity.window.attributes = activity.window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }
}

/** Ambient light in lux, or null when there's no light sensor. */
@Composable
private fun rememberAmbientLux(): State<Float?> {
    val context = LocalContext.current
    val lux = remember { mutableStateOf<Float?>(null) }
    DisposableEffect(context) {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_LIGHT)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Smoothed, so a passing shadow doesn't flip the tint.
                val v = event.values.firstOrNull() ?: return
                lux.value = lux.value?.let { it * 0.8f + v * 0.2f } ?: v
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { manager?.unregisterListener(listener) }
    }
    return lux
}

/**
 * The night layout's own slow drift: every [DRIFT_EVERY_MS] it eases to a new spot within
 * ±[DRIFT_RANGE], so the big digits never sit on the same pixels for hours, even with the
 * app-wide pixel shift turned off.
 */
@Composable
internal fun rememberNightDrift(enabled: Boolean): State<IntOffset> {
    val density = LocalDensity.current
    val range = with(density) { DRIFT_RANGE.toPx() }
    var target by remember { mutableStateOf(IntOffset.Zero) }
    LaunchedEffect(enabled) {
        val random = Random(System.nanoTime())
        while (true) {
            delay(DRIFT_EVERY_MS)
            // Reduced motion: still move (burn-in), just without easing.
            target = IntOffset((random.nextFloat() * 2 - 1).times(range).roundToInt(), (random.nextFloat() * 2 - 1).times(range).roundToInt())
        }
    }
    return androidx.compose.animation.core.animateIntOffsetAsState(
        target,
        if (enabled) tween(4_000) else tween(0),
        label = "nightDrift",
    )
}


// region Previews

@Composable
private fun NightPreview(size: DeskSize, playing: Boolean = true) {
    val fake = FakeData.tracks[0]
    val np = NowPlaying(
        track = fake.track,
        playback = PlaybackInfo(status = PlaybackStatus.PLAYING, positionMs = 60_000, updatedAtElapsedMs = 0),
        sourcePackage = fake.sourcePackage,
        sourceLabel = fake.sourceLabel,
        colors = fake.colors,
    )
    DeskDeckTheme {
        CompositionLocalProvider(LocalDeskSize provides size) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                NightLayout(
                    nowPlaying = np.takeIf { playing },
                    settings = NightSettings(),
                    ink = WhiteInk,
                    red = false,
                    hasNewNotifications = true,
                    onPlayPause = {},
                    onNext = {},
                    onPrevious = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Preview(name = "Night – expanded", widthDp = 1317, heightDp = 823)
@Composable
private fun NightExpandedPreview() = NightPreview(DeskSize.EXPANDED)

@Preview(name = "Night – medium (half split)", widthDp = 650, heightDp = 823)
@Composable
private fun NightMediumPreview() = NightPreview(DeskSize.MEDIUM)

@Preview(name = "Night – compact (pop-up)", widthDp = 560, heightDp = 340)
@Composable
private fun NightCompactPreview() = NightPreview(DeskSize.COMPACT)

@Preview(name = "Night – compact, nothing playing", widthDp = 430, heightDp = 823)
@Composable
private fun NightCompactIdlePreview() = NightPreview(DeskSize.COMPACT, playing = false)

// endregion
