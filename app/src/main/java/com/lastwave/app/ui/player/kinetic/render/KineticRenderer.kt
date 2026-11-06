package com.lastwave.app.ui.player.kinetic.render

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.lastwave.app.ui.player.kinetic.anim.AnimationEngine
import com.lastwave.app.ui.player.kinetic.anim.PresetCanvas
import com.lastwave.app.ui.player.kinetic.scene.ColorMath
import com.lastwave.app.ui.player.kinetic.scene.TextMeasurer
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws one kinetic frame onto a native canvas. All Paints, the soft-glow sprite,
 * the blur-filter table and the sort buffer are created once, so a frame performs
 * no allocation. Layers, back to front:
 *   background -> colour atmosphere -> stars -> constellation -> preset layers
 *   -> particles -> words (depth sorted) -> optional perf overlay.
 *
 * Soft glows use a single ALPHA_8 radial sprite tinted by the paint colour, which is
 * far cheaper than per-frame gradients or blurred bitmaps. Word blur/glow use a small
 * table of pre-built BlurMaskFilters (quantised radii).
 */
class KineticRenderer : PresetCanvas {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, 600, false)
        textAlign = Paint.Align.CENTER
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val perfPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() }
    private val dst = RectF()

    private val glowSprite: Bitmap = createGlowSprite()

    private val blurRadii = floatArrayOf(1.2f, 2f, 3f, 4.5f, 6.5f, 9f, 13f, 18f, 25f)
    private val blurFilters: Array<BlurMaskFilter> = Array(blurRadii.size) {
        BlurMaskFilter(blurRadii[it], BlurMaskFilter.Blur.NORMAL)
    }

    private var baselineShift = 0f
    private var density = 1f
    private var glowCount = 0

    private val order = IntArray(512)
    private var orderCount = 0

    /** Measures at the size last given to [setTextSize]. */
    val measurer = TextMeasurer { textPaint.measureText(it) }

    fun setTextSize(px: Float) {
        if (textPaint.textSize != px) {
            textPaint.textSize = px
            val fm = textPaint.fontMetrics
            baselineShift = -(fm.ascent + fm.descent) / 2f
        }
    }

    // ---- PresetCanvas ----------------------------------------------------

    override fun disc(x: Float, y: Float, r: Float, argb: Int, alpha: Float) {
        glow(x, y, r, argb, alpha)
    }

    override fun line(x1: Float, y1: Float, x2: Float, y2: Float, widthPx: Float, argb: Int, alpha: Float) {
        // callers draw into whichever canvas is active in draw()
        val c = activeCanvas ?: return
        linePaint.color = argb
        linePaint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        linePaint.strokeWidth = widthPx
        c.drawLine(x1, y1, x2, y2, linePaint)
    }

    private var activeCanvas: Canvas? = null

    private fun glow(x: Float, y: Float, r: Float, argb: Int, alpha: Float) {
        val a = alpha.coerceIn(0f, 1f)
        if (a < 0.004f || r < 1f) return
        val c = activeCanvas ?: return
        spritePaint.color = argb
        spritePaint.alpha = (a * 255f).toInt()
        dst.set(x - r, y - r, x + r, y + r)
        c.drawBitmap(glowSprite, null, dst, spritePaint)
    }

    // ---- frame -----------------------------------------------------------

    fun draw(canvas: Canvas, engine: AnimationEngine, frameStamp: Long) {
        val t0 = System.nanoTime()
        val c = engine.ctx
        val pal = c.palette
        activeCanvas = canvas
        density = c.density
        canvas.drawColor(pal.background)
        if (!engine.isReady) { activeCanvas = null; return }

        val s = c.settings
        val profile = engine.preset.profile

        drawAtmosphere(engine, profile.atmosphere)
        drawStars(engine)
        drawConstellation(engine, profile.constellation > 0f)
        engine.preset.render(c, this)
        drawParticles(engine)
        drawWords(engine)

        if (s.showPerf) {
            engine.perf.refreshText(frameStamp)
            perfPaint.textSize = 11f * density
            canvas.drawText(engine.perf.text, 8f * density, 24f * density, perfPaint)
        }
        engine.perf.onRender(System.nanoTime() - t0)
        activeCanvas = null
    }

    private fun drawAtmosphere(engine: AnimationEngine, factor: Float) {
        val c = engine.ctx
        val s = c.settings
        val k = s.backgroundIntensity * factor
        if (k <= 0.01f) return
        val pal = c.palette
        val t = c.timeSec * s.motionScale
        val big = max(c.width, c.height)
        val hero = c.heroWord
        val e = if (hero >= 0) min(1f, c.states.holdEnergy[hero] * s.longWordEffect) else 0f
        val amoled = if (s.background == com.lastwave.app.ui.player.kinetic.settings.KineticBackground.AMOLED) 0.55f else 1f
        val a = 0.17f * k * amoled * (1f + 0.35f * e + 0.15f * c.beat.pulse * s.beatReaction)
        glow(c.width * (0.20f + 0.05f * sin(t * 0.05f)), c.height * (0.26f + 0.04f * cos(t * 0.04f)), big * 0.95f, pal.primary, a)
        glow(c.width * (0.84f + 0.04f * cos(t * 0.045f)), c.height * (0.80f + 0.04f * sin(t * 0.05f)), big * 0.85f, pal.secondary, a * 0.8f)
    }

    private fun drawStars(engine: AnimationEngine) {
        val c = engine.ctx
        val pal = c.palette
        val sf = c.stars
        val ln = density
        for (i in 0 until sf.count) {
            val a = sf.alpha[i]
            if (a < 0.03f) continue
            val col = ColorMath.lerp(pal.star(sf.color[i]), 0xFFFFFFFF.toInt(), 0.35f)
            val x = sf.sx[i]
            val y = sf.sy[i]
            val r = sf.radius[i]
            fillPaint.color = col
            fillPaint.alpha = (a * 255f).toInt()
            activeCanvas!!.drawCircle(x, y, r, fillPaint)
            if (sf.bright[i] && a > 0.35f) {
                glow(x, y, r * 7f, col, a * 0.30f)
                linePaint.color = col
                linePaint.alpha = (a * 150f).toInt()
                linePaint.strokeWidth = max(0.6f, ln * 0.7f)
                val L = r * 5.5f
                activeCanvas!!.drawLine(x - L, y, x + L, y, linePaint)
                activeCanvas!!.drawLine(x, y - L, x, y + L, linePaint)
            }
        }
    }

    private fun drawConstellation(engine: AnimationEngine, enabled: Boolean) {
        if (!enabled) return
        val c = engine.ctx
        val ce = c.constellation
        val pal = c.palette
        val st = c.states
        val cv = activeCanvas!!
        val col = ColorMath.lerp(pal.accent, pal.primary, 0.4f)
        for (s in 0 until ce.capacity) {
            val i = ce.a[s]
            if (i < 0) continue
            val j = ce.b[s]
            val str = ce.strength[s]
            if (str < 0.02f) continue
            if (i < c.winLo || i >= c.winHi || j < c.winLo || j >= c.winHi) continue
            val sc = (c.screenScale[i] + c.screenScale[j]) * 0.5f
            linePaint.color = col
            linePaint.alpha = (str * 0.55f * 255f).toInt()
            linePaint.strokeWidth = max(0.6f, (0.7f + 0.9f * str) * density * sc)
            cv.drawLine(c.screenX[i], c.screenY[i], c.screenX[j], c.screenY[j], linePaint)
        }
        // Nodes: faint stars where words are about to be / have been (visible beyond their text).
        for (i in c.winLo until c.winHi) {
            val da = c.nodeAlpha[i] - st.alpha[i]
            if (da < 0.04f || c.screenScale[i] < 0.1f) continue
            val sc = c.screenScale[i]
            val nc = ColorMath.lerp(pal.star(st.colorIdx[i]), 0xFFFFFFFF.toInt(), 0.4f)
            fillPaint.color = nc
            fillPaint.alpha = (min(1f, da * 1.4f) * 255f).toInt()
            cv.drawCircle(c.screenX[i], c.screenY[i], 2.1f * density * sc, fillPaint)
            glow(c.screenX[i], c.screenY[i], 14f * density * sc, nc, da * 0.35f)
        }
    }

    private fun drawParticles(engine: AnimationEngine) {
        val c = engine.ctx
        val pal = c.palette
        val ps = c.particles
        val cv = activeCanvas!!
        for (i in 0 until ps.capacity) {
            if (!ps.alive[i]) continue
            val a = ps.alpha[i]
            if (a < 0.02f) continue
            val col = ColorMath.lerp(pal.star(ps.color[i]), 0xFFFFFFFF.toInt(), 0.3f)
            val r = ps.size[i]
            fillPaint.color = col
            fillPaint.alpha = (a * 255f).toInt()
            cv.drawCircle(ps.x[i], ps.y[i], r, fillPaint)
            if (r > 2f * density) glow(ps.x[i], ps.y[i], r * 4.5f, col, a * 0.35f)
        }
    }

    private fun drawWords(engine: AnimationEngine) {
        val c = engine.ctx
        val st = c.states
        val pal = c.palette
        val words = c.timeline.words
        val cv = activeCanvas!!

        // Collect + depth sort (far first). Insertion sort: the order barely changes frame to frame.
        orderCount = 0
        for (i in c.winLo until c.winHi) {
            if (st.alpha[i] < 0.012f || c.screenScale[i] < 0.12f) continue
            if (orderCount < order.size) order[orderCount++] = i
        }
        for (k in 1 until orderCount) {
            val v = order[k]
            val z = st.z[v]
            var j = k - 1
            while (j >= 0 && st.z[order[j]] < z) { order[j + 1] = order[j]; j-- }
            order[j + 1] = v
        }

        glowCount = 0
        for (k in 0 until orderCount) {
            val i = order[k]
            val persp = c.screenScale[i]
            val sc = persp * st.scale[i]
            val half = c.layout.width[i] * 0.5f * sc
            val x = c.screenX[i]
            val y = c.screenY[i]
            if (x + half < 0f || x - half > c.width || y < -c.height * 0.3f || y > c.height * 1.3f) continue

            val yaw = abs(cos(st.rotY[i])).coerceAtLeast(0.3f)
            val a = st.alpha[i]
            val focus = st.focus[i]
            val textCol = ColorMath.lerp(pal.dim, pal.text, focus)

            cv.save()
            cv.translate(x, y)
            if (st.rotZ[i] != 0f) cv.rotate(Math.toDegrees(st.rotZ[i].toDouble()).toFloat())
            cv.scale(sc * st.stretch[i] * yaw, sc)

            // Halo pass for lit words (limited count).
            val gl = st.glow[i]
            if (gl > 0.05f && focus > 0.3f && glowCount < 12) {
                glowCount++
                val lvl = (3 + (gl * 3f).toInt()).coerceIn(3, blurFilters.size - 1)
                textPaint.maskFilter = blurFilters[lvl]
                textPaint.color = pal.glow
                textPaint.alpha = (min(0.9f, gl * 0.55f) * a * 255f).toInt()
                cv.drawText(words[i].text, 0f, baselineShift, textPaint)
                if (gl > 0.6f) {
                    textPaint.maskFilter = blurFilters[2]
                    textPaint.alpha = (min(0.8f, gl * 0.35f) * a * 255f).toInt()
                    cv.drawText(words[i].text, 0f, baselineShift, textPaint)
                }
            }

            // Crisp (or depth-blurred) text.
            val localBlur = st.blur[i] / max(sc, 0.2f)
            textPaint.maskFilter = if (localBlur < 0.8f) null else blurFilters[blurLevel(localBlur)]
            textPaint.color = textCol
            textPaint.alpha = (a * 255f).toInt()
            cv.drawText(words[i].text, 0f, baselineShift, textPaint)
            textPaint.maskFilter = null
            cv.restore()
        }
    }

    private fun blurLevel(r: Float): Int {
        for (k in blurRadii.indices) if (blurRadii[k] >= r) return k
        return blurRadii.size - 1
    }

    private companion object {
        fun createGlowSprite(): Bitmap {
            val n = 128
            val bytes = ByteArray(n * n)
            val half = n / 2f
            for (yy in 0 until n) for (xx in 0 until n) {
                val dx = (xx + 0.5f - half) / half
                val dy = (yy + 0.5f - half) / half
                val d = kotlin.math.sqrt(dx * dx + dy * dy)
                val v = (1f - d).coerceIn(0f, 1f)
                bytes[yy * n + xx] = (v * v * 255f).toInt().toByte()
            }
            val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ALPHA_8)
            bmp.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
            return bmp
        }
    }
}
