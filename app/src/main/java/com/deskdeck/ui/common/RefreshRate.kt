package com.deskdeck.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Asks the display for 60 Hz while DeskDeck's window is showing, unless [smooth]. On a 90/120 Hz
 * tablet that's a third (or half) fewer frames to draw while music plays (lyrics, seek bar), and
 * the panel itself refreshes less often; 60 Hz still looks smooth. The system may ignore it (e.g.
 * a fixed-rate panel), and other apps keep their own rate.
 */
@Composable
fun PreferredRefreshRate(smooth: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, smooth) {
        val window = view.context.findActivity()?.window
        fun set(hz: Float) {
            window?.let { w -> w.attributes = w.attributes.apply { preferredRefreshRate = hz } }
        }
        // 0 = no preference: the system's own (highest) rate.
        set(if (smooth) 0f else BATTERY_HZ)
        onDispose { set(0f) }
    }
}

private const val BATTERY_HZ = 60f

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
