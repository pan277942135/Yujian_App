package com.yujian.ai.ui.identify

internal data class RecognitionGuideReferencePoint(val x: Float, val y: Float)

internal data class RecognitionGuideReferenceMapping(
    val referenceWidthPx: Int,
    val referenceHeightPx: Int,
    val runtimeWidthPx: Int,
    val runtimeHeightPx: Int,
    val uniformScale: Float,
    val offsetXPx: Float,
    val offsetYPx: Float,
) {
    fun map(referenceX: Float, referenceY: Float): RecognitionGuideReferencePoint =
        RecognitionGuideReferencePoint(
            x = offsetXPx + referenceX * uniformScale,
            y = offsetYPx + referenceY * uniformScale,
        )
}

/** Maps frozen-page coordinates to a runtime viewport without independent X/Y scaling. */
internal fun mapRecognitionGuideReferenceSpace(
    page: RecognitionFirstCaptureGuidePage,
    runtimeWidthPx: Int,
    runtimeHeightPx: Int,
): RecognitionGuideReferenceMapping {
    require(runtimeWidthPx > 0 && runtimeHeightPx > 0) { "Runtime viewport must be positive" }
    val referenceHeight = if (page == RecognitionFirstCaptureGuidePage.P03) 1672 else 1671
    val scale = runtimeWidthPx / 941f
    val scaledHeight = referenceHeight * scale
    return RecognitionGuideReferenceMapping(
        referenceWidthPx = 941,
        referenceHeightPx = referenceHeight,
        runtimeWidthPx = runtimeWidthPx,
        runtimeHeightPx = runtimeHeightPx,
        uniformScale = scale,
        offsetXPx = 0f,
        offsetYPx = (runtimeHeightPx - scaledHeight) / 2f,
    )
}
