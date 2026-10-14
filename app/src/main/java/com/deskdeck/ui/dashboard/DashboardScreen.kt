package com.deskdeck.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.togetherWith
import androidx.compose.ui.platform.LocalDensity
import com.deskdeck.ui.adaptive.DeskSize
import com.deskdeck.ui.adaptive.LocalDeskSize
import com.deskdeck.ui.adaptive.LocalSharedLayoutScopes
import com.deskdeck.ui.adaptive.SharedLayoutScopes
import com.deskdeck.ui.components.MiniLyrics
import com.deskdeck.ui.components.MiniPlayer
import com.deskdeck.ui.components.hasMiniLyrics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deskdeck.ui.spotify.StartedPlaylist
import com.deskdeck.ui.spotify.LocalArtTarget
import com.deskdeck.ui.spotify.LibraryOverlay
import com.deskdeck.ui.spotify.CoverFlight
import com.deskdeck.ui.spotify.ArtTarget
import com.deskdeck.data.spotify.SpotifyLibrary
import com.deskdeck.burnin.DisplayMode
import com.deskdeck.burnin.KeepScreenOn
import com.deskdeck.burnin.LocalInteractionTracker
import com.deskdeck.burnin.rememberBurnInDecision
import com.deskdeck.data.fake.FakeData
import com.deskdeck.domain.Lyrics
import com.deskdeck.domain.NowPlaying
import com.deskdeck.domain.PlaybackInfo
import com.deskdeck.domain.PlaybackStatus
import com.deskdeck.domain.RailApp
import com.deskdeck.ui.ambient.AmbientScreen
import com.deskdeck.ui.ambient.DimOverlay
import com.deskdeck.ui.ambient.PausedClockScreen
import com.deskdeck.ui.common.rememberPlaybackPosition
import com.deskdeck.ui.components.CardPalette
import com.deskdeck.ui.components.FluidBackground
import com.deskdeck.ui.components.LeftRail
import com.deskdeck.ui.components.LyricsPanel
import com.deskdeck.ui.components.NowPlayingCard
import com.deskdeck.ui.components.RailActionSheet
import com.deskdeck.ui.components.RailSheetRequest
import com.deskdeck.ui.components.animatedCardPalette
import com.deskdeck.ui.picker.AppPickerOverlay
import com.deskdeck.ui.picker.PickerMode
import com.deskdeck.ui.settings.LocalAppSettings
import com.deskdeck.ui.theme.DeskDeckTheme
import com.deskdeck.ui.theme.Dimens
import com.deskdeck.ui.theme.LocalMotion
import com.deskdeck.ui.theme.MotionSpec
import com.deskdeck.ui.theme.TrueBlack
import com.deskdeck.ui.theme.screenEntrance
import com.deskdeck.ui.lyrics.LocalLyricsDelay
import com.deskdeck.ui.volume.VolumeRow

