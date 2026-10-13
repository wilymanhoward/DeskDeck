package com.deskdeck.media

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The notification listener. Being enabled is what lets [MediaSessionRepository] read other apps'
 * media sessions; it also keeps the rail's notification count. Notification contents are never
 * read or stored, only the flags needed for counting.
 */
@AndroidEntryPoint
class MediaListenerService : NotificationListenerService() {

    @Inject lateinit var notificationAccess: NotificationAccess

    @Inject lateinit var notificationCounter: NotificationCounter

    override fun onListenerConnected() {
        super.onListenerConnected()
        notificationAccess.refresh()
        recount()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        notificationCounter.reset()
        notificationAccess.refresh()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = recount()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = recount()

    private fun recount() {
        val active = try {
            activeNotifications.orEmpty()
        } catch (_: SecurityException) {
            // Called while not (or no longer) connected.
            return
        }
        notificationCounter.update(
            items = active.map { sbn ->
                NotificationFacts(
                    packageName = sbn.packageName,
                    isOngoing = sbn.isOngoing,
                    isClearable = sbn.isClearable,
                    isGroupSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                )
            },
            ownPackage = packageName,
        )
    }
}
