package com.deskdeck.media.sources

import kotlinx.coroutines.CancellationException

/**
 * A way to get an app playing *without opening its UI*. Sources are tried in order by
 * [ResumeChain]; new ones (e.g. Spotify Connect once signed in) slot in without touching the rest,
 * and the app works fully with only the built-in ones.
 */
interface PlaybackSource {
    /** Short name for logs and tests. */
    val name: String

    /** Whether this source can try for [packageName] right now (installed, enabled in settings …). */
    suspend fun canHandle(packageName: String): Boolean = true

    /** How long to wait for the app's session after [requestPlay]; null = the chain's default. */
    val sessionTimeoutMs: Long? get() = null

    /**
     * Asks [packageName] to start playing. Fire-and-forget: success is judged by a session
     * appearing afterwards, not by this call.
     * @return false if the request couldn't even be sent.
     */
    suspend fun requestPlay(packageName: String): Boolean
}

/**
 * Tries each [PlaybackSource] in turn until the app shows a media session. Pure orchestration, so
 * the ladder is unit-testable; [sessionAppeared] waits up to its timeout for the app's session.
 */
class ResumeChain(
    private val sources: List<PlaybackSource>,
    private val stepTimeoutMs: Long = STEP_TIMEOUT_MS,
) {
    /** @return the source that worked, or null if none did. */
    suspend fun run(packageName: String, sessionAppeared: suspend (timeoutMs: Long) -> Boolean): PlaybackSource? {
        for (source in sources) {
            val tried = try {
                source.canHandle(packageName) && source.requestPlay(packageName)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (tried && sessionAppeared(source.sessionTimeoutMs ?: stepTimeoutMs)) return source
        }
        return null
    }

    companion object {
        const val STEP_TIMEOUT_MS = 3_000L
    }
}
