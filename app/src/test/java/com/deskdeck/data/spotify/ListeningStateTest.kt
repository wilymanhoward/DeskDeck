package com.deskdeck.data.spotify

import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningStateTest {
    @Test
    fun `playing here needs no network`() {
        assertEquals(true, knownPlaying(playingHere = true, spotifyHere = true, libraryOn = true, signedIn = true))
    }

    @Test
    fun `spotify paused here with the web api connected asks spotify`() {
        // Spotify Connect: the tablet's session says paused while a laptop plays.
        assertEquals(null, knownPlaying(playingHere = false, spotifyHere = true, libraryOn = true, signedIn = true))
    }

    @Test
    fun `without spotify here or without the web api it is silence, as before`() {
        assertEquals(false, knownPlaying(playingHere = false, spotifyHere = false, libraryOn = true, signedIn = true))
        assertEquals(false, knownPlaying(playingHere = false, spotifyHere = true, libraryOn = false, signedIn = true))
        assertEquals(false, knownPlaying(playingHere = false, spotifyHere = true, libraryOn = true, signedIn = false))
    }
}
