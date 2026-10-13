# DeskDeck

A desk music dashboard for Android tablets: a dark, Android Auto "Coolwalk"-style screen that shows
and controls whatever music app is playing, with time-synced lyrics and burn-in protection for
AMOLED tablets that sit on a desk all day. No maps, no navigation.

- **Works with any player** that publishes a media session: Spotify, YouTube Music, Apple Music,
  YouTube, podcast apps… No per-app SDKs or accounts.
- **Now playing:** album art with colors taken from the cover, smooth seek bar, play/pause,
  skip, shuffle and repeat, and a volume slider.
- **Apple Music-style lyrics** from [LRCLIB](https://lrclib.net): the current line fills word by
  word, nearby lines blur, instrumental breaks breathe, and the lyrics wait for the sound to
  reach you (Bluetooth delay is measured). Tap a line to jump there; per-song timing correction,
  optional romanization, cached for offline replays.
- **Sidebar:** clock, the notification bell, and your pinned music apps as source switchers: tap
  one to show and control its music here. DeskDeck never leaves the screen.
- **Notifications** inside DeskDeck: small banners by the bell, and a panel with all of them.
- **Volume HUD:** the volume buttons show DeskDeck's own glass indicator instead of the system panel.
- **Spotify library** (optional): browse your playlists and start one on this tablet.
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
- Internet access on the tablet (lyrics, and the optional Spotify library).

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

180 JVM unit tests cover the logic that doesn't need a device:

| Area | Tests |
| --- | --- |
| Lyrics | LRC parser incl. enhanced word stamps, word-timing estimator, instrumental-gap rows, title cleaning, search-result matching, repository caching and offsets |
| Playback | position interpolation, active lyric line lookup, time formatting |
| Media sessions | session selection, metadata mapping, ad/podcast/live detection, shuffle/repeat resolution, the resume ladder |
| Notifications & volume | banner rules (silent, ongoing, DND, muted apps), notification counting, volume levels and limits |
| Spotify | PKCE (RFC 7636 vector), Web API error classification |
| Burn-in | the full display-mode / keep-screen-on policy, pixel shift orbit, drift, rail opacity |
| Other | settings persistence, rail pins, app search, letterbox detection, script detection |

### Release build

`./gradlew assembleRelease` produces an R8-shrunk APK (about 2.5 MB, most of it the Inter font). It's unsigned. Configure your
own signing config before distributing.

---

## Granting Notification Access on the tablet

Android only lets an app see other apps' media sessions if it's an enabled **notification
listener**. DeskDeck uses that access for media sessions, the bell's count, and its own
notification banners and panel. Notification text is read only to show it there, kept in memory
while shown, and never stored or sent anywhere.

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
  - Tap a music app to show it here: its song, controls and lyrics. If it isn't playing, DeskDeck
    tries to start it in the background; it never opens the app. The selected app has a glowing
    ring, and the one playing has a moving equalizer.
  - Long-press an app for **Open** (the only way DeskDeck opens another app), **Pin source**,
    **Spotify library** (Spotify, when set up) and **Remove**. Apps that aren't installed are
    greyed out and offer **Get app** instead.
  - **+** adds apps to the sidebar. **⊞** shows all apps: tap one to show its music here, or tap
    its pin to add it to the sidebar.
  - **Settings › Sources**: follow whichever app starts playing (default) or stay on your choice.
  - The bell opens the notification panel (see below).
- **Lyrics:**
  - The current line fills word by word as it's sung; instrumental breaks show three dots.
  - Lyrics wait for the sound to reach you: DeskDeck measures the audio output's delay (about
    0.4 s over Bluetooth) and shifts them by it. If a speaker buffers more than it reports, set
    **Settings › Lyrics › Extra delay** once; it's remembered for that speaker only.
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
- **Notifications:**
  - New notifications slide out from the sidebar's bell as small banners while DeskDeck is on
    screen. They close after a few seconds (the thin line shows how long is left); swipe one left
    to hide it sooner.
  - Tap the bell, or a banner, to open the notification panel: everything the bell counts, newest
    first. Tap one to read it in full. The × or a swipe left clears it from the system (the bell
    follows); **Clear all** asks for a second tap to confirm, since cleared notifications are gone
    from Android too.
  - Silent notifications, ongoing ones (downloads, navigation), the music apps' own player
    notifications and group summaries never show a banner. New banners wait while you're dragging
    the seek bar or the volume slider.
  - **Settings › Notifications**: turn banners off, hide message content, follow Do Not Disturb
    (with its exceptions), set how long banners stay, and turn banners off per app.
