package com.deskdeck.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deskdeck.R
import com.deskdeck.domain.PlaybackInfo
import com.deskdeck.domain.RepeatMode
import com.deskdeck.ui.dashboard.PlayerActions
import com.deskdeck.ui.theme.CappedWhite
import com.deskdeck.ui.theme.Dimens

@Composable
fun PlaybackControls(
    playback: PlaybackInfo,
    palette: CardPalette,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        TransportButton(
            iconRes = R.drawable.ic_skip_previous,
            description = stringResource(R.string.np_previous),
            enabled = playback.canSkipPrevious,
            onClick = actions::onSkipPrevious,
        )
        PlayPauseButton(
            isPlaying = playback.isPlaying,
            container = palette.accent,
            content = palette.onAccent,
            onClick = actions::onPlayPause,
        )
        TransportButton(
            iconRes = R.drawable.ic_skip_next,
            description = stringResource(R.string.np_next),
            enabled = playback.canSkipNext,
            onClick = actions::onSkipNext,
        )
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
        )
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "playScale",
    )
    // Rounded square while playing, circle while paused, as in Android Auto.
    val corner by animateDpAsState(
        targetValue = if (isPlaying) 28.dp else Dimens.playButton / 2,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "playCorner",
    )
    val description = stringResource(if (isPlaying) R.string.np_pause else R.string.np_play)
    Box(
        modifier = Modifier
            .size(Dimens.playButton)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(corner))
            .background(container)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = content),
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), initialScale = 0.6f) + fadeIn()) togetherWith
                    (scaleOut(targetScale = 0.6f) + fadeOut())
            },
            label = "playIcon",
        ) { playing ->
            Icon(
                painter = painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

@Composable
private fun TransportButton(
    iconRes: Int,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    size: Dp = Dimens.skipButton,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            tint = CappedWhite.copy(alpha = if (enabled) 1f else 0.3f),
            modifier = Modifier.size(42.dp),
        )
    }
}

/** Shuffle/repeat: tinted with a dot when active, dimmed when the player doesn't support it. */
@Composable
private fun ToggleControl(
    iconRes: Int,
    active: Boolean,
    enabled: Boolean,
    description: String,
    accent: Color,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> CappedWhite.copy(alpha = 0.25f)
        active -> accent
        else -> CappedWhite.copy(alpha = 0.75f)
    }
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(Dimens.minTouch)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = description,
                tint = tint,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .size(4.dp)
                    .background(if (active && enabled) accent else Color.Transparent, CircleShape),
            )
        }
    }
}
