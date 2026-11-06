package com.lastwave.app.ui.player.kinetic.anim

import com.lastwave.app.ui.player.kinetic.model.HoldClass
import com.lastwave.app.ui.player.kinetic.model.LyricWord
import com.lastwave.app.ui.player.kinetic.scene.Ease
import com.lastwave.app.ui.player.kinetic.scene.Hash
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** Per-word entrance/exit behaviours. Which one a word gets is deterministic (see [BehaviourSelector]). */
enum class Behaviour {
    FADE, SCALE, SLIDE, FLOAT, DRIFT, ROTATE, ORBIT, STRETCH, BLUR_TO_SHARP,
    DEPTH_PUSH, DEPTH_PULL, PARTICLE_DISSOLVE, PARTICLE_BURST, WAVE, ELASTIC, SPRING,
    CINEMATIC_REVEAL, CONSTELLATION_FORMATION;

    companion object {
        val ALL: Array<Behaviour> = entries.toTypedArray()
    }
}

/** Reusable output of a behaviour: additive offsets and multipliers applied on top of the phase targets. */
class Mods {
    var dx = 0f
    var dy = 0f
    var dz = 0f
    var rotZ = 0f
    var rotY = 0f
    var scaleMul = 1f
    var stretch = 1f
    var blurAdd = 0f
    var alphaMul = 1f

    /** Multiplier for the particle burst emitted when this word enters. */
    var burst = 1f

    fun clear() {
        dx = 0f; dy = 0f; dz = 0f; rotZ = 0f; rotY = 0f
        scaleMul = 1f; stretch = 1f; blurAdd = 0f; alphaMul = 1f; burst = 1f
    }
}

object BehaviourSelector {

    /** Relative preference per hold class, indexed by [Behaviour.ordinal]. */
    private val shortW = floatArrayOf(
        1.0f, 1.2f, 1.2f, 0.8f, 0.8f, 0.6f, 0.2f, 0.5f, 1.2f,
        0.9f, 0.6f, 0.7f, 1.0f, 0.6f, 1.2f, 1.0f, 0.3f, 0.3f,
    )
    private val normalW = floatArrayOf(
        0.8f, 1.0f, 1.0f, 1.0f, 1.0f, 0.9f, 0.5f, 0.8f, 1.0f,
        1.0f, 0.9f, 0.9f, 0.9f, 0.9f, 1.0f, 1.0f, 0.8f, 0.7f,
    )
    private val longW = floatArrayOf(
        0.4f, 0.8f, 0.5f, 1.0f, 1.0f, 1.1f, 1.4f, 1.1f, 0.9f,
        0.8f, 1.1f, 1.1f, 0.9f, 1.0f, 0.8f, 0.9f, 1.5f, 1.4f,
    )

    /**
     * Deterministic choice from the word's seed, duration class and importance.
     * [allowed] restricts the pool (a preset's palette of behaviours). Never changes between frames.
     */
    fun choose(word: LyricWord, allowed: Array<Behaviour>, lineWordCount: Int = 0): Behaviour {
        if (allowed.isEmpty()) return Behaviour.FADE
        val table = when (word.hold) {
            HoldClass.SHORT -> shortW
            HoldClass.NORMAL -> normalW
            else -> longW
        }
        var total = 0f
        for (b in allowed) total += weight(table, b, word, lineWordCount)
        var pick = Hash.hash01(word.seed, 0x51) * total
        for (b in allowed) {
            pick -= weight(table, b, word, lineWordCount)
            if (pick <= 0f) return b
        }
        return allowed.last()
    }

    private fun weight(table: FloatArray, b: Behaviour, w: LyricWord, lineWordCount: Int): Float {
        var x = table[b.ordinal]
        // Important words lean toward dramatic entrances; line openers toward reveals.
        if (w.importance > 0.65f && (b == Behaviour.DEPTH_PULL || b == Behaviour.CINEMATIC_REVEAL ||
                b == Behaviour.PARTICLE_BURST || b == Behaviour.ELASTIC)
        ) x *= 1.6f
        if (w.wordIndex == 0 && (b == Behaviour.CINEMATIC_REVEAL || b == Behaviour.BLUR_TO_SHARP)) x *= 1.5f
        if (lineWordCount > 0 && w.wordIndex == lineWordCount - 1 && b == Behaviour.PARTICLE_DISSOLVE) x *= 1.4f
        return x
    }
}

