package com.lastwave.app.ui.player.kinetic.scene

import com.lastwave.app.ui.player.kinetic.model.LyricWordTimeline
import kotlin.math.max

/** Measures text width at the layout's reference size (implemented over android Paint in the renderer). */
fun interface TextMeasurer {
    fun measure(text: String): Float
}

/**
 * Static typographic layout of every word in a track, computed once per
 * (viewport, size, spacing) and read every frame.
 *
 * Words are wrapped into balanced rows per lyric line and centred; lines are
 * stacked top to bottom with [lineCenterY] giving each line's vertical centre
 * in scene px (y down, 0 = the first line's own top). Per-word [relX]/[relY]
 * are offsets from their line centre. No text drawing happens here.
 */
class WordLayout(private val timeline: LyricWordTimeline) {
    private val n = timeline.size
    val lineCount = timeline.lineCount

    val relX = FloatArray(n)
    val relY = FloatArray(n)
    val width = FloatArray(n)

    val lineCenterY = FloatArray(max(lineCount, 1))
    val lineHeight = FloatArray(max(lineCount, 1))

    /** Smallest / largest word index in each line, or -1 for lines without words. */
    val lineFirstWord = IntArray(max(lineCount, 1)) { -1 }
    val lineLastWord = IntArray(max(lineCount, 1)) { -1 }

    private val lineWords: Array<IntArray>

    var rowHeight = 0f; private set
    var baseSize = 0f; private set
    var maxWordWidth = 0f; private set

    init {
        val tmp = Array(max(lineCount, 1)) { ArrayList<Int>(8) }
        for (i in 0 until n) {
            val l = timeline.words[i].lineIndex
            if (l in tmp.indices) tmp[l].add(i)
        }
        lineWords = Array(tmp.size) { l ->
            tmp[l].sortedBy { timeline.words[it].wordIndex }.toIntArray()
        }
        for (l in lineWords.indices) {
            val ws = lineWords[l]
            if (ws.isEmpty()) continue
            var mn = ws[0]; var mx = ws[0]
            for (k in ws) { if (k < mn) mn = k; if (k > mx) mx = k }
            lineFirstWord[l] = mn
            lineLastWord[l] = mx
        }
    }

    fun wordCountOf(line: Int): Int = lineWords.getOrNull(line)?.size ?: 0

    fun layout(viewW: Float, baseSizePx: Float, spacing: Float, measurer: TextMeasurer) {
        baseSize = baseSizePx
        rowHeight = baseSizePx * 1.2f
        val gap = baseSizePx * 0.34f * spacing // room for the hero word growing ~1.3x in perspective
        val maxRowW = (viewW * 0.86f).coerceAtLeast(baseSizePx * 2f)
        maxWordWidth = 0f

        for (i in 0 until n) {
            width[i] = measurer.measure(timeline.words[i].text)
            if (width[i] > maxWordWidth) maxWordWidth = width[i]
        }

        var top = 0f
        val lineGap = baseSizePx * 0.62f
        for (l in lineWords.indices) {
            val ws = lineWords[l]
            if (ws.isEmpty()) {
                lineHeight[l] = baseSizePx * 0.5f
                lineCenterY[l] = top + lineHeight[l] / 2f
                top += lineHeight[l] + lineGap
                continue
            }
            // 1) greedy row count at the real max width
            val rows0 = countRows(ws, maxRowW, gap)
            // 2) balance: greedy again at the average row width (never narrower than the longest word)
            var limit = maxRowW
            if (rows0 > 1) {
                var total = 0f
                var longest = 0f
                for (k in ws) { total += width[k] + gap; if (width[k] > longest) longest = width[k] }
                limit = (total / rows0 * 1.08f).coerceIn(longest, maxRowW)
                if (countRows(ws, limit, gap) > rows0) limit = maxRowW
            }
            val rows = countRows(ws, limit, gap)
            assignRows(ws, limit, gap, rows)
            lineHeight[l] = rows * rowHeight
            lineCenterY[l] = top + lineHeight[l] / 2f
            top += lineHeight[l] + lineGap
        }
    }

    private fun countRows(ws: IntArray, limit: Float, gap: Float): Int {
        var rows = 1
        var x = 0f
        for ((idx, k) in ws.withIndex()) {
            val w = width[k]
            if (idx > 0 && x + gap + w > limit) { rows++; x = w } else x += (if (idx > 0) gap else 0f) + w
        }
        return rows
    }

    private fun assignRows(ws: IntArray, limit: Float, gap: Float, rows: Int) {
        var start = 0
        var row = 0
        while (start < ws.size) {
            var end = start
            var x = width[ws[start]]
            while (end + 1 < ws.size && x + gap + width[ws[end + 1]] <= limit) {
                end++
                x += gap + width[ws[end]]
            }
            var cx = -x / 2f
            val y = (row - (rows - 1) / 2f) * rowHeight
            for (idx in start..end) {
                val k = ws[idx]
                relX[k] = cx + width[k] / 2f
                relY[k] = y
                cx += width[k] + gap
            }
            row++
            start = end + 1
        }
    }
}
