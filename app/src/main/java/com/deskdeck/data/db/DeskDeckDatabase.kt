package com.deskdeck.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [LyricsCacheEntity::class, LyricsOffsetEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class DeskDeckDatabase : RoomDatabase() {
    abstract fun lyricsDao(): LyricsDao
}
