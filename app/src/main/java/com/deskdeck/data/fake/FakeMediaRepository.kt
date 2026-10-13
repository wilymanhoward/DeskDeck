package com.deskdeck.data.fake

import android.os.SystemClock
import com.deskdeck.domain.NowPlaying
import com.deskdeck.domain.PlaybackInfo
import com.deskdeck.domain.PlaybackStatus
import com.deskdeck.domain.RepeatMode
import com.deskdeck.media.MediaRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A pretend player over [FakeData.tracks] so the shell can be exercised without notification
 * access. It behaves like a real media session: positions are reported as "position at time"
 * snapshots and only updated on state changes, never per tick.
 */
@Singleton
class FakeMediaRepository @Inject constructor() : MediaRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var index = 0
    private var trackEndJob: Job? = null

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(buildNowPlaying(index, playing = true, positionMs = 0))
    override val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    init {
        scheduleTrackEnd()
    }

    override fun playPause() {
        val current = _nowPlaying.value ?: return
        val playing = !current.playback.isPlaying
        updatePlayback {
            it.copy(
                status = if (playing) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED,
                positionMs = currentPosition(),
                updatedAtElapsedMs = now(),
            )
        }
    }

    override fun skipNext() = loadTrack((index + 1) % FakeData.tracks.size)

    override fun skipPrevious() {
        if (currentPosition() > RESTART_THRESHOLD_MS) {
            seekTo(0)
        } else {
            loadTrack((index - 1 + FakeData.tracks.size) % FakeData.tracks.size)
        }
    }

    override fun seekTo(positionMs: Long) {
        updatePlayback { it.copy(positionMs = positionMs.coerceAtLeast(0), updatedAtElapsedMs = now()) }
    }

    override fun toggleShuffle() {
        updatePlayback { it.copy(shuffleOn = !it.shuffleOn) }
    }

    override fun cycleRepeat() {
        updatePlayback {
            val next = when (it.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            it.copy(repeatMode = next)
        }
    }

    private fun loadTrack(newIndex: Int) {
        val previous = _nowPlaying.value?.playback
        index = newIndex
        _nowPlaying.value = buildNowPlaying(newIndex, playing = previous?.isPlaying ?: true, positionMs = 0).let { np ->
            np.copy(
                playback = np.playback.copy(
                    shuffleOn = previous?.shuffleOn ?: false,
                    repeatMode = previous?.repeatMode ?: RepeatMode.OFF,
                ),
            )
        }
        scheduleTrackEnd()
    }

    private fun updatePlayback(transform: (PlaybackInfo) -> PlaybackInfo) {
        val current = _nowPlaying.value ?: return
        _nowPlaying.value = current.copy(playback = transform(current.playback))
        scheduleTrackEnd()
    }

    private fun scheduleTrackEnd() {
        trackEndJob?.cancel()
        val np = _nowPlaying.value ?: return
        val duration = np.track.durationMs ?: return
        if (!np.playback.isPlaying) return
        val remaining = duration - currentPosition()
        trackEndJob = scope.launch {
            delay(remaining.coerceAtLeast(0))
            when (_nowPlaying.value?.playback?.repeatMode) {
                RepeatMode.ONE -> seekTo(0)
                else -> skipNext()
            }
        }
    }

    private fun currentPosition(): Long {
        val np = _nowPlaying.value ?: return 0
        return np.playback.positionAt(now(), np.track.durationMs)
    }

    private fun buildNowPlaying(i: Int, playing: Boolean, positionMs: Long): NowPlaying {
        val fake = FakeData.tracks[i]
        return NowPlaying(
            track = fake.track,
            playback = PlaybackInfo(
                status = if (playing) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED,
                positionMs = positionMs,
                updatedAtElapsedMs = now(),
                shuffleSupported = true,
                // Exercise the "not supported" state on the YouTube Music sample.
                repeatSupported = fake.sourcePackage == FakeData.SPOTIFY,
            ),
            sourcePackage = fake.sourcePackage,
            sourceLabel = fake.sourceLabel,
            artwork = null,
            colors = fake.colors,
        )
    }

    private fun now() = SystemClock.elapsedRealtime()

    private companion object {
        const val RESTART_THRESHOLD_MS = 3_000L
    }
}
