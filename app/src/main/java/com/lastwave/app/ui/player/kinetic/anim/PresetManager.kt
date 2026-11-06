package com.lastwave.app.ui.player.kinetic.anim

import com.lastwave.app.ui.player.kinetic.anim.Behaviour.BLUR_TO_SHARP
import com.lastwave.app.ui.player.kinetic.anim.Behaviour.CINEMATIC_REVEAL
import com.lastwave.app.ui.player.kinetic.anim.Behaviour.CONSTELLATION_FORMATION
import com.lastwave.app.ui.player.kinetic.anim.Behaviour.DEPTH_PULL
import com.lastwave.app.ui.player.kinetic.anim.Behaviour.DEPTH_PUSH
import com.lastwave.app.ui.player.kinetic.anim.Behaviour.DRIFT
import com.lastwave.app.ui.player.kinetic.anim.Behaviour.ELASTIC
import com.lastwave.app.ui.player.kinetic.anim.Behaviour.FADE
import com.lastwave.app.ui.player.kinetic.anim.Behaviour.FLOAT
import com.lastwave.app.ui.player.kinetic.anim.Behaviour.ORBIT
import com.lastwave.app.ui.player.kinetic.anim.Behaviour.PARTICLE_BURST
import com.lastwave.app.ui.player.kinetic.anim.Behaviour.PARTICLE_DISSOLVE
import com.lastwave.app.ui.player.kinetic.anim.Behaviour.ROTATE
import com.lastwave.app.ui.player.kinetic.anim.Behaviour.SCALE
import com.lastwave.app.ui.player.kinetic.anim.Behaviour.SLIDE
import com.lastwave.app.ui.player.kinetic.anim.Behaviour.SPRING
import com.lastwave.app.ui.player.kinetic.anim.Behaviour.STRETCH
import com.lastwave.app.ui.player.kinetic.anim.Behaviour.WAVE
import com.lastwave.app.ui.player.kinetic.scene.SceneContext
import com.lastwave.app.ui.player.kinetic.settings.KineticMode
import kotlin.math.min

/**
 * ARK_KINETIC: the primary preset. Elegant, designed motion: words arrive from depth with
 * blur-to-sharp / reveal / spring entrances, the hero word sits forward with a soft glow
 * and halo, neighbouring lines recede, and star/particle motion is sparse and supportive.
 * The extra layer on top of the shared lifecycle is a soft "light cast" halo plus a faint
 * halo on the following word, so the eye is led from word to word.
 */
class ArkKineticPreset : BasePreset(
    PresetProfile(
        mode = KineticMode.ARK_KINETIC,
        allowed = arrayOf(
            CINEMATIC_REVEAL, BLUR_TO_SHARP, DEPTH_PULL, DEPTH_PUSH, FLOAT, SPRING, ELASTIC,
            DRIFT, SCALE, SLIDE, PARTICLE_DISSOLVE, WAVE, STRETCH, PARTICLE_BURST,
        ),
    ),
) {
    override fun render(ctx: SceneContext, canvas: PresetCanvas) {
        super.render(ctx, canvas)
        val nw = ctx.timing.nextWord
        if (nw < 0 || ctx.heroWord < 0) return
        val st = ctx.states
        if (nw !in ctx.winLo until ctx.winHi) return
        val prog = st.phaseProgress[nw]
        if (st.phase[nw].ordinal > 1) return // only while FUTURE/APPROACHING
        val a = 0.05f * ctx.settings.glow * min(1f, prog * 1.5f)
        val r = (ctx.layout.width[nw] * 0.5f + 60f * ctx.density) * ctx.screenScale[nw]
        canvas.disc(ctx.screenX[nw], ctx.screenY[nw], r, ctx.palette.glow, a)
    }
}

