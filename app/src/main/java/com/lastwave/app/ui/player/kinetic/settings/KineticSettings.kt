package com.lastwave.app.ui.player.kinetic.settings

/** Visual presets. All use the same timing engine; only behaviour/look differs. */
enum class KineticMode(val id: String, val label: String) {
    ARK_KINETIC("ARK_KINETIC", "Ark Kinetic"),
    CONSTELLATION("CONSTELLATION", "Constellation"),
    CINEMATIC("CINEMATIC", "Cinematic"),
    FLOATING("FLOATING", "Floating"),
    DEPTH_3D("3D_DEPTH", "3D Depth"),
    STARFIELD("STARFIELD", "Starfield"),
    MINIMAL_AMOLED("MINIMAL_AMOLED", "Minimal AMOLED"),
    BEAT_REACTIVE("BEAT_REACTIVE", "Beat Reactive");

    companion object {
        fun fromId(id: String?): KineticMode = entries.firstOrNull { it.id == id } ?: ARK_KINETIC
    }
}

enum class KineticBackground(val label: String) {
    AMOLED("AMOLED"), CINEMATIC("Cinematic"), DARK("Dark"), CUSTOM("Custom");

    companion object {
        fun fromName(name: String?): KineticBackground =
            entries.firstOrNull { it.name == name } ?: AMOLED
    }
}

/**
 * All user-facing kinetic-lyrics options. Stored as ONE DataStore string via
 * [encode]/[decode] so adding an option never needs new preference plumbing.
 * Unknown/invalid values fall back to defaults and every value is clamped.
 */
data class KineticSettings(
    val enabled: Boolean = true,
    val mode: KineticMode = KineticMode.ARK_KINETIC,
    val animationIntensity: Float = 1f,
    val wordSize: Float = 1f,
    val wordSpacing: Float = 1f,
    val glow: Float = 1f,
    val starDensity: Float = 1f,
    val starSize: Float = 1f,
    val particleDensity: Float = 1f,
    val particleSpeed: Float = 1f,
    val lineIntensity: Float = 1f,
    val depth3d: Float = 1f,
    val cameraMovement: Float = 1f,
    val blur: Float = 1f,
    val backgroundIntensity: Float = 1f,
    val beatReaction: Float = 0.6f,
    val beatEnabled: Boolean = true,
    val activeWordScale: Float = 1f,
    val activeWordGlow: Float = 1f,
    val longWordEffect: Float = 1f,
    val transitionSpeed: Float = 1f,
    val motionReduction: Boolean = false,
    val fpsTarget: Int = 120,
    val background: KineticBackground = KineticBackground.AMOLED,
    val customBackground: Int = 0xFF05060A.toInt(),
    val advanced: Boolean = false,
    val showPerf: Boolean = false,
) {
    /** Multiplier applied to camera motion, drift and orbiting when motion reduction is on. */
    val motionScale: Float get() = if (motionReduction) 0.12f else 1f

    fun encode(): String = buildString {
        fun put(k: String, v: Any) { if (isNotEmpty()) append(';'); append(k).append('=').append(v) }
        put("en", if (enabled) 1 else 0)
        put("mode", mode.id)
        put("int", animationIntensity); put("wsz", wordSize); put("wsp", wordSpacing)
        put("glow", glow); put("sd", starDensity); put("ss", starSize)
        put("pd", particleDensity); put("ps", particleSpeed); put("li", lineIntensity)
        put("d3", depth3d); put("cam", cameraMovement); put("blur", blur)
        put("bgi", backgroundIntensity); put("br", beatReaction)
        put("be", if (beatEnabled) 1 else 0)
        put("aws", activeWordScale); put("awg", activeWordGlow)
        put("lwe", longWordEffect); put("ts", transitionSpeed)
        put("mr", if (motionReduction) 1 else 0)
        put("fps", fpsTarget); put("bg", background.name); put("bgc", customBackground)
        put("adv", if (advanced) 1 else 0); put("perf", if (showPerf) 1 else 0)
    }

    companion object {
        val Default = KineticSettings()
        val FPS_CHOICES = intArrayOf(60, 90, 120)

        fun decode(raw: String?): KineticSettings {
            if (raw.isNullOrBlank()) return Default
            val m = HashMap<String, String>()
            for (part in raw.split(';')) {
                val i = part.indexOf('=')
                if (i > 0) m[part.substring(0, i)] = part.substring(i + 1)
            }
            val d = Default
            fun f(k: String, def: Float, lo: Float, hi: Float): Float =
                (m[k]?.toFloatOrNull()?.takeIf { it.isFinite() } ?: def).coerceIn(lo, hi)
            fun b(k: String, def: Boolean): Boolean = m[k]?.let { it == "1" } ?: def
            return KineticSettings(
                enabled = b("en", d.enabled),
                mode = KineticMode.fromId(m["mode"]),
                animationIntensity = f("int", d.animationIntensity, 0.3f, 1.5f),
                wordSize = f("wsz", d.wordSize, 0.6f, 1.5f),
                wordSpacing = f("wsp", d.wordSpacing, 0.7f, 1.6f),
                glow = f("glow", d.glow, 0f, 1.6f),
                starDensity = f("sd", d.starDensity, 0f, 2f),
                starSize = f("ss", d.starSize, 0.5f, 2f),
                particleDensity = f("pd", d.particleDensity, 0f, 2f),
                particleSpeed = f("ps", d.particleSpeed, 0.4f, 2f),
                lineIntensity = f("li", d.lineIntensity, 0f, 2f),
                depth3d = f("d3", d.depth3d, 0f, 1.6f),
                cameraMovement = f("cam", d.cameraMovement, 0f, 1.6f),
                blur = f("blur", d.blur, 0f, 1.6f),
                backgroundIntensity = f("bgi", d.backgroundIntensity, 0f, 1.6f),
                beatReaction = f("br", d.beatReaction, 0f, 1.6f),
                beatEnabled = b("be", d.beatEnabled),
                activeWordScale = f("aws", d.activeWordScale, 0.8f, 1.5f),
                activeWordGlow = f("awg", d.activeWordGlow, 0f, 1.6f),
                longWordEffect = f("lwe", d.longWordEffect, 0f, 1.6f),
                transitionSpeed = f("ts", d.transitionSpeed, 0.5f, 2f),
                motionReduction = b("mr", d.motionReduction),
                fpsTarget = (m["fps"]?.toIntOrNull() ?: d.fpsTarget).let { v ->
                    FPS_CHOICES.minByOrNull { kotlin.math.abs(it - v) } ?: 120
                },
                background = KineticBackground.fromName(m["bg"]),
                customBackground = m["bgc"]?.toIntOrNull() ?: d.customBackground,
                advanced = b("adv", d.advanced),
                showPerf = b("perf", d.showPerf),
            )
        }
    }
}
