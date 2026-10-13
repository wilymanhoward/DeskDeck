package com.deskdeck.data.lyrics

import com.deskdeck.domain.LyricsResult
import com.deskdeck.domain.Track

interface LyricsRepository {
    suspend fun lyricsFor(track: Track, forceRefresh: Boolean = false): LyricsResult
}
