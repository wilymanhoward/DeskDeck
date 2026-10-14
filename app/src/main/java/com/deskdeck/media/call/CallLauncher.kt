package com.deskdeck.media.call

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.deskdeck.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything DeskDeck does to a call, only ever on the user's tap: it sends the call app's own
 * buttons (PendingIntents) and never fakes or automates taps.
 *
 * Split screen (tested on a Tab S9 FE, One UI 8.5): the app's launch intent with
 * FLAG_ACTIVITY_LAUNCH_ADJACENT opens it beside DeskDeck, from full screen and from split screen.
 * WhatsApp's own notification intents are immutable, so they can't be sent "beside": they'd open
 * full screen, or in DeskDeck's own half when split. In a pop-up window the flag opens another
 * pop-up on top; DeskDeck shows a hint there instead.
 */
@Singleton
class CallLauncher @Inject constructor() {

    enum class BesideResult { OPENED, HINT }

    /** Whether "beside DeskDeck" can work from this window (not from a pop-up window). */
    fun canOpenBeside(activity: Activity, packageName: String): Boolean =
        !isPopUpWindow(activity) && activity.packageManager.getLaunchIntentForPackage(packageName) != null

    /** Opens the call app beside DeskDeck, or says to show the drag-from-taskbar hint. */
    fun openBeside(activity: Activity, packageName: String): BesideResult {
        if (isPopUpWindow(activity)) return BesideResult.HINT
        val launch = activity.packageManager.getLaunchIntentForPackage(packageName) ?: return BesideResult.HINT
        return runCatching {
            activity.startActivity(Intent(launch).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT))
            BesideResult.OPENED
        }.getOrElse {
            Log.w(TAG, "open beside failed", it)
            BesideResult.HINT
        }
    }

    /** Sends one of the call app's own buttons. False if it's gone (the call ended meanwhile). */
    fun send(activity: Activity, intent: PendingIntent?, what: String): Boolean {
        intent ?: return false
        if (BuildConfig.DEBUG) logIntent(what, intent)
        return try {
            intent.send(activity, 0, null, null, null, null, backgroundStartOptions())
            true
        } catch (e: PendingIntent.CanceledException) {
            Log.i(TAG, "$what: intent cancelled", e)
            false
        }
    }

    /**
     * Since Android 14 a PendingIntent's sender must allow it to open the app's screen; DeskDeck
     * is on screen when the user taps, so it may.
     */
    private fun backgroundStartOptions(): Bundle? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA -> ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
            .toBundle()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ActivityOptions.makeBasic()
            .apply {
                @Suppress("DEPRECATION")
                setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }
            .toBundle()
        else -> null
    }

    /** Debug builds: what kind of intent each button is, to learn how the app behaves on a real call. */
    private fun logIntent(what: String, intent: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.i(TAG, "$what: activity=${intent.isActivity} broadcast=${intent.isBroadcast} service=${intent.isService || intent.isForegroundService} immutable=${intent.isImmutable}")
        }
    }

    companion object {
        private const val TAG = "DeskDeckCall"

        /**
         * A pop-up (freeform) window is smaller than the screen in both directions; a split-screen
         * half spans the full height (side by side) or the full width (stacked).
         */
        fun isPopUpWindow(activity: Activity): Boolean {
            if (!activity.isInMultiWindowMode) return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
            val wm = activity.windowManager
            val window = wm.currentWindowMetrics.bounds
            val screen = wm.maximumWindowMetrics.bounds
            return window.width() < screen.width() && window.height() < screen.height()
        }
    }
}
