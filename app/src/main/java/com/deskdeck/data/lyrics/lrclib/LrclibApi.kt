package com.deskdeck.data.lyrics.lrclib

import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/** LRCLIB (https://lrclib.net): free, keyless lyrics database. */
interface LrclibApi {

    /** Exact lookup; 404 when there's no record within ±2 s of [duration]. */
    @GET("api/get")
    suspend fun get(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String?,
        @Query("duration") duration: Int,
    ): Response<LrclibTrack>

    @GET("api/search")
    suspend fun search(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String?,
    ): List<LrclibTrack>

    @GET("api/search")
    suspend fun searchFreeText(@Query("q") query: String): List<LrclibTrack>
}

@JsonClass(generateAdapter = true)
data class LrclibTrack(
    val id: Long,
    val trackName: String?,
    val artistName: String?,
    val albumName: String?,
    /** Seconds. */
    val duration: Double?,
    val instrumental: Boolean = false,
    val plainLyrics: String?,
    val syncedLyrics: String?,
)
