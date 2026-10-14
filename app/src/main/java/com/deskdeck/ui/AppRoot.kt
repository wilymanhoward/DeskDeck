package com.deskdeck.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deskdeck.burnin.InteractionTracker
import com.deskdeck.burnin.KeepScreenOn
import com.deskdeck.burnin.LocalInteractionTracker
import com.deskdeck.burnin.observeInteractions
import com.deskdeck.burnin.rememberPixelShift
import com.deskdeck.ui.ambient.BrightnessCapOverlay
import com.deskdeck.ui.dashboard.DashboardRoute
import com.deskdeck.ui.onboarding.OnboardingScreen
import com.deskdeck.ui.settings.LocalAppSettings
import com.deskdeck.ui.settings.SettingsScreen
import com.deskdeck.ui.theme.DeskDeckTheme
import com.deskdeck.ui.theme.LocalMotion
import com.deskdeck.ui.theme.MotionSpec
import com.deskdeck.ui.theme.rememberMotionSpec
import com.deskdeck.ui.brief.BriefViewModel
import com.deskdeck.ui.brief.MorningBriefScreen
import com.deskdeck.domain.brief.briefVisible
import com.deskdeck.ui.common.rememberCurrentTime
import java.time.Instant
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.LaunchedEffect
import com.deskdeck.ui.night.LocalNightMode
import com.deskdeck.ui.night.NightScreen
import com.deskdeck.ui.night.NightViewModel
import com.deskdeck.ui.notifications.BannerHold
import com.deskdeck.ui.notifications.BannerHost
import com.deskdeck.ui.notifications.LocalBannerHold
import com.deskdeck.ui.notifications.NotificationCenter
import com.deskdeck.ui.volume.VolumeHud
import com.deskdeck.ui.adaptive.LocalDeskSize
import com.deskdeck.ui.adaptive.currentDeskSize
import kotlin.math.roundToInt

/**
 * App root: theme and settings, the burn-in measures that apply to every screen (pixel shift,
 * brightness cap, touch tracking), and navigation between onboarding, dashboard and settings.
 */
@Composable
fun AppRoot(viewModel: AppViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val accessGranted by viewModel.notificationAccessGranted.collectAsStateWithLifecycle()
    var onboardingSkipped by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val openAccessSettings = { viewModel.openNotificationAccessSettings(context) }

    val tracker = remember { InteractionTracker() }
    // Full tablet, half split screen, or a narrow split / pop-up window; follows resizes live.
    val deskSize = currentDeskSize()
    val motion = rememberMotionSpec()
    val bannerHold = remember { BannerHold() }
    val shift by rememberPixelShift(settings.burnIn.pixelShift, settings.burnIn.pixelShiftDp)
    val nightViewModel: NightViewModel = hiltViewModel()
    val nightState by nightViewModel.state.collectAsStateWithLifecycle()
    // Night Mode takes over only the dashboard (not onboarding), and only while DeskDeck is in front.
    val nightShown = nightState.active && (accessGranted || onboardingSkipped)

    // Morning Brief: from the scheduled wake-up for the chosen time, until Done, or previewed.
    val briefViewModel: BriefViewModel = hiltViewModel()
    val brief by briefViewModel.brief.collectAsStateWithLifecycle()
    val briefRefreshing by briefViewModel.refreshing.collectAsStateWithLifecycle()
    val briefPreview by briefViewModel.previewRequested.collectAsStateWithLifecycle()
    var dismissedWakeMs by rememberSaveable { mutableLongStateOf(0L) }
    val minute by rememberCurrentTime(withSeconds = false)
    val briefScheduled = remember(minute, nightState, settings.night, dismissedWakeMs) {
        briefVisible(
            now = Instant.now(),
            enabled = settings.night.enabled && settings.night.brief,
            nightActive = nightState.active,
            lastWake = nightState.lastWakeAt,
            duration = settings.night.briefDuration,
            dismissedWake = dismissedWakeMs.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
        )
    }
    val briefShown = (briefPreview || briefScheduled) && !nightShown && (accessGranted || onboardingSkipped)
    LaunchedEffect(briefShown) { if (briefShown) briefViewModel.refreshIfStale(settings.night) }
    LaunchedEffect(briefPreview) { if (briefPreview) settingsOpen = false }

    // DeskDeck never leaves the screen by itself: Back on the dashboard does nothing. Overlays
    // (sheet, picker, settings) register their own handlers later, so those still close with Back.
    BackHandler(enabled = accessGranted || onboardingSkipped) { }

    DeskDeckTheme(accent = Color(settings.accent.argb)) {
        CompositionLocalProvider(
            LocalAppSettings provides settings,
            LocalInteractionTracker provides tracker,
            LocalMotion provides motion,
            LocalBannerHold provides bannerHold,
            LocalNightMode provides nightShown,
            LocalDeskSize provides deskSize,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .observeInteractions(tracker),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .offset { IntOffset(shift.x.roundToInt(), shift.y.roundToInt()) },
                ) {
                    AnimatedContent(
                        targetState = accessGranted || onboardingSkipped,
                        transitionSpec = { fadeIn(motion.fade(MotionSpec.LONG_MS)) togetherWith fadeOut(motion.fade()) },
                        label = "appRoot",
                    ) { showDashboard ->
                        if (showDashboard) {
                            DashboardRoute(
                                notificationAccessGranted = accessGranted,
                                onGrantAccess = openAccessSettings,
                                onOpenSettings = { settingsOpen = true },
                                // The night screen has its own burn-in care; dimming/ambient would fight it.
                                burnInEnabled = !settingsOpen && !nightShown && !briefShown,
                            )
                        } else {
                            // Setting things up: keep the screen on so it doesn't time out mid-read.
                            KeepScreenOn(enabled = true)
                            OnboardingScreen(
                                onOpenSettings = openAccessSettings,
                                onOpenAppInfo = { viewModel.openAppInfo(context) },
                                onSkip = { onboardingSkipped = true },
                            )
                        }
                    }
                    // A slow, calm fade between the dashboard and the night screen.
                    AnimatedVisibility(
                        visible = nightShown,
                        enter = fadeIn(motion.fade(NIGHT_FADE_MS)),
                        exit = fadeOut(motion.fade(NIGHT_FADE_MS)),
                    ) {
                        NightScreen(onOpenSettings = { settingsOpen = true }, viewModel = nightViewModel)
                    }
                    AnimatedVisibility(
                        visible = briefShown,
                        enter = fadeIn(motion.fade(NIGHT_FADE_MS)),
                        exit = fadeOut(motion.fade(MotionSpec.LONG_MS)),
                    ) {
                        val done = {
                            if (briefPreview) briefViewModel.endPreview() else dismissedWakeMs = nightState.lastWakeAt?.toEpochMilli() ?: 0L
                        }
                        BackHandler(onBack = done)
                        MorningBriefScreen(
                            brief = brief,
                            settings = settings.night,
                            hasMarketKey = briefViewModel.hasMarketKey,
                            refreshing = briefRefreshing,
                            onDone = done,
                        )
                    }
                    AnimatedVisibility(visible = settingsOpen, enter = fadeIn(motion.fade()), exit = fadeOut(motion.fade())) {
                        KeepScreenOn(enabled = true)
                        SettingsScreen(onBack = { settingsOpen = false })
                    }
                    // Above every screen (settings, ambient too) and inside the pixel shift.
                    BannerHost()
                    NotificationCenter()
                    VolumeHud()
                }
                BrightnessCapOverlay(cap = settings.burnIn.brightnessCap)
            }
        }
    }
}

private const val NIGHT_FADE_MS = 2_000
