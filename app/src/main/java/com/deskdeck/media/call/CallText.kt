package com.deskdeck.media.call

/**
 * The only place call detection matches words, for when an app doesn't use Android's structured
 * call signals (CallStyle). English and Malay, lower-case, matched as substrings.
 *
 * Fragile by nature: an app can reword its notifications in any update, and other languages
 * aren't covered. It's only consulted when the structured signals are missing, and each match is
 * covered by a test.
 */
object CallText {
    private val INCOMING = listOf(
        "incoming", // "Incoming voice call", "Incoming video call"
        "masuk", // "Panggilan suara masuk", "Panggilan video masuk"
    )
    private val VIDEO = listOf("video") // the same word in both languages
    private val ONGOING = listOf(
        "ongoing", // "Ongoing voice call"
        "sedang berlangsung", // "Panggilan suara sedang berlangsung"
    )
    // Calling out, before the other side answers.
    private val CALLING_OUT = listOf("calling", "ringing", "memanggil", "berdering")
    private val ANSWER = listOf("answer", "accept", "jawab", "terima")
    private val DECLINE = listOf("decline", "reject", "tolak")
    private val HANG_UP = listOf("hang up", "end call", "letak", "tamatkan", "tamat")

    fun saysIncoming(vararg texts: String?) = matches(INCOMING, texts)
    fun saysVideo(vararg texts: String?) = matches(VIDEO, texts)
    fun saysOngoing(vararg texts: String?) = matches(ONGOING, texts)
    fun saysCallingOut(vararg texts: String?) = matches(CALLING_OUT, texts)

    fun isAnswer(title: String?) = matches(ANSWER, arrayOf(title))
    fun isDecline(title: String?) = matches(DECLINE, arrayOf(title))
    fun isHangUp(title: String?) = matches(HANG_UP, arrayOf(title))

    private fun matches(words: List<String>, texts: Array<out String?>): Boolean =
        texts.any { text -> text != null && words.any { it in text.lowercase() } }
}
