package com.lastwave.app.ui.player.kinetic

import com.google.common.truth.Truth.assertThat
import com.lastwave.app.data.lyrics.LyricLine
import com.lastwave.app.data.lyrics.LyricSyllable
import com.lastwave.app.ui.player.kinetic.anim.AnimationEngine
import com.lastwave.app.ui.player.kinetic.model.LyricWordTimeline
import com.lastwave.app.ui.player.kinetic.model.WordPhase
import com.lastwave.app.ui.player.kinetic.scene.TextMeasurer
import com.lastwave.app.ui.player.kinetic.settings.KineticMode
import com.lastwave.app.ui.player.kinetic.settings.KineticSettings
import org.junit.Test
import java.lang.management.ManagementFactory

/**
 * End-to-end checks that drive the whole engine (timing -> preset -> projection ->
 * stars/particles/constellation) frame by frame at 120 Hz over a synthetic song.
 */
class KineticEngineSmokeTest {

    private fun syl(t: Long, d: Long, s: String) = LyricSyllable(timeMs = t, durationMs = d, text = "$s ")

    /** Word-synced lines, a 3.2 s held word, then a line-only (estimated) line. */
    private fun song(): List<LyricLine> {
        val l0 = LyricLine(
            timeMs = 1000, text = "hold me in the light",
            syllables = listOf(syl(1000, 300, "hold"), syl(1400, 300, "me"), syl(1800, 250, "in"), syl(2100, 250, "the"), syl(2400, 3200, "light")),
        )
        val l1 = LyricLine(
            timeMs = 6500, text = "we are stars tonight",
            syllables = listOf(syl(6500, 400, "we"), syl(7000, 400, "are"), syl(7500, 500, "stars"), syl(8100, 900, "tonight")),
        )
        val l2 = LyricLine(timeMs = 10_000, durationMs = 3000, text = "and the morning comes again")
        return listOf(l0, l1, l2)
    }

    private val measurer = TextMeasurer { it.length * 18f }
    private val frameNs = 8_333_333L // 120 Hz

    private fun engine(mode: KineticMode, settings: KineticSettings = KineticSettings.Default): AnimationEngine {
        val tl = LyricWordTimeline.build(song(), "track-A")
        val e = AnimationEngine(tl, 1234, settings.copy(mode = mode))
        e.setPalette(0xFF7B4DFF.toInt(), 0xFF2EC4FF.toInt(), 0xFFFF5CA8.toInt())
        e.ensureLayout(1080f, 2200f, 2.75f, 1f, measurer)
        return e
    }

    private fun checkFinite(e: AnimationEngine) {
        val c = e.ctx
        val st = c.states
        for (i in c.winLo until c.winHi) {
            val v = floatArrayOf(
                st.x[i], st.y[i], st.z[i], st.scale[i], st.alpha[i], st.blur[i], st.glow[i],
                st.rotX[i], st.rotY[i], st.rotZ[i], c.screenX[i], c.screenY[i], c.screenScale[i],
            )
            for (f in v) assertThat(f.isNaN() || f.isInfinite()).isFalse()
            assertThat(st.alpha[i]).isAtLeast(-0.001f)
            assertThat(st.alpha[i]).isAtMost(1.001f)
        }
        assertThat(c.particles.aliveCount).isAtMost(c.particles.capacity)
    }

    @Test fun everyModeRunsTheWholeSongWithFiniteValues() {
        for (mode in KineticMode.entries) {
            val e = engine(mode)
            assertThat(e.isReady).isTrue()
            var now = 1_000_000_000L
            var pos = 0L
            while (pos < 14_500) {
                e.frame(pos, now)
                checkFinite(e)
                now += frameNs
                pos += 8
            }
        }
    }

    @Test fun layoutIsReadyAndHeroWordIsBrightestAndInFront() {
        val e = engine(KineticMode.ARK_KINETIC)
        var now = 1_000_000_000L
        var pos = 0L
        while (pos < 1650) { e.frame(pos, now); now += frameNs; pos += 8 }
        val c = e.ctx
        val hero = c.heroWord
        assertThat(hero).isAtLeast(0)
        val st = c.states
        for (i in c.winLo until c.winHi) {
            if (i == hero) continue
            assertThat(st.alpha[hero]).isAtLeast(st.alpha[i] - 0.001f)
            assertThat(st.z[hero]).isAtMost(st.z[i] + 0.001f)
        }
    }

    @Test fun wordChangesAreContinuousNoPopInNoPopOut() {
        val e = engine(KineticMode.ARK_KINETIC)
        val st = e.ctx.states
        val n = e.ctx.n
        val prev = FloatArray(n)
        val prevScale = FloatArray(n)
        var now = 1_000_000_000L
        var pos = 0L
        var worst = 0f
        var worstScale = 0f
        while (pos < 14_500) {
            e.frame(pos, now)
            if (pos > 100) {
                for (i in e.ctx.winLo until e.ctx.winHi) {
                    worst = maxOf(worst, kotlin.math.abs(st.alpha[i] - prev[i]))
                    worstScale = maxOf(worstScale, kotlin.math.abs(st.scale[i] - prevScale[i]))
                }
            }
            for (i in 0 until n) { prev[i] = st.alpha[i]; prevScale[i] = st.scale[i] }
            now += frameNs
            pos += 8
        }
        // 8 ms of song per frame: any single-frame jump above this would read as a pop.
        assertThat(worst).isLessThan(0.12f)
        assertThat(worstScale).isLessThan(0.12f)
    }

