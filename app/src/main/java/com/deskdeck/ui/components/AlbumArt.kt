package com.deskdeck.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.deskdeck.R
import com.deskdeck.domain.ArtworkColors
import com.deskdeck.ui.theme.CappedWhite
import com.deskdeck.ui.theme.Dimens
import com.deskdeck.ui.theme.LocalMotion
import com.deskdeck.ui.theme.MotionSpec
import com.deskdeck.ui.theme.SurfaceHighest
import com.deskdeck.ui.theme.SurfaceMid
import com.deskdeck.ui.theme.withTone

private data class ArtKey(val trackId: String, val artwork: Bitmap?, val colors: ArtworkColors?)

/**
 * Square album art that crossfades between tracks. When a player sends no art, a placeholder
 * gradient in the track's colors (or neutral tones) is shown instead.
 */
@Composable
fun AlbumArt(
    trackId: String,
    artwork: Bitmap?,
    colors: ArtworkColors?,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.np_album_art)
    val motion = LocalMotion.current
    AnimatedContent(
        // Colors only matter for the placeholder; with a bitmap, late-arriving colors aren't a new image.
        targetState = ArtKey(trackId, artwork, if (artwork == null) colors else null),
        transitionSpec = {
            val enter = fadeIn(motion.fade(MotionSpec.LONG_MS))
            val exit = fadeOut(motion.fade())
            if (motion.reduced || initialState.trackId == targetState.trackId) enter togetherWith exit
            else (enter + slideInHorizontally(motion.gentle()) { it / 16 }) togetherWith
                (exit + slideOutHorizontally(motion.gentle()) { -it / 16 })
        },
        modifier = modifier.semantics { contentDescription = description },
        label = "albumArt",
    ) { key ->
        val art = key.artwork
        if (art != null) {
            val image = remember(art) { art.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(Dimens.artCorner)),
            )
        } else {
            ArtPlaceholder(key.colors, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ArtPlaceholder(colors: ArtworkColors?, modifier: Modifier = Modifier) {
    val brush = if (colors != null) {
        Brush.linearGradient(
            listOf(Color(colors.primary).withTone(0.45f), Color(colors.secondary).withTone(0.25f)),
        )
    } else {
        Brush.linearGradient(listOf(SurfaceHighest, SurfaceMid))
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.artCorner))
            .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_music_note),
            contentDescription = null,
            tint = CappedWhite.copy(alpha = 0.45f),
            modifier = Modifier
                .fillMaxWidth(0.3f)
                .aspectRatio(1f),
        )
    }
}
