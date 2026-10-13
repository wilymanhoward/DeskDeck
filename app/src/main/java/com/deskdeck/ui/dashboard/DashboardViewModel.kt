package com.deskdeck.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deskdeck.data.NetworkMonitor
import com.deskdeck.data.lyrics.LyricsRepository
import com.deskdeck.domain.MediaKind
import com.deskdeck.domain.NowPlaying
import com.deskdeck.domain.RailApp
import com.deskdeck.domain.Track
import com.deskdeck.launcher.AppLauncher
import com.deskdeck.launcher.RailAppsRepository
import com.deskdeck.media.MediaRepository
import com.deskdeck.media.NotificationAccess
import com.deskdeck.media.NotificationCounter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val media: MediaRepository,
    private val lyricsRepository: LyricsRepository,
    railAppsRepository: RailAppsRepository,
    notificationCounter: NotificationCounter,
    notificationAccess: NotificationAccess,
    private val appLauncher: AppLauncher,
    networkMonitor: NetworkMonitor,
) : ViewModel(), DashboardActions {

    val nowPlaying: StateFlow<NowPlaying?> = media.nowPlaying

    val railApps: StateFlow<List<RailApp>> = railAppsRepository.railApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Hidden (0) whenever notification access is off, even if a stale count lingers. */
    val notificationCount: StateFlow<Int> = combine(notificationCounter.count, notificationAccess.granted) { count, granted ->
        if (granted) count else 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val retryRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val lyricsTrack: StateFlow<Track?> = media.nowPlaying
        .map { it?.track }
        .distinctUntilChanged { old, new -> old?.id == new?.id && old?.kind == new?.kind }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val lyrics: StateFlow<LyricsUiState> = lyricsTrack
        .flatMapLatest { track ->
            val offset = if (track == null) flowOf(0L) else lyricsRepository.offsetFor(track)
            combine(lyricsFor(track), offset) { state, offsetMs ->
                if (state is LyricsUiState.Synced) state.copy(offsetMs = offsetMs) else state
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LyricsUiState.Idle)

    init {
        // A desk tablet loses Wi-Fi now and then: retry a failed lookup as soon as it's back.
        viewModelScope.launch {
            networkMonitor.isOnline.drop(1).filter { it }.collect {
                if (lyrics.value is LyricsUiState.Error) onRetryLyrics()
            }
        }
    }

    private fun lyricsFor(track: Track?): Flow<LyricsUiState> = when {
        track == null -> flowOf(LyricsUiState.Idle)
        track.kind != MediaKind.MUSIC -> flowOf(LyricsUiState.Unavailable(track.kind))
        else -> retryRequests
            .map { true }
            .onStart { emit(false) }
            .transformLatest { forceRefresh ->
                emit(LyricsUiState.Loading)
                // Skipping through tracks quickly cancels this before any request goes out.
                if (!forceRefresh) delay(LOOKUP_DEBOUNCE_MS)
                emit(lyricsRepository.lyricsFor(track, forceRefresh).toUiState(track.id))
            }
    }

    override fun onPlayPause() = media.playPause()
    override fun onSkipNext() = media.skipNext()
    override fun onSkipPrevious() = media.skipPrevious()
    override fun onSeek(positionMs: Long) = media.seekTo(positionMs)
    override fun onToggleShuffle() = media.toggleShuffle()
    override fun onCycleRepeat() = media.cycleRepeat()

    override fun onRailAppClick(app: RailApp) {
        if (app.installed) appLauncher.launchOrOpenStore(app.packageName) else appLauncher.openStorePage(app.packageName)
    }

    override fun onRetryLyrics() {
        retryRequests.tryEmit(Unit)
    }

    override fun onAdjustLyricsOffset(deltaMs: Long) {
        val track = lyricsTrack.value ?: return
        val current = (lyrics.value as? LyricsUiState.Synced)?.offsetMs ?: 0L
        viewModelScope.launch {
            lyricsRepository.setOffset(track, (current + deltaMs).coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS))
        }
    }

    override fun onResetLyricsOffset() {
        val track = lyricsTrack.value ?: return
        viewModelScope.launch { lyricsRepository.setOffset(track, 0) }
    }

    private companion object {
        const val MAX_OFFSET_MS = 10_000L
        const val LOOKUP_DEBOUNCE_MS = 300L
    }
}
