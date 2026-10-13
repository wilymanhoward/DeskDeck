package com.deskdeck.media.sources

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.view.KeyEvent
import com.deskdeck.data.settings.SettingsRepository
import com.deskdeck.media.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Step 1: the app has a session but nothing loaded: ask it to load its last queue without
 * playing (`prepare`), so it shows up ready, as when opened by hand.
 */
class SessionKeySource @Inject constructor(
    private val media: MediaRepository,
) : PlaybackSource {
    override val name = "session-key"
    override val sessionTimeoutMs = BACKGROUND_WAIT_MS

    override suspend fun requestPlay(packageName: String): Boolean = media.requestPrepare(packageName)
}

/**
 * Step 2: an explicit ACTION_MEDIA_BUTTON broadcast to the app's own media button receiver, which
 * is how a headset "play" reaches an app that isn't running. On Android 12+ the app may not be
 * allowed to start playback from the background, in which case nothing happens (best effort).
 */
class MediaButtonSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PlaybackSource {
    override val name = "media-button"
    override val sessionTimeoutMs = BACKGROUND_WAIT_MS

    override suspend fun canHandle(packageName: String) = receiver(packageName) != null

    override suspend fun requestPlay(packageName: String): Boolean {
        val component = receiver(packageName) ?: return false
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            context.sendBroadcast(
                Intent(Intent.ACTION_MEDIA_BUTTON)
                    .setComponent(component)
                    .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY)),
            )
        }
        return true
    }

    private fun receiver(packageName: String): ComponentName? =
        context.packageManager
            .queryBroadcastReceivers(Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(packageName), 0)
            .firstOrNull()
            ?.activityInfo
            ?.let { ComponentName(it.packageName, it.name) }
}

/**
 * Step 3 (opt-in in Settings): connect to the app's MediaBrowserService and press play there. Most
 * big music apps only accept connections from approved callers (Android Auto, Wear …), so this
 * usually fails quickly; it's offered for players that are open.
 */
@Suppress("DEPRECATION") // androidx.media compat: see SessionEntry for why it's still used.
class MediaBrowserSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) : PlaybackSource {
    override val name = "media-browser"
    override val sessionTimeoutMs = BACKGROUND_WAIT_MS

    override suspend fun canHandle(packageName: String): Boolean =
        settings.settings.first().mediaBrowserResume && service(packageName) != null

    override suspend fun requestPlay(packageName: String): Boolean = withContext(Dispatchers.Main) {
        val component = service(packageName) ?: return@withContext false
        var browser: MediaBrowserCompat? = null
        val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val callback = object : MediaBrowserCompat.ConnectionCallback() {
                    override fun onConnected() {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onConnectionFailed() {
                        if (cont.isActive) cont.resume(false)
                    }

                    override fun onConnectionSuspended() {
                        if (cont.isActive) cont.resume(false)
                    }
                }
                browser = MediaBrowserCompat(context, component, callback, null).also { it.connect() }
                cont.invokeOnCancellation { browser?.disconnect() }
            }
        } ?: false
        val b = browser
        if (connected && b != null) {
            MediaControllerCompat(context, b.sessionToken).transportControls.play()
            // Give the command a moment to land before letting go of the connection.
            delay(1_000)
        }
        b?.disconnect()
        connected
    }

    private fun service(packageName: String): ComponentName? =
        context.packageManager
            .queryIntentServices(Intent(SERVICE_ACTION).setPackage(packageName), 0)
            .firstOrNull()
            ?.serviceInfo
            ?.let { ComponentName(it.packageName, it.name) }

    private companion object {
        const val SERVICE_ACTION = "android.media.browse.MediaBrowserService"
        const val CONNECT_TIMEOUT_MS = 2_000L
    }
}

/** Steps 1–3 work in the background or not at all; no point waiting long before the next. */
private const val BACKGROUND_WAIT_MS = 1_500L
