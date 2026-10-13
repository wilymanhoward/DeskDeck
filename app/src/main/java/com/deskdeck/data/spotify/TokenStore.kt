package com.deskdeck.data.spotify

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/** What a signed-in Spotify account is kept as. */
data class SpotifyTokens(
    val accessToken: String,
    val refreshToken: String,
    /** Wall-clock ms after which [accessToken] is no longer accepted. */
    val expiresAtMs: Long,
    val userId: String? = null,
    val displayName: String? = null,
)

/**
 * Stores [SpotifyTokens] encrypted with an AES-256-GCM key that lives in the Android Keystore and
 * never leaves it. (EncryptedSharedPreferences is deprecated; this is the same idea with no extra
 * library.) If the key is gone (app data restored on another device), [load] returns null and the
 * user signs in again.
 */
@Singleton
class TokenStore @Inject constructor(@param:ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(tokens: SpotifyTokens) {
        val json = JSONObject()
            .put("access", tokens.accessToken)
            .put("refresh", tokens.refreshToken)
            .put("expiresAt", tokens.expiresAtMs)
            .putOpt("userId", tokens.userId)
            .putOpt("name", tokens.displayName)
            .toString()
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_DATA, Base64.encodeToString(sealed, Base64.NO_WRAP))
            .apply()
    }

    fun load(): SpotifyTokens? = runCatching {
        val iv = Base64.decode(prefs.getString(KEY_IV, null) ?: return null, Base64.NO_WRAP)
        val data = Base64.decode(prefs.getString(KEY_DATA, null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        val json = JSONObject(String(cipher.doFinal(data), Charsets.UTF_8))
        SpotifyTokens(
            accessToken = json.getString("access"),
            refreshToken = json.getString("refresh"),
            expiresAtMs = json.getLong("expiresAt"),
            userId = json.optString("userId").ifEmpty { null },
            displayName = json.optString("name").ifEmpty { null },
        )
    }.getOrElse {
        clear()
        null
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val PREFS = "spotify_account"
        const val KEY_IV = "iv"
        const val KEY_DATA = "data"
        const val ALIAS = "deskdeck_spotify_tokens"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
