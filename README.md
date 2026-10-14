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
- **Night Mode:** a StandBy-style bedside screen on a schedule: a big dim clock, the next alarm,
  and small music controls, in red when the room is dark.
- **Split screen and pop-up view:** a full, a half-screen and a mini-player layout, picked by
  the window's size.
- **WhatsApp calls:** an incoming call banner with Decline / Answer, a call card with a timer
  during the call, and music paused and resumed around it.
- **Morning Brief:** after your wake-up time, the weather, your market watchlist (stocks, index
  ETFs, Bitcoin) and a few headlines, until you tap Done.
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

223 JVM unit tests cover the logic that doesn't need a device:

| Area | Tests |
| --- | --- |
| Lyrics | LRC parser incl. enhanced word stamps, word-timing estimator, instrumental-gap rows, title cleaning, search-result matching, repository caching and offsets |
| Playback | position interpolation, active lyric line lookup, time formatting |
| Media sessions | session selection, metadata mapping, ad/podcast/live detection, shuffle/repeat resolution, the resume ladder |
| Notifications & volume | banner rules (silent, ongoing, DND, muted apps), notification counting, volume levels and limits |
| Calls | WhatsApp call detection from fake notification data (structured signals, English/Malay wording, missed calls and messages ignored), the call state machine, when music pauses and resumes |
| Layout | window size classes (full tablet, half split, narrow split, pop-up) |
| Spotify | PKCE (RFC 7636 vector), Web API error classification |
| Burn-in | the full display-mode / keep-screen-on policy, pixel shift orbit, drift, rail opacity |
| Other | settings persistence, rail pins, app search, letterbox detection, script detection |

### Debug tools (debug builds only)

- **Settings › Calls › Simulate call** posts made-up call notifications shaped like WhatsApp's
  (structured, Malay wording only, or without call buttons). They go through the real detection
  path, and their Answer, Decline and Hang up buttons work, so the banner, the call layout and the
  music pause/resume can be tried without a call.
- adb hooks (`DebugTools`): `-a com.deskdeck.debug.FAKE_CALL --es state incoming|ongoing|end`
  feeds a call straight into the call monitor; `-a com.deskdeck.debug.SPLIT_TEST --es mode
  adjacent|plain|pi_immutable|pi_mutable|voip_*` tries the ways of opening an app, or a stand-in
  call screen in its own task with picture-in-picture, beside DeskDeck. Both via
  `adb shell am start -n com.deskdeck/.MainActivity …`.
- In debug builds, tapping a call button logs what kind of intent WhatsApp gave it (tag
  `DeskDeckCall`).

None of this (nor the notification permission it needs) is in release builds.

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
  calls, lyrics (incl. audio delay), night mode (incl. the Morning Brief), clock, appearance, sidebar.

### Split screen and pop-up view

DeskDeck picks its layout from the window's size class, not the device, and follows resizes live
without restarting:

