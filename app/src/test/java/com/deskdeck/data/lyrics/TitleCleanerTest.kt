package com.deskdeck.data.lyrics

import com.deskdeck.domain.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleCleanerTest {

    private fun clean(title: String) = TitleCleaner.cleanTitle(title)

    @Test
    fun `strips remaster and version suffixes`() {
        assertEquals("Here Comes the Sun", clean("Here Comes the Sun (Remastered 2009)"))
        assertEquals("Bohemian Rhapsody", clean("Bohemian Rhapsody - Remastered 2011"))
        assertEquals("Heroes", clean("Heroes - 2017 Remaster"))
        assertEquals("Song", clean("Song - Single Version"))
        assertEquals("Song", clean("Song - Radio Edit"))
        assertEquals("Song", clean("Song - Live - 2011 Remaster"))
    }

    @Test
    fun `strips live and from-movie suffixes`() {
        assertEquals("Hotel California", clean("Hotel California - Live"))
        assertEquals("Hotel California", clean("Hotel California (Live at the Forum, 1976)"))
        assertEquals("Let It Go", clean("Let It Go - From \"Frozen\""))
    }

    @Test
    fun `strips featured artists`() {
        assertEquals("Stay", clean("Stay (feat. Justin Bieber)"))
        assertEquals("Stay", clean("Stay [ft. Someone]"))
        assertEquals("Stay", clean("Stay feat. Justin Bieber"))
        assertEquals("Stay", clean("Stay (with Someone)"))
    }

    @Test
    fun `strips video platform noise`() {
        assertEquals("Song", clean("Song (Official Video)"))
        assertEquals("Song", clean("Song [Official Music Video] (HD)"))
        assertEquals("Song", clean("Song (Lyrics)"))
        assertEquals("Song", clean("Song | Official Audio"))
        assertEquals("Song", clean("Song (Visualizer)"))
        assertEquals("Song", clean("Song【MV】"))
    }

    @Test
    fun `keeps meaningful brackets and words that only contain noise words`() {
        assertEquals("Stayin' Alive", clean("Stayin' Alive"))
        assertEquals("(I Can't Get No) Satisfaction", clean("(I Can't Get No) Satisfaction"))
        assertEquals("Song (Stayin' Alive)", clean("Song (Stayin' Alive)"))
        assertEquals("Song (Remix)", clean("Song (Remix)"))
        assertEquals("Livewire", clean("Livewire"))
    }

    @Test
    fun `cleans channel style artists`() {
        assertEquals("Adele", TitleCleaner.cleanArtist("AdeleVEVO"))
        assertEquals("Adele", TitleCleaner.cleanArtist("Adele - Topic"))
        assertEquals("Adele", TitleCleaner.cleanArtist("Adele Official"))
    }

    @Test
    fun `primary artist is the first credited one but keeps ampersand bands`() {
        assertEquals("Justin Bieber", TitleCleaner.primaryArtist("Justin Bieber, Jaden"))
        assertEquals("Calvin Harris", TitleCleaner.primaryArtist("Calvin Harris feat. Rihanna"))
        assertEquals("Simon & Garfunkel", TitleCleaner.primaryArtist("Simon & Garfunkel"))
    }

    @Test
    fun `splits artist and title only on a real separator`() {
        assertEquals("Adele" to "Hello", TitleCleaner.splitArtistTitle("Adele - Hello"))
        assertNull(TitleCleaner.splitArtistTitle("Hello - Live"))
        assertNull(TitleCleaner.splitArtistTitle("Hello"))
    }

    @Test
    fun `query from a youtube video`() {
        val track = Track(
            id = "yt|x",
            title = "Adele - Hello (Official Music Video)",
            artist = "AdeleVEVO",
            album = null,
            durationMs = 367_400,
        )
        val q = TitleCleaner.queryFor(track)
        assertEquals("Hello", q.title)
        assertEquals("Adele", q.artist)
        assertEquals("Adele", q.primaryArtist)
        assertEquals(367, q.durationSec)
    }

    @Test
    fun `query keeps a dashed title when the artist is unrelated`() {
        val track = Track(id = "s|x", title = "Part One - The Beginning", artist = "Band", album = "LP", durationMs = 200_000)
        val q = TitleCleaner.queryFor(track)
        assertEquals("Part One - The Beginning", q.title)
        assertEquals("Band", q.artist)
    }

    @Test
    fun `query from spotify style metadata`() {
        val track = Track(
            id = "s|x",
            title = "Never Say Never",
            artist = "Justin Bieber, Jaden",
            album = "Never Say Never - The Remixes",
            durationMs = 228_500,
        )
        val q = TitleCleaner.queryFor(track)
        assertEquals("Never Say Never", q.title)
        assertEquals("Justin Bieber, Jaden", q.artist)
        assertEquals("Justin Bieber", q.primaryArtist)
        assertEquals(229, q.durationSec)
    }

    @Test
    fun `normalize removes accents, punctuation and case`() {
        assertEquals("beyonce halo", TitleCleaner.normalize("Beyoncé – Halo!"))
        assertEquals("simon and garfunkel", TitleCleaner.normalize("Simon & Garfunkel"))
        assertEquals("미안해", TitleCleaner.normalize("미안해"))
    }
}
