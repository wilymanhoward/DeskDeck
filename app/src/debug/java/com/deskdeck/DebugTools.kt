package com.deskdeck

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Debug builds only (src/debug; release has an empty stand-in). Developer hooks driven over adb:
 *
 *   adb shell am start -n com.deskdeck/.MainActivity -a com.deskdeck.debug.SPLIT_TEST --es mode <mode> --es pkg com.whatsapp
 *
 * Modes: adjacent (launch intent + FLAG_ACTIVITY_LAUNCH_ADJACENT), plain (launch intent, no flag),
 * pi_immutable / pi_mutable (a PendingIntent sent with the adjacent flag via startIntentSender).
 */
object DebugTools {
    private const val TAG = "DeskDeckDebug"
    private const val ACTION_SPLIT_TEST = "com.deskdeck.debug.SPLIT_TEST"

    fun handle(activity: Activity, intent: Intent?) {
        if (intent?.action != ACTION_SPLIT_TEST) return
        val mode = intent.getStringExtra("mode") ?: "adjacent"
        val pkg = intent.getStringExtra("pkg") ?: "com.whatsapp"
        intent.action = null
        // Let DeskDeck come to the front first, as it would be when the user taps a button.
        Handler(Looper.getMainLooper()).postDelayed({ splitTest(activity, mode, pkg) }, 1_000)
    }

    private fun splitTest(activity: Activity, mode: String, pkg: String) {
        val launch = activity.packageManager.getLaunchIntentForPackage(pkg) ?: return Log.w(TAG, "no launch intent for $pkg").let { }
        val adjacent = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
        Log.i(TAG, "split test mode=$mode inMultiWindow=${activity.isInMultiWindowMode}")
        val options = ActivityOptions.makeBasic().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                @Suppress("DEPRECATION")
                setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }
        }.toBundle()
        runCatching {
            when (mode) {
                "adjacent" -> activity.startActivity(Intent(launch).addFlags(adjacent))
                "plain" -> activity.startActivity(Intent(launch).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "pi_immutable", "pi_mutable" -> {
                    val flags = if (mode == "pi_mutable") PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
                    val pi = PendingIntent.getActivity(activity, 7, Intent(launch).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags or PendingIntent.FLAG_UPDATE_CURRENT)
                    activity.startIntentSender(pi.intentSender, null, adjacent, adjacent, 0, options)
                }
            }
        }.onFailure { Log.w(TAG, "split test failed", it) }
        Handler(Looper.getMainLooper()).postDelayed({
            Log.i(TAG, "after mode=$mode inMultiWindow=${activity.isInMultiWindowMode}")
        }, 2_500)
    }
}
