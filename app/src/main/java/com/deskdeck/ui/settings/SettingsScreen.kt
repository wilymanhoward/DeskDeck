package com.deskdeck.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deskdeck.BuildConfig
import com.deskdeck.R
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalContext
import com.deskdeck.ui.spotify.openSignIn
import com.deskdeck.ui.spotify.errorText
import com.deskdeck.data.spotify.SpotifyAccount
import com.deskdeck.data.settings.AccentColor
import com.deskdeck.data.settings.AppSettings
import com.deskdeck.data.settings.ClockFormat
import com.deskdeck.data.settings.KeepScreenOn
import com.deskdeck.data.settings.SettingsRanges
import com.deskdeck.data.settings.SourceMode
import com.deskdeck.ui.components.FluidBackground
import com.deskdeck.ui.lyrics.Romanizer
import com.deskdeck.ui.picker.AppPickerOverlay
import com.deskdeck.ui.picker.PickerMode
import com.deskdeck.ui.theme.ActiveLyricWhite
import com.deskdeck.ui.theme.CappedWhite
import com.deskdeck.ui.theme.Dimens
import com.deskdeck.ui.theme.MutedText
import com.deskdeck.ui.theme.TrueBlack
import com.deskdeck.ui.theme.glass
import com.deskdeck.ui.theme.screenEntrance
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private enum class Category(val titleRes: Int) {
    SCREEN(R.string.settings_cat_screen),
    SOURCES(R.string.settings_cat_sources),
    NOTIFICATIONS(R.string.settings_cat_notifications),
    LYRICS(R.string.settings_cat_lyrics),
    CLOCK(R.string.settings_cat_clock),
    APPEARANCE(R.string.settings_cat_appearance),
    SIDEBAR(R.string.settings_cat_sidebar),
}