    @Test fun heldWordEvolvesAndReleasesEnergyIntoParticles() {
        val e = engine(KineticMode.ARK_KINETIC)
        val c = e.ctx
        val st = c.states
        // The held word is "light" (index 4: starts 2400, lasts 3200).
        var now = 1_000_000_000L
        var pos = 0L
        var scaleEarly = 0f
        var scaleLate = 0f
        var glowEarly = 0f
        var glowLate = 0f
        var maxEnergy = 0f
        var peakParticlesWhileHeld = 0
        var particlesAfterRelease = 0
        while (pos < 6600) {
            e.frame(pos, now)
            if (pos in 2700..2760) { scaleEarly = st.scale[4]; glowEarly = st.glow[4] }
            if (pos in 5200..5260) { scaleLate = st.scale[4]; glowLate = st.glow[4] }
            if (pos in 2400..5600) {
                maxEnergy = maxOf(maxEnergy, st.holdEnergy[4])
                peakParticlesWhileHeld = maxOf(peakParticlesWhileHeld, c.particles.aliveCount)
            }
            if (pos in 5660..5800) particlesAfterRelease = maxOf(particlesAfterRelease, c.particles.aliveCount)
            now += frameNs
            pos += 8
        }
        assertThat(maxEnergy).isGreaterThan(0.9f)
        assertThat(scaleLate).isGreaterThan(scaleEarly + 0.02f) // word expands over the hold
        assertThat(glowLate).isGreaterThan(glowEarly)           // glow gains energy
        assertThat(peakParticlesWhileHeld).isGreaterThan(0)      // orbiting / outbound stars exist
        assertThat(particlesAfterRelease).isGreaterThan(0)       // release leaves a particle transition
        assertThat(st.phase[4] == WordPhase.EXIT || st.phase[4] == WordPhase.MEMORY).isTrue()
    }

    @Test fun shortWordsDoNotDevelopHoldVisuals() {
        val e = engine(KineticMode.ARK_KINETIC)
        val st = e.ctx.states
        var now = 1_000_000_000L
        var pos = 0L
        var maxShort = 0f
        while (pos < 2000) {
            e.frame(pos, now)
            maxShort = maxOf(maxShort, st.holdEnergy[0]) // "hold", 300 ms
            now += frameNs; pos += 8
        }
        assertThat(maxShort).isLessThan(0.05f)
    }

    @Test fun seekingBackAndForthStaysValid() {
        val e = engine(KineticMode.ARK_KINETIC)
        var now = 1_000_000_000L
        val jumps = longArrayOf(500, 9000, 2500, 13_000, 100, 7000, 4000)
        for (p in jumps) {
            var pos = p
            repeat(40) { e.frame(pos, now); checkFinite(e); now += frameNs; pos += 8 }
        }
    }

    @Test fun motionReductionShrinksCameraTravel() {
        fun travel(reduced: Boolean): Float {
            val e = engine(KineticMode.DEPTH_3D, KineticSettings.Default.copy(motionReduction = reduced))
            var now = 1_000_000_000L
            var pos = 0L
            var lo = Float.MAX_VALUE
            var hi = -Float.MAX_VALUE
            while (pos < 8000) {
                e.frame(pos, now)
                lo = minOf(lo, e.ctx.camera.offX); hi = maxOf(hi, e.ctx.camera.offX)
                now += frameNs; pos += 8
            }
            return hi - lo
        }
        assertThat(travel(true)).isLessThan(travel(false) * 0.5f + 0.0001f)
    }

    @Test fun deterministicForTheSameTrackAndSeed() {
        fun run(): FloatArray {
            val e = engine(KineticMode.ARK_KINETIC)
            var now = 1_000_000_000L
            var pos = 0L
            while (pos < 5000) { e.frame(pos, now); now += frameNs; pos += 8 }
            return e.ctx.states.x.copyOf()
        }
        assertThat(run().toList()).isEqualTo(run().toList())
    }

    @Test fun steadyStateFramesDoNotAllocate() {
        val bean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean ?: return
        if (!bean.isThreadAllocatedMemorySupported) return
        val e = engine(KineticMode.ARK_KINETIC)
        var now = 1_000_000_000L
        var pos = 0L
        while (pos < 4000) { e.frame(pos, now); now += frameNs; pos += 8 } // warm up (JIT, pools)
        val id = Thread.currentThread().id
        val before = bean.getThreadAllocatedBytes(id)
        var frames = 0
        while (frames < 600) { e.frame(pos, now); now += frameNs; pos += 8; frames++ }
        val perFrame = (bean.getThreadAllocatedBytes(id) - before) / frames
        assertThat(perFrame).isLessThan(256L)
    }
}
