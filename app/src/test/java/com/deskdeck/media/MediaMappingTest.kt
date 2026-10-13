// The androidx.media compat classes are deprecated in favor of Media3, but they are still the most
// direct way to read shuffle/repeat from another app's session (Media3 uses them internally for that).
@file:Suppress("DEPRECATION")

package com.deskdeck.media

import android.media.session.PlaybackState
import android.support.v4.media.session.PlaybackStateCompat
import com.deskdeck.domain.MediaKind
import com.deskdeck.domain.PlaybackStatus
import com.deskdeck.domain.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaMappingTest {

    private val spotify = "com.spotify.music"

    // region toTrack

    @Test
    fun `builds a track from standard metadata`() {
        val track = RawMetadata(title = " Song ", artist = "Artist", album = "Album", durationMs = 200_000)
            .toTrack(spotify, canSeek = true)!!
        assertEquals("Song", track.title)
        assertEquals("Artist", track.artist)
        assertEquals("Album", track.album)
        assertEquals(200_000L, track.durationMs)
        assertEquals(MediaKind.MUSIC, track.kind)
    }

    @Test
    fun `falls back to display title and subtitle`() {
        val track = RawMetadata(title = null, artist = null, album = null, displayTitle = "Video", displaySubtitle = "Channel")
            .toTrack("com.google.android.youtube", canSeek = true)!!
        assertEquals("Video", track.title)
        assertEquals("Channel", track.artist)
    }

    @Test
    fun `no title means no track`() {
        assertNull(RawMetadata(title = "  ", artist = "Artist", album = null).toTrack(spotify, canSeek = true))
    }

    @Test
    fun `zero duration becomes null`() {
        val track = RawMetadata(title = "Song", artist = "A", album = null, durationMs = 0).toTrack(spotify, canSeek = true)!!
        assertNull(track.durationMs)
        assertFalse(track.hasDuration)
    }

    @Test
    fun `track id ignores duration and album so late metadata doesn't look like a new track`() {
        val early = RawMetadata(title = "Song", artist = "A", album = null, durationMs = null).toTrack(spotify, true)!!
        val late = RawMetadata(title = "Song", artist = "A", album = "Album", durationMs = 180_000).toTrack(spotify, true)!!
        assertEquals(early.id, late.id)
    }

    // endregion

    // region Media kind

    @Test
    fun `advertisement flag marks an ad`() {
        val track = RawMetadata(title = "Buy things", artist = "Brand", album = null, advertisement = 1).toTrack(spotify, true)!!
        assertEquals(MediaKind.AD, track.kind)
    }

    @Test
    fun `spotify ad title and promo artist are detected`() {
        assertEquals(MediaKind.AD, RawMetadata("Advertisement", "Brand", null).toTrack(spotify, false)!!.kind)
        assertEquals(MediaKind.AD, RawMetadata("Try Premium", "Spotify", null).toTrack(spotify, false)!!.kind)
        assertEquals(MediaKind.AD, RawMetadata("Promo", "Brand", null, mediaId = "spotify:ad:123").toTrack(spotify, false)!!.kind)
    }

    @Test
    fun `artist named Spotify with an album is not an ad`() {
        val track = RawMetadata("Song", "Spotify", "Real album", durationMs = 1000).toTrack(spotify, true)!!
        assertEquals(MediaKind.MUSIC, track.kind)
    }

    @Test
    fun `podcasts are detected by episode ids, genre, and podcast apps`() {
        assertEquals(MediaKind.PODCAST, RawMetadata("Ep 1", "Show", null, mediaUri = "spotify:episode:abc").toTrack(spotify, true)!!.kind)
        assertEquals(MediaKind.PODCAST, RawMetadata("Ep 1", "Show", null, genre = "Podcast").toTrack("some.app", true)!!.kind)
        assertEquals(MediaKind.PODCAST, RawMetadata("Ep 1", "Show", null).toTrack("au.com.shiftyjelly.pocketcasts", true)!!.kind)
    }

    @Test
    fun `no duration and no seeking is a live stream`() {
        assertEquals(MediaKind.LIVE, RawMetadata("Radio", "Station", null).toTrack("radio.app", canSeek = false)!!.kind)
    }

    @Test
    fun `no duration but seekable stays music`() {
        assertEquals(MediaKind.MUSIC, RawMetadata("Song", "Artist", null).toTrack("player.app", canSeek = true)!!.kind)
    }

    // endregion

    // region Playback state

    @Test
    fun `maps framework playback states`() {
        assertEquals(PlaybackStatus.PLAYING, mapPlaybackStatus(PlaybackState.STATE_PLAYING))
        assertEquals(PlaybackStatus.PAUSED, mapPlaybackStatus(PlaybackState.STATE_PAUSED))
        assertEquals(PlaybackStatus.BUFFERING, mapPlaybackStatus(PlaybackState.STATE_BUFFERING))
        assertEquals(PlaybackStatus.BUFFERING, mapPlaybackStatus(PlaybackState.STATE_SKIPPING_TO_NEXT))
        assertEquals(PlaybackStatus.STOPPED, mapPlaybackStatus(PlaybackState.STATE_STOPPED))
        assertEquals(PlaybackStatus.STOPPED, mapPlaybackStatus(PlaybackState.STATE_NONE))
        assertEquals(PlaybackStatus.STOPPED, mapPlaybackStatus(PlaybackState.STATE_ERROR))
    }

    private val noToggles = ToggleSupport(null, false, null, RepeatMode.OFF)

    @Test
    fun `capabilities come from action flags`() {
        val info = buildPlaybackInfo(
            state = PlaybackState.STATE_PLAYING,
            positionMs = 1_000,
            lastPositionUpdateTime = 500,
            speed = 1f,
            actions = PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_PLAY_PAUSE,
            toggles = noToggles,
        )
        assertTrue(info.canSkipNext)
        assertFalse(info.canSkipPrevious)
        assertFalse(info.canSeek)
        assertEquals(1_000L, info.positionMs)
        assertEquals(500L, info.updatedAtElapsedMs)
    }

    @Test
    fun `players without any action flags keep skip buttons enabled`() {
        val info = buildPlaybackInfo(PlaybackState.STATE_PAUSED, 0, 0, 0f, actions = 0, toggles = noToggles)
        assertTrue(info.canSkipNext)
        assertTrue(info.canSkipPrevious)
        assertFalse(info.canSeek)
    }

    // endregion

    // region Shuffle / repeat

    private val compatBits = PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE or PlaybackStateCompat.ACTION_SET_REPEAT_MODE

    @Test
    fun `compat bits with known modes give full support and real state`() {
        val t = resolveToggles(compatBits, PlaybackStateCompat.SHUFFLE_MODE_ALL, PlaybackStateCompat.REPEAT_MODE_ONE, emptyList())
        assertEquals(ToggleCommand.Compat, t.shuffleCommand)
        assertTrue(t.shuffleOn)
        assertEquals(ToggleCommand.Compat, t.repeatCommand)
        assertEquals(RepeatMode.ONE, t.repeatMode)
    }

    @Test
    fun `repeat group counts as repeat all`() {
        val t = resolveToggles(compatBits, PlaybackStateCompat.SHUFFLE_MODE_NONE, PlaybackStateCompat.REPEAT_MODE_GROUP, emptyList())
        assertFalse(t.shuffleOn)
        assertEquals(RepeatMode.ALL, t.repeatMode)
    }

    @Test
    fun `custom actions are used when compat state is unknown`() {
        val custom = listOf(
            CustomActionInfo("com.player.TOGGLE_SHUFFLE", "Shuffle"),
            CustomActionInfo("com.player.CYCLE", "Repeat mode"),
        )
        val t = resolveToggles(
            compatBits,
            PlaybackStateCompat.SHUFFLE_MODE_INVALID,
            PlaybackStateCompat.REPEAT_MODE_INVALID,
            custom,
        )
        assertEquals(ToggleCommand.Custom("com.player.TOGGLE_SHUFFLE"), t.shuffleCommand)
        assertEquals(ToggleCommand.Custom("com.player.CYCLE"), t.repeatCommand)
        assertFalse(t.shuffleOn)
        assertEquals(RepeatMode.OFF, t.repeatMode)
    }

    @Test
    fun `compat bit without known state still allows the command`() {
        val t = resolveToggles(compatBits, PlaybackStateCompat.SHUFFLE_MODE_INVALID, PlaybackStateCompat.REPEAT_MODE_INVALID, emptyList())
        assertEquals(ToggleCommand.Compat, t.shuffleCommand)
        assertEquals(ToggleCommand.Compat, t.repeatCommand)
    }

    @Test
    fun `nothing advertised means unsupported`() {
        val t = resolveToggles(
            PlaybackState.ACTION_PLAY_PAUSE,
            PlaybackStateCompat.SHUFFLE_MODE_INVALID,
            PlaybackStateCompat.REPEAT_MODE_INVALID,
            listOf(CustomActionInfo("com.player.LIKE", "Like")),
        )
        assertNull(t.shuffleCommand)
        assertNull(t.repeatCommand)
    }

    @Test
    fun `repeat cycles off, all, one`() {
        assertEquals(PlaybackStateCompat.REPEAT_MODE_ALL, RepeatMode.OFF.nextCompatRepeatMode())
        assertEquals(PlaybackStateCompat.REPEAT_MODE_ONE, RepeatMode.ALL.nextCompatRepeatMode())
        assertEquals(PlaybackStateCompat.REPEAT_MODE_NONE, RepeatMode.ONE.nextCompatRepeatMode())
    }

    // endregion
}
