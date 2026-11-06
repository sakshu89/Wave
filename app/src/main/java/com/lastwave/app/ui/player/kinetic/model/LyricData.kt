package com.lastwave.app.ui.player.kinetic.model

import com.lastwave.app.data.lyrics.LyricLine
import com.lastwave.app.data.lyrics.LyricSyllable
import com.lastwave.app.ui.player.resolveSyllableDisplayTexts

/**
 * One displayable lyric word with the most precise timing LastWave provided.
 *
 * Immutable and built once per track by [LyricWordTimeline.build]; the frame
 * loop only reads it. [isEstimated] is true when timing was derived from line
 * timing rather than provider word/syllable data. Presets must not present
 * estimated words as exact.
 */
class LyricWord(
    val lineIndex: Int,
    /** Index of the word inside its line. */
    val wordIndex: Int,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val isBackground: Boolean,
    val isEstimated: Boolean,
    /** 0..1 visual importance (length, held duration, line end). */
    val importance: Float,
    /** Stable per-word seed; every "random" choice derives from it. */
    val seed: Int,
) {
    val durationMs: Long get() = endMs - startMs
    val hold: HoldClass get() = HoldClass.of(durationMs)
}

/** Duration class used to pick the long-hold behaviour. */
enum class HoldClass {
    SHORT, NORMAL, LONG, VERY_LONG;

    companion object {
        const val SHORT_MAX_MS = 400L
        const val NORMAL_MAX_MS = 1000L
        const val LONG_MAX_MS = 2000L

        fun of(durationMs: Long): HoldClass = when {
            durationMs < SHORT_MAX_MS -> SHORT
            durationMs < NORMAL_MAX_MS -> NORMAL
            durationMs <= LONG_MAX_MS -> LONG
            else -> VERY_LONG
        }
    }
}

/**
 * Flat, time-sorted word list for a whole track.
 *
 * Timing priority is syllable > word > line: syllable-bearing lines are grouped
 * into words (honouring `appendToPrevious`), lines without syllables are split
 * on whitespace and their time span is shared by character weight.
 */
