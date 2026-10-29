package com.yujian.ai.ui.recognition.reveal

import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.subject.FishSubjectQuality
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.identify.DisplayPoint
import com.yujian.ai.ui.identify.NormalizedSourceRect
import com.yujian.ai.ui.identify.RecognitionImageTransform
import com.yujian.ai.ui.recognition.RecognitionContourSegment

data class RecognitionRevealKey(
    val recognitionSessionId: String,
    val recognitionAttemptId: String,
)

data class RecognitionRevealWindowRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val center: DisplayPoint get() = DisplayPoint((left + right) / 2f, (top + bottom) / 2f)

    fun isValid(): Boolean = listOf(left, top, right, bottom).all(Float::isFinite) &&
        width > 1f && height > 1f
}

enum class RecognitionGeometryTrustState {
    TRUSTED_MASK,
    TRUSTED_BBOX_ONLY,
    UNTRUSTED,
    MISSING,
}

enum class RecognitionRevealEligibility {
    FULL_REVEAL,
    TITLE_FADE_ONLY,
    PHOTO_ONLY,
    STATIC_RESULT,
}

/**
 * Geometry copied from the Processing attempt. The contour is normalized to
 * the segmentation ROI; subjectBox maps that ROI back to the EXIF-normalized
 * source bitmap. No Bitmap is carried in this handoff object.
 */
data class RecognitionSubjectGeometrySnapshot(
    val imageId: String,
    val recognitionSessionId: String = "",
    val recognitionAttemptId: String = "",
    val photoSha256: String = "",
    val sourceWidthPx: Int,
    val sourceHeightPx: Int,
    val detectorBox: NormalizedFishBox,
    val detectorConfidence: Float,
    val subjectBox: NormalizedSourceRect,
    val contour: List<RecognitionContourSegment>,
    val quality: FishSubjectQuality?,
    val maskSize: Int,
    val expectedMaskSize: Int,
)

data class RecognitionProcessingPhotoSnapshot(
    val imageId: String,
    val viewportBoundsInWindow: RecognitionRevealWindowRect,
    val sourceToViewport: RecognitionImageTransform,
)

data class RecognitionRevealHandoff(
    val key: RecognitionRevealKey,
    val imageId: String,
    val photoSourceIdentity: String,
    val normalizedSourceWidth: Int,
    val normalizedSourceHeight: Int,
    /** Rotation already applied by RecognitionImageStore; detector coordinates use this bitmap. */
    val sourceOrientationDegreesApplied: Int,
    val trustedDetectorBBox: NormalizedSourceRect?,
    val detectorConfidence: Float?,
    val subjectMaskOrContour: List<RecognitionContourSegment>,
    val subjectRoiInSource: NormalizedSourceRect?,
    val subjectAnchorNormalizedSource: DisplayPoint?,
    val contourSourceSpace: String,
    val geometryTrustState: RecognitionGeometryTrustState,
    val processingPhotoViewportBounds: RecognitionRevealWindowRect?,
    val processingPhotoTransform: RecognitionImageTransform?,
    val processingSubjectAnchor: DisplayPoint?,
    val resultIdentity: String?,
    val eligibility: RecognitionRevealEligibility,
    val handoffTimestampUptimeMs: Long,
)