- **Settings** (gear icon): screen & burn-in, sources (incl. the Spotify library), notifications,
  lyrics (incl. audio delay), clock, appearance, sidebar.

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

## Spotify library (optional)

Browse your Spotify playlists inside DeskDeck and start one on this tablet without switching apps.
It's off until you set it up. Everything else in DeskDeck works without a Spotify login.

### 1. Create a Spotify app (once)

1. Go to <https://developer.spotify.com/dashboard> and sign in with your Spotify account.
2. **Create app**: any name and description (e.g. "DeskDeck"). Under **Redirect URIs** add exactly
   `deskdeck://spotify-callback`. Under **Which API/SDKs are you planning to use?** tick **Web API**.
   Accept the terms and save.
3. Open the app's **Settings** and copy the **Client ID**. DeskDeck uses PKCE, so there is no
   client secret to copy, and none is ever stored.
4. Open **User Management** and add the Spotify account you'll sign in with (name and the email
   of that account). In Development Mode only listed accounts can use the app, even your own if
   it isn't the owner account.

### 2. Add the Client ID to the build

In `local.properties` (next to `sdk.dir`; the file is gitignored, so the ID is never committed):

```properties
spotify.clientId=your_client_id_here
```

Rebuild and install (`./gradlew assembleDebug`).

### 3. Connect in DeskDeck

**Settings › Sources › Spotify library**: turn it on, then **Connect**. Spotify's sign-in page
opens in a sheet over DeskDeck; after you agree it returns to DeskDeck by itself. Then open the
library by long-pressing Spotify in the sidebar (**Spotify library**), or with the **Spotify
library** button on the Now Playing card while Spotify is playing.

Tokens are stored encrypted with a key kept in the Android Keystore and refreshed automatically.
**Disconnect** deletes them and the cached playlist list. DeskDeck asks only for:
`playlist-read-private`, `playlist-read-collaborative`, `user-library-read`,
`user-read-playback-state`, `user-modify-playback-state`.

### Limits (Spotify's, not DeskDeck's)

- **Development Mode**: a new Spotify app runs in Development Mode. The app owner needs Spotify
  Premium, at most 5 accounts can be added under User Management, search results are capped at
  10, and some Web API endpoints aren't available. For a personal desk display this is fine.
- **Premium** is required to start playback through the Web API. Without it DeskDeck says so;
  you can still pick music in Spotify and see it here.
- **Not registered**: if Spotify answers "the user may not be registered", add the account under
  User Management (step 1.4) and connect again.
- **Rate limits** (HTTP 429, including the daily quota) show "Spotify is busy" with the wait time
  Spotify gives.
- **Waking Spotify**: playback starts on *this tablet's* Spotify. If Spotify isn't running or
  hasn't registered as a device yet, DeskDeck wakes it in the background with a play key press
  (the same way the sidebar starts apps) and retries for about 8 seconds. Spotify may play its
  last track for a moment before switching to the playlist. If it still can't be reached, the
  library shows **Open Spotify**; DeskDeck never opens Spotify on its own.
- **Offline**: the last playlist list stays visible; starting one needs a connection.
- **Spotify-made playlists** (editorial, Daily Mix and similar) may be missing or not playable for
  apps created after late 2024; your own and followed user playlists work.
