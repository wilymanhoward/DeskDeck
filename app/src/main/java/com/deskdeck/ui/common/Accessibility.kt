package com.deskdeck.ui.common

import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * [size] sp, but following the user's font size only up to [maxScale]. For display text (the
 * night clock, the temperature, a price) that is already very large and wouldn't fit if it grew
 * with the rest; body text keeps scaling fully.
 */
@Composable
fun cappedSp(size: Float, maxScale: Float = 1f): TextUnit {
    val scale = LocalDensity.current.fontScale
    return (size * minOf(scale, maxScale) / scale).sp
}

/** True while TalkBack (or another touch-exploration service) is on; follows changes live. */
@Composable
fun rememberTouchExploration(): State<Boolean> {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    val state = remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { state.value = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return state
}