object RecognitionRevealHandoffFactory {
    fun create(
        recognitionSessionId: String,
        recognitionAttemptId: String,
        image: SelectedImage,
        result: ProductionRecognitionResult,
        subject: RecognitionSubjectGeometrySnapshot?,
        processingPhoto: RecognitionProcessingPhotoSnapshot?,
        highConfidenceResult: Boolean,
        handoffTimestampUptimeMs: Long,
    ): RecognitionRevealHandoff {
        val key = RecognitionRevealKey(recognitionSessionId, recognitionAttemptId)
        val primary = result.assessment.primary
        val rawBox = primary?.box?.takeIf(RecognitionRevealPolicy::isValidBox)
        val bbox = rawBox?.toNormalizedRect()
        val confidence = primary?.confidence?.takeIf { it.isFinite() && it in 0f..1f }
        val geometryTrust = RecognitionRevealPolicy.geometryTrust(
            detectorBox = primary?.box,
            subject = subject,
            imageId = image.imageId,
            sourceWidthPx = image.bitmap.width,
            sourceHeightPx = image.bitmap.height,
            detectorConfidence = confidence,
            recognitionSessionId = recognitionSessionId,
            recognitionAttemptId = recognitionAttemptId,
            photoSha256 = image.photoSha256,
        )
        val maskTrusted = geometryTrust == RecognitionGeometryTrustState.TRUSTED_MASK
        val photo = processingPhoto?.takeIf {
            it.imageId == image.imageId &&
                it.viewportBoundsInWindow.isValid() &&
                it.sourceToViewport.sourceWidthPx == image.bitmap.width &&
                it.sourceToViewport.sourceHeightPx == image.bitmap.height
        }
        val anchorSource = if (maskTrusted) {
            bottomContourAnchor(requireNotNull(subject).contour, subject.subjectBox)
        } else {
            null
        }
        val anchor = if (photo != null && anchorSource != null) {
            val mapped = photo.sourceToViewport.mapNormalized(anchorSource.x, anchorSource.y)
            DisplayPoint(
                x = photo.viewportBoundsInWindow.left + mapped.x,
                y = photo.viewportBoundsInWindow.top + mapped.y,
            ).takeIf { point ->
                point.x.isFinite() && point.y.isFinite() &&
                    point.x in photo.viewportBoundsInWindow.left..photo.viewportBoundsInWindow.right &&
                    point.y in photo.viewportBoundsInWindow.top..photo.viewportBoundsInWindow.bottom
            }
        } else {
            null
        }
        val resultIdentity = result.prediction?.top1?.speciesKey?.takeIf(String::isNotBlank)
        val identityValid = resultIdentity != null &&
            result.prediction?.top1?.speciesName?.isNotBlank() == true
        val eligibility = RecognitionRevealPolicy.eligibility(
            resultReady = result.ready,
            identityValid = identityValid,
            highConfidenceResult = highConfidenceResult,
            geometryTrust = geometryTrust,
            processingPhotoValid = photo != null,
            anchorValid = anchor != null,
        )
        val contour = if (maskTrusted) subject?.contour?.toList().orEmpty() else emptyList()
        val subjectBox = if (maskTrusted) subject?.subjectBox else null

        return RecognitionRevealHandoff(
            key = key,
            imageId = image.imageId,
            photoSourceIdentity = "${image.imageId}|${image.source}|${image.bitmap.width}x${image.bitmap.height}|sha256=${image.photoSha256}",
            normalizedSourceWidth = image.bitmap.width,
            normalizedSourceHeight = image.bitmap.height,
            sourceOrientationDegreesApplied = image.sourceOrientationDegrees,
            trustedDetectorBBox = bbox,
            detectorConfidence = confidence,
            subjectMaskOrContour = contour,
            subjectRoiInSource = subjectBox,
            subjectAnchorNormalizedSource = anchorSource,
            contourSourceSpace = if (maskTrusted) "SEGMENTATION_ROI_NORMALIZED" else "UNAVAILABLE",
            geometryTrustState = geometryTrust,
            processingPhotoViewportBounds = photo?.viewportBoundsInWindow,
            processingPhotoTransform = photo?.sourceToViewport,
            processingSubjectAnchor = anchor,
            resultIdentity = resultIdentity,
            eligibility = eligibility,
            handoffTimestampUptimeMs = handoffTimestampUptimeMs,
        )
    }

    private fun NormalizedFishBox.toNormalizedRect() = NormalizedSourceRect(x1, y1, x2, y2)

    private fun bottomContourAnchor(
        contour: List<RecognitionContourSegment>,
        roi: NormalizedSourceRect,
    ): DisplayPoint? {
        val points = contour.flatMap { listOf(it.startX to it.startY, it.endX to it.endY) }
        if (points.isEmpty()) return null
        val lowest = points.maxOf { it.second }
        val lowerBand = points.filter { it.second >= lowest - 0.025f }
        if (lowerBand.isEmpty()) return null
        val x = lowerBand.map { it.first }.average().toFloat()
        val y = lowerBand.map { it.second }.average().toFloat()
        val sourceX = roi.left + x * roi.width
        val sourceY = roi.top + y * roi.height
        return DisplayPoint(sourceX, sourceY).takeIf {
            it.x.isFinite() && it.y.isFinite() && it.x in 0f..1f && it.y in 0f..1f
        }
    }
}
