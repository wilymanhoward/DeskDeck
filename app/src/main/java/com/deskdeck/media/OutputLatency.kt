package com.deskdeck.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.deskdeck.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How far the sound you hear lags the position the player reports, and why. */
data class AudioDelay(val ms: Long, val bluetooth: Boolean, val measured: Boolean)

/** Where media plays now. [id] is stable per device, for remembering per-speaker settings. */
data class OutputRoute(val id: String, val name: String?, val bluetooth: Boolean)

/**
 * The music output's latency: from audio being handed to Android until it comes out of the
 * speaker or headphones. Players like Spotify report the position they've decoded, so without
 * this, lyrics run early: a third of a second inside the tablet over Bluetooth, and more once the
 * Bluetooth speaker's own buffering is added.
 *
 * Android has no API for another app's output latency, so it's measured the way video players
 * keep lip-sync: a short *silent* [AudioTrack] on the same media output, comparing the frames
 * written with the presentation timestamps Android reports (which include the delay a Bluetooth
 * device reports about itself). It's measured only while music is already playing, so it never
 * wakes a speaker, and again whenever the audio route changes.
 */
@Singleton
class OutputLatency @Inject constructor(
    @param:ApplicationContext context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val _delay = MutableStateFlow(typical())
    val delay: StateFlow<AudioDelay> = _delay.asStateFlow()

    private val _route = MutableStateFlow(currentRoute())
    val route: StateFlow<OutputRoute> = _route.asStateFlow()

    /** The route a measurement belongs to; a new route makes it stale. */
    private var measuredRoute: String? = null
    private var job: Job? = null

    private val routeCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = routeChanged()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = routeChanged()
    }

    init {
        audio.registerAudioDeviceCallback(routeCallback, handler)
    }

    /** Measures if the current route hasn't been measured yet. Call while music is playing. */
    fun measureIfNeeded() {
        val route = currentRoute().id
        if (route == measuredRoute || job?.isActive == true) return
        job = scope.launch {
            val ms = withContext(Dispatchers.IO) { runCatching(::measure).getOrNull() }
            Log.i(TAG, "Output latency on $route: ${ms ?: "unavailable"} ms")
            if (ms != null) {
                measuredRoute = route
                _delay.value = AudioDelay(ms, bluetooth = isBluetooth(), measured = true)
            }
        }
    }

    private fun routeChanged() {
        _route.value = currentRoute()
        if (_route.value.id == measuredRoute) return
        measuredRoute = null
        _delay.value = typical()
    }

    /**
     * Plays ~1.5 s of silence and returns the median of (when the last written frame will be heard
     * − now), taken once the stream has settled.
     */
    private fun measure(): Long? {
        val rate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC).takeIf { it > 0 } ?: 48_000
        val minBuffer = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuffer)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        try {
            track.setVolume(0f)
            val chunk = ShortArray(rate / 50 * 2) // 20 ms of stereo silence
            val timestamp = AudioTimestamp()
            val samples = mutableListOf<Long>()
            var written = 0L
            track.play()
            val start = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - start < MEASURE_MS) {
                val n = track.write(chunk, 0, chunk.size)
                if (n <= 0) break
                written += n / 2
                // Skip the start-up, when timestamps are still settling.
                if (SystemClock.elapsedRealtime() - start < SETTLE_MS) continue
                if (track.getTimestamp(timestamp) && timestamp.framePosition > 0) {
                    val heardAtNs = timestamp.nanoTime + (written - timestamp.framePosition) * 1_000_000_000L / rate
                    samples += (heardAtNs - System.nanoTime()) / 1_000_000L
                }
            }
            if (samples.size < MIN_SAMPLES) return null
            return samples.sorted()[samples.size / 2].takeIf { it in 0..MAX_PLAUSIBLE_MS }
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private fun typical() = isBluetooth().let { bt -> AudioDelay(if (bt) BLUETOOTH_TYPICAL_MS else SPEAKER_TYPICAL_MS, bt, measured = false) }

    /** Where media goes now: the first Bluetooth, wired or USB output if present, else the speaker. */
    private fun currentRoute(): OutputRoute {
        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val external = outputs.firstOrNull { it.type in EXTERNAL_TYPES }
            ?: return OutputRoute("speaker", name = null, bluetooth = false)
        val name = external.productName?.toString()?.takeIf { it.isNotBlank() }
        return OutputRoute("${external.type}:$name", name, bluetooth = external.type in BLUETOOTH_TYPES)
    }

    private fun isBluetooth() = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in BLUETOOTH_TYPES }

    private companion object {
        const val TAG = "OutputLatency"
        const val MEASURE_MS = 1_500L
        const val SETTLE_MS = 600L
        const val MIN_SAMPLES = 5
        const val MAX_PLAUSIBLE_MS = 2_000L
        const val BLUETOOTH_TYPICAL_MS = 300L
        const val SPEAKER_TYPICAL_MS = 40L
        val BLUETOOTH_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
        )
        val EXTERNAL_TYPES = BLUETOOTH_TYPES + setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
        )
    }
}
