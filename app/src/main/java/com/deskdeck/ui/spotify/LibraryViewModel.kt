package com.deskdeck.ui.spotify

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deskdeck.data.spotify.LibraryItem
import com.deskdeck.data.spotify.SpotifyAccount
import com.deskdeck.data.spotify.SpotifyAuth
import com.deskdeck.data.spotify.SpotifyError
import com.deskdeck.data.spotify.SpotifyLibrary
import com.deskdeck.media.sources.SourceSwitcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Starting a playlist: which tile, and whether Spotify had to be woken first. */
data class StartingPlaylist(val itemId: String, val waking: Boolean)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: SpotifyLibrary,
    private val auth: SpotifyAuth,
    private val sourceSwitcher: SourceSwitcher,
) : ViewModel() {

    val account: StateFlow<SpotifyAccount> = auth.account
    val items: StateFlow<List<LibraryItem>> =
        library.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** The last problem to explain above the grid; cleared by the next successful action. */
    private val _error = MutableStateFlow<SpotifyError?>(null)
    val error: StateFlow<SpotifyError?> = _error.asStateFlow()

    private val _starting = MutableStateFlow<StartingPlaylist?>(null)
    val starting: StateFlow<StartingPlaylist?> = _starting.asStateFlow()

    private var playJob: Job? = null

    /** Shows the cached list at once and refreshes it in the background. */
    fun refresh() {
        if (_refreshing.value || auth.account.value !is SpotifyAccount.SignedIn) return
        viewModelScope.launch {
            _refreshing.value = true
            _error.value = library.refresh()
            _refreshing.value = false
        }
    }

    fun beginSignIn(): Uri = auth.beginSignIn()

    /** Starts [item] on this tablet, then [onStarted] (close the library, fly the cover). */
    fun play(item: LibraryItem, onStarted: () -> Unit) {
        playJob?.cancel()
        _error.value = null
        _starting.value = StartingPlaylist(item.id, waking = false)
        playJob = viewModelScope.launch {
            val error = library.play(item) { _starting.value = StartingPlaylist(item.id, waking = true) }
            _starting.value = null
            _error.value = error
            if (error == null) {
                // Show Spotify in the card; its session appears as playback starts.
                sourceSwitcher.select(SpotifyLibrary.SPOTIFY_PACKAGE)
                onStarted()
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
