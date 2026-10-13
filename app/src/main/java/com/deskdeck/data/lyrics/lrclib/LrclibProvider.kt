package com.deskdeck.data.lyrics.lrclib

import com.deskdeck.data.lyrics.LyricMatcher
import com.deskdeck.data.lyrics.LyricsCandidate
import com.deskdeck.data.lyrics.LyricsProvider
import com.deskdeck.data.lyrics.LyricsQuery
import com.deskdeck.data.lyrics.ProviderResult
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

/**
 * LRCLIB lookup: the exact `/api/get` first (needs artist + duration), then `/api/search` by
 * fields, then free-text search, picking the best result with [LyricMatcher].
 */
@Singleton
class LrclibProvider @Inject constructor(
    private val api: LrclibApi,
) : LyricsProvider {

    override val name = "LRCLIB"

    override suspend fun find(query: LyricsQuery): ProviderResult {
        exactMatch(query)?.let { return it }

        val byFields = search { api.search(query.title, query.primaryArtist) }
        LyricMatcher.best(query, byFields.map { it.toCandidate() })?.let { return it.payload.toResult() }

        val freeText = listOfNotNull(query.primaryArtist, query.title).joinToString(" ")
        val byText = search { api.searchFreeText(freeText) }
        LyricMatcher.best(query, byText.map { it.toCandidate() })?.let { return it.payload.toResult() }

        return ProviderResult.NotFound
    }

    private suspend fun exactMatch(query: LyricsQuery): ProviderResult? {
        val artist = query.artist ?: return null
        val duration = query.durationSec ?: return null
        val response = api.get(query.title, artist, query.album, duration)
        // 404: no exact record. Other non-2xx (e.g. 400 for odd input): let search try instead.
        if (!response.isSuccessful) return null
        val track = response.body() ?: return null
        if (track.syncedLyrics.isNullOrBlank() && track.plainLyrics.isNullOrBlank() && !track.instrumental) return null
        return track.toResult()
    }

    private suspend fun search(call: suspend () -> List<LrclibTrack>): List<LrclibTrack> = try {
        call()
    } catch (e: HttpException) {
        if (e.code() == 404) emptyList() else throw IOException("LRCLIB error ${e.code()}", e)
    }

    private fun LrclibTrack.toCandidate() = LyricsCandidate(
        title = trackName,
        artist = artistName,
        durationSec = duration,
        hasSynced = !syncedLyrics.isNullOrBlank(),
        hasPlain = !plainLyrics.isNullOrBlank(),
        instrumental = instrumental,
        payload = this,
    )

    private fun LrclibTrack.toResult() = ProviderResult.Found(
        syncedLrc = syncedLyrics?.takeIf { it.isNotBlank() },
        plain = plainLyrics?.takeIf { it.isNotBlank() },
        instrumental = instrumental,
    )
}
