package com.deskdeck.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deskdeck.domain.activeIndexAt
import com.deskdeck.ui.dashboard.LyricsUiState
import com.deskdeck.ui.lyrics.LocalLyricsDelay
import com.deskdeck.ui.theme.ActiveLyricWhite
import com.deskdeck.ui.theme.LocalMotion
import com.deskdeck.ui.theme.MotionSpec
import com.deskdeck.ui.theme.MutedText

/** Whether [MiniLyrics] has something to show (timed lyrics only). */
fun LyricsUiState.hasMiniLyrics(): Boolean = this is LyricsUiState.Synced

/**
 * The medium layout's lyrics: the line being sung with one line before and after, moving up a
 * line at a time. Same timing as the full panel (sync offset and audio delay included).
 */
@Composable
fun MiniLyrics(state: LyricsUiState, position: State<Long>, modifier: Modifier = Modifier) {
    val synced = state as? LyricsUiState.Synced ?: return
    val delayMs = LocalLyricsDelay.current
    val motion = LocalMotion.current
    val index by remember(synced, delayMs) {
        derivedStateOf { synced.lines.activeIndexAt(position.value + synced.offsetMs - delayMs) }
    }
    AnimatedContent(
        targetState = index,
        transitionSpec = {
            if (motion.reduced) {
                fadeIn(motion.fade()) togetherWith fadeOut(motion.fade())
            } else {
                (fadeIn(motion.fade(MotionSpec.LONG_MS)) + slideInVertically(motion.gentle()) { it / 3 }) togetherWith
                    (fadeOut(motion.fade()) + slideOutVertically(motion.gentle()) { -it / 3 })
            }
        },
        label = "miniLyrics",
        modifier = modifier,
    ) { i ->
        Column(
            Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, androidx.compose.ui.Alignment.CenterVertically),
        ) {
            val lines = synced.lines
            Line(lines.getOrNull(i - 1)?.text)
            Text(
                text = lines.getOrNull(i)?.text?.takeIf { it.isNotBlank() } ?: "♪",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = ActiveLyricWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Line(lines.getOrNull(i + 1)?.text)
        }
    }
}

@Composable
private fun Line(text: String?) {
    Text(
        text = text.orEmpty(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MutedText.copy(alpha = 0.55f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}
