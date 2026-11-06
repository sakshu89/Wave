package com.lastwave.app.ui.player.kinetic.timing

import com.lastwave.app.ui.player.kinetic.model.HoldClass
import com.lastwave.app.ui.player.kinetic.model.LyricWordTimeline
import com.lastwave.app.ui.player.kinetic.model.WordPhase
import com.lastwave.app.ui.player.kinetic.model.WordStates

/** Phase windows and hold-energy shaping. All times in milliseconds. */
class TimingConfig(
    /** A word starts APPROACHING this long before its start time. */
    val approachMs: Long = 1200L,
    /** ENTER length (clamped to half the word's duration). */
    val enterMs: Long = 180L,
    /** ACTIVE lasts until this long after the start; the rest of the word is HOLD. */
    val holdStartMs: Long = 400L,
    val exitMs: Long = 450L,
    /** How long a finished word stays visible as MEMORY before fully faded. */
    val memoryMs: Long = 6000L,
    /** Hold energy starts building this long after the word starts... */
    val energyStartMs: Long = 300L,
    /** ...and reaches 1.0 this long after [energyStartMs] (smoothstep-eased). */
    val energyRiseMs: Long = 2400L,
    /** Backward steps larger than this, or forward steps larger than [resyncForwardMs], are seeks. */
    val resyncBackwardMs: Long = 100L,
    val resyncForwardMs: Long = 1500L,
)

/** Transition callbacks. Fired once per word, in order, only on natural playback (never on seeks). */
interface WordEventListener {
    fun onWordEnter(wordIndex: Int) {}
    fun onWordActive(wordIndex: Int) {}
    fun onWordExit(wordIndex: Int) {}

    /** [tier] is [HoldClass.LONG] after 1 s of singing the word and [HoldClass.VERY_LONG] after 2 s. */
    fun onLongHold(wordIndex: Int, tier: HoldClass) {}
}

/**
 * Turns the shared playback position into per-word lifecycle state, every frame.
 *
 * It owns no clock: callers pass the position from LastWave's existing frame
 * clock. It is stateless with respect to time apart from a small processing
 * window, so pausing and scrubbing are exact. Steady-state `update` performs no
 * allocation and touches only the words near the playhead.
 *
 * Writes [WordStates.phase], [WordStates.phaseProgress], [WordStates.holdEnergy]
 * and [WordStates.releaseEnergy]; every other state field belongs to the
 * animation layer.
 */
