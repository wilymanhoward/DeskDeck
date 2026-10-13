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
import com.deskdeck.launcher.KnownApps
import com.deskdeck.launcher.RailAppsRepository
import com.deskdeck.media.AppLabels
import com.deskdeck.media.MediaRepository
import com.deskdeck.media.NotificationAccess
import com.deskdeck.media.AudioDelay
import com.deskdeck.media.NotificationBanners
import com.deskdeck.media.OutputLatency
import com.deskdeck.media.OutputRoute
import com.deskdeck.media.NotificationCounter
import com.deskdeck.media.sources.SourceStatus
import com.deskdeck.media.sources.SourceSwitcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    private val railAppsRepository: RailAppsRepository,
    notificationCounter: NotificationCounter,
    notificationAccess: NotificationAccess,
    private val appLauncher: AppLauncher,
    private val sourceSwitcher: SourceSwitcher,
    private val appLabels: AppLabels,
    networkMonitor: NetworkMonitor,
    private val banners: NotificationBanners,
    private val outputLatency: OutputLatency,
) : ViewModel(), DashboardActions {

    /** Audio output latency, so lyrics follow what's heard rather than what's decoded. */
    val audioDelay: StateFlow<AudioDelay> = outputLatency.delay
    val audioRoute: StateFlow<OutputRoute> = outputLatency.route

    /** Measures the output latency if this route hasn't been yet; call while music plays. */
    fun measureAudioDelay() = outputLatency.measureIfNeeded()

    val nowPlaying: StateFlow<NowPlaying?> = media.nowPlaying

    /** Any app playing, shown or not: what burn-in should treat as "music is on". */
    val anyPlaying: StateFlow<Boolean> = media.anyPlaying

    val railState: StateFlow<RailState> =
        combine(media.selection, media.nowPlaying, media.sessionPackages) { selection, np, packages ->
            RailState(
                highlightPackage = selection?.packageName ?: np?.sourcePackage,
                playingPackage = np?.takeIf { it.playback.isPlaying }?.sourcePackage,
                sessionPackages = packages,
                pinnedPackage = selection?.takeIf { it.pinned }?.packageName,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RailState())

    /** Set when an app is selected but has nothing to show (yet). */
    val emptySource: StateFlow<EmptySourceUi?> =
        combine(media.selection, media.nowPlaying, sourceSwitcher.status) { selection, np, status ->
            if (np != null) return@combine null
            val pkg = status.packageName ?: selection?.packageName ?: return@combine null
            val reason = when (status) {
                is SourceStatus.Starting -> EmptySourceUi.Reason.STARTING
                is SourceStatus.NotInstalled -> EmptySourceUi.Reason.NOT_INSTALLED
                else -> EmptySourceUi.Reason.NO_SESSION
            }
            EmptySourceUi(pkg, KnownApps.label(pkg) ?: appLabels.labelOf(pkg), reason)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    // Rail and source actions. Only onOpenApp/onGetApp leave DeskDeck, and only on an explicit tap.

    override fun onRailAppClick(app: RailApp) = sourceSwitcher.select(app.packageName)

    override fun onPinSource(packageName: String) = sourceSwitcher.select(packageName, pin = true)

    override fun onUnpinSource() = sourceSwitcher.setPinned(false)

    override fun onRemoveFromRail(packageName: String) {
        viewModelScope.launch { railAppsRepository.unpin(packageName) }
    }

    override fun onOpenNotifications() = banners.openCenter()

    override fun onOpenApp(packageName: String) = appLauncher.launchOrOpenStore(packageName)

    override fun onGetApp(packageName: String) = appLauncher.openStorePage(packageName)

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
