package com.deskdeck.media

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaRoute2Info
import android.media.MediaRouter2
import android.media.RouteDiscoveryPreference
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AudioOutputDevice(val id: String, val name: String, val selected: Boolean, val available: Boolean)
data class AudioOutputState(
    val devices: List<AudioOutputDevice> = emptyList(),
    val source: String? = null,
    val pending: String? = null,
    val issue: Issue? = null,
) {
    enum class Issue { ACCESS_REQUIRED, UNSUPPORTED, TRANSFER_FAILED }
}

@SuppressLint("NewApi")
@HiltViewModel
class AudioOutputViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessions: MediaSessionRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AudioOutputState())
    val state = mutableState.asStateFlow()
    private var router: MediaRouter2? = null
    private var watch: Job? = null
    private var timeout: Job? = null
    private var clients = 0

    private val routes = object : MediaRouter2.RouteCallback() {
        override fun onRoutesUpdated(routes: List<MediaRoute2Info>) = refresh()
    }
    private val controllers = object : MediaRouter2.ControllerCallback() {
        override fun onControllerUpdated(controller: MediaRouter2.RoutingController) = refresh()
    }
    private val transfers = object : MediaRouter2.TransferCallback() {
        override fun onTransfer(oldController: MediaRouter2.RoutingController, newController: MediaRouter2.RoutingController) = refresh()
        override fun onTransferFailure(requestedRoute: MediaRoute2Info) = failTransfer()
    }

    fun start() {
        clients++
        if (clients != 1) return
        if (Build.VERSION.SDK_INT < 35) {
            mutableState.value = AudioOutputState(issue = AudioOutputState.Issue.UNSUPPORTED)
            return
        }
        watch = viewModelScope.launch {
            sessions.shownController.collect { controller ->
                disconnect()
                mutableState.value = AudioOutputState(source = sessions.nowPlaying.value?.sourceLabel)
                try {
                    router = MediaRouter2.getInstance(context, controller?.packageName ?: "android", context.mainExecutor) {
                        disconnect()
                        mutableState.value = mutableState.value.copy(devices = emptyList(), pending = null, issue = AudioOutputState.Issue.ACCESS_REQUIRED)
                    }
                    router?.registerRouteCallback(context.mainExecutor, routes, RouteDiscoveryPreference.Builder(emptyList(), false).build())
                    router?.registerControllerCallback(context.mainExecutor, controllers)
                    router?.registerTransferCallback(context.mainExecutor, transfers)
                    refresh()
                } catch (_: SecurityException) {
                    disconnect()
                    mutableState.value = mutableState.value.copy(issue = AudioOutputState.Issue.ACCESS_REQUIRED)
                } catch (_: IllegalArgumentException) {
                    disconnect()
                    mutableState.value = mutableState.value.copy(issue = AudioOutputState.Issue.TRANSFER_FAILED)
                }
            }
        }
    }

    fun stop() {
        clients = (clients - 1).coerceAtLeast(0)
        if (clients == 0) {
            watch?.cancel()
            watch = null
            disconnect()
        }
    }

    private fun refresh() {
        val current = router ?: return
        val active = current.controllers.lastOrNull() ?: current.systemController
        val selected = active.selectedRoutes.map { it.id }.toSet()
        val transferable = active.transferableRoutes.map { it.id }.toSet()
        val devices = (active.selectedRoutes + current.routes).distinctBy { it.id }.map { route ->
            AudioOutputDevice(route.id, route.name.toString(), route.id in selected, route.id in selected || route.id in transferable)
        }.sortedWith(compareBy({ it.name.lowercase(Locale.ROOT) }, { it.id }))
        val pending = mutableState.value.pending?.takeUnless { it in selected }
        if (pending == null) timeout?.cancel()
        mutableState.value = mutableState.value.copy(devices = devices, pending = pending)
    }

    fun select(id: String) {
        val current = router ?: return
        val device = mutableState.value.devices.firstOrNull { it.id == id } ?: return
        if (!device.available || device.selected || mutableState.value.pending != null) return
        val route = current.routes.firstOrNull { it.id == id } ?: return
        mutableState.value = mutableState.value.copy(pending = id, issue = null)
        timeout?.cancel()
        timeout = viewModelScope.launch {
            delay(12_000)
            refresh()
            if (mutableState.value.pending != null) failTransfer()
        }
        try {
            current.transferTo(route)
        } catch (_: SecurityException) {
            failTransfer()
        } catch (_: IllegalArgumentException) {
            failTransfer()
        }
    }

    private fun failTransfer() {
        timeout?.cancel()
        mutableState.value = mutableState.value.copy(pending = null, issue = AudioOutputState.Issue.TRANSFER_FAILED)
    }

    private fun disconnect() {
        timeout?.cancel()
        router?.let {
            it.unregisterRouteCallback(routes)
            it.unregisterControllerCallback(controllers)
            it.unregisterTransferCallback(transfers)
        }
        router = null
    }

    override fun onCleared() {
        disconnect()
    }
}
