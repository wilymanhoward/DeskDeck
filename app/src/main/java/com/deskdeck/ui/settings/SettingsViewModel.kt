package com.deskdeck.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deskdeck.data.lyrics.LyricsRepository
import com.deskdeck.data.settings.AppSettings
import com.deskdeck.data.settings.BurnInSettings
import com.deskdeck.data.settings.NightSettings
import com.deskdeck.data.settings.NotificationSettings
import com.deskdeck.ui.brief.BriefPreview
import com.deskdeck.media.NightModeController
import com.deskdeck.data.settings.SettingsRepository
import com.deskdeck.data.spotify.SpotifyAccount
import com.deskdeck.data.spotify.SpotifyAuth
import com.deskdeck.data.spotify.SpotifyLibrary
import com.deskdeck.media.AppLabels
import com.deskdeck.media.AudioDelay
import com.deskdeck.media.OutputLatency
import com.deskdeck.media.OutputRoute
import com.deskdeck.media.NotificationBanners
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val lyricsRepository: LyricsRepository,
    banners: NotificationBanners,
    appLabels: AppLabels,
    outputLatency: OutputLatency,
    private val spotifyAuth: SpotifyAuth,
    private val spotifyLibrary: SpotifyLibrary,
    private val nightMode: NightModeController,
    private val briefPreview: BriefPreview,
) : ViewModel() {

    fun showBriefNow() = briefPreview.show()

    val spotifyAccount: StateFlow<SpotifyAccount> = spotifyAuth.account

    fun beginSpotifySignIn(): String = spotifyAuth.beginSignIn().toString()

    fun disconnectSpotify() {
        viewModelScope.launch { spotifyLibrary.signOut() }
    }

    val audioDelay: StateFlow<AudioDelay> = outputLatency.delay
    val audioRoute: StateFlow<OutputRoute> = outputLatency.route

    fun setRouteAdjust(routeId: String, ms: Int) = update {
        val map = if (ms == 0) it.lyricsRouteAdjustMs - routeId else it.lyricsRouteAdjustMs + (routeId to ms)
        it.copy(lyricsRouteAdjustMs = map)
    }

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /** Apps for the per-app banner list: those seen notifying, plus any already muted. */
    val notificationApps: StateFlow<List<NotificationApp>> =
        combine(banners.knownPackages, settings) { known, s ->
            (known + s.notifications.mutedPackages)
                .map { NotificationApp(it, appLabels.labelOf(it), muted = it in s.notifications.mutedPackages) }
                .sortedBy { it.label.lowercase() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setBannersMuted(packageName: String, muted: Boolean) = update {
        val n = it.notifications
        it.copy(notifications = n.copy(mutedPackages = if (muted) n.mutedPackages + packageName else n.mutedPackages - packageName))
    }

    fun updateNotifications(transform: (NotificationSettings) -> NotificationSettings) =
        update { it.copy(notifications = transform(it.notifications)) }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun updateNight(transform: (NightSettings) -> NightSettings) {
        viewModelScope.launch { settingsRepository.updateNight(transform) }
    }

    /** "Start Night Mode now": on until the next wake-up time. */
    fun startNightNow() {
        nightMode.startNow()
    }

    fun updateBurnIn(transform: (BurnInSettings) -> BurnInSettings) = update { it.copy(burnIn = transform(it.burnIn)) }

    fun clearLyricsCache(onDone: () -> Unit) {
        viewModelScope.launch {
            lyricsRepository.clearCache()
            onDone()
        }
    }

    fun resetLyricOffsets(onDone: () -> Unit) {
        viewModelScope.launch {
            lyricsRepository.resetAllOffsets()
            onDone()
        }
    }
}

data class NotificationApp(val packageName: String, val label: String, val muted: Boolean)
