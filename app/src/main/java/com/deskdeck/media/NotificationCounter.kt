package com.deskdeck.media

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What counting needs from a `StatusBarNotification`, as plain values for testing. */
data class NotificationFacts(
    val packageName: String,
    val isOngoing: Boolean,
    val isClearable: Boolean,
    val isGroupSummary: Boolean,
)

/**
 * Notifications a person would consider "unread": dismissable, not ongoing (music players,
 * downloads, navigation), and not a group summary (so a chat with 3 messages counts 3, not 4).
 */
fun isUserNotification(n: NotificationFacts, ownPackage: String): Boolean =
    !n.isOngoing && n.isClearable && !n.isGroupSummary && n.packageName != ownPackage

fun countUserNotifications(items: List<NotificationFacts>, ownPackage: String): Int =
    items.count { isUserNotification(it, ownPackage) }

/** Live count for the rail badge, fed by [MediaListenerService]. */
@Singleton
class NotificationCounter @Inject constructor() {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    fun update(items: List<NotificationFacts>, ownPackage: String) {
        _count.value = countUserNotifications(items, ownPackage)
    }

    fun reset() {
        _count.value = 0
    }
}
