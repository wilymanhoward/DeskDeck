package com.deskdeck.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LetterboxTest {

    /** A synthetic image: dark outside [content] = (left, top, right, bottom). */
    private fun bounds(w: Int, h: Int, content: IntArray): IntArray? {
        val (l, t, r, b) = content
        return Letterbox.contentBounds(
            width = w,
            height = h,
            isDarkRow = { y -> y < t || y >= b },
            isDarkColumn = { x -> x < l || x >= r },
        )
    }

    @Test
    fun `square thumbnail with a 16-9 frame is cropped to the frame`() {
        // 512x512 with a 512x288 picture centered: 112 px bars top and bottom.
        assertArrayEquals(intArrayOf(0, 112, 512, 400), bounds(512, 512, intArrayOf(0, 112, 512, 400)))
    }

    @Test
    fun `pillarbox bars are cropped too`() {
        assertArrayEquals(intArrayOf(100, 0, 412, 512), bounds(512, 512, intArrayOf(100, 0, 412, 512)))
    }

    @Test
    fun `no bars means no crop`() {
        assertNull(bounds(512, 512, intArrayOf(0, 0, 512, 512)))
    }

    @Test
    fun `thin bars are ignored`() {
        assertNull(bounds(512, 512, intArrayOf(0, 20, 512, 492)))
    }

    @Test
    fun `dark area on one side only is a dark cover, not a letterbox`() {
        assertNull(bounds(512, 512, intArrayOf(0, 200, 512, 512)))
    }

    @Test
    fun `clearly asymmetric bars are not cropped`() {
        assertNull(bounds(512, 512, intArrayOf(0, 60, 512, 380)))
    }

    @Test
    fun `an all black image is left alone`() {
        assertNull(Letterbox.contentBounds(64, 64, { true }, { true }))
    }
}
