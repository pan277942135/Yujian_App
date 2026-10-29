package com.yujian.ai.ui.recognition.reveal

import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ai.subject.FishSubjectQuality
import com.yujian.ai.ui.identify.NormalizedSourceRect

/** Pure trust and eligibility rules shared by production handoff and unit tests. */
object RecognitionRevealPolicy {
    fun geometryTrust(
        detectorBox: NormalizedFishBox?,
        subject: RecognitionSubjectGeometrySnapshot?,
        imageId: String,
        sourceWidthPx: Int,
        sourceHeightPx: Int,
        detectorConfidence: Float?,
        recognitionSessionId: String? = null,
        recognitionAttemptId: String? = null,
        photoSha256: String? = null,
    ): RecognitionGeometryTrustState {
        if (detectorBox == null) return RecognitionGeometryTrustState.MISSING
        if (!isValidBox(detectorBox)) return RecognitionGeometryTrustState.UNTRUSTED
        if (subject == null) return RecognitionGeometryTrustState.TRUSTED_BBOX_ONLY
        val matches = subject.imageId == imageId &&
            subject.sourceWidthPx == sourceWidthPx && subject.sourceHeightPx == sourceHeightPx &&
            subject.detectorBox == detectorBox && detectorConfidence != null &&
            subject.detectorConfidence == detectorConfidence &&
            (recognitionSessionId == null || subject.recognitionSessionId == recognitionSessionId) &&
            (recognitionAttemptId == null || subject.recognitionAttemptId == recognitionAttemptId) &&
            (photoSha256 == null || subject.photoSha256 == photoSha256)
        val trustedMask = matches && subject.quality == FishSubjectQuality.GOOD &&
            subject.maskSize > 0 && subject.maskSize == subject.expectedMaskSize &&
            isValidRect(subject.subjectBox) && subject.contour.isNotEmpty() &&
            subject.contour.all { segment ->
                listOf(
                    segment.startX, segment.startY, segment.endX, segment.endY,
                    segment.control1X, segment.control1Y, segment.control2X, segment.control2Y,
                ).all { it.isFinite() && it in 0f..1f }
            }
        return if (trustedMask) RecognitionGeometryTrustState.TRUSTED_MASK
        else RecognitionGeometryTrustState.TRUSTED_BBOX_ONLY
    }

    fun eligibility(
        resultReady: Boolean,
        identityValid: Boolean,
        highConfidenceResult: Boolean,
        geometryTrust: RecognitionGeometryTrustState,
        processingPhotoValid: Boolean,
        anchorValid: Boolean,
    ): RecognitionRevealEligibility = when {
        !resultReady || !identityValid -> RecognitionRevealEligibility.STATIC_RESULT
        !highConfidenceResult -> RecognitionRevealEligibility.PHOTO_ONLY
        geometryTrust == RecognitionGeometryTrustState.TRUSTED_MASK &&
            processingPhotoValid && anchorValid -> RecognitionRevealEligibility.FULL_REVEAL
        else -> RecognitionRevealEligibility.TITLE_FADE_ONLY
    }

    fun isValidBox(box: NormalizedFishBox): Boolean =
        listOf(box.x1, box.y1, box.x2, box.y2).all(Float::isFinite) &&
            box.x1 >= 0f && box.y1 >= 0f && box.x2 <= 1f && box.y2 <= 1f &&
            box.x1 < box.x2 && box.y1 < box.y2

    private fun isValidRect(rect: NormalizedSourceRect): Boolean =
        listOf(rect.left, rect.top, rect.right, rect.bottom).all(Float::isFinite) &&
            rect.left >= 0f && rect.top >= 0f && rect.right <= 1f && rect.bottom <= 1f &&
            rect.left < rect.right && rect.top < rect.bottom
}
