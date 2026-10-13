// The androidx.media compat classes are deprecated in favor of Media3, but they are still the most
// direct way to read shuffle/repeat from another app's session (Media3 uses them internally for that).
@file:Suppress("DEPRECATION")

package com.deskdeck.media

import android.media.session.PlaybackState
import android.support.v4.media.session.PlaybackStateCompat
import com.deskdeck.domain.MediaKind
import com.deskdeck.domain.PlaybackInfo
import com.deskdeck.domain.PlaybackStatus
import com.deskdeck.domain.RepeatMode
import com.deskdeck.domain.Track

/**
 * Plain-value view of `MediaMetadata`, so mapping and detection can be unit-tested without
 * Android framework objects. Built by [MediaControllerSnapshot].
 */
data class RawMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String? = null,
    val displayTitle: String? = null,
    val displaySubtitle: String? = null,
    val durationMs: Long? = null,
    val mediaId: String? = null,
    val mediaUri: String? = null,
    val genre: String? = null,
    /** Compat `METADATA_KEY_ADVERTISEMENT`: 1 when the item is an ad. */
    val advertisement: Long? = null,
)

/** Builds a [Track] from session metadata; null when there is nothing worth showing. */
fun RawMetadata.toTrack(packageName: String, canSeek: Boolean): Track? {
    val title = (title ?: displayTitle)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val artist = (artist ?: albumArtist ?: displaySubtitle)?.trim()?.takeIf { it.isNotEmpty() }
    val duration = durationMs?.takeIf { it > 0 }
    return Track(
        // Duration and album are left out of the identity: some players fill them in a moment
        // after the track starts, and that must not look like a new track.
        id = "$packageName|$title|${artist.orEmpty()}",
        title = title,
        artist = artist,
        album = album?.trim()?.takeIf { it.isNotEmpty() },
        durationMs = duration,
        kind = detectMediaKind(packageName, this, title, artist, duration, canSeek),
    )
}

private val PODCAST_APPS = setOf(
    "com.google.android.apps.podcasts",
    "au.com.shiftyjelly.pocketcasts",
    "com.bambuna.podcastaddict",
    "fm.castbox.audiobook.radio.podcast",
    "com.podcast.podcasts",
)

internal fun detectMediaKind(
    packageName: String,
    raw: RawMetadata,
    title: String,
    artist: String?,
    durationMs: Long?,
    canSeek: Boolean,
): MediaKind {
    val ids = listOfNotNull(raw.mediaId, raw.mediaUri).map { it.lowercase() }

    val isAd = raw.advertisement == 1L ||
        ids.any { it.startsWith("spotify:ad:") } ||
        title.equals("Advertisement", ignoreCase = true) ||
        title.equals("Ad", ignoreCase = true) ||
        // Spotify's own promos come through as artist "Spotify" with no album.
        (packageName == "com.spotify.music" && artist.equals("Spotify", ignoreCase = true) && raw.album.isNullOrBlank())
    if (isAd) return MediaKind.AD

    val isPodcast = packageName in PODCAST_APPS ||
        ids.any { it.contains(":episode:") || it.contains("/episode/") } ||
        raw.genre?.contains("podcast", ignoreCase = true) == true
    if (isPodcast) return MediaKind.PODCAST

    // Radio-style streams: no duration and no seeking. Tracks that merely lack a duration (some
    // video players) stay MUSIC so lyrics are still looked up.
    if (durationMs == null && !canSeek) return MediaKind.LIVE

    return MediaKind.MUSIC
}

fun mapPlaybackStatus(state: Int): PlaybackStatus = when (state) {
    PlaybackState.STATE_PLAYING -> PlaybackStatus.PLAYING
    PlaybackState.STATE_PAUSED -> PlaybackStatus.PAUSED
    PlaybackState.STATE_BUFFERING,
    PlaybackState.STATE_CONNECTING,
    PlaybackState.STATE_FAST_FORWARDING,
    PlaybackState.STATE_REWINDING,
    PlaybackState.STATE_SKIPPING_TO_NEXT,
    PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
    PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
    -> PlaybackStatus.BUFFERING
    else -> PlaybackStatus.STOPPED
}

data class CustomActionInfo(val action: String, val name: String)

/** How a shuffle or repeat change is sent to the player. */
sealed interface ToggleCommand {
    /** `setShuffleMode`/`setRepeatMode` through the compat controller (Media3 and MediaSessionCompat players). */
    data object Compat : ToggleCommand

