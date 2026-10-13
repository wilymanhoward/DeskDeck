package com.deskdeck.ui.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import kotlinx.coroutines.delay

/**
 * The current local time, re-emitted exactly on each second (or minute) boundary rather than on
 * a fixed interval, so the clock never lags behind the system clock.
 */
@Composable
fun rememberCurrentTime(withSeconds: Boolean): State<LocalDateTime> =
    produceState(LocalDateTime.now(), withSeconds) {
        while (true) {
            val now = LocalDateTime.now()
            value = now
            val millisIntoSecond = now.get(ChronoField.MILLI_OF_SECOND).toLong()
            val wait = if (withSeconds) {
                1000 - millisIntoSecond
            } else {
                60_000 - (now.second * 1000L + millisIntoSecond)
            }
            delay(wait.coerceAtLeast(1))
        }
    }

/** Follows the system 12/24-hour setting. A user override arrives with settings in M5. */
@Composable
fun rememberIs24Hour(): Boolean {
    val context = LocalContext.current
    return remember(context) { DateFormat.is24HourFormat(context) }
}

fun clockFormatter(is24Hour: Boolean, withSeconds: Boolean): DateTimeFormatter {
    val pattern = when {
        is24Hour && withSeconds -> "HH:mm:ss"
        is24Hour -> "HH:mm"
        withSeconds -> "h:mm:ss"
        else -> "h:mm"
    }
    return DateTimeFormatter.ofPattern(pattern)
}
