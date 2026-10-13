package com.deskdeck.ui.volume

import androidx.lifecycle.ViewModel
import com.deskdeck.domain.VolumeEvent
import com.deskdeck.domain.VolumeState
import com.deskdeck.media.VolumeController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class VolumeViewModel @Inject constructor(private val volume: VolumeController) : ViewModel() {
    val state: StateFlow<VolumeState> = volume.state
    val events: SharedFlow<VolumeEvent> = volume.events

    fun setFraction(fraction: Float) = volume.setFraction(fraction)
    fun toggleMute() = volume.toggleMute()
}
