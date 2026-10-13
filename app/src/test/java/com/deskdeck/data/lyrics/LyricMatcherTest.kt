package com.deskdeck.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricMatcherTest {

    private val query = LyricsQuery(
        title = "Never Say Never",
        artist = "Justin Bieber, Jaden",
        primaryArtist = "Justin Bieber",
        album = null,
        durationSec = 229,
    )

    private fun candidate(
        id: String,
        title: String = "Never Say Never",
        artist: String = "Justin Bieber",
        duration: Double? = 229.0,
        synced: Boolean = true,
        plain: Boolean = true,
        instrumental: Boolean = false,
    ) = LyricsCandidate(title, artist, duration, synced, plain, instrumental, id)

    @Test
    fun `exact match wins`() {
        val best = LyricMatcher.best(query, listOf(candidate("other", title = "Baby"), candidate("exact")))
        assertEquals("exact", best?.payload)
    }

    @Test
    fun `artist with featured credits still matches`() {
        val best = LyricMatcher.best(query, listOf(candidate("feat", artist = "Justin Bieber feat. Jaden Smith")))
        assertEquals("feat", best?.payload)
    }

    @Test
    fun `candidate title noise is cleaned before comparing`() {
        val best = LyricMatcher.best(query, listOf(candidate("noisy", title = "Never Say Never (feat. Jaden Smith)")))
        assertEquals("noisy", best?.payload)
    }

    @Test
    fun `closer duration wins between otherwise equal results`() {
        val best = LyricMatcher.best(
            query,
            listOf(candidate("far", duration = 240.0), candidate("close", duration = 230.0)),
        )
        assertEquals("close", best?.payload)
    }

    @Test
    fun `results far off in duration are rejected`() {
        assertNull(LyricMatcher.best(query, listOf(candidate("extended", duration = 400.0))))
    }

    @Test
    fun `wrong artist is rejected`() {
        assertNull(LyricMatcher.best(query, listOf(candidate("cover", artist = "Some Cover Band"))))
    }

    @Test
    fun `different song is rejected`() {
        assertNull(LyricMatcher.best(query, listOf(candidate("baby", title = "Baby"))))
    }

    @Test
    fun `synced lyrics win a near tie`() {
        val best = LyricMatcher.best(
            query,
            listOf(candidate("plain", synced = false), candidate("synced", synced = true)),
        )
        assertEquals("synced", best?.payload)
    }

    @Test
    fun `results without any lyrics are skipped`() {
        assertNull(LyricMatcher.best(query, listOf(candidate("empty", synced = false, plain = false))))
    }

    @Test
    fun `instrumental results without text are acceptable`() {
        val best = LyricMatcher.best(query, listOf(candidate("inst", synced = false, plain = false, instrumental = true)))
        assertEquals("inst", best?.payload)
    }

    @Test
    fun `unknown duration still matches on title and artist`() {
        val noDuration = query.copy(durationSec = null)
        assertEquals("x", LyricMatcher.best(noDuration, listOf(candidate("x", duration = null)))?.payload)
    }

    @Test
    fun `similarity basics`() {
        assertEquals(1.0, LyricMatcher.similarity("abc", "abc"), 0.0)
        assertEquals(0.0, LyricMatcher.similarity("", "abc"), 0.0)
        assertTrue(LyricMatcher.similarity("never say never", "never say neve") > 0.9)
        assertTrue(LyricMatcher.similarity("hello", "world") < 0.5)
    }
}
