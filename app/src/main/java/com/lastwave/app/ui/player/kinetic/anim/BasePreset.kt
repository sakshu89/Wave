package com.lastwave.app.ui.player.kinetic.anim

import com.lastwave.app.ui.player.kinetic.model.HoldClass
import com.lastwave.app.ui.player.kinetic.model.WordPhase
import com.lastwave.app.ui.player.kinetic.scene.Ease
import com.lastwave.app.ui.player.kinetic.scene.Hash
import com.lastwave.app.ui.player.kinetic.scene.SceneContext
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Shared implementation of the word lifecycle visuals. Every word's transform
 * is a pure function of (phase, phaseProgress, holdEnergy, scene time, seed),
 * so there is no per-word integrator to drift, and all phase boundaries meet
 * with equal values (no jumps). Motion is "designed": each phase has its own
 * curve and a per-word [Behaviour] adds a deterministic entrance/exit flavour.
 *
 * Long holds: `holdEnergy` (0..1, rising while the word is sung) drives, in order of
 * appearance: slow expansion, breathing glow with growing amplitude and a
 * slowly increasing rate, orbiting stars, outbound particles, stronger
 * constellation links, and a slow 3D drift. On release the stored energy
 * decays through EXIT while a particle/star release fires.
 */
open class BasePreset(override val profile: PresetProfile) : AnimationPreset {

    private var emitAcc = 0f
    private var angleAcc = 0f

    override fun reset(ctx: SceneContext) {
        emitAcc = 0f
        val words = ctx.timeline.words
        for (i in words.indices) {
            val w = words[i]
            ctx.behaviour[i] = BehaviourSelector.choose(
                w, profile.allowed, ctx.layout.wordCountOf(w.lineIndex),
            ).ordinal
        }
        ctx.particles.reset()
        ctx.constellation.reset()
    }

