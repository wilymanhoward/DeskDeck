package com.deskdeck.media.call

import com.deskdeck.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The current call, fed by the notification listener: its own path, separate from the banner
 * queue (call notifications are ongoing and would never show a banner there).
 */
@Singleton
class CallMonitor @Inject constructor(
    private val sources: List<@JvmSuppressWildcards CallSource>,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<CallState>(CallState.Idle)
    val state: StateFlow<CallState> = _state.asStateFlow()

    private var pendingRemoval: Job? = null
    private var settle: Job? = null

    /** Cheap first check, before a notification's extras are read. */
    fun handles(packageName: String): Boolean = sources.any { packageName in it.packages }

    /** The source's reading of [n], or null if it isn't a live call. */
    fun detect(n: CallNotification): CallDetection? =
        sources.firstOrNull { n.packageName in it.packages }?.detect(n)

    /** A call notification was posted or updated. */
    fun onCall(call: Call, detection: CallDetection) {
        // Answering can swap the ringing notification for the in-call one: not an ended call.
        pendingRemoval?.cancel()
        apply(CallEvent.Posted(call, detection))
    }

    fun onRemoved(key: String) {
        val shown = _state.value
        if (shown !is CallState.Incoming && shown !is CallState.Ongoing) return
        if (shown.callOrNull?.key != key) return
        pendingRemoval?.cancel()
        pendingRemoval = scope.launch {
            delay(REMOVAL_GRACE_MS)
            apply(CallEvent.Removed(key))
        }
    }

    /** Notification access went away: nothing about calls can be known any more. */
    fun reset() {
        pendingRemoval?.cancel()
        settle?.cancel()
        _state.value = CallState.Idle
    }

    private fun apply(event: CallEvent) {
        val next = reduceCall(_state.value, event, System.currentTimeMillis())
        _state.value = next
        if (next is CallState.Ended) {
            settle?.cancel()
            settle = scope.launch {
                delay(ENDED_SHOWN_MS)
                apply(CallEvent.Settled)
            }
        }
    }

    companion object {
        const val REMOVAL_GRACE_MS = 1_500L
        const val ENDED_SHOWN_MS = 3_000L
    }
}
