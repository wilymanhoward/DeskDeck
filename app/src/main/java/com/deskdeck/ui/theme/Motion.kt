package com.deskdeck.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * MotionSpec: the one place DeskDeck's animation timing lives. Components ask for an intent
 * (gentle, snappy, bouncy, fade …) instead of inventing numbers, so the app moves consistently.
 *
 * Choices:
 * - **Springs over tweens** for anything a finger causes (presses, the seek pill, the rail ring):
 *   springs keep their velocity when interrupted, so quick repeated taps never look jumpy.
 * - **gentle** (critically damped, low stiffness) for big calm changes such as album art scaling
 *   with play/pause; **snappy** for state changes that should feel immediate; **bouncy** only
 *   for small press feedback, where a little overshoot reads as "tactile".
 * - **Long tweens for color** (palette crossfades, background): color changing on a spring looks
 *   like flicker, a slow ease looks like light.
 * - **Reduced motion**: when the system "Remove animations" setting is on (animator duration
 *   scale 0), specs become short fade-length tweens and decorative travel/scale is disabled.
 *   Ambient content repositions infrequently to preserve burn-in protection.
 *
 * Performance rule that goes with it: animated values are read in `graphicsLayer {}` or draw
 * lambdas wherever possible, so an animation redraws a layer instead of recomposing a tree.
 */
@Immutable
class MotionSpec(val reduced: Boolean) {

    fun <T> gentle(): FiniteAnimationSpec<T> =
        if (reduced) tween(SHORT_MS) else spring(dampingRatio = 1f, stiffness = 140f)

    fun <T> snappy(): FiniteAnimationSpec<T> =
        if (reduced) tween(SHORT_MS) else spring(dampingRatio = 0.85f, stiffness = 650f)

    fun <T> bouncy(): FiniteAnimationSpec<T> =
        if (reduced) tween(SHORT_MS) else spring(dampingRatio = 0.5f, stiffness = 500f)

    /** Content crossfades (text, art, panels). */
    fun <T> fade(durationMs: Int = MEDIUM_MS, delayMs: Int = 0): TweenSpec<T> =
        tween(if (reduced) SHORT_MS else durationMs, delayMillis = if (reduced) 0 else delayMs, easing = FastOutSlowInEasing)

    /** Slow color changes: palette crossfades on track change, the background. */
    fun <T> color(): FiniteAnimationSpec<T> =
        tween(if (reduced) SHORT_MS else COLOR_MS, easing = LinearOutSlowInEasing)

    /** Delay between staggered elements (title, then artist …); none with reduced motion. */
    val staggerMs: Int get() = if (reduced) 0 else STAGGER_MS

    companion object {
        const val SHORT_MS = 150
        const val MEDIUM_MS = 320
        const val LONG_MS = 520
        const val COLOR_MS = 1_200
        const val STAGGER_MS = 70
        const val EQUALIZER_MS = 1_100
        const val AMBIENT_MS = 1_500

        /** Scale of pressed buttons. */
        const val PRESSED_SCALE = 0.88f
    }
}

val LocalMotion = staticCompositionLocalOf { MotionSpec(reduced = false) }

/** Reads the system "Remove animations" setting (animator duration scale 0). */
@Composable
fun rememberMotionSpec(): MotionSpec {
    val context = LocalContext.current
    val resolver = context.contentResolver
    fun isReduced() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(isReduced()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = isReduced() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        reduced = isReduced()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return remember(reduced) { MotionSpec(reduced) }
}
