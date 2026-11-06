# DEVELOPMENT_PROGRESS — Premium Kinetic Lyrics

Resume rule: a new session reads this file + ARCHITECTURE.md and continues at **NEXT EXACT TASK**. Never restart, never regenerate completed phases.

## Status: Phases 0–14 IMPLEMENTED. Phase 15 (real Android build + on-device tuning) is the user's step.

| Phase | Title | Status |
|---|---|---|
| 0 | Repository inspection | DONE |
| 1 | Architecture + progress system | DONE |
| 2 | Lyric timing model (`LyricWordTimeline`) | DONE — compiled + tests passed on user's machine |
| 3 | WordState + TimingEngine | DONE — compiled + tested in sandbox (see below) |
| 4 | AnimationEngine / AnimationPreset / PresetManager / behaviours | DONE — sandbox-tested |
| 5 | ARK_KINETIC | DONE — sandbox-tested |
| 6 | Long-word evolving animation | DONE — sandbox-tested (energy, expansion, orbit, outbound particles, release burst) |
| 7 | StarField + ParticleSystem | DONE — sandbox-tested |
| 8 | ConstellationEngine | DONE — sandbox-tested |
| 9 | 3D depth / camera (perspective, drift, parallax, motion reduction) | DONE — sandbox-tested |
| 10 | PaletteManager (album colours, AMOLED/CINEMATIC/DARK/CUSTOM) | DONE — wraps the existing ambient colours |
| 11 | Other presets (CONSTELLATION, CINEMATIC, FLOATING, 3D_DEPTH, STARFIELD, MINIMAL_AMOLED, BEAT_REACTIVE) | DONE — all 8 modes run the full-song test |
| 12 | Settings (own DataStore file, Simple/Advanced dialog) | DONE — Compose code NOT compiled in sandbox |
| 13 | Performance (pooled arrays, zero steady-state allocation, FPS target, perf overlay) | DONE — allocation verified in sandbox; GPU/real fps NOT measured |
| 14 | Integration into `ModernLyricsView.kt` | DONE — NOT compiled in sandbox |
| 15 | Final build / on-device test / tuning | **USER: run build gate below** |

## Build gate (user, in Termux)
```
cd LastWave-Native-5.0.0
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest --tests "*kinetic*"
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`. No Gradle/AGP/Kotlin/dependency changes were made.

## What was verified in the sandbox, and how
The sandbox has no Android SDK and no network, so Gradle could not run. Instead the Kotlin 2.0.21 compiler bundled with Gradle (`/opt/gradle-8.14.3/lib`) was used directly:
- All pure-Kotlin kinetic code (model, timing, anim, fx, scene, settings model, PerfMonitor) **compiled with zero errors**.
- `render/KineticRenderer.kt` was **type-checked against hand-written stubs** of the `android.graphics` classes it uses (stubs mirror only the signatures used; real behaviour untested).
- All 27 unit tests under `app/src/test/.../kinetic/` **ran and passed** on the JVM (JUnit 4.13.2, with a small stand-in for Google Truth because Truth is unavailable offline; the repo tests use the real Truth).
- The smoke test drives the whole engine at 120 Hz over a synthetic song in all 8 modes: no NaN/Inf, bounded particles, hero word brightest and nearest, no single-frame alpha/scale pops across word changes, held word expands/glows and leaves particles on release, motion reduction shrinks camera travel, deterministic per seed, seeks stay valid, **< 256 B allocated per frame in steady state**.
- A headless Java2D rasterisation of the engine state was inspected by eye (hero + glow, dim memory, faint upcoming lines, constellation lines, stars). After it, the word gap was widened (0.26 -> 0.34 em) so the enlarged hero word doesn't overlap its neighbour; tests re-ran green.

**NOT verified (needs your build/device):**
- `KineticLyricsView.kt`, `KineticSettingsStore.kt`, `KineticSettingsDialog.kt` and the edits to `ModernLyricsView.kt` (Compose/Android code, never compiled).
- The real Android renderer on a GPU: actual FPS on the Realme GT 6T, BlurMaskFilter cost, glow-sprite look.
- Visual tuning: all look/feel constants are best estimates.

