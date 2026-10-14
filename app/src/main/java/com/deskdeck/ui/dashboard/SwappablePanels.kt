package com.deskdeck.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.offset
import com.deskdeck.ui.theme.Dimens
import com.deskdeck.ui.theme.LocalMotion
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** The Now Playing card's share of the width; the lyrics get the rest. */
private const val CARD_SHARE = 0.47f

/**
 * The Now Playing card and the lyrics side by side, swappable by hand: long-press the card to
 * pick it up, drag it across, and the lyrics slide over to the other side as soon as the card
 * passes the middle. Let go and the card settles into its slot; the side is saved through
 * [onCardSideChange].
 *
 * Both panels are positioned by animated offsets read in the placement phase, so dragging and the
 * lyrics' slide never recompose either panel.
 */
@Composable
fun SwappablePanels(
    cardOnRight: Boolean,
    onCardSideChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    card: @Composable (Modifier) -> Unit,
    lyrics: @Composable (Modifier) -> Unit,
) {
    val motion = LocalMotion.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentOnChange by rememberUpdatedState(onCardSideChange)
    val currentCardOnRight by rememberUpdatedState(cardOnRight)

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val total = constraints.maxWidth.toFloat()
        val gap = with(density) { Dimens.zoneGap.toPx() }
        val cardWidth = (total - gap) * CARD_SHARE
        val lyricsWidth = total - gap - cardWidth
        fun cardSlot(right: Boolean) = if (right) lyricsWidth + gap else 0f
        fun lyricsSlot(cardRight: Boolean) = if (cardRight) 0f else cardWidth + gap

        val cardX = remember { Animatable(cardSlot(cardOnRight)) }
        val lyricsX = remember { Animatable(lyricsSlot(cardOnRight)) }
        var dragging by remember { mutableStateOf(false) }
        // Where the card would land if dropped now; the lyrics follow it live.
        var landsRight by remember { mutableStateOf(cardOnRight) }
        val lift by animateFloatAsState(if (dragging && !motion.reduced) 1f else 0f, motion.snappy(), label = "cardLift")

        // Saved side or window size changed (not by dragging): settle both panels.
        LaunchedEffect(cardOnRight, cardWidth, lyricsWidth) {
            if (dragging) return@LaunchedEffect
            landsRight = cardOnRight
            launch { cardX.animateTo(cardSlot(cardOnRight), motion.gentle()) }
            lyricsX.animateTo(lyricsSlot(cardOnRight), motion.gentle())
        }
        LaunchedEffect(landsRight, lyricsWidth) {
            lyricsX.animateTo(lyricsSlot(landsRight), if (motion.reduced) motion.fade() else motion.gentle())
        }

        fun drop() {
            dragging = false
            scope.launch { cardX.animateTo(cardSlot(landsRight), if (motion.reduced) motion.fade() else motion.bouncy()) }
            if (landsRight != currentCardOnRight) currentOnChange(landsRight)
        }

        val cardDp = with(density) { cardWidth.toDp() }
        val lyricsDp = with(density) { lyricsWidth.toDp() }
        lyrics(
            Modifier
                .width(lyricsDp)
                .fillMaxHeight()
                .offset { IntOffset(lyricsX.value.roundToInt(), 0) },
        )
        Box(
            Modifier
                .width(cardDp)
                .fillMaxHeight()
                .zIndex(1f)
                .offset { IntOffset(cardX.value.roundToInt(), 0) }
                .pointerInput(cardWidth, lyricsWidth) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            dragging = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            val x = (cardX.value + amount.x).coerceIn(0f, total - cardWidth)
                            scope.launch { cardX.snapTo(x) }
                            val right = x + cardWidth / 2 > total / 2
                            if (right != landsRight) {
                                landsRight = right
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragEnd = ::drop,
                        onDragCancel = ::drop,
                    )
                }
                // Picked up: a touch larger, with a deeper shadow.
                .graphicsLayer {
                    val scale = 1f + 0.025f * lift
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = 28.dp.toPx() * lift
                    shape = RoundedCornerShape(Dimens.cardCorner)
                    clip = false
                },
        ) {
            card(Modifier.fillMaxHeight().width(cardDp))
        }
    }
}
