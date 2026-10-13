package com.deskdeck.launcher

import android.content.pm.PackageManager
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options

/** Coil model for an installed app's launcher icon: `AsyncImage(model = AppIcon("com.spotify.music"))`. */
data class AppIcon(val packageName: String)

/** Loads launcher icons through Coil, so they get its memory cache and async loading for free. */
class AppIconFetcher(
    private val data: AppIcon,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val drawable = try {
            options.context.packageManager.getApplicationIcon(data.packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        // Rasterize adaptive icons once at a fixed size so the result can be memory-cached.
        return ImageFetchResult(
            image = drawable.toBitmap(ICON_SIZE_PX, ICON_SIZE_PX).asImage(),
            isSampled = false,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<AppIcon> {
        override fun create(data: AppIcon, options: Options, imageLoader: ImageLoader): Fetcher =
            AppIconFetcher(data, options)
    }

    private companion object {
        const val ICON_SIZE_PX = 192
    }
}

class AppIconKeyer : Keyer<AppIcon> {
    override fun key(data: AppIcon, options: Options): String = "app-icon:${data.packageName}"
}
