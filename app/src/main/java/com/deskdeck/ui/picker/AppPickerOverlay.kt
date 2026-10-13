package com.deskdeck.ui.picker

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.deskdeck.R
import com.deskdeck.domain.RailApp
import com.deskdeck.launcher.AppIcon
import com.deskdeck.launcher.LaunchableApp
import com.deskdeck.ui.theme.CappedWhite
import com.deskdeck.ui.theme.Dimens
import com.deskdeck.ui.theme.MutedText
import com.deskdeck.ui.theme.SurfaceHigh
import com.deskdeck.ui.theme.SurfaceLow

enum class PickerMode {
    /** From the "all apps" button: tap opens an app, the pin adds it to the rail. */
    LAUNCH,

    /** From "+" or a long-press on the rail: tap pins/unpins, pinned apps can be reordered. */
    EDIT,
}

/**
 * The app picker, drawn inside the activity window rather than as a dialog or bottom-sheet
 * window: a separate window would bring the system bars back over the immersive dashboard.
 */
@Composable
fun AppPickerOverlay(mode: PickerMode?, onDismiss: () -> Unit) {
    // Keep showing the last mode while the panel animates out.
    var shownMode by remember { mutableStateOf(PickerMode.LAUNCH) }
    if (mode != null) shownMode = mode

    BackHandler(enabled = mode != null, onBack = onDismiss)

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = mode != null,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visible = mode != null,
            enter = slideInVertically(tween(350)) { it } + fadeIn(tween(350)),
            exit = slideOutVertically(tween(250)) { it } + fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            AppPickerPanel(mode = shownMode, onDismiss = onDismiss)
        }
    }
}

@Composable
private fun AppPickerPanel(
    mode: PickerMode,
    onDismiss: () -> Unit,
    viewModel: AppPickerViewModel = hiltViewModel(),
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val pinned by viewModel.pinned.collectAsStateWithLifecycle()
    val pinnedPackages by viewModel.pinnedPackages.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .widthIn(max = 1100.dp)
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .clip(RoundedCornerShape(topStart = Dimens.cardCorner, topEnd = Dimens.cardCorner))
            .background(SurfaceLow)
            // Swallow taps so they don't reach the scrim behind the panel.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(horizontal = 28.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(if (mode == PickerMode.EDIT) R.string.picker_edit else R.string.picker_all_apps),
                    style = MaterialTheme.typography.headlineSmall,
                    color = CappedWhite,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(if (mode == PickerMode.EDIT) R.string.picker_hint_edit else R.string.picker_hint_launch),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedText,
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.picker_search)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.width(320.dp),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onDismiss, modifier = Modifier.size(Dimens.minTouch)) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.picker_close))
            }
        }

        if (mode == PickerMode.EDIT) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.picker_in_sidebar),
                style = MaterialTheme.typography.titleMedium,
                color = CappedWhite,
            )
            Spacer(Modifier.height(8.dp))
            if (pinned.isEmpty()) {
                Text(stringResource(R.string.picker_sidebar_empty), style = MaterialTheme.typography.bodyMedium, color = MutedText)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(pinned, key = { it.packageName }) { app ->
                        PinnedAppChip(
                            app = app,
                            isFirst = app == pinned.first(),
                            isLast = app == pinned.last(),
                            onMove = { delta -> viewModel.move(app.packageName, delta) },
                            onRemove = { viewModel.unpin(app.packageName) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        if (apps.isEmpty() && query.isNotBlank()) {
            Text(
                text = stringResource(R.string.picker_no_results, query),
                style = MaterialTheme.typography.bodyLarge,
                color = MutedText,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 128.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(apps, key = { it.packageName }) { app ->
                val isPinned = app.packageName in pinnedPackages
                AppTile(
                    app = app,
                    pinned = isPinned,
                    mode = mode,
                    onClick = {
                        if (mode == PickerMode.EDIT) {
                            viewModel.togglePin(app.packageName)
                        } else {
                            onDismiss()
                            viewModel.launch(app.packageName)
                        }
                    },
                    onTogglePin = { viewModel.togglePin(app.packageName) },
                )
            }
        }
    }
}

@Composable
private fun AppTile(
    app: LaunchableApp,
    pinned: Boolean,
    mode: PickerMode,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val pinState = stringResource(if (pinned) R.string.picker_state_pinned else R.string.picker_state_not_pinned)
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(if (mode == PickerMode.EDIT && pinned) SurfaceHigh else Color.Transparent)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { if (mode == PickerMode.EDIT) stateDescription = pinState }
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                model = AppIcon(app.packageName),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyMedium,
                color = CappedWhite,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (mode == PickerMode.LAUNCH) {
            val description = stringResource(
                if (pinned) R.string.picker_unpin else R.string.picker_pin,
                app.label,
            )
            IconButton(
                onClick = onTogglePin,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_push_pin),
                    contentDescription = description,
                    tint = if (pinned) MaterialTheme.colorScheme.primary else MutedText.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }
        } else if (pinned) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun PinnedAppChip(
    app: RailApp,
    isFirst: Boolean,
    isLast: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(SurfaceHigh)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = AppIcon(app.packageName),
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .alpha(if (app.installed) 1f else 0.4f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.titleMedium,
            color = CappedWhite,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 160.dp),
            overflow = TextOverflow.Ellipsis,
        )
        ChipButton(R.drawable.ic_chevron_left, stringResource(R.string.picker_move_up, app.label), !isFirst) { onMove(-1) }
        ChipButton(R.drawable.ic_chevron_right, stringResource(R.string.picker_move_down, app.label), !isLast) { onMove(1) }
        ChipButton(R.drawable.ic_close, stringResource(R.string.picker_unpin, app.label), true, onRemove)
    }
}

@Composable
private fun ChipButton(iconRes: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            tint = CappedWhite.copy(alpha = if (enabled) 0.85f else 0.25f),
            modifier = Modifier.size(22.dp),
        )
    }
}
