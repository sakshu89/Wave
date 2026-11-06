package com.lastwave.app.ui.player.kinetic.scene

import com.lastwave.app.ui.player.kinetic.settings.KineticBackground
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Pure-Kotlin ARGB/HSL helpers (no android.graphics, so they are unit-testable). */
object ColorMath {
    fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    fun lerp(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(shift: Int): Int {
            val x = (a ushr shift) and 0xFF
            val y = (b ushr shift) and 0xFF
            return (x + (y - x) * k + 0.5f).toInt()
        }
        return argb(ch(24), ch(16), ch(8), ch(0))
    }

    /** Writes h (0..360), s, l (0..1) into [out]. */
    fun toHsl(c: Int, out: FloatArray) {
        val r = ((c shr 16) and 0xFF) / 255f
        val g = ((c shr 8) and 0xFF) / 255f
        val b = (c and 0xFF) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val l = (mx + mn) / 2f
        val d = mx - mn
        var h = 0f
        var s = 0f
        if (d > 1e-6f) {
            s = d / (1f - abs(2f * l - 1f)).coerceAtLeast(1e-6f)
            h = when (mx) {
                r -> ((g - b) / d).mod(6f)
                g -> (b - r) / d + 2f
                else -> (r - g) / d + 4f
            } * 60f
        }
        out[0] = h; out[1] = s.coerceIn(0f, 1f); out[2] = l
    }

