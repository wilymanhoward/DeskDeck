package com.deskdeck.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deskdeck.ui.dashboard.DashboardRoute
import com.deskdeck.ui.onboarding.OnboardingScreen

/** Onboarding until notification access is granted (or skipped for this session), then the dashboard. */
@Composable
fun AppRoot(viewModel: AppViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val accessGranted by viewModel.notificationAccessGranted.collectAsStateWithLifecycle()
    var skipped by rememberSaveable { mutableStateOf(false) }
    val openSettings = { viewModel.openNotificationAccessSettings(context) }

    AnimatedContent(
        targetState = accessGranted || skipped,
        transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) },
        label = "appRoot",
    ) { showDashboard ->
        if (showDashboard) {
            DashboardRoute(
                notificationAccessGranted = accessGranted,
                onGrantAccess = openSettings,
            )
        } else {
            OnboardingScreen(
                onOpenSettings = openSettings,
                onOpenAppInfo = { viewModel.openAppInfo(context) },
                onSkip = { skipped = true },
            )
        }
    }
}
