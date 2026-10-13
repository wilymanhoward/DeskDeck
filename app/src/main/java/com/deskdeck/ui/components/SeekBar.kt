package com.deskdeck.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.deskdeck.R
import com.deskdeck.ui.common.formatPlaybackTime
import com.deskdeck.ui.theme.LocalMotion
import com.deskdeck.ui.theme.MutedText

private val TrackHeight = 6.dp
private val ThumbRadius = 7.dp
private val TrackHeightTouched = 12.dp

/**
 * Slim seek bar with elapsed/total time. The per-frame [position] is read only inside the draw
 * lambda, so playback progress redraws the bar without recomposing it; the time labels recompose
 * once per second.
 */
@Composable
fun SeekBar(
    position: State<Long>,
    durationMs: Long?,
    seekEnabled: Boolean,
    isLive: Boolean,
    activeColor: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = durationMs?.takeIf { it > 0 }
    val canSeek = seekEnabled && duration != null && !isLive
    val currentOnSeek by rememberUpdatedState(onSeek)

    var dragFraction by remember { mutableStateOf<Float?>(null) }
    // Apple-style pill: no thumb; the bar grows taller while touched.
    val trackHeight by animateDpAsState(
        targetValue = if (dragFraction != null) TrackHeightTouched else TrackHeight,
        animationSpec = LocalMotion.current.snappy(),
        label = "trackHeight",
    )
    val elapsedSeconds by remember { derivedStateOf { position.value / 1000 } }
    val shownMs = dragFraction?.let { f -> duration?.let { (f * it).toLong() } } ?: (elapsedSeconds * 1000)

    val description = stringResource(R.string.np_seek_bar)
    val totalText = when {
        isLive -> stringResource(R.string.np_live)
        duration != null -> formatPlaybackTime(duration)
        else -> "--:--"
    }

    Column(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .then(
                    if (canSeek) {
                        Modifier
                            .pointerInput(duration) {
                                detectTapGestures { offset ->
                                    val f = fractionAt(offset.x, size.width.toFloat(), ThumbRadius.toPx())
                                    currentOnSeek((f * duration).toLong())
                                }
                            }
                            .pointerInput(duration) {
                                val inset = ThumbRadius.toPx()
                                detectHorizontalDragGestures(
                                    onDragStart = { dragFraction = fractionAt(it.x, size.width.toFloat(), inset) },
                                    onHorizontalDrag = { change, _ ->
                                        change.consume()
                                        dragFraction = fractionAt(change.position.x, size.width.toFloat(), inset)
                                    },
                                    onDragEnd = {
                                        dragFraction?.let { currentOnSeek((it * duration).toLong()) }
                                        dragFraction = null
                                    },
                                    onDragCancel = { dragFraction = null },
                                )
                            }
                    } else {
                        Modifier
                    },
                )
                .semantics {
                    contentDescription = description
                    stateDescription = "${formatPlaybackTime(elapsedSeconds * 1000)} / $totalText"
                    if (duration != null) {
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            current = (elapsedSeconds * 1000f / duration).coerceIn(0f, 1f),
                            range = 0f..1f,
                        )
                    }
                    if (canSeek) {
                        setProgress { target ->
                            currentOnSeek((target.coerceIn(0f, 1f) * duration).toLong())
                            true
                        }
                    }
                }
                .drawBehind {
                    val inset = ThumbRadius.toPx()
                    val startX = inset
                    val endX = size.width - inset
                    val centerY = size.height / 2
                    val stroke = trackHeight.toPx()
                    val fraction = when {
                        isLive -> 1f
                        dragFraction != null -> dragFraction!!
                        duration != null -> (position.value.toFloat() / duration).coerceIn(0f, 1f)
                        else -> 0f
                    }
                    val progressX = startX + (endX - startX) * fraction
                    drawLine(activeColor.copy(alpha = 0.22f), Offset(startX, centerY), Offset(endX, centerY), stroke, StrokeCap.Round)
                    drawLine(activeColor, Offset(startX, centerY), Offset(progressX, centerY), stroke, StrokeCap.Round)
                },
        )
        Row(Modifier.fillMaxWidth()) {
            TimeLabel(if (isLive) "" else formatPlaybackTime(shownMs))
            Spacer(Modifier.weight(1f))
            TimeLabel(totalText)
        }
    }
}

@Composable
private fun TimeLabel(text: String) {
    Text(
        text = text,
        // Tabular figures keep the label from jittering as digits change.
        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
        color = MutedText,
    )
}

private fun fractionAt(x: Float, width: Float, inset: Float): Float {
    val usable = (width - 2 * inset).coerceAtLeast(1f)
    return ((x - inset) / usable).coerceIn(0f, 1f)
}
