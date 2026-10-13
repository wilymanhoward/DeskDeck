package com.deskdeck.data.lyrics

import com.deskdeck.domain.LyricsResult
import com.deskdeck.domain.Track
import kotlinx.coroutines.flow.Flow

interface LyricsRepository {
    suspend fun lyricsFor(track: Track, forceRefresh: Boolean = false): LyricsResult

    /** The user's sync correction for [track] in ms (positive = lyrics earlier), 0 when unset. */
    fun offsetFor(track: Track): Flow<Long>

    suspend fun setOffset(track: Track, offsetMs: Long)

    suspend fun clearCache()

    suspend fun resetAllOffsets()
}
