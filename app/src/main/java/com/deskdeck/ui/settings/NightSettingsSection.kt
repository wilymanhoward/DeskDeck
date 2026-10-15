package com.deskdeck.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.deskdeck.R
import com.deskdeck.data.settings.BriefDuration
import com.deskdeck.data.settings.ClockFormat
import com.deskdeck.data.settings.NightMusicStyle
import com.deskdeck.data.settings.NightClockStyle
import com.deskdeck.data.settings.NightSettings
import com.deskdeck.data.settings.RedTint
import com.deskdeck.data.settings.SettingsRanges
import com.deskdeck.data.settings.TemperatureUnit
import com.deskdeck.ui.theme.CappedWhite
import com.deskdeck.ui.theme.Dimens
import com.deskdeck.ui.theme.MutedText
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Settings › Night Mode: schedule, clock, night screen, Morning Brief, weather and market. */
@Composable
internal fun NightSettingsSection(n: NightSettings, vm: SettingsViewModel) {
    val context = LocalContext.current
    SettingCard {
        SwitchRow(stringResource(R.string.night_enabled), stringResource(R.string.night_enabled_detail), n.enabled) { v ->
            vm.updateNight { it.copy(enabled = v) }
        }
        ActionRow(
            title = stringResource(R.string.night_start_now),
            subtitle = stringResource(R.string.night_start_now_detail),
            actionLabel = stringResource(R.string.night_start),
            onClick = vm::startNightNow,
        )
    }

    SettingCard {
        val weekdayLabel = if (n.weekendDifferent) stringResource(R.string.night_weekdays) else null
        TimeRow(stringResource(R.string.night_bedtime), weekdayLabel, n.weekdays.bedtimeMin, n.clockFormat, n.enabled) { m ->
            vm.updateNight { it.copy(weekdays = it.weekdays.copy(bedtimeMin = m)) }
        }
        TimeRow(stringResource(R.string.night_wake), weekdayLabel, n.weekdays.wakeMin, n.clockFormat, n.enabled) { m ->
            vm.updateNight { it.copy(weekdays = it.weekdays.copy(wakeMin = m)) }
        }
        SwitchRow(stringResource(R.string.night_weekend_different), stringResource(R.string.night_weekend_different_detail), n.weekendDifferent, n.enabled) { v ->
            vm.updateNight { it.copy(weekendDifferent = v) }
        }
        if (n.weekendDifferent) {
            val weekendLabel = stringResource(R.string.night_weekend)
            TimeRow(stringResource(R.string.night_bedtime), weekendLabel, n.weekend.bedtimeMin, n.clockFormat, n.enabled) { m ->
                vm.updateNight { it.copy(weekend = it.weekend.copy(bedtimeMin = m)) }
            }
            TimeRow(stringResource(R.string.night_wake), weekendLabel, n.weekend.wakeMin, n.clockFormat, n.enabled) { m ->
                vm.updateNight { it.copy(weekend = it.weekend.copy(wakeMin = m)) }
            }
        }
        WakeDayChips(n.wakeDays, n.enabled) { days -> vm.updateNight { it.copy(wakeDays = days) } }
    }

    SettingCard {
        ChoiceRow(
            title = stringResource(R.string.night_clock_format),
            options = ClockFormat.entries,
            selected = n.clockFormat,
            label = {
                stringResource(
                    when (it) {
                        ClockFormat.SYSTEM -> R.string.settings_clock_system
                        ClockFormat.H12 -> R.string.settings_clock_12
                        ClockFormat.H24 -> R.string.settings_clock_24
                    },
                )
            },
            onSelect = { v -> vm.updateNight { it.copy(clockFormat = v) } },
        )
    }
    SettingCard {
        ClockFaceGallery(
            n = n,
            onStyle = { v -> vm.updateNight { it.copy(clockStyle = v) } },
            onColor = { v -> vm.updateNight { it.copy(clockColor = v) } },
        )
    }
    SettingCard {
        ChoiceRow(
            title = stringResource(R.string.night_music_style),
            subtitle = stringResource(R.string.night_music_style_detail),
            options = NightMusicStyle.entries,
            selected = n.musicStyle,
            label = {
                stringResource(
                    when (it) {
                        NightMusicStyle.PANEL -> R.string.night_music_panel
                        NightMusicStyle.BAR -> R.string.night_music_bar
                        NightMusicStyle.HIDDEN -> R.string.night_music_hidden
                    },
                )
            },
            onSelect = { v -> vm.updateNight { it.copy(musicStyle = v) } },
        )
    }
    SettingCard {
        val hasLightSensor = remember { hasLightSensor(context) }
        ChoiceRow(
            title = stringResource(R.string.night_red_tint),
            subtitle = stringResource(if (hasLightSensor) R.string.night_red_tint_detail else R.string.night_red_tint_detail_no_sensor),
            options = RedTint.entries,
            selected = n.redTint,
            label = {
                stringResource(
                    when (it) {
                        RedTint.OFF -> R.string.night_tint_off
                        RedTint.ON -> R.string.night_tint_on
                        RedTint.AUTO -> R.string.night_tint_auto
                    },
                )
            },
            onSelect = { v -> vm.updateNight { it.copy(redTint = v) } },
        )
        SliderRow(
            title = stringResource(R.string.night_brightness),
            subtitle = stringResource(R.string.night_brightness_detail),
            value = n.brightness,
            range = SettingsRanges.nightBrightness,
            steps = 0,
            valueLabel = { stringResource(R.string.settings_percent, (it * 100).roundToInt()) },
            onValueChangeFinished = { v -> vm.updateNight { it.copy(brightness = v) } },
        )
    }

    SettingCard {
        SwitchRow(stringResource(R.string.brief_enabled), stringResource(R.string.brief_enabled_detail), n.brief) { v ->
            vm.updateNight { it.copy(brief = v) }
        }
        ChoiceRow(
            title = stringResource(R.string.brief_duration),
            options = BriefDuration.entries,
            selected = n.briefDuration,
            label = { d -> d.minutes?.let { stringResource(R.string.brief_minutes, it) } ?: stringResource(R.string.brief_until_dismissed) },
            enabled = n.brief,
            onSelect = { v -> vm.updateNight { it.copy(briefDuration = v) } },
        )
        SwitchRow(stringResource(R.string.brief_weather), null, n.briefWeather, n.brief) { v -> vm.updateNight { it.copy(briefWeather = v) } }
        SwitchRow(stringResource(R.string.brief_market), null, n.briefMarket, n.brief) { v -> vm.updateNight { it.copy(briefMarket = v) } }
        SwitchRow(stringResource(R.string.brief_headlines), null, n.briefHeadlines, n.brief) { v -> vm.updateNight { it.copy(briefHeadlines = v) } }
        ActionRow(
            title = stringResource(R.string.brief_show_now),
            subtitle = stringResource(R.string.brief_show_now_detail),
            actionLabel = stringResource(R.string.brief_show),
            onClick = vm::showBriefNow,
        )
    }

    SettingCard {
        WeatherLocation(n, vm)
        ChoiceRow(
            title = stringResource(R.string.weather_unit),
            options = TemperatureUnit.entries,
            selected = n.temperatureUnit,
            label = { stringResource(if (it == TemperatureUnit.CELSIUS) R.string.weather_celsius else R.string.weather_fahrenheit) },
            onSelect = { v -> vm.updateNight { it.copy(temperatureUnit = v) } },
        )
    }

    SettingCard { Watchlist(n.watchlist) { list -> vm.updateNight { it.copy(watchlist = list) } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeRow(title: String, label: String?, minutes: Int, format: ClockFormat, enabled: Boolean, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    val is24 = when (format) {
        ClockFormat.H24 -> true
        ClockFormat.H12 -> false
        ClockFormat.SYSTEM -> DateFormat.is24HourFormat(context)
    }
    var picking by rememberSaveable { mutableStateOf(false) }
    val time = LocalTime.of(minutes / 60, minutes % 60).format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a"))
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouch)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = title) { picking = true }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(title, label, enabled, Modifier.weight(1f))
        Text(time, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), color = CappedWhite.copy(alpha = if (enabled) 1f else 0.4f))
    }
    if (picking) {
        val state = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60, is24Hour = is24)
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    onPick(state.hour * 60 + state.minute)
                    picking = false
                }) { Text(stringResource(R.string.night_time_ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.night_time_cancel)) } },
            title = { Text(listOfNotNull(title, label).joinToString(" · ")) },
            text = { TimePicker(state = state) },
        )
    }
}

