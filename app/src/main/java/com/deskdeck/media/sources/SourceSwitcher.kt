package com.deskdeck.media.sources

import com.deskdeck.data.settings.SettingsRepository
import com.deskdeck.data.settings.SourceMode
import com.deskdeck.di.ApplicationScope
import com.deskdeck.launcher.InstalledAppsRepository
import com.deskdeck.media.MediaRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the now-playing card should say while the selected app has nothing to show. */
sealed interface SourceStatus {
    val packageName: String?

    data object Ready : SourceStatus {
        override val packageName: String? = null
    }

    /** Running the [ResumeChain]: "Starting Spotify…". */
    data class Starting(override val packageName: String) : SourceStatus

    /** Every [PlaybackSource] tried, no session: "Start playing from Spotify, then it appears here". */
    data class NoSession(override val packageName: String) : SourceStatus

    data class NotInstalled(override val packageName: String) : SourceStatus
}

/**
 * Rail taps land here. Selecting an app shows its session right away if it has one; otherwise the
 * [ResumeChain] tries to wake it in the background. This class never shows another app: that
 * only happens from an explicit "Open …" button. Apps like YouTube Music and Apple Music can't be
 * woken without showing them (Android 12+ blocks their background service start, and their
 * player stays empty or inactive until their UI opens), so they get that button quickly.
 */
@Singleton
class SourceSwitcher @Inject constructor(
    private val media: MediaRepository,
    sources: List<@JvmSuppressWildcards PlaybackSource>,
    private val installedApps: InstalledAppsRepository,
    settings: SettingsRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val chain = ResumeChain(sources)
    private var resumeJob: Job? = null

    private val sourceMode: StateFlow<SourceMode> = settings.settings
        .map { it.sourceMode }
        .stateIn(scope, SharingStarted.Eagerly, SourceMode.AUTO_FOLLOW)

    private val _status = MutableStateFlow<SourceStatus>(SourceStatus.Ready)
    val status: StateFlow<SourceStatus> = _status.asStateFlow()

    init {
        // Back to normal once the app shows a session, or when the selection moves elsewhere
        // (another tap, or auto-follow switching to an app that started playing).
        scope.launch {
            combine(media.selection, media.sessionPackages) { selection, packages -> selection to packages }
                .collect { (selection, packages) ->
                    val pkg = _status.value.packageName ?: return@collect
                    if (selection?.packageName != pkg) {
                        resumeJob?.cancel()
                        _status.value = SourceStatus.Ready
                    } else if (pkg in packages) {
                        _status.value = SourceStatus.Ready
                    }
                }
        }
    }

    /** @param pin true/false to override the Settings mode (the rail's "Pin source" action). */
    fun select(packageName: String, pin: Boolean? = null) {
        resumeJob?.cancel()
        media.select(packageName, pinned = pin ?: (sourceMode.value == SourceMode.PIN))
        when {
            !isInstalled(packageName) -> _status.value = SourceStatus.NotInstalled(packageName)
            packageName in media.sessionPackages.value -> _status.value = SourceStatus.Ready
            else -> {
                _status.value = SourceStatus.Starting(packageName)
                resumeJob = scope.launch {
                    val worked = chain.run(packageName) { timeoutMs ->
                        withTimeoutOrNull(timeoutMs) { media.sessionPackages.first { packageName in it } } != null
                    }
                    if (_status.value == SourceStatus.Starting(packageName)) {
                        _status.value = if (worked != null) SourceStatus.Ready else SourceStatus.NoSession(packageName)
                    }
                }
            }
        }
    }

    fun setPinned(pinned: Boolean) = media.setPinned(pinned)

    private fun isInstalled(packageName: String): Boolean {
        val apps = installedApps.apps.value
        // Until the app list has loaded, don't claim anything is missing.
        return apps.isEmpty() || apps.any { it.packageName == packageName }
    }
}
