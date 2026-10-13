package com.deskdeck.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.deskdeck.domain.ArtworkColors
import com.deskdeck.ui.theme.LocalMotion
import com.deskdeck.ui.theme.TrueBlack
import com.deskdeck.ui.theme.withTone
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.delay

/** Where each blob wanders: base position (fraction of the screen), orbit size and speed. */
private data class Blob(val x: Float, val y: Float, val orbitX: Float, val orbitY: Float, val speed: Float, val phase: Float, val radius: Float)

private val Blobs = listOf(
    Blob(0.18f, 0.25f, 0.10f, 0.08f, 0.050f, 0.0f, 0.62f),
    Blob(0.78f, 0.20f, 0.08f, 0.10f, 0.041f, 1.7f, 0.55f),
    Blob(0.55f, 0.78f, 0.12f, 0.07f, 0.036f, 3.1f, 0.60f),
    Blob(0.12f, 0.85f, 0.07f, 0.09f, 0.045f, 4.4f, 0.48f),
    Blob(0.90f, 0.70f, 0.09f, 0.06f, 0.038f, 5.6f, 0.50f),
)

/**
 * Apple Music-style background: soft color blobs from the album art drifting slowly on true black,
 * crossfading to the new palette on every track change.
 *
 * Cheap on purpose (it's on screen all day): radial gradients are already soft, so no blur pass is
 * needed; the drift is updated about 15 times a second (smooth at this speed) instead of every
 * frame, and stops entirely when [animate] is false (covered, ambient, reduced motion). Colors are
 * forced to dark tones so the brightest point stays low, which also keeps it burn-in safe; the
 * movement itself helps against burn-in too.
 *
 * @param intensity 0..1 scales the blobs' opacity (ambient uses a dimmer background).
 */
@Composable
fun FluidBackground(
    colors: ArtworkColors?,
    fallbackAccent: Color,
    animate: Boolean,
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
) {
    val motion = LocalMotion.current
    val targets = remember(colors, fallbackAccent) { backgroundColors(colors, fallbackAccent) }
    val blobColors: List<State<Color>> = targets.mapIndexed { i, c -> animateColorAsState(c, motion.color(), label = "blob$i") }

    val time = remember { mutableFloatStateOf(0f) }
    val drifting = animate && !motion.reduced
    LaunchedEffect(drifting) {
        while (drifting) {
            delay(FRAME_MS)
            time.floatValue += FRAME_MS / 1000f
        }
    }

    Canvas(modifier) {
        drawRect(TrueBlack)
        val t = time.floatValue
        val maxDim = max(size.width, size.height)
        Blobs.forEachIndexed { i, blob ->
            val center = Offset(
                x = size.width * (blob.x + blob.orbitX * sin(t * blob.speed * 6.28f + blob.phase)),
                y = size.height * (blob.y + blob.orbitY * cos(t * blob.speed * 5.1f + blob.phase)),
            )
            val radius = maxDim * blob.radius
            val color = blobColors[i].value
            drawCircle(
                brush = Brush.radialGradient(
                    0f to color.copy(alpha = 0.55f * intensity),
                    0.55f to color.copy(alpha = 0.22f * intensity),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}

/** Five dark, gently saturated tones from the art (or the accent), repeating if there are fewer. */
internal fun backgroundColors(colors: ArtworkColors?, fallbackAccent: Color): List<Color> {
    val source = colors?.swatches?.takeIf { it.isNotEmpty() }?.map { Color(it) }
        ?: colors?.let { listOf(Color(it.primary), Color(it.secondary)) }
        ?: listOf(fallbackAccent, Color(0xFF3B4A6B), Color(0xFF2A2F45))
    return List(Blobs.size) { i ->
        val lightness = if (i % 2 == 0) 0.30f else 0.22f
        source[i % source.size].withTone(lightness, maxSaturation = 0.6f)
    }
}

private const val FRAME_MS = 66L
