package com.lastwave.app.ui.player.kinetic.fx

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Living constellation: thin lines between nearby lyric words.
 *
 * Rules (so it never becomes a network diagram):
 *  - every node links to at most [MAX_LINKS_PER_NODE] nearest neighbours
 *  - links are limited by screen distance and by depth difference
 *  - the hero (active) word gets the strongest links to its nearest neighbours
 *  - link strength eases in/out through a small slot table, so lines fade
 *    naturally and re-form as words move instead of popping
 *
 * Fixed capacity, no allocation per frame.
 */
class ConstellationEngine(val capacity: Int = 96) {
    val a = IntArray(capacity) { -1 }
    val b = IntArray(capacity)
    /** Current drawn strength 0..1. */
    val strength = FloatArray(capacity)
    private val target = FloatArray(capacity)

    companion object {
        const val MAX_LINKS_PER_NODE = 2
        const val MAX_NODES = 48
    }

    fun reset() {
        for (i in 0 until capacity) { a[i] = -1; strength[i] = 0f; target[i] = 0f }
    }

    private fun slotFor(i: Int, j: Int): Int {
        var free = -1
        for (s in 0 until capacity) {
            if (a[s] == i && b[s] == j) return s
            if (a[s] < 0 && free < 0) free = s
        }
        if (free >= 0) {
            a[free] = i; b[free] = j; strength[free] = 0f; target[free] = 0f
        }
        return free
    }

    private fun want(i: Int, j: Int, t: Float) {
        val lo = if (i < j) i else j
        val hi = if (i < j) j else i
        val s = slotFor(lo, hi)
        if (s >= 0 && t > target[s]) target[s] = t
    }

    /**
     * @param lo/hi word index window to consider
     * @param sx/sy/z screen position and world depth per word
     * @param nodeAlpha per-word visibility 0..1 (text or node dot)
     * @param energy per-word hold energy (hero links strengthen as it builds)
     * @param hero index of the active word, or -1
     * @param maxDist max link distance in px
     * @param intensity user line intensity * preset factor (0 disables)
     * @param beat 0..1 beat pulse
     */
    fun update(
        dt: Float, lo: Int, hi: Int, sx: FloatArray, sy: FloatArray, z: FloatArray,
        nodeAlpha: FloatArray, energy: FloatArray, hero: Int, maxDist: Float, intensity: Float, beat: Float,
    ) {
        for (s in 0 until capacity) target[s] = 0f

        if (intensity > 0.01f) {
            val maxD2 = maxDist * maxDist
            var nodes = 0
            for (i in lo until hi) {
                if (nodeAlpha[i] < 0.05f) continue
                if (++nodes > MAX_NODES) break
                var best1 = -1; var d1 = Float.MAX_VALUE
                var best2 = -1; var d2 = Float.MAX_VALUE
                for (j in lo until hi) {
                    if (j == i || nodeAlpha[j] < 0.05f) continue
                    val dx = sx[j] - sx[i]
                    val dy = sy[j] - sy[i]
                    val d = dx * dx + dy * dy
                    if (d > maxD2 * (if (i == hero || j == hero) 2.0f else 1f)) continue
                    if (abs(z[j] - z[i]) > 700f) continue
                    if (d < d1) { best2 = best1; d2 = d1; best1 = j; d1 = d }
                    else if (d < d2) { best2 = j; d2 = d }
                }
                if (best1 >= 0) link(i, best1, d1, maxD2, nodeAlpha, z, hero, energy, intensity, beat)
                if (best2 >= 0) link(i, best2, d2, maxD2, nodeAlpha, z, hero, energy, intensity, beat)
            }
        }

        val up = 1f - exp(-dt * 6f)
        val down = 1f - exp(-dt * 2.5f)
        for (s in 0 until capacity) {
            if (a[s] < 0) continue
            val t = target[s]
            strength[s] += (t - strength[s]) * (if (t > strength[s]) up else down)
            if (t == 0f && strength[s] < 0.01f) { a[s] = -1; strength[s] = 0f }
        }
    }

    private fun link(
        i: Int, j: Int, d2: Float, maxD2: Float, nodeAlpha: FloatArray, z: FloatArray,
        hero: Int, energy: FloatArray, intensity: Float, beat: Float,
    ) {
        val prox = 1f - sqrt(d2 / (maxD2 * 2f)).coerceIn(0f, 1f)
        val depth = 1f - (abs(z[j] - z[i]) / 700f).coerceIn(0f, 1f)
        val vis = if (nodeAlpha[i] < nodeAlpha[j]) nodeAlpha[i] else nodeAlpha[j]
        var t = vis * prox * depth * 0.35f
        if (i == hero || j == hero) {
            val e = if (i == hero) energy[i] else energy[j]
            t = (0.55f + 0.35f * e) * prox.coerceAtLeast(0.35f) * depth.coerceAtLeast(0.4f)
        }
        t *= intensity * (1f + 0.25f * beat)
        if (t > 0.01f) want(i, j, t.coerceIn(0f, 1f))
    }
}
