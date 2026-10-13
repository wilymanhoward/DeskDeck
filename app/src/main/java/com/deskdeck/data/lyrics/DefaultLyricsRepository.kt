package com.deskdeck.data.lyrics

import com.deskdeck.data.db.LyricsCacheEntity
import com.deskdeck.data.db.LyricsDao
import com.deskdeck.data.db.LyricsOffsetEntity
import com.deskdeck.domain.Lyrics
import com.deskdeck.domain.LyricsResult
import com.deskdeck.domain.Track
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Cache-first lyrics lookup. Hits are kept forever; "not found" is kept for [NOT_FOUND_TTL_MS] so
 * new LRCLIB entries are picked up eventually without asking on every play. Network failures are
 * never cached.
 */
@Singleton
class DefaultLyricsRepository @Inject constructor(
    private val dao: LyricsDao,
    private val providers: List<@JvmSuppressWildcards LyricsProvider>,
    private val clock: Clock,
) : LyricsRepository {

    override suspend fun lyricsFor(track: Track, forceRefresh: Boolean): LyricsResult {
        val query = TitleCleaner.queryFor(track)
        val key = cacheKey(query)
        val cached = dao.cached(key)
        val now = clock.millis()

        if (!forceRefresh && cached != null && (!cached.notFound || now - cached.fetchedAt < NOT_FOUND_TTL_MS)) {
            return cached.toResult()
        }

        var networkError: IOException? = null
        for (provider in providers) {
            try {
                val result = provider.find(query)
                if (result is ProviderResult.Found) {
                    val entity = LyricsCacheEntity(
                        key = key,
                        syncedLrc = result.syncedLrc,
                        plain = result.plain,
                        instrumental = result.instrumental,
                        notFound = false,
                        source = provider.name,
                        fetchedAt = now,
                    )
                    dao.putCached(entity)
                    return entity.toResult()
                }
            } catch (e: IOException) {
                networkError = e
            }
        }

        // A previous hit beats a failed or empty refresh.
        if (cached != null && !cached.notFound) return cached.toResult()
        if (networkError != null) return LyricsResult.Error(networkError.message)

        dao.putCached(
            LyricsCacheEntity(key, null, null, instrumental = false, notFound = true, source = "", fetchedAt = now),
        )
        return LyricsResult.NotFound
    }

    override fun offsetFor(track: Track): Flow<Long> =
        dao.offset(cacheKey(TitleCleaner.queryFor(track))).map { it ?: 0L }

    override suspend fun setOffset(track: Track, offsetMs: Long) {
        val key = cacheKey(TitleCleaner.queryFor(track))
        if (offsetMs == 0L) dao.deleteOffset(key) else dao.putOffset(LyricsOffsetEntity(key, offsetMs))
    }

    override suspend fun clearCache() = dao.clearCache()

    override suspend fun resetAllOffsets() = dao.clearOffsets()

    private fun LyricsCacheEntity.toResult(): LyricsResult {
        if (notFound) return LyricsResult.NotFound
        val lines = syncedLrc?.let(LrcParser::parse)?.let(WordTiming::complete).orEmpty()
        val lyrics = when {
            lines.isNotEmpty() -> Lyrics.Synced(lines)
            !plain.isNullOrBlank() -> Lyrics.Plain(plain.trim())
            instrumental -> Lyrics.Instrumental
            else -> return LyricsResult.NotFound
        }
        return LyricsResult.Found(lyrics, source)
    }

    companion object {
        const val NOT_FOUND_TTL_MS = 3L * 24 * 60 * 60 * 1000

        /** Normalized "artist|title|seconds", so the same song shares a cache entry across players. */
        fun cacheKey(query: LyricsQuery): String = listOf(
            TitleCleaner.normalize(query.primaryArtist.orEmpty()),
            TitleCleaner.normalize(query.title),
            query.durationSec?.toString().orEmpty(),
        ).joinToString("|")
    }
}
