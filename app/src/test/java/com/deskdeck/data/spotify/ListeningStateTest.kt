package com.deskdeck.data.spotify

import org.junit.Assert.assertEquals
import org.junit.Test


class RemotePlaybackTest {
    private val spotify = SpotifyLibrary.SPOTIFY_PACKAGE

    private fun session(title: String, playing: Boolean) = com.deskdeck.domain.NowPlaying(
        track = com.deskdeck.domain.Track(id = "$spotify|$title|Olivia Rodrigo", title = title, artist = "Olivia Rodrigo", album = null, durationMs = 180_000),
        playback = com.deskdeck.domain.PlaybackInfo(
            status = if (playing) com.deskdeck.domain.PlaybackStatus.PLAYING else com.deskdeck.domain.PlaybackStatus.PAUSED,
            positionMs = 3_000,
            updatedAtElapsedMs = 0,
        ),
        sourcePackage = spotify,
        sourceLabel = "Spotify",
    )

    private fun remote(title: String, playing: Boolean = true) = RemotePlayback(
        isPlaying = playing, progressMs = 61_000, atElapsedMs = 5_000, title = title, artist = "Olivia Rodrigo",
        album = "GUTS", durationMs = 200_000, imageUrl = null, deviceName = "HOWARD-LAPTOP",
    )

    @Test
    fun `a stale paused spotify session follows the laptop's song and position`() {
        val np = withRemote(session("purple", playing = false), remote("vampire"), art = null)!!
        assertEquals("vampire", np.track.title)
        assertEquals(com.deskdeck.domain.PlaybackStatus.PLAYING, np.playback.status)
        // Extrapolated from Spotify's reading, so the lyrics move.
        assertEquals(71_000L, np.playback.positionAt(15_000, np.track.durationMs))
    }

    @Test
    fun `the same song keeps its id, so lyrics and art aren't fetched again`() {
        val np = withRemote(session("purple", playing = false), remote("purple"), art = null)!!
        assertEquals("$spotify|purple|Olivia Rodrigo", np.track.id)
    }

    @Test
    fun `a session that is really playing here wins`() {
        val here = session("purple", playing = true)
        assertEquals(here, withRemote(here, remote("vampire"), art = null))
        assertEquals(null, withRemote(null, remote("vampire"), art = null))
        // Spotify says it plays on this tablet: its own session is the better source.
        val paused = session("purple", playing = false)
        assertEquals(paused, withRemote(paused, remote("vampire").copy(onThisDevice = true), art = null))
    }

    @Test
    fun `polls every 30 s, or just after the song ends`() {
        assertEquals(30_000L, nextPollMs(null))
        assertEquals(30_000L, nextPollMs(remote("x").copy(progressMs = 10_000)))
        assertEquals(6_500L, nextPollMs(remote("x").copy(progressMs = 195_000)))
        assertEquals(3_000L, nextPollMs(remote("x").copy(progressMs = 199_900)))
        assertEquals(30_000L, nextPollMs(remote("x", playing = false)))
    }

    @Test
    fun `web api player json becomes remote playback with the device`() {
        val json = PlayerJson(
            isPlaying = true,
            progressMs = 42_000,
            item = PlayerItemJson("vampire", 219_000, listOf(PlayerArtistJson("Olivia Rodrigo")), PlayerAlbumJson("GUTS", listOf(PlayerImageJson("https://i.scdn.co/x")))),
            device = PlayerDeviceJson(id = "laptop-id", name = "HOWARD-LAPTOP", type = "Computer"),
        )
        val r = json.toRemote(nowElapsedMs = 1_000)
        assertEquals("vampire", r.title)
        assertEquals("Olivia Rodrigo", r.artist)
        assertEquals("HOWARD-LAPTOP", r.deviceName)
        assertEquals("https://i.scdn.co/x", r.imageUrl)
        assertEquals(1_000L, r.atElapsedMs)
    }
}
