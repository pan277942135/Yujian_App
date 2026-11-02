package com.yujian.ai.ui.designsystem.lakeglass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun LakeGlassShaderLabScreen(
    initialMode: String = "static",
    viewportOnly: Boolean = false,
) {
    val initialParameters = remember(initialMode) {
        when (initialMode) {
            "caustics" -> LakeGlassEffectParameters.STATIC_DEFAULT.copy(causticsEnabled = true)
            "sunlight" -> LakeGlassEffectParameters.STATIC_DEFAULT.copy(sunlightEnabled = true)
            "droplets" -> LakeGlassEffectParameters.STATIC_DEFAULT.copy(dropletsEnabled = true)
            "combined" -> LakeGlassEffectParameters.STATIC_DEFAULT.copy(
                causticsEnabled = true,
                sunlightEnabled = true,
                dropletsEnabled = true,
                motionEnabled = true,
            )
            else -> LakeGlassEffectParameters.STATIC_DEFAULT
        }
    }
    var parameters by remember(initialMode) { mutableStateOf(initialParameters) }
    var firstFrameSummary by remember { mutableStateOf("Waiting for static scene decode") }
    val controls = parameters.bounded()

    if (viewportOnly) {
        LakeGlassBackgroundHost(
            modifier = Modifier.fillMaxSize(),
            effectParameters = controls,
            allowOptionalAgsl = Build.VERSION.SDK_INT >= 33 && controls.anyEffectEnabled,
        )
        return
    }

    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xFFF2F6F4)),
        ) {
            Column(Modifier.weight(0.52f).fillMaxWidth()) {
                LakeGlassBackgroundHost(
                    modifier = Modifier.fillMaxSize(),
                    effectParameters = controls,
                    allowOptionalAgsl = Build.VERSION.SDK_INT >= 33 && controls.anyEffectEnabled,
                    onFirstFrame = { decodeMs, firstDisplayMs, bitmapBytes ->
                        firstFrameSummary = "decode ${decodeMs}ms · first draw ${firstDisplayMs}ms · bitmap ${bitmapBytes / 1024} KiB"
                    },
                )
            }
            Column(
                modifier = Modifier
                    .weight(0.48f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Lake Glass V2 · Shader Lab", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (Build.VERSION.SDK_INT >= 33) "API ${Build.VERSION.SDK_INT} · AGSL optional" else "API ${Build.VERSION.SDK_INT} · static fallback",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(firstFrameSummary, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { parameters = LakeGlassEffectParameters.STATIC_DEFAULT }) { Text("Static") }
                    Button(onClick = {
                        parameters = parameters.copy(causticsEnabled = true, sunlightEnabled = false, dropletsEnabled = false)
                    }) { Text("Caustics only") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        parameters = parameters.copy(causticsEnabled = false, sunlightEnabled = true, dropletsEnabled = false)
                    }) { Text("Sunlight only") }
                    Button(onClick = {
                        parameters = parameters.copy(causticsEnabled = false, sunlightEnabled = false, dropletsEnabled = true)
                    }) { Text("Droplets only") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        parameters = parameters.copy(causticsEnabled = true, sunlightEnabled = true, dropletsEnabled = true)
                    }) { Text("All effects") }
                    Button(onClick = {
                        parameters = parameters.copy(
                            causticsEnabled = true,
                            sunlightEnabled = true,
                            dropletsEnabled = true,
                            motionEnabled = true,
                        )
                    }) { Text("Animate all") }
                }
                SwitchRow(title = "Animate", checked = parameters.motionEnabled) {
                    parameters = parameters.copy(motionEnabled = it)
                }
                EffectSwitch("Caustics · in-place baked-luma modulation", parameters.causticsEnabled) {
                    parameters = parameters.copy(causticsEnabled = it)
                }
                AmountSlider(
                    title = "Caustic amplitude",
                    value = parameters.causticsAmount,
                    range = 0f..0.035f,
                    onValue = { parameters = parameters.copy(causticsAmount = it) },
                )
                EffectSwitch("Sunlight · fixed upper-right source", parameters.sunlightEnabled) {
                    parameters = parameters.copy(sunlightEnabled = it)
                }
                AmountSlider(
                    title = "Sunlight modulation",
                    value = parameters.sunlightAmount,
                    range = 0f..0.018f,
                    onValue = { parameters = parameters.copy(sunlightAmount = it) },
                )
                EffectSwitch("Droplets · screen-space refraction", parameters.dropletsEnabled) {
                    parameters = parameters.copy(dropletsEnabled = it)
                }
                AmountSlider(
                    title = "Refraction distance (Master px)",
                    value = parameters.dropletAmount,
                    range = 0f..12f,
                    onValue = { parameters = parameters.copy(dropletAmount = it) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SwitchRow(Modifier.weight(1f), "Left", parameters.drop01Enabled) { parameters = parameters.copy(drop01Enabled = it) }
                    SwitchRow(Modifier.weight(1f), "Upper right", parameters.drop02Enabled) { parameters = parameters.copy(drop02Enabled = it) }
                    SwitchRow(Modifier.weight(1f), "Lower right", parameters.drop03Enabled) { parameters = parameters.copy(drop03Enabled = it) }
                }
                Text(
                    "Static Master is the only visible scene. Diagnostic RGBA layers are never drawn. Motion is off by default.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun EffectSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SwitchRow(Modifier, title, checked, onCheckedChange)
}

@Composable
private fun SwitchRow(
    modifier: Modifier = Modifier,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AmountSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValue: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodySmall)
            Text(String.format(Locale.US, "%.4f", value), style = MaterialTheme.typography.bodySmall)
        }
        Slider(value = value, onValueChange = onValue, valueRange = range)
        Spacer(Modifier.height(1.dp))
    }
}
