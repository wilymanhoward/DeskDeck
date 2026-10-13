package com.deskdeck.media

import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.deskdeck.di.ApplicationScope
import com.deskdeck.domain.NowPlaying
import com.deskdeck.domain.PlaybackInfo
import com.deskdeck.domain.PlaybackStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Tracks other apps' media sessions (allowed because [MediaListenerService] is an enabled
 * notification listener), picks the one to show with [SessionSelector], and publishes it as
 * [NowPlaying]. Everything runs on the main thread: session callbacks are delivered on [handler]
 * and [scope] uses the main dispatcher.
 */
@Singleton
class MediaSessionRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationAccess: NotificationAccess,
    private val artworkLoader: ArtworkLoader,
    private val appLabels: AppLabels,
    @param:ApplicationScope private val scope: CoroutineScope,
) : MediaRepository {

    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val entries = mutableListOf<SessionEntry>()
    private var nextEntryId = 1L
    private var selected: SessionEntry? = null
    private var listening = false

    private var artworkKey: String? = null
    private var artwork: ResolvedArtwork? = null
    private var artworkJob: Job? = null

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    override val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateSessions(controllers.orEmpty(), alreadyRunning = false)
    }

    /** Re-reads the real state after an optimistic update, in case the player ignored the command. */
    private val resync = Runnable { publish() }

    init {
        scope.launch {
            notificationAccess.granted.collect { granted ->
                if (granted) startListening() else stopListening()
            }
        }
    }

    private fun startListening() {
        if (listening) return
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, notificationAccess.listenerComponent, handler)
            listening = true
            updateSessions(sessionManager.getActiveSessions(notificationAccess.listenerComponent), alreadyRunning = true)
        } catch (_: SecurityException) {
            // Access was revoked between the check and the call. The next NotificationAccess.refresh() retries.
            stopListening()
        }
    }

    private fun stopListening() {
        if (listening) {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
            listening = false
        }
        entries.forEach { it.release() }
        entries.clear()
        selected = null
        publish()
    }

    private fun updateSessions(controllers: List<MediaController>, alreadyRunning: Boolean) {
        val others = controllers.filter { it.packageName != context.packageName }
        val tokens = others.map { it.sessionToken }.toSet()
        entries.removeAll { entry ->
            (entry.token !in tokens).also { gone -> if (gone) entry.release() }
        }
        others.forEachIndexed { index, controller ->
            val entry = entries.firstOrNull { it.token == controller.sessionToken }
                ?: SessionEntry(nextEntryId++, controller, context) { reselect() }.also {
                    it.register(handler, alreadyRunning)
                    entries += it
                }
            entry.systemOrder = index
        }
        reselect()
    }

    private fun reselect() {
        val id = SessionSelector.select(entries.map { it.candidate() }, selected?.id)
        selected = entries.firstOrNull { it.id == id }
        publish()
    }

    private fun publish() {
        handler.removeCallbacks(resync)
        val entry = selected
        val track = entry?.track()
        if (entry == null || track == null) {
            artworkJob?.cancel()
            artworkKey = null
            artwork = null
            _nowPlaying.value = null
            return
        }

        val bitmap = entry.artBitmap()
        val uri = entry.artUri()
        // Bitmaps are new objects on every metadata update, so key on what identifies the art.
        val key = "${track.id}|${bitmap?.width}x${bitmap?.height}|$uri"
        if (key != artworkKey) {
            artworkKey = key
            // Show a direct bitmap immediately; keep the previous colors until the new ones are ready
            // so the card doesn't flash grey between tracks.
            artwork = ResolvedArtwork(bitmap, artwork?.colors)
            artworkJob?.cancel()
            artworkJob = scope.launch {
                val resolved = artworkLoader.resolve(bitmap, uri)
                if (artworkKey == key) {
                    artwork = resolved
                    publish()
                }
            }
        }

        _nowPlaying.value = NowPlaying(
            track = track,
            playback = entry.playbackInfo(),
            sourcePackage = entry.packageName,
            sourceLabel = appLabels.labelOf(entry.packageName),
            artwork = artwork?.bitmap,
            colors = artwork?.colors,
        )
    }

    // region Commands

    override fun playPause() {
        val entry = selected ?: return
        val current = _nowPlaying.value?.playback ?: return
        val playing = current.status == PlaybackStatus.PLAYING || current.status == PlaybackStatus.BUFFERING
        if (playing) entry.pause() else entry.play()
        updateOptimistically {
            it.copy(
                status = if (playing) PlaybackStatus.PAUSED else PlaybackStatus.PLAYING,
                positionMs = currentPosition(),
                updatedAtElapsedMs = SystemClock.elapsedRealtime(),
            )
        }
    }

    override fun skipNext() {
        selected?.skipNext()
    }

    override fun skipPrevious() {
        selected?.skipPrevious()
    }

    override fun seekTo(positionMs: Long) {
        val entry = selected ?: return
        entry.seekTo(positionMs)
        // Without this the seek bar jumps back to the old position until the player reports in.
        updateOptimistically { it.copy(positionMs = positionMs, updatedAtElapsedMs = SystemClock.elapsedRealtime()) }
    }

    override fun toggleShuffle() {
        val entry = selected ?: return
        val current = _nowPlaying.value?.playback ?: return
        val assumed = entry.toggleShuffle(current.shuffleOn) ?: return
        updateOptimistically { it.copy(shuffleOn = assumed) }
    }

    override fun cycleRepeat() {
        val entry = selected ?: return
        val current = _nowPlaying.value?.playback ?: return
        val assumed = entry.cycleRepeat(current.repeatMode) ?: return
        updateOptimistically { it.copy(repeatMode = assumed) }
    }

    private fun updateOptimistically(transform: (PlaybackInfo) -> PlaybackInfo) {
        val np = _nowPlaying.value ?: return
        _nowPlaying.value = np.copy(playback = transform(np.playback))
        handler.removeCallbacks(resync)
        handler.postDelayed(resync, RESYNC_DELAY_MS)
    }

    private fun currentPosition(): Long {
        val np = _nowPlaying.value ?: return 0
        return np.playback.positionAt(SystemClock.elapsedRealtime(), np.track.durationMs)
    }

    // endregion

    private companion object {
        const val RESYNC_DELAY_MS = 1_500L
    }
}
