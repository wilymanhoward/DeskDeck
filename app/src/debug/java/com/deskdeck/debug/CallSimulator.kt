package com.deskdeck.debug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.deskdeck.BuildConfig
import com.deskdeck.R
import com.deskdeck.media.call.CallDetection
import com.deskdeck.media.call.CallNotification
import com.deskdeck.media.call.CallSource
import com.deskdeck.media.call.WhatsAppCallSource

/**
 * Debug builds only. Posts made-up call notifications as DeskDeck, shaped like WhatsApp's, so
 * the call UI can be tried without a call. They go through the real path: notification listener
 * → [DebugCallSource] → WhatsApp's detection rules → CallMonitor. The simulated Answer, Decline
 * and Hang up buttons work (they update or remove the notification).
 */
object CallSimulator {
    const val CHANNEL = "debug_call_simulator"
    private const val ID = 4242

    enum class Mode {
        /** Android's call extras (call type, video flag, answer/decline/hang-up intents). */
        STRUCTURED,
        /** A call notification with no buttons at all: DeskDeck can only offer "Open". */
        NO_BUTTONS,
        /** No call extras, only Malay wording and labelled buttons (the text fallback). */
        WORDING_MALAY,
    }

    private var caller = "Aisyah"
    private var video = true
    private var mode = Mode.STRUCTURED
    private var startedAt = 0L

    fun incoming(context: Context, video: Boolean, mode: Mode, caller: String = "Aisyah") {
        this.video = video
        this.mode = mode
        this.caller = caller
        post(context, incoming = true)
    }

    fun ongoing(context: Context) {
        startedAt = System.currentTimeMillis()
        post(context, incoming = false)
    }

    fun end(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }

    private fun post(context: Context, incoming: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java)
        // Low importance: no system heads-up or sound on top of DeskDeck's own banner.
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Simulated calls (debug)", NotificationManager.IMPORTANCE_LOW))
        val malay = mode == Mode.WORDING_MALAY
        val text = when {
            incoming && malay -> if (video) "Panggilan video masuk" else "Panggilan suara masuk"
            incoming -> if (video) "Incoming video call" else "Incoming voice call"
            malay -> if (video) "Panggilan video sedang berlangsung" else "Panggilan suara sedang berlangsung"
            else -> if (video) "Ongoing video call" else "Ongoing voice call"
        }
        val answer = action(context, SimCallReceiver.ANSWER, 1)
        val decline = action(context, SimCallReceiver.DECLINE, 2)
        val hangUp = action(context, SimCallReceiver.HANG_UP, 3)
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_handset)
            .setContentTitle(caller)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
        if (!incoming) builder.setUsesChronometer(true).setWhen(startedAt).setShowWhen(true)
        when (mode) {
            Mode.STRUCTURED -> {
                // What Notification.CallStyle puts in its extras (posting real CallStyle needs a
                // foreground service or a full-screen intent, which the simulator doesn't use).
                builder.addExtras(
                    Bundle().apply {
                        putInt("android.callType", if (incoming) 1 else 2)
                        putBoolean("android.callIsVideo", video)
                        if (incoming) {
                            putParcelable("android.answerIntent", answer)
                            putParcelable("android.declineIntent", decline)
                        } else {
                            putParcelable("android.hangUpIntent", hangUp)
                        }
                    },
                )
                if (incoming) {
                    builder.addAction(Notification.Action.Builder(null, "Decline", decline).build())
                    builder.addAction(Notification.Action.Builder(null, "Answer", answer).build())
                } else {
                    builder.addAction(Notification.Action.Builder(null, "Hang up", hangUp).build())
                }
            }
            Mode.NO_BUTTONS -> builder.addExtras(Bundle().apply { putInt("android.callType", if (incoming) 1 else 2) })
            Mode.WORDING_MALAY -> if (incoming) {
                builder.addAction(Notification.Action.Builder(null, "Tolak", decline).build())
                builder.addAction(Notification.Action.Builder(null, "Jawab", answer).build())
            } else {
                builder.addAction(Notification.Action.Builder(null, "Letak", hangUp).build())
            }
        }
        nm.notify(ID, builder.build())
    }

    private fun action(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        code,
        Intent(action).setClass(context, SimCallReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** The simulated call's buttons: Answer turns it into an ongoing call; Decline and Hang up end it. */
class SimCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ANSWER -> CallSimulator.ongoing(context)
            DECLINE, HANG_UP -> CallSimulator.end(context)
        }
    }

    companion object {
        const val ANSWER = "com.deskdeck.debug.SIM_ANSWER"
        const val DECLINE = "com.deskdeck.debug.SIM_DECLINE"
        const val HANG_UP = "com.deskdeck.debug.SIM_HANG_UP"
    }
}

/** Reads the simulator's notifications with WhatsApp's rules, as if they were WhatsApp's. */
class DebugCallSource : CallSource {
    private val whatsApp = WhatsAppCallSource()
    override val packages = setOf(BuildConfig.APPLICATION_ID)

    override fun detect(n: CallNotification): CallDetection? =
        if (n.channelId == CallSimulator.CHANNEL) whatsApp.detect(n.copy(packageName = WhatsAppCallSource.WHATSAPP)) else null
}
