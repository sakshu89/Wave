package com.lastwave.app.ui.player.kinetic.scene

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** Tiny allocation-free xorshift RNG. Same seed => same sequence. */
class FastRng(seed: Int) {
    private var s = if (seed == 0) 0x1234567 else seed

    fun nextInt(): Int {
        var x = s
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        s = x
        return x
    }

    /** [0,1) */
    fun next01(): Float = (nextInt() ushr 8) / 16777216f

    fun range(a: Float, b: Float): Float = a + (b - a) * next01()
}

object Hash {
    /** Stateless deterministic float in [0,1) from (seed, salt). */
    fun hash01(seed: Int, salt: Int): Float {
        var h = seed xor (salt * 0x27d4eb2d)
        h = h xor (h ushr 15)
        h *= -0x7a143595
        h = h xor (h ushr 13)
        h *= -0x3d4d51cb
        h = h xor (h ushr 16)
        return (h ushr 8) / 16777216f
    }
}

object Ease {
    fun clamp01(t: Float): Float = if (t < 0f) 0f else if (t > 1f) 1f else t
    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
    fun smooth(t: Float): Float { val x = clamp01(t); return x * x * (3f - 2f * x) }
    fun out3(t: Float): Float { val x = 1f - clamp01(t); return 1f - x * x * x }
    fun inOut(t: Float): Float { val x = clamp01(t); return if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f }

    /** Damped overshoot 0..1..(slightly >1)..1, starts at 0 and ends near 1. */
    fun elastic(t: Float): Float {
        val x = clamp01(t)
        return 1f - exp(-5.5f * x) * cos(11f * x)
    }

    /** Same shape but softer, for position springs. */
    fun spring(t: Float): Float {
        val x = clamp01(t)
        return 1f - exp(-6.5f * x) * cos(8.5f * x)
    }

    fun sinPi(t: Float): Float = sin(Math.PI.toFloat() * clamp01(t))
}
