package com.deskdeck.data.spotify

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import com.deskdeck.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The connected Spotify account, as Settings and the library show it. */
sealed interface SpotifyAccount {
    data object NotConfigured : SpotifyAccount
    data object SignedOut : SpotifyAccount
    data object SigningIn : SpotifyAccount
    data class SignedIn(val displayName: String?) : SpotifyAccount
    data class Failed(val error: SpotifyError) : SpotifyAccount
}

/**
 * Spotify sign-in with Authorization Code + PKCE: no client secret in the app, only the Client ID
 * from local.properties. The authorize page opens in a Custom Tab over DeskDeck, and Spotify sends
 * the browser back to [REDIRECT_URI], which DeskDeck's activity receives. Tokens are kept
 * encrypted by [TokenStore] and refreshed automatically shortly before they expire.
 */
@Singleton
class SpotifyAuth @Inject constructor(
    @ApplicationContext context: Context,
    private val store: TokenStore,
    private val accounts: SpotifyAccountsApi,
) {
    val clientId: String = BuildConfig.SPOTIFY_CLIENT_ID
    val isConfigured: Boolean get() = clientId.isNotBlank()

    /** The pending sign-in survives DeskDeck being killed while the Custom Tab is open. */
    private val pending = context.getSharedPreferences("spotify_pending_auth", Context.MODE_PRIVATE)
    private val refreshLock = Mutex()
    private var tokens: SpotifyTokens? = store.load()

    private val _account = MutableStateFlow(initialAccount())
    val account: StateFlow<SpotifyAccount> = _account.asStateFlow()

    val userId: String? get() = tokens?.userId

    private fun initialAccount(): SpotifyAccount = when {
        !isConfigured -> SpotifyAccount.NotConfigured
        tokens != null -> SpotifyAccount.SignedIn(tokens?.displayName)
        else -> SpotifyAccount.SignedOut
    }

    /** The authorize URL to open; remembers the verifier and state for [completeSignIn]. */
    fun beginSignIn(): Uri {
        val verifier = Pkce.newVerifier()
        val state = Pkce.newState()
        pending.edit {
            putString(KEY_VERIFIER, verifier)
            putString(KEY_STATE, state)
        }
        _account.value = SpotifyAccount.SigningIn
        return "https://accounts.spotify.com/authorize".toUri().buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", Pkce.challenge(verifier))
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", SCOPES.joinToString(" "))
            .build()
    }

    fun isRedirect(uri: Uri?): Boolean = uri != null && uri.toString().startsWith(REDIRECT_URI)

    /**
     * Finishes sign-in from the redirect: checks state, exchanges the code, and loads the
     * profile through [loadProfile] (the library repository's `/me` call).
     */
    suspend fun completeSignIn(redirect: Uri, loadProfile: suspend (String) -> SpotifyUser?) {
        val verifier = pending.getString(KEY_VERIFIER, null)
        val expectedState = pending.getString(KEY_STATE, null)
        pending.edit { clear() }
        val code = redirect.getQueryParameter("code")
        if (verifier == null || code == null || redirect.getQueryParameter("state") != expectedState) {
            // Cancelled ("error=access_denied"), or a redirect we didn't start.
            _account.value = if (tokens != null) SpotifyAccount.SignedIn(tokens?.displayName) else SpotifyAccount.SignedOut
            return
        }
        try {
            val response = accounts.exchangeCode(clientId, code, REDIRECT_URI, verifier)
            val body = response.body()
            if (!response.isSuccessful || body?.refreshToken == null) {
                _account.value = SpotifyAccount.Failed(SpotifyError.Other(response.code(), "Sign-in was refused"))
                return
            }
            var signedIn = SpotifyTokens(body.accessToken, body.refreshToken, expiry(body.expiresInSec))
            val user = loadProfile(signedIn.accessToken)
            signedIn = signedIn.copy(userId = user?.id, displayName = user?.displayName)
            tokens = signedIn
            store.save(signedIn)
            _account.value = SpotifyAccount.SignedIn(signedIn.displayName)
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            _account.value = SpotifyAccount.Failed(SpotifyError.Offline)
        }
    }

    /** Reports a problem found while using the account (e.g. not registered in the dashboard). */
    fun reportProblem(error: SpotifyError) {
        if (error == SpotifyError.NotRegistered) _account.value = SpotifyAccount.Failed(error)
    }

    /**
     * A usable access token: the stored one, or a refreshed one if it's about to expire or
     * [forceRefresh] (after a 401). Null when signed out or the refresh token was revoked.
     * @throws IOException when offline.
     */
    suspend fun accessToken(forceRefresh: Boolean = false): String? = refreshLock.withLock {
        val current = tokens ?: return null
        if (!forceRefresh && System.currentTimeMillis() < current.expiresAtMs - REFRESH_MARGIN_MS) return current.accessToken
        val response = accounts.refresh(clientId, current.refreshToken)
        val body = response.body()
        if (!response.isSuccessful || body == null) {
            // invalid_grant: the user revoked access or the token is too old. Sign in again.
            if (response.code() in 400..499) signOut()
            return null
        }
        val refreshed = current.copy(
            accessToken = body.accessToken,
            refreshToken = body.refreshToken ?: current.refreshToken,
            expiresAtMs = expiry(body.expiresInSec),
        )
        tokens = refreshed
        store.save(refreshed)
        refreshed.accessToken
    }

    fun signOut() {
        tokens = null
        store.clear()
        _account.value = if (isConfigured) SpotifyAccount.SignedOut else SpotifyAccount.NotConfigured
    }

    private fun expiry(expiresInSec: Long) = System.currentTimeMillis() + expiresInSec * 1_000

    companion object {
        /** Must match a Redirect URI in the app's settings on developer.spotify.com exactly. */
        const val REDIRECT_URI = "deskdeck://spotify-callback"
        val SCOPES = listOf(
            "playlist-read-private",
            "playlist-read-collaborative",
            "user-library-read",
            "user-read-playback-state",
            "user-modify-playback-state",
        )
        private const val KEY_VERIFIER = "verifier"
        private const val KEY_STATE = "state"
        private const val REFRESH_MARGIN_MS = 60_000L
    }
}
