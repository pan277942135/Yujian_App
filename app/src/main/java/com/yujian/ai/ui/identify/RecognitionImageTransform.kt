package com.yujian.ai.ui.identify

import com.yujian.ai.ai.NormalizedFishBox
import kotlin.math.max
import kotlin.math.min

enum class RecognitionContentScaleMode { CROP, FIT }

data class DisplayPoint(val x: Float, val y: Float)

data class DisplayRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val center: DisplayPoint get() = DisplayPoint((left + right) / 2f, (top + bottom) / 2f)
}

data class NormalizedSourceRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** One source-to-viewport transform shared by the Recognition photo and every fish overlay. */
data class RecognitionImageTransform(
    val sourceWidthPx: Int,
    val sourceHeightPx: Int,
    val viewportWidthPx: Float,
    val viewportHeightPx: Float,
    val contentScaleMode: RecognitionContentScaleMode,
    val scale: Float,
    val translationX: Float,
    val translationY: Float,
    val drawnWidth: Float,
    val drawnHeight: Float,
    val visibleSourceRect: NormalizedSourceRect,
) {
    val offsetX: Float get() = translationX
    val offsetY: Float get() = translationY

    fun mapNormalized(x: Float, y: Float): DisplayPoint = DisplayPoint(
        translationX + x.coerceIn(0f, 1f) * drawnWidth,
        translationY + y.coerceIn(0f, 1f) * drawnHeight,
    )

    fun mapNormalizedRect(rect: NormalizedSourceRect): DisplayRect {
        val normalized = NormalizedSourceRect(
            left = min(rect.left, rect.right).coerceIn(0f, 1f),
            top = min(rect.top, rect.bottom).coerceIn(0f, 1f),
            right = max(rect.left, rect.right).coerceIn(0f, 1f),
            bottom = max(rect.top, rect.bottom).coerceIn(0f, 1f),
        )
        val topLeft = mapNormalized(normalized.left, normalized.top)
        val bottomRight = mapNormalized(normalized.right, normalized.bottom)
        return DisplayRect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
    }

    fun mapSourcePixel(x: Float, y: Float): DisplayPoint = mapNormalized(
        x / sourceWidthPx.toFloat(),
        y / sourceHeightPx.toFloat(),
    )

    fun mapSourceRect(rect: NormalizedSourceRect): DisplayRect = mapNormalizedRect(rect)

    fun mapSubjectRelative(subjectBox: NormalizedFishBox, x: Float, y: Float): DisplayPoint {
        val subject = subjectBox.normalized()
        return mapNormalized(
            subject.x1 + subject.width * x.coerceIn(0f, 1f),
            subject.y1 + subject.height * y.coerceIn(0f, 1f),
        )
    }

    fun mapBox(box: NormalizedFishBox): DisplayPoint {
        return mapBoxRect(box).center
    }

    fun mapBoxRect(box: NormalizedFishBox): DisplayRect {
        val normalized = box.normalized()
        return mapNormalizedRect(
            NormalizedSourceRect(normalized.x1, normalized.y1, normalized.x2, normalized.y2),
        )
    }
}

fun calculateRecognitionImageTransform(
    containerWidth: Float,
    containerHeight: Float,
    imageWidth: Int,
    imageHeight: Int,
    contentScaleMode: RecognitionContentScaleMode = RecognitionContentScaleMode.CROP,
): RecognitionImageTransform {
    require(containerWidth > 0f && containerHeight > 0f)
    require(imageWidth > 0 && imageHeight > 0)
    val scale = when (contentScaleMode) {
        RecognitionContentScaleMode.CROP -> max(containerWidth / imageWidth, containerHeight / imageHeight)
        RecognitionContentScaleMode.FIT -> min(containerWidth / imageWidth, containerHeight / imageHeight)
    }
    val drawnWidth = imageWidth * scale
    val drawnHeight = imageHeight * scale
    val translationX = (containerWidth - drawnWidth) / 2f
    val translationY = (containerHeight - drawnHeight) / 2f
    val sourceLeftPx = ((0f - translationX) / scale).coerceIn(0f, imageWidth.toFloat())
    val sourceTopPx = ((0f - translationY) / scale).coerceIn(0f, imageHeight.toFloat())
    val sourceRightPx = ((containerWidth - translationX) / scale).coerceIn(0f, imageWidth.toFloat())
    val sourceBottomPx = ((containerHeight - translationY) / scale).coerceIn(0f, imageHeight.toFloat())
    return RecognitionImageTransform(
        sourceWidthPx = imageWidth,
        sourceHeightPx = imageHeight,
        viewportWidthPx = containerWidth,
        viewportHeightPx = containerHeight,
        contentScaleMode = contentScaleMode,
        scale = scale,
        translationX = translationX,
        translationY = translationY,
        drawnWidth = drawnWidth,
        drawnHeight = drawnHeight,
        visibleSourceRect = NormalizedSourceRect(
            left = sourceLeftPx / imageWidth,
            top = sourceTopPx / imageHeight,
            right = sourceRightPx / imageWidth,
            bottom = sourceBottomPx / imageHeight,
        ),
    )
}
