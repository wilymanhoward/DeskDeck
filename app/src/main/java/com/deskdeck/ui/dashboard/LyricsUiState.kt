package com.deskdeck.ui.dashboard

import com.deskdeck.domain.LyricLine
import com.deskdeck.domain.Lyrics
import com.deskdeck.domain.LyricsResult
import com.deskdeck.domain.MediaKind

sealed interface LyricsUiState {
    /** Nothing is playing. */
    data object Idle : LyricsUiState
    data object Loading : LyricsUiState
    /** [offsetMs]: user sync correction, positive = lyrics shown earlier. */
    data class Synced(val trackId: String, val lines: List<LyricLine>, val offsetMs: Long = 0) : LyricsUiState
    data class Plain(val trackId: String, val text: String) : LyricsUiState
    data object Instrumental : LyricsUiState
    data object NotFound : LyricsUiState
    data class Error(val message: String?) : LyricsUiState

    /** Ads, podcasts and live streams: lyrics are not looked up. */
    data class Unavailable(val kind: MediaKind) : LyricsUiState
}

fun LyricsResult.toUiState(trackId: String): LyricsUiState = when (this) {
    is LyricsResult.Found -> when (val lyrics = lyrics) {
        is Lyrics.Synced ->
            if (lyrics.lines.isEmpty()) LyricsUiState.NotFound else LyricsUiState.Synced(trackId, lyrics.lines)
        is Lyrics.Plain ->
            if (lyrics.text.isBlank()) LyricsUiState.NotFound else LyricsUiState.Plain(trackId, lyrics.text)
        Lyrics.Instrumental -> LyricsUiState.Instrumental
    }
    LyricsResult.NotFound -> LyricsUiState.NotFound
    is LyricsResult.Error -> LyricsUiState.Error(message)
}
