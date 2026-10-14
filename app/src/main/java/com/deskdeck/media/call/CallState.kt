package com.deskdeck.media.call

import android.app.PendingIntent
import android.graphics.Bitmap

/** A call as DeskDeck shows it. Lives only in memory while the call lasts; nothing is stored. */
data class Call(
    /** The notification key (one call = one notification while it rings and while it's on). */
    val key: String,
    val packageName: String,
    val appLabel: String,
    /** Null when the app didn't name the caller. */
    val caller: String?,
    val isVideo: Boolean,
    /** The system would ring for it (not muted by Do Not Disturb). */
    val ringsThroughDnd: Boolean = true,
    val avatar: Bitmap? = null,
    val actions: CallActions = CallActions(),
)

/** The app's own buttons for this call. DeskDeck only ever sends these; it never fakes taps. */
data class CallActions(
    val answer: PendingIntent? = null,
    val decline: PendingIntent? = null,
    val hangUp: PendingIntent? = null,
    /** The notification's own tap action: the app's call screen. */
    val open: PendingIntent? = null,
)

sealed interface CallState {
    data object Idle : CallState

    data class Incoming(val call: Call) : CallState

    /** [startedAtMs]: when the call connected (wall clock), or null while still calling out. */
    data class Ongoing(val call: Call, val startedAtMs: Long?) : CallState

    /** Shown briefly after a call; [answered] false means missed or declined. */
    data class Ended(val call: Call, val answered: Boolean, val endedAtMs: Long) : CallState

    val callOrNull: Call?
        get() = when (this) {
            Idle -> null
            is Incoming -> call
            is Ongoing -> call
            is Ended -> call
        }
}

/** What happened to a call notification. */
sealed interface CallEvent {
    data class Posted(val call: Call, val detection: CallDetection) : CallEvent
    data class Removed(val key: String) : CallEvent
    /** [CallMonitor] clears [CallState.Ended] after a short while. */
    data object Settled : CallEvent
}

/**
 * The call state machine, pure so it can be tested. One call at a time: a new call notification
 * replaces whatever was shown; removing the shown call's notification ends it.
 */
fun reduceCall(state: CallState, event: CallEvent, nowMs: Long): CallState = when (event) {
    is CallEvent.Posted -> {
        val call = event.call
        when (event.detection.kind) {
            CallDetection.Kind.INCOMING -> CallState.Incoming(call)
            CallDetection.Kind.ONGOING -> {
                val previous = state as? CallState.Ongoing
                val sameCall = previous?.call?.key == call.key
                // The app's own timer if it shows one; else when we first saw it connected. An
                // update of the same call keeps its start, and an answered incoming call starts now.
                val started = event.detection.startedAtMs
                    ?: previous?.startedAtMs?.takeIf { sameCall }
                    ?: nowMs.takeUnless { event.detection.connecting }
                CallState.Ongoing(call, started)
            }
        }
    }
    is CallEvent.Removed -> when {
        state is CallState.Incoming && state.call.key == event.key -> CallState.Ended(state.call, answered = false, endedAtMs = nowMs)
        state is CallState.Ongoing && state.call.key == event.key -> CallState.Ended(state.call, answered = true, endedAtMs = nowMs)
        else -> state
    }
    CallEvent.Settled -> if (state is CallState.Ended) CallState.Idle else state
}
