package com.yujian.ai.ui.designsystem.lakeglass

import kotlin.math.max

/** One CenterCrop matrix from the 1440x3120 Master into a measured viewport. */
internal data class LakeGlassViewportTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val viewportWidth: Float,
    val viewportHeight: Float,
) {
    fun masterToViewport(x: Float, y: Float): Pair<Float, Float> =
        (offsetX + x * scale) to (offsetY + y * scale)

    fun viewportToMaster(x: Float, y: Float): Pair<Float, Float> =
        ((x - offsetX) / scale) to ((y - offsetY) / scale)

    companion object {
        const val MASTER_WIDTH = 1440f
        const val MASTER_HEIGHT = 3120f

        fun centerCrop(width: Float, height: Float): LakeGlassViewportTransform {
            require(width > 0f && height > 0f)
            val scale = max(width / MASTER_WIDTH, height / MASTER_HEIGHT)
            return LakeGlassViewportTransform(
                scale = scale,
                offsetX = (width - MASTER_WIDTH * scale) * 0.5f,
                offsetY = (height - MASTER_HEIGHT * scale) * 0.5f,
                viewportWidth = width,
                viewportHeight = height,
            )
        }
    }
}
