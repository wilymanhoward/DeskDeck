package com.deskdeck.ui.components

import android.os.SystemClock
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import com.deskdeck.domain.LyricRow
import com.deskdeck.domain.LyricWord
import com.deskdeck.domain.activeRowAt
import com.deskdeck.domain.buildLyricRows
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deskdeck.R
import com.deskdeck.domain.LyricLine
import com.deskdeck.domain.MediaKind
import com.deskdeck.ui.dashboard.DashboardActions
import com.deskdeck.ui.dashboard.LyricsUiState
import com.deskdeck.ui.lyrics.Romanizer
import com.deskdeck.ui.settings.LocalAppSettings
import com.deskdeck.ui.theme.ActiveLyricWhite
import com.deskdeck.ui.theme.CappedWhite
import com.deskdeck.ui.theme.Dimens
import com.deskdeck.ui.theme.LocalMotion
import com.deskdeck.ui.theme.LyricTextStyle
import com.deskdeck.ui.theme.MotionSpec
import com.deskdeck.ui.theme.MutedText
import com.deskdeck.ui.theme.SurfaceHigh
import com.deskdeck.ui.theme.glass
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** How long auto-follow stays paused after the user scrolls the lyrics manually. */
private const val RESUME_FOLLOW_AFTER_MS = 5_000L
private const val OFFSET_STEP_MS = 100L
private const val TIMING_CONTROLS_AUTO_CLOSE_MS = 6_000L

/** Where the active line rests, as a fraction of the panel height (Apple Music keeps it high). */
private const val ACTIVE_LINE_ANCHOR = 0.35f
private const val UNSUNG_ALPHA = 0.38f
private val FILL_EDGE = 10.dp
private val WORD_LIFT = 3.dp
private const val EMPHASIS_EASE_MS = 180
private const val FLASH_MS = 500
private const val BREATH_MS = 1_400
private const val GAP_EXIT_MS = 400
private val GAP_ROW_HEIGHT = 56.dp
private val DOT_RADIUS = 6.dp
private val DOT_SPACING = 22.dp

@Composable
fun LyricsPanel(
    state: LyricsUiState,
    position: State<Long>,
    actions: DashboardActions,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotion.current
    AnimatedContent(
        targetState = state,
        contentKey = { it.contentKey() },
        transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.fade()) },
        modifier = modifier
            .glass(RoundedCornerShape(Dimens.cardCorner), tint = Color.Black.copy(alpha = 0.12f)),
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

/**
 * Apple Music-style synced lyrics. The active line sits about a third of the way down and fills
 * word by word as it's sung; lines further away are smaller, dimmer and (Android 12+) blurred, and
 * instrumental gaps show breathing dots. Everything that moves per frame reads [position] in the
 * draw phase, so playback only redraws the active line and gap, never recomposes the list.
 */
