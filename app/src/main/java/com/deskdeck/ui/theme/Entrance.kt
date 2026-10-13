package com.deskdeck.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** A single entrance for a screen; layer reads keep each frame out of composition. */
@Composable
fun Modifier.screenEntrance(): Modifier {
    val motion = LocalMotion.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(motion) { progress.animateTo(1f, motion.fade(MotionSpec.LONG_MS)) }
    return graphicsLayer {
        alpha = progress.value
        translationY = if (motion.reduced) 0f else (1f - progress.value) * 12.dp.toPx()
    }
}
