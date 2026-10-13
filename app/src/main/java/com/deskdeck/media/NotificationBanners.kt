package com.deskdeck.media

import android.app.NotificationManager
import com.deskdeck.data.settings.AppSettings
import com.deskdeck.data.settings.NotificationSettings
import com.deskdeck.data.settings.SettingsRepository
import com.deskdeck.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

/** One in-app banner. Lives only in memory; nothing about a notification's content is stored. */
data class Banner(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val text: String?,
    /** The long form shown when the banner is expanded (big text or inbox lines), else [text]. */
    val expandedText: String?,
    /** Changes when the notification is updated, so an updated banner restarts its timer. */
    val postedAt: Long,
)

/** What the banner decision needs from a posted `StatusBarNotification` and its ranking, as plain values. */
data class PostedFacts(
    val packageName: String,
    val isOngoing: Boolean,
    val isGroupSummary: Boolean,
    /** A player's own notification (MediaStyle, a media session, or the transport category). */
    val isMedia: Boolean,
    /** The channel importance from the ranking; [NotificationManager.IMPORTANCE_UNSPECIFIED] if unknown. */
    val importance: Int,
    /** Whether the system would let it through the current Do Not Disturb filter. */
    val passesDnd: Boolean,
    val onlyAlertOnce: Boolean,
    /** A banner was already shown for this key (this is an update). */
    val alreadyAlerted: Boolean,
    val hasContent: Boolean,
)

/**
 * The banner rules. Like the system's heads-up rules, silent notifications (below default
 * importance) and updates flagged "only alert once" stay quiet; ongoing, player and group-summary
 * notifications never show a banner.
 */
fun shouldShowBanner(f: PostedFacts, ownPackage: String, settings: NotificationSettings): Boolean = when {
    !settings.banners -> false
    f.packageName == ownPackage || f.packageName in settings.mutedPackages -> false
    f.isOngoing || f.isGroupSummary || f.isMedia || !f.hasContent -> false
    f.importance != NotificationManager.IMPORTANCE_UNSPECIFIED && f.importance < NotificationManager.IMPORTANCE_DEFAULT -> false
    settings.respectDnd && !f.passesDnd -> false
    f.onlyAlertOnce && f.alreadyAlerted -> false
    else -> true
}

/**
 * The banner queue, fed by [MediaListenerService] and shown by the banner host. Newest first,
 * capped at [MAX_QUEUED]: a desk display should show what's happening now, and everything older
 * is still in the system shade and the rail count. Banners only arrive while DeskDeck is on
 * screen, so nothing piles up to pop out stale when it comes back.
 */
@Singleton
class NotificationBanners @Inject constructor(
    settingsRepository: SettingsRepository,
    @param:ApplicationScope scope: CoroutineScope,
) {
    private val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private val _queue = MutableStateFlow<List<Banner>>(emptyList())
    val queue: StateFlow<List<Banner>> = _queue.asStateFlow()

    private val _knownPackages = MutableStateFlow<Set<String>>(emptySet())

    /** Apps seen posting user notifications, for the per-app list in Settings. */
    val knownPackages: StateFlow<Set<String>> = _knownPackages.asStateFlow()

    private val alerted = mutableSetOf<String>()
    private var visible = false

    /** Set by the listener while it's connected; cancels a notification by key. */
    var canceller: ((String) -> Unit)? = null

    fun setVisible(isVisible: Boolean) {
        visible = isVisible
        if (!isVisible) _queue.value = emptyList()
    }

    fun wasAlerted(key: String): Boolean = key in alerted

    /** Called for every posted notification; [build] reads the content only if a banner will show. */
    fun onPosted(key: String, facts: PostedFacts, ownPackage: String, build: () -> Banner) {
        val notifications = settings.value.notifications
        if (!visible || !shouldShowBanner(facts, ownPackage, notifications)) return
        alerted += key
        val banner = build()
        _queue.value = (listOf(banner) + _queue.value.filter { it.key != key }).take(MAX_QUEUED)
    }

    fun onRemoved(key: String) {
        alerted -= key
        remove(key)
    }

    /** Hides the banner; the notification itself stays in the shade. */
    fun dismiss(key: String) = remove(key)

    /** Clears the notification from the system too; the rail count follows through the listener. */
    fun clear(key: String) {
        remove(key)
        canceller?.invoke(key)
    }

    fun addKnownPackages(packages: Collection<String>) {
        if (!_knownPackages.value.containsAll(packages)) _knownPackages.value = _knownPackages.value + packages
    }

    private fun remove(key: String) {
        _queue.value = _queue.value.filter { it.key != key }
    }

    companion object {
        const val MAX_QUEUED = 3
    }
}
