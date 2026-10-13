package com.deskdeck.media

import android.content.Context
import android.graphics.Bitmap
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.deskdeck.domain.ArtworkColors
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ResolvedArtwork(val bitmap: Bitmap?, val colors: ArtworkColors?)

/** Turns whatever art a player published (bitmap or URI) into a bitmap plus card colors. */
@Singleton
class ArtworkLoader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    suspend fun resolve(bitmap: Bitmap?, uri: String?): ResolvedArtwork {
        val art = bitmap ?: uri?.let { load(it) }
        return ResolvedArtwork(art, art?.let { colorsOf(it) })
    }

    private suspend fun load(uri: String): Bitmap? = try {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .size(ART_SIZE_PX)
            // Palette needs to read pixels, which hardware bitmaps don't allow.
            .allowHardware(false)
            .build()
        (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // content:// URIs from other apps are often not readable by us (SecurityException). No art then.
        null
    }

    private suspend fun colorsOf(bitmap: Bitmap): ArtworkColors? = withContext(Dispatchers.Default) {
        val readable = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return@withContext null
        } else {
            bitmap
        }
        val palette = Palette.from(readable).maximumColorCount(16).generate()
        val primary = palette.vibrantSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch
            ?: return@withContext null
        val secondary = palette.darkMutedSwatch ?: palette.darkVibrantSwatch ?: palette.dominantSwatch ?: primary
        ArtworkColors(primary = primary.rgb, secondary = secondary.rgb)
    }

    private companion object {
        const val ART_SIZE_PX = 768
    }
}
