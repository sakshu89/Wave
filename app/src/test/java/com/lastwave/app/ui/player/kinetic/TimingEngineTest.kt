package com.lastwave.app.ui.player.kinetic

import com.google.common.truth.Truth.assertThat
import com.lastwave.app.data.lyrics.LyricLine
import com.lastwave.app.data.lyrics.LyricSyllable
import com.lastwave.app.ui.player.kinetic.model.HoldClass
import com.lastwave.app.ui.player.kinetic.model.LyricWordTimeline
import com.lastwave.app.ui.player.kinetic.model.WordPhase
import com.lastwave.app.ui.player.kinetic.model.WordStates
import com.lastwave.app.ui.player.kinetic.timing.TimingEngine
import com.lastwave.app.ui.player.kinetic.timing.WordEventListener
import org.junit.Test

class TimingEngineTest {

    private fun line(vararg syls: Triple<Long, Long, String>): LyricLine =
        LyricLine(
            timeMs = syls.first().first,
            text = syls.joinToString(" ") { it.third },
            syllables = syls.map { (t, d, s) -> LyricSyllable(timeMs = t, durationMs = d, text = "$s ") },
        )

    private fun engine(vararg lines: LyricLine): Pair<TimingEngine, WordStates> {
        val tl = LyricWordTimeline.build(lines.toList(), "t")
        val st = WordStates(tl.size)
        return TimingEngine(tl, st) to st
    }

    private class Recorder : WordEventListener {
        val log = ArrayList<String>()
        override fun onWordEnter(wordIndex: Int) { log += "enter$wordIndex" }
        override fun onWordActive(wordIndex: Int) { log += "active$wordIndex" }
        override fun onWordExit(wordIndex: Int) { log += "exit$wordIndex" }
        override fun onLongHold(wordIndex: Int, tier: HoldClass) { log += "hold$wordIndex:$tier" }
    }

    // One 3 s word starting at 5000 ms.
    private fun longWord() = engine(line(Triple(5000L, 3000L, "hold")))

    @Test fun longWordWalksEveryPhase() {
        val (e, st) = longWord()
        fun at(pos: Long): WordPhase { e.update(pos); return st.phase[0] }
        assertThat(at(3000)).isEqualTo(WordPhase.FUTURE)
        assertThat(at(4400)).isEqualTo(WordPhase.APPROACHING)
        assertThat(st.phaseProgress[0]).isWithin(0.01f).of(0.5f)
        assertThat(at(5050)).isEqualTo(WordPhase.ENTER)
        assertThat(at(5300)).isEqualTo(WordPhase.ACTIVE)
        assertThat(at(5600)).isEqualTo(WordPhase.HOLD)
        assertThat(at(7000)).isEqualTo(WordPhase.HOLD)
        assertThat(at(8100)).isEqualTo(WordPhase.EXIT)
        assertThat(at(8600)).isEqualTo(WordPhase.MEMORY)
        assertThat(st.phaseProgress[0]).isLessThan(1f)
        assertThat(at(20_000)).isEqualTo(WordPhase.MEMORY)
        assertThat(st.phaseProgress[0]).isEqualTo(1f)
    }

    @Test fun shortWordSkipsHold() {
        val (e, st) = engine(line(Triple(1000L, 200L, "hi")))
        e.update(1120)
        assertThat(st.phase[0]).isEqualTo(WordPhase.ACTIVE)
        e.update(1210)
        assertThat(st.phase[0]).isEqualTo(WordPhase.EXIT)
        assertThat(st.holdEnergy[0]).isEqualTo(0f)
    }

    @Test fun holdEnergyBuildsContinuouslyAndReleasesOnExit() {
        val (e, st) = longWord()
        e.update(5100)
        assertThat(st.holdEnergy[0]).isEqualTo(0f)
        var prev = 0f
        var pos = 5400L
        while (pos < 8000) {
            e.update(pos)
            val v = st.holdEnergy[0]
            assertThat(v).isAtLeast(prev)
            assertThat(v).isAtMost(1f)
            prev = v
            pos += 16
        }
        assertThat(prev).isGreaterThan(0.8f)
        e.update(8010)
        assertThat(st.phase[0]).isEqualTo(WordPhase.EXIT)
        assertThat(st.releaseEnergy[0]).isGreaterThan(0.8f)
        e.update(8440)
        assertThat(st.holdEnergy[0]).isLessThan(0.1f)
        e.update(9000)
        assertThat(st.releaseEnergy[0]).isEqualTo(0f)
    }

