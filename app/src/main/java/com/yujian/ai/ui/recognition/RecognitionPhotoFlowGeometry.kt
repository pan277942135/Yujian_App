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
    val statusCenterXPx: Float,
    val orbCenterYPx: Float,
    val orbCoreSizePx: Float,
    val landscape: Boolean,
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
    cameraPreviewRect: DisplayRect? = null,
    diagnosticReservePx: Float = 0f,
): RecognitionPhotoFlowPlan {
    require(sourceWidth > 0 && sourceHeight > 0)
    require(viewportWidthPx > 0f && viewportHeightPx > 0f && density > 0f)

    val compact = viewportHeightPx / density < 600f || viewportWidthPx / density < 360f
    val landscape = viewportWidthPx > viewportHeightPx
    val margin = (if (compact) 12f else 16f) * density
    val contentLeft = maxOf(safeLeftPx, margin)
    val contentRight = minOf(viewportWidthPx - safeRightPx, viewportWidthPx - margin)
    val contentWidth = (contentRight - contentLeft).coerceAtLeast(1f)
    val contentTop = safeTopPx
    val contentBottom = (viewportHeightPx - safeBottomPx - diagnosticReservePx).coerceAtLeast(contentTop + 1f)
    val contentHeight = contentBottom - contentTop
    val navHeight = (if (compact) 44f else 48f) * density
    val navGap = (if (compact) 12f else 16f) * density
    val panelGap = (if (compact) 20f else 28f) * density
    val orbCore = (if (compact) 68f else 80f) * density
    val orbOuter = orbCore + 28f * density
    val statusHeight = 22f * density
    val statusGap = 12f * density
    val bottomGap = (if (compact) 16f else 24f) * density
    val targetTop = contentTop + navHeight + navGap
    val photoWidth = if (landscape) (contentWidth * .62f - 12f * density).coerceAtLeast(1f) else contentWidth
    val maxTargetHeight = if (landscape) {
        minOf(contentHeight * .72f, contentBottom - targetTop - 12f * density).coerceAtLeast(1f)
    } else {
        minOf(contentHeight * (if (compact) .50f else .56f),
            contentBottom - targetTop - panelGap - statusHeight - statusGap - orbOuter - bottomGap).coerceAtLeast(1f)
    }
    val fitted = calculateRecognitionImageTransform(
        containerWidth = photoWidth,
        containerHeight = maxTargetHeight,
        imageWidth = sourceWidth,
        imageHeight = sourceHeight,
        contentScaleMode = RecognitionContentScaleMode.FIT,
    )
    // Top-anchor the actual contained photo; status follows its real bottom, not an empty letterbox.
    val target = fitted.copy(translationY = 0f).inViewport(
        left = contentLeft,
        top = targetTop,
        viewportWidth = viewportWidthPx,
        viewportHeight = viewportHeightPx,
    )
    val statusCenterX = if (landscape) {
        contentLeft + contentWidth * .81f
    } else viewportWidthPx * .5f
    val statusTop = if (landscape) {
        targetTop + maxTargetHeight * .5f - (statusHeight + statusGap + orbOuter) * .5f
    } else target.photoContentRect.bottom + panelGap
    val orbCenterY = statusTop + statusHeight + statusGap + orbOuter * .5f

    val start = if (cameraSource) {
        val preview = cameraPreviewRect?.takeIf { it.width > 0f && it.height > 0f }
            ?: DisplayRect(0f, 0f, viewportWidthPx, viewportHeightPx)
        calculateRecognitionImageTransform(
            containerWidth = preview.width,
            containerHeight = preview.height,
            imageWidth = sourceWidth,
            imageHeight = sourceHeight,
            contentScaleMode = RecognitionContentScaleMode.CROP,
        ).inViewport(preview.left, preview.top, viewportWidthPx, viewportHeightPx)
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
        statusPanelTopPx = statusTop,
        statusCenterXPx = statusCenterX,
        orbCenterYPx = orbCenterY,
        orbCoreSizePx = orbCore,
        landscape = landscape,
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