| Window | Layout |
| --- | --- |
| **Full tablet**, or a wide split (2/3 of the screen) | Sidebar, Now Playing and lyrics, as usual |
| **Half** of the screen in split screen | Sidebar, Now Playing, and the line being sung with one before and after (the card alone if there's no room) |
| **Narrow** split (1/3) or a **pop-up** window | Mini player: art, title and artist, a thin progress line, previous / play-pause / next |

The album art and title move between the layouts (just a fade with Remove animations). Night Mode
puts the clock on top with a one-row player under it, and the Morning Brief stacks its cards.
Banners, the notification panel, the volume HUD and the ambient screen fit every size.

### Calls (WhatsApp)

On by default under **Settings › Calls**; it uses the notification access DeskDeck already has.
WhatsApp and WhatsApp Business are supported.

- **Incoming call:** a large banner at the top, above every screen (dimmed in Night Mode), with
  the caller, voice or video, **Decline** and **Answer**. It stays until the call is answered, declined or missed; the × or a deliberate
  swipe up hides it. With **Hide content** (Settings › Notifications) it says "WhatsApp video call"
  instead of the name and shows no picture. When Do Not Disturb would keep the call quiet, only a
  small pill shows; tap it for the banner.
- **During the call:** a call card with the caller, a running timer, **Show call** (WhatsApp's call
  screen) and **Hang up** when WhatsApp offers its own button. The player shrinks to the mini
  player; the lyrics stay beside it only on the full tablet. A video call keeps playing in
  WhatsApp's own floating window over DeskDeck (move or resize it there); **Show call** brings it
  back full screen.
- **Music:** if music is still playing once the call connects, DeskDeck pauses it and plays it
  again afterwards (**Resume music after calls**). It only resumes what it paused itself.
- DeskDeck only ever sends WhatsApp's own buttons. It never fakes taps, and it opens WhatsApp only
  when you tap a button that says so.

### Night Mode

Off until you turn it on under **Settings › Night Mode**.

- **Schedule:** a bedtime and wake-up time (crossing midnight is fine), the days it runs, and
  optionally a different weekend schedule. A night belongs to the day you wake up on, so "Saturday"
  means the night from Friday to Saturday.
- **Start now / End now:** **Start Night Mode now** in Settings, or **long-press the clock** in the
  sidebar, starts it until the next wake-up; ending it early lasts until the next bedtime.
- **The screen:** true black, the clock on the right (bold, thin or analog; 12/24 h) with the date
  and your next alarm, and the playing song on the left with play/pause and skip. Everything is
  drawn dim, the window brightness is held at the night level (5 % by default), and the layout
  drifts a little every 90 s. Nothing else from DeskDeck shows: banners are replaced by a tiny dot
  next to the date, and the volume HUD has a dim night look.
- **Red tint:** on, off, or automatic: red when the light sensor reads a dark room (or all night on
  tablets without a light sensor).
- **Tap** brightens the screen for 10 seconds and shows **End Night Mode** and the settings button.
  With TalkBack on, those stay available all the time.
- DeskDeck shows Night Mode only while it's on screen. It doesn't turn the screen on or appear
  over other apps at bedtime (that would need a full-screen intent or an overlay).

### Morning Brief

After the wake-up time of a scheduled night, DeskDeck shows a brief in place of the dashboard for
15, 30 or 60 minutes, or until you tap **Done** (it never stays longer than 4 hours). **Settings ›
Night Mode › Show Morning Brief now** opens it any time.

- **Weather:** now, high/low, chance of rain and sunrise, from [Open-Meteo](https://open-meteo.com)
  (no key). Use a city name, or the tablet's approximate location (DeskDeck asks for coarse
  location only when you choose that). °C or °F.
- **Market:** one page per watchlist item, turning every few seconds (swipe to browse; it doesn't
  turn by itself under TalkBack), each with price, change and a small chart. Indices aren't on the
  free data plan, so they're shown through the ETF that tracks them: **S&P 500** via SPY,
  **Nasdaq 100** via QQQ, **Dow Jones** via DIA ("Shown via SPY" on the card). Stock symbols
  (`AAPL`) work as they are. **Bitcoin**/**BTC** and **Ethereum**/**ETH** show the last 24 hours,
  since crypto never closes; other pairs can be written like `SOL/USD`. Needs a Twelve Data key
  (below).
- **Headlines:** the latest three from CNBC's public markets RSS feed (one with very large text).
  Tapping one asks before opening it in your browser.
- **Fresh data:** about 15 minutes before each wake-up, a background job fetches everything once a
  network is available and caches it, so the brief appears instantly and also works offline.
  Android decides the exact time (Doze and battery saving can delay it), so when the brief opens
  with data older than 30 minutes, or without something it should show (e.g. a newly added
  watchlist item), it refreshes then and says "Updating…". The bottom line shows when it was last
  updated.

#### Twelve Data key (for the market card)

1. Create a free account at <https://twelvedata.com> and copy your API key from the dashboard.
2. Add it to `local.properties` (gitignored, never committed):

   ```properties
   twelvedata.apiKey=your_key_here
   ```

3. Rebuild and install. Without a key the market card says how to add one; weather and headlines
   work without it.

The free plan allows 800 requests a day and 8 a minute. Each watchlist item costs 2 requests per
update (quote and chart), so keep the watchlist to about 4 items. Markets outside the US (e.g.
KLCI) need a paid plan, so they aren't offered.

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
│              gap rows), Volume, RailApp, night/ (NightScheduleCalculator), brief/ (MorningBrief,
│              briefVisible)
├─ media/      MediaListenerService (notification listener), NotificationAccess, NotificationCounter,
│              call/ (CallSource, WhatsAppCallSource, CallText, CallMonitor + reduceCall, CallLauncher,
│              CallMusic),
│              NotificationBanners, MediaSessionRepository + SessionEntry (framework MediaController;
│              compat controller only for shuffle/repeat), SessionSelector, MediaMapping,
│              VolumeController, OutputLatency, ArtworkLoader, Letterbox, NightModeController
│  └─ sources/ PlaybackSource, ResumeChain, SourceSwitcher (rail taps; never launches apps)
├─ data/       lyrics/ (LyricsProvider, LrclibProvider, LrcParser, WordTiming, TitleCleaner,
│              LyricMatcher, DefaultLyricsRepository), spotify/ (SpotifyAuth, TokenStore, SpotifyApi,
│              SpotifyLibrary), brief/ (BriefApis, BriefRepository, RssParser, BriefPrefetch),
│              db/ (Room: lyrics cache, offsets, playlist cache, brief cache), settings/
│              (DataStore, NightSettings), NetworkMonitor
├─ launcher/   InstalledAppsRepository (LauncherApps), RailAppsRepository, AppLauncher, AppIconFetcher
├─ burnin/     BurnInPolicy (pure state machine), Motion (pixel shift, drift, rail opacity), Compose glue
├─ ui/         AppRoot, dashboard/, components/ (LeftRail, NowPlayingCard, SeekBar, LyricsPanel…),
│              notifications/, volume/, spotify/, onboarding/, picker/, settings/, ambient/,
│              lyrics/, night/, brief/, call/, adaptive/ (DeskSize, shared elements), common/,
│              theme/ (Motion, Glass, Type)
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
- **Night Mode:** `NightScheduleCalculator` (pure, tested across midnight, weekend schedules,
  DST and manual overrides) says whether it's night and when that next changes;
  `NightModeController` sleeps exactly until then. The Morning Brief's visibility is another pure
  function (`briefVisible`) of the last wake-up, the chosen duration and **Done**.
