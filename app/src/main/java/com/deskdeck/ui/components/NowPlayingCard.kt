package com.deskdeck.ui.components

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.deskdeck.R
import com.deskdeck.ui.theme.pressScale
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.deskdeck.ui.spotify.LocalArtTarget
import com.deskdeck.data.spotify.SpotifyLibrary
import com.deskdeck.data.lyrics.TitleCleaner
import com.deskdeck.domain.MediaKind
import com.deskdeck.domain.NowPlaying
import com.deskdeck.domain.Track
import com.deskdeck.launcher.AppIcon
import com.deskdeck.ui.common.clockFormatter
import com.deskdeck.ui.common.rememberCurrentTime
import com.deskdeck.ui.common.rememberIs24Hour
import com.deskdeck.ui.dashboard.EmptySourceUi
import com.deskdeck.ui.dashboard.PlayerActions
import com.deskdeck.ui.settings.LocalAppSettings
import com.deskdeck.ui.theme.ActiveLyricWhite
import com.deskdeck.ui.theme.CappedWhite
import com.deskdeck.ui.theme.Dimens
import com.deskdeck.ui.theme.LocalMotion
import com.deskdeck.ui.theme.MotionSpec
import com.deskdeck.ui.theme.MutedText
import com.deskdeck.ui.theme.SurfaceHighest
import com.deskdeck.ui.theme.glass
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
    emptySource: EmptySourceUi? = null,
    onOpenApp: (String) -> Unit = {},
    onGetApp: (String) -> Unit = {},
    volumeControl: (@Composable () -> Unit)? = null,
    /** Shown as a Library button while Spotify is the source; null hides it. */
    onOpenLibrary: (() -> Unit)? = null,
) {
    val motion = LocalMotion.current
    BoxWithConstraints(modifier = modifier.glass(RoundedCornerShape(Dimens.cardCorner))) {
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val minHeight = (if (volumeControl != null) 496.dp else 440.dp) * fontScale
        val contentModifier = if (maxHeight < minHeight) {
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).height(minHeight)
        } else Modifier.fillMaxSize()
        AnimatedContent(
            modifier = contentModifier,
            targetState = nowPlaying,
            contentKey = { it == null },
            transitionSpec = { fadeIn(motion.fade(MotionSpec.LONG_MS)) togetherWith fadeOut(motion.fade()) },
            label = "nowPlayingContent",
        ) { np ->
            if (np == null) {
                EmptyNowPlaying(accessMissing, onGrantAccess, emptySource, onOpenApp, onGetApp)
            } else {
                NowPlayingContent(np, position, palette, actions, volumeControl, onOpenLibrary)
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
    volumeControl: (@Composable () -> Unit)?,
    onOpenLibrary: (() -> Unit)?,
) {
    val track = nowPlaying.track
    val artTarget = LocalArtTarget.current
    val motion = LocalMotion.current
    // Apple Music-style: the cover sits large while playing and settles back when paused.
    val artScale by animateFloatAsState(
        targetValue = if (motion.reduced || nowPlaying.playback.isPlaying) 1f else PAUSED_ART_SCALE,
        animationSpec = motion.gentle(),
        label = "artScale",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.cardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.np_playing_on, nowPlaying.sourceLabel),
                style = MaterialTheme.typography.labelLarge,
                color = MutedText,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (onOpenLibrary != null && nowPlaying.sourcePackage == SpotifyLibrary.SPOTIFY_PACKAGE) {
                LibraryButton(onOpenLibrary)
            }
        }
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
                    modifier = Modifier
                        .aspectRatio(1f, matchHeightConstraintsFirst = true)
                        // Where a playlist's cover lands when it's started from the library.
                        .onGloballyPositioned { artTarget.bounds = it.boundsInRoot() }
                        // Scale and depth shadow in one layer, read in the draw phase.
                        .graphicsLayer {
                            scaleX = artScale
                            scaleY = artScale
                            shadowElevation = 32.dp.toPx()
                            shape = RoundedCornerShape(Dimens.artCorner)
                            ambientShadowColor = Color.Black
                            spotShadowColor = Color.Black
                        },
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
            activeColor = ActiveLyricWhite,
            onSeek = actions::onSeek,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        PlaybackControls(
            playback = nowPlaying.playback,
            palette = palette,
            actions = actions,
        )
        if (volumeControl != null) {
            Spacer(Modifier.height(4.dp))
            volumeControl()
        }
    }
}

