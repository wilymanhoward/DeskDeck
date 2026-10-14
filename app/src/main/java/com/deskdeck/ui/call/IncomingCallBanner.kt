package com.deskdeck.ui.call

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deskdeck.R
import com.deskdeck.burnin.KeepScreenOn
import com.deskdeck.burnin.LocalInteractionTracker
import com.deskdeck.media.call.Call
import com.deskdeck.media.call.CallState
import com.deskdeck.ui.night.LocalNightMode
import com.deskdeck.ui.notifications.DenseGlass
import com.deskdeck.ui.settings.LocalAppSettings
import com.deskdeck.ui.theme.CappedWhite
import com.deskdeck.ui.theme.Dimens
import com.deskdeck.ui.theme.LocalMotion
import com.deskdeck.ui.theme.MotionSpec
import com.deskdeck.ui.theme.MutedText
import com.deskdeck.ui.theme.glass
import kotlinx.coroutines.launch

/** Apple's call colors. */
internal val AnswerGreen = Color(0xFF34C759)
internal val DeclineRed = Color(0xFFFF3B30)

/**
 * Incoming calls, above every screen (Night Mode too, dimmed): a large glass banner at the top
 * with the caller, and Decline / Answer (and Answer beside DeskDeck where split screen works).
 * It stays until the call is answered, declined or missed; it only goes away early with its ×
 * or a deliberate swipe up. When Do Not Disturb would keep the phone quiet, only a small
 * indicator shows; tapping it opens the banner.
 */
@Composable
fun IncomingCallHost(viewModel: CallViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val closedKey by viewModel.closedKey.collectAsStateWithLifecycle()
    val expandedKey by viewModel.expandedKey.collectAsStateWithLifecycle()
    val hint by viewModel.splitHint.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current.notifications
    val night = LocalNightMode.current
    val motion = LocalMotion.current
    val tracker = LocalInteractionTracker.current
    val activity = LocalContext.current.findActivity()

    val callSettings = LocalAppSettings.current.calls
    val incoming = (state as? CallState.Incoming)?.call
        ?.takeIf { it.key != closedKey && callSettings.enabled && callSettings.incomingBanner }
    // Kept while the banner animates out after the call stops ringing.
    var lastCall by remember { mutableStateOf<Call?>(null) }
    if (incoming != null) lastCall = incoming
    val quiet = incoming != null && settings.respectDnd && !incoming.ringsThroughDnd && incoming.key != expandedKey

    // A ringing call wakes a dimmed screen (not the night screen: the banner shows dimmed there).
    LaunchedEffect(incoming?.key, quiet) {
        if (incoming != null && !quiet && !night) tracker.onInteraction(force = true)
    }
    if (incoming != null) KeepScreenOn(enabled = true)

    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
        val width = min(560.dp, maxWidth - Dimens.screenPadding * 2)
        AnimatedVisibility(
            visible = incoming != null && !quiet,
            enter = if (motion.reduced) fadeIn(motion.fade()) else fadeIn(motion.fade()) + slideInVertically(motion.bouncy()) { -it } + scaleIn(motion.bouncy(), initialScale = 0.9f),
            exit = if (motion.reduced) fadeOut(motion.fade()) else fadeOut(motion.fade()) + slideOutVertically(motion.gentle()) { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = Dimens.screenPadding),
        ) {
            val call = incoming ?: lastCall ?: return@AnimatedVisibility
            IncomingCallBanner(
                call = call,
                hideContent = settings.hideContent,
                night = night,
                canAnswerBeside = activity != null && viewModel.canOpenBeside(activity, call),
                onAnswer = { activity?.let { viewModel.answer(it, call) } },
                onDecline = { activity?.let { viewModel.decline(it, call) } },
                onAnswerBeside = { activity?.let { viewModel.answerBeside(it, call) } },
                onOpenApp = { activity?.let { viewModel.openApp(it, call) } },
                onClose = { viewModel.close(call) },
                modifier = Modifier.width(width),
            )
        }
        AnimatedVisibility(
            visible = quiet,
            enter = fadeIn(motion.fade()),
            exit = fadeOut(motion.fade()),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = Dimens.screenPadding),
        ) {
            (incoming ?: lastCall)?.let { QuietCallIndicator(it, settings.hideContent, onClick = { viewModel.expand(it) }) }
        }
        AnimatedVisibility(
            visible = hint != null,
            enter = fadeIn(motion.fade()),
            exit = fadeOut(motion.fade(MotionSpec.LONG_MS)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
        ) {
            SplitHint(hint.orEmpty())
        }
    }
}

