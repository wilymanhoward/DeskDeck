package com.deskdeck.data.fake

import com.deskdeck.data.lyrics.LyricsRepository
import com.deskdeck.domain.LyricsResult
import com.deskdeck.domain.Track
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

@Singleton
class FakeLyricsRepository @Inject constructor() : LyricsRepository {
    override suspend fun lyricsFor(track: Track, forceRefresh: Boolean): LyricsResult {
        delay(400) // Long enough to see the loading state.
        val lyrics = FakeData.lyricsFor(track.id) ?: return LyricsResult.NotFound
        return LyricsResult.Found(lyrics, source = "Sample")
    }
}
