package com.deskdeck.ui.theme

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * "Liquid glass" panel: translucent fill, a soft highlight along the top edge and a 0.5 dp light
 * hairline. Behind every panel is DeskDeck's own blurry color background, so the translucency
 * already shows soft, out-of-focus color; no backdrop blur is needed (Compose has none: its blur
 * only blurs a node's own content).
 *
 * @param tint fill; keep it low so the background shows through but text stays readable.
 */
fun Modifier.glass(
    shape: Shape,
    tint: Color = GlassTint,
    border: Color = GlassBorder,
): Modifier = this
    .clip(shape)
    .drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val highlight = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.07f),
            0.35f to Color.Transparent,
        )
        val hairline = Stroke(width = 0.5.dp.toPx() * 2) // Half falls outside the clip: 0.5 dp visible.
        onDrawBehind {
            drawOutline(outline, if (Build.VERSION.SDK_INT >= 31) tint else SurfaceLow)
            drawOutline(outline, highlight)
            drawOutline(outline, border, style = hairline)
        }
    }

val GlassTint = Color.White.copy(alpha = 0.06f)
val GlassTintStrong = Color(0xFF1C1D21).copy(alpha = 0.82f)
val GlassBorder = Color.White.copy(alpha = 0.14f)

/** Press feedback: the node scales down on a spring while pressed. Applied in the layer, so no recomposition. */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = MotionSpec.PRESSED_SCALE): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = LocalMotion.current
    val scale by animateFloatAsState(if (pressed && !motion.reduced) pressedScale else 1f, motion.bouncy(), label = "pressScale")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
