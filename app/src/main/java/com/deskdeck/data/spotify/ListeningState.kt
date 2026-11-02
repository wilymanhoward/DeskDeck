package com.deskdeck.data.spotify

import android.os.SystemClock
import com.deskdeck.data.settings.SettingsRepository
import com.deskdeck.di.ApplicationScope
import com.deskdeck.domain.NowPlaying
import com.deskdeck.domain.PlaybackStatus
import com.deskdeck.domain.Track
import com.deskdeck.media.ArtworkLoader
import com.deskdeck.media.MediaRepository
import com.deskdeck.media.ResolvedArtwork
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** Spotify's own view of the account's playback (`/me/player`), as read at [atElapsedMs]. */
data class RemotePlayback(
    val isPlaying: Boolean,
    val progressMs: Long,
    /** `SystemClock.elapsedRealtime()` when [progressMs] was read, to extrapolate from. */
    val atElapsedMs: Long,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val imageUrl: String?,
    /** The device playing, e.g. "Howard's MacBook". */
    val deviceName: String?,
    /** That device is this tablet (then its own session is the better source). */
    val onThisDevice: Boolean = false,
)

/**
 * What's playing, including Spotify on another device the tablet's Spotify remote-controls
 * (Spotify Connect, e.g. a laptop). Then the tablet's Spotify session can't be trusted: it may say
 * playing (with no Spotify audio on the tablet) or say paused and stop updating, track included
 * (both seen on the Tab S9 FE). So while Spotify is a source here, DeskDeck asks the Web API every
 * [POLL_MS] (and right after each song should end): whether it plays, where (the Audio output
 * panel shows the device), and, when the session here is stale, the song and position for the
 * lyrics. Needs the Spotify library connected in Settings; without it, everything is as the
 * tablet's sessions report.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ListeningState @Inject constructor(
    media: MediaRepository,
    library: SpotifyLibrary,
    auth: SpotifyAuth,
    settings: SettingsRepository,
    artworkLoader: ArtworkLoader,
    @ApplicationScope scope: CoroutineScope,
) {
    private val askSpotify = combine(
        media.sessionPackages.map { SpotifyLibrary.SPOTIFY_PACKAGE in it },
        settings.settings.map { it.spotifyLibrary },
        auth.account.map { it is SpotifyAccount.SignedIn },
    ) { spotifyHere, libraryOn, signedIn -> spotifyHere && libraryOn && signedIn }.distinctUntilChanged()

    /** Spotify's playback per the Web API, while Spotify is a source here and the library is connected. */
    val remote: StateFlow<RemotePlayback?> = askSpotify
        .flatMapLatest { ask ->
            if (!ask) flowOf<RemotePlayback?>(null) else flow {
                while (true) {
                    val playback = library.currentPlayback()
                    emit(playback)
                    delay(nextPollMs(playback))
                }
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** Music playing anywhere: Standby's idle start and the burn-in timers use this. */
    val anyPlaying: StateFlow<Boolean> = combine(media.anyPlaying, remote) { here, remote -> here || remote?.isPlaying == true }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    private val remoteArt = remote.map { it?.imageUrl }.distinctUntilChanged()
        .mapLatest { url -> url?.let { artworkLoader.resolve(null, it, cropLetterbox = false) } }
        .onStart { emit(null) }

    /** The shown session, or Spotify's remote playback when the tablet's Spotify session is stale. */
    val nowPlaying: StateFlow<NowPlaying?> = combine(media.nowPlaying, remote, remoteArt, ::withRemote)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), media.nowPlaying.value)

    private companion object {
        const val POLL_MS = 30_000L
    }
}

/** Every 30 s, or just after the current song should end (so the next one's lyrics come quickly). */
internal fun nextPollMs(playback: RemotePlayback?, pollMs: Long = 30_000L): Long {
    val duration = playback?.durationMs?.takeIf { playback.isPlaying && it > 0 } ?: return pollMs
    return (duration - playback.progressMs + 1_500).coerceIn(3_000, pollMs)
}

/**
 * The shown Spotify session with the song, position and art Spotify's Web API reports, while the
 * tablet's session itself isn't playing (it's stale during Spotify Connect). Anything else as is.
 */
internal fun withRemote(np: NowPlaying?, remote: RemotePlayback?, art: ResolvedArtwork?): NowPlaying? {
    if (np == null || remote?.title == null) return np
    if (np.sourcePackage != SpotifyLibrary.SPOTIFY_PACKAGE || np.playback.isPlaying || remote.onThisDevice) return np
    // Same id scheme as the session mapping, so the same song keeps its lyrics and art.
    val track = Track(
        id = "${np.sourcePackage}|${remote.title}|${remote.artist.orEmpty()}",
        title = remote.title,
        artist = remote.artist,
        album = remote.album,
        durationMs = remote.durationMs,
    )
    val sameSong = track.id == np.track.id
    return np.copy(
        track = track,
        playback = np.playback.copy(
            status = if (remote.isPlaying) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED,
            positionMs = remote.progressMs,
            updatedAtElapsedMs = remote.atElapsedMs,
            speed = 1f,
        ),
        artwork = if (sameSong) np.artwork else art?.bitmap,
        colors = if (sameSong) np.colors else art?.colors,
    )
}

/** For [SpotifyLibrary.currentPlayback]: the Web API's player JSON as a [RemotePlayback]. */
internal fun PlayerJson.toRemote(nowElapsedMs: Long = SystemClock.elapsedRealtime()) = RemotePlayback(
    isPlaying = isPlaying,
    progressMs = progressMs ?: 0,
    atElapsedMs = nowElapsedMs,
    title = item?.name,
    artist = item?.artists?.mapNotNull { it.name }?.takeIf { it.isNotEmpty() }?.joinToString(", "),
    album = item?.album?.name,
    durationMs = item?.durationMs,
    imageUrl = item?.album?.images?.firstOrNull()?.url,
    deviceName = device?.name,
)
