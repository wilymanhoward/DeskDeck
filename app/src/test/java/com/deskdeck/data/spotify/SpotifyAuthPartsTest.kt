package com.deskdeck.data.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyAuthPartsTest {

    @Test
    fun `pkce challenge matches the RFC 7636 example`() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun `verifiers are long, url safe and different every time`() {
        val a = Pkce.newVerifier()
        val b = Pkce.newVerifier()
        assertTrue(a.length in 43..128)
        assertTrue(a.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertNotEquals(a, b)
    }

    @Test
    fun `premium, registration and rate limit errors are recognised`() {
        assertEquals(SpotifyError.PremiumRequired, classifyApiError(403, ApiError(403, "Player command failed: Premium required", "PREMIUM_REQUIRED"), null))
        assertEquals(
            SpotifyError.NotRegistered,
            classifyApiError(403, ApiError(403, "Check settings on developer.spotify.com/dashboard, the user may not be registered.", null), null),
        )
        assertEquals(SpotifyError.RateLimited(30), classifyApiError(429, null, "30"))
        assertEquals(SpotifyError.RateLimited(null), classifyApiError(429, null, null))
    }

    @Test
    fun `missing device and everything else`() {
        assertEquals(SpotifyError.NoDevice, classifyApiError(404, ApiError(404, "Player command failed: No active device found", "NO_ACTIVE_DEVICE"), null))
        assertEquals(SpotifyError.SignedOut, classifyApiError(401, null, null))
        assertEquals(SpotifyError.Other(503, "Service unavailable"), classifyApiError(503, ApiError(503, "Service unavailable", null), null))
        assertEquals(SpotifyError.Other(403, "Forbidden"), classifyApiError(403, ApiError(403, "Forbidden", null), null))
    }
}
