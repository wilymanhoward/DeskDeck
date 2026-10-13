package com.deskdeck.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [LyricsCacheEntity::class, LyricsOffsetEntity::class, PlaylistEntity::class],
    version = 2,
    exportSchema = true,
    // 2: the Spotify playlist cache (a new table; lyrics data is untouched).
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class DeskDeckDatabase : RoomDatabase() {
    abstract fun lyricsDao(): LyricsDao
    abstract fun playlistDao(): PlaylistDao
}
