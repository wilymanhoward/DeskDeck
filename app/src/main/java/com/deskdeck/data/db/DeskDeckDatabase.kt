package com.deskdeck.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [LyricsCacheEntity::class, LyricsOffsetEntity::class, PlaylistEntity::class, BriefCacheEntity::class],
    version = 3,
    exportSchema = true,
    // 2: the Spotify playlist cache (a new table; lyrics data is untouched).
    // 3: the Morning Brief cache (a new table).
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class DeskDeckDatabase : RoomDatabase() {
    abstract fun lyricsDao(): LyricsDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun briefDao(): BriefDao
}
