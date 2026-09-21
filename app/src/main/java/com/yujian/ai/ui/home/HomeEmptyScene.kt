package com.yujian.ai.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import kotlin.math.max

internal data class HomeCropTransform(
    val scale: Float,
    val renderedWidthPx: Float,
    val renderedHeightPx: Float,
    val cropLeftPx: Float,
    val cropTopPx: Float,
)

internal fun calculateHomeCropTransform(
    containerWidthPx: Float,
    containerHeightPx: Float,
    sourceWidthPx: Float,
    sourceHeightPx: Float,
): HomeCropTransform {
    val scale = max(containerWidthPx / sourceWidthPx, containerHeightPx / sourceHeightPx)
    val renderedWidth = sourceWidthPx * scale
    val renderedHeight = sourceHeightPx * scale
    return HomeCropTransform(
        scale = scale,
        renderedWidthPx = renderedWidth,
        renderedHeightPx = renderedHeight,
        cropLeftPx = (containerWidthPx - renderedWidth) / 2f,
        cropTopPx = (containerHeightPx - renderedHeight) / 2f,
    )
}

internal fun mapHomeNormalizedAnchor(
    transform: HomeCropTransform,
    normalizedX: Float,
    normalizedY: Float,
): Offset = Offset(
    x = transform.cropLeftPx + normalizedX * transform.renderedWidthPx,
    y = transform.cropTopPx + normalizedY * transform.renderedHeightPx,
)

/** Empty Home's runtime layer renderer entry point. */
@Composable
internal fun HomeEmptyScene(
    modifier: Modifier = Modifier,
    motionState: HomeMotionState = rememberHomeMotionState(),
    runtimeAssets: EmptyHomeRuntimeAssets? = rememberEmptyHomeRuntimeAssets(),
) {
    EmptyHomeSceneRenderer(
        modifier = modifier,
        motionState = motionState,
        runtimeAssets = runtimeAssets,
    )
}
