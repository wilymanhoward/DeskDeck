package com.deskdeck

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.deskdeck.media.call.Call
import com.deskdeck.media.call.CallActions
import com.deskdeck.media.call.CallDetection
import com.deskdeck.media.call.CallMonitor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import androidx.compose.runtime.Composable
import com.deskdeck.debug.DebugCallSource
import com.deskdeck.media.call.CallSource
import android.util.Log

/**
 * Debug builds only (src/debug; release has an empty stand-in). Developer hooks driven over adb:
 *
 *   adb shell am start -n com.deskdeck/.MainActivity -a com.deskdeck.debug.SPLIT_TEST --es mode <mode> --es pkg com.whatsapp
 *
 * Modes: adjacent (launch intent + FLAG_ACTIVITY_LAUNCH_ADJACENT), plain (launch intent, no flag),
 * pi_immutable / pi_mutable (a PendingIntent sent with the adjacent flag via startIntentSender).
 *
 *   adb shell am start -n com.deskdeck/.MainActivity -a com.deskdeck.debug.FAKE_CALL --es state incoming|ongoing|end
 *       [--es caller Aisyah] [--ez video true] [--ez dnd true] [--ez buttons false]
 *
 * Feeds a made-up WhatsApp call straight into [CallMonitor] (no notification) to look at the call UI.
 */
object DebugTools {
    private const val TAG = "DeskDeckDebug"
    private const val ACTION_SPLIT_TEST = "com.deskdeck.debug.SPLIT_TEST"
    private const val ACTION_FAKE_CALL = "com.deskdeck.debug.FAKE_CALL"
    private const val FAKE_KEY = "debug|fake-call"

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DebugEntryPoint {
        fun callMonitor(): CallMonitor
    }

    /** The simulator's notifications, read with WhatsApp's rules. */
    fun extraCallSources(): List<CallSource> = listOf(DebugCallSource())

    /** Settings › Calls: the "Simulate call" buttons. */
    @Composable
    fun CallSimulatorCard() = com.deskdeck.debug.CallSimulatorCard()

    fun handle(activity: Activity, intent: Intent?) {
        if (intent?.action == ACTION_FAKE_CALL) {
            intent.action = null
            fakeCall(activity, intent)
            return
        }
        if (intent?.action != ACTION_SPLIT_TEST) return
        val mode = intent.getStringExtra("mode") ?: "adjacent"
        val pkg = intent.getStringExtra("pkg") ?: "com.whatsapp"
        intent.action = null
        // Let DeskDeck come to the front first, as it would be when the user taps a button.
        Handler(Looper.getMainLooper()).postDelayed({ splitTest(activity, mode, pkg) }, 1_000)
    }

    private fun fakeCall(activity: Activity, intent: Intent) {
        val monitor = EntryPointAccessors.fromApplication(activity.applicationContext, DebugEntryPoint::class.java).callMonitor()
        val state = intent.getStringExtra("state") ?: "incoming"
        if (state == "end") return monitor.onRemoved(FAKE_KEY)
        // Harmless stand-ins for the app's buttons: a broadcast nobody listens to.
        fun noop(code: Int) = PendingIntent.getBroadcast(activity, code, Intent("com.deskdeck.debug.NOOP").setPackage(activity.packageName), PendingIntent.FLAG_IMMUTABLE)
        val buttons = intent.getBooleanExtra("buttons", true)
        val incoming = state == "incoming"
        val call = Call(
            key = FAKE_KEY,
            packageName = "com.whatsapp",
            appLabel = "WhatsApp",
            caller = intent.getStringExtra("caller") ?: "Aisyah",
            isVideo = intent.getBooleanExtra("video", true),
            ringsThroughDnd = !intent.getBooleanExtra("dnd", false),
            actions = CallActions(
                answer = noop(1).takeIf { buttons && incoming },
                decline = noop(2).takeIf { buttons && incoming },
                hangUp = noop(3).takeIf { buttons && !incoming },
            ),
        )
        val detection = CallDetection(
            kind = if (incoming) CallDetection.Kind.INCOMING else CallDetection.Kind.ONGOING,
            caller = call.caller,
            isVideo = call.isVideo,
            startedAtMs = null,
            answer = null,
            decline = null,
            hangUp = null,
            usedText = false,
        )
        monitor.onCall(call, detection)
        Log.i(TAG, "fake call $state")
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