@Composable
fun IncomingCallBanner(
    call: Call,
    hideContent: Boolean,
    night: Boolean,
    canAnswerBeside: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onAnswerBeside: () -> Unit,
    onOpenApp: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotion.current
    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val dismissPx = with(LocalDensity.current) { 96.dp.toPx() }
    val currentOnClose by rememberUpdatedState(onClose)
    val kind = stringResource(if (call.isVideo) R.string.call_kind_video else R.string.call_kind_voice, call.appLabel)
    // Privacy: "WhatsApp video call" instead of the name, and no picture.
    val title = if (hideContent || call.caller == null) kind else call.caller
    val subtitle = if (hideContent || call.caller == null) stringResource(R.string.call_incoming) else kind
    val closeLabel = stringResource(R.string.call_banner_close)
    val hasButtons = call.actions.answer != null && call.actions.decline != null

    Column(
        modifier
            // Night Mode: the same banner, dimmed but readable.
            .graphicsLayer {
                translationY = offsetY.value
                alpha = (if (night) 0.75f else 1f) * (1f + offsetY.value / (dismissPx * 2)).coerceIn(0f, 1f)
            }
            .glass(RoundedCornerShape(36.dp), tint = if (night) Color.Black.copy(alpha = 0.92f) else DenseGlass)
            // Only a deliberate swipe up hides it; small drags spring back.
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        val next = offsetY.value + if (offsetY.value + dy > 0) dy * 0.15f else dy
                        scope.launch { offsetY.snapTo(next) }
                    },
                    onDragEnd = {
                        scope.launch {
                            if (offsetY.value < -dismissPx) {
                                offsetY.animateTo(-dismissPx * 3, motion.snappy())
                                currentOnClose()
                            } else {
                                offsetY.animateTo(0f, motion.bouncy())
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offsetY.animateTo(0f, motion.bouncy()) } },
                )
            }
            .semantics {
                paneTitle = kind
                liveRegion = LiveRegionMode.Assertive
                customActions = listOf(CustomAccessibilityAction(closeLabel) { currentOnClose(); true })
            }
            .padding(start = 24.dp, end = 12.dp, top = 20.dp, bottom = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CallerAvatar(call, hideContent, night)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = CappedWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(if (call.isVideo) R.drawable.ic_videocam else R.drawable.ic_handset), null, tint = MutedText, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(subtitle, style = MaterialTheme.typography.titleMedium, color = MutedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(Dimens.minTouch)) {
                Icon(painterResource(R.drawable.ic_close), closeLabel, tint = MutedText)
            }
        }
        Spacer(Modifier.height(20.dp))
        if (hasButtons) {
            Row(
                Modifier.fillMaxWidth().padding(end = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top,
            ) {
                RoundCallButton(stringResource(R.string.call_decline), stringResource(R.string.call_decline_description, title), DeclineRed, rotation = 0f, night, onDecline)
                RoundCallButton(stringResource(R.string.call_answer), stringResource(R.string.call_answer_description, title), AnswerGreen, rotation = -135f, night, onAnswer)
            }
            if (canAnswerBeside) {
                Spacer(Modifier.height(14.dp))
                BesideButton(
                    label = stringResource(R.string.call_answer_beside),
                    description = stringResource(R.string.call_answer_beside_description, title),
                    night = night,
                    onClick = onAnswerBeside,
                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                )
            }
        } else {
            // The app's own buttons couldn't be identified: only a way to its call screen.
            OutlinedButton(onClick = onOpenApp, modifier = Modifier.fillMaxWidth().padding(end = 12.dp).heightIn(min = Dimens.minTouch)) {
                Text(stringResource(R.string.call_open_app, call.appLabel), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** The caller's picture (or a handset) with a soft ring that breathes while it rings. */
@Composable
private fun CallerAvatar(call: Call, hideContent: Boolean, night: Boolean) {
    val motion = LocalMotion.current
    val pulse = if (motion.reduced) {
        remember { androidx.compose.runtime.mutableFloatStateOf(0.5f) }
    } else {
        rememberInfiniteTransition(label = "callPulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(PULSE_MS, easing = LinearEasing), RepeatMode.Reverse),
            label = "callPulseValue",
        )
    }
    val ring = if (night) MutedText else AnswerGreen
    Box(
        Modifier
            .size(72.dp)
            // Read in the draw phase: the pulse never recomposes.
            .drawBehind {
                val p = pulse.value
                drawCircle(ring.copy(alpha = 0.12f + 0.25f * (1f - p)), radius = size.minDimension / 2 * (0.92f + 0.08f * p))
                drawCircle(ring.copy(alpha = 0.55f - 0.3f * p), radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) {
        val avatar = call.avatar?.takeUnless { hideContent }
        if (avatar != null) {
            val image = remember(avatar) { avatar.asImageBitmap() }
            Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.size(60.dp).clip(CircleShape).graphicsLayer { alpha = if (night) 0.6f else 1f })
        } else {
            Box(Modifier.size(60.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(if (call.isVideo) R.drawable.ic_videocam else R.drawable.ic_handset), null, tint = CappedWhite, modifier = Modifier.size(30.dp))
            }
        }
    }
}

/** A wide green-tinted pill: answer (or open the call) beside DeskDeck in split screen. */
@Composable
internal fun BesideButton(label: String, description: String, night: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val green = AnswerGreen.copy(alpha = if (night) 0.55f else 1f)
    Row(
        modifier
            .heightIn(min = Dimens.minTouch)
            .clip(RoundedCornerShape(28.dp))
            .background(green.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_split_screen), null, tint = green, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = green, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RoundCallButton(
    label: String,
    description: String,
    color: Color,
    rotation: Float,
    night: Boolean,
    onClick: () -> Unit,
) {
    val fill = color.copy(alpha = if (night) 0.55f else 1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(112.dp)) {
        Box(
            Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(fill)
                .clickable(onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_handset), null, tint = Color.White, modifier = Modifier.size(32.dp).rotate(rotation))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = CappedWhite, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** Do Not Disturb would keep the phone quiet: a small pill instead of the banner. */
@Composable
private fun QuietCallIndicator(call: Call, hideContent: Boolean, onClick: () -> Unit) {
    val text = if (hideContent || call.caller == null) {
        stringResource(if (call.isVideo) R.string.call_kind_video else R.string.call_kind_voice, call.appLabel)
    } else {
        call.caller
    }
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .glass(RoundedCornerShape(24.dp), tint = DenseGlass)
            .clickable(onClickLabel = stringResource(R.string.call_quiet_open), onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(if (call.isVideo) R.drawable.ic_videocam else R.drawable.ic_handset), null, tint = AnswerGreen, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = CappedWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.call_quiet_dnd), style = MaterialTheme.typography.labelMedium, color = MutedText)
    }
}

@Composable
internal fun SplitHint(appLabel: String) {
    Text(
        text = stringResource(R.string.call_split_hint, appLabel),
        style = MaterialTheme.typography.titleSmall,
        color = CappedWhite,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .glass(RoundedCornerShape(24.dp), tint = DenseGlass)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val PULSE_MS = 1_400
