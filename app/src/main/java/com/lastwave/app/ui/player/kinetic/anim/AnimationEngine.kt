package com.lastwave.app.ui.player.kinetic.anim

import com.lastwave.app.ui.player.kinetic.model.HoldClass
import com.lastwave.app.ui.player.kinetic.model.LyricWordTimeline
import com.lastwave.app.ui.player.kinetic.model.WordPhase
import com.lastwave.app.ui.player.kinetic.render.PerfMonitor
import com.lastwave.app.ui.player.kinetic.scene.Ease
import com.lastwave.app.ui.player.kinetic.scene.SceneContext
import com.lastwave.app.ui.player.kinetic.scene.TextMeasurer
import com.lastwave.app.ui.player.kinetic.settings.KineticSettings
import com.lastwave.app.ui.player.kinetic.timing.TimingConfig
import com.lastwave.app.ui.player.kinetic.timing.WordEventListener
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Per-track, frame-synchronised driver. It owns NO clock: [frame] receives the
 * playback position (from LastWave's existing frame clock) and the frame
 * timestamp. Order each frame:
 *   timing -> focus/scroll -> window -> camera -> preset -> projection -> stars/particles/constellation.
 */
class AnimationEngine(
    timeline: LyricWordTimeline,
    seed: Int,
    settings: KineticSettings = KineticSettings.Default,
) : WordEventListener {

    val ctx = SceneContext(timeline, seed, settings)
    var preset: AnimationPreset = PresetManager.create(settings.mode)
        private set
    val perf = PerfMonitor()

    private var lastNanos = 0L
    private var lastPos = Long.MIN_VALUE
    private var layoutW = -1f
    private var layoutH = -1f
    private var layoutSize = -1f
    private var layoutSpacing = -1f
    private var layoutReady = false
    private var appliedSpeed = -1f
    private val paletteSource = IntArray(3)
    private var paletteSet = false

    init {
        applyTimingConfig()
        ctx.beat.enabled = settings.beatEnabled
        preset.reset(ctx)
    }

    /** True once a viewport + measurer have produced a layout. */
    val isReady: Boolean get() = layoutReady

    fun applySettings(s: KineticSettings) {
        val old = ctx.settings
        if (s == old) return
        ctx.settings = s
        if (s.mode != old.mode) {
            preset = PresetManager.create(s.mode)
            preset.reset(ctx)
        }
        ctx.beat.enabled = s.beatEnabled
        ctx.particles.budget = (ctx.particles.capacity * s.particleDensity.coerceIn(0f, 2f) / 2f * 1.4f)
            .toInt().coerceIn(0, ctx.particles.capacity)
        applyTimingConfig()
        applyPaletteTarget()
    }

    fun setPalette(c1: Int, c2: Int, c3: Int) {
        paletteSet = true
        paletteSource[0] = c1; paletteSource[1] = c2; paletteSource[2] = c3
        applyPaletteTarget()
    }

    private fun applyPaletteTarget() {
        if (!paletteSet) return
        val s = ctx.settings
        ctx.palette.setTarget(
            paletteSource[0], paletteSource[1], paletteSource[2],
            s.background, s.customBackground, s.backgroundIntensity,
        )
    }

    private fun applyTimingConfig() {
        val ts = (ctx.settings.transitionSpeed * preset.profile.speed).coerceIn(0.4f, 2.5f)
        if (ts == appliedSpeed) return
        appliedSpeed = ts
        ctx.timing.config = TimingConfig(
            approachMs = (1200f / ts).toLong(),
            enterMs = (180f / ts).toLong(),
            exitMs = (450f / ts).toLong(),
        )
    }

    /**
     * Provide the viewport and a text measurer; recomputes the layout only when something changed.
     * The measurer must already be configured to [baseSizePx].
     */
    fun baseSizePx(density: Float, fontScale: Float): Float = 34f * density * fontScale * ctx.settings.wordSize

    fun ensureLayout(w: Float, h: Float, density: Float, fontScale: Float, measurer: TextMeasurer) {
        val s = ctx.settings
        val size = baseSizePx(density, fontScale)
        if (w <= 0f || h <= 0f) return
        if (layoutReady && w == layoutW && h == layoutH && size == layoutSize && s.wordSpacing == layoutSpacing) return
        layoutW = w; layoutH = h; layoutSize = size; layoutSpacing = s.wordSpacing
        ctx.width = w; ctx.height = h; ctx.density = density
        ctx.layout.layout(w, size, s.wordSpacing, measurer)
        ctx.camera.cx = w * 0.5f
        ctx.camera.cy = h * 0.46f
        ctx.camera.focal = 900f * density
        ctx.scrollInit = false
        layoutReady = true
    }

    // ---- timing callbacks -> preset ------------------------------------

    override fun onWordEnter(wordIndex: Int) = preset.onWordEnter(ctx, wordIndex)
    override fun onWordActive(wordIndex: Int) = preset.onWordActive(ctx, wordIndex)
    override fun onWordExit(wordIndex: Int) = preset.onWordExit(ctx, wordIndex)
    override fun onLongHold(wordIndex: Int, tier: HoldClass) = preset.onLongHold(ctx, wordIndex, tier)

    /**
     * Advance one frame. [posMs] is the shared playback position (lyric offset already applied).
     * [audioLevel] (0..1) is optional; when given it feeds the audio-onset beat layer.
     */
    fun frame(posMs: Long, nowNanos: Long, audioLevel: Float = -1f) {
        perf.onFrame(nowNanos)
        val c = ctx
        val dt = if (lastNanos == 0L) 1f / 60f else ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastNanos = nowNanos
        if (!layoutReady || c.n == 0) return

        val s = c.settings
        val p = preset.profile
        val words = c.timeline.words
        val st = c.states
        val seeked = lastPos == Long.MIN_VALUE || posMs < lastPos - 100L || posMs - lastPos > 1500L
        lastPos = posMs
        c.posMs = posMs
        c.timeSec += dt

        if (c.palette.step(dt)) { /* colours changed; renderer reads palette directly */ }
        if (audioLevel >= 0f) c.beat.feedLevel(audioLevel, dt)
        c.beat.step(dt)

        if (seeked) { c.particles.reset(); c.constellation.reset() }
        c.timing.update(posMs, this)

        // Hero = word currently being sung.
        val aw = c.timing.activeWord
        c.heroWord = if (aw >= 0 && (st.phase[aw] == WordPhase.ACTIVE || st.phase[aw] == WordPhase.HOLD)) aw else -1

        // Focus line, with the scroll leading into the next line shortly before it starts.
        val nw = c.timing.nextWord
        val line = when {
            aw >= 0 -> words[aw].lineIndex
            nw >= 0 -> words[nw].lineIndex
            else -> 0
        }
        var focusF = line.toFloat()
        var targetY = c.layout.lineCenterY[line]
        if (nw >= 0 && aw >= 0) {
            val nl = words[nw].lineIndex
            if (nl != line) {
                val lead = Ease.smooth((700f - (words[nw].startMs - posMs)) / 700f)
                focusF = line + (nl - line) * lead
                targetY = Ease.lerp(c.layout.lineCenterY[line], c.layout.lineCenterY[nl], lead)
            }
        }
        c.focusLine = line
        c.focusLineF = focusF
        if (!c.scrollInit || seeked) {
            c.scrollY = targetY; c.scrollInit = true
        } else {
            val k = 1f - exp(-dt * 6f * (s.transitionSpeed * p.speed))
            c.scrollY += (targetY - c.scrollY) * k
        }

        // Word window around the focus.
        var lo = Int.MAX_VALUE
        var hi = -1
        val l0 = max(0, floor(focusF).toInt() - 3)
        val l1 = min(c.layout.lineCount - 1, ceil(focusF).toInt() + 3)
        for (l in l0..l1) {
            val a = c.layout.lineFirstWord[l]
            val b = c.layout.lineLastWord[l]
            if (a >= 0 && a < lo) lo = a
            if (b > hi) hi = b
        }
        if (hi < 0) { c.winLo = 0; c.winHi = 0 } else { c.winLo = lo; c.winHi = hi + 1 }

        // Camera.
        val heroEnergy = if (c.heroWord >= 0) st.holdEnergy[c.heroWord] * s.longWordEffect else 0f
        c.camera.update(dt, s.cameraMovement * s.motionScale * p.camera, min(heroEnergy, 1f), c.beat.pulse * s.beatReaction)

        // Preset writes the word transforms.
        preset.update(c, dt)

        // Perspective projection.
        for (i in c.winLo until c.winHi) {
            c.camera.project(st.x[i], st.y[i], st.z[i])
            c.screenX[i] = c.camera.projX
            c.screenY[i] = c.camera.projY
            c.screenScale[i] = c.camera.projScale
        }

        // Background systems.
        val starDens = s.starDensity * p.stars
        c.stars.update(
            dt, c.width, c.height, starDens, s.starSize * c.density, p.starBright * (0.9f + 0.4f * s.backgroundIntensity.coerceAtMost(1f)),
            s.motionScale, c.camera.offX, c.camera.offY, c.beat.pulse * s.beatReaction, 1f,
        )
        c.particles.update(dt, s.particleSpeed * (0.6f + 0.4f * s.motionScale), c.screenX, c.screenY, c.screenScale, st.phase)
        c.constellation.update(
            dt, c.winLo, c.winHi, c.screenX, c.screenY, st.z, c.nodeAlpha, st.holdEnergy, c.heroWord,
            min(c.width, c.height) * 0.42f, s.lineIntensity * p.constellation, c.beat.pulse * s.beatReaction,
        )

        perf.activeWords = c.visibleWords
        perf.particles = c.particles.aliveCount
        perf.stars = c.stars.count
    }
}
