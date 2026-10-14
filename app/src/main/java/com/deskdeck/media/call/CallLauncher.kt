package com.deskdeck.media.call

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
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
 * No "beside DeskDeck": WhatsApp's call screen isn't exported and is only reachable through its
 * own immutable intents, and on One UI those always bring it up full screen (from its floating
 * picture-in-picture window, the background, or new), whatever the order, launch bounds or which
 * half of a split it's started from (tested on a Tab S9 FE, One UI 8.5). Intents are sent from
 * the app context: sent from DeskDeck's window in split screen, they'd open in DeskDeck's half.
 */
@Singleton
class CallLauncher @Inject constructor() {

    /** Sends one of the call app's own buttons. False if it's gone (the call ended meanwhile). */
    fun send(context: Context, intent: PendingIntent?, what: String): Boolean {
        intent ?: return false
        if (BuildConfig.DEBUG) logIntent(what, intent)
        return try {
            intent.send(context.applicationContext, 0, null, null, null, null, backgroundStartOptions())
            true
        } catch (e: PendingIntent.CanceledException) {
            Log.i(TAG, "$what: intent cancelled", e)
            false
        }
    }

    /** "Show call" without the app's own call-screen intent: the app's main screen instead. */
    fun openApp(context: Context, packageName: String): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return runCatching { context.startActivity(Intent(launch).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
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

    private companion object {
        const val TAG = "DeskDeckCall"
    }
}