@Composable
fun DashboardRoute(
    notificationAccessGranted: Boolean,
    onGrantAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    burnInEnabled: Boolean,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val railApps by viewModel.railApps.collectAsStateWithLifecycle()
    val notificationCount by viewModel.notificationCount.collectAsStateWithLifecycle()
    val railState by viewModel.railState.collectAsStateWithLifecycle()
    val emptySource by viewModel.emptySource.collectAsStateWithLifecycle()
    val anyPlaying by viewModel.anyPlaying.collectAsStateWithLifecycle()
    val motion = LocalMotion.current
    var foreground by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        foreground = true
        onPauseOrDispose { foreground = false }
    }
    val settings = LocalAppSettings.current
    var picker by rememberSaveable { mutableStateOf<PickerMode?>(null) }
    var railSheet by remember { mutableStateOf<RailSheetRequest?>(null) }
    var libraryOpen by rememberSaveable { mutableStateOf(false) }
    var flight by remember { mutableStateOf<StartedPlaylist?>(null) }
    val artTarget = remember { ArtTarget() }
    val currentOnOpenSettings by rememberUpdatedState(onOpenSettings)
    // Picker and navigation are UI state, so these actions are handled here; the rest go to the ViewModel.
    val actions = remember(viewModel) {
        object : DashboardActions by viewModel {
            override fun onAllApps() {
                picker = PickerMode.LAUNCH
            }

            override fun onAddRailApp() {
                picker = PickerMode.EDIT
            }

            override fun onRailAppLongClick(app: RailApp, anchorY: Float) {
                railSheet = RailSheetRequest(app, anchorY)
            }

            override fun onSettings() = currentOnOpenSettings()

            override fun onOpenLibrary() {
                libraryOpen = true
            }
        }
    }

    val tracker = LocalInteractionTracker.current
    val burnIn = rememberBurnInDecision(
        settings = settings.burnIn,
        keepScreenOn = settings.keepScreenOn,
        // Any app playing counts, even one that isn't the selected source.
        isPlaying = anyPlaying,
        enabled = burnInEnabled && picker == null && railSheet == null && !libraryOpen,
    )
    KeepScreenOn(enabled = burnIn.keepScreenOn)
    val dashboardVisible = burnIn.mode == DisplayMode.ACTIVE || burnIn.mode == DisplayMode.DIMMED
    val position = rememberPlaybackPosition(nowPlaying?.playback, nowPlaying?.track?.durationMs, everyFrame = dashboardVisible)
    val wake = { tracker.onInteraction(force = true) }
    val audioDelay by viewModel.audioDelay.collectAsStateWithLifecycle()
    // Measured only while something plays, so the silent test track never wakes a speaker.
    LaunchedEffect(anyPlaying, foreground, audioDelay.measured) {
        if (anyPlaying && foreground && !audioDelay.measured) viewModel.measureAudioDelay()
    }
    val audioRoute by viewModel.audioRoute.collectAsStateWithLifecycle()
    val lyricsDelay = (if (settings.lyricsLatencyCompensation) audioDelay.ms else 0L) +
        (settings.lyricsRouteAdjustMs[audioRoute.id] ?: 0)

    CompositionLocalProvider(LocalLyricsDelay provides lyricsDelay, LocalArtTarget provides artTarget) {
    Box(Modifier.fillMaxSize()) {
        DashboardScreen(
            nowPlaying = nowPlaying,
            lyrics = lyrics,
            railApps = railApps,
            notificationCount = notificationCount,
            notificationAccessGranted = notificationAccessGranted,
            onGrantAccess = onGrantAccess,
            actions = actions,
            position = position,
            railState = railState,
            emptySource = emptySource,
            animateBackground = foreground && dashboardVisible && burnInEnabled && picker == null && railSheet == null && !libraryOpen,
            volumeControl = { VolumeRow(Modifier.fillMaxWidth()) },
        )
        AppPickerOverlay(mode = picker, onDismiss = { picker = null })
        if (settings.spotifyLibrary) {
            LibraryOverlay(
                visible = libraryOpen,
                onDismiss = { libraryOpen = false },
                onStarted = {
                    libraryOpen = false
                    flight = it
                },
                onOpenSpotify = { actions.onOpenApp(SpotifyLibrary.SPOTIFY_PACKAGE) },
                onOpenSettings = {
                    libraryOpen = false
                    actions.onSettings()
                },
            )
            CoverFlight(flight = flight, target = artTarget, onDone = { flight = null })
        }
        RailActionSheet(
            request = railSheet,
            pinnedPackage = railState.pinnedPackage,
            actions = actions,
            onDismiss = { railSheet = null },
        )

        DimOverlay(visible = burnIn.mode == DisplayMode.DIMMED, level = settings.burnIn.dimLevel, onWake = wake)
        AnimatedVisibility(
            visible = burnIn.mode == DisplayMode.AMBIENT,
            enter = fadeIn(motion.fade(MotionSpec.AMBIENT_MS)),
            exit = fadeOut(motion.fade()),
        ) {
            AmbientScreen(nowPlaying = nowPlaying, lyrics = lyrics, position = position, onWake = wake)
        }
        AnimatedVisibility(
            visible = burnIn.mode == DisplayMode.PAUSED_CLOCK,
            enter = fadeIn(motion.fade(MotionSpec.AMBIENT_MS)),
            exit = fadeOut(motion.fade()),
        ) {
            PausedClockScreen(onWake = wake)
        }
    }
    }
}

