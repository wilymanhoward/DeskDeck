package com.deskdeck.data.lyrics

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A search result from a lyrics provider, reduced to what matching needs. */
data class LyricsCandidate<T>(
    val title: String?,
    val artist: String?,
    val durationSec: Double?,
    val hasSynced: Boolean,
    val hasPlain: Boolean,
    val instrumental: Boolean,
    val payload: T,
)

/**
 * Picks the search result that best matches a [LyricsQuery]. Title counts most, then artist, then
 * duration; results far off in duration are rejected outright (likely a different recording), and
 * synced lyrics win a near-tie.
 */
object LyricMatcher {

    private const val MIN_TITLE = 0.6
    private const val MIN_ARTIST = 0.5
    private const val MIN_TOTAL = 0.65
    private const val MAX_DURATION_DIFF_SEC = 20.0

    fun <T> best(query: LyricsQuery, candidates: List<LyricsCandidate<T>>): LyricsCandidate<T>? =
        candidates
            .filter { it.hasSynced || it.hasPlain || it.instrumental }
            .mapNotNull { c -> score(query, c)?.let { c to it } }
            .maxByOrNull { it.second }
            ?.first

    /** Score in 0..~1.05, or null when the candidate is not acceptable. */
    fun <T> score(query: LyricsQuery, candidate: LyricsCandidate<T>): Double? {
        val titleSim = similarity(
            TitleCleaner.normalize(query.title),
            TitleCleaner.normalize(TitleCleaner.cleanTitle(candidate.title.orEmpty())),
        )
        if (titleSim < MIN_TITLE) return null

        val artistSim = if (query.primaryArtist.isNullOrBlank()) {
            0.5
        } else {
            artistSimilarity(query, candidate.artist.orEmpty())
        }
        if (!query.primaryArtist.isNullOrBlank() && artistSim < MIN_ARTIST) return null

        val durationScore = if (query.durationSec != null && candidate.durationSec != null) {
            val diff = abs(query.durationSec - candidate.durationSec)
            if (diff > MAX_DURATION_DIFF_SEC) return null
            if (diff <= 2.0) 1.0 else max(0.0, 1.0 - (diff - 2.0) / 13.0)
        } else {
            0.5
        }

        val total = 0.5 * titleSim + 0.3 * artistSim + 0.2 * durationScore + if (candidate.hasSynced) 0.05 else 0.0
        return total.takeIf { it >= MIN_TOTAL }
    }

    private fun artistSimilarity(query: LyricsQuery, candidateArtist: String): Double {
        val candidate = TitleCleaner.normalize(candidateArtist)
        val primary = TitleCleaner.normalize(query.primaryArtist.orEmpty())
        // "Justin Bieber feat. Jaden Smith" contains "justin bieber": that's a match.
        if (primary.isNotEmpty() && (candidate.contains(primary) || primary.contains(candidate) && candidate.isNotEmpty())) {
            return 1.0
        }
        val full = TitleCleaner.normalize(query.artist.orEmpty())
        return max(similarity(primary, candidate), similarity(full, candidate))
    }

    /** 1 - normalized Levenshtein distance; containment of one in the other counts as at least 0.9. */
    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val ratio = 1.0 - levenshtein(a, b).toDouble() / max(a.length, b.length)
        val contained = (a.contains(b) || b.contains(a)) && min(a.length, b.length) >= 3
        return if (contained) max(ratio, 0.9) else ratio
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }
}
