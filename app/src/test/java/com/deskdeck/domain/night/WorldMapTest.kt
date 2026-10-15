package com.deskdeck.domain.night

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldMapTest {
    @Test
    fun `land and sea where they should be`() {
        // Cities on land.
        assertTrue(WorldMap.isLand(101.7, 3.1)) // Kuala Lumpur
        assertTrue(WorldMap.isLand(2.3, 48.9)) // Paris
        assertTrue(WorldMap.isLand(-100.0, 40.0)) // US Midwest
        assertTrue(WorldMap.isLand(-60.0, -10.0)) // Amazon
        assertTrue(WorldMap.isLand(20.0, 5.0)) // Central Africa
        assertTrue(WorldMap.isLand(135.0, -25.0)) // Australia
        assertTrue(WorldMap.isLand(100.0, 60.0)) // Siberia
        // Open sea.
        assertFalse(WorldMap.isLand(-40.0, 30.0)) // Atlantic
        assertFalse(WorldMap.isLand(-150.0, 0.0)) // Pacific
        assertFalse(WorldMap.isLand(80.0, -20.0)) // Indian Ocean
        assertFalse(WorldMap.isLand(18.0, 35.0)) // Mediterranean
    }

    @Test
    fun `a dot grid has a plausible amount of land`() {
        val dots = WorldMap.landDots(90)
        // About 30 % of the earth is land; the cropped map has a bit more.
        val total = 90 * ((WorldMap.TOP_LAT - WorldMap.BOTTOM_LAT) / 4).toInt()
        val share = dots.size.toDouble() / total
        assertTrue("land share $share", share in 0.22..0.42)
    }

    @Test
    fun `the sun is over the Pacific at midnight UTC and over Greenwich at noon`() {
        val noon = WorldMap.subsolarPoint(Instant.parse("2026-06-21T12:00:00Z"))
        assertEquals(0.0, noon.lon, 0.01)
        assertEquals(23.4, noon.lat, 0.3) // June solstice
        val midnight = WorldMap.subsolarPoint(Instant.parse("2026-12-21T00:00:00Z"))
        assertEquals(180.0, kotlin.math.abs(midnight.lon), 0.01)
        assertEquals(-23.4, midnight.lat, 0.3)
    }

    @Test
    fun `day and night follow the sun`() {
        val sun = WorldMap.subsolarPoint(Instant.parse("2026-06-21T12:00:00Z"))
        assertTrue(WorldMap.isDay(0.0, 51.5, sun)) // London at noon
        assertFalse(WorldMap.isDay(139.7, 35.7, sun)) // Tokyo at 21:00
        // June: the far north has daylight, the far south doesn't.
        assertTrue(WorldMap.isDay(180.0, 75.0, sun))
        assertFalse(WorldMap.isDay(0.0, -70.0, sun))
        // The day/night line stays on the map.
        val lat = WorldMap.terminatorLat(90.0, sun)
        assertTrue(lat in WorldMap.BOTTOM_LAT..WorldMap.TOP_LAT)
    }
}
