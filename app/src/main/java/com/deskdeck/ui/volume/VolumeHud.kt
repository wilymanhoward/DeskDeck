package com.deskdeck.ui.volume

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deskdeck.R
import com.deskdeck.burnin.LocalInteractionTracker
import com.deskdeck.domain.SpeakerLevel
import com.deskdeck.domain.VolumeState
import com.deskdeck.ui.components.SpeakerIcon
import com.deskdeck.ui.theme.ActiveLyricWhite
import com.deskdeck.ui.theme.GlassBorder
import com.deskdeck.ui.theme.GlassTintStrong
import com.deskdeck.ui.night.LocalNightMode
import com.deskdeck.ui.theme.LocalMotion
import com.deskdeck.ui.theme.glass
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val HudWidth = 64.dp
private val HudHeight = 220.dp
private val IconSize = 28.dp
private val IconBottom = 18.dp

/**
 * DeskDeck's volume HUD: a vertical glass pill on the right edge that springs in on any volume
 * change, fills with the level, and fades out [HIDE_DELAY_MS] after the last change. It can be
 * dragged to set the volume, and tapping the speaker toggles mute.
 */
@Composable
fun VolumeHud(modifier: Modifier = Modifier, viewModel: VolumeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val motion = LocalMotion.current
    val view = LocalView.current
    val tracker = LocalInteractionTracker.current

    var visible by remember { mutableStateOf(false) }
    var lastChangeAt by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            visible = true
            lastChangeAt = System.nanoTime()
            // DeskDeck's own keys wake a dimmed or ambient screen; another device's change doesn't.
            if (event.userInitiated) tracker.onInteraction(force = true)
            if (event.hitLimit) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
    LaunchedEffect(lastChangeAt, dragging) {
        if (!dragging) {
            delay(HIDE_DELAY_MS)
            visible = false
        }
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        AnimatedVisibility(
            visible = visible,
            enter = if (motion.reduced) {
                fadeIn(motion.fade())
            } else {
                fadeIn(motion.fade()) +
                    slideInHorizontally(motion.bouncy()) { it / 2 } +
                    scaleIn(motion.bouncy(), initialScale = 0.85f, transformOrigin = TransformOrigin(1f, 0.5f))
            },
            exit = if (motion.reduced) fadeOut(motion.fade()) else fadeOut(motion.fade()) + slideOutHorizontally(motion.gentle()) { it / 3 },
            modifier = Modifier.padding(end = 28.dp),
        ) {
            VolumePill(
                state = state,
                onFraction = viewModel::setFraction,
                onToggleMute = viewModel::toggleMute,
                onDragging = { dragging = it; lastChangeAt = System.nanoTime() },
            )
        }
    }
}

@Composable
private fun VolumePill(
    state: VolumeState,
    onFraction: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onDragging: (Boolean) -> Unit,
) {
    val motion = LocalMotion.current
    val currentOnFraction by rememberUpdatedState(onFraction)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val target = dragFraction ?: state.fraction
    val fill by animateFloatAsState(target, motion.snappy(), label = "volumeFill")
    val level = if (dragFraction != null) SpeakerLevel.of(target, muted = false) else state.speaker

    val description = stringResource(R.string.volume_hud)
    val percent = (target * 100).roundToInt()
    val stateText = if (state.muted) stringResource(R.string.volume_muted) else stringResource(R.string.volume_percent, percent)
    val iconBand = (IconBottom + IconSize) / HudHeight
    // At night: a dim fill and a darker pill, so a volume change doesn't flash the room.
    val nightMode = LocalNightMode.current
    val fillColor = if (nightMode) ActiveLyricWhite.copy(alpha = 0.28f) else ActiveLyricWhite.copy(alpha = 0.92f)

    Box(
        Modifier
            .size(HudWidth, HudHeight)
            .glass(RoundedCornerShape(HudWidth / 2), tint = if (nightMode) Color.Black.copy(alpha = 0.9f) else GlassTintStrong, border = if (nightMode) Color.White.copy(alpha = 0.06f) else GlassBorder)
            // Level fill from the bottom, read in the draw phase.
            .drawBehind {
                val h = size.height * fill
                drawRect(fillColor, Offset(0f, size.height - h), Size(size.width, h))
            }
            .pointerInput(state.fixed) {
                if (state.fixed) return@pointerInput
                fun fractionAt(y: Float) = (1f - y / size.height).coerceIn(0f, 1f)
                detectVerticalDragGestures(
                    onDragStart = { dragFraction = fractionAt(it.y); onDragging(true) },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        dragFraction = fractionAt(change.position.y).also(currentOnFraction)
                    },
                    onDragEnd = { dragFraction = null; onDragging(false) },
                    onDragCancel = { dragFraction = null; onDragging(false) },
                )
            }
            .semantics {
                contentDescription = description
                stateDescription = stateText
                liveRegion = LiveRegionMode.Polite
                progressBarRangeInfo = ProgressBarRangeInfo(state.fraction, 0f..1f)
                if (!state.fixed) setProgress { currentOnFraction(it.coerceIn(0f, 1f)); true }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        SpeakerIcon(
            level = level,
            color = if (nightMode) ActiveLyricWhite.copy(alpha = 0.35f) else ActiveLyricWhite,
            fillColor = if (nightMode) ActiveLyricWhite.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.7f),
            // How much of the icon the fill covers, so the glyph stays visible on white.
            fill = ((fill - IconBottom / HudHeight) / (iconBand - IconBottom / HudHeight)).coerceIn(0f, 1f),
            modifier = Modifier
                .padding(bottom = IconBottom)
                .size(IconSize)
                .pointerInput(Unit) { detectTapGestures { onToggleMute() } },
        )
    }
}

private const val HIDE_DELAY_MS = 1_500L
