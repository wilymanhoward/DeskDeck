package com.deskdeck.domain

/** A shortcut in the left rail. */
data class RailApp(
    val packageName: String,
    val label: String,
    val installed: Boolean,
    /** Fallback tile color (ARGB) used when the app icon isn't available. */
    val tileColor: Int,
)
