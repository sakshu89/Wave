package com.lastwave.app.ui.player.kinetic.fx

import com.lastwave.app.ui.player.kinetic.model.WordPhase
import com.lastwave.app.ui.player.kinetic.scene.FastRng
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Fixed-size particle pool. Two kinds:
 *  - free particles: bursts, outbound trickles, released energy; curved paths via a per-particle curl
 *  - orbit particles: little stars circling a held word; when the word is released
 *    (or leaves the HOLD state) they detach with tangential velocity.
 *
 * No allocation after construction. Positions are screen px.
 */
class ParticleSystem(val capacity: Int = 640) {
    val x = FloatArray(capacity)
    val y = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val curl = FloatArray(capacity)
    val life = FloatArray(capacity)
    private val maxLife = FloatArray(capacity)
    val size = FloatArray(capacity)
    val color = IntArray(capacity)
    val alive = BooleanArray(capacity)

    private val orbitWord = IntArray(capacity) { -1 }
    private val orbitAngle = FloatArray(capacity)
    private val orbitRadius = FloatArray(capacity)
    private val orbitSpeed = FloatArray(capacity)
    private val orbitTilt = FloatArray(capacity)

    /** Alpha 0..1 for drawing (written by update). */
    val alpha = FloatArray(capacity)

    var aliveCount = 0; private set

    /** Cap on simultaneously alive particles (settings: particle density). */
    var budget = capacity

    private var cursor = 0
    private val rng = FastRng(0x51ed270b)

    fun reset() {
        for (i in 0 until capacity) { alive[i] = false; orbitWord[i] = -1 }
        aliveCount = 0
    }

    private fun alloc(): Int {
        for (k in 0 until capacity) {
            val idx = (cursor + k) % capacity
            if (!alive[idx]) { cursor = (idx + 1) % capacity; return idx }
        }
        val idx = cursor
        cursor = (cursor + 1) % capacity
        return idx
    }

    private fun live(): Int {
        var n = 0
        for (i in 0 until capacity) if (alive[i]) n++
        return n
    }

    /** Radial burst at (px,py). */
    fun burst(px: Float, py: Float, n: Int, speed: Float, sizeScale: Float, lifeScale: Float, colorIdx: Int) {
        var left = n
        if (aliveCount >= budget) return
        while (left-- > 0 && aliveCount < budget) {
            val i = alloc()
            if (!alive[i]) aliveCount++
            val a = rng.range(0f, (2 * PI).toFloat())
            val s = speed * rng.range(0.35f, 1f)
            x[i] = px; y[i] = py
            vx[i] = cos(a) * s; vy[i] = sin(a) * s
            curl[i] = rng.range(-1.2f, 1.2f)
            maxLife[i] = lifeScale * rng.range(0.7f, 1.5f)
            life[i] = 0f
            size[i] = sizeScale * rng.range(0.7f, 1.8f)
            color[i] = if (rng.next01() < 0.6f) colorIdx else 3
            orbitWord[i] = -1
            alive[i] = true
        }
    }

    /** Slow outbound particle from a word edge (long-hold trickle). */
    fun trickle(px: Float, py: Float, dirAngle: Float, speed: Float, sizeScale: Float, colorIdx: Int) {
        if (aliveCount >= budget) return
        val i = alloc()
        if (!alive[i]) aliveCount++
        val a = dirAngle + rng.range(-0.5f, 0.5f)
        val s = speed * rng.range(0.5f, 1f)
        x[i] = px; y[i] = py
        vx[i] = cos(a) * s; vy[i] = sin(a) * s
        curl[i] = rng.range(-0.8f, 0.8f)
        maxLife[i] = rng.range(1.6f, 3.0f)
        life[i] = 0f
        size[i] = sizeScale * rng.range(0.7f, 1.4f)
        color[i] = colorIdx
        orbitWord[i] = -1
        alive[i] = true
    }

