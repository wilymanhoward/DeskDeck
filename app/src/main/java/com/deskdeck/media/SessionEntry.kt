// The androidx.media compat classes are deprecated in favor of Media3, but they are still the most
// direct way to read shuffle/repeat from another app's session (Media3 uses them internally for that).
@file:Suppress("DEPRECATION")

package com.deskdeck.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.SystemClock
import android.view.KeyEvent
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.deskdeck.domain.PlaybackInfo
import com.deskdeck.domain.PlaybackStatus
import com.deskdeck.domain.RepeatMode
import com.deskdeck.domain.Track

/**
 * One active media session. Metadata, playback state and transport go through the framework
 * [MediaController]; a [MediaControllerCompat] on the same token is used only for shuffle/repeat,
 * whose state the framework API doesn't expose.
 */
internal class SessionEntry(
    val id: Long,
    /** Exposed for volume, which acts on the session itself rather than through transport controls. */
    internal val controller: MediaController,
    context: Context,
    private val onChanged: (SessionEntry) -> Unit,
) {
    val token: MediaSession.Token = controller.sessionToken
    val packageName: String = controller.packageName

    var systemOrder = 0
    var lastStartedPlayingAt = 0L
        private set
    private var wasPlaying = false
    private var destroyed = false

    private val compat: MediaControllerCompat? = try {
        MediaControllerCompat(context, MediaSessionCompat.Token.fromToken(token))
    } catch (_: Exception) {
        null
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = changed()
        override fun onMetadataChanged(metadata: MediaMetadata?) = changed()
        override fun onSessionDestroyed() {
            destroyed = true
            changed()
        }
    }

    private val compatCallback = object : MediaControllerCompat.Callback() {
        override fun onShuffleModeChanged(shuffleMode: Int) = changed()
        override fun onRepeatModeChanged(repeatMode: Int) = changed()
        override fun onSessionReady() = changed()
    }

    /**
     * @param alreadyRunning true for sessions found when tracking starts. If they are playing,
     * we don't know when they started, so they rank below any session seen starting later.
     */
    fun register(handler: Handler, alreadyRunning: Boolean) {
        controller.registerCallback(callback, handler)
        compat?.registerCallback(compatCallback, handler)
        wasPlaying = isPlaying
        if (wasPlaying) lastStartedPlayingAt = if (alreadyRunning) 1L else SystemClock.elapsedRealtime()
    }

    fun release() {
        controller.unregisterCallback(callback)
        compat?.unregisterCallback(compatCallback)
    }

    private fun changed() {
        val playing = isPlaying
        if (playing && !wasPlaying) lastStartedPlayingAt = SystemClock.elapsedRealtime()
        wasPlaying = playing
        onChanged(this)
    }

    val isPlaying: Boolean get() = !destroyed && controller.playbackState?.state == PlaybackState.STATE_PLAYING

    fun candidate(): SessionCandidate = SessionCandidate(
        id = id,
        packageName = packageName,
        isPlaying = isPlaying,
        hasMetadata = !destroyed && track() != null,
        lastStartedPlayingAt = lastStartedPlayingAt,
        systemOrder = systemOrder,
    )

    fun track(): Track? {
        val raw = controller.metadata?.toRaw() ?: return null
        return raw.toTrack(packageName, canSeek = playbackInfo().canSeek)
    }

    fun artBitmap(): Bitmap? = controller.metadata?.let { m ->
        m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    }

    fun artUri(): String? = controller.metadata?.let { m ->
        m.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: m.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
    }?.takeIf { it.isNotBlank() }

    private fun toggles(): ToggleSupport {
        val state = controller.playbackState
        // Before the compat session is ready the modes read as INVALID anyway; this just skips the IPC.
        val ready = compat?.takeIf { it.isSessionReady }
        return resolveToggles(
            actions = state?.actions ?: 0L,
            compatShuffleMode = ready?.shuffleMode ?: PlaybackStateCompat.SHUFFLE_MODE_INVALID,
            compatRepeatMode = ready?.repeatMode ?: PlaybackStateCompat.REPEAT_MODE_INVALID,
            customActions = state?.customActions.orEmpty().map { CustomActionInfo(it.action, it.name.toString()) },
        )
    }

    fun playbackInfo(): PlaybackInfo {
        val state = controller.playbackState
            ?: return PlaybackInfo(status = PlaybackStatus.STOPPED, positionMs = 0, updatedAtElapsedMs = 0)
        return buildPlaybackInfo(
            state = state.state,
            positionMs = state.position,
            lastPositionUpdateTime = state.lastPositionUpdateTime,
            speed = state.playbackSpeed,
            actions = state.actions,
            toggles = toggles(),
        )
    }

    // region Commands

    private val transport get() = controller.transportControls

    fun play() = transport.play()

    /** Load the last queue without starting playback. */
    fun prepare() = transport.prepare()

    /** A MEDIA_PLAY key press, as a headset button would send; wakes sessions that ignore play(). */
    fun sendPlayKey() {
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            controller.dispatchMediaButtonEvent(KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY))
        }
    }

    val hasMetadata: Boolean get() = !destroyed && track() != null
    fun pause() = transport.pause()
    fun skipNext() = transport.skipToNext()
    fun skipPrevious() = transport.skipToPrevious()
    fun seekTo(positionMs: Long) = transport.seekTo(positionMs)

    /** @return the shuffle state the UI can assume right away, or null if only the player knows. */
    fun toggleShuffle(currentlyOn: Boolean): Boolean? = when (val command = toggles().shuffleCommand) {
        ToggleCommand.Compat -> {
            val mode = if (currentlyOn) PlaybackStateCompat.SHUFFLE_MODE_NONE else PlaybackStateCompat.SHUFFLE_MODE_ALL
            compat?.transportControls?.setShuffleMode(mode)
            !currentlyOn
        }
        is ToggleCommand.Custom -> {
            transport.sendCustomAction(command.action, null)
            null
        }
        null -> null
    }

    /** @return the repeat mode the UI can assume right away, or null if only the player knows. */
    fun cycleRepeat(current: RepeatMode): RepeatMode? = when (val command = toggles().repeatCommand) {
        ToggleCommand.Compat -> {
            compat?.transportControls?.setRepeatMode(current.nextCompatRepeatMode())
            when (current) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
        }
        is ToggleCommand.Custom -> {
            transport.sendCustomAction(command.action, null)
            null
        }
        null -> null
    }

    // endregion
}

private const val METADATA_KEY_ADVERTISEMENT = "android.media.metadata.ADVERTISEMENT"

private fun MediaMetadata.toRaw() = RawMetadata(
    title = getString(MediaMetadata.METADATA_KEY_TITLE),
    artist = getString(MediaMetadata.METADATA_KEY_ARTIST),
    album = getString(MediaMetadata.METADATA_KEY_ALBUM),
    albumArtist = getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
    displayTitle = getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
    displaySubtitle = getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
    durationMs = getLong(MediaMetadata.METADATA_KEY_DURATION),
    mediaId = getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
    mediaUri = getString(MediaMetadata.METADATA_KEY_MEDIA_URI),
    genre = getString(MediaMetadata.METADATA_KEY_GENRE),
    advertisement = getLong(METADATA_KEY_ADVERTISEMENT),
)
