package com.lastwave.app.ui.player.kinetic.fx

import kotlin.math.exp

/**
 * Lightweight, switchable beat layer producing a decaying 0..1 [pulse].
 *
 * LastWave exposes no live audio beat to the UI, so there are two sources:
 *  1. Vocal rhythm (default): each word onset calls [kick], giving a gentle pulse
 *     that follows the phrasing. This is NOT an audio beat tracker and is
 *     documented as such.
 *  2. Audio level onsets (optional): feed a 0..1 loudness/peak value (e.g. from
 *     playback/analysis OutputMeterState) to [feedLevel]; onsets above a slow
 *     running average trigger a kick.
 *
 * Reactions must stay subtle; consumers scale [pulse] by their own small factors.
 */
class BeatDetector {
    var enabled = true
    var pulse = 0f; private set
    private var avg = 0f
    private var cooldown = 0f

    fun kick(strength: Float) {
        if (!enabled) return
        val s = strength.coerceIn(0f, 1f)
        if (s > pulse) pulse = s
    }

    fun feedLevel(level: Float, dt: Float) {
        if (!enabled) return
        val l = level.coerceIn(0f, 1f)
        cooldown -= dt
        if (cooldown <= 0f && l > avg * 1.35f + 0.03f) {
            kick(0.5f + 0.5f * (l - avg).coerceIn(0f, 1f))
            cooldown = 0.18f
        }
        avg += (l - avg) * (1f - exp(-dt * 2.2f))
    }

    fun step(dt: Float) {
        pulse *= exp(-dt * 5.5f)
        if (pulse < 0.002f) pulse = 0f
        if (!enabled) pulse = 0f
    }

    fun reset() { pulse = 0f; avg = 0f; cooldown = 0f }
}
