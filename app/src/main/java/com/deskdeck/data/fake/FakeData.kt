package com.deskdeck.data.fake

import com.deskdeck.data.lyrics.WordTiming
import com.deskdeck.domain.ArtworkColors
import com.deskdeck.domain.LyricLine
import com.deskdeck.domain.Lyrics
import com.deskdeck.domain.RailApp
import com.deskdeck.domain.Track

/**
 * Sample content for the M1 shell and Compose previews. The songs and lyrics are made up.
 * Three tracks cover the main lyric states: synced, plain-only, and not found (also without
 * album colors, to exercise the missing-art fallback).
 */
object FakeData {

    const val SPOTIFY = "com.spotify.music"
    const val YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"
    const val APPLE_MUSIC = "com.apple.android.music"

    val railApps = listOf(
        RailApp(SPOTIFY, "Spotify", installed = true, tileColor = 0xFF1DB954.toInt()),
        RailApp(YOUTUBE_MUSIC, "YouTube Music", installed = true, tileColor = 0xFFE53935.toInt()),
        RailApp(APPLE_MUSIC, "Apple Music", installed = false, tileColor = 0xFFFA2D48.toInt()),
    )

    data class FakeTrack(
        val track: Track,
        val sourcePackage: String,
        val sourceLabel: String,
        val colors: ArtworkColors?,
        val lyrics: Lyrics?,
    )

    val tracks = listOf(
        FakeTrack(
            track = Track(
                id = "fake-neon-harbor",
                title = "Neon Harbor",
                artist = "Lowlight Society",
                album = "Static Tides",
                durationMs = 170_000,
            ),
            sourcePackage = SPOTIFY,
            sourceLabel = "Spotify",
            colors = ArtworkColors(primary = 0xFF2B6CB0.toInt(), secondary = 0xFF7F5AF0.toInt()),
            lyrics = Lyrics.Synced(neonHarborLines()),
        ),
        FakeTrack(
            track = Track(
                id = "fake-paper-satellites",
                title = "Paper Satellites",
                artist = "Mira Vale",
                album = "Small Hours",
                durationMs = 185_000,
            ),
            sourcePackage = SPOTIFY,
            sourceLabel = "Spotify",
            colors = ArtworkColors(primary = 0xFFC2410C.toInt(), secondary = 0xFFB45309.toInt()),
            lyrics = Lyrics.Plain(paperSatellitesLyrics),
        ),
        FakeTrack(
            track = Track(
                id = "fake-glasshouse",
                title = "Glasshouse (Live at the Annex)",
                artist = "The Quiet Engines",
                album = null,
                durationMs = 252_000,
            ),
            sourcePackage = YOUTUBE_MUSIC,
            sourceLabel = "YouTube Music",
            colors = null,
            lyrics = null,
        ),
    )

    fun lyricsFor(trackId: String): Lyrics? = tracks.firstOrNull { it.track.id == trackId }?.lyrics

    private fun neonHarborLines(): List<LyricLine> {
        val chorus = listOf(
            "Oh, the harbor's glowing neon blue",
            "Every wave says something new",
            "Hold the line, I'm coming through",
            "Neon harbor, me and you",
        )
        val timed = mutableListOf<Pair<Double, String>>()
        fun add(startSec: Double, lines: List<String>, stepSec: Double = 4.5) =
            lines.forEachIndexed { i, text -> timed += (startSec + i * stepSec) to text }

        add(8.0, listOf(
            "Streetlights hum a quiet tune",
            "Painting gold on the afternoon",
            "I left the radio on for you",
            "In case you find your way back through",
        ))
        add(26.0, chorus)
        timed += 44.0 to ""
        add(52.0, listOf(
            "Paper boats on a rooftop sea",
            "Little maps that you drew for me",
            "Every corner sings in a minor key",
            "But it sounds like home, it sounds like free",
        ))
        add(70.0, chorus)
        timed += 88.0 to ""
        add(100.0, listOf(
            "And if the tide pulls the lights away",
            "I'll be the signal, I'll be the stay",
            "Count the seconds, count the days",
            "Till the morning finds our place",
        ), stepSec = 5.0)
        timed += 120.0 to ""
        add(128.0, chorus)
        timed += 146.0 to "Neon harbor, me and you"
        timed += 151.0 to "And when the last light fades along the water, I'll still be standing at the edge of the pier"
        timed += 158.0 to ""
        return WordTiming.complete(timed.map { (sec, text) -> LyricLine((sec * 1000).toLong(), text) })
    }

    private val paperSatellitesLyrics: String get() = """
        We folded up the evening
        Into something we could throw
        Paper satellites ascending
        Over rooftops, soft and slow

        You said the sky was listening
        So I whispered something true
        Paper satellites, keep spinning
        Till they carry me to you

        And if they fall, we'll fold another
        Out of letters never sent
        Paper satellites forever
        Circling everywhere we went
    """.trimIndent()
}
