package com.deskdeck.ui.components

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deskdeck.R
import com.deskdeck.domain.MediaKind
import com.deskdeck.domain.NowPlaying
import com.deskdeck.domain.Track
import com.deskdeck.ui.common.clockFormatter
import com.deskdeck.ui.common.rememberCurrentTime
import com.deskdeck.ui.common.rememberIs24Hour
import com.deskdeck.ui.dashboard.PlayerActions
import com.deskdeck.ui.settings.LocalAppSettings
import com.deskdeck.ui.theme.CappedWhite
import com.deskdeck.ui.theme.Dimens
import com.deskdeck.ui.theme.MutedText
import com.deskdeck.ui.theme.SurfaceHighest
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun NowPlayingCard(
    nowPlaying: NowPlaying?,
    position: State<Long>,
    palette: CardPalette,
    actions: PlayerActions,
    accessMissing: Boolean,
    onGrantAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.cardCorner))
            .background(Brush.verticalGradient(listOf(palette.top, palette.bottom))),
    ) {
        AnimatedContent(
            targetState = nowPlaying,
            contentKey = { it == null },
            transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) },
            label = "nowPlayingContent",
        ) { np ->
            if (np == null) {
                EmptyNowPlaying(accessMissing, onGrantAccess)
            } else {
                NowPlayingContent(np, position, palette, actions)
            }
        }
    }
}

@Composable
private fun NowPlayingContent(
    nowPlaying: NowPlaying,
    position: State<Long>,
    palette: CardPalette,
    actions: PlayerActions,
) {
    val track = nowPlaying.track
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.cardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.np_playing_on, nowPlaying.sourceLabel),
            style = MaterialTheme.typography.labelLarge,
            color = MutedText,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        // Art takes whatever height is left, stays square, and disappears in very short windows.
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (maxHeight >= 72.dp) {
                AlbumArt(
                    trackId = track.id,
                    artwork = nowPlaying.artwork,
                    colors = nowPlaying.colors,
                    modifier = Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        TrackHeadline(track)
        Spacer(Modifier.height(16.dp))
        SeekBar(
            position = position,
            durationMs = track.durationMs,
            seekEnabled = nowPlaying.playback.canSeek,
            isLive = track.kind == MediaKind.LIVE,
            activeColor = palette.accent,
            onSeek = actions::onSeek,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        PlaybackControls(
            playback = nowPlaying.playback,
            palette = palette,
            actions = actions,
        )
    }
}

@Composable
private fun TrackHeadline(track: Track) {
    AnimatedContent(
        targetState = track,
        contentKey = { it.id },
        transitionSpec = {
            (fadeIn(tween(450)) + slideInVertically(tween(450)) { it / 3 }) togetherWith fadeOut(tween(200))
        },
        label = "trackHeadline",
        modifier = Modifier.fillMaxWidth(),
    ) { t ->
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = t.title,
                style = MaterialTheme.typography.headlineMedium,
                color = CappedWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = listOfNotNull(t.artist ?: stringResource(R.string.np_unknown_artist), t.album)
                    .joinToString(" • "),
                style = MaterialTheme.typography.titleMedium,
                color = MutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Shown when no media session is active: a large clock and a hint, or a way to grant
 * notification access when that is why nothing shows up.
 */
@Composable
private fun EmptyNowPlaying(accessMissing: Boolean, onGrantAccess: () -> Unit) {
    val is24Hour = rememberIs24Hour()
    val showSeconds = LocalAppSettings.current.clockShowSeconds
    val now by rememberCurrentTime(withSeconds = showSeconds)
    val timeFormatter = remember(is24Hour, showSeconds) { clockFormatter(is24Hour, showSeconds) }
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"))
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.cardPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = now.format(timeFormatter),
            style = MaterialTheme.typography.displayLarge,
            color = CappedWhite,
            maxLines = 1,
        )
        Text(
            text = now.format(dateFormatter),
            style = MaterialTheme.typography.titleLarge,
            color = MutedText,
        )
        Spacer(Modifier.height(48.dp))
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(SurfaceHighest, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_music_note),
                contentDescription = null,
                tint = MutedText,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(if (accessMissing) R.string.np_access_off else R.string.np_empty_hint),
            style = MaterialTheme.typography.headlineSmall,
            color = CappedWhite,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(if (accessMissing) R.string.np_access_off_detail else R.string.np_empty_subhint),
            style = MaterialTheme.typography.bodyLarge,
            color = MutedText,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 380.dp),
        )
        if (accessMissing) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onGrantAccess, modifier = Modifier.heightIn(min = Dimens.minTouch)) {
                Text(stringResource(R.string.np_grant_access), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
