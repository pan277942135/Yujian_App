package com.yujian.ai.ui.designsystem.components.aiorb

import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage

enum class AIOrbVisualState {
    CAPTURE_BUTTON_IDLE,
    CAPTURE_BUTTON_ACTIVATED,
    ORB_MORPH_IN,
    ORB_ACTIVE,
    ORB_CONTRACTING,
    HIDDEN,
}

/** Debug comparison of the same core image and the same scene frame. */
enum class OrbWakeRenderMode { CORE_ONLY, CORE_WITH_OPTICS, FULL_ORB_WAKE }

internal fun orbOpticsVisible(stage: RecognitionSceneStage, mode: OrbWakeRenderMode): Boolean =
    mode != OrbWakeRenderMode.CORE_ONLY && orbCoreOpticalEffectEnabled(stage)

internal fun orbWakeDetailsVisible(mode: OrbWakeRenderMode): Boolean =
    mode == OrbWakeRenderMode.FULL_ORB_WAKE

data class AIOrbMotionFrame(
    /** The immutable water core is present from frame zero; wake reveals internal optics only. */
    val waterAlpha: Float,
    /** Kept for call-site compatibility. The frozen R06 conversion runs alongside ORB_WAKE. */
    val ribbonOpening: Float,
    val destinationProgress: Float,
    val completionAlpha: Float,
    /** Camera capture art is a handoff overlay; gallery entry has no camera overlay. */
    val cameraHeroAlpha: Float,
)

/** All Orb layers sample one stable Processing scene clock. */
fun aiOrbMotionFrame(
    entranceMs: Long,
    fromCamera: Boolean,
    reduceMotion: Boolean,
    completion: Float,
): AIOrbMotionFrame {
    val safeEntranceMs = entranceMs.coerceAtLeast(0L)
    val safeCompletion = completion.coerceIn(0f, 1f)
    val progress = if (reduceMotion) 1f else (safeEntranceMs / 900f).coerceIn(0f, 1f)
    val eased = progress * progress * (3f - 2f * progress)
    val heroProgress = if (reduceMotion) 1f else ((safeEntranceMs - 150L) / 200f).coerceIn(0f, 1f)
    return AIOrbMotionFrame(
        waterAlpha = 1f,
        ribbonOpening = 1f,
        destinationProgress = eased,
        completionAlpha = 1f - safeCompletion,
        cameraHeroAlpha = if (fromCamera) 1f - heroProgress else 0f,
    )
}

/** Selects a visual state only; the host continues to own capture, inference and navigation. */
fun aiOrbProcessingState(
    entranceMs: Long,
    @Suppress("UNUSED_PARAMETER") fromCamera: Boolean,
    reduceMotion: Boolean,
    completion: Float,
): AIOrbVisualState = when {
    completion.coerceIn(0f, 1f) > 0f -> AIOrbVisualState.ORB_CONTRACTING
    reduceMotion -> AIOrbVisualState.ORB_ACTIVE
    entranceMs < 350L -> AIOrbVisualState.ORB_MORPH_IN
    else -> AIOrbVisualState.ORB_ACTIVE
}

/** PHOTO_STABLE keeps the byte-frozen core raster untouched until Orb wake begins. */
internal fun orbCoreOpticalEffectEnabled(
    stage: RecognitionSceneStage,
): Boolean = stage != RecognitionSceneStage.PHOTO_STABLE
