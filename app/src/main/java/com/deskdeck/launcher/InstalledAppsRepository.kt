package com.deskdeck.launcher

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import com.deskdeck.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.Collator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LaunchableApp(val packageName: String, val label: String)

/** Apps with a launcher entry, kept current through [LauncherApps.Callback] (installs, updates, removals). */
@Singleton
class InstalledAppsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    private val _apps = MutableStateFlow<List<LaunchableApp>>(emptyList())
    val apps: StateFlow<List<LaunchableApp>> = _apps.asStateFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    private fun refresh() {
        scope.launch {
            _apps.value = withContext(Dispatchers.IO) { load() }
        }
    }

    private fun load(): List<LaunchableApp> {
        val collator = Collator.getInstance()
        return launcherApps.getActivityList(null, Process.myUserHandle())
            .map { LaunchableApp(it.applicationInfo.packageName, it.label.toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }
}

/** Case- and accent-insensitive filter on label or package name. */
fun List<LaunchableApp>.matching(query: String): List<LaunchableApp> {
    val q = query.trim()
    if (q.isEmpty()) return this
    val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
    return filter { app ->
        app.packageName.contains(q, ignoreCase = true) ||
            app.label.windowed(q.length, 1, partialWindows = false).any { collator.compare(it, q) == 0 }
    }
}
