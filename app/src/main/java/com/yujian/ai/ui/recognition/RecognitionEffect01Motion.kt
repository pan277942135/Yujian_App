package com.yujian.ai.ui.recognition

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

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

    const val goldColorArgb = 0xFFFEF08AL
    const val cyanColorArgb = 0xFF38BDF8L
    const val greenColorArgb = 0xFF2DD4BFL
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
    const val triggerMs = 850L
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

internal fun upwardCausticFrame(elapsedMs: Long): UpwardCausticFrame {
    val progress = smoothStep01(
        elapsedMs.coerceAtLeast(0L).toFloat() /
            UpwardCausticSurgeCalibration.durationMs.toFloat(),
    )
    val eased = smoothStep01(progress)
    val arc = -sin(progress * PI).toFloat() *
        UpwardCausticSurgeCalibration.maxArcHeightRatio
    val rise = progress * progress * sqrt(progress)
    val release = 1f - smoothStep01((progress - .92f) / .08f)
    val opacity = UpwardCausticSurgeCalibration.maxCompositeOpacity *
        rise * release

    return UpwardCausticFrame(
        progress = progress,
        centerYFraction = 1f - eased,
        arcOffsetFraction = arc,
        bodyWidthFraction = UpwardCausticSurgeCalibration.bodyWidthHeightRatio,
        crestWidthFraction = UpwardCausticSurgeCalibration.crestWidthHeightRatio,
        tailWidthFraction = UpwardCausticSurgeCalibration.tailWidthHeightRatio,
        opacity = opacity.coerceIn(0f, UpwardCausticSurgeCalibration.maxCompositeOpacity),
    )
}

internal fun effect01SpreadProgress(elapsedMs: Long): Float =
    smoothStep01(
        elapsedMs.coerceAtLeast(0L).toFloat() /
            Effect01NativeCalibration.spreadDurationMs.toFloat(),
    )

/** Returns the photo-only transform; zero motion is exact outside the bounded impact window. */
internal fun photoShockwaveTransform(
    processingElapsedMs: Long,
    reduceMotion: Boolean = false,
): PhotoShockwaveTransform {
    if (reduceMotion) return PhotoShockwaveTransform()
    val localMs = processingElapsedMs - PhotoShockwaveCalibration.triggerMs
    if (localMs <= 0L || localMs >= PhotoShockwaveCalibration.durationMs) {
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
                translationYDp =
                    PhotoShockwaveCalibration.maxUpwardTranslationDp +
                        (PhotoShockwaveCalibration.maxReboundTranslationDp -
                            PhotoShockwaveCalibration.maxUpwardTranslationDp) * p,
                rotationDegrees =
                    PhotoShockwaveCalibration.maxRotationDegrees -
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
