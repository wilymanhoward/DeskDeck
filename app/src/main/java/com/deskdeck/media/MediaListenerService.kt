package com.deskdeck.media

import android.app.Notification
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The notification listener. Being enabled is what lets [MediaSessionRepository] read other apps'
 * media sessions; it also keeps the rail's notification count and feeds [NotificationBanners].
 * Content (title and text) is read only for a notification that will show a banner, and is kept
 * in memory just while that banner is queued; nothing is written to disk.
 */
@AndroidEntryPoint
class MediaListenerService : NotificationListenerService() {

    @Inject lateinit var notificationAccess: NotificationAccess

    @Inject lateinit var notificationCounter: NotificationCounter

    @Inject lateinit var banners: NotificationBanners

    @Inject lateinit var appLabels: AppLabels

    override fun onListenerConnected() {
        super.onListenerConnected()
        banners.canceller = { key -> runCatching { cancelNotification(key) } }
        notificationAccess.refresh()
        recount()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        banners.canceller = null
        notificationCounter.reset()
        notificationAccess.refresh()
    }

    // Only the ranking variants are overridden: the framework's defaults just forward to the plain ones.
    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        recount()
        if (sbn != null) offerBanner(sbn, rankingMap)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        recount()
        if (sbn != null) banners.onRemoved(sbn.key)
    }

    private fun offerBanner(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        val n = sbn.notification
        val extras = n.extras
        val ranking = Ranking().takeIf { rankingMap?.getRanking(sbn.key, it) == true }
        val title = extras.getCharSequence(Notification.EXTRA_TITLE_BIG) ?: extras.getCharSequence(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)
        val facts = PostedFacts(
            packageName = sbn.packageName,
            isOngoing = sbn.isOngoing,
            isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isMedia = extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
                extras.getString(Notification.EXTRA_TEMPLATE).orEmpty().contains("MediaStyle") ||
                n.category == Notification.CATEGORY_TRANSPORT,
            importance = ranking?.importance ?: NotificationManager.IMPORTANCE_UNSPECIFIED,
            passesDnd = ranking?.matchesInterruptionFilter() ?: (currentInterruptionFilter <= INTERRUPTION_FILTER_ALL),
            onlyAlertOnce = n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0,
            alreadyAlerted = banners.wasAlerted(sbn.key),
            hasContent = !title.isNullOrBlank() || !text.isNullOrBlank(),
        )
        banners.onPosted(sbn.key, facts, ownPackage = packageName) {
            val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")
            val expanded = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: lines ?: text?.toString()
            Banner(
                key = sbn.key,
                packageName = sbn.packageName,
                appLabel = appLabels.labelOf(sbn.packageName),
                title = title?.toString()?.trim(),
                text = text?.toString()?.trim(),
                expandedText = expanded?.trim(),
                postedAt = sbn.postTime,
            )
        }
    }

    private fun recount() {
        val active = try {
            activeNotifications.orEmpty()
        } catch (_: SecurityException) {
            // Called while not (or no longer) connected.
            return
        }
        val facts = active.map { sbn ->
            NotificationFacts(
                packageName = sbn.packageName,
                isOngoing = sbn.isOngoing,
                isClearable = sbn.isClearable,
                isGroupSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            )
        }
        notificationCounter.update(items = facts, ownPackage = packageName)
        banners.addKnownPackages(
            facts.filter { !it.isOngoing && !it.isGroupSummary && it.packageName != packageName }.map { it.packageName },
        )
    }
}
