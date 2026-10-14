package com.deskdeck.media

import android.app.Notification
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.deskdeck.media.call.CallMonitor
import com.deskdeck.media.call.toCall
import com.deskdeck.media.call.toCallNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The notification listener. Being enabled is what lets [MediaSessionRepository] read other apps'
 * media sessions; it also keeps the rail's notification count and feeds [NotificationBanners].
 * Content (title and text) is read only for a notification that will show a banner, or while the
 * notification panel is open, and is kept in memory only while shown; nothing is written to disk.
 */
@AndroidEntryPoint
class MediaListenerService : NotificationListenerService() {

    @Inject lateinit var notificationAccess: NotificationAccess

    @Inject lateinit var notificationCounter: NotificationCounter

    @Inject lateinit var banners: NotificationBanners

    @Inject lateinit var appLabels: AppLabels

    @Inject lateinit var calls: CallMonitor

    override fun onListenerConnected() {
        super.onListenerConnected()
        banners.canceller = { key -> runCatching { cancelNotification(key) } }
        banners.activeReader = ::readActive
        notificationAccess.refresh()
        recount()
        // A call that was already ringing or on when the listener (re)connected.
        val ranking = runCatching { currentRanking }.getOrNull()
        runCatching { activeNotifications.orEmpty() }.getOrDefault(emptyArray()).forEach { offerCall(it, ranking) }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        banners.canceller = null
        banners.activeReader = null
        notificationCounter.reset()
        calls.reset()
        notificationAccess.refresh()
    }

    // Only the ranking variants are overridden: the framework's defaults just forward to the plain ones.
    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        recount()
        if (sbn == null) return
        // Calls have their own path (incoming call banner, call layout), never the banner queue.
        if (offerCall(sbn, rankingMap)) return
        offerBanner(sbn, rankingMap)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        recount()
        if (sbn != null) {
            banners.onRemoved(sbn.key)
            calls.onRemoved(sbn.key)
        }
    }

    /** True if [sbn] is a live call (WhatsApp so far), now handed to [CallMonitor]. */
    private fun offerCall(sbn: StatusBarNotification, rankingMap: RankingMap?): Boolean {
        if (!calls.handles(sbn.packageName)) return false
        val passesDnd = passesDnd(sbn, rankingMap)
        val detection = calls.detect(sbn.toCallNotification(passesDnd)) ?: return false
        calls.onCall(sbn.toCall(this, detection, appLabels.labelOf(sbn.packageName), passesDnd), detection)
        return true
    }

    private fun passesDnd(sbn: StatusBarNotification, rankingMap: RankingMap?): Boolean {
        val ranking = Ranking().takeIf { rankingMap?.getRanking(sbn.key, it) == true }
        return ranking?.matchesInterruptionFilter() ?: (currentInterruptionFilter <= INTERRUPTION_FILTER_ALL)
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
        banners.onPosted(sbn.key, facts, ownPackage = packageName) { sbn.toBanner() }
    }

    private fun StatusBarNotification.toBanner(): Banner {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE_BIG) ?: extras.getCharSequence(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")
        val expanded = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: lines ?: text?.toString()
        return Banner(
            key = key,
            packageName = packageName,
            appLabel = appLabels.labelOf(packageName),
            title = title?.toString()?.trim(),
            text = text?.toString()?.trim(),
            expandedText = expanded?.trim(),
            postedAt = postTime,
        )
    }

    /** The notifications the rail counts, with content, for the notification panel. */
    private fun readActive(): List<Banner> {
        val active = try {
            activeNotifications.orEmpty()
        } catch (_: SecurityException) {
            return emptyList()
        }
        // The same rule as the rail count, so the panel lists exactly what the badge counts.
        return active.filter { isUserNotification(it.facts(), ownPackage = packageName) }.map { it.toBanner() }
    }

    private fun StatusBarNotification.facts() = NotificationFacts(
        packageName = packageName,
        isOngoing = isOngoing,
        isClearable = isClearable,
        isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        channelId = notification.channelId,
    )

    private fun recount() {
        val active = try {
            activeNotifications.orEmpty()
        } catch (_: SecurityException) {
            // Called while not (or no longer) connected.
            return
        }
        val facts = active.map { it.facts() }
        notificationCounter.update(items = facts, ownPackage = packageName)
        banners.onActiveChanged()
        banners.addKnownPackages(
            facts.filter { !it.isOngoing && !it.isGroupSummary && it.packageName != packageName }.map { it.packageName },
        )
    }
}
