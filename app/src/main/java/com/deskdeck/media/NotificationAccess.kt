package com.deskdeck.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the user has enabled DeskDeck under "Notification access". There is no broadcast for
 * this, so [refresh] is called when the activity resumes and when the listener (dis)connects.
 */
@Singleton
class NotificationAccess @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val listenerComponent = ComponentName(context, MediaListenerService::class.java)

    private val _granted = MutableStateFlow(isGranted())
    val granted: StateFlow<Boolean> = _granted.asStateFlow()

    fun refresh() {
        val granted = isGranted()
        if (granted && !_granted.value) {
            // After a fresh grant (or an app update) the system may not have bound the listener yet.
            NotificationListenerService.requestRebind(listenerComponent)
        }
        _granted.value = granted
    }

    private fun isGranted(): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    /** Opens DeskDeck's own toggle on Android 11+, otherwise the list of notification listeners. */
    fun settingsIntent(): Intent {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listenerComponent.flattenToString())
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun fallbackSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** App info page, where "Allow restricted settings" lives for sideloaded apps on Android 13+. */
    fun appInfoIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
