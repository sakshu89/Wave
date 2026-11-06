package com.lastwave.app.ui.player.kinetic.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private class Knob(
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val get: (KineticSettings) -> Float,
    val set: (KineticSettings, Float) -> KineticSettings,
)

private val simpleKnobs = listOf(
    Knob("Animation intensity", 0.3f..1.5f, { it.animationIntensity }, { s, v -> s.copy(animationIntensity = v) }),
    Knob("Word size", 0.6f..1.5f, { it.wordSize }, { s, v -> s.copy(wordSize = v) }),
)

private val advancedKnobs = listOf(
    Knob("Word spacing", 0.7f..1.6f, { it.wordSpacing }, { s, v -> s.copy(wordSpacing = v) }),
    Knob("Glow", 0f..1.6f, { it.glow }, { s, v -> s.copy(glow = v) }),
    Knob("Active-word glow", 0f..1.6f, { it.activeWordGlow }, { s, v -> s.copy(activeWordGlow = v) }),
    Knob("Active-word scale", 0.8f..1.5f, { it.activeWordScale }, { s, v -> s.copy(activeWordScale = v) }),
    Knob("Long-word effect", 0f..1.6f, { it.longWordEffect }, { s, v -> s.copy(longWordEffect = v) }),
    Knob("Star density", 0f..2f, { it.starDensity }, { s, v -> s.copy(starDensity = v) }),
    Knob("Star size", 0.5f..2f, { it.starSize }, { s, v -> s.copy(starSize = v) }),
    Knob("Particle density", 0f..2f, { it.particleDensity }, { s, v -> s.copy(particleDensity = v) }),
    Knob("Particle speed", 0.4f..2f, { it.particleSpeed }, { s, v -> s.copy(particleSpeed = v) }),
    Knob("Constellation lines", 0f..2f, { it.lineIntensity }, { s, v -> s.copy(lineIntensity = v) }),
    Knob("3D depth", 0f..1.6f, { it.depth3d }, { s, v -> s.copy(depth3d = v) }),
    Knob("Camera movement", 0f..1.6f, { it.cameraMovement }, { s, v -> s.copy(cameraMovement = v) }),
    Knob("Blur", 0f..1.6f, { it.blur }, { s, v -> s.copy(blur = v) }),
    Knob("Background intensity", 0f..1.6f, { it.backgroundIntensity }, { s, v -> s.copy(backgroundIntensity = v) }),
    Knob("Beat reaction", 0f..1.6f, { it.beatReaction }, { s, v -> s.copy(beatReaction = v) }),
    Knob("Transition speed", 0.5f..2f, { it.transitionSpeed }, { s, v -> s.copy(transitionSpeed = v) }),
)

private val customSwatches = intArrayOf(
    0xFF05060A.toInt(), 0xFF0A0614.toInt(), 0xFF04101A.toInt(), 0xFF140608.toInt(), 0xFF06140C.toInt(),
)

/** Kinetic-lyrics settings. Simple mode shows the essentials; Advanced reveals every option. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KineticSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val current by KineticSettingsStore.state.collectAsState()
    val s = current ?: KineticSettings.Default
    fun change(t: (KineticSettings) -> KineticSettings) = KineticSettingsStore.update(context, t)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 640.dp).padding(vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Kinetic Lyrics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                SwitchRow("Kinetic lyric visualizer", s.enabled) { v -> change { it.copy(enabled = v) } }

                if (s.enabled) {
                    Text("Mode", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        KineticMode.entries.forEach { m ->
                            FilterChip(selected = s.mode == m, onClick = { change { it.copy(mode = m) } }, label = { Text(m.label) })
                        }
                    }

                    simpleKnobs.forEach { k -> KnobRow(k, s) { v -> change { cur -> k.set(cur, v) } } }

                    SwitchRow("Motion reduction", s.motionReduction) { v -> change { it.copy(motionReduction = v) } }

                    Text("Background", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KineticBackground.entries.forEach { b ->
                            FilterChip(selected = s.background == b, onClick = { change { it.copy(background = b) } }, label = { Text(b.label) })
                        }
                    }
                    if (s.background == KineticBackground.CUSTOM) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            customSwatches.forEach { c ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = androidx.compose.ui.graphics.Color(c),
                                    tonalElevation = 0.dp,
                                    border = androidx.compose.foundation.BorderStroke(
                                        if (s.customBackground == c) 2.dp else 1.dp,
                                        if (s.customBackground == c) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    ),
                                    modifier = Modifier.clickable { change { it.copy(customBackground = c) } },
                                ) { Text("   ", modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) }
                            }
                        }
                    }

                    SwitchRow("Beat reaction layer", s.beatEnabled) { v -> change { it.copy(beatEnabled = v) } }

                    SwitchRow("Advanced options", s.advanced) { v -> change { it.copy(advanced = v) } }
                    if (s.advanced) {
                        advancedKnobs.forEach { k -> KnobRow(k, s) { v -> change { cur -> k.set(cur, v) } } }
                        Text("FPS target", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KineticSettings.FPS_CHOICES.forEach { f ->
                                FilterChip(selected = s.fpsTarget == f, onClick = { change { it.copy(fpsTarget = f) } }, label = { Text("$f") })
                            }
                        }
                        SwitchRow("Performance overlay (dev)", s.showPerf) { v -> change { it.copy(showPerf = v) } }
                        TextButton(onClick = { change { KineticSettings.Default.copy(advanced = true) } }) {
                            Text("Reset to defaults")
                        }
                    }
                }

                FilledTonalButton(onClick = onDismiss, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun KnobRow(k: Knob, s: KineticSettings, onChange: (Float) -> Unit) {
    val value = k.get(s)
    var dragging by remember { mutableStateOf<Float?>(null) }
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(k.label, style = MaterialTheme.typography.bodyMedium)
            Text("%.2f".format(dragging ?: value), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = dragging ?: value,
            onValueChange = { dragging = it; onChange(it) },
            onValueChangeFinished = { dragging = null },
            valueRange = k.range,
        )
    }
}
