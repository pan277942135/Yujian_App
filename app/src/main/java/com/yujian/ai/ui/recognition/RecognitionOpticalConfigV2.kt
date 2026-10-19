package com.yujian.ai.ui.recognition

/** Versioned Android mapping of design/system/recognition_optical_v2/RecognitionOpticalConfigV2.json. */
internal object RecognitionOpticalConfigV2 {
    const val VERSION = "2.0.0"
    const val REFERENCE_WIDTH_PX = 600f

    const val CORE_WIDTH_PX = 2.8f
    const val CORE_INTENSITY = .92f
    const val INNER_BLOOM_RADIUS_PX = 20f
    const val INNER_BLOOM_INTENSITY = .68f
    const val INNER_BLOOM_ALPHA_MODIFIER = .78f
    const val OUTER_BLOOM_RADIUS_PX = 62f
    const val OUTER_BLOOM_INTENSITY = .48f
    const val OUTER_BLOOM_ALPHA_MODIFIER = .74f
    const val ATMOSPHERIC_SPILL_RADIUS_PX = 105f
    const val ATMOSPHERIC_SPILL_INTENSITY = .23f

    const val BRIGHT_LUMINANCE_THRESHOLD = .46f
    const val BRIGHT_LUMINANCE_MAX = .88f
    const val BRIGHT_BACKGROUND_MAX_GAIN = .28f

    private val islandStrengths = mapOf(
        "G1" to 0.90f,
        "B1" to 1.00f,
        "G3" to 0.88f,
        "B3" to 0.84f,
    )

    fun islandStrength(islandId: String): Float = islandStrengths[islandId] ?: 0f

    fun photoScale(displayedPhotoWidthPx: Float): Float =
        (displayedPhotoWidthPx / REFERENCE_WIDTH_PX).coerceAtLeast(.01f)

    fun localBackgroundGain(luminance: Float): Float {
        val over = (luminance.coerceIn(0f, 1f) - BRIGHT_LUMINANCE_THRESHOLD).coerceAtLeast(0f)
        val range = BRIGHT_LUMINANCE_MAX - BRIGHT_LUMINANCE_THRESHOLD
        return (1f + BRIGHT_BACKGROUND_MAX_GAIN * over / range)
            .coerceIn(1f, 1f + BRIGHT_BACKGROUND_MAX_GAIN)
    }

    fun layerDimensions(displayedPhotoWidthPx: Float, quality: RecognitionQualityLevel): RecognitionOpticalLayerDimensions {
        val scale = photoScale(displayedPhotoWidthPx)
        val outerScale = when (quality) {
            RecognitionQualityLevel.FULL -> 1f
            RecognitionQualityLevel.BALANCED -> .75f
            RecognitionQualityLevel.LITE -> .50f
        }
        return RecognitionOpticalLayerDimensions(
            coreWidthPx = CORE_WIDTH_PX * scale,
            innerRadiusPx = INNER_BLOOM_RADIUS_PX * scale,
            outerRadiusPx = OUTER_BLOOM_RADIUS_PX * scale * outerScale,
            spillRadiusPx = ATMOSPHERIC_SPILL_RADIUS_PX * scale * outerScale,
        )
    }
}

internal data class RecognitionOpticalLayerDimensions(
    val coreWidthPx: Float,
    val innerRadiusPx: Float,
    val outerRadiusPx: Float,
    val spillRadiusPx: Float,
)

internal enum class RecognitionOpticalBlend {
    SOURCE_OVER,
    SCREEN,
    SOFT_LIGHT,
}
