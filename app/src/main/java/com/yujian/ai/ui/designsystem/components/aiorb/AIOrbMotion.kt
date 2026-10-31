package com.yujian.ai.ui.designsystem.components.aiorb

enum class AIOrbVisualState {
    CAPTURE_BUTTON_IDLE,
    CAPTURE_BUTTON_ACTIVATED,
    ORB_MORPH_IN,
    ORB_ACTIVE,
    ORB_CONTRACTING,
    HIDDEN,
}

data class AIOrbMotionFrame(
    val waterAlpha: Float,
    val ribbonOpening: Float,
    val destinationProgress: Float,
    val completionAlpha: Float,
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
    return AIOrbMotionFrame(
        waterAlpha = if (reduceMotion || !fromCamera) 1f else ((safeEntranceMs - 150L) / 200f).coerceIn(0f, 1f),
        ribbonOpening = if (reduceMotion) 1f else ((safeEntranceMs - 350L) / 300f).coerceIn(0f, 1f),
        destinationProgress = eased,
        completionAlpha = 1f - safeCompletion,
    )
}

/** Selects a visual state only; the host continues to own capture, inference and navigation. */
fun aiOrbProcessingState(
    entranceMs: Long,
    fromCamera: Boolean,
    reduceMotion: Boolean,
    completion: Float,
): AIOrbVisualState = when {
    completion.coerceIn(0f, 1f) > 0f -> AIOrbVisualState.ORB_CONTRACTING
    reduceMotion -> AIOrbVisualState.ORB_ACTIVE
    fromCamera && entranceMs < 150L -> AIOrbVisualState.CAPTURE_BUTTON_ACTIVATED
    entranceMs < 350L -> AIOrbVisualState.ORB_MORPH_IN
    else -> AIOrbVisualState.ORB_ACTIVE
}