class TimingEngine(
    private val timeline: LyricWordTimeline,
    val states: WordStates,
    /** Replaceable at runtime (e.g. transition-speed setting); takes effect next frame. */
    var config: TimingConfig = TimingConfig(),
) {
    private val words = timeline.words
    private val n = words.size
    private val starts = LongArray(n) { words[it].startMs }
    private val ends = LongArray(n) { words[it].endMs }

    /** Highest hold tier already announced per word (0 none, 1 LONG, 2 VERY_LONG). */
    private val firedTier = IntArray(n)

    // Words in [lo, hi) are processed each frame; below lo are finished, from hi on are FUTURE.
    private var lo = 0
    private var hi = 0
    private var lastPos = Long.MIN_VALUE
    private var hint = -1

    /** Index of the last word whose start has passed, or -1. */
    var activeWord: Int = -1
        private set

    /** Line index of [activeWord], or -1. */
    var activeLine: Int = -1
        private set

    /** Index of the next word that has not started yet, or -1 at the end. */
    var nextWord: Int = -1
        private set

    init {
        require(states.size == n) { "WordStates size ${states.size} != timeline size $n" }
    }

    /** Forget all progress (call on track change); the next update resyncs silently. */
    fun reset() {
        states.resetTiming()
        firedTier.fill(0)
        lo = 0; hi = 0; hint = -1
        lastPos = Long.MIN_VALUE
        activeWord = -1; activeLine = -1; nextWord = -1
    }

    /**
     * Advance to [posMs] (the shared playback position, lyric offset included).
     * Pass a [listener] to receive lifecycle events; pass null to update silently.
     */
    fun update(posMs: Long, listener: WordEventListener? = null) {
        if (n == 0) return

        var pos = posMs
        var events = listener
        val isSeek = lastPos == Long.MIN_VALUE ||
            pos < lastPos - config.resyncBackwardMs ||
            pos - lastPos > config.resyncForwardMs
        if (isSeek) {
            resync()
            events = null
        } else if (pos < lastPos) {
            pos = lastPos // sub-threshold clock jitter: never run backwards
        }
        lastPos = pos

        // Admit words that are now within their approach window.
        while (hi < n && starts[hi] - config.approachMs <= pos) hi++

        for (i in lo until hi) process(i, pos, events)

        // Retire fully faded words from the front of the window.
        while (lo < hi &&
            states.phase[lo] == WordPhase.MEMORY &&
            states.phaseProgress[lo] >= 1f
        ) lo++

        hint = timeline.activeWordAt(pos, hint)
        activeWord = hint
        activeLine = if (hint >= 0) words[hint].lineIndex else -1
        nextWord = if (hint + 1 < n) hint + 1 else -1
    }

    private fun resync() {
        states.resetTiming()
        firedTier.fill(0)
        lo = 0
        hi = 0
        hint = -1
    }

    private fun process(i: Int, pos: Long, events: WordEventListener?) {
        val s = starts[i]
        val e = ends[i]
        val dur = e - s
        val cfg = config

        val oldPhase = states.phase[i]
        var newPhase: WordPhase
        var progress: Float
        var energy = 0f
        var release = 0f

        val enterEnd = s + minOf(cfg.enterMs, dur / 2)
        val holdStart = s + cfg.holdStartMs
        val activeEnd = if (e < holdStart) e else holdStart
        val exitEnd = e + cfg.exitMs
        val memoryEnd = exitEnd + cfg.memoryMs

        when {
            pos < s - cfg.approachMs -> {
                newPhase = WordPhase.FUTURE; progress = 0f
            }
            pos < s -> {
                newPhase = WordPhase.APPROACHING
                progress = (pos - (s - cfg.approachMs)).toFloat() / cfg.approachMs
            }
            pos < enterEnd -> {
                newPhase = WordPhase.ENTER
                progress = (pos - s).toFloat() / (enterEnd - s)
            }
            pos < activeEnd -> {
                newPhase = WordPhase.ACTIVE
                progress = (pos - enterEnd).toFloat() / (activeEnd - enterEnd).coerceAtLeast(1L)
                energy = energyAt(pos - s)
            }
            pos < e -> {
                newPhase = WordPhase.HOLD
                progress = (pos - holdStart).toFloat() / (e - holdStart).coerceAtLeast(1L)
                energy = energyAt(pos - s)
            }
            pos < exitEnd -> {
                newPhase = WordPhase.EXIT
                progress = (pos - e).toFloat() / cfg.exitMs
                release = energyAt(dur)
                energy = release * (1f - progress)
            }
            pos < memoryEnd -> {
                newPhase = WordPhase.MEMORY
                progress = (pos - exitEnd).toFloat() / cfg.memoryMs
            }
            else -> {
                newPhase = WordPhase.MEMORY; progress = 1f
            }
        }

        states.phase[i] = newPhase
        states.phaseProgress[i] = progress.coerceIn(0f, 1f)
        states.holdEnergy[i] = energy
        states.releaseEnergy[i] = release

        // Hold tiers: announced once while the word is still being sung.
        if (pos in s until e) {
            val held = pos - s
            var tier = firedTier[i]
            if (held >= HoldClass.NORMAL_MAX_MS && tier < 1) {
                tier = 1
                if (events != null) events.onLongHold(i, HoldClass.LONG)
            }
            if (held >= HoldClass.LONG_MAX_MS && tier < 2) {
                tier = 2
                if (events != null) events.onLongHold(i, HoldClass.VERY_LONG)
            }
            firedTier[i] = tier
        } else if (pos >= e && firedTier[i] == 0) {
            // Passed during a silent resync: mark tiers as already spent.
            firedTier[i] = if (dur > HoldClass.LONG_MAX_MS) 2 else if (dur >= HoldClass.NORMAL_MAX_MS) 1 else 0
        }

        if (events != null) {
            val o = oldPhase.ordinal
            val nw = newPhase.ordinal
            if (o < WordPhase.ENTER.ordinal && nw >= WordPhase.ENTER.ordinal) events.onWordEnter(i)
            if (o < WordPhase.ACTIVE.ordinal && nw >= WordPhase.ACTIVE.ordinal) events.onWordActive(i)
            if (o < WordPhase.EXIT.ordinal && nw >= WordPhase.EXIT.ordinal) events.onWordExit(i)
        }
    }

    /** Smoothstep-eased 0..1 energy after [elapsedMs] of a word being sung. */
    private fun energyAt(elapsedMs: Long): Float {
        val raw = (elapsedMs - config.energyStartMs).toFloat() / config.energyRiseMs
        if (raw <= 0f) return 0f
        if (raw >= 1f) return 1f
        return raw * raw * (3f - 2f * raw)
    }
}
