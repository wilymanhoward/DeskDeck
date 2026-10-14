package com.deskdeck.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deskdeck.DebugTools
import com.deskdeck.R
import com.deskdeck.data.settings.AppSettings

/** Settings › Calls: call awareness for WhatsApp, and what DeskDeck does around calls. */
@Composable
internal fun CallSettingsSection(s: AppSettings, vm: SettingsViewModel) {
    val c = s.calls
    SettingCard {
        SwitchRow(stringResource(R.string.settings_calls), stringResource(R.string.settings_calls_detail), c.enabled) { v ->
            vm.updateCalls { it.copy(enabled = v) }
        }
        SwitchRow(stringResource(R.string.settings_call_banner), stringResource(R.string.settings_call_banner_detail), c.incomingBanner, c.enabled) { v ->
            vm.updateCalls { it.copy(incomingBanner = v) }
        }
        SwitchRow(stringResource(R.string.settings_call_layout), stringResource(R.string.settings_call_layout_detail), c.callLayout, c.enabled) { v ->
            vm.updateCalls { it.copy(callLayout = v) }
        }
    }
    SettingCard {
        SwitchRow(stringResource(R.string.settings_call_pause_music), stringResource(R.string.settings_call_pause_music_detail), c.pauseMusic, c.enabled) { v ->
            vm.updateCalls { it.copy(pauseMusic = v) }
        }
        SwitchRow(stringResource(R.string.settings_call_resume_music), stringResource(R.string.settings_call_resume_music_detail), c.resumeMusic, c.enabled && c.pauseMusic) { v ->
            vm.updateCalls { it.copy(resumeMusic = v) }
        }
    }
    SettingCard {
        RowText(
            title = stringResource(R.string.settings_calls_about),
            subtitle = stringResource(R.string.settings_calls_about_detail),
            enabled = true,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
        )
    }
    // Debug builds only: post made-up call notifications to try the call UI without a call.
    DebugTools.CallSimulatorCard()
}
