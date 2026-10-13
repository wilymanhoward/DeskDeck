package com.deskdeck.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSearchTest {

    private val apps = listOf(
        LaunchableApp("com.spotify.music", "Spotify"),
        LaunchableApp("com.google.android.apps.youtube.music", "YouTube Music"),
        LaunchableApp("com.example.cafe", "Café Radio"),
    )

    private fun labels(query: String) = apps.matching(query).map { it.label }

    @Test
    fun `blank query returns everything`() {
        assertEquals(apps.map { it.label }, labels("  "))
    }

    @Test
    fun `matches labels case-insensitively anywhere`() {
        assertEquals(listOf("YouTube Music"), labels("tube"))
        assertEquals(listOf("Spotify"), labels("SPOT"))
    }

    @Test
    fun `ignores accents`() {
        assertEquals(listOf("Café Radio"), labels("cafe"))
    }

    @Test
    fun `matches package names`() {
        assertEquals(listOf("YouTube Music"), labels("apps.youtube"))
    }
}