class ConstellationPreset : BasePreset(
    PresetProfile(
        mode = KineticMode.CONSTELLATION,
        allowed = arrayOf(CONSTELLATION_FORMATION, DRIFT, ORBIT, FADE, FLOAT, BLUR_TO_SHARP, DEPTH_PUSH),
        memoryAlpha = 0.38f, glow = 0.8f, stars = 0.8f, particles = 0.6f,
        constellation = 1.9f, memoryStars = 1.6f, wander = 1.3f,
    ),
)

class CinematicPreset : BasePreset(
    PresetProfile(
        mode = KineticMode.CINEMATIC,
        allowed = arrayOf(CINEMATIC_REVEAL, BLUR_TO_SHARP, DEPTH_PUSH, STRETCH, FADE, SCALE),
        heroScale = 1.22f, glow = 1.1f, depth = 1.1f, camera = 1.2f, stars = 0.5f, particles = 0.4f,
        constellation = 0.3f, atmosphere = 1.4f, lineJitter = 0.2f, wander = 0.4f, speed = 0.8f,
    ),
)

class FloatingPreset : BasePreset(
    PresetProfile(
        mode = KineticMode.FLOATING,
        allowed = arrayOf(FLOAT, DRIFT, WAVE, SPRING, SLIDE, FADE, ROTATE),
        depth = 0.7f, camera = 0.8f, stars = 0.8f, wander = 2.4f, lineJitter = 1.6f,
    ),
)

class Depth3dPreset : BasePreset(
    PresetProfile(
        mode = KineticMode.DEPTH_3D,
        allowed = arrayOf(DEPTH_PUSH, DEPTH_PULL, ROTATE, STRETCH, ELASTIC, SCALE, ORBIT),
        heroScale = 1.15f, depth = 1.7f, camera = 1.5f, blur = 1.3f, constellation = 0.6f,
    ),
)

class StarfieldPreset : BasePreset(
    PresetProfile(
        mode = KineticMode.STARFIELD,
        allowed = arrayOf(PARTICLE_DISSOLVE, PARTICLE_BURST, ORBIT, FADE, DRIFT, BLUR_TO_SHARP),
        stars = 2f, starBright = 1.4f, particles = 1.6f, constellation = 0.7f,
        memoryStars = 1.8f, orbit = 1.6f, atmosphere = 0.7f,
    ),
)

class MinimalAmoledPreset : BasePreset(
    PresetProfile(
        mode = KineticMode.MINIMAL_AMOLED,
        allowed = arrayOf(FADE, BLUR_TO_SHARP, SCALE, FLOAT),
        memoryAlpha = 0.22f, glow = 0.5f, depth = 0.35f, camera = 0.3f, stars = 0.15f,
        particles = 0.2f, constellation = 0f, atmosphere = 0f, wander = 0.2f, orbit = 0f,
    ),
)

/**
 * BEAT_REACTIVE: the same lifecycle with a stronger beat response. The beat is still
 * capped by the shared subtle scale factors, so the lyric never becomes an equalizer.
 */
class BeatReactivePreset : BasePreset(
    PresetProfile(
        mode = KineticMode.BEAT_REACTIVE,
        allowed = arrayOf(ELASTIC, SPRING, SCALE, PARTICLE_BURST, WAVE, DEPTH_PULL, STRETCH),
        glow = 1.2f, stars = 1.1f, particles = 1.3f, beat = 2.2f,
    ),
)

object PresetManager {
    fun create(mode: KineticMode): AnimationPreset = when (mode) {
        KineticMode.ARK_KINETIC -> ArkKineticPreset()
        KineticMode.CONSTELLATION -> ConstellationPreset()
        KineticMode.CINEMATIC -> CinematicPreset()
        KineticMode.FLOATING -> FloatingPreset()
        KineticMode.DEPTH_3D -> Depth3dPreset()
        KineticMode.STARFIELD -> StarfieldPreset()
        KineticMode.MINIMAL_AMOLED -> MinimalAmoledPreset()
        KineticMode.BEAT_REACTIVE -> BeatReactivePreset()
    }
}
