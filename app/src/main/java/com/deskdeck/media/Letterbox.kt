package com.deskdeck.media

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.get
import kotlin.math.abs

/**
 * Video thumbnails (YouTube) often arrive as a square bitmap with the 16:9 frame letterboxed in
 * black. This finds the picture inside the bars so the card shows the picture, not the bars, and
 * Palette doesn't pick up black.
 *
 * Conservative on purpose, since a dark album cover must never be cropped: bars must be near-pure
 * black, on both opposite sides, roughly symmetric, and each at least [MIN_BAR] of the size.
 */
object Letterbox {

    /** Players whose art is a video frame. Cropping is limited to them as a second safety net. */
    val VIDEO_APPS = setOf("com.google.android.youtube", "com.google.android.apps.youtube.music")

    private const val MIN_BAR = 0.08
    private const val MAX_ASYMMETRY = 0.03
    private const val DARK_LUMA = 16

    /** Content bounds as [left, top, right, bottom) or null if there are no clear bars. */
    fun contentBounds(width: Int, height: Int, isDarkRow: (Int) -> Boolean, isDarkColumn: (Int) -> Boolean): IntArray? {
        if (width <= 0 || height <= 0) return null

        var top = 0
        while (top < height && isDarkRow(top)) top++
        if (top == height) return null // All black: nothing to show either way.
        var bottom = height
        while (bottom > top && isDarkRow(bottom - 1)) bottom--

        var left = 0
        while (left < width && isDarkColumn(left)) left++
        var right = width
        while (right > left && isDarkColumn(right - 1)) right--

        val vertical = symmetricBars(top, height - bottom, height)
        val horizontal = symmetricBars(left, width - right, width)
        if (!vertical && !horizontal) return null
        return intArrayOf(
            if (horizontal) left else 0,
            if (vertical) top else 0,
            if (horizontal) right else width,
            if (vertical) bottom else height,
        )
    }

    private fun symmetricBars(first: Int, second: Int, size: Int): Boolean =
        first >= size * MIN_BAR && second >= size * MIN_BAR && abs(first - second) <= size * MAX_ASYMMETRY

    /** Returns [bitmap] cropped to its picture, or [bitmap] itself when it isn't letterboxed. */
    fun crop(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val step = 8
        fun dark(x: Int, y: Int): Boolean {
            val c = bitmap[x, y]
            return Color.red(c) < DARK_LUMA && Color.green(c) < DARK_LUMA && Color.blue(c) < DARK_LUMA
        }
        val bounds = contentBounds(
            width = w,
            height = h,
            isDarkRow = { y -> (0 until w step step).all { x -> dark(x, y) } },
            isDarkColumn = { x -> (0 until h step step).all { y -> dark(x, y) } },
        ) ?: return bitmap
        val (l, t, r, b) = bounds
        return Bitmap.createBitmap(bitmap, l, t, r - l, b - t)
    }
}
