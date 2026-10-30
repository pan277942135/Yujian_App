package com.yujian.ai.ui.recognition

/** All entrance layers sample one elapsed value; no independent restart when models advance. */
data class ProcessingOrbMotionFrame(
    val waterAlpha: Float,
    val ribbonOpening: Float,
    val destinationProgress: Float,
    val completionAlpha: Float,
)

fun processingOrbMotionFrame(entranceMs: Long, fromCamera: Boolean, reduceMotion: Boolean, completion: Float): ProcessingOrbMotionFrame {
    val progress = if (reduceMotion) 1f else (entranceMs / 900f).coerceIn(0f, 1f)
    val eased = progress * progress * (3f - 2f * progress)
    return ProcessingOrbMotionFrame(
        waterAlpha = if (reduceMotion || !fromCamera) 1f else ((entranceMs - 150L) / 200f).coerceIn(0f, 1f),
        ribbonOpening = if (reduceMotion) 1f else ((entranceMs - 350L) / 300f).coerceIn(0f, 1f),
        destinationProgress = eased,
        completionAlpha = 1f - completion.coerceIn(0f, 1f),
    )
}
