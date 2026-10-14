package com.deskdeck.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.deskdeck.R
import com.deskdeck.domain.PlaybackInfo
import com.deskdeck.domain.RepeatMode
import com.deskdeck.ui.dashboard.PlayerActions
import com.deskdeck.ui.theme.ActiveLyricWhite
import com.deskdeck.ui.theme.CappedWhite
import com.deskdeck.ui.theme.Dimens
import com.deskdeck.ui.theme.LocalMotion
import com.deskdeck.ui.theme.pressScale

@Composable
fun PlaybackControls(
    playback: PlaybackInfo,
    palette: CardPalette,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val narrow = maxWidth < Dimens.minTouch * 2 + Dimens.skipButton * 2 + Dimens.playButton
        val shuffle: @Composable () -> Unit = {
        ToggleControl(
            iconRes = R.drawable.ic_shuffle,
            active = playback.shuffleOn,
            enabled = playback.shuffleSupported,
            description = stringResource(
                when {
                    !playback.shuffleSupported -> R.string.np_shuffle_unavailable
                    playback.shuffleOn -> R.string.np_shuffle_on
                    else -> R.string.np_shuffle_off
                },
            ),
            accent = palette.accent,
            onClick = actions::onToggleShuffle,
        )
        }
        val repeat: @Composable () -> Unit = {
        ToggleControl(
            iconRes = if (playback.repeatMode == RepeatMode.ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat,
            active = playback.repeatMode != RepeatMode.OFF,
            enabled = playback.repeatSupported,
            description = stringResource(
                when {
                    !playback.repeatSupported -> R.string.np_repeat_unavailable
                    playback.repeatMode == RepeatMode.ALL -> R.string.np_repeat_all
                    playback.repeatMode == RepeatMode.ONE -> R.string.np_repeat_one
                    else -> R.string.np_repeat_off
                },
            ),
            accent = palette.accent,
            onClick = actions::onCycleRepeat,
        )        }
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                if (!narrow) shuffle()
        TransportButton(
            iconRes = R.drawable.ic_skip_previous,
            description = stringResource(R.string.np_previous),
            enabled = playback.canSkipPrevious,
            onClick = actions::onSkipPrevious,
        )
        PlayPauseButton(isPlaying = playback.isPlaying, onClick = actions::onPlayPause)
        TransportButton(
            iconRes = R.drawable.ic_skip_next,
            description = stringResource(R.string.np_next),
            enabled = playback.canSkipNext,
            onClick = actions::onSkipNext,
        )
                if (!narrow) repeat()
            }
            if (narrow) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    shuffle()
                    repeat()
                }
            }
        }
    }
}

/** Large glyph, no container (Apple Music style), morphing between pause bars and play triangle. */
@Composable
internal fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit, size: Dp = Dimens.playButton) {
    val interaction = remember { MutableInteractionSource() }
    val motion = LocalMotion.current
    val toPlay by animateFloatAsState(if (isPlaying) 0f else 1f, motion.snappy(), label = "playMorph")
    val description = stringResource(if (isPlaying) R.string.np_pause else R.string.np_play)
    Box(
        modifier = Modifier
            .size(size)
            .pressScale(interaction)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = ripple(color = CappedWhite), role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        PlayPauseGlyph(progressToPlay = { toPlay }, color = ActiveLyricWhite, modifier = Modifier.size(size * 0.6f))
    }
}

/**
 * Pause (two bars) ↔ play (triangle) as one shape: the triangle is cut into two quads at the
 * same split as the two bars, and each corner is interpolated, so the bars visibly bend into the
 * triangle instead of swapping icons. [progressToPlay] is read in the draw phase.
 */
@Composable
fun PlayPauseGlyph(progressToPlay: () -> Float, color: Color, modifier: Modifier = Modifier) {
    val path = remember { Path() }
    Canvas(modifier) {
        val p = progressToPlay().coerceIn(0f, 1f)
        val w = size.width
        val h = size.height
        fun pt(pause: Offset, play: Offset) = Offset(lerp(pause.x, play.x, p) * w, lerp(pause.y, play.y, p) * h)
        path.reset()
        for (quad in MorphQuads) {
            val pts = quad.pause.indices.map { pt(quad.pause[it], quad.play[it]) }
            path.moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
            path.close()
        }
        drawPath(path, color)
        // Same-color stroke with round joins softens every corner, like SF Symbols' filled glyphs.
        drawPath(path, color, style = Stroke(width = w * 0.06f, join = StrokeJoin.Round))
    }
}

private class MorphQuad(val pause: List<Offset>, val play: List<Offset>)

// Unit-square corners (top-left, top-right, bottom-right, bottom-left). The play triangle
// (0.30,0.17)-(0.86,0.5)-(0.30,0.83) is split at x = 0.58.
private val MorphQuads = listOf(
    MorphQuad(
        pause = listOf(Offset(0.24f, 0.18f), Offset(0.42f, 0.18f), Offset(0.42f, 0.82f), Offset(0.24f, 0.82f)),
        play = listOf(Offset(0.30f, 0.17f), Offset(0.58f, 0.335f), Offset(0.58f, 0.665f), Offset(0.30f, 0.83f)),
    ),
    MorphQuad(
        pause = listOf(Offset(0.58f, 0.18f), Offset(0.76f, 0.18f), Offset(0.76f, 0.82f), Offset(0.58f, 0.82f)),
        play = listOf(Offset(0.58f, 0.335f), Offset(0.86f, 0.5f), Offset(0.86f, 0.5f), Offset(0.58f, 0.665f)),
    ),
)

@Composable
internal fun TransportButton(
    iconRes: Int,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    size: Dp = Dimens.skipButton,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(size)
            .pressScale(interaction)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = ripple(color = CappedWhite), enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = ActiveLyricWhite.copy(alpha = if (enabled) 1f else 0.3f),
            modifier = Modifier.size(size * 0.56f),
        )
    }
}

/** Previous, play/pause and next at a smaller size, for the mini player. */
@Composable
fun MiniTransport(playback: PlaybackInfo, actions: PlayerActions, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        TransportButton(
            iconRes = R.drawable.ic_skip_previous,
            description = stringResource(R.string.np_previous),
            enabled = playback.canSkipPrevious,
            onClick = actions::onSkipPrevious,
            size = Dimens.minTouch,
        )
        PlayPauseButton(isPlaying = playback.isPlaying, onClick = actions::onPlayPause, size = 64.dp)
        TransportButton(
            iconRes = R.drawable.ic_skip_next,
            description = stringResource(R.string.np_next),
            enabled = playback.canSkipNext,
            onClick = actions::onSkipNext,
            size = Dimens.minTouch,
        )
    }
}

/** Shuffle/repeat: accent with a dot when active, dimmed when the player doesn't support it. */
@Composable
private fun ToggleControl(
    iconRes: Int,
    active: Boolean,
    enabled: Boolean,
    description: String,
    accent: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val tint = when {
        !enabled -> CappedWhite.copy(alpha = 0.25f)
        active -> accent
        else -> CappedWhite.copy(alpha = 0.75f)
    }
    Box(
        modifier = Modifier
            .size(Dimens.minTouch)
            .pressScale(interaction)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = ripple(color = CappedWhite), enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painter = painterResource(iconRes), contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .size(4.dp)
                    .background(if (active && enabled) accent else Color.Transparent, CircleShape),
            )
        }
    }
}
