package com.deskdeck.launcher

/** Well-known music apps: the default rail, and labels/colors to show while they aren't installed. */
object KnownApps {
    const val SPOTIFY = "com.spotify.music"
    const val YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"
    const val APPLE_MUSIC = "com.apple.android.music"

    private val known = mapOf(
        SPOTIFY to ("Spotify" to 0xFF1DB954.toInt()),
        YOUTUBE_MUSIC to ("YouTube Music" to 0xFFE53935.toInt()),
        APPLE_MUSIC to ("Apple Music" to 0xFFFA2D48.toInt()),
    )

    val defaultRail = listOf(SPOTIFY, YOUTUBE_MUSIC, APPLE_MUSIC)

    fun label(packageName: String): String? = known[packageName]?.first

    /** Brand-ish tile color for the letter fallback; a neutral grey for anything else. */
    fun tileColor(packageName: String): Int = known[packageName]?.second ?: 0xFF3A3D43.toInt()
}

/** Pure operations on the ordered list of pinned package names, and its stored form. */
object RailPins {

    /** Null (never saved) means the defaults; an empty string means the user removed everything. */
    fun decode(raw: String?): List<String> =
        raw?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct() ?: KnownApps.defaultRail

    fun encode(pins: List<String>): String = pins.joinToString("\n")

    fun pin(pins: List<String>, packageName: String): List<String> =
        if (packageName in pins) pins else pins + packageName

    fun unpin(pins: List<String>, packageName: String): List<String> = pins - packageName

    fun toggle(pins: List<String>, packageName: String): List<String> =
        if (packageName in pins) unpin(pins, packageName) else pin(pins, packageName)

    /** Moves [packageName] by [delta] positions, clamped to the list bounds. */
    fun move(pins: List<String>, packageName: String, delta: Int): List<String> {
        val from = pins.indexOf(packageName)
        if (from < 0) return pins
        val to = (from + delta).coerceIn(0, pins.lastIndex)
        if (from == to) return pins
        return pins.toMutableList().apply {
            removeAt(from)
            add(to, packageName)
        }
    }
}