object BehaviourFx {
    private const val TAU = (2.0 * PI).toFloat()

    /**
     * Entrance transform. [t] = 0 when far/unannounced .. 1 when arrived; [amt] scales displacement
     * (animation intensity * motion scale). At t == 1 everything is neutral, so there is never a jump.
     */
    fun enter(b: Behaviour, seed: Int, t: Float, amt: Float, out: Mods) {
        val e = Ease.out3(t)
        val u = 1f - e
        val dir = if (Hash.hash01(seed, 1) < 0.5f) -1f else 1f
        val ang = Hash.hash01(seed, 2) * TAU
        when (b) {
            Behaviour.FADE -> out.alphaMul = 0.25f + 0.75f * e
            Behaviour.SCALE -> out.scaleMul = 1f - 0.35f * u
            Behaviour.SLIDE -> { out.dx = dir * 72f * u * amt }
            Behaviour.FLOAT -> { out.dy = 40f * u * amt }
            Behaviour.DRIFT -> { out.dx = cos(ang) * 26f * u * amt; out.dy = sin(ang) * 26f * u * amt }
            Behaviour.ROTATE -> { out.rotZ = dir * 0.38f * u * amt }
            Behaviour.ORBIT -> {
                val a = ang + u * 4.2f
                out.dx = cos(a) * 34f * u * amt; out.dy = sin(a) * 20f * u * amt
            }
            Behaviour.STRETCH -> { out.stretch = 1f + 0.35f * u; out.scaleMul = 1f - 0.08f * u }
            Behaviour.BLUR_TO_SHARP -> { out.blurAdd = 16f * u }
            Behaviour.DEPTH_PUSH -> { out.dz = 280f * u * amt; out.alphaMul = 0.4f + 0.6f * e }
            Behaviour.DEPTH_PULL -> { out.dz = -230f * u * amt; out.alphaMul = 0.5f + 0.5f * e }
            Behaviour.PARTICLE_DISSOLVE -> { out.alphaMul = e * e; out.blurAdd = 10f * u; out.burst = 1.5f }
            Behaviour.PARTICLE_BURST -> { out.scaleMul = 1f - 0.2f * u; out.burst = 2.2f }
            Behaviour.WAVE -> { out.dy = sin(u * 7f + ang) * 16f * u * amt; out.rotZ = sin(u * 5f + ang) * 0.06f * u * amt }
            Behaviour.ELASTIC -> { out.scaleMul = 0.55f + 0.45f * Ease.elastic(t) }
            Behaviour.SPRING -> { out.dy = 34f * (1f - Ease.spring(t)) * amt }
            Behaviour.CINEMATIC_REVEAL -> {
                out.stretch = 1f + 0.20f * u; out.blurAdd = 11f * u; out.dz = 150f * u * amt
                out.alphaMul = 0.3f + 0.7f * e
            }
            Behaviour.CONSTELLATION_FORMATION -> {
                out.dx = cos(ang) * 90f * u * amt; out.dy = sin(ang) * 70f * u * amt
                out.alphaMul = 0.3f + 0.7f * e
            }
        }
    }

    /** Exit drift; [t] = 0 at release .. 1 fully gone. Continuous at t == 0 (zero offset). */
    fun exit(b: Behaviour, seed: Int, t: Float, amt: Float, out: Mods) {
        val e = Ease.smooth(t)
        val dir = if (Hash.hash01(seed, 3) < 0.5f) -1f else 1f
        val ang = Hash.hash01(seed, 4) * TAU
        var dx = cos(ang) * 30f * e
        var dy = -18f * e + sin(ang) * 12f * e
        when (b) {
            Behaviour.SLIDE -> { dx = dir * 60f * e; dy = 0f }
            Behaviour.FLOAT, Behaviour.SPRING -> { dx = 0f; dy = -42f * e }
            Behaviour.ROTATE -> out.rotZ = -dir * 0.22f * e * amt
            Behaviour.DEPTH_PUSH, Behaviour.DEPTH_PULL -> { dx *= 0.4f; dy *= 0.4f }
            Behaviour.PARTICLE_DISSOLVE, Behaviour.PARTICLE_BURST -> { out.blurAdd = 6f * e; dy = -10f * e }
            Behaviour.ORBIT -> { dx = cos(ang + e * 2f) * 44f * e; dy = sin(ang + e * 2f) * 24f * e }
            else -> {}
        }
        out.dx += dx * amt
        out.dy += dy * amt
    }
}
