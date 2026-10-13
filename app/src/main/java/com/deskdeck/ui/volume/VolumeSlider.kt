package com.deskdeck.ui.volume

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deskdeck.R
import com.deskdeck.domain.SpeakerLevel
import com.deskdeck.domain.VolumeState
import com.deskdeck.ui.components.SpeakerIcon
import com.deskdeck.ui.theme.Dimens
import com.deskdeck.ui.theme.LocalMotion
import com.deskdeck.ui.theme.MutedText
import kotlin.math.roundToInt

private val TrackHeight = 6.dp
private val TrackHeightTouched = 12.dp
private val TrackInset = 7.dp

/** The Now Playing card's volume row, wired to the shared [VolumeController][com.deskdeck.media.VolumeController]. */
@Composable
fun VolumeRow(modifier: Modifier = Modifier, viewModel: VolumeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    VolumeSlider(state, viewModel::setFraction, viewModel::toggleMute, modifier)
}

/** Speaker (tap to mute) and a pill track in the seek bar's style, growing while touched. */
@Composable
fun VolumeSlider(
    state: VolumeState,
    onFraction: (Float) -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnFraction by rememberUpdatedState(onFraction)
    var touched by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val trackHeight by animateDpAsState(
        targetValue = if (touched || dragFraction != null) TrackHeightTouched else TrackHeight,
        animationSpec = LocalMotion.current.snappy(),
        label = "volumeTrackHeight",
    )
    // While dragging, follow the finger rather than the stepped system level.
    val fraction = dragFraction ?: state.fraction
    val level = if (dragFraction != null) SpeakerLevel.of(fraction, muted = false) else state.speaker
    val enabled = !state.fixed

    val muteLabel = stringResource(if (state.muted) R.string.volume_unmute else R.string.volume_mute)
    val description = stringResource(R.string.volume_hud)
    val stateText = if (state.muted) stringResource(R.string.volume_muted) else stringResource(R.string.volume_percent, (fraction * 100).roundToInt())

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onToggleMute, enabled = enabled, modifier = Modifier.size(Dimens.minTouch).semantics { contentDescription = muteLabel }) {
            SpeakerIcon(level = level, color = MutedText, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier
                .weight(1f)
                .height(Dimens.minTouch)
                .then(
                    if (enabled) {
                        Modifier
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        touched = true
                                        try { tryAwaitRelease() } finally { touched = false }
                                    },
                                    onTap = { currentOnFraction(fractionAt(it.x, size.width.toFloat(), TrackInset.toPx())) },
                                )
                            }
                            .pointerInput(Unit) {
                                val inset = TrackInset.toPx()
                                detectHorizontalDragGestures(
                                    onDragStart = { dragFraction = fractionAt(it.x, size.width.toFloat(), inset) },
                                    onHorizontalDrag = { change, _ ->
                                        change.consume()
                                        dragFraction = fractionAt(change.position.x, size.width.toFloat(), inset).also(currentOnFraction)
                                    },
                                    onDragEnd = { dragFraction = null },
                                    onDragCancel = { dragFraction = null },
                                )
                            }
                    } else {
                        Modifier
                    },
                )
                .semantics {
                    contentDescription = description
                    stateDescription = stateText
                    progressBarRangeInfo = ProgressBarRangeInfo(state.fraction, 0f..1f)
                    if (enabled) setProgress { currentOnFraction(it.coerceIn(0f, 1f)); true }
                }
                .drawBehind {
                    val inset = TrackInset.toPx()
                    val centerY = size.height / 2
                    val endX = size.width - inset
                    val stroke = trackHeight.toPx()
                    val color = MutedText
                    drawLine(color.copy(alpha = 0.25f), Offset(inset, centerY), Offset(endX, centerY), stroke, StrokeCap.Round)
                    drawLine(color, Offset(inset, centerY), Offset(inset + (endX - inset) * fraction, centerY), stroke, StrokeCap.Round)
                },
        )
    }
}

private fun fractionAt(x: Float, width: Float, inset: Float): Float {
    val usable = (width - 2 * inset).coerceAtLeast(1f)
    return ((x - inset) / usable).coerceIn(0f, 1f)
}
