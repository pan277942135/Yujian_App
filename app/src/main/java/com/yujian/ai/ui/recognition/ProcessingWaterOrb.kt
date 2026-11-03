package com.yujian.ai.ui.recognition

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.yujian.ai.ui.designsystem.components.aiorb.AIOrbCore
import com.yujian.ai.ui.designsystem.components.aiorb.aiOrbProcessingState
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
import com.yujian.ai.ui.waterlight.renderer.OrbFilamentRendererMode
import com.yujian.ai.ui.waterlight.renderer.OrbRuntimeProfileV1

/** Compatibility adapter; the only Orb renderer now lives in the shared AI Orb Core. */
@Composable
fun ProcessingWaterOrb(
    coreSize: Dp,
    entranceElapsedMs: Long,
    elapsedMs: Long,
    completion: Float,
    reduceMotion: Boolean,
    lowPerformance: Boolean,
    fromCamera: Boolean,
    modifier: Modifier = Modifier,
    waterLightFrame: WaterLightFrame? = null,
    waterLightMaterial: WaterLightMaterialProfile = WaterLightMaterialProfile.ORB_LEGACY,
    rendererMode: OrbFilamentRendererMode? = null,
    lifeState: OrbRuntimeProfileV1.LifeState = OrbRuntimeProfileV1.LifeState.S2_ACTIVE,
    motionEvent: OrbRuntimeProfileV1.MotionEvent = OrbRuntimeProfileV1.MotionEvent.NONE,
    motionEventStartOpticalMs: Long = 0L,
) {
    AIOrbCore(
        state = aiOrbProcessingState(entranceElapsedMs, fromCamera, reduceMotion, completion),
        coreSize = coreSize,
        entranceElapsedMs = entranceElapsedMs,
        elapsedMs = elapsedMs,
        completion = completion,
        reduceMotion = reduceMotion,
        lowPerformance = lowPerformance,
        fromCamera = fromCamera,
        waterLightFrame = waterLightFrame,
        waterLightMaterial = waterLightMaterial,
        orbRendererMode = rendererMode ?: if (com.yujian.ai.BuildConfig.DEBUG) {
            OrbFilamentRendererMode.ORB_V1_CANDIDATE
        } else OrbFilamentRendererMode.LEGACY,
        orbLifeState = lifeState,
        orbMotionEvent = motionEvent,
        orbMotionEventStartOpticalMs = motionEventStartOpticalMs,
        modifier = modifier,
    )
}