    override fun update(ctx: SceneContext, dt: Float) {
        val s = ctx.settings
        val st = ctx.states
        val words = ctx.timeline.words
        val lay = ctx.layout
        val t = ctx.timeSec
        val dens = ctx.density
        val mot = s.motionScale
        val amt = s.animationIntensity * (0.35f + 0.65f * mot)
        val depthK = s.depth3d * profile.depth * (0.4f + 0.6f * mot)
        val hero = profile.heroScale * s.activeWordScale
        val gA = profile.glow * s.activeWordGlow * s.glow
        val longE = s.longWordEffect
        val beatK = ctx.beat.pulse * s.beatReaction * profile.beat
        val m = ctx.mods
        val tau = (2.0 * PI).toFloat()
        var visible = 0
        val invDt = if (dt > 1e-4f) 1f / dt else 0f

        for (i in ctx.winLo until ctx.winHi) {
            val word = words[i]
            val seed = word.seed
            val ph = st.phase[i]
            val pr = st.phaseProgress[i]
            val vis = lineVis(word.lineIndex - ctx.focusLineF)
            val en = min(st.holdEnergy[i] * longE, 1.4f)
            val b = Behaviour.ALL[ctx.behaviour[i]]
            val memA = profile.memoryAlpha * (0.6f + 0.4f * vis)
            val zFar = 520f + (Hash.hash01(seed, 7) - 0.5f) * 160f

            var a: Float
            var sc: Float
            var z: Float
            var bl: Float
            var gl: Float
            var fo: Float
            var tEnter = 1f
            var dot: Float
            var breathGate = 0f

            when (ph) {
                WordPhase.FUTURE -> {
                    a = profile.futureAlpha * vis; sc = 0.80f; z = zFar; bl = 5f; gl = 0f; fo = 0f
                    tEnter = 0.30f; dot = 0.14f * vis
                }
                WordPhase.APPROACHING -> {
                    val e = Ease.smooth(pr)
                    a = Ease.lerp(profile.futureAlpha * vis, 0.42f, e)
                    sc = Ease.lerp(0.80f, 0.95f, e); z = Ease.lerp(zFar, 140f, e)
                    bl = Ease.lerp(5f, 1.5f, e); gl = 0.25f * e; fo = 0.35f * e
                    tEnter = 0.30f + 0.30f * e
                    dot = Ease.lerp(0.14f * vis, 0.6f, e)
                }
                WordPhase.ENTER -> {
                    val e = Ease.out3(pr)
                    val pulse = Ease.sinPi(pr)
                    a = Ease.lerp(0.42f, 1f, e)
                    sc = Ease.lerp(0.95f, hero, e) + 0.08f * hero * pulse
                    z = Ease.lerp(140f, -150f, e)
                    bl = Ease.lerp(1.5f, 0f, e)
                    gl = Ease.lerp(0.25f, gA, e) + 0.7f * gA * pulse
                    fo = Ease.lerp(0.35f, 1f, e)
                    tEnter = 0.60f + 0.40f * e
                    dot = 1f
                }
                WordPhase.ACTIVE -> {
                    a = 1f; sc = hero; z = -150f; bl = 0f; gl = gA; fo = 1f; dot = 1f
                    breathGate = Ease.smooth(pr)
                }
                WordPhase.HOLD -> {
                    a = 1f; sc = hero; z = -150f; bl = 0f; gl = gA; fo = 1f; dot = 1f
                    breathGate = 1f
                }
                WordPhase.EXIT -> {
                    val e = Ease.smooth(pr)
                    a = Ease.lerp(1f, memA, e)
                    sc = Ease.lerp(hero, 0.86f, e); z = Ease.lerp(-150f, 240f, e)
                    bl = Ease.lerp(0f, 3f, e); gl = Ease.lerp(gA, 0.12f, e); fo = Ease.lerp(1f, 0.4f, e)
                    dot = Ease.lerp(1f, 0.5f * profile.memoryStars, e)
                    breathGate = 1f - e
                }
                WordPhase.MEMORY -> {
                    val fade = (1f - min(1f, pr / 0.55f)).pow(1.3f)
                    a = memA * fade
                    sc = Ease.lerp(0.86f, 0.70f, pr); z = Ease.lerp(240f, 420f, pr)
                    bl = Ease.lerp(3f, 6f, pr); gl = 0.12f * (1f - pr); fo = 0.4f * (1f - pr)
                    dot = 0.5f * profile.memoryStars * (1f - pr * pr)
                }
            }

            // Behaviour flavour.
            m.clear()
            if (tEnter < 1f && ph.ordinal < WordPhase.ACTIVE.ordinal) BehaviourFx.enter(b, seed, tEnter, amt, m)
            if (ph == WordPhase.EXIT) BehaviourFx.exit(b, seed, pr, amt, m)
            else if (ph == WordPhase.MEMORY) BehaviourFx.exit(b, seed, 1f, amt, m)

            // Long-hold evolution, continuous across ACTIVE -> HOLD -> EXIT because `en` decays in EXIT.
            var holdDx = 0f
            var holdDy = 0f
            var holdRotY = 0f
            var holdRotZ = 0f
            if (ph.ordinal >= WordPhase.ACTIVE.ordinal && ph.ordinal <= WordPhase.EXIT.ordinal) {
                val ph1 = Hash.hash01(seed, 21) * tau
                val rate = 0.70f + 0.60f * en
                val breath = sin(t * tau * rate + ph1) * (0.012f + 0.035f * en) * breathGate
                sc *= 1f + 0.18f * en + breath
                z -= 140f * en
                gl *= 1f + 0.8f * en + 0.25f * en * sin(t * tau * (0.60f + 0.70f * en) + ph1 * 0.7f)
                holdRotY = sin(t * 0.50f + ph1) * 0.30f * en * mot
                holdRotZ = sin(t * 0.37f + ph1 * 1.3f) * 0.06f * en * mot
                holdDx = sin(t * 0.41f + ph1) * 14f * en * mot
                holdDy = kotlin.math.cos(t * 0.33f + ph1 * 0.6f) * 9f * en * mot
            }

            // Beat: subtle, hero only.
            if (fo > 0.9f && beatK > 0f) {
                sc *= 1f + 0.04f * beatK
                gl *= 1f + 0.5f * beatK
            }

            // Ambient wander ("floating through space") and per-line jitter.
            val ax = sin(t * 0.37f + Hash.hash01(seed, 5) * tau) * 7f * profile.wander * mot
            val ay = sin(t * 0.51f + Hash.hash01(seed, 6) * tau) * 9f * profile.wander * mot
            val lineShift = (Hash.hash01(ctx.seed + word.lineIndex * 31, 11) - 0.5f) * 0.12f * ctx.width * profile.lineJitter
            val lineRot = (Hash.hash01(ctx.seed + word.lineIndex * 31, 12) - 0.5f) * 0.03f * profile.lineJitter

            val x = lay.relX[i] + lineShift + (m.dx + holdDx + ax) * dens
            val y = lay.lineCenterY[word.lineIndex] + lay.relY[i] - ctx.scrollY + (m.dy + holdDy + ay) * dens
            val zz = (z + m.dz) * depthK * dens

            val depthBlur = max(0f, z) / 900f * 4f
            val blurPx = (bl + m.blurAdd + depthBlur) * s.blur * profile.blur
            var alpha = (a * m.alphaMul).coerceIn(0f, 1f)
            val far = kotlin.math.abs(word.lineIndex - ctx.focusLineF)
            if (far > 4f) alpha = 0f
            var scale = sc * m.scaleMul
            if (word.isBackground) { scale *= 0.74f; alpha *= 0.8f }

            st.vx[i] = (x - st.x[i]) * invDt
            st.vy[i] = (y - st.y[i]) * invDt
            st.vz[i] = (zz - st.z[i]) * invDt
            st.x[i] = x; st.y[i] = y; st.z[i] = zz
            st.scale[i] = scale
            st.rotZ[i] = m.rotZ + holdRotZ + lineRot
            st.rotY[i] = m.rotY + holdRotY
            st.stretch[i] = m.stretch
            st.alpha[i] = alpha
            st.blur[i] = blurPx
            st.glow[i] = gl
            st.focus[i] = fo
            st.colorIdx[i] = (Hash.hash01(seed, 9) * 3f).toInt().coerceIn(0, 2)
            ctx.nodeAlpha[i] = if (far > 4f) 0f else min(1f, dot)
            if (alpha > 0.02f) visible++
        }
        ctx.visibleWords = visible

        driveHeroParticles(ctx, dt, longE)
    }

