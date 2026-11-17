# Ultra Music Player

An offline Android music player built with Kotlin, Jetpack Compose (Material 3), AndroidX Media3, Room and DataStore. It finds the audio on your phone, organises it by tags and real folders, and adds a five‑band equalizer, synced lyrics, tag editing, a ringtone trimmer, three home‑screen widgets and 24 background skins.

- **Application ID:** `com.ultramusicplayer.app`
- **Minimum Android version:** Android 8.0 (API 26)
- **Target / compile SDK:** 35 (Android 15)
- **Toolchain:** JDK 17, Gradle 8.11.1 (wrapper), Android Gradle Plugin 8.7.3, Kotlin 2.0.21

---

## ⚠️ Build status: read this first

This project was written in an environment **without the Android SDK and with no access to Google's Maven repository**, so it has **not yet been compiled by Gradle or run on an emulator or a phone**. The pure-Kotlin logic was compiled and unit-tested (see [Verification](#verification-status)). The first Gradle build may still turn up small compile errors that need fixing. The included GitHub Actions workflow is the quickest way to find them and to produce the APK.

---

## Build the debug APK

### Option A: GitHub Actions (no local Android setup)

1. Create a GitHub repository and push this folder to it.
2. The **Build APK** workflow (`.github/workflows/android.yml`) runs on every push. It runs the unit tests and `assembleDebug`.
3. Open the workflow run → **Artifacts** → download **UltraMusicPlayer-debug-apk** (a zip that contains `app-debug.apk`).

### Option B: Android Studio

1. Open the `UltraMusicPlayer` folder in Android Studio (Ladybug or newer).
2. Let Gradle sync; Studio installs SDK 35 if it's missing.
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.

### Option C: command line

```bash
# Requires JDK 17 and the Android SDK (set ANDROID_HOME or create local.properties with sdk.dir=...)
./gradlew testDebugUnitTest assembleDebug
```

**APK path:** `app/build/outputs/apk/debug/app-debug.apk`

## Install on your phone

**With USB (adb):**
1. On the phone: Settings → About phone → tap *Build number* 7 times → back to Settings → System → Developer options → turn on **USB debugging**.
2. Connect the phone, accept the prompt, then run:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

**Without a computer:**
1. Copy `app-debug.apk` to the phone (Drive, email, USB cable…).
2. Open it from the Files app. When asked, allow **Install unknown apps** for that app, then tap **Install**.
3. Open **Ultra Music Player** and follow the three welcome steps.

## Build a signed release App Bundle (AAB)

Signing credentials are **never** stored in the repository. The build reads them from environment variables, from `~/.gradle/gradle.properties`, or from the untracked `local.properties`.

