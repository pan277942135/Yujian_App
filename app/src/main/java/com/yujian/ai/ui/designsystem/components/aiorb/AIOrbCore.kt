package com.yujian.ai.ui.designsystem.components.aiorb

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.annotation.DrawableRes
import com.yujian.ai.R
import com.yujian.ai.ui.waterlight.core.EnergyEvent
import com.yujian.ai.ui.waterlight.core.EnergyFeedback
import com.yujian.ai.ui.waterlight.core.WaterLightPhase
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
import com.yujian.ai.ui.waterlight.renderer.OrbWaterLightRibbons
import com.yujian.ai.ui.waterlight.renderer.OrbCoreOpticalEffects
import com.yujian.ai.ui.waterlight.renderer.OrbFilamentRendererMode
import com.yujian.ai.ui.waterlight.renderer.OrbRuntimeProfileV1
import com.yujian.ai.ui.waterlight.renderer.OrbWakeBreathReleaseV11
import com.yujian.ai.ui.waterlight.renderer.OrbCoreImageOpticalEffect
import com.yujian.ai.ui.designsystem.components.YuJianCaptureButton
import com.yujian.ai.ui.designsystem.components.YuJianCaptureButtonRasterAssets
import com.yujian.ai.ui.designsystem.components.YuJianCaptureButtonRasterMotion


/** The home button and its navigation handoff share one frozen visual identity. */
object AIOrbCaptureVisualAssets {
    val HOME_IDLE: Int get() = R.drawable.ai_orb_camera_activated_v1
}

/**
 * One public, state-driven visual entry for the YuJian capture button and AI Orb.
 *
 * Hosts own navigation, capture, PhotoSession, inference and result routing.
 * This component owns only the visual state selected by the host.
 */
@Composable
fun AIOrbCore(
    state: AIOrbVisualState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    enabled: Boolean = true,
    contentDescription: String = "开始识鱼",
    motionEnabled: Boolean = true,
    rasterAssets: YuJianCaptureButtonRasterAssets? = null,
    rasterMotion: YuJianCaptureButtonRasterMotion? = null,
    visualSize: Dp? = null,
    touchTargetSize: Dp? = null,
    visualModifier: Modifier = Modifier,
    frozenVisual: Boolean = true,
    @DrawableRes frozenVisualResource: Int = R.drawable.capture_button_main_v1,
    onCaptureHaptic: (() -> Unit)? = null,
    clickElapsedMs: Long? = null,
    cameraReady: Boolean = false,
    coreSize: Dp = 80.dp,
    entranceElapsedMs: Long = 0L,
    elapsedMs: Long = 0L,
    completion: Float = 0f,
    reduceMotion: Boolean = false,
    lowPerformance: Boolean = false,
    fromCamera: Boolean = false,
    waterLightFrame: WaterLightFrame? = null,
    energyEvent: EnergyEvent? = null,
    waterLightMaterial: WaterLightMaterialProfile = WaterLightMaterialProfile.ORB_LEGACY,
    orbRendererMode: OrbFilamentRendererMode = OrbFilamentRendererMode.ORB_V1_CANDIDATE,
    orbLifeState: OrbRuntimeProfileV1.LifeState = OrbRuntimeProfileV1.LifeState.S2_ACTIVE,
    orbLifeAlpha: Float? = null,
    orbMotionEvent: OrbRuntimeProfileV1.MotionEvent = OrbRuntimeProfileV1.MotionEvent.NONE,
    orbMotionEventStartOpticalMs: Long = 0L,
    orbShowDroplets: Boolean = true,
    orbShowSunlight: Boolean = true,
    orbShowCaustics: Boolean = true,
) {
    when (state) {
        AIOrbVisualState.CAPTURE_BUTTON_IDLE -> YuJianCaptureButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            contentDescription = contentDescription,
            motionEnabled = motionEnabled,
            rasterAssets = rasterAssets,
            rasterMotion = rasterMotion,
            visualSize = visualSize,
            touchTargetSize = touchTargetSize,
            visualModifier = visualModifier,
            frozenVisual = frozenVisual,
            frozenVisualResource = frozenVisualResource,
            onCaptureHaptic = onCaptureHaptic,
            clickElapsedMs = clickElapsedMs,
            cameraReady = cameraReady,
        )
        AIOrbVisualState.CAPTURE_BUTTON_ACTIVATED -> ActivatedCameraWaterState(
            coreSize = coreSize,
            entranceElapsedMs = entranceElapsedMs,
            modifier = modifier,
        )
        AIOrbVisualState.ORB_MORPH_IN,
        AIOrbVisualState.ORB_ACTIVE,
        AIOrbVisualState.ORB_CONTRACTING -> OrbVisual(
            coreSize = coreSize,
            entranceElapsedMs = entranceElapsedMs,
            elapsedMs = elapsedMs,
            completion = completion,
            reduceMotion = reduceMotion,
            lowPerformance = lowPerformance,
            fromCamera = fromCamera,
            waterLightFrame = waterLightFrame,
            energyEvent = energyEvent,
            waterLightMaterial = waterLightMaterial,
            orbRendererMode = orbRendererMode,
            orbLifeState = orbLifeState,
            orbLifeAlpha = orbLifeAlpha,
            orbMotionEvent = orbMotionEvent,
            orbMotionEventStartOpticalMs = orbMotionEventStartOpticalMs,
            orbShowDroplets = orbShowDroplets,
            orbShowSunlight = orbShowSunlight,
            orbShowCaustics = orbShowCaustics,
            modifier = modifier,
        )
        AIOrbVisualState.HIDDEN -> Unit
    }
}

