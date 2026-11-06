package com.lastwave.app.ui.player.kinetic.fx

import com.lastwave.app.ui.player.kinetic.scene.FastRng
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pooled background stars (struct-of-arrays, zero per-frame allocation).
 *
 * Stars differ in size, brightness, depth, drift velocity, twinkle, colour and
 * lifespan-free "flash" (a transient brightening used when words react).
 * Positions are normalised (-1.3..1.3) and wrap; depth drives parallax so near
 * stars move more than far ones. Results for the renderer land in [sx]/[sy]
 * (screen px), [radius] (px) and [alpha] (0..1) for the first [count] stars.
 */
class StarField(val capacity: Int = 240) {
    private val nx = FloatArray(capacity)
    private val ny = FloatArray(capacity)
    val depth = FloatArray(capacity)
    private val baseSize = FloatArray(capacity)
    private val baseBright = FloatArray(capacity)
    private val twPhase = FloatArray(capacity)
    private val twSpeed = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val flash = FloatArray(capacity)

    /** 0..3 palette index (see PaletteManager.star). */
    val color = IntArray(capacity)

    /** True for the rare large stars that get a halo + cross sparkle. */
    val bright = BooleanArray(capacity)

    val sx = FloatArray(capacity)
    val sy = FloatArray(capacity)
    val radius = FloatArray(capacity)
    val alpha = FloatArray(capacity)

    var count = 0; private set

    private var seed = 1
    private var time = 0f

    fun reseed(seed: Int) {
        this.seed = seed
        val r = FastRng(seed xor 0x5bd1e995)
        for (i in 0 until capacity) {
            nx[i] = r.range(-1.3f, 1.3f)
            ny[i] = r.range(-1.3f, 1.3f)
            // Bias toward far (small, dim) stars so the field has real depth.
            val d = r.next01()
            depth[i] = d * d * 0.85f + 0.12f
            val isBright = r.next01() < 0.06f
            bright[i] = isBright
            baseSize[i] = (if (isBright) r.range(1.6f, 2.6f) else r.range(0.5f, 1.4f)) * (0.55f + depth[i] * 0.7f)
            baseBright[i] = (if (isBright) r.range(0.75f, 1f) else r.range(0.25f, 0.7f)) * (0.45f + depth[i] * 0.55f)
            twPhase[i] = r.range(0f, (2 * PI).toFloat())
            twSpeed[i] = r.range(0.4f, 2.2f)
            val ang = r.range(0f, (2 * PI).toFloat())
            val sp = r.range(0.002f, 0.010f)
            vx[i] = kotlin.math.cos(ang) * sp
            vy[i] = sin(ang) * sp
            color[i] = when {
                r.next01() < 0.55f -> 3
                r.next01() < 0.5f -> 0
                r.next01() < 0.5f -> 1
                else -> 2
            }
            flash[i] = 0f
        }
    }

    /**
     * @param density 0..2 relative star count (1 ≈ 130 stars)
     * @param sizeScale star size multiplier
     * @param brightScale overall brightness multiplier
     * @param motion drift multiplier (0 freezes drift)
     * @param parX/parY camera parallax offset in px, applied scaled by depth
     * @param beat 0..1 beat pulse (gentle brightening)
     */
    fun update(
        dt: Float, w: Float, h: Float, density: Float, sizeScale: Float, brightScale: Float,
        motion: Float, parX: Float, parY: Float, beat: Float, speed: Float,
    ) {
        time += dt
        count = (130f * density).toInt().coerceIn(0, capacity)
        val decay = exp(-dt * 2.2f)
        val half = 0.5f * (if (w > h) w else h) * 1.0f
        for (i in 0 until count) {
            val d = depth[i]
            val m = dt * (0.35f + d) * motion * speed
            nx[i] += vx[i] * m * 12f
            ny[i] += vy[i] * m * 12f
            if (nx[i] > 1.3f) nx[i] -= 2.6f else if (nx[i] < -1.3f) nx[i] += 2.6f
            if (ny[i] > 1.3f) ny[i] -= 2.6f else if (ny[i] < -1.3f) ny[i] += 2.6f
            flash[i] *= decay

            sx[i] = w * 0.5f + nx[i] * half - parX * (0.25f + d * 0.9f)
            sy[i] = h * 0.5f + ny[i] * half - parY * (0.25f + d * 0.9f)
            val tw = 0.62f + 0.38f * sin(time * twSpeed[i] + twPhase[i])
            alpha[i] = (baseBright[i] * brightScale * tw * (1f + 0.35f * beat) + flash[i]).coerceIn(0f, 1f)
            radius[i] = baseSize[i] * sizeScale * (1f + flash[i] * 0.6f)
        }
    }

    /** Brighten stars near a screen point: "nearby stars react". */
    fun pulseNear(x: Float, y: Float, rad: Float, amount: Float) {
        val r2 = rad * rad
        for (i in 0 until count) {
            val dx = sx[i] - x
            val dy = sy[i] - y
            val d2 = dx * dx + dy * dy
            if (d2 < r2) {
                val f = 1f - sqrt(d2) / rad
                val v = flash[i] + amount * f * (0.5f + depth[i])
                flash[i] = if (v > 0.9f) 0.9f else v
            }
        }
    }
}