@Composable
fun DashboardScreen(
    nowPlaying: NowPlaying?,
    lyrics: LyricsUiState,
    railApps: List<RailApp>,
    notificationCount: Int,
    actions: DashboardActions,
    notificationAccessGranted: Boolean = true,
    onGrantAccess: () -> Unit = {},
    position: State<Long> = rememberPlaybackPosition(nowPlaying?.playback, nowPlaying?.track?.durationMs),
    railState: RailState = RailState(highlightPackage = nowPlaying?.sourcePackage),
    emptySource: EmptySourceUi? = null,
    animateBackground: Boolean = false,
    /** The card's volume row; a slot so previews work without the volume ViewModel. */
    volumeControl: (@Composable () -> Unit)? = null,
) {
    val settings = LocalAppSettings.current
    val palette = animatedCardPalette(
        colors = nowPlaying?.colors?.takeIf { settings.dynamicColors },
        fallbackAccent = MaterialTheme.colorScheme.primary,
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(TrueBlack)
            .screenEntrance(),
    ) {
        FluidBackground(
            colors = nowPlaying?.colors?.takeIf { settings.dynamicColors },
            fallbackAccent = MaterialTheme.colorScheme.primary,
            animate = animateBackground,
            modifier = Modifier.fillMaxSize(),
        )
        val motion = LocalMotion.current
        val deskSize = LocalDeskSize.current
        // One layout per size class; the art and title travel between them where it's cheap.
        SharedTransitionLayout(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = deskSize,
            transitionSpec = { fadeIn(motion.fade(MotionSpec.LONG_MS)) togetherWith fadeOut(motion.fade()) },
            label = "deskLayout",
        ) { size ->
        val scopes = remember(this@SharedTransitionLayout, this) { SharedLayoutScopes(this@SharedTransitionLayout, this) }
        CompositionLocalProvider(LocalSharedLayoutScopes provides scopes) {
        if (size == DeskSize.COMPACT) {
            MiniPlayer(
                nowPlaying = nowPlaying,
                position = position,
                actions = actions,
                accessMissing = !notificationAccessGranted,
                onGrantAccess = onGrantAccess,
                emptySource = emptySource,
                onOpenApp = actions::onOpenApp,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(Dimens.screenPadding),
            )
        } else Row(
            modifier = Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(Dimens.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(Dimens.zoneGap),
        ) {
            LeftRail(
                apps = railApps,
                state = railState,
                animate = animateBackground,
                notificationCount = notificationCount,
                onAppClick = actions::onRailAppClick,
                onAppLongClick = actions::onRailAppLongClick,
                onAddApp = actions::onAddRailApp,
                onAllApps = actions::onAllApps,
                onSettings = actions::onSettings,
                onNotifications = actions::onOpenNotifications,
                onToggleNight = actions::onToggleNight,
            )
            if (size == DeskSize.MEDIUM) {
                MediumPanels(
                    nowPlaying = nowPlaying,
                    lyrics = lyrics,
                    position = position,
                    palette = palette,
                    actions = actions,
                    accessMissing = !notificationAccessGranted,
                    onGrantAccess = onGrantAccess,
                    emptySource = emptySource,
                    volumeControl = volumeControl,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            } else {
                // Long-press the card and drag it across to swap sides with the lyrics.
                SwappablePanels(
                    cardOnRight = settings.nowPlayingOnRight,
                    onCardSideChange = actions::onMoveNowPlaying,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    card = { cardModifier ->
                        NowPlayingCard(
                            nowPlaying = nowPlaying,
                            position = position,
                            palette = palette,
                            actions = actions,
                            accessMissing = !notificationAccessGranted,
                            onGrantAccess = onGrantAccess,
                            emptySource = emptySource,
                            onOpenApp = actions::onOpenApp,
                            onGetApp = actions::onGetApp,
                            volumeControl = volumeControl,
                            onOpenLibrary = actions::onOpenLibrary.takeIf { settings.spotifyLibrary },
                            modifier = cardModifier,
                        )
                    },
                    lyrics = { lyricsModifier ->
                        LyricsPanel(state = lyrics, position = position, actions = actions, modifier = lyricsModifier)
                    },
                )
            }
        }
        }
        }
        }
    }
}

/**
 * Medium windows (half the tablet in split screen): the Now Playing card, and under it the line
 * being sung with one before and after, when there's room for both. Otherwise the card alone.
 */
@Composable
private fun MediumPanels(
    nowPlaying: NowPlaying?,
    lyrics: LyricsUiState,
    position: State<Long>,
    palette: CardPalette,
    actions: DashboardActions,
    accessMissing: Boolean,
    onGrantAccess: () -> Unit,
    emptySource: EmptySourceUi?,
    volumeControl: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val cardMin = (if (volumeControl != null) 496.dp else 440.dp) * fontScale
        val showLyrics = nowPlaying != null && lyrics.hasMiniLyrics() && maxHeight >= cardMin + Dimens.zoneGap + MiniLyricsHeight
        Column(Modifier.fillMaxSize()) {
            NowPlayingCard(
                nowPlaying = nowPlaying,
                position = position,
                palette = palette,
                actions = actions,
                accessMissing = accessMissing,
                onGrantAccess = onGrantAccess,
                emptySource = emptySource,
                onOpenApp = actions::onOpenApp,
                onGetApp = actions::onGetApp,
                volumeControl = volumeControl,
                onOpenLibrary = actions::onOpenLibrary.takeIf { LocalAppSettings.current.spotifyLibrary },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            AnimatedVisibility(visible = showLyrics, enter = fadeIn(LocalMotion.current.fade()), exit = fadeOut(LocalMotion.current.fade())) {
                MiniLyrics(lyrics, position, Modifier.padding(top = Dimens.zoneGap).fillMaxWidth().height(MiniLyricsHeight))
            }
        }
    }
}

private val MiniLyricsHeight = 168.dp

// region Previews

private val previewActions = object : DashboardActions {}

private fun previewNowPlaying(index: Int = 0): NowPlaying {
    val fake = FakeData.tracks[index]
    return NowPlaying(
        track = fake.track,
        playback = PlaybackInfo(
            status = PlaybackStatus.PAUSED,
            positionMs = 36_000,
            updatedAtElapsedMs = 0,
            shuffleSupported = true,
            repeatSupported = true,
        ),
        sourcePackage = fake.sourcePackage,
        sourceLabel = fake.sourceLabel,
        colors = fake.colors,
    )
}

@Preview(name = "Tab S9 – synced lyrics", widthDp = 1280, heightDp = 800)
@Composable
private fun DashboardPreview() {
    val np = previewNowPlaying()
    val lines = (FakeData.lyricsFor(np.track.id) as Lyrics.Synced).lines
    DeskDeckTheme {
        DashboardScreen(
            nowPlaying = np,
            lyrics = LyricsUiState.Synced(np.track.id, lines),
            railApps = FakeData.railApps,
            notificationCount = 3,
            actions = previewActions,
            position = remember { mutableLongStateOf(36_000L) },
        )
    }
}

@Preview(name = "Nothing playing", widthDp = 1280, heightDp = 800)
@Composable
private fun DashboardEmptyPreview() {
    DeskDeckTheme {
        DashboardScreen(
            nowPlaying = null,
            lyrics = LyricsUiState.Idle,
            railApps = FakeData.railApps,
            notificationCount = 0,
            actions = previewActions,
        )
    }
}

@Preview(name = "Notification access missing", widthDp = 1280, heightDp = 800)
@Composable
private fun DashboardNoAccessPreview() {
    DeskDeckTheme {
        DashboardScreen(
            nowPlaying = null,
            lyrics = LyricsUiState.Idle,
            railApps = FakeData.railApps,
            notificationCount = 0,
            actions = previewActions,
            notificationAccessGranted = false,
        )
    }
}

@Preview(name = "No lyrics, no art", widthDp = 1280, heightDp = 800)
@Composable
private fun DashboardNotFoundPreview() {
    DeskDeckTheme {
        DashboardScreen(
            nowPlaying = previewNowPlaying(index = 2),
            lyrics = LyricsUiState.NotFound,
            railApps = FakeData.railApps,
            notificationCount = 12,
            actions = previewActions,
        )
    }
}

@Composable
private fun SizedDashboardPreview(size: DeskSize) {
    val np = previewNowPlaying()
    val lines = (FakeData.lyricsFor(np.track.id) as Lyrics.Synced).lines
    DeskDeckTheme {
        CompositionLocalProvider(LocalDeskSize provides size) {
            DashboardScreen(
                nowPlaying = np,
                lyrics = LyricsUiState.Synced(np.track.id, lines),
                railApps = FakeData.railApps,
                notificationCount = 3,
                actions = previewActions,
                position = remember { mutableLongStateOf(36_000L) },
            )
        }
    }
}

@Preview(name = "Expanded – full tablet", widthDp = 1317, heightDp = 823)
@Composable
private fun DashboardExpandedPreview() = SizedDashboardPreview(DeskSize.EXPANDED)

@Preview(name = "Medium – half split screen", widthDp = 650, heightDp = 823)
@Composable
private fun DashboardMediumPreview() = SizedDashboardPreview(DeskSize.MEDIUM)

@Preview(name = "Compact – narrow split", widthDp = 430, heightDp = 823)
@Composable
private fun DashboardCompactPreview() = SizedDashboardPreview(DeskSize.COMPACT)

@Preview(name = "Compact – pop-up view", widthDp = 560, heightDp = 340)
@Composable
private fun DashboardPopUpPreview() = SizedDashboardPreview(DeskSize.COMPACT)

@Preview(name = "Compact – nothing playing", widthDp = 430, heightDp = 600)
@Composable
private fun DashboardCompactEmptyPreview() {
    DeskDeckTheme {
        CompositionLocalProvider(LocalDeskSize provides DeskSize.COMPACT) {
            DashboardScreen(
                nowPlaying = null,
                lyrics = LyricsUiState.Idle,
                railApps = FakeData.railApps,
                notificationCount = 0,
                actions = previewActions,
            )
        }
    }
}

// endregion
