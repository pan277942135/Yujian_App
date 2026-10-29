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

/**
 * 【效果02 (推荐)】四周光效冻结版 (深湖青与琥珀金调)
 * Default visual effect calibration.
 */
internal object Effect02NativeCalibration {
    const val sceneId = "fish-catch-recognition"
    const val displayName = "效果02 (默认推荐)"
    const val spreadDurationMs = 1_200L
    const val intensity = 1.80f
    const val speed = 1.00f
    const val marginRatio = .015f
    const val glowSizeDp = 45f
    const val bottomMaskStrength = 1.00f
    const val blendRatio = .80f
    const val blendMode = "overlay"

    const val goldColorArgb = 0xFF4D9B91L
    const val cyanColorArgb = 0xFFD89913L
    const val greenColorArgb = 0xFF719FBCL
}

/**
 * 湖面回响水波共振配置 (Water Resonance Config V1.0.0)
 */
internal object WaterResonanceCalibration {
    const val version = "1.0.0"
    const val durationMs = 2_600L
    const val freezeAtMs = 2_100L

    const val ambientColorArgb = 0xFF729E9BL
    const val ambientAlpha = 0.22f

    const val rippleColorArgb = 0xFFD9BE7DL
    const val rippleAlpha = 0.45f

    const val goldColorArgb = 0xFFF0DCA5L
    const val goldAlpha = 0.45f

    const val residueColorArgb = 0xFFC8AE76L
    const val residueAlpha = 0.22f

    const val fishFieldLengthRatio = 0.88f
    const val fishFieldWidthRatio = 0.72f
    const val softFeatherRatio = 0.10f
    const val wavefrontWidthRatio = 0.020f
    const val waveDistortionRatio = 0.007f

    val pulseStartTimes = longArrayOf(300L, 700L, 1100L)
    val pulseDurations = longArrayOf(550L, 550L, 550L)
    val pulseStartScales = floatArrayOf(0.42f, 0.52f, 0.60f)
    val pulseEndScales = floatArrayOf(0.80f, 0.88f, 0.95f)
    val pulsePeakAlphas = floatArrayOf(0.55f, 0.48f, 0.42f)
    val pulseColors = longArrayOf(0xFFD9BE7DL, 0xFFE2CFA0L, 0xFFA7C8BFL)
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

/** Relative SWOS environment intensity; zero is exact at P0 and P7. */
internal fun processingEnvironmentStrength(elapsedMs: Long): Float {
    val keys = longArrayOf(0L, 350L, 950L, 1_200L, 1_550L, 2_050L, 2_600L, 2_800L)
    val values = floatArrayOf(0f, 10f, 45f, 75f, 60f, 35f, 10f, 0f)
    val t = elapsedMs.coerceIn(keys.first(), keys.last())
    val upper = keys.indexOfFirst { it >= t }.let { if (it < 0) keys.lastIndex else it }
    if (upper == 0) return values[0]
    val lower = upper - 1
    val p = (t - keys[lower]).toFloat() / (keys[upper] - keys[lower]).toFloat()
    return values[lower] + (values[upper] - values[lower]) * smoothStep01(p)
}

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
