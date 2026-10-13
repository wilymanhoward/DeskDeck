package com.deskdeck.media

import android.app.NotificationManager
import com.deskdeck.data.settings.NotificationSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BannerRulesTest {

    private val own = "com.deskdeck"
    private val defaults = NotificationSettings()

    private fun posted(
        pkg: String = "chat.app",
        ongoing: Boolean = false,
        summary: Boolean = false,
        media: Boolean = false,
        importance: Int = NotificationManager.IMPORTANCE_HIGH,
        passesDnd: Boolean = true,
        onlyAlertOnce: Boolean = false,
        alreadyAlerted: Boolean = false,
        hasContent: Boolean = true,
    ) = PostedFacts(pkg, ongoing, summary, media, importance, passesDnd, onlyAlertOnce, alreadyAlerted, hasContent)

    @Test
    fun `a normal message shows a banner`() {
        assertTrue(shouldShowBanner(posted(), own, defaults))
    }

    @Test
    fun `ongoing, player, summary and empty notifications never do`() {
        assertFalse(shouldShowBanner(posted(ongoing = true), own, defaults))
        assertFalse(shouldShowBanner(posted(media = true), own, defaults))
        assertFalse(shouldShowBanner(posted(summary = true), own, defaults))
        assertFalse(shouldShowBanner(posted(hasContent = false), own, defaults))
        assertFalse(shouldShowBanner(posted(pkg = own), own, defaults))
    }

    @Test
    fun `silent channels stay quiet, unknown importance is allowed`() {
        assertFalse(shouldShowBanner(posted(importance = NotificationManager.IMPORTANCE_LOW), own, defaults))
        assertTrue(shouldShowBanner(posted(importance = NotificationManager.IMPORTANCE_DEFAULT), own, defaults))
        assertTrue(shouldShowBanner(posted(importance = NotificationManager.IMPORTANCE_UNSPECIFIED), own, defaults))
    }

    @Test
    fun `updates flagged only-alert-once don't banner again`() {
        assertFalse(shouldShowBanner(posted(onlyAlertOnce = true, alreadyAlerted = true), own, defaults))
        assertTrue(shouldShowBanner(posted(onlyAlertOnce = true, alreadyAlerted = false), own, defaults))
        assertTrue(shouldShowBanner(posted(onlyAlertOnce = false, alreadyAlerted = true), own, defaults))
    }

    @Test
    fun `do not disturb is respected unless turned off`() {
        assertFalse(shouldShowBanner(posted(passesDnd = false), own, defaults))
        assertTrue(shouldShowBanner(posted(passesDnd = false), own, defaults.copy(respectDnd = false)))
    }

    @Test
    fun `master switch and per-app mute`() {
        assertFalse(shouldShowBanner(posted(), own, defaults.copy(banners = false)))
        assertFalse(shouldShowBanner(posted(pkg = "chat.app"), own, defaults.copy(mutedPackages = setOf("chat.app"))))
        assertTrue(shouldShowBanner(posted(pkg = "mail.app"), own, defaults.copy(mutedPackages = setOf("chat.app"))))
    }
}
