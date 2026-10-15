package com.deskdeck.ui.settings

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deskdeck.R
import com.deskdeck.data.settings.ClockFormat
import com.deskdeck.data.settings.NightClockColor
import com.deskdeck.data.settings.NightClockStyle
import com.deskdeck.data.settings.NightSettings
import com.deskdeck.ui.common.rememberCurrentTime
import com.deskdeck.ui.night.faces.ClockFace
import com.deskdeck.ui.night.faces.FaceInfo
import com.deskdeck.ui.night.faces.facePalette
import com.deskdeck.ui.night.nextAlarm
import com.deskdeck.ui.theme.CappedWhite
import com.deskdeck.ui.theme.MutedText

/**
 * Settings › Night Mode › Clock face: live previews of every face side by side (like choosing a
 * StandBy face), then the color. Previews show the current time and next alarm.
 */
@Composable
internal fun ClockFaceGallery(n: NightSettings, onStyle: (NightClockStyle) -> Unit, onColor: (NightClockColor) -> Unit) {
    val context = LocalContext.current
    val now by rememberCurrentTime(withSeconds = false)
    val is24 = when (n.clockFormat) {
        ClockFormat.H24 -> true
        ClockFormat.H12 -> false
        ClockFormat.SYSTEM -> DateFormat.is24HourFormat(context)
    }
    val alarm = remember(now.hour, now.minute, is24) { nextAlarm(context, is24) }
    val palette = facePalette(n.clockColor, red = false)
    val accent = MaterialTheme.colorScheme.primary
    val listState = rememberLazyListState()
    // Open with the chosen face in view.
    LaunchedEffect(Unit) { listState.scrollToItem(NightClockStyle.entries.indexOf(n.clockStyle).coerceAtLeast(0)) }

    Column(Modifier.padding(vertical = 14.dp)) {
        RowText(
            title = stringResource(R.string.night_clock_face),
            subtitle = stringResource(R.string.night_clock_face_detail),
            enabled = true,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(14.dp))
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(NightClockStyle.entries) { style ->
                val selected = style == n.clockStyle
                val name = stringResource(faceName(style))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(232.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onStyle(style) }
                        .semantics {
                            this.selected = selected
                            role = Role.RadioButton
                            contentDescription = name
                        }
                        .padding(4.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black)
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) accent else Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(20.dp),
                            )
                            .padding(8.dp),
                    ) {
                        ClockFace(style, palette, FaceInfo(now, is24, alarm, place = null), Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) CappedWhite else MutedText,
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.night_clock_color),
            style = MaterialTheme.typography.titleMedium,
            color = CappedWhite,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            NightClockColor.entries.forEach { color ->
                val selected = color == n.clockColor
                val name = stringResource(colorName(color))
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onColor(color) }
                        .semantics {
                            this.selected = selected
                            role = Role.RadioButton
                            contentDescription = name
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // A ring around the chosen color, like iOS's color pickers.
                    if (selected) Box(Modifier.size(46.dp).border(2.dp, CappedWhite, CircleShape))
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color(color.argb)))
                }
            }
        }
        Text(
            stringResource(R.string.night_clock_color_detail),
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp),
        )
    }
}

private fun faceName(style: NightClockStyle): Int = when (style) {
    NightClockStyle.BOLD -> R.string.night_style_bold
    NightClockStyle.THIN -> R.string.night_style_thin
    NightClockStyle.ANALOG -> R.string.night_style_analog
    NightClockStyle.LAYERED -> R.string.night_style_layered
    NightClockStyle.SERIF -> R.string.night_style_serif
    NightClockStyle.CALENDAR -> R.string.night_style_calendar
    NightClockStyle.WORLD -> R.string.night_style_world
}

private fun colorName(color: NightClockColor): Int = when (color) {
    NightClockColor.WHITE -> R.string.color_white
    NightClockColor.RED -> R.string.color_red
    NightClockColor.ORANGE -> R.string.color_orange
    NightClockColor.YELLOW -> R.string.color_yellow
    NightClockColor.GREEN -> R.string.color_green
    NightClockColor.TEAL -> R.string.color_teal
    NightClockColor.BLUE -> R.string.color_blue
    NightClockColor.PURPLE -> R.string.color_purple
    NightClockColor.PINK -> R.string.color_pink
}