    /** Number of orbit particles currently circling [word]. */
    fun orbitCount(word: Int): Int {
        var n = 0
        for (i in 0 until capacity) if (alive[i] && orbitWord[i] == word) n++
        return n
    }

    /** Add at most one orbit star for [word] per call, so the ring grows gradually. */
    fun growOrbit(word: Int, target: Int, wordHalfWidth: Float, sizeScale: Float, colorIdx: Int) {
        if (aliveCount >= budget || orbitCount(word) >= target) return
        val i = alloc()
        if (!alive[i]) aliveCount++
        orbitWord[i] = word
        orbitAngle[i] = rng.range(0f, (2 * PI).toFloat())
        orbitRadius[i] = wordHalfWidth * rng.range(0.85f, 1.35f) + rng.range(10f, 30f)
        orbitSpeed[i] = rng.range(0.5f, 1.3f) * (if (rng.next01() < 0.5f) 1f else -1f)
        orbitTilt[i] = rng.range(0.28f, 0.6f)
        maxLife[i] = 1e9f
        life[i] = 0f
        size[i] = sizeScale * rng.range(0.9f, 1.8f)
        color[i] = if (rng.next01() < 0.5f) colorIdx else 3
        alive[i] = true
        x[i] = 0f; y[i] = 0f
    }

    /** Detach all orbit stars of [word] with outward/tangential velocity (release). */
    fun releaseOrbit(word: Int, boost: Float) {
        for (i in 0 until capacity) {
            if (alive[i] && orbitWord[i] == word) detach(i, boost)
        }
    }

    private fun detach(i: Int, boost: Float) {
        val a = orbitAngle[i]
        val tang = orbitSpeed[i] * orbitRadius[i] * 0.9f
        // tangent of the (elliptical) orbit plus an outward kick
        vx[i] = (-sin(a) * tang + cos(a) * 110f) * boost
        vy[i] = (cos(a) * tang * orbitTilt[i] + sin(a) * 110f * orbitTilt[i]) * boost
        curl[i] = rng.range(-0.9f, 0.9f)
        orbitWord[i] = -1
        life[i] = 0f
        maxLife[i] = rng.range(1.4f, 2.6f)
    }

    /**
     * @param sx/sy/sc per-word screen position and scale
     * @param phase per-word phase (orbits only survive while the word is ACTIVE/HOLD)
     * @param halfWidth per-word half width at base size, px
     */
    fun update(
        dt: Float, speed: Float, sx: FloatArray, sy: FloatArray, sc: FloatArray,
        phase: Array<WordPhase>,
    ) {
        val drag = exp(-dt * 1.3f)
        var n = 0
        for (i in 0 until capacity) {
            if (!alive[i]) continue
            val ow = orbitWord[i]
            if (ow >= 0) {
                val ph = phase[ow]
                if (ph != WordPhase.ACTIVE && ph != WordPhase.HOLD) {
                    detach(i, 1f)
                } else {
                    orbitAngle[i] += orbitSpeed[i] * dt * speed
                    val r = orbitRadius[i] * sc[ow]
                    x[i] = sx[ow] + cos(orbitAngle[i]) * r
                    y[i] = sy[ow] + sin(orbitAngle[i]) * r * orbitTilt[i]
                    alpha[i] = 0.85f
                    n++
                    continue
                }
            }
            life[i] += dt
            val t = life[i] / maxLife[i]
            if (t >= 1f) { alive[i] = false; continue }
            // rotate velocity (curved trajectory) then apply drag
            val c = curl[i] * dt
            val cs = cos(c); val sn = sin(c)
            val nvx = vx[i] * cs - vy[i] * sn
            val nvy = vx[i] * sn + vy[i] * cs
            vx[i] = nvx * drag
            vy[i] = nvy * drag
            x[i] += vx[i] * dt * speed
            y[i] += vy[i] * dt * speed
            val fade = 1f - t
            alpha[i] = fade * fade * (if (t < 0.08f) t / 0.08f else 1f)
            n++
        }
        aliveCount = n
    }
}