/** Two-pane landscape settings: categories on the left, the selected category's cards on the right. */
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableStateOf(Category.SCREEN) }
    var picker by rememberSaveable { mutableStateOf<PickerMode?>(null) }
    BackHandler(enabled = picker == null, onBack = onBack)

    BoxWithConstraints(Modifier.fillMaxSize().background(TrueBlack).screenEntrance()) {
        FluidBackground(null, MaterialTheme.colorScheme.primary, animate = false, modifier = Modifier.fillMaxSize(), intensity = 0.45f)
        val sidebarWidth = if (maxWidth < 800.dp) 200.dp else 280.dp
        Row(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(Dimens.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(Dimens.zoneGap),
        ) {
            Column(Modifier.width(sidebarWidth).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(Dimens.minTouch)) {
                        Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.settings_back))
                    }
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = CappedWhite,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                Spacer(Modifier.height(16.dp))
                Category.entries.forEach { c ->
                    CategoryItem(stringResource(c.titleRes), selected = c == category) { category = c }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.settings_about, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(Dimens.cardCorner))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(category.titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    color = CappedWhite,
                    modifier = Modifier
                        .padding(start = 8.dp, top = 12.dp)
                        .semantics { heading() },
                )
                when (category) {
                    Category.SCREEN -> ScreenSettings(settings, viewModel)
                    Category.SOURCES -> SourceSettings(settings, viewModel)
                    Category.NOTIFICATIONS -> BannerSettings(settings, viewModel)
                    Category.LYRICS -> LyricsSettings(settings, viewModel)
                    Category.CLOCK -> ClockSettings(settings, viewModel)
                    Category.APPEARANCE -> AppearanceSettings(settings, viewModel)
                    Category.SIDEBAR -> SettingCard {
                        ActionRow(
                            title = stringResource(R.string.settings_sidebar_apps),
                            subtitle = stringResource(R.string.settings_sidebar_apps_detail),
                            actionLabel = stringResource(R.string.settings_edit),
                            onClick = { picker = PickerMode.EDIT },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        AppPickerOverlay(mode = picker, onDismiss = { picker = null })
    }
}

// region Categories

@Composable
private fun ScreenSettings(s: AppSettings, vm: SettingsViewModel) {
    val b = s.burnIn
    val minutes: @Composable (Int) -> String = { stringResource(R.string.settings_minutes, it) }
    SettingCard {
        ChoiceRow(
            title = stringResource(R.string.settings_keep_screen_on),
            subtitle = stringResource(R.string.settings_keep_screen_on_detail),
            options = KeepScreenOn.entries,
            selected = s.keepScreenOn,
            label = {
                stringResource(if (it == KeepScreenOn.ALWAYS) R.string.settings_keep_always else R.string.settings_keep_while_playing)
            },
            onSelect = { v -> vm.update { it.copy(keepScreenOn = v) } },
        )
    }
    SettingCard {
        SwitchRow(stringResource(R.string.settings_pixel_shift), stringResource(R.string.settings_pixel_shift_detail), b.pixelShift) { v ->
            vm.updateBurnIn { it.copy(pixelShift = v) }
        }
        SliderRow(
            title = stringResource(R.string.settings_pixel_shift_amount),
            value = b.pixelShiftDp.toFloat(),
            range = SettingsRanges.pixelShiftDp.first.toFloat()..SettingsRanges.pixelShiftDp.last.toFloat(),
            steps = SettingsRanges.pixelShiftDp.last - SettingsRanges.pixelShiftDp.first - 1,
            valueLabel = { stringResource(R.string.settings_dp, it.roundToInt()) },
            enabled = b.pixelShift,
            onValueChangeFinished = { v -> vm.updateBurnIn { it.copy(pixelShiftDp = v.roundToInt()) } },
        )
    }
    SettingCard {
        SwitchRow(stringResource(R.string.settings_idle_dim), stringResource(R.string.settings_idle_dim_detail), b.idleDim) { v ->
            vm.updateBurnIn { it.copy(idleDim = v) }
        }
        ChoiceRow(
            title = stringResource(R.string.settings_dim_after),
            options = SettingsRanges.minuteChoices,
            selected = b.dimAfterMin,
            label = minutes,
            enabled = b.idleDim,
            onSelect = { v -> vm.updateBurnIn { it.copy(dimAfterMin = v) } },
        )
        SliderRow(
            title = stringResource(R.string.settings_dim_level),
            value = b.dimLevel,
            range = SettingsRanges.dimLevel,
            steps = 5,
            valueLabel = { stringResource(R.string.settings_percent, (it * 100).roundToInt()) },
            enabled = b.idleDim,
            onValueChangeFinished = { v -> vm.updateBurnIn { it.copy(dimLevel = v) } },
        )
    }
    SettingCard {
        SwitchRow(stringResource(R.string.settings_ambient), stringResource(R.string.settings_ambient_detail), b.ambient) { v ->
            vm.updateBurnIn { it.copy(ambient = v) }
        }
        ChoiceRow(
            title = stringResource(if (b.idleDim) R.string.settings_ambient_after else R.string.settings_ambient_after_no_dim),
            options = SettingsRanges.minuteChoices,
            selected = b.ambientAfterMin,
            label = minutes,
            enabled = b.ambient,
            onSelect = { v -> vm.updateBurnIn { it.copy(ambientAfterMin = v) } },
        )
    }
    SettingCard {
        SwitchRow(stringResource(R.string.settings_paused_clock), stringResource(R.string.settings_paused_clock_detail), b.pausedClock) { v ->
            vm.updateBurnIn { it.copy(pausedClock = v) }
        }
        ChoiceRow(
            title = stringResource(R.string.settings_paused_after),
            options = SettingsRanges.pausedMinuteChoices,
            selected = b.pausedClockAfterMin,
            label = minutes,
            enabled = b.pausedClock,
            onSelect = { v -> vm.updateBurnIn { it.copy(pausedClockAfterMin = v) } },
        )
        SwitchRow(
            title = stringResource(R.string.settings_screen_off),
            subtitle = stringResource(R.string.settings_screen_off_detail),
            checked = b.screenOffWhenPaused,
            enabled = b.pausedClock && s.keepScreenOn == KeepScreenOn.ALWAYS,
        ) { v -> vm.updateBurnIn { it.copy(screenOffWhenPaused = v) } }
    }
    SettingCard {
        SliderRow(
            title = stringResource(R.string.settings_brightness_cap),
            subtitle = stringResource(R.string.settings_brightness_cap_detail),
            value = b.brightnessCap,
            range = SettingsRanges.brightnessCap,
            steps = 9,
            valueLabel = { stringResource(R.string.settings_percent, (it * 100).roundToInt()) },
            onValueChangeFinished = { v -> vm.updateBurnIn { it.copy(brightnessCap = v) } },
        )
        SwitchRow(stringResource(R.string.settings_rail_drift), stringResource(R.string.settings_rail_drift_detail), b.railOpacityDrift) { v ->
            vm.updateBurnIn { it.copy(railOpacityDrift = v) }
        }
    }
}

@Composable
private fun SourceSettings(s: AppSettings, vm: SettingsViewModel) {
    SettingCard {
        ChoiceRow(
            title = stringResource(R.string.settings_source_mode),
            subtitle = stringResource(R.string.settings_source_mode_detail),
            options = SourceMode.entries,
            selected = s.sourceMode,
            label = {
                stringResource(if (it == SourceMode.AUTO_FOLLOW) R.string.settings_source_follow else R.string.settings_source_pin)
            },
            onSelect = { v -> vm.update { it.copy(sourceMode = v) } },
        )
        SwitchRow(
            title = stringResource(R.string.settings_media_browser),
            subtitle = stringResource(R.string.settings_media_browser_detail),
            checked = s.mediaBrowserResume,
        ) { v -> vm.update { it.copy(mediaBrowserResume = v) } }
    }
    SettingCard {
        val account by vm.spotifyAccount.collectAsStateWithLifecycle()
        val context = LocalContext.current
        SwitchRow(
            title = stringResource(R.string.settings_spotify_library),
            subtitle = stringResource(R.string.settings_spotify_library_detail),
            checked = s.spotifyLibrary,
        ) { v -> vm.update { it.copy(spotifyLibrary = v) } }
        when (val a = account) {
            SpotifyAccount.NotConfigured -> RowText(
                title = stringResource(R.string.settings_spotify_account),
                subtitle = stringResource(R.string.library_setup_detail),
                enabled = s.spotifyLibrary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            )
            is SpotifyAccount.SignedIn -> ActionRow(
                title = stringResource(R.string.settings_spotify_account),
                subtitle = a.displayName?.let { stringResource(R.string.settings_spotify_connected, it) }
                    ?: stringResource(R.string.settings_spotify_connected_unnamed),
                actionLabel = stringResource(R.string.settings_spotify_disconnect),
                enabled = s.spotifyLibrary,
                onClick = vm::disconnectSpotify,
            )
            else -> ActionRow(
                title = stringResource(R.string.settings_spotify_account),
                subtitle = when (a) {
                    SpotifyAccount.SigningIn -> stringResource(R.string.settings_spotify_signing_in)
                    is SpotifyAccount.Failed -> errorText(a.error).second
                    else -> stringResource(R.string.settings_spotify_not_connected)
                },
                actionLabel = stringResource(R.string.settings_spotify_connect),
                enabled = s.spotifyLibrary,
                onClick = { openSignIn(context, vm.beginSpotifySignIn()) },
            )
        }
    }
}

@Composable
private fun BannerSettings(s: AppSettings, vm: SettingsViewModel) {
    val n = s.notifications
    val apps by vm.notificationApps.collectAsStateWithLifecycle()
    SettingCard {
        SwitchRow(stringResource(R.string.settings_banners), stringResource(R.string.settings_banners_detail), n.banners) { v ->
            vm.updateNotifications { it.copy(banners = v) }
        }
        SwitchRow(
            title = stringResource(R.string.settings_banner_hide_content),
            subtitle = stringResource(R.string.settings_banner_hide_content_detail),
            checked = n.hideContent,
            enabled = n.banners,
        ) { v -> vm.updateNotifications { it.copy(hideContent = v) } }
        SwitchRow(
            title = stringResource(R.string.settings_banner_dnd),
            subtitle = stringResource(R.string.settings_banner_dnd_detail),
            checked = n.respectDnd,
            enabled = n.banners,
        ) { v -> vm.updateNotifications { it.copy(respectDnd = v) } }
        SliderRow(
            title = stringResource(R.string.settings_banner_duration),
            value = n.durationSec.toFloat(),
            range = SettingsRanges.bannerDurationSec.first.toFloat()..SettingsRanges.bannerDurationSec.last.toFloat(),
            steps = SettingsRanges.bannerDurationSec.last - SettingsRanges.bannerDurationSec.first - 1,
            valueLabel = { stringResource(R.string.settings_seconds, it.roundToInt()) },
            enabled = n.banners,
            onValueChangeFinished = { v -> vm.updateNotifications { it.copy(durationSec = v.roundToInt()) } },
        )
    }
    SettingCard {
        RowText(
            title = stringResource(R.string.settings_banner_apps),
            subtitle = stringResource(R.string.settings_banner_apps_detail),
            enabled = n.banners,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
        )
        apps.forEach { app ->
            SwitchRow(title = app.label, subtitle = null, checked = !app.muted, enabled = n.banners) { allowed ->
                vm.setBannersMuted(app.packageName, muted = !allowed)
            }
        }
    }
}

@Composable
private fun LyricsSettings(s: AppSettings, vm: SettingsViewModel) {
    SettingCard {
        var previewSize by remember(s.lyricsFontSizeSp) { mutableFloatStateOf(s.lyricsFontSizeSp.toFloat()) }
        SliderRow(
            title = stringResource(R.string.settings_lyrics_size),
            value = s.lyricsFontSizeSp.toFloat(),
            range = SettingsRanges.lyricsFontSizeSp.first.toFloat()..SettingsRanges.lyricsFontSizeSp.last.toFloat(),
            steps = (SettingsRanges.lyricsFontSizeSp.last - SettingsRanges.lyricsFontSizeSp.first) / 2 - 1,
            valueLabel = { stringResource(R.string.settings_sp, it.roundToInt()) },
            onValueChange = { previewSize = it },
            onValueChangeFinished = { v -> vm.update { it.copy(lyricsFontSizeSp = v.roundToInt()) } },
        )
        Text(
            text = stringResource(R.string.settings_lyrics_preview),
            fontSize = previewSize.roundToInt().sp,
            fontWeight = FontWeight.SemiBold,
            color = ActiveLyricWhite,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
        val supported = Romanizer.isSupported
        SwitchRow(
            title = stringResource(R.string.settings_romanization),
            subtitle = stringResource(if (supported) R.string.settings_romanization_detail else R.string.settings_romanization_unsupported),
            checked = s.showRomanization && supported,
            enabled = supported,
        ) { v -> vm.update { it.copy(showRomanization = v) } }
    }
    SettingCard {
        val delay by vm.audioDelay.collectAsStateWithLifecycle()
        val route by vm.audioRoute.collectAsStateWithLifecycle()
        val output = route.name ?: stringResource(if (route.bluetooth) R.string.settings_output_bluetooth else R.string.settings_output_device)
        SwitchRow(
            title = stringResource(R.string.settings_latency),
            subtitle = stringResource(
                if (delay.measured) R.string.settings_latency_measured else R.string.settings_latency_estimated,
                output,
                delay.ms,
            ),
            checked = s.lyricsLatencyCompensation,
        ) { v -> vm.update { it.copy(lyricsLatencyCompensation = v) } }
        SliderRow(
            title = stringResource(R.string.settings_sync_adjust, output),
            subtitle = stringResource(R.string.settings_sync_adjust_detail),
            value = (s.lyricsRouteAdjustMs[route.id] ?: 0).toFloat(),
            range = SettingsRanges.lyricsSyncAdjustMs.first.toFloat()..SettingsRanges.lyricsSyncAdjustMs.last.toFloat(),
            steps = (SettingsRanges.lyricsSyncAdjustMs.last - SettingsRanges.lyricsSyncAdjustMs.first) / 50 - 1,
            valueLabel = {
                val ms = it.roundToInt()
                when {
                    ms > 0 -> stringResource(R.string.settings_sync_later, ms)
                    ms < 0 -> stringResource(R.string.settings_sync_earlier, -ms)
                    else -> stringResource(R.string.lyrics_offset_none)
                }
            },
            onValueChangeFinished = { v -> vm.setRouteAdjust(route.id, (v / 50).roundToInt() * 50) },
        )
    }
    SettingCard {
        ConfirmingActionRow(
            title = stringResource(R.string.settings_reset_offsets),
            subtitle = stringResource(R.string.settings_reset_offsets_detail),
            actionLabel = stringResource(R.string.settings_reset),
            action = vm::resetLyricOffsets,
        )
        ConfirmingActionRow(
            title = stringResource(R.string.settings_clear_cache),
            subtitle = stringResource(R.string.settings_clear_cache_detail),
            actionLabel = stringResource(R.string.settings_clear),
            action = vm::clearLyricsCache,
        )
    }
}

@Composable
private fun ClockSettings(s: AppSettings, vm: SettingsViewModel) {
    SettingCard {
        ChoiceRow(
            title = stringResource(R.string.settings_clock_format),
            options = ClockFormat.entries,
            selected = s.clockFormat,
            label = {
                stringResource(
                    when (it) {
                        ClockFormat.SYSTEM -> R.string.settings_clock_system
                        ClockFormat.H12 -> R.string.settings_clock_12
                        ClockFormat.H24 -> R.string.settings_clock_24
                    },
                )
            },
            onSelect = { v -> vm.update { it.copy(clockFormat = v) } },
        )
        SwitchRow(stringResource(R.string.settings_clock_seconds), null, s.clockShowSeconds) { v ->
            vm.update { it.copy(clockShowSeconds = v) }
        }
    }
}

@Composable
private fun AppearanceSettings(s: AppSettings, vm: SettingsViewModel) {
    SettingCard {
        SwitchRow(stringResource(R.string.settings_dynamic_colors), stringResource(R.string.settings_dynamic_colors_detail), s.dynamicColors) { v ->
            vm.update { it.copy(dynamicColors = v) }
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 14.dp)) {
            Text(stringResource(R.string.settings_accent), style = MaterialTheme.typography.titleMedium, color = CappedWhite)
            Text(stringResource(R.string.settings_accent_detail), style = MaterialTheme.typography.bodyMedium, color = MutedText)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentColor.entries.forEach { accent ->
                    AccentSwatch(accent, selected = accent == s.accent) { vm.update { it.copy(accent = accent) } }
                }
            }
        }
    }
}

// endregion

// region Building blocks

@Composable
private fun CategoryItem(title: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else CappedWhite,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouch)
            .clip(RoundedCornerShape(28.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    )
}

@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(Dimens.cardCorner))
            .padding(vertical = 8.dp),
        content = content,
    )
}