    private fun driveHeroParticles(ctx: SceneContext, dt: Float, longE: Float) {
        val h = ctx.heroWord
        if (h < 0) { emitAcc = 0f; return }
        val s = ctx.settings
        val st = ctx.states
        val dens = s.particleDensity * profile.particles
        if (dens <= 0.01f) return
        val mot = s.motionScale
        val e = min(st.holdEnergy[h] * longE, 1f)
        val d = ctx.density

        val orbitTarget = (e * 6f * profile.orbit * dens * mot).toInt()
        if (orbitTarget > 0) {
            ctx.particles.growOrbit(h, orbitTarget, ctx.layout.width[h] * 0.5f, 1.5f * d, st.colorIdx[h])
        }

        emitAcc += dt * (e * e * 14f * dens + 1.2f * dens)
        while (emitAcc >= 1f) {
            emitAcc -= 1f
            angleAcc += 2.3999632f
            val sc = ctx.screenScale[h]
            val half = ctx.layout.width[h] * 0.5f * sc * st.scale[h]
            val px = ctx.screenX[h] + sin(angleAcc * 1.7f) * half
            val py = ctx.screenY[h] + kotlin.math.cos(angleAcc * 1.3f) * ctx.layout.baseSize * 0.3f * sc
            ctx.particles.trickle(px, py, angleAcc, 38f * d * s.particleSpeed * (0.6f + e), 1.2f * d, st.colorIdx[h])
        }
    }

