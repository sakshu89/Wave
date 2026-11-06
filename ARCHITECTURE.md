# ARCHITECTURE — Premium Kinetic Lyrics (LastWave-Native 5.0.0 mod)

Personal-use modification. Original LICENSE / attribution files are untouched.
Only the lyric **rendering/animation layer** is being replaced. Playback, providers,
parsing, caching, artwork and the player UI are preserved.

## 1. Existing LastWave facts (verified in source, Phase 0)

Source root: `app/src/main/java/com/lastwave/app/`
Toolchain: Gradle 9.3.1, AGP 9.1.1, Kotlin 2.4.10, Compose BOM 2026.06.01, compileSdk 37, minSdk 29, JDK 17. Do not upgrade.

| Concern | Where | Notes |
|---|---|---|
| Playback | `playback/MusicPlayer.kt` (9k lines), `MusicPlaybackService.kt` | Media3/ExoPlayer + MediaSession. Untouched. |
| Progress source | `MusicPlayer.progressState: StateFlow<PlaybackProgressState>` (`positionMs`, `durationMs`), ~60 ms samples | Passed to the lyrics panel as `progressState`. |
| Frame clock (already exists) | `SyncedLyricsProgressHost` in `ui/player/ModernLyricsView.kt` (~L1387) | `withFrameNanos` clock steered toward the ExoPlayer playhead; snaps on seek. Exposes `currentPosition: () -> Long` (includes user lyrics offset). **This is THE clock we reuse. No second clock.** |
| Lyric models | `data/lyrics/LyricsRepository.kt` L41–88: `LyricSyllable(timeMs, durationMs, text, isBackground, appendToPrevious)`, `LyricLine(timeMs, durationMs, text, syllables, transliteration…)`, `LyricsResult` | Syllable/word timing already present from Apple Music, BetterLyrics, QQ, Kugou, Bini, LyricsPlus, Spotify, etc. |
| Providers / parsing | `data/lyrics/*LyricsApi.kt`, `TtmlParser.kt`, `LrclibLyricsApi.kt` | Untouched. |
| UI state | `ui/player/LyricsUiState.kt` (`Success(lines, isSynced, isWordSynced, …)`) | Input to our engine. |
| Current renderer | `ui/player/ModernLyricsView.kt` (2307 lines): `ModernLyricsPanel` → `SyncedLyricsProgressHost` → `ModernSyncedLyricsList` (LazyColumn) → `ModernWordByWordLine` | Karaoke list. Stays as the fallback / "Classic" mode. |
| Panel call site | `ui/player/PlayerHost.kt` ~L2969 (`ModernLyricsPanel(...)`), gets `primaryColor=ambientColor`, `secondaryColor=ambientCompanion`, `tertiaryColor=ambientDeep` | Already album-art derived (PlayerHost ~L2064/L2404). |
| Colour extraction | `data/repository/NowPlayingPaletteExtractor.kt` (androidx Palette), ambient colours in `PlayerHost.kt` | `PaletteManager` will wrap the 3 ambient colours; no new extraction needed. |
| Background | `ui/player/FluidArtworkBackground.kt` | Kept; kinetic mode draws its own near-black space over/instead of it. |
| Settings | `data/local/SettingsPreferences.kt` (DataStore, `Keys.*`, `readSafely`), `ui/settings/SettingsViewModel.kt`, `ui/settings/SettingsScreen.kt` | New keys follow the `lw_lyrics_*` pattern. |
| Beat/BPM | `playback/automix/**`, `playback/analysis/OutputTap.kt` / `OutputMeterState.kt` (post-DSP peak/RMS windows) | No live beat tracker for the lyrics UI. Plan: optional lightweight onset detector fed from `OutputMeterSnapshot` (switchable). |
| Existing lyric animation | `syllableLift`, `drawWithSyllableLift`, per-line `animateFloatAsState` in `ModernLyricsView.kt` | Not reused by kinetic engine. |

## 2. Implemented architecture (as built)

Package `com.lastwave.app.ui.player.kinetic` (all new; pure Kotlin except `render/*` and `settings/KineticSettingsStore|Dialog`):

```
model/    LyricData.kt     LyricWord, HoldClass, LyricWordTimeline (syllable->word grouping, estimation, O(1) active lookup)
          WordState.kt     WordPhase (FUTURE..MEMORY) + struct-of-arrays WordStates
timing/   TimingEngine.kt  position -> phase/progress/holdEnergy per word; events (enter/active/exit/longHold); seek-safe
anim/     AnimationPreset  interface (update/render/reset/onWordEnter/onWordActive/onWordExit/onLongHold) + PresetProfile
          AnimationEngine  per-frame driver: timing -> focus/scroll -> window -> camera -> preset -> projection -> fx
          BasePreset       shared lifecycle keyframes, long-hold evolution, release burst
          Behaviours       18 deterministic entrance behaviours selected per word (duration/importance/position/seed)
          PresetManager    ARK_KINETIC, CONSTELLATION, CINEMATIC, FLOATING, 3D_DEPTH, STARFIELD, MINIMAL_AMOLED, BEAT_REACTIVE
fx/       StarField, ParticleSystem (pooled; burst/dust/orbit/release), ConstellationEngine (bounded-degree edges, fading), BeatDetector
scene/    SceneContext, WordLayout (balanced rows, line stacking, RTL-safe), CameraController (perspective, drift, parallax),
          PaletteManager (primary/secondary/accent/background/glow from album colours), Util (RNG, easing)
render/   KineticRenderer (android.graphics; pooled paints, glow sprite, blur-filter table), KineticLyricsView (Compose edge,
          withFrameNanos loop, draw-only invalidation, FPS throttle), PerfMonitor
settings/ KineticSettings (all options, encode/decode), KineticSettingsStore (own DataStore), KineticSettingsDialog (Simple/Advanced)
```

Rules kept: no second clock; no allocation per frame in steady state (tested); deterministic per-word randomness from `LyricWord.seed`;
timing priority syllable > word > line-estimated (flagged); hold tiers short <400 ms, normal 400-1000, long 1-2 s, very long >2 s.

## 3. Integration points (as built)

Only `ui/player/ModernLyricsView.kt` was modified (4 additive hunks): host params + call-site args, a branch in `SyncedLyricsProgressHost`
that renders `KineticLyricsView` with the same `currentPosition` lambda when enabled (otherwise the classic list, unchanged), and a
"Kinetic Lyrics" row in `LyricsOptionsMenuDialog`. Album colours come from the panel's existing `primaryColor/secondaryColor/tertiaryColor`.

## 4. Build / environment

- Target: Termux, `./gradlew assembleDebug`, APK at `app/build/outputs/apk/debug/app-debug.apk` (project's `builds/README.md` mentions `LastWave-v5.0.0-debug.apk` as a published copy).
- No dependency, Gradle, AGP or Kotlin changes planned.
- The authoring sandbox has **no Android SDK and no network**, so Gradle could not run there. Pure-Kotlin code was compiled/tested with Gradle's bundled Kotlin compiler; the Android/Compose files were NOT compiled. See DEVELOPMENT_PROGRESS.md "Build gate" and "What was verified".
