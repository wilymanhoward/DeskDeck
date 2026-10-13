package com.deskdeck.ui.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.deskdeck.R
import com.deskdeck.launcher.AppIcon
import com.deskdeck.media.Banner
import com.deskdeck.ui.settings.LocalAppSettings
import com.deskdeck.ui.theme.CappedWhite
import com.deskdeck.ui.theme.LocalMotion
import com.deskdeck.ui.theme.MutedText
import com.deskdeck.ui.theme.glass
import com.deskdeck.ui.theme.pressScale
import kotlinx.coroutines.launch

private val BannerMaxWidth = 560.dp
private val BannerShape = RoundedCornerShape(28.dp)

// Denser than other glass: banners sit over album art and lyrics, and their text must stay readable.
private val BannerTint = Color(0xFF1C1D21).copy(alpha = 0.95f)

/** A banner on screen, kept around after it leaves the queue so it can animate out. */
private class BannerEntry(banner: Banner) {
    val key = banner.key
    var banner by mutableStateOf(banner)
    val visibility = MutableTransitionState(false).apply { targetState = true }
}

/**
 * In-app notification banners: glass pills that slide down from the top center, newest on top,
 * at most [NotificationBanners.MAX_QUEUED][com.deskdeck.media.NotificationBanners.MAX_QUEUED].
 * Each one closes itself after the configured time (the thin line shows how long is left), can be
 * swiped up, and expands on tap. They stay in the top part of the screen, clear of the transport
 * controls, and new ones wait while the user is seeking or setting the volume ([LocalBannerHold]).
 */
