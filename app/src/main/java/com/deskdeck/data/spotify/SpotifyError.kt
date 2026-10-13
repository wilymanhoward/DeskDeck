package com.deskdeck.data.spotify

/** Why a Spotify request didn't work, in terms the library screen can explain. */
sealed interface SpotifyError {
    /** No Client ID in local.properties: the feature isn't set up. */
    data object NotConfigured : SpotifyError

    /** No account connected, or its sign-in was revoked/expired for good. */
    data object SignedOut : SpotifyError

    data object Offline : SpotifyError

    /**
     * Development Mode only lets accounts listed under the app's "Users Management" in the
     * Developer Dashboard use it (plus the owner).
     */
    data object NotRegistered : SpotifyError

    /** Starting playback through the Web API needs Spotify Premium. */
    data object PremiumRequired : SpotifyError

    /** Too many requests (or the app's daily quota): wait [retryAfterSec]. */
    data class RateLimited(val retryAfterSec: Long?) : SpotifyError

    /** This tablet's Spotify didn't show up as a playback device, even after waking it. */
    data object NoDevice : SpotifyError

    /** Anything else: 5xx, an unexpected 4xx; [message] is Spotify's own text if any. */
    data class Other(val code: Int, val message: String?) : SpotifyError
}

/**
 * Maps a failed Web API response to a [SpotifyError]. 401 is not handled here: callers refresh
 * the token and retry first, and only a refresh that fails means [SpotifyError.SignedOut].
 */
fun classifyApiError(code: Int, body: ApiError?, retryAfterHeader: String?): SpotifyError {
    val message = body?.message.orEmpty()
    return when {
        code == 401 -> SpotifyError.SignedOut
        code == 429 -> SpotifyError.RateLimited(retryAfterHeader?.trim()?.toLongOrNull())
        code == 403 && body?.reason == "PREMIUM_REQUIRED" -> SpotifyError.PremiumRequired
        code == 403 && message.contains("premium", ignoreCase = true) -> SpotifyError.PremiumRequired
        // Spotify's wording: "Check settings on developer.spotify.com/dashboard, the user may not be registered."
        code == 403 && message.contains("not be registered", ignoreCase = true) -> SpotifyError.NotRegistered
        code == 403 && message.contains("developer.spotify.com/dashboard", ignoreCase = true) -> SpotifyError.NotRegistered
        code == 404 && (body?.reason == "NO_ACTIVE_DEVICE" || message.contains("device", ignoreCase = true)) -> SpotifyError.NoDevice
        else -> SpotifyError.Other(code, body?.message)
    }
}
