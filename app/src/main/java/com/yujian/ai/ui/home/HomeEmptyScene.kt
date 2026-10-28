package com.yujian.ai.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Empty Home's runtime layer renderer entry point. */
@Composable
internal fun HomeEmptyScene(
    layoutMapping: EmptyHomeLayoutMapping,
    modifier: Modifier = Modifier,
    motionState: HomeMotionState = rememberHomeMotionState(),
    runtimeAssets: EmptyHomeRuntimeAssets? = rememberEmptyHomeRuntimeAssets(),
) {
    EmptyHomeSceneRenderer(
        modifier = modifier,
        motionState = motionState,
        runtimeAssets = runtimeAssets,
        sceneTransform = layoutMapping.sceneTransform,
    )
}
