package com.yujian.ai.ui.recognition

import com.yujian.ai.ui.identify.DisplayRect
import com.yujian.ai.ui.identify.RecognitionContentScaleMode
import com.yujian.ai.ui.identify.RecognitionImageTransform
import com.yujian.ai.ui.identify.calculateRecognitionImageTransform

data class RecognitionPhotoFlowPlan(
    val startTransform: RecognitionImageTransform,
    val targetTransform: RecognitionImageTransform,
    val targetPhotoRect: DisplayRect,
    val statusPanelTopPx: Float,
)

/** One-source photo entry geometry for both CameraX and Photo Picker sessions. */
fun planRecognitionPhotoFlow(
    sourceWidth: Int,
    sourceHeight: Int,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    density: Float,
    safeLeftPx: Float,
    safeTopPx: Float,
    safeRightPx: Float,
    safeBottomPx: Float,
    cameraSource: Boolean,
): RecognitionPhotoFlowPlan {
    require(sourceWidth > 0 && sourceHeight > 0)
    require(viewportWidthPx > 0f && viewportHeightPx > 0f && density > 0f)

    val margin = 16f * density
    val contentLeft = maxOf(safeLeftPx, margin)
    val contentRight = minOf(viewportWidthPx - safeRightPx, viewportWidthPx - margin)
    val contentWidth = (contentRight - contentLeft).coerceAtLeast(1f)
    val contentTop = safeTopPx
    val contentBottom = (viewportHeightPx - safeBottomPx).coerceAtLeast(contentTop + 1f)
    val contentHeight = contentBottom - contentTop

    val navHeight = 52f * density
    val navGap = 16f * density
    val panelHeight = 88f * density
    val panelGap = 20f * density
    val targetTop = contentTop + navHeight + navGap
    val maxTargetHeight = minOf(
        contentHeight * .52f,
        (contentBottom - targetTop - panelGap - panelHeight).coerceAtLeast(1f),
    )
    val targetLocal = calculateRecognitionImageTransform(
        containerWidth = contentWidth,
        containerHeight = maxTargetHeight,
        imageWidth = sourceWidth,
        imageHeight = sourceHeight,
        contentScaleMode = RecognitionContentScaleMode.FIT,
    )
    val target = targetLocal.inViewport(
        left = contentLeft,
        top = targetTop,
        viewportWidth = viewportWidthPx,
        viewportHeight = viewportHeightPx,
    )

    val start = if (cameraSource) {
        calculateRecognitionImageTransform(
            containerWidth = viewportWidthPx,
            containerHeight = viewportHeightPx,
            imageWidth = sourceWidth,
            imageHeight = sourceHeight,
            contentScaleMode = RecognitionContentScaleMode.CROP,
        )
    } else {
        val startWidth = contentWidth * .88f
        val startHeight = contentHeight * .68f
        val startCenterX = (contentLeft + contentRight) * .5f
        val startCenterY = contentTop + contentHeight * .44f
        val startLocal = calculateRecognitionImageTransform(
            containerWidth = startWidth,
            containerHeight = startHeight,
            imageWidth = sourceWidth,
            imageHeight = sourceHeight,
            contentScaleMode = RecognitionContentScaleMode.FIT,
        )
        startLocal.inViewport(
            left = startCenterX - startWidth * .5f,
            top = startCenterY - startHeight * .5f,
            viewportWidth = viewportWidthPx,
            viewportHeight = viewportHeightPx,
        )
    }

    return RecognitionPhotoFlowPlan(
        startTransform = start,
        targetTransform = target,
        targetPhotoRect = target.photoContentRect,
        statusPanelTopPx = targetTop + maxTargetHeight + panelGap,
    )
}

fun interpolateRecognitionPhotoTransform(
    start: RecognitionImageTransform,
    target: RecognitionImageTransform,
    progress: Float,
): RecognitionImageTransform {
    require(start.sourceWidthPx == target.sourceWidthPx && start.sourceHeightPx == target.sourceHeightPx)
    val t = progress.coerceIn(0f, 1f)
    val q = t * t * (3f - 2f * t)
    fun mix(a: Float, b: Float) = a + (b - a) * q
    return target.copy(
        scale = mix(start.scale, target.scale),
        translationX = mix(start.translationX, target.translationX),
        translationY = mix(start.translationY, target.translationY),
        drawnWidth = mix(start.drawnWidth, target.drawnWidth),
        drawnHeight = mix(start.drawnHeight, target.drawnHeight),
        visibleSourceRect = start.visibleSourceRect.let { from ->
            val to = target.visibleSourceRect
            com.yujian.ai.ui.identify.NormalizedSourceRect(
                left = mix(from.left, to.left),
                top = mix(from.top, to.top),
                right = mix(from.right, to.right),
                bottom = mix(from.bottom, to.bottom),
            )
        },
    )
}

private fun RecognitionImageTransform.inViewport(
    left: Float,
    top: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): RecognitionImageTransform = copy(
    viewportWidthPx = viewportWidth,
    viewportHeightPx = viewportHeight,
    translationX = translationX + left,
    translationY = translationY + top,
)