@Composable
private fun SyncedLyrics(
    lines: List<LyricLine>,
    offsetMs: Long,
    position: State<Long>,
    actions: DashboardActions,
) {
    val motion = LocalMotion.current
    val listState = rememberLazyListState()
    val settings = LocalAppSettings.current
    val style = rememberLyricStyle(settings.lyricsFontSizeSp)
    val rows = remember(lines) { buildLyricRows(lines) }
    val romanized = rememberRomanized(lines.map { it.text }, settings.showRomanization)
    // Changes only when the row changes, so per-frame position updates don't recompose the list.
    val activeRow by remember(rows, offsetMs) {
        derivedStateOf { rows.activeRowAt(position.value + offsetMs) }
    }
    val lyricClock = remember(position, offsetMs) { { position.value + offsetMs } }

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
    LaunchedEffect(activeRow, following, motion) {
        if (following) listState.animateAnchorOn(activeRow.coerceAtLeast(0), ACTIVE_LINE_ANCHOR, motion)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val seekLabel = stringResource(R.string.lyrics_seek_to_line)
        LazyColumn(
            state = listState,
            // The active line rests at ACTIVE_LINE_ANCHOR; the padding lets the first and last get there.
            contentPadding = PaddingValues(top = maxHeight * ACTIVE_LINE_ANCHOR, bottom = maxHeight * (1 - ACTIVE_LINE_ANCHOR)),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(userScrollDetector)
                .edgeFade(),
        ) {
            items(count = rows.size, key = { it }) { index ->
                when (val row = rows[index]) {
                    is LyricRow.Line -> LyricLineItem(
                        line = row.line,
                        romanized = romanized.getOrNull(row.lineIndex),
                        style = style,
                        distance = index - activeRow,
                        clock = lyricClock,
                        clickLabel = seekLabel,
                        onClick = {
                            following = true
                            actions.onSeek((row.line.startMs - offsetMs).coerceAtLeast(0))
                        },
                    )
                    is LyricRow.Gap -> GapItem(gap = row, distance = index - activeRow, clock = lyricClock)
                }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricLineItem(
    line: LyricLine,
    romanized: String?,
    style: TextStyle,
    distance: Int,
    clock: () -> Long,
    clickLabel: String,
    onClick: () -> Unit,
) {
    val motion = LocalMotion.current
    val active = distance == 0
    val targetAlpha = when {
        active -> 1f
        distance < 0 -> 0.3f
        else -> (0.62f - 0.08f * (distance - 1)).coerceAtLeast(0.28f)
    }
    val alpha by animateFloatAsState(targetAlpha, motion.fade(), label = "lineAlpha")
    val scale by animateFloatAsState(
        targetValue = if (active || motion.reduced) 1f else 0.86f,
        animationSpec = motion.gentle(),
        label = "lineScale",
    )
    // Farther lines are softer; blur needs Android 12, below that alpha alone does the job.
    val blur by animateDpAsState(
        targetValue = if (active) 0.dp else (abs(distance).coerceAtMost(4) * 0.8f).dp,
        animationSpec = motion.fade(),
        label = "lineBlur",
    )
    // A quick flash on the line that was tapped to seek.
    val flash = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val spaceWidth = rememberSpaceWidth(style)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .drawBehind { if (flash.value > 0f) drawRect(Color.White.copy(alpha = 0.12f * flash.value)) }
            .clickable(onClickLabel = clickLabel) {
                onClick()
                scope.launch {
                    flash.snapTo(1f)
                    flash.animateTo(0f, tween(FLASH_MS))
                }
            }
            .padding(horizontal = 32.dp, vertical = 10.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
            .then(if (Build.VERSION.SDK_INT >= 31 && blur > 0.dp) Modifier.blur(blur, BlurredEdgeTreatment.Unbounded) else Modifier)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (line.words.isEmpty()) {
            Text(text = line.text, style = style, color = CappedWhite, textAlign = TextAlign.Center)
        } else {
            FlowRow(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                line.words.forEach { word ->
                    val trailingSpace = word.text.endsWith(" ")
                    if (active) SungWord(word, style, clock) else Text(word.text.trimEnd(), style = style, color = CappedWhite)
                    if (trailingSpace) Spacer(Modifier.width(spaceWidth))
                }
            }
        }
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

/**
 * A word of the active line: dim until sung, then filled with white from the left as playback
 * passes through it (a soft-edged mask, so the fill moves smoothly within the word). While it's
 * being sung it lifts slightly and glows. All of it reads [clock] in the draw phase.
 */
@Composable
private fun SungWord(word: LyricWord, style: TextStyle, clock: () -> Long) {
    val reduced = LocalMotion.current.reduced
    val text = word.text.trimEnd()
    Box(
        Modifier
            .graphicsLayer {
                if (!reduced) translationY = -WORD_LIFT.toPx() * word.emphasisAt(clock())
            }
            .drawBehind {
                val glow = word.emphasisAt(clock())
                if (glow <= 0f) return@drawBehind
                // A soft pill just around the word; it stays inside the line's clip, so no hard edges.
                val inset = 4.dp.toPx()
                drawRoundRect(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.09f * glow), Color.Transparent),
                        center = center,
                        radius = size.maxDimension * 0.6f,
                    ),
                    topLeft = Offset(-inset, -inset),
                    size = Size(size.width + 2 * inset, size.height + 2 * inset),
                    cornerRadius = CornerRadius(size.height / 2),
                )
            },
    ) {
        Text(text, style = style, color = CappedWhite.copy(alpha = UNSUNG_ALPHA))
        Text(
            text = text,
            style = style,
            color = ActiveLyricWhite,
            modifier = Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    val progress = word.progressAt(clock())
                    if (progress <= 0f) return@drawWithContent
                    drawContent()
                    if (progress >= 1f) return@drawWithContent
                    // Keep the sung part: opaque up to the edge, fading out across FILL_EDGE.
                    val edge = FILL_EDGE.toPx()
                    val x = -edge + (size.width + 2 * edge) * progress
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Black,
                            ((x - edge / 2) / size.width).coerceIn(0f, 1f) to Color.Black,
                            ((x + edge / 2) / size.width).coerceIn(0f, 1f) to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        )
    }
}

/** 0..1: how much a word is "the one being sung now", easing in at its start and out after its end. */
private fun LyricWord.emphasisAt(positionMs: Long): Float = when {
    positionMs < startMs -> 0f
    positionMs < endMs -> ((positionMs - startMs) / EMPHASIS_EASE_MS.toFloat()).coerceAtMost(1f)
    else -> (1f - (positionMs - endMs) / EMPHASIS_EASE_MS.toFloat()).coerceAtLeast(0f)
}

/**
 * Three dots for an instrumental gap. They fill one after another as the gap passes, breathe
 * gently while it's the active row, and shrink away just before the next line starts.
 */
@Composable
private fun GapItem(gap: LyricRow.Gap, distance: Int, clock: () -> Long) {
    val motion = LocalMotion.current
    val active = distance == 0
    val breath = if (active && !motion.reduced) {
        rememberInfiniteTransition(label = "gapBreath").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(BREATH_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "gapBreathPhase",
        )
    } else {
        rememberUpdatedState(0f)
    }
    val alpha by animateFloatAsState(if (active) 1f else 0.35f, motion.fade(), label = "gapAlpha")
    val color = ActiveLyricWhite
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(GAP_ROW_HEIGHT)
            .graphicsLayer { this.alpha = alpha }
            .clearAndSetSemantics {},
    ) {
        val now = clock()
        val progress = if (active) gap.progressAt(now) else 0f
        // Shrink away during the last moments of the gap.
        val ending = if (active) ((gap.endMs - now) / GAP_EXIT_MS.toFloat()).coerceIn(0f, 1f) else 1f
        val scale = (1f + 0.15f * breath.value) * ending
        val radius = DOT_RADIUS.toPx() * scale
        val spacing = DOT_SPACING.toPx()
        val startX = size.width / 2 - spacing
        for (i in 0 until 3) {
            val fill = (progress * 3 - i).coerceIn(0f, 1f)
            drawCircle(color.copy(alpha = 0.3f + 0.7f * fill), radius, Offset(startX + i * spacing, size.height / 2))
        }
    }
}

/** Width of a space in [style], to put between word items (a Text's own trailing space isn't measured). */
@Composable
private fun rememberSpaceWidth(style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(style, density) {
        val px = measurer.measure("a a", style).size.width - measurer.measure("aa", style).size.width
        with(density) { px.coerceAtLeast(0).toDp() }
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

/** Scrolls so that item [index] is centered on [anchor] (a fraction of the viewport height), with a spring. */
private suspend fun LazyListState.animateAnchorOn(index: Int, anchor: Float, motion: MotionSpec) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) {
        scrollToItem(index)
    }
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val anchorY = info.viewportStartOffset + (info.viewportEndOffset - info.viewportStartOffset) * anchor
    val delta = item.offset + item.size / 2 - anchorY
    animateScrollBy(delta, motion.gentle())
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
