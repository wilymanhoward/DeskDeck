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
import com.deskdeck.debug.FakeCallActivity
import com.deskdeck.debug.FakeChatsActivity
import android.util.Log

/**
 * Debug builds only (src/debug; release has an empty stand-in). Developer hooks driven over adb:
 *
 *   adb shell am start -n com.deskdeck/.MainActivity -a com.deskdeck.debug.SPLIT_TEST --es mode <mode> --es pkg com.whatsapp
 *
 * Modes: adjacent (launch intent + FLAG_ACTIVITY_LAUNCH_ADJACENT), plain (launch intent, no flag),
 * pi_immutable / pi_mutable (a PendingIntent sent with the adjacent flag via startIntentSender),
 * voip_pip: start a fake call screen in its own task and shrink it to picture-in-picture
 * (run first), then voip_beside: "Open call beside DeskDeck" with pkg as the app and the fake
 * call screen's immutable intent as the call screen.
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
            val fakeCall = PendingIntent.getActivity(
                activity, 9, Intent(activity, FakeCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE,
            )
            when (mode) {
                // Reverse order: the call screen first (its own intent, full screen), then DeskDeck
                // itself beside it, which DeskDeck can do because the flag is on its own launch.
                "voip_swap" -> {
                    fakeCall.send(activity, 0, null, null, null, null, options)
                    Handler(Looper.getMainLooper()).postDelayed({
                        runCatching {
                            activity.applicationContext.startActivity(
                                Intent(activity.applicationContext, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT),
                                ActivityOptions.makeBasic().toBundle(),
                            )
                        }.onFailure { Log.w(TAG, "swap relaunch failed", it) }
                    }, 600)
                }
                "voip_chats" -> activity.startActivity(Intent(activity, FakeChatsActivity::class.java).addFlags(adjacent))
                "voip_finish" -> activity.startActivity(Intent(activity, FakeCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(FakeCallActivity.EXTRA_FINISH, true))
                "voip_background" -> activity.startActivity(Intent(activity, FakeCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(FakeCallActivity.EXTRA_NO_PIP, true))
                "voip_pip" -> activity.startActivity(Intent(activity, FakeCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(FakeCallActivity.EXTRA_PIP, true))
                "voip_beside" -> {
                    // "Beside", then the call screen's own intent from the app context (tested: it goes full screen).
                    activity.startActivity(Intent(launch).addFlags(adjacent))
                    run {
                        Handler(Looper.getMainLooper()).postDelayed({
                            fakeCall.send(activity.applicationContext, 0, null, null, null, null, options)
                        }, 700)
                    }
                }
                // The fake call screen's own (immutable) intent, sent with launch bounds for the right half.
                "voip_bounds", "voip_split_bounds" -> {
                    val send = {
                        val screen = activity.windowManager.maximumWindowMetrics.bounds
                        val right = android.graphics.Rect(screen.centerX(), screen.top, screen.right, screen.bottom)
                        val opts = ActivityOptions.makeBasic().setLaunchBounds(right).apply {
                            @Suppress("DEPRECATION")
                            setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                        }.toBundle()
                        fakeCall.send(activity.applicationContext, 0, null, null, null, null, opts)
                    }
                    if (mode == "voip_split_bounds") {
                        activity.startActivity(Intent(launch).addFlags(adjacent))
                        Handler(Looper.getMainLooper()).postDelayed({ send() }, 700)
                    } else {
                        send()
                    }
                }
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
