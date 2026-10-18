package com.deskdeck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.deskdeck.R
import com.deskdeck.domain.SpeakerLevel
import com.deskdeck.ui.theme.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deskdeck.media.AudioOutputViewModel
import com.deskdeck.media.AudioOutputState

@Composable
fun AudioOutputButton() {
    var open by rememberSaveable { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier.heightIn(min = Dimens.minTouch)
            .pressScale(interaction, pressedScale = 0.96f)
            .clip(RoundedCornerShape(50))
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = { open = true })
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(painterResource(R.drawable.ic_audio_output), contentDescription = null, tint = CappedWhite, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.audio_output), style = MaterialTheme.typography.labelLarge, color = CappedWhite)
        }
    }
    if (open) {
        Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.padding(24.dp).widthIn(max = 460.dp).fillMaxWidth()) {
                Column(
                    Modifier.screenEntrance()
                        .glass(RoundedCornerShape(Dimens.cardCorner), tint = GlassTintStrong.copy(alpha = 0.95f))
                        .verticalScroll(rememberScrollState()).padding(Dimens.cardPadding),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.audio_output), style = MaterialTheme.typography.headlineSmall,
                            color = CappedWhite, modifier = Modifier.weight(1f).semantics { heading() },
                        )
                        IconButton(onClick = { open = false }, modifier = Modifier.size(Dimens.minTouch)) {
                            Box(Modifier.size(36.dp).background(Color.White.copy(alpha = 0.08f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.audio_output_done),
                                    tint = CappedWhite, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    AudioOutputOptions()
                }
            }
        }
    }
}

@Composable
fun AudioOutputOptions(viewModel: AudioOutputViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.start()
        onDispose { viewModel.stop() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(state.source?.let { stringResource(R.string.audio_output_for_app, it) }
            ?: stringResource(R.string.audio_output_system), style = MaterialTheme.typography.bodyMedium, color = MutedText)
        Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.devices.forEach { device ->
                key(device.id) {
                    AudioOutputRow(device, pending = state.pending == device.id, enabled = device.available && state.pending == null) {
                        viewModel.select(device.id)
                    }
                }
            }
        }
        val issue = when (state.issue) {
            AudioOutputState.Issue.ACCESS_REQUIRED -> R.string.audio_output_access
            AudioOutputState.Issue.UNSUPPORTED -> R.string.audio_output_unsupported
            AudioOutputState.Issue.TRANSFER_FAILED -> R.string.audio_output_failed
            null -> null
        }
        if (issue != null) Text(stringResource(issue), style = MaterialTheme.typography.bodyMedium, color = MutedText)
        else if (state.devices.isEmpty()) Text(stringResource(R.string.audio_output_empty), style = MaterialTheme.typography.bodyMedium, color = MutedText)
        Text(stringResource(R.string.audio_output_routing_note), style = MaterialTheme.typography.bodySmall, color = MutedText)
    }
}

@Composable
private fun AudioOutputRow(device: com.deskdeck.media.AudioOutputDevice, pending: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val motion = LocalMotion.current
    val tint by animateColorAsState(
        if (device.selected) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.025f),
        motion.color(), label = "outputSelectionTint",
    )
    val iconTint = if (device.selected) CappedWhite else MutedText
    Row(
        Modifier.fillMaxWidth().pressScale(interaction, pressedScale = 0.98f)
            .clip(RoundedCornerShape(22.dp)).background(tint)
            .selectable(selected = device.selected, role = Role.RadioButton, enabled = enabled,
                interactionSource = interaction, indication = ripple(), onClick = onClick)
            .heightIn(min = 80.dp).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            SpeakerIcon(SpeakerLevel.HIGH, color = iconTint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(device.name, style = MaterialTheme.typography.titleMedium, color = if (device.available) CappedWhite else MutedText)
            val detail = when {
                pending -> R.string.audio_output_connecting
                device.selected -> R.string.audio_output_current
                !device.available -> R.string.audio_output_not_available
                else -> null
            }
            if (detail != null) Text(stringResource(detail), style = MaterialTheme.typography.bodySmall, color = MutedText)
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (pending) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = CappedWhite)
            else if (device.selected) Icon(painterResource(R.drawable.ic_check), contentDescription = null,
                tint = CappedWhite, modifier = Modifier.size(24.dp))
        }
    }
}