    /** The player's own custom action (e.g. Spotify); the player decides what the next mode is. */
    data class Custom(val action: String) : ToggleCommand
}

data class ToggleSupport(
    val shuffleCommand: ToggleCommand?,
    val shuffleOn: Boolean,
    val repeatCommand: ToggleCommand?,
    val repeatMode: RepeatMode,
)

/**
 * Works out whether shuffle/repeat can be controlled and their current state, best effort:
 * 1. compat action bit + known compat mode: full support with real state;
 * 2. a custom action named like "shuffle"/"repeat": supported, state only if compat knows it;
 * 3. compat action bit without known mode: supported, state unknown (shown as off);
 * 4. otherwise unsupported (the button is disabled).
 * [compatShuffleMode]/[compatRepeatMode] are `PlaybackStateCompat` modes, `*_INVALID` when unknown.
 */
fun resolveToggles(
    actions: Long,
    compatShuffleMode: Int,
    compatRepeatMode: Int,
    customActions: List<CustomActionInfo>,
): ToggleSupport {
    val shuffleBit = actions and PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE != 0L
    val repeatBit = actions and PlaybackStateCompat.ACTION_SET_REPEAT_MODE != 0L
    val shuffleKnown = compatShuffleMode != PlaybackStateCompat.SHUFFLE_MODE_INVALID
    val repeatKnown = compatRepeatMode != PlaybackStateCompat.REPEAT_MODE_INVALID

    fun custom(vararg keywords: String) = customActions.firstOrNull { ca ->
        keywords.any { ca.action.contains(it, ignoreCase = true) || ca.name.contains(it, ignoreCase = true) }
    }?.let { ToggleCommand.Custom(it.action) }

    val shuffleCommand = when {
        shuffleBit && shuffleKnown -> ToggleCommand.Compat
        else -> custom("shuffle") ?: if (shuffleBit) ToggleCommand.Compat else null
    }
    val repeatCommand = when {
        repeatBit && repeatKnown -> ToggleCommand.Compat
        else -> custom("repeat", "loop") ?: if (repeatBit) ToggleCommand.Compat else null
    }

    return ToggleSupport(
        shuffleCommand = shuffleCommand,
        shuffleOn = shuffleKnown && compatShuffleMode != PlaybackStateCompat.SHUFFLE_MODE_NONE,
        repeatCommand = repeatCommand,
        repeatMode = when (compatRepeatMode) {
            PlaybackStateCompat.REPEAT_MODE_ONE -> RepeatMode.ONE
            PlaybackStateCompat.REPEAT_MODE_ALL, PlaybackStateCompat.REPEAT_MODE_GROUP -> RepeatMode.ALL
            else -> RepeatMode.OFF
        },
    )
}

fun RepeatMode.nextCompatRepeatMode(): Int = when (this) {
    RepeatMode.OFF -> PlaybackStateCompat.REPEAT_MODE_ALL
    RepeatMode.ALL -> PlaybackStateCompat.REPEAT_MODE_ONE
    RepeatMode.ONE -> PlaybackStateCompat.REPEAT_MODE_NONE
}

/** Combines a framework playback state (as plain values) and toggle support into [PlaybackInfo]. */
fun buildPlaybackInfo(
    state: Int,
    positionMs: Long,
    lastPositionUpdateTime: Long,
    speed: Float,
    actions: Long,
    toggles: ToggleSupport,
): PlaybackInfo {
    // Some players publish no action flags at all; don't disable every button for them.
    fun can(action: Long) = actions == 0L || actions and action != 0L
    return PlaybackInfo(
        status = mapPlaybackStatus(state),
        positionMs = positionMs,
        updatedAtElapsedMs = lastPositionUpdateTime,
        speed = speed,
        canSeek = actions and PlaybackState.ACTION_SEEK_TO != 0L,
        canSkipNext = can(PlaybackState.ACTION_SKIP_TO_NEXT),
        canSkipPrevious = can(PlaybackState.ACTION_SKIP_TO_PREVIOUS),
        shuffleSupported = toggles.shuffleCommand != null,
        shuffleOn = toggles.shuffleOn,
        repeatSupported = toggles.repeatCommand != null,
        repeatMode = toggles.repeatMode,
    )
}