    override fun render(ctx: SceneContext, canvas: PresetCanvas) {
        val h = ctx.heroWord
        if (h < 0 || profile.atmosphere <= 0f) return
        val s = ctx.settings
        val e = min(ctx.states.holdEnergy[h] * s.longWordEffect, 1f)
        val sc = ctx.screenScale[h]
        val r = (ctx.layout.width[h] * 0.5f + 90f * ctx.density) * sc * ctx.states.scale[h] * (1f + 0.5f * e)
        val a = 0.11f * profile.atmosphere * s.glow * (0.7f + 0.6f * e) * ctx.states.alpha[h]
        canvas.disc(ctx.screenX[h], ctx.screenY[h], r, ctx.palette.glow, a)
    }

    override fun onWordEnter(ctx: SceneContext, wordIndex: Int) {
        val s = ctx.settings
        val w = ctx.timeline.words[wordIndex]
        ctx.beat.kick(0.30f + 0.40f * w.importance)
        val dens = s.particleDensity * profile.particles
        val factor = when (Behaviour.ALL[ctx.behaviour[wordIndex]]) {
            Behaviour.PARTICLE_BURST -> 2.2f
            Behaviour.PARTICLE_DISSOLVE -> 1.5f
            else -> 1f
        }
        val n = ((4f + 6f * w.importance) * factor * dens).toInt()
        val x = ctx.screenX[wordIndex]
        val y = ctx.screenY[wordIndex]
        if (n > 0 && ctx.width > 0f) {
            ctx.particles.burst(x, y, n, 120f * ctx.density * s.particleSpeed, 1.3f * ctx.density, 1.1f, ctx.states.colorIdx[wordIndex])
        }
        ctx.stars.pulseNear(x, y, 280f * ctx.density, 0.55f * profile.starBright)
    }

    override fun onWordActive(ctx: SceneContext, wordIndex: Int) {}

    override fun onWordExit(ctx: SceneContext, wordIndex: Int) {
        val s = ctx.settings
        val st = ctx.states
        val dens = s.particleDensity * profile.particles
        val rel = min(st.releaseEnergy[wordIndex] * s.longWordEffect, 1f)
        ctx.particles.releaseOrbit(wordIndex, 1.2f)
        val n = ((3f + 34f * rel) * dens).toInt()
        val x = ctx.screenX[wordIndex]
        val y = ctx.screenY[wordIndex]
        if (n > 0 && ctx.width > 0f) {
            ctx.particles.burst(
                x, y, n, (90f + 160f * rel) * ctx.density * s.particleSpeed,
                1.2f * ctx.density, 1.3f + rel, st.colorIdx[wordIndex],
            )
        }
        if (rel > 0.2f) {
            ctx.stars.pulseNear(x, y, (220f + 260f * rel) * ctx.density, (0.5f + 0.5f * rel) * profile.starBright)
            ctx.beat.kick(0.4f + 0.4f * rel)
        }
    }

    override fun onLongHold(ctx: SceneContext, wordIndex: Int, tier: HoldClass) {
        val s = ctx.settings
        val dens = s.particleDensity * profile.particles
        val x = ctx.screenX[wordIndex]
        val y = ctx.screenY[wordIndex]
        val strong = tier == HoldClass.VERY_LONG
        val n = ((if (strong) 14f else 8f) * dens * s.longWordEffect).toInt()
        if (n > 0 && ctx.width > 0f) {
            ctx.particles.burst(x, y, n, (if (strong) 150f else 100f) * ctx.density * s.particleSpeed,
                1.1f * ctx.density, 1.6f, ctx.states.colorIdx[wordIndex])
        }
        ctx.stars.pulseNear(x, y, (if (strong) 420f else 300f) * ctx.density, (if (strong) 0.7f else 0.4f) * profile.starBright)
        if (strong) ctx.beat.kick(0.5f)
    }

    companion object {
        // line offset -> visibility, linear between integer offsets
        private val VIS = floatArrayOf(0f, 0.10f, 0.24f, 0.48f, 1.0f, 0.62f, 0.32f, 0.14f, 0f)

        /** Visibility of a line at signed (fractional) distance [d] from the focus line. */
        fun lineVis(d: Float): Float {
            val x = d + 4f
            if (x <= 0f || x >= 8f) return 0f
            val i = x.toInt()
            val f = x - i
            return VIS[i] + (VIS[i + 1] - VIS[i]) * f
        }
    }
}
