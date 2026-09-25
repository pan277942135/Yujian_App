package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.NormalizedFishBox
import kotlin.math.max
import kotlin.math.min

data class DisplayedImageRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** Maps normalized detector coordinates into the actual displayed image transform. */
data class DisplayedImageTransform(
    val imageWidth: Int,
    val imageHeight: Int,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val scale: Float,
    val left: Float,
    val top: Float,
) {
    val displayedWidth: Float get() = imageWidth * scale
    val displayedHeight: Float get() = imageHeight * scale

    fun map(box: NormalizedFishBox): DisplayedImageRect {
        val normalized = box.normalized()
        return DisplayedImageRect(
            left + normalized.x1 * displayedWidth,
            top + normalized.y1 * displayedHeight,
            left + normalized.x2 * displayedWidth,
            top + normalized.y2 * displayedHeight,
        )
    }

    companion object {
        fun fit(imageWidth: Int, imageHeight: Int, viewportWidth: Float, viewportHeight: Float): DisplayedImageTransform =
            create(imageWidth, imageHeight, viewportWidth, viewportHeight, crop = false)

        fun crop(imageWidth: Int, imageHeight: Int, viewportWidth: Float, viewportHeight: Float): DisplayedImageTransform =
            create(imageWidth, imageHeight, viewportWidth, viewportHeight, crop = true)

        private fun create(
            imageWidth: Int,
            imageHeight: Int,
            viewportWidth: Float,
            viewportHeight: Float,
            crop: Boolean,
        ): DisplayedImageTransform {
            require(imageWidth > 0 && imageHeight > 0)
            require(viewportWidth > 0f && viewportHeight > 0f)
            val widthScale = viewportWidth / imageWidth
            val heightScale = viewportHeight / imageHeight
            val scale = if (crop) max(widthScale, heightScale) else min(widthScale, heightScale)
            val drawnWidth = imageWidth * scale
            val drawnHeight = imageHeight * scale
            return DisplayedImageTransform(
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                scale = scale,
                left = (viewportWidth - drawnWidth) / 2f,
                top = (viewportHeight - drawnHeight) / 2f,
            )
        }
    }
}

/** The image store applies EXIF rotation before detection; this helper documents/test-covers that contract. */
fun NormalizedFishBox.rotateClockwise(rotationDegrees: Int): NormalizedFishBox = when ((rotationDegrees % 360 + 360) % 360) {
    90 -> NormalizedFishBox(1f - y2, x1, 1f - y1, x2)
    180 -> NormalizedFishBox(1f - x2, 1f - y2, 1f - x1, 1f - y1)
    270 -> NormalizedFishBox(y1, 1f - x2, y2, 1f - x1)
    else -> this
}
