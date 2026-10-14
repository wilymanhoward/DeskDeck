package com.deskdeck.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.deskdeck.R
import com.deskdeck.data.lyrics.TitleCleaner
import com.deskdeck.domain.NowPlaying
import com.deskdeck.ui.adaptive.SharedKeys
import com.deskdeck.ui.adaptive.sharedNowPlayingBounds
import com.deskdeck.ui.adaptive.sharedNowPlayingElement
import com.deskdeck.ui.common.clockFormatter
import com.deskdeck.ui.common.rememberCurrentTime
import com.deskdeck.ui.common.rememberIs24Hour
import com.deskdeck.ui.dashboard.EmptySourceUi
import com.deskdeck.ui.dashboard.PlayerActions
import com.deskdeck.ui.theme.ActiveLyricWhite
import com.deskdeck.ui.theme.CappedWhite
import com.deskdeck.ui.theme.Dimens
import com.deskdeck.ui.theme.LocalMotion
import com.deskdeck.ui.theme.MutedText
import com.deskdeck.ui.theme.glass

/**
 * The compact layout (narrow split screen, pop-up view): album art, title and artist, a thin
 * progress line, and previous / play-pause / next. Side by side in a wide window, stacked in a
 * tall one; the art goes when there's no room for it.
 */
@Composable
fun MiniPlayer(
    nowPlaying: NowPlaying?,
    position: State<Long>,
    actions: PlayerActions,
    accessMissing: Boolean,
    onGrantAccess: () -> Unit,
    modifier: Modifier = Modifier,
    emptySource: EmptySourceUi? = null,
    onOpenApp: (String) -> Unit = {},
) {
    val motion = LocalMotion.current
    BoxWithConstraints(modifier.glass(RoundedCornerShape(Dimens.cardCorner))) {
        val wide = maxWidth >= maxHeight
        AnimatedContent(
            targetState = nowPlaying,
            contentKey = { it == null },
            transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.fade()) },
            label = "miniPlayer",
            modifier = Modifier.fillMaxSize(),
        ) { np ->
            if (np == null) {
                MiniEmpty(accessMissing, onGrantAccess, emptySource, onOpenApp)
            } else if (wide) {
                Row(Modifier.fillMaxSize().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    val art = min(this@BoxWithConstraints.maxHeight - 40.dp, this@BoxWithConstraints.maxWidth * 0.4f)
                    if (art >= 72.dp) {
                        MiniArt(np, Modifier.size(art))
                        Spacer(Modifier.width(20.dp))
                    }
                    MiniDetails(np, position, actions, Modifier.weight(1f), centered = false)
                }
            } else {
                Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (maxHeight >= 72.dp) MiniArt(np, Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true))
                    }
                    Spacer(Modifier.height(16.dp))
                    MiniDetails(np, position, actions, Modifier.fillMaxWidth(), centered = true)
                }
            }
        }
    }
}

@Composable
private fun MiniArt(np: NowPlaying, modifier: Modifier) {
    AlbumArt(
        trackId = np.track.id,
        artwork = np.artwork,
        colors = np.colors,
        modifier = modifier.sharedNowPlayingElement(SharedKeys.ART),
    )
}

@Composable
private fun MiniDetails(np: NowPlaying, position: State<Long>, actions: PlayerActions, modifier: Modifier, centered: Boolean) {
    val display = remember(np.track) { TitleCleaner.forDisplay(np.track) }
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Column(modifier, horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            text = display.title,
            style = MaterialTheme.typography.titleLarge,
            color = CappedWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = align,
            modifier = Modifier
                .fillMaxWidth()
                .sharedNowPlayingBounds(SharedKeys.TITLE)
                .semantics { heading() },
        )
        Text(
            text = display.artist ?: stringResource(R.string.np_unknown_artist),
            style = MaterialTheme.typography.titleSmall,
            color = MutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        ThinProgress(position, np.track.durationMs, Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        MiniTransport(np.playback, actions)
    }
}

/** Progress only (no seeking at this size), read in the draw phase. */
@Composable
internal fun ThinProgress(position: State<Long>, durationMs: Long?, modifier: Modifier) {
    val duration = durationMs?.takeIf { it > 0 }
    Canvas(modifier.height(3.dp)) {
        val f = duration?.let { (position.value.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
        val y = size.height / 2
        drawLine(MutedText.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), size.height, StrokeCap.Round)
        if (f > 0f) drawLine(ActiveLyricWhite, Offset(0f, y), Offset(size.width * f, y), size.height, StrokeCap.Round)
    }
}

@Composable
private fun MiniEmpty(accessMissing: Boolean, onGrantAccess: () -> Unit, emptySource: EmptySourceUi?, onOpenApp: (String) -> Unit) {
    val is24Hour = rememberIs24Hour()
    val now by rememberCurrentTime(withSeconds = false)
    val formatter = remember(is24Hour) { clockFormatter(is24Hour, withSeconds = false) }
    val source = emptySource.takeUnless { accessMissing }
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(now.format(formatter), style = MaterialTheme.typography.displayMedium, color = CappedWhite, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                accessMissing -> stringResource(R.string.np_access_off)
                source?.reason == EmptySourceUi.Reason.STARTING -> stringResource(R.string.np_source_starting, source.label)
                source?.reason == EmptySourceUi.Reason.NO_SESSION -> stringResource(R.string.np_source_no_session, source.label)
                source?.reason == EmptySourceUi.Reason.NOT_INSTALLED -> stringResource(R.string.np_source_not_installed, source.label)
                else -> stringResource(R.string.np_empty_hint)
            },
            style = MaterialTheme.typography.titleMedium,
            color = MutedText,
            textAlign = TextAlign.Center,
        )
        when {
            accessMissing -> {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onGrantAccess, modifier = Modifier.heightIn(min = Dimens.minTouch)) {
                    Text(stringResource(R.string.np_grant_access), style = MaterialTheme.typography.labelLarge)
                }
            }
            source?.reason == EmptySourceUi.Reason.NO_SESSION -> {
                Spacer(Modifier.height(16.dp))
                // Only on this explicit tap does DeskDeck open the other app.
                OutlinedButton(onClick = { onOpenApp(source.packageName) }, modifier = Modifier.heightIn(min = Dimens.minTouch)) {
                    Text(stringResource(R.string.np_source_open, source.label), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
