package com.deskdeck.ui.brief

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deskdeck.data.brief.BriefRepository
import com.deskdeck.data.settings.NightSettings
import com.deskdeck.domain.brief.MorningBrief
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** "Show Morning Brief now" from Settings, to look at it any time. */
@Singleton
class BriefPreview @Inject constructor() {
    private val _requested = MutableStateFlow(false)
    val requested: StateFlow<Boolean> = _requested.asStateFlow()
    fun show() { _requested.value = true }
    fun hide() { _requested.value = false }
}

@HiltViewModel
class BriefViewModel @Inject constructor(
    private val repository: BriefRepository,
    private val preview: BriefPreview,
) : ViewModel() {
    val brief: StateFlow<MorningBrief?> = repository.brief.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val previewRequested: StateFlow<Boolean> = preview.requested
    val hasMarketKey: Boolean get() = repository.hasMarketKey

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /**
     * The prefetch (WorkManager) may have run late or not at all; refresh when the brief opens if
     * the cached copy is older than [STALE_MS].
     */
    fun refreshIfStale(settings: NightSettings) {
        val cached = brief.value
        val age = System.currentTimeMillis() - (cached?.fetchedAtMs ?: 0)
        // Also refresh when a part that should be there is missing (e.g. a key was just added, or a watchlist item).
        val missing = cached == null ||
            (settings.briefWeather && cached.weather == null) ||
            (settings.briefMarket && hasMarketKey && !cached.markets.map { it.name }.containsAll(settings.watchlist)) ||
            (settings.briefHeadlines && cached.headlines.isEmpty())
        if ((age < STALE_MS && !missing) || _refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            repository.refresh(settings)
            _refreshing.value = false
        }
    }

    fun endPreview() = preview.hide()

    private companion object {
        const val STALE_MS = 30 * 60_000L
    }
}
