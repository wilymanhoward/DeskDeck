package com.deskdeck.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cached lookup result, including "not found" so we don't ask again on every play. */
@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey val key: String,
    val syncedLrc: String?,
    val plain: String?,
    val instrumental: Boolean,
    val notFound: Boolean,
    val source: String,
    val fetchedAt: Long,
)

/** User's manual sync correction for one track. Positive = lyrics shown earlier. */
@Entity(tableName = "lyrics_offset")
data class LyricsOffsetEntity(
    @PrimaryKey val key: String,
    val offsetMs: Long,
)
