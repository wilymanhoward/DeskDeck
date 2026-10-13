package com.deskdeck.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deskdeck.data.fake.FakeData
import com.deskdeck.data.lyrics.LyricsRepository
import com.deskdeck.domain.MediaKind
import com.deskdeck.domain.NowPlaying
import com.deskdeck.domain.RailApp
import com.deskdeck.domain.Track
import com.deskdeck.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
) : ViewModel(), DashboardActions {

    val nowPlaying: StateFlow<NowPlaying?> = media.nowPlaying

    // Sample values until the app launcher and notification listener land (M2/M4).
    val railApps: StateFlow<List<RailApp>> = MutableStateFlow(FakeData.railApps).asStateFlow()
    val notificationCount: StateFlow<Int> = MutableStateFlow(3).asStateFlow()

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

    private fun lyricsFor(track: Track?): Flow<LyricsUiState> = when {
        track == null -> flowOf(LyricsUiState.Idle)
        track.kind != MediaKind.MUSIC -> flowOf(LyricsUiState.Unavailable(track.kind))
        else -> retryRequests
            .map { true }
            .onStart { emit(false) }
            .transformLatest { forceRefresh ->
                emit(LyricsUiState.Loading)
                emit(lyricsRepository.lyricsFor(track, forceRefresh).toUiState(track.id))
            }
    }

    override fun onPlayPause() = media.playPause()
    override fun onSkipNext() = media.skipNext()
    override fun onSkipPrevious() = media.skipPrevious()
    override fun onSeek(positionMs: Long) = media.seekTo(positionMs)
    override fun onToggleShuffle() = media.toggleShuffle()
    override fun onCycleRepeat() = media.cycleRepeat()

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
    }
}
