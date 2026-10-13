package com.deskdeck.ui.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.TransformOrigin
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
import com.deskdeck.ui.theme.Dimens
import com.deskdeck.ui.theme.LocalMotion
import com.deskdeck.ui.theme.MutedText
import com.deskdeck.ui.theme.glass
import com.deskdeck.ui.theme.pressScale
import kotlinx.coroutines.launch

private val BannerHeight = 56.dp
private val BannerShape = RoundedCornerShape(BannerHeight / 2)

/** Where banners and the panel start: just right of the rail. Banners line up with its bell. */
internal val RailEdge = Dimens.screenPadding + Dimens.railWidth + 8.dp
private val BellTop = Dimens.screenPadding + Dimens.minTouch + 4.dp

// Denser than other glass: banners sit over album art, and their text must stay readable.
internal val DenseGlass = Color(0xFF1C1D21).copy(alpha = 0.95f)

/** A banner on screen, kept around after it leaves the queue so it can animate out. */
private class BannerEntry(banner: Banner) {
    val key = banner.key
    var banner by mutableStateOf(banner)
    val visibility = MutableTransitionState(false).apply { targetState = true }
}

/**
 * In-app notification banners: compact glass pills that slide out from the rail's bell, newest on
 * top, at most [NotificationBanners.MAX_QUEUED][com.deskdeck.media.NotificationBanners.MAX_QUEUED].
 * Each closes itself after the configured time (the thin line shows how long is left) or with a
 * swipe left; a tap opens the notification panel. They stay in the top-left corner, clear of the
 * transport controls, and new ones wait while the user is seeking or setting the volume
 * ([LocalBannerHold]).
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

    Box(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
        Column(
            modifier = Modifier.padding(start = RailEdge, top = BellTop),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            entries.forEach { entry ->
                key(entry.key) {
                    AnimatedVisibility(
                        visibleState = entry.visibility,
                        enter = if (motion.reduced) {
                            fadeIn(motion.fade()) + expandVertically(motion.fade(), expandFrom = Alignment.Top)
                        } else {
                            // Grows out of the bell.
                            expandVertically(motion.gentle(), expandFrom = Alignment.Top) +
                                slideInHorizontally(motion.bouncy()) { -it / 3 } +
                                scaleIn(motion.bouncy(), initialScale = 0.6f, transformOrigin = TransformOrigin(0f, 0.5f)) +
                                fadeIn(motion.fade())
                        },
                        exit = if (motion.reduced) {
                            fadeOut(motion.fade()) + shrinkVertically(motion.fade())
                        } else {
                            fadeOut(motion.fade()) + slideOutHorizontally(motion.snappy()) { -it / 2 } +
                                shrinkVertically(motion.snappy(), shrinkTowards = Alignment.Top)
                        },
                    ) {
                        DisposableEffect(Unit) {
                            onDispose { if (!entry.visibility.targetState) entries.remove(entry) }
                        }
                        BannerPill(
                            banner = entry.banner,
                            hideContent = settings.hideContent,
                            durationMs = settings.durationSec * 1_000,
                            onOpen = viewModel::openCenter,
                            onDismiss = { viewModel.dismiss(entry.key) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BannerPill(
    banner: Banner,
    hideContent: Boolean,
    durationMs: Int,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val motion = LocalMotion.current
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var dragging by remember { mutableStateOf(false) }
    val offsetX = remember { Animatable(0f) }

    // Time left, 1 → 0. Paused while touched; an update to the notification restarts it.
    val remaining = remember { Animatable(1f) }
    var timedFor by remember { mutableLongStateOf(banner.postedAt) }
    LaunchedEffect(pressed || dragging, banner.postedAt, durationMs) {
        if (timedFor != banner.postedAt) {
            remaining.snapTo(1f)
            timedFor = banner.postedAt
        }
        if (pressed || dragging) return@LaunchedEffect
        remaining.animateTo(0f, tween((remaining.value * durationMs).toInt(), easing = LinearEasing))
        currentOnDismiss()
    }

    val dismissLabel = stringResource(R.string.banner_dismiss)
    val lineColor = CappedWhite.copy(alpha = 0.35f)
    val primary = if (hideContent) banner.appLabel else banner.title?.takeIf { it.isNotBlank() } ?: banner.appLabel
    val secondary = if (hideContent) stringResource(R.string.banner_hidden_content) else banner.text?.takeIf { it.isNotBlank() }

    Row(
        modifier = Modifier
            .height(BannerHeight)
            .widthIn(min = 220.dp, max = 380.dp)
            .graphicsLayer {
                translationX = offsetX.value
                alpha = (1f + offsetX.value / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
            .pressScale(interaction, pressedScale = 0.96f)
            .glass(BannerShape, tint = DenseGlass)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        // Free toward the rail, resistant the other way.
                        val next = offsetX.value + if (offsetX.value + dx > 0) dx * 0.2f else dx
                        scope.launch { offsetX.snapTo(next) }
                    },
                    onDragEnd = {
                        dragging = false
                        scope.launch {
                            if (offsetX.value < -size.width * 0.3f) {
                                offsetX.animateTo(-size.width * 1.2f, motion.snappy())
                                currentOnDismiss()
                            } else {
                                offsetX.animateTo(0f, motion.bouncy())
                            }
                        }
                    },
                    onDragCancel = {
                        dragging = false
                        scope.launch { offsetX.animateTo(0f, motion.bouncy()) }
                    },
                )
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                customActions = listOf(CustomAccessibilityAction(dismissLabel) { onDismiss(); true })
            }
            // The countdown line along the bottom edge, drawn from the animated value.
            .drawWithContent {
                drawContent()
                val inset = BannerHeight.toPx() / 2
                val y = size.height - 1.5.dp.toPx()
                val end = inset + (size.width - 2 * inset) * remaining.value
                if (end > inset) drawLine(lineColor, Offset(inset, y), Offset(end, y), 1.5.dp.toPx(), StrokeCap.Round)
            }
            .padding(start = 10.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = AppIcon(banner.packageName),
            contentDescription = banner.appLabel,
            error = painterResource(R.drawable.ic_notifications),
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = primary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = CappedWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (secondary != null) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
