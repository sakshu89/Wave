package com.lastwave.app.ui.player.kinetic.render

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.lastwave.app.data.lyrics.LyricLine
import com.lastwave.app.ui.player.kinetic.anim.AnimationEngine
import com.lastwave.app.ui.player.kinetic.model.LyricWordTimeline
import com.lastwave.app.ui.player.kinetic.settings.KineticSettings

/**
 * Compose edge of the kinetic lyric engine. It owns no clock: [positionMs] is the
 * playback position LastWave's existing frame clock already provides (lyric offset
 * included). Each vsync, `withFrameNanos` advances the engine once and bumps a single
 * state read ONLY inside the draw block, so frames redraw without recomposition.
 *
 * Frames are throttled to the FPS target (60/90/120) while playing and to ~30 fps
 * while paused (ambient stars keep drifting).
 */
@Composable
fun KineticLyricsView(
    lines: List<LyricLine>,
    trackKey: String,
    positionMs: () -> Long,
    isPlaying: Boolean,
    settings: KineticSettings,
    primary: Color,
    secondary: Color,
    tertiary: Color,
    fontScale: Float,
    modifier: Modifier = Modifier,
) {
    val engine = remember(lines, trackKey) {
        AnimationEngine(
            timeline = LyricWordTimeline.build(lines, trackKey),
            seed = trackKey.hashCode() xor lines.size,
            settings = settings,
        )
    }
    val renderer = remember { KineticRenderer() }
    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    val frameTick = remember { mutableLongStateOf(0L) }

    val latestPosition by rememberUpdatedState(positionMs)
    val playing by rememberUpdatedState(isPlaying)

    LaunchedEffect(engine, settings) { engine.applySettings(settings) }

    val c1 = primary.toArgb()
    val c2 = secondary.toArgb()
    val c3 = tertiary.toArgb()
    LaunchedEffect(engine, c1, c2, c3) { engine.setPalette(c1, c2, c3) }

    LaunchedEffect(engine, size, density, fontScale, settings.wordSize, settings.wordSpacing) {
        if (size.width > 0 && size.height > 0) {
            renderer.setTextSize(engine.baseSizePx(density, fontScale))
            engine.ensureLayout(size.width.toFloat(), size.height.toFloat(), density, fontScale, renderer.measurer)
        }
    }

    val fps = settings.fpsTarget.coerceIn(30, 144)
    LaunchedEffect(engine, fps) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val interval = if (playing) 1_000_000_000L / fps else 33_333_333L
                if (last != 0L && now - last < interval * 92L / 100L) return@withFrameNanos
                last = now
                engine.frame(latestPosition(), now)
                frameTick.longValue = now
            }
        }
    }

    Canvas(modifier = modifier.clipToBounds().onSizeChanged { size = it }) {
        val stamp = frameTick.longValue // the only per-frame state read: invalidates draw, not composition
        drawIntoCanvas { renderer.draw(it.nativeCanvas, engine, stamp) }
    }
}