@Composable
private fun RowText(title: String, subtitle: String?, enabled: Boolean, modifier: Modifier = Modifier) {
    val alpha = if (enabled) 1f else 0.4f
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = CappedWhite.copy(alpha = alpha))
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MutedText.copy(alpha = alpha))
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouch)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(title, subtitle, enabled, Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Slider that only persists when the drag ends (no DataStore write per pixel). */
@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: @Composable (Float) -> String,
    onValueChangeFinished: (Float) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit = {},
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(horizontal = 24.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowText(title, subtitle, enabled, Modifier.weight(1f))
            Text(valueLabel(local), style = MaterialTheme.typography.labelLarge, color = CappedWhite.copy(alpha = if (enabled) 1f else 0.4f))
        }
        Slider(
            value = local,
            onValueChange = {
                local = it
                onValueChange(it)
            },
            onValueChangeFinished = { onValueChangeFinished(local) },
            valueRange = range,
            steps = steps,
            enabled = enabled,
        )
    }
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
        RowText(title, subtitle, enabled)
        Spacer(Modifier.height(10.dp))
        // Segments share the row equally, so size it for the longest label plus the checkmark,
        // rather than a fixed width that clips longer labels ("Stay on my choice").
        val labels = options.map { label(it) }
        val measurer = rememberTextMeasurer()
        val labelStyle = MaterialTheme.typography.labelLarge
        val density = LocalDensity.current
        val segmentWidth = remember(labels, labelStyle, density) {
            val longest = labels.maxOfOrNull { measurer.measure(it, labelStyle).size.width } ?: 0
            with(density) { longest.toDp() } + SEGMENT_CHROME
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            SingleChoiceSegmentedButtonRow(Modifier.widthIn(min = maxOf(140.dp, segmentWidth) * options.size)) {
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        enabled = enabled,
                        modifier = Modifier.heightIn(min = Dimens.minTouch),
                    ) { Text(labels[index], maxLines = 1, softWrap = false) }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String?, actionLabel: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(title, subtitle, enabled = true, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        FilledTonalButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(actionLabel)
        }
    }
}

