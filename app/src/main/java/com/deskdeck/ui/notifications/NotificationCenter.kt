package com.deskdeck.ui.notifications

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
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
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PanelMaxWidth = 460.dp

/**
 * The notification panel, opened from the rail's bell or a banner: every notification the bell
 * counts, newest first. Tap one to read it in full; swipe it left or tap its × to clear it from
 * the system (the bell follows). It slides out beside the rail over the Now Playing card.
 */
@Composable
fun NotificationCenter(viewModel: BannerViewModel = hiltViewModel()) {
    val open by viewModel.centerOpen.collectAsStateWithLifecycle()
    val items by viewModel.active.collectAsStateWithLifecycle()
    val hideContent = LocalAppSettings.current.notifications.hideContent
    val motion = LocalMotion.current
    BackHandler(enabled = open, onBack = viewModel::closeCenter)

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = open, enter = fadeIn(motion.fade()), exit = fadeOut(motion.fade())) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = viewModel::closeCenter),
            )
        }
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
            val start = overlayStart()
            val width = minOf(PanelMaxWidth, maxWidth - start - Dimens.screenPadding)
            AnimatedVisibility(
                visible = open,
                enter = if (motion.reduced) fadeIn(motion.fade()) else slideInHorizontally(motion.gentle()) { -it } + fadeIn(motion.fade()),
                exit = if (motion.reduced) fadeOut(motion.fade()) else slideOutHorizontally(motion.snappy()) { -it } + fadeOut(motion.fade()),
                modifier = Modifier.padding(start = start, top = Dimens.screenPadding, bottom = Dimens.screenPadding),
            ) {
                Column(
                    Modifier
                        .width(width)
                        .fillMaxHeight()
                        .glass(RoundedCornerShape(Dimens.cardCorner), tint = DenseGlass)
                        // Taps inside the panel must not reach the scrim.
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                ) {
                    PanelHeader(count = items.size, onClearAll = viewModel::clearAll, onClose = viewModel::closeCenter)
                    if (items.isEmpty()) {
                        EmptyPanel(Modifier.weight(1f).fillMaxWidth())
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(items, key = { it.key }) { item ->
                                NotificationRow(
                                    item = item,
                                    hideContent = hideContent,
                                    onClear = { viewModel.clear(item.key) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(count: Int, onClearAll: () -> Unit, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.center_title),
                style = MaterialTheme.typography.headlineSmall,
                color = CappedWhite,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = if (count == 0) stringResource(R.string.rail_no_notifications) else pluralStringResource(R.plurals.rail_notifications, count, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
            )
        }
        if (count > 0) ClearAllButton(count, onClearAll)
        IconButton(onClick = onClose, modifier = Modifier.size(Dimens.minTouch)) {
            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.center_close), tint = MutedText)
        }
    }
}

/**
 * Clearing removes notifications from the system for good, so it takes two taps: the first turns
 * the button into "Clear N?", which reverts after a few seconds if not confirmed.
 */
@Composable
private fun ClearAllButton(count: Int, onClearAll: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(CONFIRM_WINDOW_MS)
            armed = false
        }
    }
    TextButton(
        onClick = {
            if (armed) {
                armed = false
                onClearAll()
            } else {
                armed = true
            }
        },
    ) {
        Text(
            text = if (armed) pluralStringResource(R.plurals.center_clear_confirm, count, count) else stringResource(R.string.center_clear_all),
            color = if (armed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

private const val CONFIRM_WINDOW_MS = 4_000L

@Composable
private fun EmptyPanel(modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(painterResource(R.drawable.ic_notifications), null, tint = MutedText.copy(alpha = 0.6f), modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.center_empty), style = MaterialTheme.typography.bodyLarge, color = MutedText)
    }
}

@Composable
private fun NotificationRow(item: Banner, hideContent: Boolean, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    val scope = rememberCoroutineScope()
    val currentOnClear by rememberUpdatedState(onClear)
    var expanded by rememberSaveable(item.key) { mutableStateOf(false) }
    val offsetX = remember { Animatable(0f) }
    val clearLabel = stringResource(R.string.banner_clear)
    val time = remember(item.postedAt) { relativeTime(item.postedAt) }
    val timeText = when (time) {
        is RelativeTime.Now -> stringResource(R.string.center_time_now)
        is RelativeTime.Minutes -> stringResource(R.string.center_time_minutes, time.value)
        is RelativeTime.Hours -> stringResource(R.string.center_time_hours, time.value)
        is RelativeTime.Date -> time.text
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = offsetX.value
                alpha = (1f + offsetX.value / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .pointerInput(item.key) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        scope.launch { offsetX.snapTo((offsetX.value + dx).coerceAtMost(0f)) }
                    },
                    onDragEnd = {
                        scope.launch {
                            if (offsetX.value < -size.width * 0.35f) {
                                offsetX.animateTo(-size.width.toFloat(), motion.snappy())
                                currentOnClear()
                            } else {
                                offsetX.animateTo(0f, motion.bouncy())
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offsetX.animateTo(0f, motion.bouncy()) } },
                )
            }
            .clickable { expanded = !expanded }
            .semantics(mergeDescendants = true) {
                customActions = listOf(CustomAccessibilityAction(clearLabel) { onClear(); true })
            }
            .animateContentSize(motion.gentle())
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AsyncImage(
            model = AppIcon(item.packageName),
            contentDescription = null,
            error = painterResource(R.drawable.ic_notifications),
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.appLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(" · $timeText", style = MaterialTheme.typography.labelMedium, color = MutedText, maxLines = 1)
            }
            if (hideContent) {
                Text(stringResource(R.string.banner_hidden_content), style = MaterialTheme.typography.bodyMedium, color = CappedWhite)
            } else {
                item.title?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = CappedWhite,
                        maxLines = if (expanded) 3 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val body = if (expanded) item.expandedText ?: item.text else item.text
                body?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CappedWhite.copy(alpha = 0.85f),
                        maxLines = if (expanded) 12 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        IconButton(onClick = onClear, modifier = Modifier.size(44.dp)) {
            Icon(painterResource(R.drawable.ic_close), clearLabel, tint = MutedText, modifier = Modifier.size(18.dp))
        }
    }
}

private sealed interface RelativeTime {
    data object Now : RelativeTime
    data class Minutes(val value: Int) : RelativeTime
    data class Hours(val value: Int) : RelativeTime
    data class Date(val text: String) : RelativeTime
}

private fun relativeTime(postedAt: Long, now: Long = System.currentTimeMillis()): RelativeTime {
    val minutes = ((now - postedAt) / 60_000).toInt()
    return when {
        minutes < 1 -> RelativeTime.Now
        minutes < 60 -> RelativeTime.Minutes(minutes)
        minutes < 24 * 60 -> RelativeTime.Hours(minutes / 60)
        else -> RelativeTime.Date(DateFormat.getDateInstance(DateFormat.SHORT).format(Date(postedAt)))
    }
}
