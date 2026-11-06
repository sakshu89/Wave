package com.lastwave.app.ui.player.kinetic.anim

import com.lastwave.app.ui.player.kinetic.model.HoldClass
import com.lastwave.app.ui.player.kinetic.scene.SceneContext
import com.lastwave.app.ui.player.kinetic.settings.KineticMode

/** Minimal drawing surface presets can use for extra layers (implemented by the renderer). */
interface PresetCanvas {
    /** Soft glowing disc centred at (x, y) with radius r px. */
    fun disc(x: Float, y: Float, r: Float, argb: Int, alpha: Float)

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, widthPx: Float, argb: Int, alpha: Float)
}

/**
 * Look-and-feel knobs of a preset. Presets share one timing engine and one
 * state model; they differ in these numbers, in the behaviours they may pick,
 * and in optional overrides. Multipliers are 1.0 = the ARK_KINETIC reference.
 */
class PresetProfile(
    val mode: KineticMode,
    val allowed: Array<Behaviour>,
    val futureAlpha: Float = 0.12f,
    val memoryAlpha: Float = 0.30f,
    val heroScale: Float = 1.18f,
    val glow: Float = 1f,
    val depth: Float = 1f,
    val camera: Float = 1f,
    val stars: Float = 1f,
    val starBright: Float = 1f,
    val particles: Float = 1f,
    val constellation: Float = 1f,
    val atmosphere: Float = 1f,
    val beat: Float = 0.6f,
    val wander: Float = 1f,
    val lineJitter: Float = 1f,
    val blur: Float = 1f,
    val orbit: Float = 1f,
    val memoryStars: Float = 1f,
    /** Multiplies the user's transition-speed setting. */
    val speed: Float = 1f,
)

/**
 * A pluggable animation style. New styles are added by creating a preset and
 * registering it in [PresetManager]; no playback code is involved.
 *
 * Presets write transform fields of `ctx.states` for the words in
 * `[ctx.winLo, ctx.winHi)` and may drive the particle/star/constellation
 * subsystems. Timing fields are owned by the timing engine.
 */
interface AnimationPreset {
    val profile: PresetProfile

    /** Called when the preset is (re)attached to a scene or a track starts. */
    fun reset(ctx: SceneContext)

    /** Per frame, after timing has been updated and before projection. */
    fun update(ctx: SceneContext, dt: Float)

    /** Extra layers drawn behind the words (halos etc.). */
    fun render(ctx: SceneContext, canvas: PresetCanvas)

    fun onWordEnter(ctx: SceneContext, wordIndex: Int)
    fun onWordActive(ctx: SceneContext, wordIndex: Int)
    fun onWordExit(ctx: SceneContext, wordIndex: Int)
    fun onLongHold(ctx: SceneContext, wordIndex: Int, tier: HoldClass)
}
