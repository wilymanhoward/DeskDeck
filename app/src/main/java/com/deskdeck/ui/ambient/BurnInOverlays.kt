package com.deskdeck.ui.ambient

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deskdeck.R
import com.deskdeck.burnin.Drift
import com.deskdeck.domain.NowPlaying
import com.deskdeck.domain.activeIndexAt
import com.deskdeck.ui.common.clockFormatter
import com.deskdeck.ui.common.rememberCurrentTime
import com.deskdeck.ui.common.rememberIs24Hour
import com.deskdeck.ui.dashboard.LyricsUiState
import com.deskdeck.ui.settings.LocalAppSettings
import com.deskdeck.ui.theme.TrueBlack
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay

/** Black scrim over the dashboard; the first tap only wakes (it never reaches the controls below). */
@Composable
fun DimOverlay(visible: Boolean, level: Float, onWake: () -> Unit) {
    val alpha by animateFloatAsState(if (visible) level else 0f, tween(if (visible) 2_000 else 300), label = "dim")
    if (alpha <= 0f) return
    val description = stringResource(R.string.burnin_tap_to_wake)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = alpha))
            .semantics { contentDescription = description }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onWake),
    )
}

/** Caps overall brightness. Draw-only: it doesn't intercept touches. */
@Composable
fun BrightnessCapOverlay(cap: Float) {
    if (cap >= 1f) return
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = (1f - cap).coerceIn(0f, 0.5f))),
    )
}

/** Screensaver: small art, title, artist, current lyric line and clock, drifting slowly on black. */
@Composable
fun AmbientScreen(
    nowPlaying: NowPlaying?,
    lyrics: LyricsUiState,
    position: State<Long>,
    onWake: () -> Unit,
) {
    DriftingContainer(onWake = onWake) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val art = nowPlaying?.artwork
            if (art != null) {
                val image = remember(art) { art.asImageBitmap() }
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(112.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .alpha(0.55f),
                )
                Spacer(Modifier.width(24.dp))
            }
            Column(Modifier.widthIn(max = 520.dp)) {
                AmbientClock(alpha = 0.55f, fontSize = 40)
                if (nowPlaying != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = nowPlaying.track.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    nowPlaying.track.artist?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.4f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    CurrentLyricLine(lyrics, position)
                }
            }
        }
    }
}

/** Nothing playing for a while: a very dim clock, still drifting. */
@Composable
fun PausedClockScreen(onWake: () -> Unit) {
    DriftingContainer(onWake = onWake) {
        AmbientClock(alpha = 0.3f, fontSize = 72)
    }
}

@Composable
private fun AmbientClock(alpha: Float, fontSize: Int) {
    val settings = LocalAppSettings.current
    val is24Hour = rememberIs24Hour()
    val now by rememberCurrentTime(withSeconds = settings.clockShowSeconds)
    val formatter = remember(is24Hour, settings.clockShowSeconds) { clockFormatter(is24Hour, settings.clockShowSeconds) }
    Text(
        text = now.format(formatter),
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Light,
        color = Color.White.copy(alpha = alpha),
        maxLines = 1,
    )
}

@Composable
private fun CurrentLyricLine(lyrics: LyricsUiState, position: State<Long>) {
    val synced = lyrics as? LyricsUiState.Synced ?: return
    val line by remember(synced) {
        derivedStateOf {
            synced.lines.getOrNull(synced.lines.activeIndexAt(position.value + synced.offsetMs))?.text
        }
    }
    val text = line?.takeIf { it.isNotBlank() } ?: return
    Spacer(Modifier.height(16.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        color = Color.White.copy(alpha = 0.7f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Black full-screen container whose content wanders slowly around the screen: a couple of dp per
 * second toward a random target, as discrete steps (no per-frame animation, so it costs almost
 * nothing). Any tap wakes.
 */
@Composable
private fun DriftingContainer(onWake: () -> Unit, content: @Composable () -> Unit) {
    val description = stringResource(R.string.burnin_tap_to_wake)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(TrueBlack)
            .semantics { contentDescription = description }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onWake),
    ) {
        val density = LocalDensity.current
        val bounds = with(density) { IntSize(maxWidth.roundToPx(), maxHeight.roundToPx()) }
        var contentSize by remember { mutableStateOf(IntSize.Zero) }
        var position by remember { mutableStateOf(IntOffset.Zero) }
        val stepPx = with(density) { DRIFT_STEP.toPx() }

        LaunchedEffect(bounds, contentSize) {
            if (contentSize == IntSize.Zero) return@LaunchedEffect
            val maxX = (bounds.width - contentSize.width).toFloat()
            val maxY = (bounds.height - contentSize.height).toFloat()
            val random = Random(SystemClock.elapsedRealtime())
            var current = Drift.clamp(Drift.Point(position.x.toFloat(), position.y.toFloat()), maxX, maxY)
            if (position == IntOffset.Zero) current = Drift.randomPoint(maxX, maxY, random)
            var target = Drift.randomPoint(maxX, maxY, random)
            while (true) {
                position = IntOffset(current.x.roundToInt(), current.y.roundToInt())
                delay(DRIFT_TICK_MS)
                current = Drift.step(current, target, stepPx)
                if (current == target) target = Drift.randomPoint(maxX, maxY, random)
            }
        }

        Box(
            Modifier
                .offset { position }
                .onSizeChanged { contentSize = it },
        ) {
            content()
        }
    }
}

private val DRIFT_STEP = 2.dp
private const val DRIFT_TICK_MS = 1_000L
