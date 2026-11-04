package com.yujian.ai.ui.recognition

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import com.yujian.ai.ui.waterlight.core.RecognitionSceneFrame
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage

/**
 * Android-native projection of the frozen Effect 01 contract.
 * Original Effect 01 renderer source was NOT_FOUND at the audited baseline.
 */
internal object Effect01NativeCalibration {
    const val sceneId = "fish-catch-recognition"
    const val displayName = "鱼获识别"
    const val spreadDurationMs = 1_200L
    const val intensity = 1.50f
    const val speed = 1.00f
    const val marginRatio = .060f
    const val glowSizeDp = 36f
    const val bottomMaskStrength = 1.00f
    const val blendRatio = .75f
    const val blendMode = "overlay"

    const val deepPeacockColorArgb = 0xFF0B6E69L
    const val waterGreenColorArgb = 0xFF4DD0C7L
    const val iceWhiteColorArgb = 0xFFDDF7F5L
}

internal object UpwardCausticSurgeCalibration {
    const val durationMs = 1_100L
    const val maxArcHeightRatio = .025f
    const val bodyWidthHeightRatio = .10f
    const val crestWidthHeightRatio = .015f
    const val tailWidthHeightRatio = .08f
    const val maxCompositeOpacity = .22f
}

internal object PhotoShockwaveCalibration {
    const val durationMs = 260L
    const val maxUpwardTranslationDp = -3f
    const val maxReboundTranslationDp = 2f
    const val maxRotationDegrees = .15f
    const val peakScale = 1.004f
}

internal data class UpwardCausticFrame(
    val progress: Float,
    val centerYFraction: Float,
    val arcOffsetFraction: Float,
    val bodyWidthFraction: Float,
    val crestWidthFraction: Float,
    val tailWidthFraction: Float,
    val opacity: Float,
)

internal data class PhotoShockwaveTransform(
    val translationYDp: Float = 0f,
    val rotationDegrees: Float = 0f,
    val scale: Float = 1f,
)

internal fun upwardCausticFrame(progress: Float): UpwardCausticFrame {
    val boundedProgress = progress.coerceIn(0f, 1f)
    val easedProgress = smoothStep01(boundedProgress)
    val arc = -sin(boundedProgress * PI).toFloat() *
        UpwardCausticSurgeCalibration.maxArcHeightRatio
    val rise = boundedProgress * boundedProgress * sqrt(boundedProgress)
    val release = 1f - smoothStep01((boundedProgress - .92f) / .08f)
    val opacity = UpwardCausticSurgeCalibration.maxCompositeOpacity * rise * release

    return UpwardCausticFrame(
        progress = boundedProgress,
        centerYFraction = 1f - easedProgress,
        arcOffsetFraction = arc,
        bodyWidthFraction = UpwardCausticSurgeCalibration.bodyWidthHeightRatio,
        crestWidthFraction = UpwardCausticSurgeCalibration.crestWidthHeightRatio,
        tailWidthFraction = UpwardCausticSurgeCalibration.tailWidthHeightRatio,
        opacity = opacity.coerceIn(0f, UpwardCausticSurgeCalibration.maxCompositeOpacity),
    )
}

internal fun upwardCausticFrame(scene: RecognitionSceneFrame): UpwardCausticFrame =
    upwardCausticFrame(when (scene.stage) {
        RecognitionSceneStage.ORB_RELEASE -> scene.stageProgress
        RecognitionSceneStage.PERIMETER_ARRIVAL,
        RecognitionSceneStage.PATH_TO_FISH,
        RecognitionSceneStage.CONTOUR_A,
        RecognitionSceneStage.CONTOUR_B,
        RecognitionSceneStage.CONTOUR_C -> 1f
        else -> 0f
    })

