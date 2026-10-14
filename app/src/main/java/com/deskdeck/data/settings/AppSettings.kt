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

/** DeskDeck's own notification banners (it's full-screen, so the system's are easy to miss). */
data class NotificationSettings(
    val banners: Boolean = true,
    /** Apps whose notifications never show a banner. */
    val mutedPackages: Set<String> = emptySet(),
    /** Show only the app name and a generic line. */
    val hideContent: Boolean = false,
    /** Follow the system's Do Not Disturb, including its exceptions (priority contacts, alarms). */
    val respectDnd: Boolean = true,
    val durationSec: Int = 5,
)

/** Call awareness (N2): the incoming call banner, the in-call layout, and music around calls. */
data class CallSettings(
    val enabled: Boolean = true,
    val incomingBanner: Boolean = true,
    val callLayout: Boolean = true,
    /** Pause music that is still playing once a call starts. */
    val pauseMusic: Boolean = true,
    /** Play it again after the call (only what DeskDeck paused). */
    val resumeMusic: Boolean = true,
)

data class AppSettings(
    val clockFormat: ClockFormat = ClockFormat.SYSTEM,
    val clockShowSeconds: Boolean = false,
    val lyricsFontSizeSp: Int = 30,
    val showRomanization: Boolean = false,
    /** Delay lyrics by the measured audio output latency (Bluetooth adds a third of a second). */
    val lyricsLatencyCompensation: Boolean = true,
    /**
     * Extra lyrics delay per audio output (keyed by [com.deskdeck.media.OutputRoute.id]), for
     * speakers that buffer more than they report. Positive = lyrics later.
     */
    val lyricsRouteAdjustMs: Map<String, Int> = emptyMap(),
    val night: NightSettings = NightSettings(),
    val dynamicColors: Boolean = true,
    val accent: AccentColor = AccentColor.BLUE,
    val keepScreenOn: KeepScreenOn = KeepScreenOn.WHILE_PLAYING,
    val sourceMode: SourceMode = SourceMode.AUTO_FOLLOW,
    /** Also try an app's MediaBrowserService when starting it (usually refused, so off by default). */
    val mediaBrowserResume: Boolean = false,
    /** Now Playing card on the right and lyrics on the left (moved by dragging the card). */
    val nowPlayingOnRight: Boolean = false,
    /** Browse and start Spotify playlists from DeskDeck (needs a Client ID and sign-in). */
    val spotifyLibrary: Boolean = false,
    val burnIn: BurnInSettings = BurnInSettings(),
    val notifications: NotificationSettings = NotificationSettings(),
    val calls: CallSettings = CallSettings(),
)

/** Allowed values, shared by the settings UI and input validation. */
object SettingsRanges {
    val minuteChoices = listOf(1, 3, 5, 10)
    val pausedMinuteChoices = listOf(1, 3, 5, 10, 15)
    val pixelShiftDp = 2..10
    val lyricsFontSizeSp = 22..42
    val dimLevel = 0.2f..0.8f
    val brightnessCap = 0.5f..1f
    val bannerDurationSec = 3..10
    val lyricsSyncAdjustMs = -1_000..2_000
    val nightBrightness = 0.01f..0.5f
}
