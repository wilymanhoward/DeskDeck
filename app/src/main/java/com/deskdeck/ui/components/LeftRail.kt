package com.deskdeck.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.deskdeck.R
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.deskdeck.burnin.rememberRailOpacity
import com.deskdeck.domain.RailApp
import com.deskdeck.launcher.AppIcon
import com.deskdeck.ui.common.clockFormatter
import com.deskdeck.ui.common.rememberCurrentTime
import com.deskdeck.ui.common.rememberIs24Hour
import com.deskdeck.ui.dashboard.RailState
import com.deskdeck.ui.settings.LocalAppSettings
import com.deskdeck.ui.theme.CappedWhite
import com.deskdeck.ui.theme.Dimens
import com.deskdeck.ui.theme.LocalMotion
import com.deskdeck.ui.theme.MotionSpec
import com.deskdeck.ui.theme.MutedText
import com.deskdeck.ui.theme.SurfaceHighest
import com.deskdeck.ui.theme.pressScale
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun LeftRail(
    apps: List<RailApp>,
    state: RailState,
    notificationCount: Int,
    onAppClick: (RailApp) -> Unit,
    onAppLongClick: (RailApp, anchorY: Float) -> Unit,
    onAddApp: () -> Unit,
    onAllApps: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    onNotifications: () -> Unit = {},
    onToggleNight: () -> Unit = {},
) {
    val railOpacity = rememberRailOpacity(LocalAppSettings.current.burnIn.railOpacityDrift)
    val motion = LocalMotion.current
    val centers = remember { mutableStateMapOf<String, Float>() }
    var railTop by remember { mutableFloatStateOf(0f) }
    // Drop positions of unpinned apps so the ring never points at an empty slot.
    val pinned = remember(apps) { apps.map { it.packageName }.toSet() }
    LaunchedEffect(pinned) { centers.keys.retainAll(pinned) }
    val targetCenter = centers[state.highlightPackage]?.minus(railTop)
    val movingCenter = remember { Animatable(0f) }
    var ringShown by remember { mutableStateOf(false) }
    LaunchedEffect(targetCenter, motion) {
        when {
            targetCenter == null -> ringShown = false
            // The first appearance lands on the icon; only source switches travel.
            !ringShown || motion.reduced -> { movingCenter.snapTo(targetCenter); ringShown = true }
            else -> movingCenter.animateTo(targetCenter, motion.snappy())
        }
    }
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .width(Dimens.railWidth)
            .fillMaxHeight()
            .onGloballyPositioned { railTop = it.boundsInRoot().top }
            .drawBehind {
                if (targetCenter != null && ringShown) {
                    val y = movingCenter.value
                    val side = (Dimens.minTouch + 4.dp).toPx()
                    val center = Offset(size.width / 2, y)
                    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.20f), Color.Transparent), center, side * 0.8f), side * 0.8f, center)
                    drawRoundRect(accent.copy(alpha = 0.8f), Offset((size.width - side) / 2, y - side / 2), Size(side, side), CornerRadius(18.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                }
            }
            // Read in the draw phase: the slow opacity drift never recomposes the rail.
            .graphicsLayer { alpha = railOpacity.value },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RailClock(onLongClick = onToggleNight)
        Spacer(Modifier.height(4.dp))
        NotificationIndicator(notificationCount, onNotifications)

        // Shortcuts are centered in the free space and scroll if many apps are pinned.
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                apps.forEach { app ->
                    RailAppTile(
                        app = app,
                        animate = animate,
                        onPosition = { centers[app.packageName] = it },
                        highlighted = app.packageName == state.highlightPackage,
                        playing = app.packageName == state.playingPackage,
                        hasSession = app.packageName in state.sessionPackages,
                        pinned = app.packageName == state.pinnedPackage,
                        onClick = { onAppClick(app) },
                        onLongClick = { anchorY -> onAppLongClick(app, anchorY) },
                    )
                }
                RailIconButton(R.drawable.ic_add, stringResource(R.string.rail_add_app), onAddApp)
            }
        }

        RailIconButton(R.drawable.ic_apps, stringResource(R.string.rail_all_apps), onAllApps)
        Spacer(Modifier.height(4.dp))
        RailIconButton(R.drawable.ic_settings, stringResource(R.string.rail_settings), onSettings)
    }
}

