package com.deskdeck.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.deskdeck.R
import com.deskdeck.domain.RailApp
import com.deskdeck.launcher.AppIcon
import com.deskdeck.ui.common.clockFormatter
import com.deskdeck.ui.common.rememberCurrentTime
import com.deskdeck.ui.common.rememberIs24Hour
import com.deskdeck.ui.theme.CappedWhite
import com.deskdeck.ui.theme.Dimens
import com.deskdeck.ui.theme.MutedText
import com.deskdeck.ui.theme.SurfaceHighest
import java.time.format.DateTimeFormatter

@Composable
fun LeftRail(
    apps: List<RailApp>,
    activePackage: String?,
    notificationCount: Int,
    onAppClick: (RailApp) -> Unit,
    onAppLongClick: (RailApp) -> Unit,
    onAddApp: () -> Unit,
    onAllApps: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(Dimens.railWidth)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RailClock()
        Spacer(Modifier.height(4.dp))
        NotificationIndicator(notificationCount)

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
                        active = app.packageName == activePackage,
                        onClick = { onAppClick(app) },
                        onLongClick = { onAppLongClick(app) },
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
private fun RailClock() {
    val is24Hour = rememberIs24Hour()
    val now by rememberCurrentTime(withSeconds = false)
    val formatter = remember(is24Hour) { clockFormatter(is24Hour, withSeconds = false) }
    val amPm = remember { DateTimeFormatter.ofPattern("a") }
    Column(
        modifier = Modifier
            .height(Dimens.minTouch)
            .semantics(mergeDescendants = true) { heading() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = now.format(formatter),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = CappedWhite,
            maxLines = 1,
            softWrap = false,
        )
        if (!is24Hour) {
            Text(
                text = now.format(amPm),
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NotificationIndicator(count: Int) {
    val description = if (count > 0) {
        stringResource(R.string.rail_notifications, count)
    } else {
        stringResource(R.string.rail_no_notifications)
    }
    Box(
        modifier = Modifier
            .size(Dimens.minTouch)
            .clearAndSetSemantics { contentDescription = description },
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
private fun RailAppTile(app: RailApp, active: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val description = when {
        !app.installed -> stringResource(R.string.rail_get_app, app.label)
        active -> stringResource(R.string.rail_now_playing_app, app.label)
        else -> stringResource(R.string.rail_open_app, app.label)
    }
    val editLabel = stringResource(R.string.rail_edit_apps)
    val indicatorHeight by animateDpAsState(if (active) 28.dp else 0.dp, label = "railIndicator")
    Box(contentAlignment = Alignment.Center) {
        // Android Auto-style pill marking the app that owns the current media session.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-8).dp)
                .size(width = 4.dp, height = indicatorHeight)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Box(
            modifier = Modifier
                .size(Dimens.minTouch)
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    role = Role.Button,
                    onLongClickLabel = editLabel,
                    onLongClick = onLongClick,
                    onClick = onClick,
                )
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            if (app.installed) {
                AsyncImage(
                    model = AppIcon(app.packageName),
                    contentDescription = null,
                    modifier = Modifier.size(46.dp),
                )
            } else {
                NotInstalledTile(app)
            }
        }
    }
}

/** Greyed-out letter tile with a download badge; tapping it opens the Play Store page. */
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
                style = MaterialTheme.typography.titleLarge,
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
    IconButton(onClick = onClick, modifier = Modifier.size(Dimens.minTouch)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            tint = MutedText,
            modifier = Modifier.size(28.dp),
        )
    }
}
