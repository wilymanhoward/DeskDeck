package com.deskdeck.ui.call

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deskdeck.R
import com.deskdeck.media.call.CallState
import com.deskdeck.ui.theme.CappedWhite
import com.deskdeck.ui.theme.Dimens
import com.deskdeck.ui.theme.MutedText
import com.deskdeck.ui.theme.glass
import kotlinx.coroutines.delay

/** The call layout's card: who, voice or video, a running timer, and the call's own buttons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CallCard(
    state: CallState,
    hideContent: Boolean,
    canOpenBeside: Boolean,
    onOpenBeside: () -> Unit,
    onHangUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val call = state.callOrNull ?: return
    val kind = stringResource(if (call.isVideo) R.string.call_kind_video else R.string.call_kind_voice, call.appLabel)
    val title = if (hideContent || call.caller == null) kind else call.caller
    val ended = state is CallState.Ended
    val status = when (state) {
        is CallState.Ongoing -> state.startedAtMs?.let { callTimer(it) } ?: stringResource(R.string.call_connecting)
        is CallState.Ended -> stringResource(R.string.call_ended)
        else -> ""
    }
    Column(
        modifier
            .glass(RoundedCornerShape(Dimens.cardCorner))
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(AnswerGreen.copy(alpha = if (ended) 0.08f else 0.18f)), contentAlignment = Alignment.Center) {
                val avatar = call.avatar?.takeUnless { hideContent }
                if (avatar != null) {
                    val image = remember(avatar) { avatar.asImageBitmap() }
                    Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(CircleShape))
                } else {
                    Icon(painterResource(if (call.isVideo) R.drawable.ic_videocam else R.drawable.ic_handset), null, tint = if (ended) MutedText else AnswerGreen, modifier = Modifier.size(26.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = CappedWhite, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(if (call.isVideo) R.drawable.ic_videocam else R.drawable.ic_handset), null, tint = MutedText, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    // The timer is read out only when it changes state (connecting → time → ended), not every second.
                    Text(
                        text = if (hideContent || call.caller == null) status else "$kind · $status",
                        style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                        color = if (ended) MutedText else AnswerGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { if (ended) liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
        if (!ended && (canOpenBeside || call.actions.hangUp != null)) {
            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (canOpenBeside) {
                    BesideButton(
                        label = stringResource(R.string.call_open_beside),
                        description = stringResource(R.string.call_open_beside_description, title),
                        night = false,
                        onClick = onOpenBeside,
                        modifier = Modifier.widthIn(min = 200.dp),
                    )
                }
                // Only when the app offers its own hang-up button.
                if (call.actions.hangUp != null) HangUpButton(stringResource(R.string.call_hang_up_description, title), onHangUp)
            }
        }
    }
}

@Composable
private fun HangUpButton(description: String, onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = Dimens.minTouch)
            .clip(RoundedCornerShape(28.dp))
            .background(DeclineRed)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_handset), null, tint = Color.White, modifier = Modifier.size(22.dp).rotate(0f))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.call_hang_up), style = MaterialTheme.typography.titleSmall, color = Color.White)
    }
}

/** "4:07", "1:02:45": time since [startedAtMs], ticking on the second. */
@Composable
private fun callTimer(startedAtMs: Long): String {
    val elapsed by produceState(System.currentTimeMillis() - startedAtMs, startedAtMs) {
        while (true) {
            value = System.currentTimeMillis() - startedAtMs
            delay(1_000L - value.mod(1_000L))
        }
    }
    val total = (elapsed / 1_000).coerceAtLeast(0)
    val h = total / 3_600
    val m = total % 3_600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
