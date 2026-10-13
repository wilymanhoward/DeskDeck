package com.deskdeck.data.lyrics

/** Raw lyrics as a provider returns them; parsing happens in the repository. */
sealed interface ProviderResult {
    data class Found(
        val syncedLrc: String?,
        val plain: String?,
        val instrumental: Boolean,
    ) : ProviderResult

    data object NotFound : ProviderResult
}

/**
 * A source of lyrics. Implementations throw `IOException` for network problems so the repository
 * can tell "not found" from "couldn't ask".
 */
interface LyricsProvider {
    /** Short name stored with cached lyrics, e.g. "LRCLIB". */
    val name: String

    suspend fun find(query: LyricsQuery): ProviderResult
}
