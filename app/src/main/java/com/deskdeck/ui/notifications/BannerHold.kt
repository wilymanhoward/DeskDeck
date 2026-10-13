package com.deskdeck.ui.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Holds new banners back while the user is mid-gesture on a control (seeking, setting volume). */
class BannerHold {
    private var holders by mutableIntStateOf(0)
    val held: Boolean get() = holders > 0

    fun acquire() {
        holders++
    }

    fun release() {
        holders = (holders - 1).coerceAtLeast(0)
    }
}

val LocalBannerHold = staticCompositionLocalOf { BannerHold() }

/** Holds banners for as long as [active] is true. */
@Composable
fun HoldBannersWhile(active: Boolean) {
    val hold = LocalBannerHold.current
    DisposableEffect(active, hold) {
        if (active) hold.acquire()
        onDispose { if (active) hold.release() }
    }
}
