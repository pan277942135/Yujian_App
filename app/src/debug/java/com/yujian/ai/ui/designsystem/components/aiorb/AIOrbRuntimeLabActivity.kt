package com.yujian.ai.ui.designsystem.components.aiorb

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.yujian.ai.R
import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ui.theme.YujianTheme
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
import com.yujian.ai.ui.waterlight.renderer.OrbFilamentRendererMode
import com.yujian.ai.ui.waterlight.renderer.OrbRuntimeProfileV1
import java.io.File
import java.io.FileOutputStream

/** Debug-only entry point for examining the production AIOrbCore candidate renderer. */
class AIOrbRuntimeLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extras = intent.extras
        val initialState = extras?.getString("state")
        val initialMode = extras?.getString("mode")
        val initialSize = extras?.getInt("size_dp") ?: 80
        val initialDark = extras?.getBoolean("dark") ?: true
        val initialLow = extras?.getBoolean("low") ?: false
        val initialOpticalMs = extras?.getLong("optical_ms") ?: 0L
        val initialEvent = extras?.getString("event")
        val initialEventStart = extras?.getLong("event_start_ms") ?: 0L
        val initialLifeAlpha = extras?.getFloat("life_alpha")
        val transitionTarget = extras?.getString("transition_target")
        val transitionSampleMs = extras?.getLong("transition_sample_ms") ?: 0L
        val initialDroplets = extras?.getBoolean("droplets") ?: true
        val initialSunlight = extras?.getBoolean("sunlight") ?: true
        val initialCaustics = extras?.getBoolean("caustics") ?: true
        setContent {
            YujianTheme {
                OrbRuntimeLabScreen(
                    initialState, initialMode, initialSize, initialDark, initialLow,
                    initialOpticalMs, initialEvent, initialEventStart, initialLifeAlpha,
                    initialDroplets, initialSunlight, initialCaustics, transitionTarget, transitionSampleMs,
                )
            }
        }
    }
}

