package com.lastwave.app.ui.player.kinetic.scene

import com.lastwave.app.ui.player.kinetic.anim.Mods
import com.lastwave.app.ui.player.kinetic.fx.BeatDetector
import com.lastwave.app.ui.player.kinetic.fx.ConstellationEngine
import com.lastwave.app.ui.player.kinetic.fx.ParticleSystem
import com.lastwave.app.ui.player.kinetic.fx.StarField
import com.lastwave.app.ui.player.kinetic.model.LyricWordTimeline
import com.lastwave.app.ui.player.kinetic.model.WordStates
import com.lastwave.app.ui.player.kinetic.settings.KineticSettings
import com.lastwave.app.ui.player.kinetic.timing.TimingEngine

/**
 * Everything one track's kinetic scene shares: timeline, per-word state,
 * subsystems and per-frame scratch values. Created once per track; presets and
 * the renderer read/write it but never allocate per frame.
 */
class SceneContext(
    val timeline: LyricWordTimeline,
    val seed: Int,
    var settings: KineticSettings,
) {
    val n: Int = timeline.size
    val states = WordStates(n)
    val timing = TimingEngine(timeline, states)
    val layout = WordLayout(timeline)
    val palette = PaletteManager()
    val camera = CameraController()
    val stars = StarField()
    val particles = ParticleSystem()
    val constellation = ConstellationEngine()
    val beat = BeatDetector()
    val mods = Mods()

    // Viewport.
    var width = 0f
    var height = 0f
    var density = 1f

    // Per-word projected screen data (valid for words in [winLo, winHi)).
    val screenX = FloatArray(n)
    val screenY = FloatArray(n)
    val screenScale = FloatArray(n)

    /** 0..1 visibility of the word's constellation node (text or the dot that outlives it). */
    val nodeAlpha = FloatArray(n)

    /** Per-word chosen entrance behaviour ordinal (see Behaviour). */
    val behaviour = IntArray(n)

    // Frame state.
    var posMs = 0L
    var timeSec = 0f

    /** Scene-space y of the line currently centred on screen (smoothed). */
    var scrollY = 0f
    var scrollInit = false

    /** Word index window processed/drawn this frame. */
    var winLo = 0
    var winHi = 0

    /** Index of the hero word (ACTIVE/HOLD), or -1. */
    var heroWord = -1

    /** Line used for line-visibility falloff. */
    var focusLine = 0

    /** Continuous version of [focusLine] (moves toward the next line just before it starts). */
    var focusLineF = 0f

    /** Cached count of visible words, for the perf monitor. */
    var visibleWords = 0

    init {
        stars.reseed(seed)
    }
}
