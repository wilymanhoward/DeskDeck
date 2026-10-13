package com.deskdeck.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.deskdeck.R
import com.deskdeck.domain.RailApp
import com.deskdeck.launcher.AppIcon
import com.deskdeck.ui.dashboard.DashboardActions
import com.deskdeck.ui.theme.CappedWhite
import com.deskdeck.ui.theme.Dimens
import com.deskdeck.ui.theme.GlassTintStrong
import com.deskdeck.ui.theme.LocalMotion
import com.deskdeck.ui.theme.glass
import kotlin.math.roundToInt

/** A long-press on a rail app: which app, and where its tile is (root y, px). */
data class RailSheetRequest(val app: RailApp, val anchorY: Float)

/**
 * The rail's long-press menu, next to the tile: "Open …" (the explicit launch path), pin/unpin as
 * source, and remove from the sidebar. Drawn in the activity's own window so immersive mode stays.
 */
@Composable
fun RailActionSheet(
    request: RailSheetRequest?,
    pinnedPackage: String?,
    actions: DashboardActions,
    onDismiss: () -> Unit,
) {
    val motion = LocalMotion.current
    var shown by remember { mutableStateOf(request) }
    if (request != null) shown = request
    BackHandler(enabled = request != null, onBack = onDismiss)

    AnimatedVisibility(
        visible = request != null,
        enter = if (motion.reduced) fadeIn(motion.fade()) else fadeIn(motion.fade()) + scaleIn(motion.snappy(), initialScale = 0.96f, transformOrigin = TransformOrigin(0f, 0.5f)),
        exit = fadeOut(motion.fade()),
    ) {
        val r = shown ?: return@AnimatedVisibility
        val app = r.app
        val pinned = pinnedPackage == app.packageName
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            val density = LocalDensity.current
            val margin = with(density) { 16.dp.toPx() }
            val left = with(density) { (Dimens.screenPadding + Dimens.railWidth + 8.dp).roundToPx() }
            val maxY = with(density) { maxHeight.toPx() }
            var sheetHeight by remember { mutableIntStateOf(0) }
            val top = (r.anchorY - sheetHeight / 2f).coerceIn(margin, (maxY - sheetHeight - margin).coerceAtLeast(margin))

            Column(
                modifier = Modifier
                    .offset { IntOffset(left, top.roundToInt()) }
                    .onSizeChanged { sheetHeight = it.height }
                    .width(300.dp)
                    .glass(RoundedCornerShape(Dimens.cardCorner), tint = GlassTintStrong)
                    // Taps inside the sheet must not dismiss it.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(model = AppIcon(app.packageName), contentDescription = null, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = CappedWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (app.installed) {
                    SheetItem(R.drawable.ic_open_in_new, stringResource(R.string.sheet_open_app, app.label)) {
                        onDismiss()
                        actions.onOpenApp(app.packageName)
                    }
                    SheetItem(
                        R.drawable.ic_push_pin,
                        stringResource(if (pinned) R.string.sheet_unpin_source else R.string.sheet_pin_source),
                    ) {
                        onDismiss()
                        if (pinned) actions.onUnpinSource() else actions.onPinSource(app.packageName)
                    }
                } else {
                    SheetItem(R.drawable.ic_download, stringResource(R.string.rail_get_app, app.label)) {
                        onDismiss()
                        actions.onGetApp(app.packageName)
                    }
                }
                SheetItem(R.drawable.ic_close, stringResource(R.string.sheet_remove_from_sidebar)) {
                    onDismiss()
                    actions.onRemoveFromRail(app.packageName)
                }
            }
        }
    }
}

@Composable
private fun SheetItem(iconRes: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouch)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = CappedWhite)
    }
}
