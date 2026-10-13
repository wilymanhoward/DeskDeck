package com.deskdeck.ui.spotify

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.deskdeck.R
import com.deskdeck.data.spotify.LibraryItem
import com.deskdeck.data.spotify.SpotifyAccount
import com.deskdeck.data.spotify.SpotifyError
import com.deskdeck.ui.theme.CappedWhite
import com.deskdeck.ui.theme.Dimens
import com.deskdeck.ui.theme.GlassTintStrong
import com.deskdeck.ui.theme.LocalMotion
import com.deskdeck.ui.theme.MutedText
import com.deskdeck.ui.theme.glass
import com.deskdeck.ui.theme.pressScale

/** A playlist that just started, and where its cover was, so it can fly into the Now Playing card. */
data class StartedPlaylist(val item: LibraryItem, val coverBounds: Rect?)

/**
 * The Spotify library: an Apple Music-style grid of the user's playlists (Liked Songs first),
 * cached so it opens instantly and refreshed in the background. Tapping one starts it on this
 * tablet's Spotify and hands back [StartedPlaylist]; nothing here opens the Spotify app, except
 * the explicit "Open Spotify" button shown when the tablet's Spotify can't be reached.
 */
@Composable
fun LibraryOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    onStarted: (StartedPlaylist) -> Unit,
    onOpenSpotify: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val motion = LocalMotion.current
    BackHandler(enabled = visible, onBack = onDismiss)

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(motion.fade()), exit = fadeOut(motion.fade())) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = if (motion.reduced) fadeIn(motion.fade()) else slideInVertically(motion.gentle()) { it / 5 } + fadeIn(motion.fade()),
            exit = if (motion.reduced) fadeOut(motion.fade()) else slideOutVertically(motion.snappy()) { it / 5 } + fadeOut(motion.fade()),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            LibraryPanel(onDismiss, onStarted, onOpenSpotify, onOpenSettings)
        }
    }
}

@Composable
private fun LibraryPanel(
    onDismiss: () -> Unit,
    onStarted: (StartedPlaylist) -> Unit,
    onOpenSpotify: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val starting by viewModel.starting.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coverBounds = remember { mutableStateMapOf<String, Rect>() }

    // Cached list now, fresh list in a moment.
    LaunchedEffect(account) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .widthIn(max = 1100.dp)
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .glass(RoundedCornerShape(topStart = Dimens.cardCorner, topEnd = Dimens.cardCorner), tint = GlassTintStrong)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(horizontal = 28.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.headlineMedium,
                color = CappedWhite,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (account is SpotifyAccount.SignedIn) {
                if (refreshing) {
                    CircularProgressIndicator(color = MutedText, strokeWidth = 2.dp, modifier = Modifier.padding(16.dp).size(24.dp))
                } else {
                    IconButton(onClick = viewModel::refresh, modifier = Modifier.size(Dimens.minTouch)) {
                        Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.library_refresh), tint = MutedText)
                    }
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(Dimens.minTouch)) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.library_close), tint = MutedText)
            }
        }
        Spacer(Modifier.height(12.dp))

        when (val a = account) {
            SpotifyAccount.NotConfigured -> CenteredMessage(
                title = stringResource(R.string.library_setup_title),
                detail = stringResource(R.string.library_setup_detail),
            )
            SpotifyAccount.SignedOut, SpotifyAccount.SigningIn, is SpotifyAccount.Failed -> CenteredMessage(
                title = stringResource(R.string.library_connect_title),
                detail = (a as? SpotifyAccount.Failed)?.error?.let { errorText(it).second }
                    ?: stringResource(R.string.library_connect_detail),
                action = stringResource(R.string.library_connect),
                onAction = { openSignIn(context, viewModel.beginSignIn().toString()) },
            )
            is SpotifyAccount.SignedIn -> {
                error?.let { e ->
                    ErrorBar(
                        error = e,
                        onDismiss = viewModel::dismissError,
                        onOpenSpotify = onOpenSpotify,
                        onReconnect = onOpenSettings,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (items.isEmpty() && refreshing) {
                    CenteredMessage(title = stringResource(R.string.library_loading))
                } else if (items.isEmpty()) {
                    CenteredMessage(title = stringResource(R.string.library_empty))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 168.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items, key = { it.id }) { item ->
                            PlaylistTile(
                                item = item,
                                starting = starting?.takeIf { it.itemId == item.id },
                                enabled = starting == null,
                                onCoverPlaced = { coverBounds[item.id] = it },
                                onClick = {
                                    viewModel.play(item) { onStarted(StartedPlaylist(item, coverBounds[item.id])) }
                                },
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistTile(
    item: LibraryItem,
    starting: StartingPlaylist?,
    enabled: Boolean,
    onCoverPlaced: (Rect) -> Unit,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val name = if (item.isLikedSongs) stringResource(R.string.library_liked_songs) else item.name
    Column(
        Modifier
            .pressScale(interaction, pressedScale = 0.95f)
            .clip(RoundedCornerShape(20.dp))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClickLabel = stringResource(R.string.library_play, name), onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .onGloballyPositioned { onCoverPlaced(it.boundsInRoot()) }
                .clip(RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (item.isLikedSongs) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(Color(0xFF4A2FBD), Color(0xFF8C9EE8)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_heart), null, tint = Color.White, modifier = Modifier.size(56.dp))
                }
            } else {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.ic_music_note),
                    error = painterResource(R.drawable.ic_music_note),
                    modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.06f)),
                )
            }
            if (starting != null) StartingOverlay(starting.waking)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = CappedWhite,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        item.subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MutedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A breathing dark veil over the cover while playback starts; says so if Spotify had to be woken. */
@Composable
private fun StartingOverlay(waking: Boolean) {
    val reduced = LocalMotion.current.reduced
    val pulse = rememberInfiniteTransition(label = "starting").animateFloat(
        initialValue = 0.45f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "startingPulse",
    )
    Box(
        Modifier
            .fillMaxSize()
            // Read in the layer, so the pulse redraws without recomposing; steady with reduced motion.
            .graphicsLayer { alpha = if (reduced) 0.6f else pulse.value }
            .background(Color.Black),
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(32.dp))
        if (waking) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.library_starting_spotify),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ErrorBar(error: SpotifyError, onDismiss: () -> Unit, onOpenSpotify: () -> Unit, onReconnect: () -> Unit) {
    val (title, detail) = errorText(error)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = CappedWhite)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MutedText)
        }
        when (error) {
            SpotifyError.NoDevice -> FilledTonalButton(onClick = onOpenSpotify) { Text(stringResource(R.string.library_open_spotify)) }
            SpotifyError.SignedOut, SpotifyError.NotRegistered -> TextButton(onClick = onReconnect) { Text(stringResource(R.string.library_settings)) }
            else -> Unit
        }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_dismiss)) }
    }
}

