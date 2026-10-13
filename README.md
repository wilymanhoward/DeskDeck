# DeskDeck

A desk music dashboard for Android tablets: a dark, Android Auto "Coolwalk"-style screen that shows
and controls whatever music app is playing, with time-synced lyrics and burn-in protection for
AMOLED tablets that sit on a desk all day. No maps, no navigation.

- **Works with any player** that publishes a media session: Spotify, YouTube Music, Apple Music,
  YouTube, podcast apps… No per-app SDKs or accounts.
- **Now playing:** album art with colors taken from the cover, smooth seek bar, play/pause,
  skip, shuffle and repeat.
- **Synced lyrics** from [LRCLIB](https://lrclib.net): the current line is highlighted and centered,
  tap a line to jump there, per-song timing correction, optional romanization. Lyrics are cached
  for offline replays.
- **Sidebar:** clock, live notification count, your pinned apps (tap to open), an all-apps picker.
- **Burn-in protection:** pixel shift, idle dimming, a drifting ambient mode, a dim paused clock,
  a brightness cap, and keep-screen-on rules.

Built for landscape Samsung Galaxy Tab devices (developed on a Tab S9 FE, Android 16), and works on
any Android 8.0+ tablet.

---

## Build and run

### Requirements

- Android Studio (recent), or JDK 17+ for the command line. The project builds with the JDK bundled
  with Android Studio (JBR 25).
- Android SDK with platform 37 (`compileSdk 37`, `targetSdk 35`, `minSdk 26`). Android Studio
  offers to install it on first sync.
- Internet access on the tablet (lyrics).

Toolchain: Gradle 9.8.1 (wrapper), Android Gradle Plugin 9.4.1 with built-in Kotlin 2.4.21, KSP 2.3.

### Android Studio

1. **File › Open** the `DeskDeck` folder and let Gradle sync.
2. Connect the tablet with USB debugging on (Settings › Developer options › USB debugging) and
   accept the "Allow USB debugging?" prompt on the tablet.
3. Select the tablet and press **Run**.

### Command line

```bash
./gradlew installDebug
```

```bash
adb shell am start -n com.deskdeck/.MainActivity
```

On Windows, use `gradlew.bat` from PowerShell and point `JAVA_HOME` at Android Studio's JDK:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat installDebug
```

> **Windows user names with spaces:** if Gradle fails with `java.io.IOException: Unable to establish
> loopback connection`, the JDK can't create its local sockets under your temp folder (for example
> `C:\Users\Jane Doe\AppData\Local\Temp`). Point them at a folder without spaces:
>
> ```powershell
> New-Item -ItemType Directory -Force .gradle\uds | Out-Null; $env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$PWD\.gradle\uds"; .\gradlew.bat installDebug
> ```

### Tests and checks

```bash
./gradlew testDebugUnitTest lintDebug
```

136 JVM unit tests cover the logic that doesn't need a device:

| Area | Tests |
| --- | --- |
| Lyrics | LRC parser, title cleaning and display titles, search-result matching, repository caching and offsets |
| Playback | position interpolation, active lyric line lookup, time formatting |
| Media sessions | session selection, metadata mapping, ad/podcast/live detection, shuffle/repeat resolution |
| Burn-in | the full display-mode / keep-screen-on policy, pixel shift orbit, drift, rail opacity |
| Other | settings persistence, rail pins, notification counting, app search, letterbox detection, script detection |

### Release build

`./gradlew assembleRelease` produces an R8-shrunk APK (about 2 MB). It's unsigned. Configure your
own signing config before distributing.

---

## Granting Notification Access on the tablet

Android only lets an app see other apps' media sessions if it's an enabled **notification
listener**. DeskDeck never reads or stores notification contents. It only uses that access for media
sessions and to count notifications for the sidebar badge.

1. Open DeskDeck. The welcome screen explains this and has an **Open settings** button.
2. On the screen that opens, turn on **DeskDeck** and confirm the dialog.
   (Manually: **Settings › Notifications › Advanced settings › Notification access** on Samsung, or
   **Settings › Apps › Special app access › Notification access**.)
3. Go back to DeskDeck. It continues on its own.

**Switch greyed out ("Restricted setting")?** Android 13+ blocks this for apps installed from a
downloaded APK file. Open **Settings › Apps › DeskDeck**, tap **⋮** (top right), choose
**Allow restricted settings**, confirm, then repeat step 2. The welcome screen has an
**Open App info** shortcut for this. Apps installed from Android Studio / `adb` aren't affected.

You can skip the welcome screen with **Not now**. The dashboard then shows a **Grant access**
button instead of the player.

---

## Using DeskDeck

- **Sidebar:**
  - Tap an app to open it. Apps that aren't installed are greyed out and open their Play Store page.
  - The app that's playing has a blue marker.
  - **+** or a long-press on an app edits the sidebar: add, remove, reorder.
  - **⊞** opens all apps. Tap to open one, or tap its pin to add it to the sidebar.
- **Lyrics:**
  - Scroll freely. Following the song resumes after 5 seconds.
  - Tap a line to jump to it.
  - If the timing is off, use the sliders button (top right of the lyrics) for **Earlier / Later**
    in 0.1 s steps. It's saved per song.
- **Volume:**
  - The volume buttons show DeskDeck's own HUD on the right edge instead of the system panel.
    Drag the HUD to set the level; tap its speaker to mute.
  - The slider under the playback controls does the same. Volume changed from elsewhere
    (Bluetooth speaker buttons, another device) shows the HUD too.
  - When the shown app plays on a remote device with adjustable volume (Cast, some Spotify Connect
    speakers), the volume follows that device; otherwise it's the tablet's media volume.
- **Settings** (gear icon): screen & burn-in, lyrics, clock, appearance, sidebar.

### Burn-in protection

All of this is configurable under **Settings › Screen & burn-in**:

| Feature | Default | What it does |
| --- | --- | --- |
| Pixel shift | on, 6 dp | Moves the whole interface a few dp along a slow orbit about once a minute. |
| Dim when idle | on, 3 min, 50 % | Darkens the screen after no touch. The first tap only wakes it and never presses a control. |
| Ambient mode | on, 5 min after dimming | Small cover, title, current lyric line and clock on black, slowly drifting. Tap to wake. |
| Clock when nothing plays | on, 5 min | After nothing has played *and* nothing has been touched for that long, only a very dim drifting clock. |
| Brightness cap | 90 % | Caps how bright DeskDeck can get, on top of the tablet's brightness. |
| Sidebar opacity drift | on | Sidebar icons slowly vary between 70 % and 100 % opacity. |
| Keep screen on | While playing | **While playing**: on while music plays and through the paused-clock wait, then your tablet's screen timeout takes over. **Always**: stays on; optionally turn off with the paused clock. |

Static text is capped at about 85 % white, and nothing large and bright stays on screen. "Turn the
screen off" means DeskDeck stops holding it on, so the system timeout turns it off. Ordinary apps
can't force the screen off without device-admin rights.

---

## Architecture

Single activity, Jetpack Compose with Material 3, MVVM with `StateFlow`, Hilt for dependency
injection, coroutines.

```
com.deskdeck
├─ domain/     Track, PlaybackInfo (position interpolation), NowPlaying, Lyrics, RailApp
├─ media/      MediaListenerService (notification listener), NotificationAccess, NotificationCounter,
│              MediaSessionRepository + SessionEntry (framework MediaController; compat controller
│              only for shuffle/repeat), SessionSelector, MediaMapping, ArtworkLoader, Letterbox
├─ data/       lyrics/ (LyricsProvider, LrclibProvider, LrcParser, TitleCleaner, LyricMatcher,
│              DefaultLyricsRepository), db/ (Room cache and offsets), settings/ (DataStore),
│              NetworkMonitor
├─ launcher/   InstalledAppsRepository (LauncherApps), RailAppsRepository, AppLauncher, AppIconFetcher
├─ burnin/     BurnInPolicy (pure state machine), Motion (pixel shift, drift, rail opacity), Compose glue
├─ ui/         AppRoot, dashboard/, components/ (LeftRail, NowPlayingCard, SeekBar, LyricsPanel…),
│              onboarding/, picker/, settings/, ambient/, lyrics/Romanizer, theme/
└─ di/         Hilt modules
```

**How it fits together:**

- **Sessions:** `MediaSessionRepository` listens for active sessions and registers a callback on
  each one. It picks one to show: the session that most recently *started* playing, otherwise it
  keeps the current one, otherwise the system's top session. It then publishes `NowPlaying`.
- **Position:** The player is never polled. The UI extrapolates `position + elapsed × speed` from
  the last snapshot, every frame while the dashboard is visible and 4× a second when it's covered.
- **Lyrics:** The ViewModel looks them up when the track changes:
  - **Order:** Room cache first, then LRCLIB. LRCLIB is tried as an exact lookup, then a field
    search, then a free-text search. The best result is picked by title, artist and duration.
  - **Retries:** "Not found" is retried after 3 days. A failed lookup retries automatically when
    the network comes back.
- **Burn-in:** `BurnInPolicy` decides the display mode and keep-screen-on from idle time, playback
  and settings. The UI re-evaluates it exactly when the next threshold is due.

### Dependencies and why

| Library | Why |
| --- | --- |
| Compose BOM, Material 3, Activity, Lifecycle | UI |
| Hilt (+ hilt-lifecycle-viewmodel-compose) | Dependency injection, including the notification listener service |
| androidx.media (compat) | Shuffle/repeat **state** lives in the compat "extra binder", which the framework `MediaController` can't read. Used only for that (it's deprecated in favor of Media3, which uses the same mechanism internally for other apps' sessions). |
| Retrofit + Moshi (codegen via KSP) + OkHttp 4.12 | LRCLIB client. Codegen adapters avoid reflection and keep R8 simple. OkHttp stays on 4.x because Retrofit 3 and Coil are built against it. |
| Room | Lyrics cache and per-song timing offsets |
| DataStore Preferences | Settings and pinned apps |
| Coil 3 | Album art from URIs, plus launcher icons through a small custom fetcher (memory-cached) |
| Palette | Card colors from album art |

Not added on purpose: icon packs (a handful of vector drawables instead), a navigation library (one
screen plus two overlays), and transliteration libraries (Android's built-in ICU is used).

---

## Known limitations

- **Shuffle/repeat** are best-effort. With apps built on Android's media compat libraries (Spotify,
  YouTube Music) they work with real state. Apps that only offer custom actions can be toggled, but
  their state isn't shown. Otherwise the buttons are disabled.
- **Lyrics** come only from LRCLIB's community database. Some tracks have no lyrics or only unsynced
  ones. Use **Retry**, and **Earlier / Later** if the timing is off.
- **Ad / podcast / live detection** uses metadata heuristics (Spotify's ad markers, episode ids,
  podcast apps, no duration + not seekable). An unusual app may be misclassified.
- **Romanization** needs Android 10+. Japanese kanji get a Mandarin-style reading, which is only a
  rough guide.
- **Samsung taskbar handle:** with gesture navigation and the taskbar enabled, One UI draws a small
  handle at the bottom that apps can't hide. Turn it off under **Settings › Display › Taskbar** (or
  the navigation bar's gesture hint).
- **Volume keys** are only DeskDeck's while it's on screen; elsewhere the system panel appears as
  usual. Spotify reports its session as a remote route with fixed volume even when it plays on the
  tablet, so DeskDeck (like Android itself) adjusts the tablet's media volume for it. Raising volume
  past Android's hearing-safety level on headphones still needs the system's confirmation dialog,
  which DeskDeck then shows. Outside changes are detected through `VOLUME_CHANGED_ACTION`, a stable
  but undocumented system broadcast.
- **targetSdk 35** is deliberate. With 36+, Android 16 ignores orientation locks on large screens.

## Troubleshooting

- **Nothing shows while music plays:** check Notification access is still on (an app update or
  battery optimization can turn the listener off), then reopen DeskDeck.
- **Wrong lyrics:** they're matched by title, artist and duration. "Settings › Lyrics › Clear saved
  lyrics" forces a fresh lookup.
- **Screen turns off while paused:** expected with "Keep screen on: While playing" once the paused
  clock appears. Choose "Always" to keep it on.

## Credits

Lyrics by [LRCLIB](https://lrclib.net), a free, open lyrics database.

Typography uses [Inter 4.1](https://github.com/rsms/inter/releases/tag/v4.1),
Copyright © The Inter Project Authors, licensed under the SIL Open Font License 1.1.
Only `InterVariable.ttf` (879,708 bytes) is bundled; the full license is in
[`app/src/main/assets/licenses/Inter-OFL.txt`](app/src/main/assets/licenses/Inter-OFL.txt).
The outline icons are project-owned vector paths; no Apple fonts or symbols are bundled.

## R2 visual review

The three-zone dashboard now uses Inter, translucent panels, an album-colored background,
and thin outline icons. Playback controls retain their media-session behavior. The seek pill
expands while touched, art settles back when paused, play/pause morphs, and the source ring
moves between rail icons. Settings, onboarding, the app picker and ambient mode share the styling.

`ui/theme/Motion.kt` holds the spring and transition specifications. Android's **Remove animations**
setting is observed live: entry/track transitions use short fades, decorative background and
equalizer movement stops, and press/album scaling is disabled. Ambient burn-in protection retains
infrequent position changes rather than continuous travel. The root pixel shift remains a separate
burn-in measure controlled by its own setting.

Glass shows DeskDeck's own soft radial-gradient background through translucent fills, highlights
and hairline borders. It does not capture or blur foreground text or other apps. This avoids an
extra full-screen blur pass on an always-on tablet; Android versions below 12 use solid tonal
panel fills. Background colors are capped to dark tones, and drift updates at approximately 15 Hz
in the draw phase. Drift stops when the activity is paused or a settings, picker, ambient or
paused-clock screen covers it. UI transitions use the display's frame clock; no fixed 120-fps
claim is made without device profiling.

R2 is a visual review milestone. Volume HUD, notification banners, word-timed lyrics and the
Spotify library remain scheduled for R3–R6. Review the installed tablet build before continuing.