- **Morning Brief data:** `BriefRepository` fetches weather, market and headlines independently
  and merges them into one cached copy in Room, so one failing part keeps its last value.
  `BriefScheduler` enqueues a unique WorkManager job 15 minutes before the next wake-up.
- **Layouts:** `currentDeskSize()` maps Material's window size class (600 / 840 dp, plus short
  windows under 480 dp) to full, half or mini layouts; one `SharedTransitionLayout` lets the art and
  title travel between them.
- **Calls:** the listener hands notifications from call apps to `CallMonitor` before, and instead
  of, the banner queue. A `CallSource` per app (only WhatsApp so far) reads them, preferring
  Android's structured call signals (category, CallStyle extras: call type, caller, video flag,
  answer / decline / hang-up intents, WhatsApp's call channel) over wording, which is a fallback
  kept in `CallText`. `reduceCall` is the pure state machine (idle, incoming, ongoing, ended).
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
| Material 3 adaptive (BOM-managed, brings androidx.window) | Window size classes for split screen and pop-up view |
| Hilt (+ hilt-lifecycle-viewmodel-compose) | Dependency injection, including the notification listener service |
| androidx.media (compat) | Shuffle/repeat **state** lives in the compat "extra binder", which the framework `MediaController` can't read. Used only for that (it's deprecated in favor of Media3, which uses the same mechanism internally for other apps' sessions). |
| Retrofit + Moshi (codegen via KSP) + OkHttp 4.12 | LRCLIB, Open-Meteo and Twelve Data clients. Codegen adapters avoid reflection and keep R8 simple. OkHttp stays on 4.x because Retrofit 3 and Coil are built against it. |
| Room | Lyrics cache, per-song timing offsets, Spotify playlist cache, Morning Brief cache |
| WorkManager + hilt-work | Fetches the Morning Brief before you wake up, waiting for a network and surviving reboots |
| AndroidX Browser | Spotify sign-in in a Custom Tab over DeskDeck |
| DataStore Preferences | Settings and pinned apps |
| Coil 3 | Album art from URIs, plus launcher icons through a small custom fetcher (memory-cached) |
| Palette | Card colors from album art |

