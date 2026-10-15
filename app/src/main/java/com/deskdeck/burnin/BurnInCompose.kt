package com.deskdeck.burnin

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import android.view.View
import androidx.compose.runtime.Composable
import java.util.WeakHashMap
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import com.deskdeck.data.settings.BurnInSettings
import com.deskdeck.data.settings.KeepScreenOn
import kotlin.random.Random
import kotlinx.coroutines.delay

/** Last touch time (elapsedRealtime), shared by everything that reacts to idleness. */
class InteractionTracker {
    var lastInteractionMs by mutableLongStateOf(SystemClock.elapsedRealtime())
        private set

    /** Throttled: pointer moves arrive many times a second, idle logic only needs ~1 s resolution. */
    fun onInteraction(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (force || now - lastInteractionMs >= 1_000) lastInteractionMs = now
    }
}

val LocalInteractionTracker = staticCompositionLocalOf { InteractionTracker() }

/** Records every touch anywhere below this node without consuming it. */
fun Modifier.observeInteractions(tracker: InteractionTracker): Modifier = pointerInput(tracker) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial)
            tracker.onInteraction()
        }
    }
}

/** Re-evaluates [BurnInPolicy] whenever an input changes and again exactly when it next would. */
@Composable
fun rememberBurnInDecision(
    settings: BurnInSettings,
    keepScreenOn: KeepScreenOn,
    isPlaying: Boolean,
    enabled: Boolean,
): BurnInDecision {
    val tracker = LocalInteractionTracker.current
    val lastInteraction = tracker.lastInteractionMs
    // Restarts whenever playback stops; app start without playback counts as "stopped now".
    val notPlayingSince = remember(isPlaying) { if (isPlaying) null else SystemClock.elapsedRealtime() }

    val active = BurnInDecision(DisplayMode.ACTIVE, keepScreenOn = true, nextChangeAtMs = null)
    var decision by remember { mutableStateOf(active) }
    LaunchedEffect(lastInteraction, isPlaying, notPlayingSince, settings, keepScreenOn, enabled) {
        while (true) {
            val now = SystemClock.elapsedRealtime()
            val d = if (enabled) {
                BurnInPolicy.decide(BurnInInput(now, lastInteraction, isPlaying, notPlayingSince, settings, keepScreenOn))
            } else {
                active
            }
            decision = d
            val next = d.nextChangeAtMs ?: break
            delay((next - SystemClock.elapsedRealtime()).coerceAtLeast(50))
        }
    }
    return decision
}

/** Animated whole-UI offset in px, moving along [PixelShift]'s orbit. */
@Composable
fun rememberPixelShift(enabled: Boolean, amplitudeDp: Int): State<Offset> {
    val amplitudePx = with(LocalDensity.current) { amplitudeDp.toDp().toPx() }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    LaunchedEffect(enabled, amplitudePx) {
        if (!enabled) {
            offset.animateTo(Offset.Zero, tween(PixelShift.MOVE_DURATION_MS))
            return@LaunchedEffect
        }
        val random = Random(SystemClock.elapsedRealtime())
        var angle = random.nextDouble(0.0, 2 * Math.PI)
        while (true) {
            val step = PixelShift.next(angle, random)
            angle = step.angleRad
            val (x, y) = PixelShift.offset(step, amplitudePx)
            offset.animateTo(Offset(x, y), tween(PixelShift.MOVE_DURATION_MS, easing = FastOutSlowInEasing))
            delay(PixelShift.INTERVAL_MS - PixelShift.MOVE_DURATION_MS + random.nextLong(PixelShift.INTERVAL_JITTER_MS))
        }
    }
    return offset.asState()
}

/**
 * Sidebar icon opacity. Updated in tiny steps every few seconds instead of animated, so it costs
 * no frames; at this speed the steps are invisible.
 */
@Composable
fun rememberRailOpacity(enabled: Boolean): State<Float> {
    val alpha = remember { mutableFloatStateOf(1f) }
    LaunchedEffect(enabled) {
        if (!enabled) {
            alpha.floatValue = 1f
            return@LaunchedEffect
        }
        while (true) {
            alpha.floatValue = RailOpacity.at(System.currentTimeMillis())
            delay(5_000)
        }
    }
    return alpha
}

/**
 * Holds FLAG_KEEP_SCREEN_ON (via the view) while [enabled]. Counted per view: several screens can
 * ask at once (dashboard, night screen, Settings), and one going away mustn't let the screen
 * time out under another that still wants it on.
 */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        keepOnCounts[view] = (keepOnCounts[view] ?: 0) + 1
        view.keepScreenOn = true
        onDispose {
            val left = ((keepOnCounts[view] ?: 1) - 1).coerceAtLeast(0)
            keepOnCounts[view] = left
            view.keepScreenOn = left > 0
        }
    }
}

private val keepOnCounts = WeakHashMap<View, Int>()
