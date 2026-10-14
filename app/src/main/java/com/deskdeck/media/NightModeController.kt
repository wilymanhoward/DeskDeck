package com.deskdeck.media

import com.deskdeck.data.settings.SettingsRepository
import com.deskdeck.di.ApplicationScope
import com.deskdeck.domain.night.NightScheduleCalculator
import com.deskdeck.domain.night.NightState
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Whether Night Mode is on right now. Re-evaluates the [NightScheduleCalculator] whenever the
 * settings or the manual override change, and otherwise sleeps exactly until the next bedtime or
 * wake-up (checking at least once a minute, so a time-zone or clock change is noticed). This runs
 * in DeskDeck's process: Night Mode takes over the screen only while DeskDeck is in front.
 */
@Singleton
class NightModeController @Inject constructor(
    private val settings: SettingsRepository,
    private val banners: NotificationBanners,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(NightState(active = false, nextChangeAt = null, lastWakeAt = null))
    val state: StateFlow<NightState> = _state.asStateFlow()

    init {
        scope.launch {
            combine(settings.settings, settings.nightOverride) { s, o -> s.night to o }.collectLatest { (night, override) ->
                while (true) {
                    val now = Instant.now()
                    val calculator = NightScheduleCalculator(ZoneId.systemDefault())
                    val next = calculator.stateAt(now, night, override)
                    _state.value = next
                    // Night is calm: banners stay quiet (the night screen shows a dim dot instead).
                    banners.quiet = next.active
                    val untilChange = next.nextChangeAt?.let { it.toEpochMilli() - now.toEpochMilli() } ?: Long.MAX_VALUE
                    delay(untilChange.coerceIn(MIN_WAIT_MS, MAX_WAIT_MS))
                }
            }
        }
    }

    fun startNow() = scope.launch {
        val night = settings.settings.first().night
        settings.setNightOverride(NightScheduleCalculator(ZoneId.systemDefault()).startNow(Instant.now(), night))
    }

    fun endNow() = scope.launch {
        val night = settings.settings.first().night
        settings.setNightOverride(NightScheduleCalculator(ZoneId.systemDefault()).endNow(Instant.now(), night))
    }

    /** The rail clock's long-press. */
    fun toggle() = if (_state.value.active) endNow() else startNow()

    private companion object {
        const val MIN_WAIT_MS = 250L
        const val MAX_WAIT_MS = 60_000L
    }
}
