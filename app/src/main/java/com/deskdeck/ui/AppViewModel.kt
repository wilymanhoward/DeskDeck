package com.deskdeck.ui

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.lifecycle.ViewModel
import com.deskdeck.media.NotificationAccess
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class AppViewModel @Inject constructor(
    private val notificationAccess: NotificationAccess,
) : ViewModel() {

    val notificationAccessGranted: StateFlow<Boolean> = notificationAccess.granted

    fun openNotificationAccessSettings(context: Context) {
        try {
            context.startActivity(notificationAccess.settingsIntent())
        } catch (_: ActivityNotFoundException) {
            // Some OEM builds lack the per-app detail screen; the list always exists.
            context.startActivity(notificationAccess.fallbackSettingsIntent())
        }
    }

    fun openAppInfo(context: Context) {
        context.startActivity(notificationAccess.appInfoIntent())
    }
}
