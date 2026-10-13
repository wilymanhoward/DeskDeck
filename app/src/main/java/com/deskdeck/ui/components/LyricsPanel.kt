package com.deskdeck.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deskdeck.R
import com.deskdeck.domain.LyricLine
import com.deskdeck.domain.MediaKind
import com.deskdeck.domain.activeIndexAt
import com.deskdeck.ui.dashboard.LyricsUiState
import com.deskdeck.ui.theme.ActiveLyricWhite
import com.deskdeck.ui.theme.CappedWhite
import com.deskdeck.ui.theme.Dimens
import com.deskdeck.ui.theme.LyricTextStyle
import com.deskdeck.ui.theme.MutedText
import com.deskdeck.ui.theme.SurfaceLowest
import kotlinx.coroutines.delay

/** How long auto-follow stays paused after the user scrolls the lyrics manually. */
private const val RESUME_FOLLOW_AFTER_MS = 5_000L

@Composable
fun LyricsPanel(
    state: LyricsUiState,
    position: State<Long>,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = state,
        contentKey = { it.contentKey() },
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(250)) },
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.cardCorner))
            .background(SurfaceLowest),
        label = "lyricsState",
    ) { s ->
        when (s) {
            LyricsUiState.Idle -> MessageState(R.drawable.ic_lyrics, stringResource(R.string.lyrics_idle))
            LyricsUiState.Loading -> LoadingState()
            is LyricsUiState.Synced -> SyncedLyrics(s.lines, position, onSeek)
            is LyricsUiState.Plain -> PlainLyrics(s.text)
            LyricsUiState.Instrumental ->
                MessageState(R.drawable.ic_music_note, stringResource(R.string.lyrics_instrumental))
            LyricsUiState.NotFound -> MessageState(
                iconRes = R.drawable.ic_lyrics,
                title = stringResource(R.string.lyrics_not_found),
                detail = stringResource(R.string.lyrics_not_found_detail),
                onRetry = onRetry,
            )
            is LyricsUiState.Error -> MessageState(
                iconRes = R.drawable.ic_lyrics,
                title = stringResource(R.string.lyrics_error),
                detail = s.message,
                onRetry = onRetry,
            )
            is LyricsUiState.Unavailable -> MessageState(
                iconRes = R.drawable.ic_music_note,
                title = stringResource(
                    when (s.kind) {
                        MediaKind.AD -> R.string.lyrics_ad
                        MediaKind.PODCAST -> R.string.lyrics_podcast
                        else -> R.string.lyrics_live
                    },
                ),
            )
        }
    }
}

/** Same key → content is updated in place; new key → crossfade. New track → fresh scroll state. */
private fun LyricsUiState.contentKey(): Any = when (this) {
    is LyricsUiState.Synced -> "synced:$trackId"
    is LyricsUiState.Plain -> "plain:$trackId"
    else -> this::class
}

@Composable
private fun SyncedLyrics(
    lines: List<LyricLine>,
    position: State<Long>,
    onSeek: (Long) -> Unit,
) {
    val listState = rememberLazyListState()
    // Changes only when the line changes, so per-frame position updates don't recompose the list.
    val activeIndex by remember(lines) { derivedStateOf { lines.activeIndexAt(position.value) } }

    var following by remember { mutableStateOf(true) }
    var lastUserScrollAt by remember { mutableLongStateOf(0L) }
    val userScrollDetector = remember {
        object : NestedScrollConnection {
            // Programmatic scrolls (animateScrollBy) don't pass through nested scroll, so anything
            // seen here is the user dragging or flinging.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                following = false
                lastUserScrollAt = SystemClock.uptimeMillis()
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                lastUserScrollAt = SystemClock.uptimeMillis()
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(lastUserScrollAt) {
        if (!following) {
            delay(RESUME_FOLLOW_AFTER_MS)
            following = true
        }
    }
    LaunchedEffect(activeIndex, following) {
        if (following) listState.animateCenterOn(activeIndex.coerceAtLeast(0))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val seekLabel = stringResource(R.string.lyrics_seek_to_line)
        LazyColumn(
            state = listState,
            // Half-height padding lets the first and last lines sit in the middle.
            contentPadding = PaddingValues(vertical = maxHeight / 2),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(userScrollDetector)
                .edgeFade(),
        ) {
            items(count = lines.size, key = { it }) { index ->
                LyricLineItem(
                    text = lines[index].text,
                    distance = index - activeIndex,
                    clickLabel = seekLabel,
                    onClick = {
                        following = true
                        onSeek(lines[index].timeMs)
                    },
                )
            }
        }
    }
}

@Composable
private fun LyricLineItem(
    text: String,
    distance: Int,
    clickLabel: String,
    onClick: () -> Unit,
) {
    val active = distance == 0
    val targetAlpha = when {
        active -> 1f
        distance < 0 -> 0.3f
        else -> (0.62f - 0.08f * (distance - 1)).coerceAtLeast(0.28f)
    }
    val alpha by animateFloatAsState(targetAlpha, tween(450), label = "lineAlpha")
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.86f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "lineScale",
    )
    Text(
        // Empty LRC lines mark instrumental breaks.
        text = text.ifBlank { "♪" },
        style = LyricTextStyle,
        color = if (active) ActiveLyricWhite else CappedWhite,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = clickLabel, onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 10.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            },
    )
}

/** Scrolls so that item [index] is vertically centered in the viewport. */
private suspend fun LazyListState.animateCenterOn(index: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) {
        scrollToItem(index)
    }
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
    val delta = item.offset + item.size / 2 - viewportCenter
    animateScrollBy(delta.toFloat(), tween(durationMillis = 650, easing = FastOutSlowInEasing))
}

/** Fades lyrics out toward the top and bottom edges of the panel. */
private fun Modifier.edgeFade(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.15f to Color.Black,
                0.85f to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
private fun PlainLyrics(text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.lyrics_unsynced),
            style = MaterialTheme.typography.labelLarge,
            color = MutedText,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = text,
            style = LyricTextStyle.copy(fontSize = 24.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium),
            color = CappedWhite.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = MutedText, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.lyrics_loading), style = MaterialTheme.typography.titleMedium, color = MutedText)
    }
}

@Composable
private fun MessageState(
    iconRes: Int,
    title: String,
    detail: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MutedText,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = CappedWhite,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        if (detail != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyLarge,
                color = MutedText,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
        if (onRetry != null) {
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = Dimens.minTouch),
            ) {
                Text(stringResource(R.string.lyrics_retry), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
