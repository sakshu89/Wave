package com.lastwave.app.ui.player.kinetic

import com.google.common.truth.Truth.assertThat
import com.lastwave.app.data.lyrics.LyricLine
import com.lastwave.app.data.lyrics.LyricSyllable
import com.lastwave.app.ui.player.kinetic.model.HoldClass
import com.lastwave.app.ui.player.kinetic.model.LyricWordTimeline
import org.junit.Test

class LyricWordTimelineTest {

    private fun syl(t: Long, d: Long, s: String, append: Boolean = false) =
        LyricSyllable(timeMs = t, durationMs = d, text = s, appendToPrevious = append)

    @Test fun syllableTimingIsUsedDirectlyAndNotEstimated() {
        val line = LyricLine(
            timeMs = 1000, text = "hello world",
            syllables = listOf(syl(1000, 400, "hello "), syl(1500, 700, "world")),
        )
        val tl = LyricWordTimeline.build(listOf(line), "t")
        assertThat(tl.words.map { it.text }).containsExactly("hello", "world").inOrder()
        assertThat(tl.words[0].startMs).isEqualTo(1000)
        assertThat(tl.words[0].endMs).isEqualTo(1400)
        assertThat(tl.words[1].endMs).isEqualTo(2200)
        assertThat(tl.hasEstimatedWords).isFalse()
    }

    @Test fun appendToPreviousJoinsIntoOneWord() {
        val line = LyricLine(
            timeMs = 0, text = "withdrawals now",
            syllables = listOf(syl(0, 200, "with"), syl(200, 300, "drawals", append = true), syl(600, 300, "now")),
        )
        val tl = LyricWordTimeline.build(listOf(line), "t")
        assertThat(tl.words.map { it.text }).containsExactly("withdrawals", "now").inOrder()
        assertThat(tl.words[0].startMs).isEqualTo(0)
        assertThat(tl.words[0].endMs).isEqualTo(500)
    }

    @Test fun lineOnlyTimingIsEstimatedAndFlagged() {
        val lines = listOf(
            LyricLine(timeMs = 0, text = "one two three"),
            LyricLine(timeMs = 3000, text = "four"),
        )
        val tl = LyricWordTimeline.build(lines, "t")
        assertThat(tl.words.take(3).all { it.isEstimated }).isTrue()
        assertThat(tl.hasEstimatedWords).isTrue()
        val w = tl.words.take(3)
        assertThat(w[0].startMs).isEqualTo(0)
        assertThat(w[1].startMs).isAtLeast(w[0].endMs - 1)
        assertThat(w[2].endMs).isAtMost(3000)
    }

    @Test fun activeWordLookupHandlesHintSeekAndBounds() {
        val line = LyricLine(
            timeMs = 1000, text = "a b c d",
            syllables = listOf(syl(1000, 100, "a "), syl(2000, 100, "b "), syl(3000, 100, "c "), syl(4000, 100, "d")),
        )
        val tl = LyricWordTimeline.build(listOf(line), "t")
        assertThat(tl.activeWordAt(500)).isEqualTo(-1)
        assertThat(tl.activeWordAt(2050, hint = 0)).isEqualTo(1)
        assertThat(tl.activeWordAt(4500, hint = 1)).isEqualTo(3)
        assertThat(tl.activeWordAt(1500, hint = 3)).isEqualTo(0) // backwards seek
        assertThat(tl.activeWordAt(99_000)).isEqualTo(3)
    }

    @Test fun seedsAreDeterministicAndDifferPerWord() {
        val line = LyricLine(timeMs = 0, text = "x y")
        val a = LyricWordTimeline.build(listOf(line), "track")
        val b = LyricWordTimeline.build(listOf(line), "track")
        assertThat(a.words.map { it.seed }).isEqualTo(b.words.map { it.seed })
        assertThat(a.words[0].seed).isNotEqualTo(a.words[1].seed)
    }

    @Test fun holdClassBoundaries() {
        assertThat(HoldClass.of(399)).isEqualTo(HoldClass.SHORT)
        assertThat(HoldClass.of(400)).isEqualTo(HoldClass.NORMAL)
        assertThat(HoldClass.of(1000)).isEqualTo(HoldClass.LONG)
        assertThat(HoldClass.of(2000)).isEqualTo(HoldClass.LONG)
        assertThat(HoldClass.of(2001)).isEqualTo(HoldClass.VERY_LONG)
    }

    @Test fun emptyAndBlankLinesAreSafe() {
        assertThat(LyricWordTimeline.build(emptyList(), "t").size).isEqualTo(0)
        val tl = LyricWordTimeline.build(listOf(LyricLine(timeMs = 0, text = "  ")), "t")
        assertThat(tl.size).isEqualTo(0)
        assertThat(tl.activeWordAt(100)).isEqualTo(-1)
    }
}
