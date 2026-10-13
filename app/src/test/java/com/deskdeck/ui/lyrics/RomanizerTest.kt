package com.deskdeck.ui.lyrics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only script detection is JVM-testable; the ICU transliteration itself runs on-device. */
class RomanizerTest {

    @Test
    fun `latin text needs nothing, including accents, digits and punctuation`() {
        assertFalse(Romanizer.needsRomanization("Hello, world! 123"))
        assertFalse(Romanizer.needsRomanization("Café déjà vu — señor"))
        assertFalse(Romanizer.needsRomanization("♪"))
        assertFalse(Romanizer.needsRomanization(""))
    }

    @Test
    fun `other scripts are detected`() {
        assertTrue(Romanizer.needsRomanization("미안해"))
        assertTrue(Romanizer.needsRomanization("Привет"))
        assertTrue(Romanizer.needsRomanization("夜に駆ける"))
        assertTrue(Romanizer.needsRomanization("Love you ありがとう"))
    }
}
