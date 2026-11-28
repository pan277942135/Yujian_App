package com.yujian.ai.ai.subject

import com.yujian.ai.ai.FishDetectionQualityGate
import com.yujian.ai.ai.NormalizedFishBox
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Reference Fish-Mask quality rules adapted to YuJian's right/bottom-exclusive photo pixels. */
object SlimSamMaskQualityGate {
    const val DETECTION_CLIP_PADDING_RATIO = 0.45f
    const val MIN_DETECTOR_CONFIDENCE = 0.45f
    const val MIN_DETECTION_WIDTH_PX = 35
    const val MIN_DETECTION_HEIGHT_PX = 35
    const val MIN_MASK_PIXELS = 60
    const val MIN_COVERAGE = 0.18f
    const val MAX_COVERAGE = 0.85f
    const val MIN_MAJOR_SPAN = 0.48f
    const val MIN_IOU = 0.60f
    const val MIN_CLOSED_CONTOUR_POINTS = 20

    data class PixelRect(
        val left: Int,
        val top: Int,
        val rightExclusive: Int,
        val bottomExclusive: Int,
    ) {
        val width: Int get() = rightExclusive - left
        val height: Int get() = bottomExclusive - top
        val area: Long get() = width.toLong() * height
        fun isValidWithin(width: Int, height: Int): Boolean =
            left >= 0 && top >= 0 && rightExclusive <= width && bottomExclusive <= height &&
                this.width > 0 && this.height > 0
    }

    data class DetectionGeometry(
        val detection: PixelRect,
        val expandedRoi: PixelRect,
        val confidence: Float,
    ) {
        val isBboxTrustworthy: Boolean get() = confidence.isFinite() &&
            confidence >= MIN_DETECTOR_CONFIDENCE &&
            detection.width >= MIN_DETECTION_WIDTH_PX &&
            detection.height >= MIN_DETECTION_HEIGHT_PX
    }

    data class CandidateGeometry(
        val iou: Float,
        val pixelCount: Int,
        val bounds: PixelRect,
    )

    data class CandidateMetrics(
        val coverage: Float,
        val majorSpan: Float,
        val spanScore: Float,
        val rankScore: Float,
        val geometryTrusted: Boolean,
    )

    /** Uses YuJian's floor/ceil exclusive detection crop, then reference 45% round-to-nearest padding. */
    fun detectionGeometry(
        box: NormalizedFishBox,
        confidence: Float,
        photoWidth: Int,
        photoHeight: Int,
    ): DetectionGeometry? {
        if (!box.isValidNormalized() || !confidence.isFinite() || photoWidth <= 0 || photoHeight <= 0) return null
        val crop = FishDetectionQualityGate.cropBoxPixels(box, photoWidth, photoHeight)
        val detection = PixelRect(crop[0], crop[1], crop[2], crop[3])
        val padX = (detection.width * DETECTION_CLIP_PADDING_RATIO).roundToInt()
        val padY = (detection.height * DETECTION_CLIP_PADDING_RATIO).roundToInt()
        val roi = PixelRect(
            left = (detection.left - padX).coerceAtLeast(0),
            top = (detection.top - padY).coerceAtLeast(0),
            rightExclusive = (detection.rightExclusive + padX).coerceAtMost(photoWidth),
            bottomExclusive = (detection.bottomExclusive + padY).coerceAtMost(photoHeight),
        )
        if (!detection.isValidWithin(photoWidth, photoHeight) || !roi.isValidWithin(photoWidth, photoHeight)) return null
        return DetectionGeometry(detection, roi, confidence)
    }

    /** Reference score: 0.4 IoU + 0.3 pixel coverage + 0.3 two-axis span. */
    fun evaluate(candidate: CandidateGeometry, detection: DetectionGeometry): CandidateMetrics {
        val target = detection.detection
        val targetArea = max(1L, target.area).toFloat()
        val coverage = candidate.pixelCount.toFloat() / targetArea
        val rankCoverage = min(candidate.pixelCount.toFloat() / (targetArea * 0.35f), 1f)
        val spanH = min(candidate.bounds.height.toFloat() / max(1f, target.height * 0.7f), 1f)
        val spanW = min(candidate.bounds.width.toFloat() / max(1f, target.width * 0.7f), 1f)
        val spanScore = spanH * spanW
        val vertical = target.height > target.width * 1.1f
        val majorSpan = if (vertical) {
            candidate.bounds.height.toFloat() / max(1, target.height)
        } else {
            candidate.bounds.width.toFloat() / max(1, target.width)
        }
        val trusted = detection.isBboxTrustworthy &&
            candidate.pixelCount >= MIN_MASK_PIXELS &&
            coverage in MIN_COVERAGE..MAX_COVERAGE &&
            majorSpan >= MIN_MAJOR_SPAN &&
            candidate.iou.isFinite() && candidate.iou >= MIN_IOU
        return CandidateMetrics(
            coverage = coverage,
            majorSpan = majorSpan,
            spanScore = spanScore,
            rankScore = candidate.iou * 0.4f + rankCoverage * 0.3f + spanScore * 0.3f,
            geometryTrusted = trusted,
        )
    }
}
