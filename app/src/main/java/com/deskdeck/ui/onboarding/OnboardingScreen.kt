package com.deskdeck.ui.onboarding

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.deskdeck.R
import com.deskdeck.ui.theme.CappedWhite
import com.deskdeck.ui.theme.DeskDeckTheme
import com.deskdeck.ui.theme.Dimens
import com.deskdeck.ui.theme.MutedText
import com.deskdeck.ui.theme.SurfaceHigh
import com.deskdeck.ui.theme.SurfaceHighest
import com.deskdeck.ui.theme.SurfaceLow
import com.deskdeck.ui.theme.TrueBlack

@Composable
fun OnboardingScreen(
    onOpenSettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onSkip: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(TrueBlack)
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(Dimens.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(Dimens.zoneGap),
    ) {
        Illustration(
            Modifier
                .weight(0.4f)
                .fillMaxHeight(),
        )
        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.onb_title),
                style = MaterialTheme.typography.headlineMedium,
                color = CappedWhite,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(16.dp))
            HtmlText(R.string.onb_body, MaterialTheme.typography.bodyLarge, CappedWhite.copy(alpha = 0.9f))
            Spacer(Modifier.height(28.dp))

            Step(1, R.string.onb_step_1)
            Step(2, R.string.onb_step_2)
            Step(3, R.string.onb_step_3)

            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.heightIn(min = Dimens.minTouch),
                ) {
                    Text(stringResource(R.string.onb_open_settings), style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.size(12.dp))
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.heightIn(min = Dimens.minTouch),
                ) {
                    Text(stringResource(R.string.onb_not_now), style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(28.dp))
            HtmlText(R.string.onb_privacy, MaterialTheme.typography.bodyMedium, MutedText)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spacer(Modifier.height(12.dp))
                HtmlText(R.string.onb_restricted, MaterialTheme.typography.bodyMedium, MutedText)
                TextButton(
                    onClick = onOpenAppInfo,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .offset(x = (-12).dp),
                ) {
                    Text(stringResource(R.string.onb_open_app_info))
                }
            }
        }
    }
}

@Composable
private fun Step(number: Int, textRes: Int) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(SurfaceHighest, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.size(16.dp))
        HtmlText(textRes, MaterialTheme.typography.titleMedium, CappedWhite)
    }
}

@Composable
private fun HtmlText(
    textRes: Int,
    style: TextStyle,
    color: Color,
) {
    val html = stringResource(textRes)
    val text = remember(html) { AnnotatedString.fromHtml(html) }
    Text(text = text, style = style, color = color, modifier = Modifier.widthIn(max = 640.dp))
}

/** A stylized "now playing" tile with a notification bell: what access unlocks. */
@Composable
private fun Illustration(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.cardCorner))
            .background(Brush.verticalGradient(listOf(SurfaceHigh, SurfaceLow))),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerHighest),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_music_note),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .aspectRatio(1f),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(52.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_notifications),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun OnboardingPreview() {
    DeskDeckTheme {
        OnboardingScreen(onOpenSettings = {}, onOpenAppInfo = {}, onSkip = {})
    }
}