    fun fromHsl(h: Float, s: Float, l: Float): Int {
        val hh = ((h % 360f) + 360f) % 360f
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((hh / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r, g, b) = when {
            hh < 60f -> Triple(c, x, 0f)
            hh < 120f -> Triple(x, c, 0f)
            hh < 180f -> Triple(0f, c, x)
            hh < 240f -> Triple(0f, x, c)
            hh < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return argb(255, ((r + m) * 255f + 0.5f).toInt(), ((g + m) * 255f + 0.5f).toInt(), ((b + m) * 255f + 0.5f).toInt())
    }
}

/**
 * Colour atmosphere for the scene, derived from LastWave's existing artwork
 * colours (the three ambient colours already passed to the lyrics panel).
 *
 * Exposes [primary], [secondary], [accent], [background], [glow] (+ [text],
 * [dim] helpers). Changing the target crossfades smoothly; [step] returns true
 * while colours are still moving.
 */
class PaletteManager {
    var primary = 0xFFB9A7FF.toInt(); private set
    var secondary = 0xFF7FB6FF.toInt(); private set
    var accent = 0xFFFFC4E8.toInt(); private set
    var background = 0xFF000000.toInt(); private set
    var glow = 0xFFA58CFF.toInt(); private set
    var text = 0xFFF4F1FF.toInt(); private set
    var dim = 0xFF8C86A8.toInt(); private set

    /** Incremented whenever any colour changed. */
    var version = 0; private set

    private var tPrimary = primary
    private var tSecondary = secondary
    private var tAccent = accent
    private var tBackground = background
    private var tGlow = glow
    private var initialised = false

    private val hsl = FloatArray(3)

    /** Colour for star/particle index 0..3 (primary, secondary, accent, white-ish). */
    fun star(idx: Int): Int = when (idx and 3) {
        0 -> primary
        1 -> secondary
        2 -> accent
        else -> text
    }

    fun setTarget(
        c1: Int, c2: Int, c3: Int,
        mode: KineticBackground,
        custom: Int,
        backgroundIntensity: Float,
    ) {
        val d = derive(c1, c2, c3, mode, custom, backgroundIntensity)
        tPrimary = d[0]; tSecondary = d[1]; tAccent = d[2]; tBackground = d[3]; tGlow = d[4]
        if (!initialised) {
            primary = tPrimary; secondary = tSecondary; accent = tAccent
            background = tBackground; glow = tGlow
            recomputeHelpers()
            initialised = true
            version++
        }
    }

    /** Advance the crossfade. Returns true if something changed. */
    fun step(dtSec: Float): Boolean {
        val k = 1f - exp(-dtSec * 3.5f)
        val np = ColorMath.lerp(primary, tPrimary, k)
        val ns = ColorMath.lerp(secondary, tSecondary, k)
        val na = ColorMath.lerp(accent, tAccent, k)
        val nb = ColorMath.lerp(background, tBackground, k)
        val ng = ColorMath.lerp(glow, tGlow, k)
        if (np == primary && ns == secondary && na == accent && nb == background && ng == glow) {
            // lerp can stall one step short of the target because of rounding
            if (primary == tPrimary && secondary == tSecondary && accent == tAccent &&
                background == tBackground && glow == tGlow
            ) return false
            primary = tPrimary; secondary = tSecondary; accent = tAccent
            background = tBackground; glow = tGlow
        } else {
            primary = np; secondary = ns; accent = na; background = nb; glow = ng
        }
        recomputeHelpers()
        version++
        return true
    }

    private fun recomputeHelpers() {
        text = ColorMath.lerp(0xFFFFFFFF.toInt(), primary, 0.14f)
        dim = ColorMath.lerp(primary, background, 0.42f)
    }

    /** Returns [primary, secondary, accent, background, glow]. */
    fun derive(
        c1: Int, c2: Int, c3: Int,
        mode: KineticBackground,
        custom: Int,
        backgroundIntensity: Float,
    ): IntArray {
        val h = FloatArray(3)
        fun sat(c: Int): Float { ColorMath.toHsl(c, h); return h[1] * (1f - abs(h[2] * 2f - 1f) * 0.3f) }
        fun lift(c: Int): Int {
            ColorMath.toHsl(c, h)
            val neutral = h[1] < 0.10f
            val s = if (neutral) 0f else h[1].coerceIn(0.45f, 0.95f)
            val l = if (neutral) 0.80f else h[2].coerceIn(0.58f, 0.74f)
            return ColorMath.fromHsl(h[0], s, l)
        }
        val l1 = lift(c1)
        var l2 = lift(c2)
        val l3 = lift(c3)

        ColorMath.toHsl(l1, h)
        val h1 = h[0]
        ColorMath.toHsl(l2, h)
        if (h[1] > 0.1f && hueDistance(h1, h[0]) < 12f) l2 = ColorMath.fromHsl(h[0] + 28f, h[1], h[2])

        // Accent: the most saturated of the three source colours, shifted a little.
        val best = listOf(c1, c2, c3).maxByOrNull { sat(it) } ?: c1
        val accentBase = lift(best)
        ColorMath.toHsl(accentBase, h)
        val acc = if (h[1] < 0.1f) ColorMath.fromHsl(0f, 0f, 0.90f)
        else ColorMath.fromHsl(h[0] + 18f, h[1].coerceAtLeast(0.55f), 0.72f)

        ColorMath.toHsl(l1, h)
        val glowBase = if (h[1] < 0.1f) ColorMath.fromHsl(0f, 0f, 0.75f)
        else ColorMath.fromHsl(h[0], h[1].coerceAtLeast(0.6f), 0.60f)
        val gl = ColorMath.lerp(glowBase, acc, 0.35f)

        val bgi = backgroundIntensity.coerceIn(0f, 1.6f)
        ColorMath.toHsl(c1, h)
        val bg = when (mode) {
            KineticBackground.AMOLED -> 0xFF000000.toInt()
            KineticBackground.CINEMATIC ->
                ColorMath.fromHsl(h[0], (h[1] * 0.55f).coerceIn(0f, 0.6f), 0.03f + 0.03f * bgi.coerceAtMost(1f))
            KineticBackground.DARK ->
                ColorMath.fromHsl(h[0], (h[1] * 0.25f).coerceIn(0f, 0.3f), 0.07f)
            KineticBackground.CUSTOM -> custom or (0xFF shl 24)
        }
        return intArrayOf(l1, l2, acc, bg, gl)
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }
}