    @Test fun eventsFireOnceInOrderDuringNormalPlayback() {
        val (e, _) = longWord()
        val r = Recorder()
        var pos = 0L
        while (pos < 20_000) { e.update(pos, r); pos += 16 }
        assertThat(r.log).containsExactly(
            "enter0", "active0", "hold0:LONG", "hold0:VERY_LONG", "exit0",
        ).inOrder()
    }

    @Test fun skippedFrameStillFiresEachEventOnce() {
        val (e, st) = engine(line(Triple(500L, 200L, "hi")))
        val r = Recorder()
        e.update(0, r)
        e.update(1000, r) // 1 s jump: below resync threshold, word fully skipped
        assertThat(r.log).containsExactly("enter0", "active0", "exit0").inOrder()
        assertThat(st.phase[0]).isEqualTo(WordPhase.EXIT)
    }

    @Test fun seeksResyncSilentlyAndPlaybackResumes() {
        val (e, st) = longWord()
        val r = Recorder()
        e.update(6000, r)
        r.log.clear()

        e.update(100, r) // seek back
        assertThat(st.phase[0]).isEqualTo(WordPhase.FUTURE)
        assertThat(r.log).isEmpty()

        e.update(20_000, r) // seek far forward
        assertThat(st.phase[0]).isEqualTo(WordPhase.MEMORY)
        assertThat(st.phaseProgress[0]).isEqualTo(1f)
        assertThat(r.log).isEmpty()

        e.update(5500, r) // seek into the hold, then play on
        assertThat(st.phase[0]).isEqualTo(WordPhase.HOLD)
        assertThat(r.log).isEmpty()
        var pos = 5500L
        while (pos < 9000) { e.update(pos, r); pos += 16 }
        // Entered/active were skipped by the seek; later events still fire once.
        assertThat(r.log).containsExactly("hold0:LONG", "hold0:VERY_LONG", "exit0").inOrder()
    }

    @Test fun smallBackwardJitterDoesNotRefireEvents() {
        val (e, _) = longWord()
        val r = Recorder()
        e.update(4900, r) // first call is a silent resync
        e.update(5100, r)
        e.update(5090, r) // 10 ms backwards jitter
        e.update(5110, r)
        assertThat(r.log.count { it == "enter0" }).isEqualTo(1)
    }

    @Test fun tracksActiveWordLineAndNext() {
        val (e, st) = engine(
            line(Triple(1000L, 500L, "a"), Triple(2000L, 500L, "b")),
            line(Triple(4000L, 500L, "c")),
        )
        e.update(500)
        assertThat(e.activeWord).isEqualTo(-1)
        e.update(2100)
        assertThat(e.activeWord).isEqualTo(1)
        assertThat(e.activeLine).isEqualTo(0)
        assertThat(e.nextWord).isEqualTo(2)
        e.update(4100)
        assertThat(e.activeWord).isEqualTo(2)
        assertThat(e.activeLine).isEqualTo(1)
        assertThat(e.nextWord).isEqualTo(-1)
        assertThat(st.phase[0]).isEqualTo(WordPhase.MEMORY)
    }

    @Test fun pausedPositionIsStable() {
        val (e, st) = longWord()
        val r = Recorder()
        e.update(6000, r)
        val before = r.log.toList()
        val prog = st.phaseProgress[0]
        repeat(50) { e.update(6000, r) }
        assertThat(r.log).isEqualTo(before)
        assertThat(st.phaseProgress[0]).isEqualTo(prog)
    }

    @Test fun emptyTimelineIsSafe() {
        val tl = LyricWordTimeline.EMPTY
        val e = TimingEngine(tl, WordStates(0))
        e.update(1234)
        assertThat(e.activeWord).isEqualTo(-1)
    }

    @Test fun resetReturnsToFuture() {
        val (e, st) = longWord()
        e.update(6000)
        e.reset()
        assertThat(st.phase[0]).isEqualTo(WordPhase.FUTURE)
        assertThat(e.activeWord).isEqualTo(-1)
    }
}
