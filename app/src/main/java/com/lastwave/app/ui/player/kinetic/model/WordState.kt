package com.lastwave.app.ui.player.kinetic.model

/**
 * Visual lifecycle of a lyric word. Declaration order is meaningful: the
 * timing engine detects transitions by comparing ordinals.
 */
enum class WordPhase {
    FUTURE,
    APPROACHING,
    ENTER,
    ACTIVE,
    HOLD,
    EXIT,
    MEMORY,
}

/**
 * Mutable per-word render state for a whole track, as parallel primitive
 * arrays (struct-of-arrays) so the frame loop never allocates.
 *
 * Ownership of fields:
 *  - Written by `TimingEngine` (Phase 3): [phase], [phaseProgress],
 *    [holdEnergy], [releaseEnergy].
 *  - Written by the animation engine / presets (Phase 4+): everything else.
 *
 * Index `i` matches `LyricWordTimeline.words[i]`.
 */
class WordStates(val size: Int) {
    val phase: Array<WordPhase> = Array(size) { WordPhase.FUTURE }

    /** 0..1 progress inside the current [phase]. In MEMORY, 1 = fully faded. */
    val phaseProgress = FloatArray(size)

    /** 0..1 energy a held word has accumulated; grows continuously while sung. */
    val holdEnergy = FloatArray(size)

    /** Energy the word carried when it was released; 0 outside EXIT/MEMORY. */
    val releaseEnergy = FloatArray(size)

    // 3D position / transform (world units, preset-defined).
    val x = FloatArray(size)
    val y = FloatArray(size)
    val z = FloatArray(size)
    val scale = FloatArray(size) { 1f }
    val rotX = FloatArray(size)
    val rotY = FloatArray(size)
    val rotZ = FloatArray(size)

    // Appearance.
    val alpha = FloatArray(size)
    val blur = FloatArray(size)
    val glow = FloatArray(size)

    /** Horizontal stretch multiplier (1 = none). */
    val stretch = FloatArray(size) { 1f }

    /** 0..1 how "hero-lit" the word is; drives text colour from dim to bright. */
    val focus = FloatArray(size)

    // Velocity, for drifting / spring behaviours.
    val vx = FloatArray(size)
    val vy = FloatArray(size)
    val vz = FloatArray(size)

    /** Index into the palette (see PaletteManager, Phase 10). */
    val colorIdx = IntArray(size)

    /** Index of the selected animation behaviour (see AnimationEngine, Phase 4). */
    val animIdx = IntArray(size)

    /** Returns every word to its initial, invisible FUTURE state. */
    fun reset() {
        phase.fill(WordPhase.FUTURE)
        phaseProgress.fill(0f)
        holdEnergy.fill(0f)
        releaseEnergy.fill(0f)
        x.fill(0f); y.fill(0f); z.fill(0f)
        scale.fill(1f)
        rotX.fill(0f); rotY.fill(0f); rotZ.fill(0f)
        alpha.fill(0f)
        blur.fill(0f)
        glow.fill(0f)
        stretch.fill(1f)
        focus.fill(0f)
        vx.fill(0f); vy.fill(0f); vz.fill(0f)
        colorIdx.fill(0)
        animIdx.fill(0)
    }

    /** Resets only the timing-owned fields; preset-owned layout is kept. */
    fun resetTiming() {
        phase.fill(WordPhase.FUTURE)
        phaseProgress.fill(0f)
        holdEnergy.fill(0f)
        releaseEnergy.fill(0f)
    }
}