/** An action whose button briefly reads "Done" once the work has finished. */
@Composable
private fun ConfirmingActionRow(title: String, subtitle: String, actionLabel: String, action: (onDone: () -> Unit) -> Unit) {
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(done) {
        if (done) {
            delay(2_000)
            done = false
        }
    }
    ActionRow(
        title = title,
        subtitle = subtitle,
        actionLabel = if (done) stringResource(R.string.settings_done) else actionLabel,
        enabled = !done,
        onClick = { action { done = true } },
    )
}

@Composable
private fun AccentSwatch(accent: AccentColor, selected: Boolean, onClick: () -> Unit) {
    val name = stringResource(
        when (accent) {
            AccentColor.BLUE -> R.string.accent_blue
            AccentColor.TEAL -> R.string.accent_teal
            AccentColor.GREEN -> R.string.accent_green
            AccentColor.AMBER -> R.string.accent_amber
            AccentColor.ROSE -> R.string.accent_rose
            AccentColor.LAVENDER -> R.string.accent_lavender
        },
    )
    val description = stringResource(R.string.settings_accent_option, name)
    Box(
        modifier = Modifier
            .size(Dimens.minTouch)
            .clip(CircleShape)
            .border(if (selected) 3.dp else 0.dp, if (selected) CappedWhite else Color.Transparent, CircleShape)
            .padding(6.dp)
            .clip(CircleShape)
            .background(Color(accent.argb))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description },
    )
}

// endregion

/** A segment's padding, checkmark and gap around its label. */
private val SEGMENT_CHROME = 72.dp
