package com.deskdeck.data.spotify

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Proof Key for Code Exchange (RFC 7636), which lets an app sign in without a client secret:
 * the authorize request carries a hash of a random verifier, and only the holder of the verifier
 * can exchange the returned code for tokens.
 */
object Pkce {
    private val random = SecureRandom()
    private val urlSafe = Base64.getUrlEncoder().withoutPadding()

    /** 64 random bytes as base64url: 86 characters, inside the 43–128 the spec allows. */
    fun newVerifier(): String = urlSafe.encodeToString(ByteArray(64).also(random::nextBytes))

    /** S256: base64url(SHA-256(verifier)). */
    fun challenge(verifier: String): String =
        urlSafe.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    /** An unguessable value tying the redirect to the request that started it. */
    fun newState(): String = urlSafe.encodeToString(ByteArray(16).also(random::nextBytes))
}
