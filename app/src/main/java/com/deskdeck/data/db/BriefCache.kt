package com.deskdeck.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The last Morning Brief, as JSON (one row), so it shows instantly and offline with its age. */
@Entity(tableName = "brief_cache")
data class BriefCacheEntity(
    @PrimaryKey val key: String,
    val json: String,
    val fetchedAt: Long,
)

@Dao
interface BriefDao {
    @Query("SELECT * FROM brief_cache WHERE `key` = :key")
    fun observe(key: String): Flow<BriefCacheEntity?>

    @Query("SELECT * FROM brief_cache WHERE `key` = :key")
    suspend fun get(key: String): BriefCacheEntity?

    @Upsert
    suspend fun put(entity: BriefCacheEntity)
}
