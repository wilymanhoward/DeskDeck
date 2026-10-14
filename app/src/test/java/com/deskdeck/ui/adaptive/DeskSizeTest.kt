package com.deskdeck.ui.adaptive

import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import org.junit.Assert.assertEquals
import org.junit.Test

class DeskSizeTest {
    private fun size(widthDp: Int, heightDp: Int) = WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(widthDp, heightDp).toDeskSize()

    @Test
    fun `full tablet is expanded`() {
        // Tab S9 FE in landscape: 2304 x 1440 px at 280 dpi.
        assertEquals(DeskSize.EXPANDED, size(1317, 823))
    }

    @Test
    fun `half of the tablet in split screen is medium`() {
        assertEquals(DeskSize.MEDIUM, size(650, 823))
        assertEquals(DeskSize.MEDIUM, size(600, 800))
    }

    @Test
    fun `a two-thirds split is still expanded`() {
        assertEquals(DeskSize.EXPANDED, size(870, 823))
    }

    @Test
    fun `narrow split and pop-up windows are compact`() {
        assertEquals(DeskSize.COMPACT, size(430, 823))
        assertEquals(DeskSize.COMPACT, size(599, 800))
        // Wide but short pop-up window.
        assertEquals(DeskSize.COMPACT, size(700, 400))
    }
}
