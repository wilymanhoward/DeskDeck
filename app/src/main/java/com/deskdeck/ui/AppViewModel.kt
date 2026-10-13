package com.deskdeck.ui

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deskdeck.data.settings.AppSettings
import com.deskdeck.data.settings.SettingsRepository
import com.deskdeck.media.NotificationAccess
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppViewModel @Inject constructor(
    private val notificationAccess: NotificationAccess,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val notificationAccessGranted: StateFlow<Boolean> = notificationAccess.granted

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

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
