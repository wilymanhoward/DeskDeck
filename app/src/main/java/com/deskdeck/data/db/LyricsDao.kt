package com.deskdeck.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricsDao {

    @Query("SELECT * FROM lyrics_cache WHERE `key` = :key")
    suspend fun cached(key: String): LyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCached(entity: LyricsCacheEntity)

    @Query("DELETE FROM lyrics_cache")
    suspend fun clearCache()

    @Query("SELECT offsetMs FROM lyrics_offset WHERE `key` = :key")
    fun offset(key: String): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putOffset(entity: LyricsOffsetEntity)

    @Query("DELETE FROM lyrics_offset WHERE `key` = :key")
    suspend fun deleteOffset(key: String)

    @Query("DELETE FROM lyrics_offset")
    suspend fun clearOffsets()
}
