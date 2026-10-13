package com.deskdeck.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RailPinsTest {

    @Test
    fun `never saved means the default music apps`() {
        assertEquals(KnownApps.defaultRail, RailPins.decode(null))
    }

    @Test
    fun `saved empty list stays empty`() {
        assertTrue(RailPins.decode("").isEmpty())
    }

    @Test
    fun `encode and decode round trip, dropping blanks and duplicates`() {
        val pins = listOf("a.b", "c.d")
        assertEquals(pins, RailPins.decode(RailPins.encode(pins)))
        assertEquals(listOf("a.b", "c.d"), RailPins.decode("a.b\n\n c.d \na.b"))
    }

    @Test
    fun `pin appends once`() {
        assertEquals(listOf("a", "b"), RailPins.pin(listOf("a"), "b"))
        assertEquals(listOf("a", "b"), RailPins.pin(listOf("a", "b"), "a"))
    }

    @Test
    fun `toggle adds and removes`() {
        assertEquals(listOf("a", "b"), RailPins.toggle(listOf("a"), "b"))
        assertEquals(listOf("b"), RailPins.toggle(listOf("a", "b"), "a"))
    }

    @Test
    fun `move shifts by delta and clamps at the ends`() {
        val pins = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), RailPins.move(pins, "b", -1))
        assertEquals(listOf("a", "c", "b"), RailPins.move(pins, "b", 1))
        assertEquals(pins, RailPins.move(pins, "a", -1))
        assertEquals(listOf("b", "c", "a"), RailPins.move(pins, "a", 10))
        assertEquals(pins, RailPins.move(pins, "missing", 1))
    }
}
