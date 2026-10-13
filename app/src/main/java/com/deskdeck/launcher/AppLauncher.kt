package com.deskdeck.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLauncher @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** Opens [packageName]; if it can't be launched (missing, disabled), opens its store page. */
    fun launchOrOpenStore(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            openStorePage(packageName)
            return
        }
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            openStorePage(packageName)
        }
    }

    fun openStorePage(packageName: String) {
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
        val web = Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri())
        for (intent in listOf(market, web)) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                // No store app; try the web page next.
            }
        }
    }
}