/** Title, then artist a beat later, slide up into place on a track change. */
@Composable
private fun TrackHeadline(track: Track) {
    val motion = LocalMotion.current
    val display = remember(track) { TitleCleaner.forDisplay(track) }
    val artistLine = listOfNotNull(display.artist ?: stringResource(R.string.np_unknown_artist), track.album)
        .joinToString(" · ")
    Column(Modifier.fillMaxWidth()) {
        StaggeredText(display.title, delayMs = 0) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.headlineMedium,
                color = CappedWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
        StaggeredText(artistLine, delayMs = motion.staggerMs) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StaggeredText(text: String, delayMs: Int, content: @Composable (String) -> Unit) {
    val motion = LocalMotion.current
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            val enter = fadeIn(motion.fade(delayMs = delayMs))
            val slide = if (motion.reduced) {
                enter
            } else {
                enter + slideInVertically(motion.fade(MotionSpec.LONG_MS, delayMs)) { it / 2 }
            }
            slide togetherWith fadeOut(motion.fade(MotionSpec.SHORT_MS))
        },
        label = "staggeredText",
        modifier = Modifier.fillMaxWidth(),
    ) { content(it) }
}

private const val PAUSED_ART_SCALE = 0.86f

/**
 * Shown when nothing is playing: a large clock, then either a way to grant notification access, the
 * selected app's state ("Starting Spotify…", "Nothing playing in Spotify" + "Open Spotify"), or a
 * general hint.
 */
@Composable
private fun EmptyNowPlaying(
    accessMissing: Boolean,
    onGrantAccess: () -> Unit,
    emptySource: EmptySourceUi?,
    onOpenApp: (String) -> Unit,
    onGetApp: (String) -> Unit,
) {
    val motion = LocalMotion.current
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
        AnimatedContent(
            targetState = emptySource.takeUnless { accessMissing },
            contentKey = { it?.let { source -> "${source.packageName}:${source.reason}" } },
            transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.fade()) },
            label = "emptyVariant",
        ) { source ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (source != null) {
                    SourceEmptyState(source, onOpenApp, onGetApp)
                } else {
                    GeneralEmptyState(accessMissing, onGrantAccess)
                }
            }
        }
    }
}

@Composable
private fun GeneralEmptyState(accessMissing: Boolean, onGrantAccess: () -> Unit) {
    EmptyIcon { Icon(painterResource(R.drawable.ic_music_note), null, tint = MutedText, modifier = Modifier.size(32.dp)) }
    EmptyTexts(
        title = stringResource(if (accessMissing) R.string.np_access_off else R.string.np_empty_hint),
        detail = stringResource(if (accessMissing) R.string.np_access_off_detail else R.string.np_empty_subhint),
    )
    if (accessMissing) {
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrantAccess, modifier = Modifier.heightIn(min = Dimens.minTouch)) {
            Text(stringResource(R.string.np_grant_access), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The selected app has nothing to show. The buttons here are the only way the card leaves DeskDeck. */
@Composable
private fun SourceEmptyState(source: EmptySourceUi, onOpenApp: (String) -> Unit, onGetApp: (String) -> Unit) {
    val label = source.label
    if (source.reason == EmptySourceUi.Reason.NOT_INSTALLED) {
        EmptyIcon { Icon(painterResource(R.drawable.ic_download), null, tint = MutedText, modifier = Modifier.size(32.dp)) }
    } else {
        AsyncImage(model = AppIcon(source.packageName), contentDescription = null, modifier = Modifier.size(64.dp))
    }
    when (source.reason) {
        EmptySourceUi.Reason.STARTING -> {
            EmptyTexts(
                title = stringResource(R.string.np_source_starting, label),
                detail = stringResource(R.string.np_source_starting_detail, label),
            )
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(color = MutedText, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        }
        EmptySourceUi.Reason.NO_SESSION -> {
            EmptyTexts(
                title = stringResource(R.string.np_source_no_session, label),
                detail = stringResource(R.string.np_source_no_session_detail, label),
            )
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = { onOpenApp(source.packageName) }, modifier = Modifier.heightIn(min = Dimens.minTouch)) {
                Text(stringResource(R.string.np_source_open, label), style = MaterialTheme.typography.labelLarge)
            }
        }
        EmptySourceUi.Reason.NOT_INSTALLED -> {
            EmptyTexts(title = stringResource(R.string.np_source_not_installed, label), detail = null)
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = { onGetApp(source.packageName) }, modifier = Modifier.heightIn(min = Dimens.minTouch)) {
                Text(stringResource(R.string.np_source_get), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun EmptyIcon(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(SurfaceHighest, CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun EmptyTexts(title: String, detail: String?) {
    Spacer(Modifier.height(16.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = CappedWhite,
        textAlign = TextAlign.Center,
    )
    if (detail != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyLarge,
            color = MutedText,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 380.dp),
        )
    }
}

/** A small pill that opens the Spotify library. */
@Composable
private fun LibraryButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .pressScale(interaction)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_library), contentDescription = null, tint = MutedText, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.library_open), style = MaterialTheme.typography.labelLarge, color = CappedWhite)
    }
}
