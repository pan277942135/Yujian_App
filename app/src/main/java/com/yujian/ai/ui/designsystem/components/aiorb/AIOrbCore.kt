package com.yujian.ai.ui.designsystem.components.aiorb

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.annotation.DrawableRes
import androidx.annotation.VisibleForTesting
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
import com.yujian.ai.ui.waterlight.renderer.OrbShaderRoute
import com.yujian.ai.ui.designsystem.components.YuJianCaptureButton
import com.yujian.ai.ui.designsystem.components.YuJianCaptureButtonRasterAssets
import com.yujian.ai.ui.designsystem.components.YuJianCaptureButtonRasterMotion

enum class OrbRenderIsolationMode { NORMAL, RAW_CORE, RAW_CORE_WITH_R06, SUPPRESSED }

private const val MAX_SHADER_OVERLAY_ALPHA = 0.25f

/** Test-only seam: the supplied color represents shader output while preserving the real core container. */
internal data class OrbShaderOverlayTestOutput(val color: Color)

@VisibleForTesting
internal val LocalOrbShaderOverlayTestOutput = compositionLocalOf<OrbShaderOverlayTestOutput?> { null }

private data class OrbCoreRenderDiagnosticSnapshot(
    val stage: String,
    val shaderEnabled: Boolean,
    val shaderRoute: String,
    val shaderUpdateStatus: OrbShaderUpdateStatus,
    val hostRejectReason: String?,
    val wakeCheckpoint: String?,
)

internal fun aiOrbRawCoreVisible(renderIsolationMode: OrbRenderIsolationMode): Boolean =
    renderIsolationMode != OrbRenderIsolationMode.SUPPRESSED

internal fun orbDebugIsolationModeWithHostPriority(
    hostRejectReason: String?,
    hostMode: OrbRenderIsolationMode,
    requestedDebugMode: OrbRenderIsolationMode,
    debugSelectionEligible: Boolean,
): OrbRenderIsolationMode = if (
    hostRejectReason == null && debugSelectionEligible &&
    requestedDebugMode != OrbRenderIsolationMode.SUPPRESSED
) requestedDebugMode else hostMode

