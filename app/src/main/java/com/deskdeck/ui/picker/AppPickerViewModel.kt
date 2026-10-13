package com.deskdeck.ui.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deskdeck.domain.RailApp
import com.deskdeck.launcher.InstalledAppsRepository
import com.deskdeck.launcher.LaunchableApp
import com.deskdeck.launcher.RailAppsRepository
import com.deskdeck.launcher.matching
import com.deskdeck.media.sources.SourceSwitcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppPickerViewModel @Inject constructor(
    installedApps: InstalledAppsRepository,
    private val railApps: RailAppsRepository,
    private val sourceSwitcher: SourceSwitcher,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val apps: StateFlow<List<LaunchableApp>> = combine(installedApps.apps, _query) { apps, q -> apps.matching(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pinned: StateFlow<List<RailApp>> = railApps.railApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pinnedPackages: StateFlow<Set<String>> = railApps.pinnedPackages
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun onQueryChange(query: String) {
        _query.value = query
    }

    /** Shows the app's media in DeskDeck. Never launches it. */
    fun select(packageName: String) = sourceSwitcher.select(packageName)

    fun togglePin(packageName: String) {
        viewModelScope.launch { railApps.toggle(packageName) }
    }

    fun unpin(packageName: String) {
        viewModelScope.launch { railApps.unpin(packageName) }
    }

    fun move(packageName: String, delta: Int) {
        viewModelScope.launch { railApps.move(packageName, delta) }
    }
}
