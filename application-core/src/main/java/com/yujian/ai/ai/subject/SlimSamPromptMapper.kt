package com.yujian.ai.ai.subject

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pixel-exact prompt generation and resize mapping from the pinned Fish-Mask demo. */
object SlimSamPromptMapper {
    data class PromptPoint(val x: Float, val y: Float, val label: Long)

    fun fromDetection(
        detection: SlimSamMaskQualityGate.PixelRect,
        imageWidth: Int,
        imageHeight: Int,
        resizedWidth: Int,
        resizedHeight: Int,
    ): List<PromptPoint> {
        require(detection.isValidWithin(imageWidth, imageHeight))
        require(resizedWidth > 0 && resizedHeight > 0)
        val bx1 = detection.left
        val by1 = detection.top
        val bx2 = detection.rightExclusive
        val by2 = detection.bottomExclusive
        val bw = max(20, bx2 - bx1)
        val bh = max(20, by2 - by1)
        val bcx = ((bx1 + bx2) / 2f).roundToInt()
        val bcy = ((by1 + by2) / 2f).roundToInt()
        val points = if (bh > bw * 1.15f) {
            listOf(
                PromptPoint(bcx.toFloat(), (by1 + bh * .56f).roundToInt().toFloat(), 1L),
                PromptPoint(bcx.toFloat(), (by1 + bh * .28f).roundToInt().toFloat(), 1L),
                PromptPoint(bcx.toFloat(), (by1 + bh * .44f).roundToInt().toFloat(), 1L),
                PromptPoint(bcx.toFloat(), (by1 + bh * .72f).roundToInt().toFloat(), 1L),
                PromptPoint(bcx.toFloat(), (by1 + bh * .88f).roundToInt().toFloat(), 1L),
                PromptPoint(max(bx1 + 8, (bcx - bw * .22f).roundToInt()).toFloat(),
                    (by1 + bh * .55f).roundToInt().toFloat(), 1L),
                PromptPoint(min(bx2 - 8, (bcx + bw * .22f).roundToInt()).toFloat(),
                    (by1 + bh * .55f).roundToInt().toFloat(), 1L),
                PromptPoint(bcx.toFloat(), max(0, by1 - 10).toFloat(), 0L),
            )
        } else {
            listOf(
                PromptPoint(bcx.toFloat(), bcy.toFloat(), 1L),
                PromptPoint((bx1 + bw * .20f).roundToInt().toFloat(), bcy.toFloat(), 1L),
                PromptPoint((bx1 + bw * .38f).roundToInt().toFloat(), bcy.toFloat(), 1L),
                PromptPoint((bx1 + bw * .62f).roundToInt().toFloat(), bcy.toFloat(), 1L),
                PromptPoint((bx1 + bw * .80f).roundToInt().toFloat(), bcy.toFloat(), 1L),
                PromptPoint(bcx.toFloat(), max(by1 + 5, (bcy - bh * .2f).roundToInt()).toFloat(), 1L),
                PromptPoint(bcx.toFloat(), min(by2 - 5, (bcy + bh * .2f).roundToInt()).toFloat(), 1L),
            )
        }
        return points.map { point ->
            val x = point.x.roundToInt().coerceIn(0, imageWidth - 1)
            val y = point.y.roundToInt().coerceIn(0, imageHeight - 1)
            PromptPoint(
                x = (x.toFloat() * resizedWidth / imageWidth).coerceIn(0f, resizedWidth.toFloat()),
                y = (y.toFloat() * resizedHeight / imageHeight).coerceIn(0f, resizedHeight.toFloat()),
                label = point.label,
            )
        }
    }
}
