package com.deskdeck.ui.call

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deskdeck.media.call.Call
import com.deskdeck.media.call.CallLauncher
import com.deskdeck.media.call.CallMonitor
import com.deskdeck.media.call.CallState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    /** "Drag WhatsApp from the taskbar…": shown for a few seconds when "beside" can't work. */
    private val _splitHint = MutableStateFlow<String?>(null)
    val splitHint: StateFlow<String?> = _splitHint.asStateFlow()
    private var hintJob: Job? = null

    fun close(call: Call) { _closedKey.value = call.key }
    fun expand(call: Call) { _expandedKey.value = call.key }

    fun canOpenBeside(activity: Activity, call: Call) = launcher.canOpenBeside(activity, call.packageName)

    fun answer(activity: Activity, call: Call) {
        launcher.send(activity, call.actions.answer, "answer")
    }

    fun decline(activity: Activity, call: Call) {
        launcher.send(activity, call.actions.decline, "decline")
    }

    fun hangUp(activity: Activity, call: Call) {
        launcher.send(activity, call.actions.hangUp, "hangUp")
    }

    /** Opens the call app beside DeskDeck first, then answers with the app's own button. */
    fun answerBeside(activity: Activity, call: Call) {
        val opened = launcher.openBeside(activity, call.packageName)
        launcher.send(activity, call.actions.answer, "answer")
        if (opened == CallLauncher.BesideResult.HINT) showHint(call.appLabel)
    }

    fun openBeside(activity: Activity, call: Call) {
        if (launcher.openBeside(activity, call.packageName) == CallLauncher.BesideResult.HINT) showHint(call.appLabel)
    }

    /**
     * "Open WhatsApp" (when its call buttons can't be identified). In split screen it goes beside
     * DeskDeck (the app's own intent would replace DeskDeck's half); otherwise the app's call screen.
     */
    fun openApp(activity: Activity, call: Call) {
        if (activity.isInMultiWindowMode && !CallLauncher.isPopUpWindow(activity)) {
            openBeside(activity, call)
        } else if (!launcher.send(activity, call.actions.open, "open")) {
            openBeside(activity, call)
        }
    }

    private fun showHint(appLabel: String) {
        hintJob?.cancel()
        _splitHint.value = appLabel
        hintJob = viewModelScope.launch {
            delay(HINT_MS)
            _splitHint.value = null
        }
    }

    private companion object {
        const val HINT_MS = 6_000L
    }
}