1. Create a keystore once and keep it safe outside the repo:
   ```bash
   keytool -genkeypair -v -keystore ~/keys/ultra-release.jks -alias ultra \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Provide the credentials, for example in your shell:
   ```bash
   export ULTRA_KEYSTORE_PATH=$HOME/keys/ultra-release.jks
   export ULTRA_KEYSTORE_PASSWORD='…'
   export ULTRA_KEY_ALIAS=ultra
   export ULTRA_KEY_PASSWORD='…'
   ```
   (or put the same four keys in `~/.gradle/gradle.properties`).
3. Build:
   ```bash
   ./gradlew bundleRelease
   ```
   Output: `app/build/outputs/bundle/release/app-release.aab`. Without the variables the bundle is built **unsigned**.

In CI, store the keystore as a base64 secret, decode it to a temp file in the job, and expose the passwords as secrets mapped to the same variable names.

### Optional: in-app video results

Video search works without a key: it opens the YouTube app or the browser. To show results inside the app, create a YouTube Data API v3 key and supply it as `YOUTUBE_API_KEY` (environment variable, `local.properties`, or a GitHub Actions secret of the same name). It is compiled into `BuildConfig` and is never committed.

---

## Privacy policy

See [PRIVACY_POLICY.md](PRIVACY_POLICY.md). Use its public link for store listings.

## Ads (AdMob)

- **App ID** `ca-app-pub-8306955081407788~4147890222` is set in the manifest (through `manifestPlaceholders` in `app/build.gradle.kts`).
- **Banner** `ca-app-pub-8306955081407788/5403317480` is an anchored adaptive banner above the mini-player. It's hidden on Now Playing, Edit and Trim.
- **Interstitial** `ca-app-pub-8306955081407788/5572652532` may show when you open an album, artist, playlist, genre or folder. It shows at most every 4th time, no more than once every 3 minutes, and never in the first minute of a session.
- Ads are **muted**, so they never interrupt your music.
- On first launch, the Google consent form (UMP) appears where the law requires it (EEA, UK, Switzerland). A **Privacy options** button then appears in Settings.
- **Debug builds show Google's test ads.** Clicking real ads on your own phone can get an AdMob account suspended. Release builds (`./gradlew bundleRelease`) use the real unit IDs. To try real units in a debug build, build with `./gradlew assembleDebug -PuseRealAds=true`, and add your phone as a test device in AdMob first.
- Before publishing, add an `app-ads.txt` file to your developer website and link the app in AdMob once it's on Google Play.

## Features

| Area | What's implemented |
|---|---|
| Library | MediaStore scan (all volumes, including SD cards on Android 10+), plus user-added folders through the system folder picker (SAF). Dedupes by path. Auto-refresh through a debounced MediaStore observer. Background scanning with results published early. |
| Browse | Songs, Albums (grid), Artists, Album Artists, Genres, Playlists, Folders (real hierarchy per storage volume). Song sorting by title, artist, album, date added, duration, ascending or descending. |
| Search | Instant, case-insensitive search across titles, file names, albums, artists, album artists, genres, playlists and folder names. Multi-word matching, grouped results, keeps working during a rescan. |
| Playlists | Create, rename, delete, reorder (up/down), remove items. Add a single song, or all songs from an album, artist, genre, folder or the queue. **Recently Played** comes from listening history (Room). |
| Playback | Media3 ExoPlayer in a `MediaSessionService`. Mini-player and full Now Playing screen (artwork, seek bar, elapsed and remaining time, previous, play/pause, next, shuffle, repeat off/all/one). |
| Queue | Visible queue with move up/down, remove, tap-to-play, and save as playlist. **Play Next** inserts directly after the current track. Shuffle physically reorders the queue around the current track, so Play Next stays next even when shuffled. Queue, position, repeat and shuffle are restored after a restart. |
| System integration | Media notification with artwork, title, artist, previous/play/pause/next and a custom **Stop** button. Lock screen, wired-headset and Bluetooth controls through the media session. Pauses when headphones disconnect, and handles audio focus. Bluetooth "play" resumes the last queue after the app was closed (`onPlaybackResumption`). |
| Widgets | **Slim Player** 4×1 (charcoal pill: title, play/pause, next), **Album Player** 2×2 (full-bleed artwork, title, prev/play/next), **Artwork Strip** 4×1 (gradient card: artwork, title, artist, prev/play/next). |
| Shake to skip | Off by default. Next or previous track, adjustable sensitivity, two-peak detection and a 1.5 s cooldown. The sensor is registered only while music is playing. Screen-off limits are explained on screen. |
| Equalizer | Five bands (60 Hz, 230 Hz, 910 Hz, 3.6 kHz, 14 kHz) in a custom Media3 `AudioProcessor` inside the player's pipeline: identical on every device, no dependence on the OEM equalizer. On/off switch, ±12 dB sliders, reset, a saved **Custom** profile and **22 presets**. |
| Lyrics | Lookup order: manually chosen file → same basename `.lrc`/`.txt` in the song's folder → embedded tag lyrics (jaudiotagger) → normalised title + artist matches in accessible folders (a chooser appears if several match). Synced LRC auto-scrolls and highlights the current line; tap a line to seek. Supports multiple timestamps per line, `[offset:]` and enhanced word stamps. |
| Tag editing | Title, artist, album, album artist, genre, track and year. Written into the file (MP3, M4A/MP4, FLAC, Ogg Vorbis, WAV) after Android's write-consent prompt. Read-only files and other formats get an app-only override, labelled "edited in app" (removable). |
| Trimmer | Waveform timeline, range handles, ±0.1 s nudges, preview of the selection, export to a new AAC `.m4a` (original untouched). Saved to *Music/Ultra Clips* or *Ringtones*. **Set as ringtone** uses Android's *Modify system settings* flow, with "save clip only" as the fallback. |
| Music videos | Keyword search, "videos for the current song", and **Trending Music Videos** (YouTube Charts page, or the API's `mostPopular` music chart when a key is set). Opens in the YouTube app or browser. No downloading. |
| Design | Charcoal dark theme with 10 accent colours, plus light and system modes. **24 procedural skins** (gradients and textures such as dots, stripes, grid, waves, rings, stars, hexagons), live preview, your own photo through the system photo picker, and an adjustable readability overlay. All choices persist. |
| States and accessibility | First-run walkthrough. States for denied permission (with system-settings shortcut), empty library, missing lyrics, unsupported or missing files (skipped with a message), and failed edits. Content descriptions on every icon button. Text in `sp` so system font scaling works. |

## Permissions

| Permission | When it's requested | Why |
|---|---|---|
| `READ_MEDIA_AUDIO` (13+) / `READ_EXTERNAL_STORAGE` (≤12) | First run or Library screen | Read audio files to build the library |
| `POST_NOTIFICATIONS` (13+) | Last onboarding step or Settings | Show the playback notification |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK` | Install-time | Background playback |
| `WRITE_EXTERNAL_STORAGE` (≤ Android 9 only) | When saving tags or clips | Write files on old Android versions |
| `WRITE_SETTINGS` (special access) | Only when you tap **Set as ringtone** | Change the default ringtone |
| `INTERNET` | Never prompted | Only used for in-app video results when an API key is configured |

