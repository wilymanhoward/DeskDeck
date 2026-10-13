package com.deskdeck.ui.lyrics

import android.icu.text.Transliterator
import android.os.Build

/**
 * Latin-script readings for lyrics in other scripts, using the ICU transliterator built into
 * Android 10+ (no extra dependency). Good for Korean, Cyrillic, Greek, Thai, kana; kanji/hanzi get
 * Mandarin-style pinyin, which is only a rough guide for Japanese.
 */
object Romanizer {

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private val transliterator: Transliterator? by lazy {
        if (isSupported) Transliterator.getInstance("Any-Latin; NFC") else null
    }

    /** True when [text] has letters outside the Latin script. Pure; also used on older Android. */
    fun needsRomanization(text: String): Boolean = text.any { c ->
        c.isLetter() && Character.UnicodeScript.of(c.code).let {
            it != Character.UnicodeScript.LATIN && it != Character.UnicodeScript.COMMON &&
                it != Character.UnicodeScript.INHERITED
        }
    }

    /** Romanized form, or null when not needed or not supported on this Android version. */
    fun romanize(text: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !needsRomanization(text)) return null
        val t = transliterator ?: return null
        // ICU transliterators aren't documented as thread-safe.
        return synchronized(t) { t.transliterate(text) }.takeIf { it != text }
    }
}