@Composable
private fun ActivatedCameraWaterState(
    coreSize: Dp,
    entranceElapsedMs: Long,
    modifier: Modifier,
) {
    val progress = if (entranceElapsedMs < 0L) 0f else (entranceElapsedMs / 150f).coerceIn(0f, 1f)
    Box(modifier.size(coreSize + 28.dp), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.ai_orb_camera_activated_v1),
            contentDescription = null,
            modifier = Modifier.size(coreSize).graphicsLayer {
                scaleX = 1f - .04f * progress
                scaleY = scaleX
            },
        )
    }
}

@Composable
private fun OrbVisual(
    coreSize: Dp,
    entranceElapsedMs: Long,
    elapsedMs: Long,
    completion: Float,
    reduceMotion: Boolean,
    lowPerformance: Boolean,
    fromCamera: Boolean,
    waterLightFrame: WaterLightFrame?,
    energyEvent: EnergyEvent?,
    waterLightMaterial: WaterLightMaterialProfile,
    orbRendererMode: OrbFilamentRendererMode,
    orbLifeState: OrbRuntimeProfileV1.LifeState,
    orbLifeAlpha: Float?,
    orbMotionEvent: OrbRuntimeProfileV1.MotionEvent,
    orbMotionEventStartOpticalMs: Long,
    orbShowDroplets: Boolean,
    orbShowSunlight: Boolean,
    orbShowCaustics: Boolean,
    modifier: Modifier,
) {
    val frame = aiOrbMotionFrame(entranceElapsedMs, fromCamera, reduceMotion, completion)
    val ribbons = frame.ribbonOpening
    val settle = completion.coerceIn(0f, 1f)
    val sharedFrame = waterLightFrame ?: WaterLightFrame(
        sessionId = "", photoSha256 = null, elapsedMs = elapsedMs.coerceAtLeast(0L),
        opticalReferenceElapsedMs = elapsedMs.coerceAtLeast(0L), phase = WaterLightPhase.PROCESSING,
        completion = completion.coerceIn(0f, 1f), reducedMotion = reduceMotion, geometryVersion = 0L,
    )
    val activeEnergyEvent = energyEvent?.takeIf { it.matches(sharedFrame) }
    val releaseEnergy = if (activeEnergyEvent?.feedback == EnergyFeedback.RELEASE) activeEnergyEvent.level.coerceIn(0f, 1f) else 0f
    val returnEnergy = if (activeEnergyEvent?.feedback == EnergyFeedback.RETURN) activeEnergyEvent.level.coerceIn(0f, 1f) else 0f
    val orbEnergyDepletion = when (activeEnergyEvent?.feedback) {
        EnergyFeedback.RELEASE -> .18f * releaseEnergy
        EnergyFeedback.RETURN -> .18f * (1f - returnEnergy)
        else -> 0f
    }
    // Actual event geometry/time stays in the one shared frame; no additional timer is created.
    val effectiveMotionEvent = if (orbMotionEvent != OrbRuntimeProfileV1.MotionEvent.NONE) orbMotionEvent else when (activeEnergyEvent?.feedback) {
        EnergyFeedback.RELEASE -> OrbRuntimeProfileV1.MotionEvent.RELEASE
        EnergyFeedback.RETURN -> OrbRuntimeProfileV1.MotionEvent.RETURN
        else -> OrbRuntimeProfileV1.MotionEvent.NONE
    }
    val effectiveMotionStartMs = if (activeEnergyEvent != null && orbMotionEvent == OrbRuntimeProfileV1.MotionEvent.NONE) {
        (sharedFrame.opticalReferenceElapsedMs - sharedFrame.sceneFrame.stageElapsedMs).coerceAtLeast(0L)
    } else orbMotionEventStartOpticalMs
    val wake = OrbWakeBreathReleaseV11.wake(sharedFrame.opticalReferenceElapsedMs)
    val lifeProgress = OrbWakeBreathReleaseV11.s2Progress(sharedFrame.opticalReferenceElapsedMs)
    val candidateLifeAlpha = orbLifeAlpha ?: lifeProgress
    val breath = OrbWakeBreathReleaseV11.breathScale(
        sceneElapsedMs = sharedFrame.elapsedMs,
        opticalReferenceElapsedMs = sharedFrame.opticalReferenceElapsedMs,
        reducedMotion = reduceMotion,
    )
    val context = LocalContext.current
    val density = LocalDensity.current
    val coreTextureSizePx = with(density) { (coreSize / .84f).toPx() }
    val coreOpticalEffect = remember(context, android.os.Build.VERSION.SDK_INT, lowPerformance) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            runCatching { OrbCoreImageOpticalEffect.create(context.applicationContext, lowPerformance) }
                .onFailure { android.util.Log.w("OrbCoreOpticalEffect", "candidate and V1.1 AGSL unavailable; source core and clipped light fallback active", it) }
                .getOrNull()
        } else {
            android.util.Log.i("OrbCoreOpticalEffect", "API < 33 fallback: original static core with scene-clock clipped Canvas light")
            null
        }
    }
    DisposableEffect(coreOpticalEffect) {
        onDispose { coreOpticalEffect?.close() }
    }
    SideEffect {
        coreOpticalEffect?.update(sharedFrame, lifeProgress, wake.grassLight, coreTextureSizePx)
    }
    Box(
        modifier.size(coreSize + 28.dp).graphicsLayer {
            scaleX = (1f - .18f * settle) * breath
            scaleY = scaleX
            alpha = frame.completionAlpha
        },
        contentAlignment = Alignment.Center,
    ) {
        OrbWaterLightRibbons(coreSize, sharedFrame, ribbons, settle, false, lowPerformance, waterLightMaterial,
            orbRendererMode, candidateLifeAlpha, effectiveMotionEvent, effectiveMotionStartMs,
            activeEnergyEvent, releaseEnergy, returnEnergy)
        Image(
            painter = painterResource(R.drawable.processing_water_core_v1),
            contentDescription = null,
            modifier = Modifier.size(coreSize / .84f).graphicsLayer {
                alpha = (1f - orbEnergyDepletion + .22f * returnEnergy).coerceIn(0f, 1f)
                scaleX = 1f
                scaleY = 1f
                coreOpticalEffect?.renderEffect?.let { renderEffect = it }
            },
        )
        OrbCoreOpticalEffects(
            coreSize = coreSize,
            frame = sharedFrame,
            coreAlpha = 1f,
            coreScale = 1f,
            low = lowPerformance,
            lifeState = orbLifeState,
            candidate = orbRendererMode == OrbFilamentRendererMode.ORB_V1_CANDIDATE,
            wake = wake,
            showDroplets = orbShowDroplets,
            showSunlight = orbShowSunlight,
            showCaustics = orbShowCaustics,
        )
        if (fromCamera && frame.cameraHeroAlpha > 0f) {
            Image(
                painter = painterResource(R.drawable.ai_orb_camera_activated_v1),
                contentDescription = null,
                modifier = Modifier.size(coreSize).graphicsLayer {
                    alpha = frame.cameraHeroAlpha
                    val press = if (entranceElapsedMs < 150L) 1f - .04f * (entranceElapsedMs / 150f) else .96f
                    scaleX = press
                    scaleY = press
                },
            )
        }
        OrbWaterLightRibbons(coreSize, sharedFrame, ribbons, settle, true, lowPerformance, waterLightMaterial,
            orbRendererMode, candidateLifeAlpha, effectiveMotionEvent, effectiveMotionStartMs,
            activeEnergyEvent, releaseEnergy, returnEnergy)
    }
}