Not added on purpose: icon packs (a handful of vector drawables instead), a navigation library (one
screen plus two overlays), and transliteration libraries (Android's built-in ICU is used).

---

## Known limitations

- **Starting an idle app** without opening it is best-effort: asking its session to load its last
  song, a media-button broadcast, or (optional) its media browser, about 1.5 s each. Since
  Android 12 an app woken by another app may not start its player service, and **YouTube Music**
  and **Apple Music** can't be started without showing them: their player stays inactive or empty
  until their own screen opens (tested on the Tab S9 FE). DeskDeck never shows another app on its
  own, so for them the card says "Start playing from …" with an **Open …** button. Once opened,
  they stay available in DeskDeck while Android keeps them in memory.
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
- **Night Mode** only takes over while DeskDeck is on screen. It can't switch the screen on at
  bedtime or appear over another app without a full-screen intent or an overlay permission, which
  DeskDeck doesn't use. The next alarm comes from `AlarmManager.getNextAlarmClock()`, so it shows
  alarms set as alarm clocks (the Clock app's), not plain reminders.
- **Morning Brief timing** depends on Android: the prefetch job may run late under Doze or battery
  saving, so the brief can show "Updating…" for a moment when it opens.
- **Market data** is from Twelve Data's free plan: US-listed symbols and crypto only, indices
  through ETFs (so the value is the ETF's price, not index points), and quotes may be delayed.
- **Call detection** reads WhatsApp's notifications. Its structured signals are reliable; if
  WhatsApp stops sending them, detection falls back to English and Malay wording, which any
  WhatsApp update can change and which doesn't cover other languages. Other call apps (Telegram,
  Meet) aren't supported yet.
- **No call beside DeskDeck:** WhatsApp's call screen isn't open to other apps and can only be
  reached through WhatsApp's own (immutable) notification intents. On One UI those always bring it
  up full screen: from its floating window, from the background or new, whatever the order, the
  launch bounds, or which half of a split screen asks (tested with stand-ins on a Tab S9 FE, One UI
  8.5). Only you can put it into split screen (Recents → WhatsApp's icon → Open in split screen
  view). So DeskDeck leaves a video call in WhatsApp's floating window and offers **Show call**.
- **Answer** sends WhatsApp's own answer action; what WhatsApp then shows (its call screen full
  screen, or nothing) is up to WhatsApp.
- **Music after calls:** DeskDeck resumes only what it paused itself. Players that paused on
  their own when the call took the audio decide themselves whether to continue.
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

Weather data by [Open-Meteo.com](https://open-meteo.com) (CC BY 4.0). Market data by
[Twelve Data](https://twelvedata.com). Headlines from CNBC's public RSS feed; articles open on
CNBC's site.

Typography uses [Inter 4.1](https://github.com/rsms/inter/releases/tag/v4.1),
Copyright © The Inter Project Authors, licensed under the SIL Open Font License 1.1.
Only `InterVariable.ttf` (879,708 bytes) is bundled; the full license is in
[`app/src/main/assets/licenses/Inter-OFL.txt`](app/src/main/assets/licenses/Inter-OFL.txt).
The night clock uses [Nunito](https://github.com/googlefonts/nunito), Copyright 2014 The Nunito
Project Authors, SIL Open Font License 1.1 (variable font; license in
[`app/src/main/assets/licenses/Nunito-OFL.txt`](app/src/main/assets/licenses/Nunito-OFL.txt)), as a
stand-in for Apple's SF Pro Rounded.

The outline icons are project-owned vector paths; no Apple fonts or symbols are bundled.