- **App Remote SDK** isn't used: it isn't published to Maven and needs a manual AAR. The Web API
  route covers resume and play.
- YouTube Music and Apple Music have no comparable public API for this, so their sidebar icons
  stay source switchers only.

---

## Architecture

Single activity, Jetpack Compose with Material 3, MVVM with `StateFlow`, Hilt for dependency
injection, coroutines.

```
com.deskdeck
├─ domain/     Track, PlaybackInfo (position interpolation), NowPlaying, Lyrics (lines, words,
│              gap rows), Volume, RailApp
├─ media/      MediaListenerService (notification listener), NotificationAccess, NotificationCounter,
│              NotificationBanners, MediaSessionRepository + SessionEntry (framework MediaController;
│              compat controller only for shuffle/repeat), SessionSelector, MediaMapping,
│              VolumeController, OutputLatency, ArtworkLoader, Letterbox
│  └─ sources/ PlaybackSource, ResumeChain, SourceSwitcher (rail taps; never launches apps)
├─ data/       lyrics/ (LyricsProvider, LrclibProvider, LrcParser, WordTiming, TitleCleaner,
│              LyricMatcher, DefaultLyricsRepository), spotify/ (SpotifyAuth, TokenStore, SpotifyApi,
│              SpotifyLibrary), db/ (Room: lyrics cache, offsets, playlist cache), settings/
│              (DataStore), NetworkMonitor
├─ launcher/   InstalledAppsRepository (LauncherApps), RailAppsRepository, AppLauncher, AppIconFetcher
├─ burnin/     BurnInPolicy (pure state machine), Motion (pixel shift, drift, rail opacity), Compose glue
├─ ui/         AppRoot, dashboard/, components/ (LeftRail, NowPlayingCard, SeekBar, LyricsPanel…),
│              notifications/, volume/, spotify/, onboarding/, picker/, settings/, ambient/,
│              lyrics/, theme/ (Motion, Glass, Type)
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
- **Sources:** a rail tap selects the app (`SourceSwitcher`). If it has no session, the
  `ResumeChain` tries its `PlaybackSource`s in order: a play key to its paused session, a
  media-button broadcast to its receiver, and (if enabled) its media browser, waiting for a session
  after each. If none works, the card offers **Open …**.
- **Lyric timing:** lines without word stamps get estimated words (`WordTiming`), and
  `buildLyricRows` adds instrumental gaps. Lyric time is the position plus the per-song offset,
  minus the audio output delay (`OutputLatency`, measured with a silent `AudioTrack`) and the
  per-speaker extra delay. Everything that moves per frame reads the clock in the draw phase.
- **Volume:** `VolumeController` handles the volume keys and follows the shown session: an
  adjustable remote volume (Cast) through its `MediaController`, otherwise the music stream,
  always without the system panel. Changes from anywhere show the HUD.
- **Notifications:** the listener counts what a person would call unread, offers each new one to
  `NotificationBanners` (pure rules in `shouldShowBanner`), and reads the full list only while
  the panel is open.
- **Spotify:** PKCE sign-in in a Custom Tab, tokens encrypted with a Keystore key, playlists cached
  in Room, playback started on this tablet's Spotify Connect device.
- **Burn-in:** `BurnInPolicy` decides the display mode and keep-screen-on from idle time, playback
  and settings. The UI re-evaluates it exactly when the next threshold is due.

### Design and motion

- **Motion:** `ui/theme/Motion.kt` holds the shared springs and durations. Android's **Remove
  animations** setting is followed live: transitions become short fades, decorative movement
  (background drift, equalizer, lyric wave, word lift, breathing dots) stops, and press/album
  scaling is off. Ambient burn-in protection keeps infrequent position changes instead of travel.
- **Glass:** translucent fills, a top highlight and a hairline border over DeskDeck's own blurred
  album-color background; it never captures or blurs other content (no extra full-screen blur
  pass on an always-on tablet). Below Android 12 panels use solid tonal fills.
- **Background:** colors capped to dark tones, drift at about 15 Hz in the draw phase, paused when
  the activity is in the background or something covers the dashboard.
- **Performance:** measured on the Tab S9 FE (90 Hz) during playback with lyrics: about 88 fps,
  1.5 % of frames over their deadline (`adb shell dumpsys gfxinfo com.deskdeck`). Lyric blur is a
  layer effect and only the word being sung redraws each frame.

### Dependencies and why

| Library | Why |
| --- | --- |
| Compose BOM, Material 3, Activity, Lifecycle | UI |
| Hilt (+ hilt-lifecycle-viewmodel-compose) | Dependency injection, including the notification listener service |
| androidx.media (compat) | Shuffle/repeat **state** lives in the compat "extra binder", which the framework `MediaController` can't read. Used only for that (it's deprecated in favor of Media3, which uses the same mechanism internally for other apps' sessions). |
| Retrofit + Moshi (codegen via KSP) + OkHttp 4.12 | LRCLIB client. Codegen adapters avoid reflection and keep R8 simple. OkHttp stays on 4.x because Retrofit 3 and Coil are built against it. |
| Room | Lyrics cache, per-song timing offsets, Spotify playlist cache |
| AndroidX Browser | Spotify sign-in in a Custom Tab over DeskDeck |
| DataStore Preferences | Settings and pinned apps |
| Coil 3 | Album art from URIs, plus launcher icons through a small custom fetcher (memory-cached) |
| Palette | Card colors from album art |

Not added on purpose: icon packs (a handful of vector drawables instead), a navigation library (one
screen plus two overlays), and transliteration libraries (Android's built-in ICU is used).

---

## Known limitations

- **Starting an idle app** without opening it is best-effort: a play key, a media-button
  broadcast, or (optional) its media browser. Some apps ignore all three until they've been opened
  once; then the card says "Start playing from …" with an **Open …** button.
- **Shuffle/repeat** are best-effort. With apps built on Android's media compat libraries (Spotify,
  YouTube Music) they work with real state. Apps that only offer custom actions can be toggled, but
  their state isn't shown. Otherwise the buttons are disabled.
- **Audio delay** is measured with a short silent test track on the same output (only while music
  is already playing), using Android's presentation timestamps. These include what a Bluetooth
  device reports about itself, but some speakers buffer more without saying so; that part can
  only be set by ear, per speaker.
- **Word timing** is exact only for lyrics with word stamps (enhanced LRC). LRCLIB almost always
  has line timing only, so DeskDeck estimates when each word is sung from its syllables. It's
  close for most songs but can run ahead or behind on held notes or fast runs. No Spotify, Apple
  Music or Musixmatch data is used; a word-level provider can be added behind `LyricsProvider`.
- **Lyrics** come only from LRCLIB's community database. Some tracks have no lyrics or only unsynced
  ones. Use **Retry**, and **Earlier / Later** if the timing is off.
- **Ad / podcast / live detection** uses metadata heuristics (Spotify's ad markers, episode ids,
  podcast apps, no duration + not seekable). An unusual app may be misclassified.
- **Romanization** needs Android 10+. Japanese kanji get a Mandarin-style reading, which is only a
  rough guide.
- **Samsung taskbar handle:** with gesture navigation and the taskbar enabled, One UI draws a small
  handle at the bottom that apps can't hide. Turn it off under **Settings › Display › Taskbar** (or
  the navigation bar's gesture hint).
- **System pop-ups:** Android may still draw its own heads-up notification above DeskDeck, and
  apps can't suppress it. If you only want DeskDeck's banners, turn off pop-ups for those apps in
  Android's notification settings (on Samsung: **Settings › Notifications › Notification pop-up
  style**, or per app). Notification text is read only for a banner or while the panel is open and
  is kept in memory, never stored.
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