class LyricWordTimeline private constructor(
    val words: List<LyricWord>,
    val lineCount: Int,
) {
    val size: Int get() = words.size

    private val starts = LongArray(words.size) { words[it].startMs }

    /** True when at least one word carries estimated timing. */
    val hasEstimatedWords: Boolean = words.any { it.isEstimated }

    /**
     * Index of the last word whose start is <= [posMs], or -1 before the first.
     * [hint] is the previous result: playback moves forward by tiny steps, so
     * the search is O(1) amortised and only falls back to binary search on seeks.
     */
    fun activeWordAt(posMs: Long, hint: Int = -1): Int {
        val n = starts.size
        if (n == 0 || posMs < starts[0]) return -1
        var i = hint.coerceIn(0, n - 1)
        if (starts[i] <= posMs) {
            var steps = 0
            while (i + 1 < n && starts[i + 1] <= posMs) {
                i++
                if (++steps > 8) return binarySearch(posMs)
            }
            return i
        }
        return binarySearch(posMs)
    }

    private fun binarySearch(posMs: Long): Int {
        var lo = 0
        var hi = starts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (starts[mid] <= posMs) lo = mid else hi = mid - 1
        }
        return lo
    }

    companion object {
        val EMPTY = LyricWordTimeline(emptyList(), 0)

        private const val DEFAULT_SYLLABLE_MS = 250L
        private const val MAX_ESTIMATED_LINE_MS = 8_000L
        private const val ESTIMATE_TAIL_FRACTION = 0.92f

        fun build(lines: List<LyricLine>, trackKey: String): LyricWordTimeline {
            if (lines.isEmpty()) return EMPTY
            val out = ArrayList<LyricWord>(lines.size * 6)
            for (li in lines.indices) {
                val line = lines[li]
                if (line.text.isBlank() && line.syllables.isEmpty()) continue
                val nextStart = lines.getOrNull(li + 1)?.timeMs
                val built = if (line.hasSyllables) {
                    fromSyllables(li, line, nextStart, trackKey)
                } else {
                    estimate(li, line, nextStart, trackKey)
                }
                out.addAll(built)
            }
            // Stable: background/overlapping lines interleave by start time.
            out.sortBy { it.startMs }
            return LyricWordTimeline(out, lines.size)
        }

        // ---- syllable path -------------------------------------------------

        private fun fromSyllables(
            lineIndex: Int,
            line: LyricLine,
            nextLineStart: Long?,
            trackKey: String,
        ): List<LyricWord> {
            val syls = line.syllables
            val display = resolveSyllableDisplayTexts(line)
            val spaced = line.text.contains(' ') || line.text.contains(' ')

            val words = ArrayList<LyricWord>()
            var from = 0
            while (from < syls.size) {
                var to = from
                while (to < syls.lastIndex && !isBoundaryAfter(syls, display, to, spaced)) to++
                words += wordOf(lineIndex, words.size, syls, from, to, nextLineStart, trackKey, line)
                from = to + 1
            }
            return words.filter { it.text.isNotBlank() }.mapIndexed { i, w -> w.reindexed(i) }
        }

        private fun isBoundaryAfter(
            syls: List<LyricSyllable>,
            display: List<String>,
            i: Int,
            spaced: Boolean,
        ): Boolean {
            val next = syls[i + 1]
            if (next.appendToPrevious) return false
            if (!spaced) return true // spaceless scripts: one syllable = one word
            val d = display.getOrNull(i) ?: syls[i].text
            return d.endsWith(' ') || d.endsWith(' ') || next.text.startsWith(' ')
        }

        private fun wordOf(
            lineIndex: Int,
            wordIndex: Int,
            syls: List<LyricSyllable>,
            from: Int,
            to: Int,
            nextLineStart: Long?,
            trackKey: String,
            line: LyricLine,
        ): LyricWord {
            val sb = StringBuilder()
            var allBg = true
            for (k in from..to) {
                sb.append(syls[k].text)
                if (!syls[k].isBackground) allBg = false
            }
            val start = syls[from].timeMs
            val last = syls[to]
            val end = when {
                last.durationMs > 0L -> last.timeMs + last.durationMs
                to < syls.lastIndex -> syls[to + 1].timeMs
                else -> nextLineStart?.takeIf { it > start } ?: (start + DEFAULT_SYLLABLE_MS)
            }.coerceAtLeast(start + 1L)
            val text = sb.toString().trim()
            val isLineEnd = to == syls.lastIndex
            return LyricWord(
                lineIndex = lineIndex,
                wordIndex = wordIndex,
                text = text,
                startMs = start,
                endMs = end,
                isBackground = allBg,
                isEstimated = false,
                importance = importanceOf(text, end - start, isLineEnd),
                seed = seedOf(trackKey, lineIndex, wordIndex),
            )
        }

        // ---- estimation path (line timing only) ----------------------------

        private fun estimate(
            lineIndex: Int,
            line: LyricLine,
            nextLineStart: Long?,
            trackKey: String,
        ): List<LyricWord> {
            val tokens = line.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return emptyList()

            val start = line.timeMs
            val span = when {
                line.durationMs > 0L -> line.durationMs
                nextLineStart != null && nextLineStart > start -> nextLineStart - start
                else -> tokens.size * 380L
            }.coerceIn(tokens.size * 120L, MAX_ESTIMATED_LINE_MS)
            val usable = span * ESTIMATE_TAIL_FRACTION

            // Weight: letters dominate; trailing punctuation adds a small pause.
            val weights = FloatArray(tokens.size) { i ->
                val t = tokens[i]
                val letters = t.count { it.isLetterOrDigit() }.coerceAtLeast(1)
                val pause = if (t.last() in ",;:.!?…") 1.5f else 0f
                letters + pause + 1f
            }
            val total = weights.sum()

            val result = ArrayList<LyricWord>(tokens.size)
            var acc = 0f
            for (i in tokens.indices) {
                val ws = start + (usable * acc / total).toLong()
                acc += weights[i]
                val we = start + (usable * acc / total).toLong()
                val end = maxOf(we, ws + 1L)
                result += LyricWord(
                    lineIndex = lineIndex,
                    wordIndex = i,
                    text = tokens[i],
                    startMs = ws,
                    endMs = end,
                    isBackground = false,
                    isEstimated = true,
                    importance = importanceOf(tokens[i], end - ws, i == tokens.lastIndex),
                    seed = seedOf(trackKey, lineIndex, i),
                )
            }
            return result
        }

        // ---- helpers -------------------------------------------------------

        private fun LyricWord.reindexed(i: Int) =
            if (i == wordIndex) this else LyricWord(
                lineIndex, i, text, startMs, endMs, isBackground, isEstimated, importance,
                seed = seedOf(null, lineIndex, i, seed),
            )

        /** 0..1: longer, held and line-final words matter more. */
        internal fun importanceOf(text: String, durationMs: Long, isLineEnd: Boolean): Float {
            val len = (text.length / 8f).coerceIn(0f, 1f)
            val held = (durationMs / 2000f).coerceIn(0f, 1f)
            val end = if (isLineEnd) 0.2f else 0f
            return (0.15f + 0.3f * len + 0.45f * held + end).coerceIn(0f, 1f)
        }

        /** FNV-1a over the track key and word coordinates; stable across runs. */
        internal fun seedOf(trackKey: String?, line: Int, word: Int, base: Int = -0x7ee3623b): Int {
            var h = base
            if (trackKey != null) for (c in trackKey) { h = (h xor c.code) * 16777619 }
            h = (h xor line) * 16777619
            h = (h xor word) * 16777619
            return h
        }
    }
}
