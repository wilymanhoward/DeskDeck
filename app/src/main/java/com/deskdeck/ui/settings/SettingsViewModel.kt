package com.deskdeck.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deskdeck.data.lyrics.LyricsRepository
import com.deskdeck.data.settings.AppSettings
import com.deskdeck.data.settings.BurnInSettings
import com.deskdeck.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val lyricsRepository: LyricsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun updateBurnIn(transform: (BurnInSettings) -> BurnInSettings) = update { it.copy(burnIn = transform(it.burnIn)) }

    fun clearLyricsCache(onDone: () -> Unit) {
        viewModelScope.launch {
            lyricsRepository.clearCache()
            onDone()
        }
    }

    fun resetLyricOffsets(onDone: () -> Unit) {
        viewModelScope.launch {
            lyricsRepository.resetAllOffsets()
            onDone()
        }
    }
}
