package com.deskdeck.data.settings

enum class ClockFormat { SYSTEM, H12, H24 }

enum class KeepScreenOn {
    /** Screen stays on; optionally released once the paused clock shows (see [BurnInSettings.screenOffWhenPaused]). */
    ALWAYS,

    /** On while playing and during the paused grace period, then the system timeout takes over. */
    WHILE_PLAYING,
}

/** What a tap on a rail app means when another app later starts playing. */
enum class SourceMode {
    /** Switch to whichever app starts playing (the selection only lasts until then). */
    AUTO_FOLLOW,

    /** Stay on the selected app until another one is selected. */
    PIN,
}

/** Accent choices, light tones that read well on true black. */
enum class AccentColor(val argb: Long) {
    BLUE(0xFF8AB4F8),
    TEAL(0xFF80CBC4),
    GREEN(0xFFA8DAB5),
    AMBER(0xFFFDD663),
    ROSE(0xFFF6AEA9),
    LAVENDER(0xFFC58AF9),
}

data class BurnInSettings(
    /** Slowly move the whole UI by a few dp. */
    val pixelShift: Boolean = true,
    val pixelShiftDp: Int = 6,
    /** Dim the UI after [dimAfterMin] minutes without touch. */
    val idleDim: Boolean = true,
    val dimAfterMin: Int = 3,
    /** How much brightness dimming removes, 0..1. */
    val dimLevel: Float = 0.5f,
    /** Switch to the drifting ambient view [ambientAfterMin] minutes after dimming (or after idle, if dimming is off). */
    val ambient: Boolean = true,
    val ambientAfterMin: Int = 5,
    /** When nothing plays for [pausedClockAfterMin] minutes, show only a very dim moving clock. */
    val pausedClock: Boolean = true,
    val pausedClockAfterMin: Int = 5,
    /** With [KeepScreenOn.ALWAYS]: let the screen turn off once the paused clock shows. */
    val screenOffWhenPaused: Boolean = false,
    /** Maximum UI brightness, 0.5..1, applied as a black scrim over everything. */
    val brightnessCap: Float = 0.9f,
    /** Let the sidebar icons' opacity drift slowly so they don't sit at one level for hours. */
    val railOpacityDrift: Boolean = true,
)

data class AppSettings(
    val clockFormat: ClockFormat = ClockFormat.SYSTEM,
    val clockShowSeconds: Boolean = false,
    val lyricsFontSizeSp: Int = 30,
    val showRomanization: Boolean = false,
    val dynamicColors: Boolean = true,
    val accent: AccentColor = AccentColor.BLUE,
    val keepScreenOn: KeepScreenOn = KeepScreenOn.WHILE_PLAYING,
    val sourceMode: SourceMode = SourceMode.AUTO_FOLLOW,
    /** Also try an app's MediaBrowserService when starting it (usually refused, so off by default). */
    val mediaBrowserResume: Boolean = false,
    val burnIn: BurnInSettings = BurnInSettings(),
)

/** Allowed values, shared by the settings UI and input validation. */
object SettingsRanges {
    val minuteChoices = listOf(1, 3, 5, 10)
    val pausedMinuteChoices = listOf(1, 3, 5, 10, 15)
    val pixelShiftDp = 2..10
    val lyricsFontSizeSp = 22..42
    val dimLevel = 0.2f..0.8f
    val brightnessCap = 0.5f..1f
}
