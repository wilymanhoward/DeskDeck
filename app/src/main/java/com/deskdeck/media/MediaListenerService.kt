package com.deskdeck.media

import android.service.notification.NotificationListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The notification listener. Its main job is to exist: an enabled listener is what lets
 * [MediaSessionRepository] read other apps' media sessions. Connection changes are forwarded so
 * session tracking starts the moment access is granted, even if the activity isn't resumed.
 * (Notification counting for the left rail is added in M4.)
 */
@AndroidEntryPoint
class MediaListenerService : NotificationListenerService() {

    @Inject lateinit var notificationAccess: NotificationAccess

    override fun onListenerConnected() {
        super.onListenerConnected()
        notificationAccess.refresh()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        notificationAccess.refresh()
    }
}
