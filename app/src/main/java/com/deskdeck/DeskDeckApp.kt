package com.deskdeck

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.deskdeck.launcher.AppIconFetcher
import com.deskdeck.launcher.AppIconKeyer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DeskDeckApp : Application(), SingletonImageLoader.Factory {

    /** The shared Coil loader: album art URIs plus launcher icons ([com.deskdeck.launcher.AppIcon]). */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(AppIconKeyer())
                add(AppIconFetcher.Factory())
            }
            .build()
}
