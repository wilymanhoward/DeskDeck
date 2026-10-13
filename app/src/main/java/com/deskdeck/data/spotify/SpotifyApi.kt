package com.deskdeck.data.spotify

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Url

/** The parts of the Spotify Web API that DeskDeck uses (all available in Development Mode). */
interface SpotifyApi {

    @GET("me")
    suspend fun me(@Header("Authorization") auth: String): Response<SpotifyUser>

    /** The user's own and followed playlists, [limit] ≤ 50 per page. */
    @GET("me/playlists")
    suspend fun playlists(
        @Header("Authorization") auth: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): Response<PlaylistPage>

    @GET("me/player/devices")
    suspend fun devices(@Header("Authorization") auth: String): Response<DeviceList>

    /** Starts [PlayRequest.contextUri] on [deviceId]. Needs Premium. */
    @PUT("me/player/play")
    suspend fun play(
        @Header("Authorization") auth: String,
        @Query("device_id") deviceId: String?,
        @Body body: PlayRequest,
    ): Response<Unit>

    /** Further pages of [playlists], by the `next` URL Spotify returns. */
    @GET
    suspend fun playlistsPage(@Header("Authorization") auth: String, @Url url: String): Response<PlaylistPage>
}

/** accounts.spotify.com: code exchange and refresh for PKCE (no client secret). */
interface SpotifyAccountsApi {

    @FormUrlEncoded
    @POST("api/token")
    suspend fun exchangeCode(
        @Field("client_id") clientId: String,
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("code_verifier") codeVerifier: String,
        @Field("grant_type") grantType: String = "authorization_code",
    ): Response<TokenResponse>

    @FormUrlEncoded
    @POST("api/token")
    suspend fun refresh(
        @Field("client_id") clientId: String,
        @Field("refresh_token") refreshToken: String,
        @Field("grant_type") grantType: String = "refresh_token",
    ): Response<TokenResponse>
}

@JsonClass(generateAdapter = true)
data class TokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "expires_in") val expiresInSec: Long,
    /** PKCE refresh tokens rotate: a new one may come back with each refresh. */
    @Json(name = "refresh_token") val refreshToken: String?,
    val scope: String?,
)

@JsonClass(generateAdapter = true)
data class SpotifyUser(
    val id: String,
    @Json(name = "display_name") val displayName: String?,
)

@JsonClass(generateAdapter = true)
data class PlaylistPage(
    val items: List<PlaylistJson?> = emptyList(),
    val next: String?,
    val total: Int = 0,
)

@JsonClass(generateAdapter = true)
data class PlaylistJson(
    val id: String,
    val name: String?,
    val uri: String,
    val images: List<ImageJson>? = null,
    val owner: OwnerJson? = null,
    val tracks: TrackCountJson? = null,
)

@JsonClass(generateAdapter = true)
data class ImageJson(val url: String, val width: Int?, val height: Int?)

@JsonClass(generateAdapter = true)
data class OwnerJson(@Json(name = "display_name") val displayName: String?)

@JsonClass(generateAdapter = true)
data class TrackCountJson(val total: Int?)

@JsonClass(generateAdapter = true)
data class DeviceList(val devices: List<DeviceJson> = emptyList())

@JsonClass(generateAdapter = true)
data class DeviceJson(
    val id: String?,
    val name: String,
    val type: String,
    @Json(name = "is_active") val isActive: Boolean = false,
    @Json(name = "is_restricted") val isRestricted: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class PlayRequest(@Json(name = "context_uri") val contextUri: String)

/** Web API error body: `{"error":{"status":403,"message":"…","reason":"PREMIUM_REQUIRED"}}`. */
@JsonClass(generateAdapter = true)
data class ApiErrorBody(val error: ApiError?)

@JsonClass(generateAdapter = true)
data class ApiError(val status: Int?, val message: String?, val reason: String?)

/** Accounts error body: `{"error":"invalid_grant","error_description":"…"}`. */
@JsonClass(generateAdapter = true)
data class TokenErrorBody(val error: String?, @Json(name = "error_description") val description: String?)
