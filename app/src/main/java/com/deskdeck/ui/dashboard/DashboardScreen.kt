package com.deskdeck.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deskdeck.R
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
import com.deskdeck.ui.components.LeftRail
import com.deskdeck.ui.components.LyricsPanel
import com.deskdeck.ui.components.NowPlayingCard
import com.deskdeck.ui.components.animatedCardPalette
import com.deskdeck.ui.picker.AppPickerOverlay
import com.deskdeck.ui.picker.PickerMode
import com.deskdeck.ui.settings.LocalAppSettings
import com.deskdeck.ui.theme.DeskDeckTheme
import com.deskdeck.ui.theme.Dimens
import com.deskdeck.ui.theme.TrueBlack

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
    val settings = LocalAppSettings.current
    var picker by rememberSaveable { mutableStateOf<PickerMode?>(null) }
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

            override fun onRailAppLongClick(app: RailApp) {
                picker = PickerMode.EDIT
            }

            override fun onSettings() = currentOnOpenSettings()
        }
    }

    val tracker = LocalInteractionTracker.current
    val burnIn = rememberBurnInDecision(
        settings = settings.burnIn,
        keepScreenOn = settings.keepScreenOn,
        isPlaying = nowPlaying?.playback?.isPlaying == true,
        enabled = burnInEnabled && picker == null,
    )
    KeepScreenOn(enabled = burnIn.keepScreenOn)
    val dashboardVisible = burnIn.mode == DisplayMode.ACTIVE || burnIn.mode == DisplayMode.DIMMED
    val position = rememberPlaybackPosition(nowPlaying?.playback, nowPlaying?.track?.durationMs, everyFrame = dashboardVisible)
    val wake = { tracker.onInteraction(force = true) }

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
        )
        AppPickerOverlay(mode = picker, onDismiss = { picker = null })

        DimOverlay(visible = burnIn.mode == DisplayMode.DIMMED, level = settings.burnIn.dimLevel, onWake = wake)
        AnimatedVisibility(
            visible = burnIn.mode == DisplayMode.AMBIENT,
            enter = fadeIn(tween(1_500)),
            exit = fadeOut(tween(300)),
        ) {
            AmbientScreen(nowPlaying = nowPlaying, lyrics = lyrics, position = position, onWake = wake)
        }
        AnimatedVisibility(
            visible = burnIn.mode == DisplayMode.PAUSED_CLOCK,
            enter = fadeIn(tween(1_500)),
            exit = fadeOut(tween(300)),
        ) {
            PausedClockScreen(onWake = wake)
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
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(Dimens.screenPadding),
    ) {
        val compact = maxWidth < Dimens.compactWidthThreshold
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.zoneGap),
        ) {
            LeftRail(
                apps = railApps,
                activePackage = nowPlaying?.sourcePackage,
                notificationCount = notificationCount,
                onAppClick = actions::onRailAppClick,
                onAppLongClick = actions::onRailAppLongClick,
                onAddApp = actions::onAddRailApp,
                onAllApps = actions::onAllApps,
                onSettings = actions::onSettings,
            )
            if (compact) {
                CompactPanels(
                    nowPlaying = nowPlaying,
                    lyrics = lyrics,
                    position = position,
                    palette = palette,
                    actions = actions,
                    accessMissing = !notificationAccessGranted,
                    onGrantAccess = onGrantAccess,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            } else {
                // ~40% / ~45% of the screen, per the layout spec.
                NowPlayingCard(
                    nowPlaying = nowPlaying,
                    position = position,
                    palette = palette,
                    actions = actions,
                    accessMissing = !notificationAccessGranted,
                    onGrantAccess = onGrantAccess,
                    modifier = Modifier
                        .weight(0.47f)
                        .fillMaxHeight(),
                )
                LyricsPanel(
                    state = lyrics,
                    position = position,
                    actions = actions,
                    modifier = Modifier
                        .weight(0.53f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

/** Narrow windows (split-screen): one panel at a time with a switcher on top. */
@Composable
private fun CompactPanels(
    nowPlaying: NowPlaying?,
    lyrics: LyricsUiState,
    position: State<Long>,
    palette: CardPalette,
    actions: DashboardActions,
    accessMissing: Boolean,
    onGrantAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !showLyrics,
                onClick = { showLyrics = false },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.tab_now_playing), style = MaterialTheme.typography.labelLarge) }
            SegmentedButton(
                selected = showLyrics,
                onClick = { showLyrics = true },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.tab_lyrics), style = MaterialTheme.typography.labelLarge) }
        }
        Spacer(Modifier.height(12.dp))
        val panelModifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        if (showLyrics) {
            LyricsPanel(lyrics, position, actions, panelModifier)
        } else {
            NowPlayingCard(nowPlaying, position, palette, actions, accessMissing, onGrantAccess, panelModifier)
        }
    }
}

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

@Preview(name = "Split-screen (compact)", widthDp = 640, heightDp = 800)
@Composable
private fun DashboardCompactPreview() {
    DeskDeckTheme {
        DashboardScreen(
            nowPlaying = previewNowPlaying(index = 1),
            lyrics = LyricsUiState.Plain(FakeData.tracks[1].track.id, "Sample plain lyrics"),
            railApps = FakeData.railApps,
            notificationCount = 1,
            actions = previewActions,
        )
    }
}

// endregion
