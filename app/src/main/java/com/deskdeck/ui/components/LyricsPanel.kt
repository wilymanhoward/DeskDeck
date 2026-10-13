package com.deskdeck.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deskdeck.R
import com.deskdeck.domain.LyricLine
import com.deskdeck.domain.MediaKind
import com.deskdeck.domain.activeIndexAt
import com.deskdeck.ui.dashboard.DashboardActions
import com.deskdeck.ui.dashboard.LyricsUiState
import com.deskdeck.ui.lyrics.Romanizer
import com.deskdeck.ui.settings.LocalAppSettings
import com.deskdeck.ui.theme.ActiveLyricWhite
import com.deskdeck.ui.theme.CappedWhite
import com.deskdeck.ui.theme.Dimens
import com.deskdeck.ui.theme.LyricTextStyle
import com.deskdeck.ui.theme.MutedText
import com.deskdeck.ui.theme.SurfaceHigh
import com.deskdeck.ui.theme.SurfaceLowest
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** How long auto-follow stays paused after the user scrolls the lyrics manually. */
private const val RESUME_FOLLOW_AFTER_MS = 5_000L
private const val OFFSET_STEP_MS = 100L
private const val TIMING_CONTROLS_AUTO_CLOSE_MS = 6_000L

@Composable
fun LyricsPanel(
    state: LyricsUiState,
    position: State<Long>,
    actions: DashboardActions,
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
            is LyricsUiState.Synced -> SyncedLyrics(s.lines, s.offsetMs, position, actions)
            is LyricsUiState.Plain -> PlainLyrics(s.text)
            LyricsUiState.Instrumental ->
                MessageState(R.drawable.ic_music_note, stringResource(R.string.lyrics_instrumental))
            LyricsUiState.NotFound -> MessageState(
                iconRes = R.drawable.ic_lyrics,
                title = stringResource(R.string.lyrics_not_found),
                detail = stringResource(R.string.lyrics_not_found_detail),
                onRetry = actions::onRetryLyrics,
            )
            is LyricsUiState.Error -> MessageState(
                iconRes = R.drawable.ic_lyrics,
                title = stringResource(R.string.lyrics_error),
                detail = stringResource(R.string.lyrics_error_detail),
                onRetry = actions::onRetryLyrics,
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
    offsetMs: Long,
    position: State<Long>,
    actions: DashboardActions,
) {
    val listState = rememberLazyListState()
    val settings = LocalAppSettings.current
    val style = rememberLyricStyle(settings.lyricsFontSizeSp)
    val romanized = rememberRomanized(lines.map { it.text }, settings.showRomanization)
    // Changes only when the line changes, so per-frame position updates don't recompose the list.
    val activeIndex by remember(lines, offsetMs) {
        derivedStateOf { lines.activeIndexAt(position.value + offsetMs) }
    }

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
                    romanized = romanized.getOrNull(index),
                    style = style,
                    distance = index - activeIndex,
                    clickLabel = seekLabel,
                    onClick = {
                        following = true
                        actions.onSeek((lines[index].timeMs - offsetMs).coerceAtLeast(0))
                    },
                )
            }
        }
        TimingControls(
            offsetMs = offsetMs,
            onAdjust = actions::onAdjustLyricsOffset,
            onReset = actions::onResetLyricsOffset,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        )
    }
}

/** Collapsed: a small button. Expanded: Earlier / offset / Later / Reset, closing itself after a while. */
@Composable
private fun TimingControls(
    offsetMs: Long,
    onAdjust: (Long) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableLongStateOf(0L) }
    LaunchedEffect(expanded, lastInteraction) {
        if (expanded) {
            delay(TIMING_CONTROLS_AUTO_CLOSE_MS)
            expanded = false
        }
    }
    fun touch() {
        lastInteraction = SystemClock.uptimeMillis()
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(if (expanded) SurfaceHigh else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(visible = expanded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { touch(); onAdjust(OFFSET_STEP_MS) },
                    modifier = Modifier.heightIn(min = Dimens.minTouch),
                ) { Text(stringResource(R.string.lyrics_timing_earlier)) }
                Text(
                    text = offsetLabel(offsetMs),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = CappedWhite,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 96.dp),
                )
                TextButton(
                    onClick = { touch(); onAdjust(-OFFSET_STEP_MS) },
                    modifier = Modifier.heightIn(min = Dimens.minTouch),
                ) { Text(stringResource(R.string.lyrics_timing_later)) }
                TextButton(
                    onClick = { touch(); onReset() },
                    enabled = offsetMs != 0L,
                    modifier = Modifier.heightIn(min = Dimens.minTouch),
                ) { Text(stringResource(R.string.lyrics_timing_reset)) }
            }
        }
        IconButton(
            onClick = { expanded = !expanded; touch() },
            modifier = Modifier.size(Dimens.minTouch),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_tune),
                contentDescription = stringResource(
                    if (expanded) R.string.lyrics_timing_close else R.string.lyrics_timing,
                ),
                // A non-zero offset stays visible as an accent tint even when collapsed.
                tint = if (offsetMs != 0L) MaterialTheme.colorScheme.primary else MutedText.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun offsetLabel(offsetMs: Long): String {
    val seconds = "%.1f".format(abs(offsetMs) / 1000.0)
    return when {
        offsetMs > 0 -> stringResource(R.string.lyrics_offset_earlier, seconds)
        offsetMs < 0 -> stringResource(R.string.lyrics_offset_later, seconds)
        else -> stringResource(R.string.lyrics_offset_none)
    }
}

@Composable
private fun LyricLineItem(
    text: String,
    romanized: String?,
    style: TextStyle,
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
    Column(
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
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            // Empty LRC lines mark instrumental breaks.
            text = text.ifBlank { "♪" },
            style = style,
            color = if (active) ActiveLyricWhite else CappedWhite,
            textAlign = TextAlign.Center,
        )
        if (romanized != null) {
            Text(
                text = romanized,
                style = style.copy(fontSize = style.fontSize * 0.55f, lineHeight = style.lineHeight * 0.55f, fontWeight = FontWeight.Normal),
                color = MutedText,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun rememberLyricStyle(sizeSp: Int): TextStyle = remember(sizeSp) {
    LyricTextStyle.copy(fontSize = sizeSp.sp, lineHeight = (sizeSp * 4 / 3).sp)
}

/** Romanized form of each text (null where not needed), computed off the main thread. */
@Composable
private fun rememberRomanized(texts: List<String>, enabled: Boolean): List<String?> {
    val result by produceState(initialValue = emptyList<String?>(), texts, enabled) {
        value = if (enabled && Romanizer.isSupported) {
            withContext(Dispatchers.Default) { texts.map(Romanizer::romanize) }
        } else {
            emptyList()
        }
    }
    return result
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
    val settings = LocalAppSettings.current
    val lines = remember(text) { text.lines() }
    val romanized = rememberRomanized(lines, settings.showRomanization)
    val size = settings.lyricsFontSizeSp * 4 / 5
    val annotated = remember(lines, romanized) {
        buildAnnotatedString {
            lines.forEachIndexed { i, line ->
                if (i > 0) append('\n')
                append(line)
                romanized.getOrNull(i)?.let { roman ->
                    append('\n')
                    withStyle(SpanStyle(fontSize = (size * 0.6f).sp, fontWeight = FontWeight.Normal, color = MutedText)) {
                        append(roman)
                    }
                }
            }
        }
    }
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
            text = annotated,
            style = LyricTextStyle.copy(fontSize = size.sp, lineHeight = (size * 3 / 2).sp, fontWeight = FontWeight.Medium),
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
