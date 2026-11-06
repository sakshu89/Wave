package com.lastwave.app.ui.player.kinetic.scene

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Subtle cinematic camera: slow drift, small yaw/pitch, parallax and a gentle
 * push-in when a held word builds energy. All amplitudes are tiny and scaled by
 * the camera-movement setting and motion reduction, so it can never be nauseating.
 *
 * [project] is allocation-free: results land in [projX]/[projY]/[projScale].
 */
class CameraController {
    var focal = 900f
    var cx = 0f
    var cy = 0f

    /** Accumulated scene time in seconds (frame-time driven, so drift continues while paused). */
    var time = 0f
        private set

    var yaw = 0f; private set
    var pitch = 0f; private set
    var offX = 0f; private set
    var offY = 0f; private set
    var zoom = 1f; private set

    var projX = 0f; private set
    var projY = 0f; private set
    var projScale = 0f; private set

    private var sinYaw = 0f
    private var cosYaw = 1f
    private var sinPitch = 0f
    private var cosPitch = 1f

    /**
     * @param amount camera-movement setting * motion scale * preset factor (0 disables motion)
     * @param energy 0..1 hold energy of the hero word (push-in)
     * @param beat 0..1 beat pulse (tiny zoom kick)
     */
    fun update(dt: Float, amount: Float, energy: Float, beat: Float) {
        time += dt
        val t = time
        val a = amount.coerceIn(0f, 2f)
        val ty = (sin(t * 0.11f) * 0.034f + sin(t * 0.047f + 1.3f) * 0.020f) * a
        val tp = sin(t * 0.083f + 0.7f) * 0.022f * a
        val tx = (sin(t * 0.07f) * 16f + sin(t * 0.19f + 2f) * 5f) * a
        val tyOff = (cos(t * 0.05f) * 10f + sin(t * 0.13f) * 4f) * a
        val tz = 1f + (0.030f * energy + 0.010f * beat) * a.coerceAtMost(1.2f)
        val k = 1f - exp(-dt * 5f)
        yaw += (ty - yaw) * k
        pitch += (tp - pitch) * k
        offX += (tx - offX) * k
        offY += (tyOff - offY) * k
        zoom += (tz - zoom) * (1f - exp(-dt * 3f))
        sinYaw = sin(yaw); cosYaw = cos(yaw)
        sinPitch = sin(pitch); cosPitch = cos(pitch)
    }

    fun snapToIdle() {
        yaw = 0f; pitch = 0f; offX = 0f; offY = 0f; zoom = 1f
        sinYaw = 0f; cosYaw = 1f; sinPitch = 0f; cosPitch = 1f
    }

    /**
     * Perspective-project a world point (x right, y down, z away from camera).
     * If the point is behind the near plane, [projScale] is 0.
     */
    fun project(x: Float, y: Float, z: Float) {
        val px = x - offX
        val py = y - offY
        val xr = px * cosYaw + z * sinYaw
        val zr = -px * sinYaw + z * cosYaw
        val yr = py * cosPitch - zr * sinPitch
        val zr2 = py * sinPitch + zr * cosPitch
        val depth = focal + zr2
        if (depth < 60f) {
            projScale = 0f; projX = cx; projY = cy
            return
        }
        val s = focal / depth * zoom
        projScale = s
        projX = cx + xr * s
        projY = cy + yr * s
    }
}