@Composable
private fun WakeDayChips(days: Set<DayOfWeek>, enabled: Boolean, onChange: (Set<DayOfWeek>) -> Unit) {
    // Monday-first, in the device's language.
    val locale = Locale.getDefault()
    androidx.compose.foundation.layout.Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
        RowText(stringResource(R.string.night_days), stringResource(R.string.night_days_detail), enabled)
        Spacer(Modifier.size(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DayOfWeek.entries.forEach { day ->
                val on = day in days
                FilterChip(
                    selected = on,
                    enabled = enabled,
                    onClick = { onChange(if (on) days - day else days + day) },
                    label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun WeatherLocation(n: NightSettings, vm: SettingsViewModel) {
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        vm.updateNight { it.copy(useDeviceLocation = granted) }
    }
    SwitchRow(
        title = stringResource(R.string.weather_device_location),
        subtitle = stringResource(if (denied) R.string.weather_device_location_denied else R.string.weather_device_location_detail),
        checked = n.useDeviceLocation,
    ) { on ->
        if (!on) {
            vm.updateNight { it.copy(useDeviceLocation = false) }
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            vm.updateNight { it.copy(useDeviceLocation = true) }
        } else {
            askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
    if (!n.useDeviceLocation) {
        var city by rememberSaveable(n.city) { mutableStateOf(n.city) }
        OutlinedTextField(
            value = city,
            onValueChange = { city = it },
            label = { Text(stringResource(R.string.weather_city)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.updateNight { it.copy(city = city.trim()) } }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .onFocusLostSave { vm.updateNight { it.copy(city = city.trim()) } },
        )
    }
}

@Composable
private fun Watchlist(items: List<String>, onChange: (List<String>) -> Unit) {
    RowText(
        title = stringResource(R.string.market_watchlist),
        subtitle = stringResource(R.string.market_watchlist_detail),
        enabled = true,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
    )
    items.forEachIndexed { i, item ->
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(item, style = MaterialTheme.typography.titleMedium, color = CappedWhite, modifier = Modifier.weight(1f))
            if (i > 0) {
                val moveLabel = stringResource(R.string.market_move_up_description, item)
                TextButton(
                    onClick = { onChange(items.toMutableList().apply { add(i - 1, removeAt(i)) }) },
                    modifier = Modifier.semantics { contentDescription = moveLabel },
                ) { Text(stringResource(R.string.market_move_up)) }
            }
            IconButton(onClick = { onChange(items - item) }, modifier = Modifier.size(Dimens.minTouch)) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.market_remove, item), tint = MutedText)
            }
        }
    }
    var draft by rememberSaveable { mutableStateOf("") }
    val add = {
        val name = draft.trim()
        if (name.isNotEmpty() && name !in items) onChange(items + name)
        draft = ""
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text(stringResource(R.string.market_add_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        TextButton(onClick = add, enabled = draft.isNotBlank()) { Text(stringResource(R.string.market_add)) }
    }
}

private fun hasLightSensor(context: Context): Boolean =
    context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_LIGHT) != null

/** Saves when the field loses focus, so a typed city isn't lost if Done isn't pressed. */
private fun Modifier.onFocusLostSave(save: () -> Unit): Modifier = this.then(
    Modifier.onFocusChanged { if (!it.isFocused) save() },
)