## Files
Added (under `app/src/main/java/com/lastwave/app/ui/player/kinetic/`):
- `model/LyricData.kt`, `model/WordState.kt`
- `timing/TimingEngine.kt`
- `anim/AnimationPreset.kt`, `anim/AnimationEngine.kt`, `anim/BasePreset.kt`, `anim/Behaviours.kt`, `anim/PresetManager.kt`
- `fx/StarField.kt`, `fx/ParticleSystem.kt`, `fx/ConstellationEngine.kt`, `fx/BeatDetector.kt`
- `scene/SceneContext.kt`, `scene/WordLayout.kt`, `scene/CameraController.kt`, `scene/PaletteManager.kt`, `scene/Util.kt`
- `render/KineticRenderer.kt`, `render/KineticLyricsView.kt`, `render/PerfMonitor.kt`
- `settings/KineticSettings.kt`, `settings/KineticSettingsStore.kt`, `settings/KineticSettingsDialog.kt`
- Tests (`app/src/test/java/com/lastwave/app/ui/player/kinetic/`): `LyricWordTimelineTest.kt`, `TimingEngineTest.kt`, `KineticEngineSmokeTest.kt`
- Docs: `ARCHITECTURE.md`, `DEVELOPMENT_PROGRESS.md`

Modified (only one existing source file): `ui/player/ModernLyricsView.kt`, 4 additive hunks:
1. `SyncedLyricsProgressHost(...)` call gets 5 `kinetic*` args (colours, track key, font scale).
2. `SyncedLyricsProgressHost` signature: 5 new defaulted params.
3. After `currentPosition` is defined: loads kinetic settings; if enabled renders `KineticLyricsView` with the SAME `currentPosition` lambda, else falls through to the unchanged classic list.
4. `LyricsOptionsMenuDialog`: a "Kinetic Lyrics" row that opens `KineticSettingsDialog`.

Not touched: `playback/`, `data/lyrics/`, `di/`, `PlayerHost.kt`, `SettingsPreferences.kt`, Gradle files, LICENSE/attribution.

## Design decisions / limitations
- **Clock:** none of its own. `AnimationEngine.frame(posMs, nowNanos)` receives LastWave's existing frame-clock position (`SyncedLyricsProgressHost.currentPosition`).
- **Timing precedence:** syllable -> word -> line-estimated (flagged `isEstimated`). Enhanced-LRC is already normalised into syllables by LastWave's parsers.
- **Beat:** LastWave exposes no live audio analysis to the UI (`OutputMeterState` has no consumer). `BeatDetector` defaults to *vocal-rhythm* kicks on word onsets (NOT an audio beat tracker) and can take an audio level via `AnimationEngine.frame(..., audioLevel)`. Wiring `OutputMeterState.snapshots` (rmsLinear) into that parameter is a possible follow-up. Switchable in settings.
- **Settings storage:** separate DataStore file `kinetic_lyrics` (one encoded string); LastWave's own settings untouched. Process-wide `KineticSettingsStore`; sliders preview live, saved after 300 ms.
- **Kinetic mode replaces the classic list while enabled:** tap-a-line-to-seek, translation/romanization sub-lines and list scrolling are not available in kinetic mode. Turn it off in Lyrics Options -> Kinetic Lyrics to get the unchanged classic view back.
- Kinetic mode draws its own opaque background (AMOLED near-black by default) over the lyrics area.
- Encoding quirks (mojibake) in `ModernLyricsView.kt` comments were preserved byte-for-byte.
- `reference zip/extracted/` is an old copy of LastWave; ignored.
- Provenance note: files in `anim/`, `fx/`, `scene/`, `render/KineticRenderer.kt`, `render/PerfMonitor.kt` and `settings/KineticSettings.kt` appeared on disk from a parallel write during the session (and Phase 3 files were rewritten by it). They were reviewed, compiled and tested here before being built upon.

## NEXT EXACT TASK
1. Run the build gate. Paste any compiler errors; most likely in `KineticLyricsView.kt`, `KineticSettingsDialog.kt` or the `ModernLyricsView.kt` hunks.
2. On device (word-synced song -> Lyrics -> options -> Kinetic Lyrics): check FPS with *Performance overlay* (Advanced), then tune if needed:
   - `PresetProfile` values per mode in `anim/PresetManager.kt`;
   - keyframes in `anim/BasePreset.kt`;
   - blur radii / glow count in `render/KineticRenderer.kt` (lower the 12-word glow limit or blur levels if the GPU struggles);
   - `TimingConfig` windows in `timing/TimingEngine.kt`.
3. Optional follow-ups: wire `OutputMeterState` to the beat layer; tap-to-seek in kinetic mode; translation sub-lines.
