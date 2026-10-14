package com.deskdeck

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.deskdeck.launcher.AppIconFetcher
import com.deskdeck.launcher.AppIconKeyer
import com.deskdeck.data.brief.BriefScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DeskDeckApp : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var briefScheduler: BriefScheduler

    /** WorkManager with Hilt-injected workers (the default initializer is removed in the manifest). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        briefScheduler.start()
    }

    /** The shared Coil loader: album art URIs plus launcher icons ([com.deskdeck.launcher.AppIcon]). */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(AppIconKeyer())
                add(AppIconFetcher.Factory())
            }
            .build()
}