@Composable
fun BannerHost(modifier: Modifier = Modifier, viewModel: BannerViewModel = hiltViewModel()) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val held = LocalBannerHold.current.held
    val settings = LocalAppSettings.current.notifications
    val motion = LocalMotion.current

    // What's on screen: removals and updates apply at once, new banners wait while held.
    var shown by remember { mutableStateOf(emptyList<Banner>()) }
    LaunchedEffect(queue, held) {
        val shownKeys = shown.map { it.key }.toSet()
        shown = if (held) queue.filter { it.key in shownKeys } else queue
    }
    val entries = remember { mutableStateListOf<BannerEntry>() }
    LaunchedEffect(shown) {
        val keys = shown.map { it.key }.toSet()
        entries.forEach { if (it.key !in keys) it.visibility.targetState = false }
        // Iterate oldest first so each new banner ends up above the ones before it.
        shown.asReversed().forEach { banner ->
            val existing = entries.firstOrNull { it.key == banner.key }
            if (existing != null) {
                existing.banner = banner
                existing.visibility.targetState = true
            } else {
                entries.add(0, BannerEntry(banner))
            }
        }
    }
    var expandedKey by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        // One expanded banner plus two collapsed ones stay within the top ~60% of the screen.
        val expandedLines = if (maxHeight < 700.dp) 4 else 8
        Column(
            modifier = Modifier
                .padding(top = 16.dp)
                .widthIn(max = BannerMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            entries.forEach { entry ->
                key(entry.key) {
                    AnimatedVisibility(
                        visibleState = entry.visibility,
                        enter = if (motion.reduced) {
                            fadeIn(motion.fade()) + expandVertically(motion.fade(), expandFrom = Alignment.Top)
                        } else {
                            expandVertically(motion.gentle(), expandFrom = Alignment.Top) +
                                slideInVertically(motion.bouncy()) { -it * 2 } +
                                fadeIn(motion.fade())
                        },
                        exit = if (motion.reduced) {
                            fadeOut(motion.fade()) + shrinkVertically(motion.fade())
                        } else {
                            fadeOut(motion.fade()) + slideOutVertically(motion.snappy()) { -it } +
                                shrinkVertically(motion.snappy(), shrinkTowards = Alignment.Top)
                        },
                    ) {
                        DisposableEffect(Unit) {
                            onDispose {
                                if (!entry.visibility.targetState) {
                                    entries.remove(entry)
                                    if (expandedKey == entry.key) expandedKey = null
                                }
                            }
                        }
                        BannerCard(
                            banner = entry.banner,
                            hideContent = settings.hideContent,
                            durationMs = settings.durationSec * 1_000,
                            expanded = expandedKey == entry.key,
                            expandedLines = expandedLines,
                            onToggleExpanded = { expandedKey = if (expandedKey == entry.key) null else entry.key },
                            onDismiss = { viewModel.dismiss(entry.key) },
                            onClear = { viewModel.clear(entry.key) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BannerCard(
    banner: Banner,
    hideContent: Boolean,
    durationMs: Int,
    expanded: Boolean,
    expandedLines: Int,
    onToggleExpanded: () -> Unit,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
) {
    val motion = LocalMotion.current
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var dragging by remember { mutableStateOf(false) }
    val offsetY = remember { Animatable(0f) }

    // Time left, 1 → 0. Paused while expanded or touched; an update to the notification restarts it.
    val remaining = remember { Animatable(1f) }
    var timedFor by remember { mutableLongStateOf(banner.postedAt) }
    val paused = expanded || pressed || dragging
    LaunchedEffect(paused, banner.postedAt, durationMs) {
        if (timedFor != banner.postedAt) {
            remaining.snapTo(1f)
            timedFor = banner.postedAt
        }
        if (paused) return@LaunchedEffect
        remaining.animateTo(0f, tween((remaining.value * durationMs).toInt(), easing = LinearEasing))
        currentOnDismiss()
    }

    val dismissLabel = stringResource(R.string.banner_dismiss)
    val lineColor = CappedWhite.copy(alpha = 0.35f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = offsetY.value
                alpha = (1f + offsetY.value / (size.height.coerceAtLeast(1f))).coerceIn(0f, 1f)
            }
            .pressScale(interaction, pressedScale = 0.98f)
            .glass(BannerShape, tint = BannerTint)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        // Free upward, resistant downward.
                        val next = offsetY.value + if (offsetY.value + dy > 0) dy * 0.2f else dy
                        scope.launch { offsetY.snapTo(next) }
                    },
                    onDragEnd = {
                        dragging = false
                        scope.launch {
                            if (offsetY.value < -size.height * 0.3f) {
                                offsetY.animateTo(-size.height * 1.5f, motion.snappy())
                                currentOnDismiss()
                            } else {
                                offsetY.animateTo(0f, motion.bouncy())
                            }
                        }
                    },
                    onDragCancel = {
                        dragging = false
                        scope.launch { offsetY.animateTo(0f, motion.bouncy()) }
                    },
                )
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onToggleExpanded)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                customActions = listOf(CustomAccessibilityAction(dismissLabel) { onDismiss(); true })
            }
            // The countdown line along the bottom edge, drawn from the animated value.
            .drawWithContent {
                drawContent()
                val inset = 28.dp.toPx()
                val y = size.height - 1.5.dp.toPx()
                val end = inset + (size.width - 2 * inset) * remaining.value
                if (end > inset) drawLine(lineColor, Offset(inset, y), Offset(end, y), 1.5.dp.toPx(), StrokeCap.Round)
            }
            .animateContentSize(motion.gentle())
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            AsyncImage(
                model = AppIcon(banner.packageName),
                contentDescription = null,
                error = painterResource(R.drawable.ic_notifications),
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = banner.appLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hideContent) {
                    Text(
                        text = stringResource(R.string.banner_hidden_content),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CappedWhite,
                    )
                } else {
                    banner.title?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = CappedWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val body = if (expanded) banner.expandedText ?: banner.text else banner.text
                    body?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CappedWhite.copy(alpha = 0.85f),
                            maxLines = if (expanded) expandedLines else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (expanded) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.banner_clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.banner_close)) }
            }
        }
    }
}