@Composable
private fun OrbRuntimeLabScreen(
    initialState: String?,
    initialMode: String?,
    initialSize: Int,
    initialDark: Boolean,
    initialLow: Boolean,
    initialOpticalMs: Long,
    initialEvent: String?,
    initialEventStart: Long,
    initialLifeAlpha: Float?,
    initialDroplets: Boolean,
    initialSunlight: Boolean,
    initialCaustics: Boolean,
    transitionTarget: String?,
    transitionSampleMs: Long,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var lifeState by remember { mutableStateOf(
        if (initialState == "S1" || initialState == "S1_IDLE") OrbRuntimeProfileV1.LifeState.S1_IDLE
        else OrbRuntimeProfileV1.LifeState.S2_ACTIVE,
    ) }
    var mode by remember { mutableStateOf(
        if (initialMode == "LEGACY") OrbFilamentRendererMode.LEGACY else OrbFilamentRendererMode.ORB_V1_CANDIDATE,
    ) }
    var sizeDp by remember { mutableIntStateOf(if (initialSize == 68) 68 else 80) }
    var dark by remember { mutableStateOf(initialDark) }
    var low by remember { mutableStateOf(initialLow) }
    var opticalMs by remember { mutableLongStateOf(initialOpticalMs.coerceAtLeast(0L)) }
    var event by remember { mutableStateOf(
        when (initialEvent) {
            "RELEASE" -> OrbRuntimeProfileV1.MotionEvent.RELEASE
            "RETURN" -> OrbRuntimeProfileV1.MotionEvent.RETURN
            else -> OrbRuntimeProfileV1.MotionEvent.NONE
        },
    ) }
    var eventStartMs by remember { mutableLongStateOf(initialEventStart) }
    var droplets by remember { mutableStateOf(initialDroplets) }
    var sunlight by remember { mutableStateOf(initialSunlight) }
    var caustics by remember { mutableStateOf(initialCaustics) }
    val pageBackground = if (dark) Color(0xFF10272D) else Color(0xFFE8F0ED)
    val orbFrame = remember(opticalMs) {
        WaterLightFrame(
            sessionId = "orb-runtime-lab",
            photoSha256 = OrbRuntimeProfileV1.CORE_SHA256,
            elapsedMs = opticalMs,
            opticalReferenceElapsedMs = opticalMs,
            phase = RecognitionPhase.CLASSIFYING,
            completion = 0f,
            reducedMotion = false,
            geometryVersion = 1L,
            attemptId = "debug-lab",
            imageId = "processing-water-core-v1",
            photoContentVersion = 1L,
        )
    }
    LaunchedEffect(transitionTarget, transitionSampleMs) {
        val target = when (transitionTarget) {
            "S1", "S1_IDLE" -> OrbRuntimeProfileV1.LifeState.S1_IDLE
            "S2", "S2_ACTIVE" -> OrbRuntimeProfileV1.LifeState.S2_ACTIVE
            else -> null
        }
        if (target != null && target != lifeState) {
            withFrameNanos { }
            lifeState = target
            withFrameNanos { }
            opticalMs = (initialOpticalMs + transitionSampleMs).coerceAtLeast(0L)
        }
    }

    Column(
        Modifier.fillMaxSize().background(pageBackground).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("渔见 AI Orb Runtime Lab", style = MaterialTheme.typography.titleLarge,
            color = if (dark) Color.White else Color(0xFF16363A))
        Text("正式 AIOrbCore · 固定光学时钟 ${opticalMs}ms",
            color = if (dark) Color(0xFFB7CBC8) else Color(0xFF45615D))
        Spacer(Modifier.height(12.dp))
        Box(Modifier.size((sizeDp + 28).dp), contentAlignment = Alignment.Center) {
            AIOrbCore(
                state = AIOrbVisualState.ORB_ACTIVE,
                coreSize = sizeDp.dp,
                entranceElapsedMs = OrbRuntimeProfileV1.R04_ENTRANCE_MS,
                elapsedMs = opticalMs,
                completion = 0f,
                reduceMotion = false,
                lowPerformance = low,
                fromCamera = false,
                waterLightFrame = orbFrame,
                waterLightMaterial = WaterLightMaterialProfile.ORB_LEGACY,
                orbRendererMode = mode,
                orbLifeState = lifeState,
                orbLifeAlpha = initialLifeAlpha,
                orbMotionEvent = event,
                orbMotionEventStartOpticalMs = eventStartMs,
                orbShowDroplets = droplets,
                orbShowSunlight = sunlight,
                orbShowCaustics = caustics,
            )
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LabButton(mode.name) { mode = if (mode == OrbFilamentRendererMode.LEGACY) OrbFilamentRendererMode.ORB_V1_CANDIDATE else OrbFilamentRendererMode.LEGACY }
            LabButton(lifeState.name) {
                lifeState = if (lifeState == OrbRuntimeProfileV1.LifeState.S1_IDLE) OrbRuntimeProfileV1.LifeState.S2_ACTIVE else OrbRuntimeProfileV1.LifeState.S1_IDLE
            }
            LabButton("${sizeDp}dp") { sizeDp = if (sizeDp == 68) 80 else 68 }
            LabButton(if (dark) "Dark" else "Light") { dark = !dark }
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LabButton(if (low) "Low" else "Normal") { low = !low }
            LabButton("RELEASE") { event = OrbRuntimeProfileV1.MotionEvent.RELEASE; eventStartMs = opticalMs }
            LabButton("RETURN") { event = OrbRuntimeProfileV1.MotionEvent.RETURN; eventStartMs = opticalMs }
            LabButton("Clear event") { event = OrbRuntimeProfileV1.MotionEvent.NONE }
        }
        Text("S1 / S2 转换使用同一光学时间（450ms / 620ms）", color = if (dark) Color.White else Color.DarkGray)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(0L, 225L, 450L, 620L, 900L, 1800L, 2700L).forEach { sample -> LabButton("${sample}ms") { opticalMs = sample } }
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LabToggle("Drops", droplets) { droplets = it }
            LabToggle("Sun", sunlight) { sunlight = it }
            LabToggle("Caustics", caustics) { caustics = it }
        }
        LabButton("导出当前截图") {
            try {
                val directory = context.getExternalFilesDir("Pictures") ?: context.filesDir
                directory.mkdirs()
                val file = File(directory, "orb-runtime-${lifeState.name}-${sizeDp}dp-${if (dark) "dark" else "light"}-${opticalMs}ms.png")
                val bitmap = view.drawToBitmap(Bitmap.Config.ARGB_8888)
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                Toast.makeText(context, "PNG: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                Toast.makeText(context, "截图导出失败：${error.message}", Toast.LENGTH_LONG).show()
            }
        }
        Text("R02 / R03 / R05 内层可单独开关；候选 Profile 来自设计 JSON。",
            color = if (dark) Color(0xFFB7CBC8) else Color(0xFF45615D), modifier = Modifier.padding(vertical = 8.dp))
    }
}

@Composable
private fun LabButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.padding(vertical = 3.dp)) { Text(label) }
}

@Composable
private fun LabToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
