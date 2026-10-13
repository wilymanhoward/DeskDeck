package com.deskdeck.data.spotify

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.deskdeck.data.db.PlaylistDao
import com.deskdeck.data.db.PlaylistEntity
import com.deskdeck.media.sources.MediaButtonSource
import com.deskdeck.media.sources.SessionKeySource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.json.JSONObject
import retrofit2.Response

/** A tile in the library grid: a playlist, or the user's Liked Songs. */
data class LibraryItem(
    val id: String,
    val name: String,
    val subtitle: String?,
    val imageUrl: String?,
    /** What playback is started with. */
    val contextUri: String,
    val isLikedSongs: Boolean = false,
)

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Failed(val error: SpotifyError) : ApiResult<Nothing>
}

/**
 * The Spotify library: the playlist list (cached in Room, refreshed in the background) and
 * starting a playlist on *this tablet's* Spotify app, all without bringing Spotify to the front.
 */
@Singleton
class SpotifyLibrary @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: SpotifyApi,
    private val auth: SpotifyAuth,
    private val dao: PlaylistDao,
    private val sessionKey: SessionKeySource,
    private val mediaButton: MediaButtonSource,
) {
    private val devicePrefs = context.getSharedPreferences("spotify_device", Context.MODE_PRIVATE)

    val items: Flow<List<LibraryItem>> = combine(dao.playlists(), auth.account) { playlists, _ ->
        val liked = auth.userId?.let { userId ->
            LibraryItem(LIKED_ID, "", subtitle = null, imageUrl = null, contextUri = "spotify:user:$userId:collection", isLikedSongs = true)
        }
        listOfNotNull(liked) + playlists.map {
            LibraryItem(it.id, it.name, it.owner, it.imageUrl, it.uri)
        }
    }

    /** `/me`, for sign-in. */
    suspend fun loadProfile(accessToken: String): SpotifyUser? = runCatching {
        api.me("Bearer $accessToken").body()
    }.getOrNull()

    /** Fetches all pages of the user's playlists and replaces the cache. */
    suspend fun refresh(): SpotifyError? {
        val all = mutableListOf<PlaylistJson>()
        var page = when (val first = call { api.playlists(it) }) {
            is ApiResult.Ok -> first.value
            is ApiResult.Failed -> return first.error
        }
        while (true) {
            all += page.items.filterNotNull()
            val next = page.next ?: break
            page = when (val more = call { api.playlistsPage(it, next) }) {
                is ApiResult.Ok -> more.value
                is ApiResult.Failed -> return more.error
            }
        }
        dao.replaceAll(
            all.mapIndexed { i, p ->
                PlaylistEntity(
                    id = p.id,
                    name = p.name.orEmpty(),
                    uri = p.uri,
                    owner = p.owner?.displayName,
                    // Spotify lists the largest image first; the grid tiles are small.
                    imageUrl = p.images?.minByOrNull { kotlin.math.abs((it.width ?: 640) - TILE_IMAGE_PX) }?.url,
                    trackCount = p.tracks?.total,
                    position = i,
                )
            },
        )
        return null
    }

    /**
     * Starts [item] on this tablet. If the tablet's Spotify isn't a known device yet (Spotify not
     * running, or idle), it's woken in the background — the same media-button ladder the rail
     * uses — and asked again for a few seconds. [onWaking] lets the UI say "Starting Spotify…".
     */
    suspend fun play(item: LibraryItem, onWaking: () -> Unit = {}): SpotifyError? {
        var device = when (val found = findThisDevice()) {
            is ApiResult.Ok -> found.value
            is ApiResult.Failed -> return found.error
        }
        if (device == null) {
            onWaking()
            wakeSpotify()
            device = waitForDevice() ?: return SpotifyError.NoDevice
        }
        return when (val started = call { api.play(it, device, PlayRequest(item.contextUri)) }) {
            is ApiResult.Ok -> null
            is ApiResult.Failed -> {
                if (started.error != SpotifyError.NoDevice) return started.error
                // The device went to sleep between listing and playing: wake it and try once more.
                onWaking()
                wakeSpotify()
                val again = waitForDevice() ?: return SpotifyError.NoDevice
                (call { api.play(it, again, PlayRequest(item.contextUri)) } as? ApiResult.Failed)?.error
            }
        }
    }

    private suspend fun waitForDevice(): String? {
        repeat(WAKE_POLLS) {
            delay(WAKE_POLL_MS)
            (findThisDevice() as? ApiResult.Ok)?.value?.let { return it }
        }
        return null
    }

    private suspend fun wakeSpotify() {
        if (!sessionKey.requestPlay(SPOTIFY_PACKAGE) && mediaButton.canHandle(SPOTIFY_PACKAGE)) {
            mediaButton.requestPlay(SPOTIFY_PACKAGE)
        }
    }

    /** This tablet among the account's Spotify Connect devices, by remembered id, name or type. */
    private suspend fun findThisDevice(): ApiResult<String?> {
        val devices = when (val list = call { api.devices(it) }) {
            is ApiResult.Ok -> list.value.devices.filter { it.id != null && !it.isRestricted }
            is ApiResult.Failed -> return list
        }
        val remembered = devicePrefs.getString(KEY_DEVICE_ID, null)
        val names = setOfNotNull(
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME),
            Build.MODEL,
        ).map { it.lowercase() }
        val match = devices.firstOrNull { it.id == remembered }
            ?: devices.firstOrNull { it.name.lowercase() in names }
            ?: devices.singleOrNull { it.type.equals("Tablet", ignoreCase = true) }
        match?.id?.let { devicePrefs.edit().putString(KEY_DEVICE_ID, it).apply() }
        return ApiResult.Ok(match?.id)
    }

    suspend fun signOut() {
        auth.signOut()
        dao.clear()
        devicePrefs.edit().clear().apply()
    }

    /** One authorized call: refreshes and retries once on 401, and maps failures to [SpotifyError]. */
    private suspend fun <T> call(request: suspend (auth: String) -> Response<T>): ApiResult<T> = try {
        val token = auth.accessToken() ?: return ApiResult.Failed(signedOutOrUnconfigured())
        var response = request("Bearer $token")
        if (response.code() == 401) {
            val fresh = auth.accessToken(forceRefresh = true) ?: return ApiResult.Failed(signedOutOrUnconfigured())
            response = request("Bearer $fresh")
        }
        val body = response.body()
        when {
            response.isSuccessful && body != null -> ApiResult.Ok(body)
            // 204 No Content (play) has no body.
            response.isSuccessful -> @Suppress("UNCHECKED_CAST") (ApiResult.Ok(Unit) as ApiResult<T>)
            else -> ApiResult.Failed(
                classifyApiError(response.code(), parseError(response.errorBody()?.string()), response.headers()["Retry-After"])
                    .also(auth::reportProblem),
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: IOException) {
        ApiResult.Failed(SpotifyError.Offline)
    }

    private fun signedOutOrUnconfigured() = if (auth.isConfigured) SpotifyError.SignedOut else SpotifyError.NotConfigured

    private fun parseError(json: String?): ApiError? = runCatching {
        val error = JSONObject(json ?: return null).optJSONObject("error") ?: return null
        ApiError(error.optInt("status"), error.optString("message").ifEmpty { null }, error.optString("reason").ifEmpty { null })
    }.getOrNull()

    companion object {
        const val SPOTIFY_PACKAGE = "com.spotify.music"
        const val LIKED_ID = "liked-songs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val TILE_IMAGE_PX = 300
        private const val WAKE_POLLS = 8
        private const val WAKE_POLL_MS = 1_000L
    }
}
