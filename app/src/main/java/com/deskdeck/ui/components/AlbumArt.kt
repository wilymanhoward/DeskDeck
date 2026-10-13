package com.deskdeck.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.deskdeck.R
import com.deskdeck.domain.ArtworkColors
import com.deskdeck.ui.theme.CappedWhite
import com.deskdeck.ui.theme.Dimens
import com.deskdeck.ui.theme.SurfaceHighest
import com.deskdeck.ui.theme.SurfaceMid
import com.deskdeck.ui.theme.withTone

private data class ArtKey(val trackId: String, val artwork: Any?, val colors: ArtworkColors?)

/**
 * Square album art that crossfades between tracks. Until images are loaded (M2) and whenever a
 * player sends no art, a placeholder gradient in the track's colors is shown.
 */
@Composable
fun AlbumArt(
    trackId: String,
    artwork: Any?,
    colors: ArtworkColors?,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.np_album_art)
    Crossfade(
        targetState = ArtKey(trackId, artwork, colors),
        animationSpec = tween(durationMillis = 600),
        modifier = modifier.semantics { contentDescription = description },
        label = "albumArt",
    ) { key ->
        ArtPlaceholder(key.colors, Modifier.fillMaxSize())
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
