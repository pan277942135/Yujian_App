package com.yujian.ai.ui.identify

import com.yujian.ai.ai.NormalizedFishBox
import kotlin.math.max

data class DisplayPoint(val x: Float, val y: Float)

/** ContentScale.Crop transform shared by the photo and detector highlight. */
data class RecognitionImageTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val drawnWidth: Float,
    val drawnHeight: Float,
) {
    fun mapNormalized(x: Float, y: Float): DisplayPoint = DisplayPoint(
        offsetX + x.coerceIn(0f, 1f) * drawnWidth,
        offsetY + y.coerceIn(0f, 1f) * drawnHeight,
    )

    fun mapBox(box: NormalizedFishBox): DisplayPoint {
        val normalized = box.normalized()
        return mapNormalized(
            normalized.x1 + normalized.width / 2f,
            normalized.y1 + normalized.height / 2f,
        )
    }
}

fun calculateRecognitionImageTransform(
    containerWidth: Float,
    containerHeight: Float,
    imageWidth: Int,
    imageHeight: Int,
): RecognitionImageTransform {
    require(containerWidth > 0f && containerHeight > 0f)
    require(imageWidth > 0 && imageHeight > 0)
    val scale = max(containerWidth / imageWidth, containerHeight / imageHeight)
    val drawnWidth = imageWidth * scale
    val drawnHeight = imageHeight * scale
    return RecognitionImageTransform(
        scale = scale,
        offsetX = (containerWidth - drawnWidth) / 2f,
        offsetY = (containerHeight - drawnHeight) / 2f,
        drawnWidth = drawnWidth,
        drawnHeight = drawnHeight,
    )
}
