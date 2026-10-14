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
import com.deskdeck.ui.night.LocalNightMode
import com.deskdeck.ui.night.NightScreen
import com.deskdeck.ui.night.NightViewModel
import com.deskdeck.ui.notifications.BannerHold
import com.deskdeck.ui.notifications.BannerHost
import com.deskdeck.ui.notifications.LocalBannerHold
import com.deskdeck.ui.notifications.NotificationCenter
import com.deskdeck.ui.volume.VolumeHud
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
    val motion = rememberMotionSpec()
    val bannerHold = remember { BannerHold() }
    val shift by rememberPixelShift(settings.burnIn.pixelShift, settings.burnIn.pixelShiftDp)
    val nightViewModel: NightViewModel = hiltViewModel()
    val nightState by nightViewModel.state.collectAsStateWithLifecycle()
    // Night Mode takes over only the dashboard (not onboarding), and only while DeskDeck is in front.
    val nightShown = nightState.active && (accessGranted || onboardingSkipped)

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
                                burnInEnabled = !settingsOpen && !nightShown,
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
