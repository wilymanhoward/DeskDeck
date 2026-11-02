package com.deskdeck.data.spotify

import com.deskdeck.data.settings.SettingsRepository
import com.deskdeck.di.ApplicationScope
import com.deskdeck.media.MediaRepository
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
import kotlinx.coroutines.flow.stateIn

/**
 * Whether music is playing anywhere: on this tablet, or on another device the tablet's Spotify
 * remote-controls (Spotify Connect, e.g. a laptop). While another device plays, Spotify reports
 * its session here as paused and stops updating it (seen on the Tab S9 FE), so then DeskDeck asks
 * the Web API every [POLL_MS] instead; that needs the Spotify library connected in Settings.
 * Used by Standby's idle start and the burn-in timers, so they don't treat that as silence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ListeningState @Inject constructor(
    media: MediaRepository,
    library: SpotifyLibrary,
    auth: SpotifyAuth,
    settings: SettingsRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    val anyPlaying: StateFlow<Boolean> = combine(
        media.anyPlaying,
        media.sessionPackages.map { SpotifyLibrary.SPOTIFY_PACKAGE in it },
        settings.settings.map { it.spotifyLibrary },
        auth.account.map { it is SpotifyAccount.SignedIn },
        ::knownPlaying,
    )
        .distinctUntilChanged()
        .flatMapLatest { known ->
            known?.let { flowOf(it) } ?: flow {
                while (true) {
                    // Unknown (offline, signed out meanwhile) counts as not playing, as before.
                    emit(library.playingAnywhere() == true)
                    delay(POLL_MS)
                }
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    private companion object {
        const val POLL_MS = 30_000L
    }
}

/**
 * Playing here → true. Spotify's session is here but not playing, and the Web API is connected →
 * null: ask Spotify whether another device plays. Otherwise → false.
 */
internal fun knownPlaying(playingHere: Boolean, spotifyHere: Boolean, libraryOn: Boolean, signedIn: Boolean): Boolean? = when {
    playingHere -> true
    spotifyHere && libraryOn && signedIn -> null
    else -> false
}
