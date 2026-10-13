package com.deskdeck.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The Spotify playlist list as last fetched, so the library opens instantly (and offline). */
@Entity(tableName = "spotify_playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** `spotify:playlist:…`, what playback is started with. */
    val uri: String,
    val owner: String?,
    val imageUrl: String?,
    val trackCount: Int?,
    /** Order as Spotify returned it (the user's own order in their library). */
    val position: Int,
)

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM spotify_playlists ORDER BY position")
    fun playlists(): Flow<List<PlaylistEntity>>

    @Upsert
    suspend fun upsert(items: List<PlaylistEntity>)

    @Query("DELETE FROM spotify_playlists WHERE id NOT IN (:keep)")
    suspend fun deleteOthers(keep: List<String>)

    @Query("DELETE FROM spotify_playlists")
    suspend fun clear()

    /** Replaces the cache in one transaction, so the grid never shows a half-written list. */
    @Transaction
    suspend fun replaceAll(items: List<PlaylistEntity>) {
        if (items.isEmpty()) clear() else deleteOthers(items.map { it.id })
        upsert(items)
    }
}
