package com.deskdeck.ui.spotify

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.deskdeck.R
import com.deskdeck.ui.theme.Dimens
import com.deskdeck.ui.theme.LocalMotion
import kotlinx.coroutines.delay

/** Where the Now Playing card's album art is on screen (root coordinates), for the cover flight to land on. */
class ArtTarget {
    var bounds by mutableStateOf<Rect?>(null)
}

val LocalArtTarget = staticCompositionLocalOf { ArtTarget() }

/**
 * The cover of a playlist that just started flies from its library tile to the Now Playing card's
 * album art, growing and rounding as it goes, then fades into the real art. If the card isn't
 * showing art yet, it waits a moment for Spotify's session; failing that it just grows and fades.
 */
@Composable
fun CoverFlight(flight: StartedPlaylist?, target: ArtTarget, onDone: () -> Unit) {
    val from = flight?.coverBounds
    if (flight == null || from == null) {
        LaunchedEffect(flight) { if (flight != null) onDone() }
        return
    }
    val motion = LocalMotion.current
    val progress = remember(flight) { Animatable(0f) }
    val fade = remember(flight) { Animatable(1f) }
    var origin by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(flight) {
        if (motion.reduced) {
            onDone()
            return@LaunchedEffect
        }
        // Give Spotify's session a moment to appear so the card has art to land on.
        repeat(10) { if (target.bounds == null) delay(50) }
        progress.animateTo(1f, motion.gentle())
        fade.animateTo(0f, motion.fade())
        onDone()
    }

    val density = LocalDensity.current
    val cornerFrom = with(density) { 16.dp.toPx() }
    val cornerTo = with(density) { Dimens.artCorner.toPx() }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.boundsInRoot().topLeft }) {
        Box(
            Modifier
                .size(with(density) { from.width.toDp() }, with(density) { from.height.toDp() })
                .graphicsLayer {
                    val to = target.bounds ?: Rect(from.center, from.width * 1.4f / 2)
                    val t = progress.value
                    val scale = lerp(1f, to.width / from.width, t)
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = lerp(from.left, to.left, t) - origin.x
                    translationY = lerp(from.top, to.top, t) - origin.y
                    scaleX = scale
                    scaleY = scale
                    alpha = fade.value
                    shadowElevation = 24.dp.toPx() * t
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(lerp(cornerFrom, cornerTo, t) / scale)
                    clip = true
                },
        ) {
            if (flight.item.isLikedSongs) {
                Box(
                    Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF4A2FBD), Color(0xFF8C9EE8)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_heart), null, tint = Color.White, modifier = Modifier.size(56.dp))
                }
            } else {
                AsyncImage(
                    model = flight.item.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
