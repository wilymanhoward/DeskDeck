package com.deskdeck

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.deskdeck.media.NotificationAccess
import com.deskdeck.media.NotificationBanners
import com.deskdeck.media.VolumeController
import com.deskdeck.ui.AppRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var notificationAccess: NotificationAccess
    @Inject lateinit var volume: VolumeController
    @Inject lateinit var banners: NotificationBanners

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        setContent {
            // The theme lives in AppRoot because the accent color is a user setting.
            AppRoot()
        }
    }

    override fun onStart() {
        super.onStart()
        volume.start()
        banners.setVisible(true)
    }

    override fun onStop() {
        volume.stop()
        banners.setVisible(false)
        super.onStop()
    }

    // Volume keys go to DeskDeck's own HUD; handling them here keeps the system panel from showing.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        (keyCode in VOLUME_KEYS && volume.onKey(keyCode, repeat = event.repeatCount > 0)) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        (keyCode in VOLUME_KEYS && !volume.state.value.fixed) || super.onKeyUp(keyCode, event)

    override fun onResume() {
        super.onResume()
        // The user may have just toggled notification access in Settings; there's no callback for it.
        notificationAccess.refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Dialogs, the notification shade and split-screen can bring the bars back; hide them again.
        if (hasFocus) hideSystemBars()
    }

    /** Immersive mode: bars stay hidden and only reappear transiently on an edge swipe. */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private val VOLUME_KEYS = setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE)
