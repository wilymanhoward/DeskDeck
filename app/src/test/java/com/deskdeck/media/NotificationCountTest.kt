package com.deskdeck.media

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationCountTest {

    private fun n(
        pkg: String = "chat.app",
        ongoing: Boolean = false,
        clearable: Boolean = true,
        summary: Boolean = false,
    ) = NotificationFacts(pkg, ongoing, clearable, summary)

    @Test
    fun `counts dismissable notifications`() {
        assertEquals(2, countUserNotifications(listOf(n(), n("mail.app")), ownPackage = "com.deskdeck"))
    }

    @Test
    fun `ignores ongoing ones like music players and downloads`() {
        assertEquals(1, countUserNotifications(listOf(n(), n("com.spotify.music", ongoing = true)), "com.deskdeck"))
    }

    @Test
    fun `ignores non-dismissable ones`() {
        assertEquals(0, countUserNotifications(listOf(n(clearable = false)), "com.deskdeck"))
    }

    @Test
    fun `group summaries are not counted twice`() {
        val group = listOf(n(summary = true), n(), n(), n())
        assertEquals(3, countUserNotifications(group, "com.deskdeck"))
    }

    @Test
    fun `own notifications are ignored`() {
        assertEquals(0, countUserNotifications(listOf(n("com.deskdeck")), "com.deskdeck"))
    }

    @Test
    fun `samsung's media mirror in system ui isn't counted`() {
        val mirror = NotificationFacts("com.android.systemui", isOngoing = false, isClearable = true, isGroupSummary = false, channelId = "MediaOngoingActivity")
        val screenshot = NotificationFacts("com.android.systemui", isOngoing = false, isClearable = true, isGroupSummary = false, channelId = "SCN")
        assertEquals(1, countUserNotifications(listOf(mirror, screenshot), ownPackage = "com.deskdeck"))
    }
}