internal fun aiOrbShaderOverlayVisible(
    renderIsolationMode: OrbRenderIsolationMode,
    opticalEffectEnabled: Boolean,
    shaderAvailable: Boolean,
    renderEffectAvailable: Boolean,
    updateStatus: OrbShaderUpdateStatus,
    overlayAlpha: Float,
): Boolean = renderIsolationMode == OrbRenderIsolationMode.NORMAL && opticalEffectEnabled &&
    shaderAvailable && renderEffectAvailable && updateStatus == OrbShaderUpdateStatus.READY && overlayAlpha > 0f


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
    renderIsolationMode: OrbRenderIsolationMode = OrbRenderIsolationMode.NORMAL,
    hostRejectReason: String? = null,
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
            renderIsolationMode = renderIsolationMode,
            hostRejectReason = hostRejectReason,
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
    renderIsolationMode: OrbRenderIsolationMode,
    hostRejectReason: String?,
    modifier: Modifier,
) {
    if (!aiOrbRawCoreVisible(renderIsolationMode)) return
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
    val sceneFrame = sharedFrame.sceneFrame
    val wake = OrbWakeBreathReleaseV11.wake(sceneFrame.stage, sceneFrame.stageProgress)
    val lifeProgress = OrbWakeBreathReleaseV11.s2Progress(sceneFrame.stage, sceneFrame.stageProgress)
    val candidateLifeAlpha = orbLifeAlpha ?: lifeProgress
    val breath = OrbWakeBreathReleaseV11.breathScale(
        sceneElapsedMs = sharedFrame.elapsedMs,
        stage = sceneFrame.stage,
        stageProgress = sceneFrame.stageProgress,
        reducedMotion = reduceMotion,
    )
    val context = LocalContext.current
    val density = LocalDensity.current
    val coreTextureSizePx = with(density) { (coreSize / .84f).toPx() }
    val opticalEffectEnabled = renderIsolationMode == OrbRenderIsolationMode.NORMAL &&
        aiOrbOpticalEffectEnabled(sceneFrame.stage, sceneFrame.stageProgress)
    var coreOpticalEffect by remember(
        context,
        android.os.Build.VERSION.SDK_INT,
        lowPerformance,
        sharedFrame.sessionId,
        sharedFrame.attemptId,
    ) { mutableStateOf<OrbCoreImageOpticalEffect?>(null) }
    var shaderCreationAttempted by remember(
        context,
        android.os.Build.VERSION.SDK_INT,
        lowPerformance,
        sharedFrame.sessionId,
        sharedFrame.attemptId,
    ) { mutableStateOf(false) }
    val currentCoreOpticalEffect = coreOpticalEffect
    val shaderInstance = currentCoreOpticalEffect?.takeIf {
        it.route != OrbShaderRoute.UNAVAILABLE && it.renderEffect != null
    }
    val shaderUpdateGate = remember(shaderInstance) { OrbShaderUpdateGate(shaderInstance) }
    val injectedShaderOutput = LocalOrbShaderOverlayTestOutput.current
    val shaderUpdateStatus = if (injectedShaderOutput != null) OrbShaderUpdateStatus.READY
        else shaderUpdateGate.status
    val shaderRoute = when {
        injectedShaderOutput != null -> "TEST_OUTPUT_INJECTION"
        currentCoreOpticalEffect == null -> OrbShaderRoute.UNAVAILABLE.name
        else -> currentCoreOpticalEffect.route.name
    }
    val shaderOverlayAlpha = wake.optics.coerceIn(0f, 1f) * MAX_SHADER_OVERLAY_ALPHA
    val appliedRenderEffect = coreOpticalEffect?.renderEffect?.takeIf {
        injectedShaderOutput == null && renderIsolationMode == OrbRenderIsolationMode.NORMAL &&
            opticalEffectEnabled && shaderUpdateStatus == OrbShaderUpdateStatus.READY && shaderOverlayAlpha > 0f
    }
    val shaderAvailable = if (injectedShaderOutput != null) true else shaderInstance != null
    val renderEffectAvailable = injectedShaderOutput != null || appliedRenderEffect != null
    val shaderOverlayEnabled = aiOrbShaderOverlayVisible(
        renderIsolationMode = renderIsolationMode,
        opticalEffectEnabled = opticalEffectEnabled,
        shaderAvailable = shaderAvailable,
        renderEffectAvailable = renderEffectAvailable,
        updateStatus = shaderUpdateStatus,
        overlayAlpha = shaderOverlayAlpha,
    )
    val effectForDispose = coreOpticalEffect
    DisposableEffect(effectForDispose) {
        onDispose {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                effectForDispose?.close()
            }
        }
    }
    SideEffect {
        if (!shaderCreationAttempted) {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                shaderCreationAttempted = true
                android.util.Log.w(
                    "OrbCoreOpticalEffect",
                    "API < 33 fallback: original static core with scene-clock clipped Canvas light",
                )
            } else if (renderIsolationMode == OrbRenderIsolationMode.NORMAL) {
                shaderCreationAttempted = true
                coreOpticalEffect = runCatching {
                    OrbCoreImageOpticalEffect.create(context.applicationContext, lowPerformance)
                }.onFailure {
                    android.util.Log.w(
                        "OrbCoreOpticalEffect",
                        "candidate and V1.1 AGSL unavailable; source core and clipped light fallback active",
                        it,
                    )
                }.getOrNull()
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            injectedShaderOutput == null && opticalEffectEnabled && shaderInstance != null
        ) {
            val failure = shaderUpdateGate.update {
                it.update(sharedFrame, lifeProgress, wake.grassLight, coreTextureSizePx)
            }
            if (failure != null) {
                val exceptionType = failure::class.qualifiedName ?: failure.javaClass.name
                android.util.Log.e(
                    "ORB_CORE_RENDER",
                    "Shader uniform update failed: exceptionType=$exceptionType stage=${sceneFrame.stage.name} " +
                        "shaderRoute=$shaderRoute",
                    failure,
                )
            }
        }
    }
    val sourceCoreAlpha = (1f - orbEnergyDepletion + .22f * returnEnergy).coerceIn(0f, 1f)
    val lastDiagnosticSnapshot = remember { arrayOfNulls<OrbCoreRenderDiagnosticSnapshot>(1) }
    SideEffect {
        val stageProgress = sceneFrame.stageProgress.coerceIn(0f, 1f)
        val wakeCheckpoint = if (sceneFrame.stage == com.yujian.ai.ui.waterlight.core.RecognitionSceneStage.ORB_WAKE) {
            when {
                kotlin.math.abs(stageProgress - .25f) <= .01f -> "25"
                kotlin.math.abs(stageProgress - .50f) <= .01f -> "50"
                kotlin.math.abs(stageProgress - .75f) <= .01f -> "75"
                kotlin.math.abs(stageProgress - 1f) <= .01f -> "100"
                else -> null
            }
        } else null
        val currentSnapshot = OrbCoreRenderDiagnosticSnapshot(
            stage = sceneFrame.stage.name,
            shaderEnabled = shaderOverlayEnabled,
            shaderRoute = shaderRoute,
            shaderUpdateStatus = shaderUpdateStatus,
            hostRejectReason = hostRejectReason,
            wakeCheckpoint = wakeCheckpoint,
        )
        val previous = lastDiagnosticSnapshot[0]
        val isRelevantTransition = previous != null && (
            (previous.stage != currentSnapshot.stage) ||
                (!previous.shaderEnabled && currentSnapshot.shaderEnabled) ||
                (previous.shaderUpdateStatus == OrbShaderUpdateStatus.READY &&
                    currentSnapshot.shaderUpdateStatus == OrbShaderUpdateStatus.FAILED) ||
                (previous.shaderRoute != currentSnapshot.shaderRoute) ||
                (previous.hostRejectReason != currentSnapshot.hostRejectReason) ||
                (currentSnapshot.wakeCheckpoint != null &&
                    previous.wakeCheckpoint != currentSnapshot.wakeCheckpoint)
            )
        if (isRelevantTransition) {
            val logTag = if (sceneFrame.stage == com.yujian.ai.ui.waterlight.core.RecognitionSceneStage.ORB_WAKE) {
                "ORB_WAKE_VISIBILITY"
            } else "ORB_CORE_RENDER"
            android.util.Log.i(
                logTag,
                "stage=${sceneFrame.stage.name} stageProgress=$stageProgress " +
                    "semanticLocalMs=${stageProgress * sceneFrame.stage.minimumVisibleMs} " +
                    "wakeCheckpoint=${currentSnapshot.wakeCheckpoint ?: "TRANSITION"} " +
                    "wakeOptics=${wake.optics} wakeWaterCore=${wake.waterCore} " +
                    "shaderEnabled=${shaderOverlayEnabled} shaderRoute=$shaderRoute " +
                    "shaderUpdateStatus=${shaderUpdateStatus.name} overlayAlpha=$shaderOverlayAlpha " +
                    "sourceCoreAlpha=$sourceCoreAlpha hostRejectReason=${hostRejectReason ?: "NONE"} " +
                    "renderIsolationMode=${renderIsolationMode.name} geometryVersion=${sharedFrame.geometryVersion}",
            )
        }
        lastDiagnosticSnapshot[0] = currentSnapshot
    }
    val isolateCoreForTestOutput = injectedShaderOutput != null
    Box(
        modifier.size(coreSize + 28.dp).graphicsLayer {
            scaleX = (1f - .18f * settle) * breath
            scaleY = scaleX
            alpha = frame.completionAlpha
        },
        contentAlignment = Alignment.Center,
    ) {
        if (renderIsolationMode != OrbRenderIsolationMode.RAW_CORE && !isolateCoreForTestOutput) {
            OrbWaterLightRibbons(coreSize, sharedFrame, ribbons, settle, false, lowPerformance, waterLightMaterial,
                orbRendererMode, candidateLifeAlpha, effectiveMotionEvent, effectiveMotionStartMs,
                activeEnergyEvent, releaseEnergy, returnEnergy)
        }
        val corePainter = painterResource(R.drawable.processing_water_core_v1)
        Box(
            modifier = Modifier.size(coreSize / .84f).graphicsLayer {
                alpha = sourceCoreAlpha
                compositingStrategy = CompositingStrategy.Offscreen
            },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = corePainter,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
            )
            if (shaderOverlayEnabled) {
                if (injectedShaderOutput != null) {
                    Box(
                        modifier = Modifier.matchParentSize().graphicsLayer {
                            alpha = shaderOverlayAlpha
                        }.background(injectedShaderOutput.color),
                    )
                } else {
                    Image(
                        painter = corePainter,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize().graphicsLayer {
                            alpha = shaderOverlayAlpha
                            renderEffect = appliedRenderEffect
                        },
                    )
                }
            }
        }
        if (renderIsolationMode == OrbRenderIsolationMode.NORMAL && !isolateCoreForTestOutput) OrbCoreOpticalEffects(
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
        if (renderIsolationMode == OrbRenderIsolationMode.NORMAL && !isolateCoreForTestOutput &&
            fromCamera && frame.cameraHeroAlpha > 0f) {
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
        if (renderIsolationMode != OrbRenderIsolationMode.RAW_CORE && !isolateCoreForTestOutput) {
            OrbWaterLightRibbons(coreSize, sharedFrame, ribbons, settle, true, lowPerformance, waterLightMaterial,
                orbRendererMode, candidateLifeAlpha, effectiveMotionEvent, effectiveMotionStartMs,
                activeEnergyEvent, releaseEnergy, returnEnergy)
        }
    }
}
