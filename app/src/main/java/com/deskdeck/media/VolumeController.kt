package com.deskdeck.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.VolumeProvider
import android.media.session.MediaController
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.deskdeck.di.ApplicationScope
import com.deskdeck.domain.VolumeEvent
import com.deskdeck.domain.VolumeState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Volume for DeskDeck's own HUD. Follows the shown session: a remote session (Spotify Connect,
 * Cast) gets its device volume through [MediaController], anything else the tablet's music stream.
 *
 * Changes are made without `FLAG_SHOW_UI`, so the system volume panel stays hidden. Changes from
 * elsewhere (Bluetooth buttons, another phone on Spotify Connect) arrive through the
 * `VOLUME_CHANGED_ACTION` broadcast or the session's audio-info callback and show the HUD too.
 * Everything runs on the main thread.
 */
@Singleton
class VolumeController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    sessions: MediaSessionRepository,
    @param:ApplicationScope scope: CoroutineScope,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(readLocal())
    val state: StateFlow<VolumeState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<VolumeEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<VolumeEvent> = _events.asSharedFlow()

    private var controller: MediaController? = null
    private var receiverRegistered = false

    /** Remote sessions have no mute; muting sets 0 and remembers the level to come back to. */
    private var remoteUnmuteLevel = 0

    private val sessionCallback = object : MediaController.Callback() {
        override fun onAudioInfoChanged(info: MediaController.PlaybackInfo) = refresh(userInitiated = false)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val stream = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
            if (stream == AudioManager.STREAM_MUSIC || stream == -1) refresh(userInitiated = false)
        }
    }

    init {
        scope.launch { sessions.shownController.collect(::follow) }
    }

    /** Starts watching outside changes; call while DeskDeck is visible. */
    fun start() {
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(VOLUME_CHANGED_ACTION)
                addAction(STREAM_MUTE_CHANGED_ACTION)
            }
            // Both are protected system broadcasts, which reach non-exported receivers.
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        // Whatever changed while DeskDeck was away is not news; update without showing the HUD.
        _state.value = read()
    }

    fun stop() {
        if (receiverRegistered) {
            context.unregisterReceiver(receiver)
            receiverRegistered = false
        }
    }

    /**
     * Handles a hardware volume key.
     * @return false when the key should go to the system instead (fixed-volume output).
     */
    fun onKey(keyCode: Int, repeat: Boolean): Boolean {
        val current = read()
        if (current.fixed) return false
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> adjust(+1, current, repeat)
            KeyEvent.KEYCODE_VOLUME_DOWN -> adjust(-1, current, repeat)
            KeyEvent.KEYCODE_VOLUME_MUTE -> if (!repeat) toggleMute() else return true
            else -> return false
        }
        return true
    }

    /** Sets the volume from the slider or the HUD; [fraction] is 0..1. */
    fun setFraction(fraction: Float) {
        val before = read()
        if (before.fixed) return
        val level = before.levelFor(fraction)
        if (level == before.level && !before.muted) return
        val remote = remoteInfo()
        if (remote != null) {
            controller?.setVolumeTo(level, 0)
        } else {
            if (before.muted && level > 0) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
        }
        val after = if (remote != null) before.copy(level = level, muted = false) else read()
        val direction = level.compareTo(before.level)
        publish(after, userInitiated = true, hitLimit = after.atLimit(direction) && !before.atLimit(direction))
    }

    fun toggleMute() {
        val before = read()
        if (before.fixed) return
        if (remoteInfo() != null) {
            val target = if (before.level > 0) {
                remoteUnmuteLevel = before.level
                0
            } else {
                remoteUnmuteLevel.takeIf { it > 0 } ?: (before.max / 4).coerceAtLeast(1)
            }
            controller?.setVolumeTo(target, 0)
            publish(before.copy(level = target), userInitiated = true, hitLimit = false)
        } else {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
            publish(read(), userInitiated = true, hitLimit = false)
        }
    }

    private fun adjust(direction: Int, before: VolumeState, repeat: Boolean) {
        val remote = remoteInfo()
        val after = if (remote != null) {
            controller?.adjustVolume(direction, 0)
            // The device reports back through onAudioInfoChanged; assume the step meanwhile.
            before.copy(level = (before.level + direction).coerceIn(0, before.max), muted = false)
        } else {
            val raise = direction > 0
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
            val read = read()
            if (raise && !repeat && !before.muted && read.level == before.level && !before.atLimit(direction)) {
                // Android's hearing-safety limit (headphones) refuses silent raises past the safe
                // level; only its own confirmation dialog can allow it, so ask with the system UI.
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            }
            read
        }
        // Tick once when reaching the end, and again for each new press against it, not on key repeat.
        val hitLimit = after.atLimit(direction) && (!before.atLimit(direction) || !repeat)
        publish(after, userInitiated = true, hitLimit = hitLimit)
    }

    private fun follow(next: MediaController?) {
        if (controller?.sessionToken == next?.sessionToken) return
        controller?.unregisterCallback(sessionCallback)
        controller = next
        next?.registerCallback(sessionCallback, handler)
        // Switching sources changes what the volume means, but isn't a volume change.
        _state.value = read()
    }

    private fun refresh(userInitiated: Boolean) {
        val now = read()
        if (now != _state.value) publish(now, userInitiated, hitLimit = false)
    }

    private fun publish(state: VolumeState, userInitiated: Boolean, hitLimit: Boolean) {
        _state.value = state
        _events.tryEmit(VolumeEvent(state, userInitiated, hitLimit))
    }

    /**
     * The shown session's remote volume, if it has one that can be changed. Spotify reports every
     * session as remote with a fixed volume (its Connect route) even when playing on this tablet;
     * the system then adjusts the local stream, and so does DeskDeck.
     */
    private fun remoteInfo(): MediaController.PlaybackInfo? = controller?.playbackInfo?.takeIf {
        it.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE &&
            it.volumeControl != VolumeProvider.VOLUME_CONTROL_FIXED
    }

    private fun read(): VolumeState {
        val remote = remoteInfo() ?: return readLocal()
        return VolumeState(
            level = remote.currentVolume,
            max = remote.maxVolume,
            remote = true,
        )
    }

    private fun readLocal(): VolumeState = VolumeState(
        level = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
        max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
        muted = audio.isStreamMute(AudioManager.STREAM_MUSIC),
        fixed = audio.isVolumeFixed,
    )

    private companion object {
        // Not in the public SDK, but stable since Android 4 and what the system volume panel listens to.
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        const val STREAM_MUTE_CHANGED_ACTION = "android.media.STREAM_MUTE_CHANGED_ACTION"
        const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    }
}
