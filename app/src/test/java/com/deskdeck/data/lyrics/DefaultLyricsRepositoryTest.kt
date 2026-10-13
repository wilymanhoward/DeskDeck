package com.deskdeck.data.lyrics

import com.deskdeck.data.db.LyricsCacheEntity
import com.deskdeck.data.db.LyricsDao
import com.deskdeck.data.db.LyricsOffsetEntity
import com.deskdeck.domain.Lyrics
import com.deskdeck.domain.LyricsResult
import com.deskdeck.domain.Track
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultLyricsRepositoryTest {

    private class FakeDao : LyricsDao {
        val cache = mutableMapOf<String, LyricsCacheEntity>()
        val offsets = MutableStateFlow<Map<String, Long>>(emptyMap())
        override suspend fun cached(key: String) = cache[key]
        override suspend fun putCached(entity: LyricsCacheEntity) { cache[entity.key] = entity }
        override suspend fun clearCache() = cache.clear()
        override fun offset(key: String): Flow<Long?> = offsets.map { it[key] }
        override suspend fun putOffset(entity: LyricsOffsetEntity) { offsets.value += entity.key to entity.offsetMs }
        override suspend fun deleteOffset(key: String) { offsets.value -= key }
        override suspend fun clearOffsets() { offsets.value = emptyMap() }
    }

    private class FakeProvider(var next: () -> ProviderResult) : LyricsProvider {
        var calls = 0
        override val name = "Fake"
        override suspend fun find(query: LyricsQuery): ProviderResult {
            calls++
            return next()
        }
    }

    private val track = Track(id = "p|t|a", title = "Title", artist = "Artist", album = null, durationMs = 200_000)
    private var now = 1_000_000L
    private val clock get() = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)

    private fun repo(dao: LyricsDao, provider: LyricsProvider) = DefaultLyricsRepository(dao, listOf(provider), clock)

    @Test
    fun `synced lyrics are parsed and cached`() = runTest {
        val dao = FakeDao()
        val provider = FakeProvider { ProviderResult.Found("[00:01.00]Hi", null, false) }

        val first = repo(dao, provider).lyricsFor(track)
        val second = repo(dao, provider).lyricsFor(track)

        val lyrics = (first as LyricsResult.Found).lyrics as Lyrics.Synced
        assertEquals("Hi", lyrics.lines.single().text)
        assertEquals(first, second)
        assertEquals(1, provider.calls)
    }

    @Test
    fun `plain lyrics are used when there is no synced version`() = runTest {
        val result = repo(FakeDao(), FakeProvider { ProviderResult.Found(null, " Words ", false) }).lyricsFor(track)
        assertEquals(Lyrics.Plain("Words"), (result as LyricsResult.Found).lyrics)
    }

    @Test
    fun `instrumental is reported`() = runTest {
        val result = repo(FakeDao(), FakeProvider { ProviderResult.Found(null, null, true) }).lyricsFor(track)
        assertEquals(Lyrics.Instrumental, (result as LyricsResult.Found).lyrics)
    }

    @Test
    fun `not found is cached until the TTL expires`() = runTest {
        val dao = FakeDao()
        val provider = FakeProvider { ProviderResult.NotFound }

        assertEquals(LyricsResult.NotFound, repo(dao, provider).lyricsFor(track))
        assertEquals(LyricsResult.NotFound, repo(dao, provider).lyricsFor(track))
        assertEquals(1, provider.calls)

        now += DefaultLyricsRepository.NOT_FOUND_TTL_MS + 1
        repo(dao, provider).lyricsFor(track)
        assertEquals(2, provider.calls)
    }

    @Test
    fun `force refresh bypasses a cached not found`() = runTest {
        val dao = FakeDao()
        val provider = FakeProvider { ProviderResult.NotFound }
        repo(dao, provider).lyricsFor(track)
        provider.next = { ProviderResult.Found("[00:01.00]Now", null, false) }

        val result = repo(dao, provider).lyricsFor(track, forceRefresh = true)
        assertTrue(result is LyricsResult.Found)
    }

    @Test
    fun `network errors are reported and not cached`() = runTest {
        val dao = FakeDao()
        val provider = FakeProvider { throw IOException("offline") }

        assertTrue(repo(dao, provider).lyricsFor(track) is LyricsResult.Error)
        assertTrue(dao.cache.isEmpty())
    }

    @Test
    fun `a cached hit survives a failed refresh`() = runTest {
        val dao = FakeDao()
        val provider = FakeProvider { ProviderResult.Found(null, "Words", false) }
        repo(dao, provider).lyricsFor(track)
        provider.next = { throw IOException("offline") }

        val result = repo(dao, provider).lyricsFor(track, forceRefresh = true)
        assertEquals(Lyrics.Plain("Words"), (result as LyricsResult.Found).lyrics)
    }

    @Test
    fun `offset is stored per track and zero removes it`() = runTest {
        val dao = FakeDao()
        val repo = repo(dao, FakeProvider { ProviderResult.NotFound })

        assertEquals(0L, repo.offsetFor(track).first())
        repo.setOffset(track, 300)
        assertEquals(300L, repo.offsetFor(track).first())
        repo.setOffset(track, 0)
        assertEquals(0L, repo.offsetFor(track).first())
        assertTrue(dao.offsets.value.isEmpty())
    }

    @Test
    fun `cache key ignores player and featured artists`() {
        val spotify = TitleCleaner.queryFor(track.copy(id = "spotify|x", artist = "Artist, Guest"))
        val youtube = TitleCleaner.queryFor(track.copy(id = "yt|x", title = "Title (Official Video)"))
        assertEquals(DefaultLyricsRepository.cacheKey(spotify), DefaultLyricsRepository.cacheKey(youtube))
    }
}
