package com.deskdeck.media

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Cached user-visible app names ("Spotify") for package names. */
@Singleton
class AppLabels @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val cache = HashMap<String, String>()

    fun labelOf(packageName: String): String = cache.getOrPut(packageName) {
        val pm = context.packageManager
        try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}
