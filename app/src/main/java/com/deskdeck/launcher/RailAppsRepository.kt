package com.deskdeck.launcher

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.deskdeck.domain.RailApp
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The user's pinned rail apps, in order, joined with what is currently installed. */
@Singleton
class RailAppsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val installedApps: InstalledAppsRepository,
) {
    val pinnedPackages: Flow<List<String>> = dataStore.data
        .map { RailPins.decode(it[PINS_KEY]) }
        .distinctUntilChanged()

    val railApps: Flow<List<RailApp>> = combine(pinnedPackages, installedApps.apps) { pins, apps ->
        val byPackage = apps.associateBy { it.packageName }
        pins.map { pkg ->
            val installed = byPackage[pkg]
            RailApp(
                packageName = pkg,
                label = installed?.label ?: KnownApps.label(pkg) ?: pkg,
                // Until the first app list loads, assume pinned apps are there rather than flashing grey.
                installed = installed != null || apps.isEmpty(),
                tileColor = KnownApps.tileColor(pkg),
            )
        }
    }

    suspend fun toggle(packageName: String) = update { RailPins.toggle(it, packageName) }

    suspend fun unpin(packageName: String) = update { RailPins.unpin(it, packageName) }

    suspend fun move(packageName: String, delta: Int) = update { RailPins.move(it, packageName, delta) }

    private suspend fun update(transform: (List<String>) -> List<String>) {
        dataStore.edit { prefs -> prefs[PINS_KEY] = RailPins.encode(transform(RailPins.decode(prefs[PINS_KEY]))) }
    }

    private companion object {
        val PINS_KEY = stringPreferencesKey("rail_pins")
    }
}