@Composable
private fun RailClock(onLongClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val is24Hour = rememberIs24Hour()
    val showSeconds = LocalAppSettings.current.clockShowSeconds
    val now by rememberCurrentTime(withSeconds = showSeconds)
    // The rail is too narrow for "12:45:30" at this size, so seconds go on the small second line.
    val formatter = remember(is24Hour) { clockFormatter(is24Hour, withSeconds = false) }
    val secondLine = remember(is24Hour, showSeconds) {
        when {
            showSeconds && !is24Hour -> DateTimeFormatter.ofPattern("ss · a")
            showSeconds -> DateTimeFormatter.ofPattern("ss")
            !is24Hour -> DateTimeFormatter.ofPattern("a")
            else -> null
        }
    }
    Column(
        modifier = Modifier
            .height(Dimens.minTouch)
            // Long-press: start or end Night Mode right away.
            .combinedClickable(
                interactionSource = null,
                indication = null,
                onClickLabel = null,
                onLongClickLabel = stringResource(R.string.night_toggle),
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
                onClick = {},
            )
            .semantics(mergeDescendants = true) { heading() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = now.format(formatter),
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = CappedWhite,
            maxLines = 1,
            softWrap = false,
        )
        if (secondLine != null) {
            Text(
                text = now.format(secondLine),
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NotificationIndicator(count: Int, onClick: () -> Unit) {
    val description = if (count > 0) {
        pluralStringResource(R.plurals.rail_notifications, count, count)
    } else {
        stringResource(R.string.rail_no_notifications)
    }
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(Dimens.minTouch)
            .pressScale(interaction)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick { onClick(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        BadgedBox(
            badge = {
                if (count > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text(if (count > 99) "99+" else count.toString())
                    }
                }
            },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_notifications),
                contentDescription = null,
                tint = MutedText,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun RailAppTile(
    app: RailApp,
    animate: Boolean,
    onPosition: (Float) -> Unit,
    highlighted: Boolean,
    playing: Boolean,
    hasSession: Boolean,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongClick: (anchorY: Float) -> Unit,
) {
    val description = when {
        !app.installed -> stringResource(R.string.rail_not_installed, app.label)
        playing -> stringResource(R.string.rail_playing_app, app.label)
        highlighted -> stringResource(R.string.rail_selected_app, app.label)
        !hasSession -> stringResource(R.string.rail_no_session, app.label)
        else -> stringResource(R.string.rail_select_app, app.label)
    }
    val actionsLabel = stringResource(R.string.rail_app_actions)
    val accent = MaterialTheme.colorScheme.primary
    val interaction = remember { MutableInteractionSource() }
    var centerY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .size(Dimens.minTouch + 4.dp)
            .onGloballyPositioned { centerY = it.boundsInRoot().center.y; onPosition(centerY) }
            .pressScale(interaction)
            .drawBehind {
                if (!highlighted) return@drawBehind
                val glowRadius = size.minDimension * 0.8f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.10f), Color.Transparent),
                        center = center,
                        radius = glowRadius,
                    ),
                    radius = glowRadius,
                )
            }
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onLongClickLabel = actionsLabel,
                onLongClick = { onLongClick(centerY) },
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (app.installed) {
            AsyncImage(
                model = AppIcon(app.packageName),
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    // Apps without a media session read as "not ready" until selected.
                    .alpha(if (hasSession || highlighted) 1f else 0.5f),
            )
        } else {
            NotInstalledTile(app)
        }
        when {
            playing -> EqualizerBadge(accent, animate, Modifier.align(Alignment.BottomEnd).padding(3.dp))
            app.installed && !hasSession -> Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(8.dp)
                    .border(1.5.dp, MutedText, CircleShape),
            )
        }
        if (pinned) {
            Icon(
                painter = painterResource(R.drawable.ic_push_pin),
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(14.dp),
            )
        }
    }
}

/** Three bouncing bars while the source plays. The animation is read only in the draw phase. */
@Composable
private fun EqualizerBadge(color: Color, animate: Boolean, modifier: Modifier = Modifier) {
    val phase = if (animate && !LocalMotion.current.reduced) {
    rememberInfiniteTransition(label = "equalizer").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(MotionSpec.EQUALIZER_MS, easing = LinearEasing)),
        label = "equalizerPhase",
    )
    } else rememberUpdatedState(0f)
    Box(
        modifier
            .size(18.dp)
            .background(SurfaceHighest, CircleShape)
            .drawBehind {
                val barWidth = size.width / 9
                val maxHeight = size.height * 0.5f
                val bottom = size.height * 0.75f
                for (i in 0 until 3) {
                    val level = 0.35f + 0.65f * (0.5f + 0.5f * sin(phase.value + i * 2.1f))
                    val x = size.width * (0.3f + i * 0.2f) - barWidth / 2
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, bottom - maxHeight * level),
                        size = Size(barWidth, maxHeight * level),
                        cornerRadius = CornerRadius(barWidth / 2),
                    )
                }
            },
    )
}

/** Greyed-out letter tile with a download badge (not installed). */
@Composable
private fun NotInstalledTile(app: RailApp) {
    Box(Modifier.clearAndSetSemantics { }) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(app.tileColor).copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = app.label.take(1),
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = CappedWhite.copy(alpha = 0.4f),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 4.dp, y = 4.dp)
                .size(20.dp)
                .background(SurfaceHighest, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_download),
                contentDescription = null,
                tint = MutedText,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun RailIconButton(iconRes: Int, description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    IconButton(onClick = onClick, interactionSource = interaction, modifier = Modifier.size(Dimens.minTouch).pressScale(interaction)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            tint = MutedText,
            modifier = Modifier.size(28.dp),
        )
    }
}