@Composable
private fun CenteredMessage(title: String, detail: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(R.drawable.ic_library), null, tint = MutedText, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = CappedWhite, textAlign = TextAlign.Center)
        if (detail != null) {
            Spacer(Modifier.height(8.dp))
            Text(detail, style = MaterialTheme.typography.bodyLarge, color = MutedText, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 560.dp))
        }
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAction, modifier = Modifier.height(Dimens.minTouch)) { Text(action) }
        }
    }
}

/** Title and explanation for each [SpotifyError]. */
@Composable
fun errorText(error: SpotifyError): Pair<String, String> = when (error) {
    SpotifyError.NotConfigured -> stringResource(R.string.library_setup_title) to stringResource(R.string.library_setup_detail)
    SpotifyError.SignedOut -> stringResource(R.string.spotify_err_signed_out) to stringResource(R.string.spotify_err_signed_out_detail)
    SpotifyError.Offline -> stringResource(R.string.spotify_err_offline) to stringResource(R.string.spotify_err_offline_detail)
    SpotifyError.NotRegistered -> stringResource(R.string.spotify_err_not_registered) to stringResource(R.string.spotify_err_not_registered_detail)
    SpotifyError.PremiumRequired -> stringResource(R.string.spotify_err_premium) to stringResource(R.string.spotify_err_premium_detail)
    is SpotifyError.RateLimited -> stringResource(R.string.spotify_err_rate_limited) to
        (error.retryAfterSec?.let { stringResource(R.string.spotify_err_rate_limited_detail_seconds, it) } ?: stringResource(R.string.spotify_err_rate_limited_detail))
    SpotifyError.NoDevice -> stringResource(R.string.spotify_err_no_device) to stringResource(R.string.spotify_err_no_device_detail)
    is SpotifyError.Other -> stringResource(R.string.spotify_err_other) to (error.message ?: stringResource(R.string.spotify_err_other_detail, error.code))
}

/** Spotify's sign-in page in a Custom Tab over DeskDeck. Explicit, user-started, and the only web page DeskDeck opens. */
fun openSignIn(context: Context, url: String) {
    val tab = CustomTabsIntent.Builder()
        .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
        .setShowTitle(true)
        .build()
    try {
        tab.launchUrl(context, android.net.Uri.parse(url))
    } catch (_: ActivityNotFoundException) {
        // No browser at all: nothing to sign in with.
    }
}