internal fun effect01SpreadProgress(scene: RecognitionSceneFrame): Float {
    val p = smoothStep01(scene.stageProgress)
    return when (scene.stage) {
        RecognitionSceneStage.PHOTO_STABLE -> 0f
        RecognitionSceneStage.ORB_WAKE -> .10f * p
        RecognitionSceneStage.ORB_RELEASE -> .10f + .40f * p
        RecognitionSceneStage.PERIMETER_ARRIVAL -> .50f + .30f * p
        RecognitionSceneStage.PATH_TO_FISH -> .80f + .20f * p
        else -> 1f
    }.coerceIn(0f, 1f)
}

/** Frozen P0-P7 optical envelope projected onto the shared semantic scene stages. */
internal fun processingEnvironmentStrength(scene: RecognitionSceneFrame): Float {
    val p = smoothStep01(scene.stageProgress)
    val (from, to) = when (scene.stage) {
        RecognitionSceneStage.PHOTO_STABLE -> return 0f
        RecognitionSceneStage.ORB_WAKE -> 0f to 10f
        RecognitionSceneStage.ORB_RELEASE -> 10f to 45f
        RecognitionSceneStage.PERIMETER_ARRIVAL -> 45f to 75f
        RecognitionSceneStage.PATH_TO_FISH -> 75f to 60f
        RecognitionSceneStage.CONTOUR_A -> 60f to 35f
        RecognitionSceneStage.CONTOUR_B -> 35f to 10f
        RecognitionSceneStage.CONTOUR_C -> 10f to 0f
        RecognitionSceneStage.FISH_GOLD_PEAK,
        RecognitionSceneStage.ORB_RETURN,
        RecognitionSceneStage.ORB_TO_NAME,
        RecognitionSceneStage.SPECIES_NAME_REVEAL,
        RecognitionSceneStage.RESIDUE_SETTLE -> return 0f
    }
    return from + (to - from) * p
}

internal fun photoShockwaveTransform(
    scene: RecognitionSceneFrame,
    reduceMotion: Boolean = false,
): PhotoShockwaveTransform {
    if (scene.stage != RecognitionSceneStage.ORB_RELEASE) return PhotoShockwaveTransform()
    return photoShockwaveLocalTransform(scene.stageElapsedMs, reduceMotion)
}

private fun photoShockwaveLocalTransform(
    localMs: Long,
    reduceMotion: Boolean,
): PhotoShockwaveTransform {
    if (reduceMotion || localMs <= 0L || localMs >= PhotoShockwaveCalibration.durationMs) {
        return PhotoShockwaveTransform()
    }

    return when {
        localMs <= 60L -> {
            val p = smoothStep01(localMs / 60f)
            PhotoShockwaveTransform(
                translationYDp = PhotoShockwaveCalibration.maxUpwardTranslationDp * p,
                rotationDegrees = PhotoShockwaveCalibration.maxRotationDegrees * p,
                scale = 1f + (PhotoShockwaveCalibration.peakScale - 1f) * p,
            )
        }
        localMs <= 120L -> {
            val p = smoothStep01((localMs - 60L) / 60f)
            PhotoShockwaveTransform(
                translationYDp = PhotoShockwaveCalibration.maxUpwardTranslationDp +
                    (PhotoShockwaveCalibration.maxReboundTranslationDp -
                        PhotoShockwaveCalibration.maxUpwardTranslationDp) * p,
                rotationDegrees = PhotoShockwaveCalibration.maxRotationDegrees -
                    2f * PhotoShockwaveCalibration.maxRotationDegrees * p,
                scale = PhotoShockwaveCalibration.peakScale +
                    (1f - PhotoShockwaveCalibration.peakScale) * p,
            )
        }
        else -> {
            val p = smoothStep01((localMs - 120L) / 140f)
            val remaining = 1f - p
            PhotoShockwaveTransform(
                translationYDp = PhotoShockwaveCalibration.maxReboundTranslationDp * remaining,
                rotationDegrees = -PhotoShockwaveCalibration.maxRotationDegrees * remaining,
                scale = 1f,
            )
        }
    }
}

internal fun smoothStep01(value: Float): Float {
    val p = value.coerceIn(0f, 1f)
    return p * p * (3f - 2f * p)
}
