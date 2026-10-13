package com.deskdeck.data.lyrics

import com.deskdeck.domain.Track
import java.text.Normalizer

/** What we search lyrics for: cleaned title/artist plus album and duration hints. */
data class LyricsQuery(
    val title: String,
    /** Cleaned full artist string, e.g. "Justin Bieber, Jaden". */
    val artist: String?,
    /** First credited artist, used for searching and matching, e.g. "Justin Bieber". */
    val primaryArtist: String?,
    val album: String?,
    val durationSec: Int?,
)

/**
 * Cleans player metadata into something lyric databases recognize. YouTube in particular sends
 * titles like "Artist - Song (Official Video) [4K]" with the channel as artist ("ArtistVEVO").
 */
object TitleCleaner {

    /**
     * Bracketed or dashed suffixes containing one of these whole words are noise, not part of the
     * song name. Whole words matter: "(Stayin' Alive)" must survive the "live" rule.
     */
    private val NOISE = Regex(
        """(?<![\p{L}\p{N}])(official|video|audio|lyrics?|visuali[sz]er|remaster(ed)?|live|version|edit|""" +
            """mono|stereo|explicit|clean|hd|hq|4k|mv|m/v|feat\.?|ft\.?|featuring|with|prod\.?|from|bonus|""" +
            """deluxe|single|demo|anniversary|re-?record(ed)?|performance|session|unplugged)(?![\p{L}\p{N}])""",
        RegexOption.IGNORE_CASE,
    )

    private val BRACKETS = Regex("""\s*[(\[【「]([^)\]】」]*)[)\]】」]""")
    private val FEAT = Regex("""\s+(?:feat\.?|ft\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE)
    private val PIPE_SUFFIX = Regex("""\s+[|｜].*$""")
    private val DASH_SUFFIX = Regex("""\s+[-–—]\s+(.+)$""")

    /** Only video-platform decoration; unlike [NOISE] it keeps "Live", "Remastered", "feat." … for display. */
    private val VIDEO_NOISE = Regex(
        """(?<![\p{L}\p{N}])(official|video|audio|lyrics?|visuali[sz]er|mv|m/v|hd|hq|4k)(?![\p{L}\p{N}])""",
        RegexOption.IGNORE_CASE,
    )

    data class DisplayText(val title: String, val artist: String?)

    /**
     * What the now-playing card shows: "Adele - Hello (Official Music Video)" by "AdeleVEVO" becomes
     * "Hello" by "Adele". Gentler than [queryFor]: version info like "(Live)" or "(Remastered)" is
     * meaningful to read, so only video decoration is removed. Normal music metadata is unchanged.
     */
    fun forDisplay(track: Track): DisplayText {
        var title = BRACKETS.replace(track.title) { m -> if (VIDEO_NOISE.containsMatchIn(m.groupValues[1])) "" else m.value }
        title = PIPE_SUFFIX.replace(title, "")
        val (splitTitle, artist) = splitArtistPrefix(title, track.artist)
        return DisplayText(
            title = splitTitle.trim().ifBlank { track.title.trim() },
            artist = artist ?: track.artist,
        )
    }

    fun queryFor(track: Track): LyricsQuery {
        val (title, artist) = splitArtistPrefix(track.title, track.artist)
        val cleanTitle = cleanTitle(title).ifBlank { track.title.trim() }
        return LyricsQuery(
            title = cleanTitle,
            artist = artist,
            primaryArtist = artist?.let(::primaryArtist),
            album = track.album?.let(::cleanTitle)?.takeIf { it.isNotBlank() },
            durationSec = track.durationMs?.let { ((it + 500) / 1000).toInt() }?.takeIf { it > 0 },
        )
    }

    fun cleanTitle(title: String): String {
        var t = title
        t = BRACKETS.replace(t) { m -> if (isNoise(m.groupValues[1])) "" else m.value }
        t = PIPE_SUFFIX.replace(t, "")
        t = FEAT.replace(t, "")
        // Repeated: "Song - Live - 2011 Remaster".
        while (true) {
            val m = DASH_SUFFIX.find(t) ?: break
            if (!isNoise(m.groupValues[1])) break
            t = t.substring(0, m.range.first)
        }
        return t.replace(Regex("\\s+"), " ").trim().trimEnd('-', '–', '—', ':', ',').trim()
    }

    fun cleanArtist(artist: String): String = artist
        .replace(Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""VEVO$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+Official$""", RegexOption.IGNORE_CASE), "")
        .trim()

    /** "A, B", "A feat. B", "A ft. B", "A x B" → "A". "&" is kept: it is often part of a band name. */
    fun primaryArtist(artist: String): String =
        artist.split(Regex("""\s*,\s*|\s+(?:feat\.?|ft\.?|featuring|x|with)\s+""", RegexOption.IGNORE_CASE))
            .first()
            .trim()
            .ifEmpty { artist.trim() }

    /**
     * "Artist - Title" from video platforms → (Title, Artist), trusted when the channel name is
     * missing, a VEVO/Topic channel, or matches the left part. Otherwise (title, cleaned artist).
     */
    private fun splitArtistPrefix(title: String, rawArtist: String?): Pair<String, String?> {
        val artist = rawArtist?.let(::cleanArtist)
        val (left, right) = splitArtistTitle(title) ?: return title to artist
        val trusted = artist == null || isChannelName(rawArtist) ||
            normalize(left).contains(normalize(artist)) || normalize(artist).contains(normalize(left))
        return if (trusted) right to cleanArtist(left) else title to artist
    }

    /** Splits "Artist - Title"; null when the title has no such separator. */
    fun splitArtistTitle(title: String): Pair<String, String>? {
        val m = Regex("""^(.+?)\s+[-–—]\s+(.+)$""").find(title) ?: return null
        val left = m.groupValues[1].trim()
        val right = m.groupValues[2].trim()
        // "Song - Remastered 2011" is a suffix, not "Artist - Song". Judge the right side without its
        // brackets: "Hello (Official Video)" is a title, "Live" is not.
        val core = BRACKETS.replace(right, "").trim()
        if (left.isEmpty() || core.isEmpty() || isNoise(core)) return null
        return left to right
    }

    private fun isChannelName(artist: String?): Boolean =
        artist != null && (artist.endsWith("VEVO", ignoreCase = true) || artist.endsWith("- Topic", ignoreCase = true))

    private fun isNoise(text: String): Boolean = NOISE.containsMatchIn(text)

    /**
     * Comparison form: lowercase, accents removed, "&" → "and", punctuation dropped, single spaces.
     * Letters in any script are kept, so CJK/Cyrillic titles still compare.
     */
    fun normalize(text: String): String {
        // Decompose to drop accents, then recompose: Hangul would otherwise stay split into jamo.
        val stripped = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
        val composed = Normalizer.normalize(stripped, Normalizer.Form.NFC).replace("&", " and ")
        return buildString {
            for (c in composed) append(if (c.isLetterOrDigit()) c else ' ')
        }.replace(Regex("\\s+"), " ").trim()
    }
}
