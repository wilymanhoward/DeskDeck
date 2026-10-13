package com.deskdeck.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
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
    val shift by rememberPixelShift(settings.burnIn.pixelShift, settings.burnIn.pixelShiftDp)

    // DeskDeck never leaves the screen by itself: Back on the dashboard does nothing. Overlays
    // (sheet, picker, settings) register their own handlers later, so those still close with Back.
    BackHandler(enabled = accessGranted || onboardingSkipped) { }

    DeskDeckTheme(accent = Color(settings.accent.argb)) {
        CompositionLocalProvider(
            LocalAppSettings provides settings,
            LocalInteractionTracker provides tracker,
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
                        transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) },
                        label = "appRoot",
                    ) { showDashboard ->
                        if (showDashboard) {
                            DashboardRoute(
                                notificationAccessGranted = accessGranted,
                                onGrantAccess = openAccessSettings,
                                onOpenSettings = { settingsOpen = true },
                                burnInEnabled = !settingsOpen,
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
                    AnimatedVisibility(visible = settingsOpen, enter = fadeIn(tween(250)), exit = fadeOut(tween(200))) {
                        KeepScreenOn(enabled = true)
                        SettingsScreen(onBack = { settingsOpen = false })
                    }
                }
                BrightnessCapOverlay(cap = settings.burnIn.brightnessCap)
            }
        }
    }
}
