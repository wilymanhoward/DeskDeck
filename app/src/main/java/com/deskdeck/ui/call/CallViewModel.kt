package com.deskdeck.ui.call

import android.content.Context
import androidx.lifecycle.ViewModel
import com.deskdeck.media.call.Call
import com.deskdeck.media.call.CallLauncher
import com.deskdeck.media.call.CallMonitor
import com.deskdeck.media.call.CallState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class CallViewModel @Inject constructor(
    monitor: CallMonitor,
    private val launcher: CallLauncher,
) : ViewModel() {
    val state: StateFlow<CallState> = monitor.state

    /** The call whose banner was closed (×, or a deliberate swipe); the call itself goes on. */
    private val _closedKey = MutableStateFlow<String?>(null)
    val closedKey: StateFlow<String?> = _closedKey.asStateFlow()

    /** The call whose quiet indicator (Do Not Disturb) was tapped open into the full banner. */
    private val _expandedKey = MutableStateFlow<String?>(null)
    val expandedKey: StateFlow<String?> = _expandedKey.asStateFlow()

    fun close(call: Call) { _closedKey.value = call.key }
    fun expand(call: Call) { _expandedKey.value = call.key }

    fun answer(context: Context, call: Call) {
        launcher.send(context, call.actions.answer, "answer")
    }

    fun decline(context: Context, call: Call) {
        launcher.send(context, call.actions.decline, "decline")
    }

    fun hangUp(context: Context, call: Call) {
        launcher.send(context, call.actions.hangUp, "hangUp")
    }

    /**
     * "Show call" / "Open WhatsApp": the app's own call screen (full screen; a video call's
     * floating window grows back to full size), else its main screen.
     */
    fun showCall(context: Context, call: Call) {
        if (!launcher.send(context, call.actions.open, "open")) launcher.openApp(context, call.packageName)
    }
}
