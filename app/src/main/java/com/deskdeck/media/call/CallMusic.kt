package com.deskdeck.media.call

import com.deskdeck.data.settings.SettingsRepository
import com.deskdeck.di.ApplicationScope
import com.deskdeck.media.MediaRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A call connected: [previous] wasn't an ongoing call, [next] is. */
internal fun callStarted(previous: CallState, next: CallState) = previous !is CallState.Ongoing && next is CallState.Ongoing

/** The call is over (ended, or gone without an end state). */
internal fun callFinished(previous: CallState, next: CallState) = previous is CallState.Ongoing && next !is CallState.Ongoing

/**
 * Music around calls. The call app usually takes audio focus and players pause themselves; if
 * something is still playing [SETTLE_MS] after the call connects, DeskDeck pauses it and
 * remembers that it did. After the call it plays exactly those again ("Resume music after
 * calls"), and only if they're still paused. Music that paused itself is left to its app.
 */
@Singleton
class CallMusic @Inject constructor(
    private val calls: CallMonitor,
    private val media: MediaRepository,
    private val settings: SettingsRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private var pausedByUs: Set<String> = emptySet()
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            var previous: CallState = CallState.Idle
            calls.state.collect { next ->
                val prev = previous
                previous = next
                when {
                    callStarted(prev, next) -> onCallStarted(next.callOrNull?.packageName)
                    callFinished(prev, next) -> onCallFinished()
                }
            }
        }
    }

    private fun onCallStarted(callPackage: String?) = scope.launch {
        val s = settings.settings.first().calls
        if (!s.enabled || !s.pauseMusic) return@launch
        delay(SETTLE_MS)
        // Still in the call? Then pause whatever is still playing.
        if (calls.state.value !is CallState.Ongoing) return@launch
        pausedByUs = pausedByUs + media.pausePlaying(except = setOfNotNull(callPackage))
    }

    private fun onCallFinished() = scope.launch {
        val toResume = pausedByUs
        pausedByUs = emptySet()
        if (toResume.isEmpty()) return@launch
        val s = settings.settings.first().calls
        if (!s.enabled || !s.resumeMusic) return@launch
        // Let the call app give the audio back first.
        delay(RESUME_DELAY_MS)
        if (calls.state.value is CallState.Ongoing || calls.state.value is CallState.Incoming) return@launch
        media.resume(toResume)
    }

    private companion object {
        const val SETTLE_MS = 1_500L
        const val RESUME_DELAY_MS = 1_200L
    }
}