Android 10+ tag writes use MediaStore's per-file consent dialog rather than a broad permission. Added folders use persisted SAF grants.

## Supported formats

MP3, AAC/M4A, WAV (PCM), FLAC, Ogg Vorbis, Opus and AMR-NB/WB, using Media3's extractors and the phone's built-in decoders.

**Limitations**
- Formats the device can't decode (for example WMA, ALAC on some phones, or DRM-protected files) show a "can't play — skipping" message and the next track plays.
- Tag **writing** covers MP3, M4A/MP4, FLAC, Ogg Vorbis and WAV. Opus, AMR and raw AAC get app-only overrides.
- By default the library lists files Android marks as music. Turn on *Settings → Include recordings & voice notes* to also list other audio.
- The EQ processes 16-bit PCM (the player's default output). On 8 kHz AMR files the 3.6 kHz and 14 kHz bands sit above the audible range and are skipped.

## Device-specific limitations

- **Lyric auto-matching on Android 11+:** Android hides non-media files such as `.lrc` from apps. Add your music folder under *Settings → Music folders* so Ultra can find lyric files there. You can also pick a lyric file manually. On Android 10 and older, files next to songs are found automatically.
- **Shake with the screen off:** Many phones throttle or batch motion sensors while the screen is off, so shake detection may only work with the screen on.
- **Headset buttons:** The Media3 session handles play/pause, next and previous keys and the one-button headset hook (play/pause). Whether double or triple presses reach the app as next/previous depends on the Media3 version, the headset and the phone, and is on the pending device checklist.
- **SD cards:** Android 10+ indexes SD cards through MediaStore. On Android 8–9, or for folders MediaStore skips, add the SD card folder through *Add folder*.
- **Widgets:** Launchers decide the final widget size. Some launchers on older Android versions ignore `targetCellWidth/Height` and use the `minWidth/minHeight` values.
- **Ringtone:** Some manufacturer skins block third-party apps from changing the ringtone even after you grant *Modify system settings*. The clip is still saved under *Ringtones*, where you can pick it manually.

## Verification status

| Check | Status |
|---|---|
| EQ DSP: 22 presets present, flat is transparent, +12 dB at 60 Hz boosts, −12 dB at 3.6 kHz cuts, bands above Nyquist skipped, stereo channels independent | ✅ Unit-tested (`EqualizerTest`), passed on JVM |
| LRC parsing (sorting, multiple timestamps, offset, word stamps), line lookup, lyric name matching, path helpers | ✅ Unit-tested (`LyricsTest`), passed on JVM |
| Library grouping (albums, artists, genres, folder tree per volume) and search (case-insensitive, file names, playlists, folders, genres) | ✅ Exercised in a JVM harness with stubbed Android classes |
| Kotlin syntax of all 39 source files | ✅ Parsed by the Kotlin 2.0.21 compiler (full type-checking needs the Android SDK) |
| Gradle build / full compile / debug APK | ⏳ **Pending.** Run CI or Android Studio |
| Emulator testing | ⏳ Pending |
| Physical-device testing | ⏳ Pending |

**Checklist to run on a device or emulator (all pending):** library scan and refresh after adding or deleting a file · search during a rescan · Play Next order (shuffle on and off) · queue restore after force-stop · background playback with the screen locked · notification and lock-screen controls, including Stop · wired-headset and Bluetooth buttons · headphone unplug pauses · all three widget layouts and their buttons · lyric matching (same basename, embedded, title/artist chooser) · audible EQ change while toggling presets · theme, skin and photo persistence after restart · trim export and Set as ringtone · denied-permission flow · deleting a queued file (it should be skipped with a message).

## Project structure

```
app/src/main/java/com/ultramusicplayer/app/
├── UltraApp.kt / MainActivity.kt        App container, Coil artwork loader, entry activity
├── data/                                MediaStore + SAF scanner, Library model, search, Room DB,
│                                        DataStore settings, playlists/history, tag editor
├── lyrics/                              LRC parser, name matcher, lyric lookup
├── playback/                            PlaybackService (Media3 session), FiveBandEqProcessor,
│                                        EQ presets/DSP, queue persistence, artwork, shake detector,
│                                        PlayerConnection (UI ↔ MediaController)
├── widget/                              Three AppWidgetProviders + RemoteViews renderer
├── trim/                                Waveform, Transformer clip export, ringtone helpers
├── video/                               YouTube intents + optional Data API client
└── ui/                                  Compose theme, skins, navigation, screens
```
