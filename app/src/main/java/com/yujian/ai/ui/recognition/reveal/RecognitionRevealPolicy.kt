package com.yujian.ai.ui.recognition.reveal

import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ai.subject.FishSubjectQuality
import com.yujian.ai.ui.identify.DisplayPoint
import com.yujian.ai.ui.identify.DisplayRect
import com.yujian.ai.ui.identify.RecognitionImageTransform
import com.yujian.ai.ui.identify.NormalizedSourceRect
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometry
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometryFactory
import com.yujian.ai.ui.waterlight.core.WaterLightPathPoint

/** Pure trust and eligibility rules shared by production handoff and unit tests. */
object RecognitionRevealPolicy {
    /** Orb return is eligible only after the real Result and Orb/Photo/Glyph geometry are ready. */
    fun canStartOnFirstResultFrame(
        eligibility: RecognitionRevealEligibility,
        photoIdentityMatches: Boolean,
        photoGeometryReady: Boolean,
        heroReady: Boolean,
        titleBoundsReady: Boolean,
        revealGeometryReady: Boolean,
        reduceMotion: Boolean,
    ): Boolean {
        if (reduceMotion || !photoIdentityMatches || !photoGeometryReady || !heroReady || !titleBoundsReady) {
            return false
        }
        return when (eligibility) {
            RecognitionRevealEligibility.FULL_REVEAL -> revealGeometryReady
            // Orb-only still needs real Orb/photo/title geometry for the energy
            // return and glyph formation. It only omits the fish contour.
            RecognitionRevealEligibility.TITLE_FADE_ONLY -> revealGeometryReady
            RecognitionRevealEligibility.PHOTO_ONLY,
            RecognitionRevealEligibility.STATIC_RESULT -> false
        }
    }

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
            subject.maskSha256.matches(Regex("(?i)^[a-f0-9]{64}$")) &&
            subject.maskSize > 0 && subject.maskSize == subject.expectedMaskSize &&
            isValidRect(subject.subjectBox) && subject.contour.isNotEmpty() &&
            roiContainsDetectorBox(subject.subjectBox, detectorBox, sourceWidthPx, sourceHeightPx) &&
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
        contourClosed: Boolean = false,
        pStarPathValid: Boolean = false,
    ): RecognitionRevealEligibility = when {
        !resultReady || !identityValid -> RecognitionRevealEligibility.STATIC_RESULT
        !highConfidenceResult -> RecognitionRevealEligibility.STATIC_RESULT
        geometryTrust == RecognitionGeometryTrustState.TRUSTED_MASK &&
            processingPhotoValid && anchorValid && contourClosed && pStarPathValid ->
            RecognitionRevealEligibility.FULL_REVEAL
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

    private fun roiContainsDetectorBox(
        roi: NormalizedSourceRect,
        box: NormalizedFishBox,
        sourceWidthPx: Int,
        sourceHeightPx: Int,
    ): Boolean {
        if (sourceWidthPx <= 0 || sourceHeightPx <= 0) return false
        val toleranceX = 1f / sourceWidthPx
        val toleranceY = 1f / sourceHeightPx
        return roi.left <= box.x1 + toleranceX && roi.top <= box.y1 + toleranceY &&
            roi.right >= box.x2 - toleranceX && roi.bottom >= box.y2 - toleranceY
    }
}

/** Geometry builders shared by the Stage-04/05 producer and its decision gate. */
object RecognitionRevealPathGeometry {
    fun perimeterArrival(
        transform: RecognitionImageTransform,
        detectorBox: NormalizedFishBox,
        releaseEndpoint: WaterLightPathPoint,
        seed: Int,
    ): WaterLightPathGeometry? {
        if (!releaseEndpoint.isValid() || !RecognitionRevealPolicy.isValidBox(detectorBox)) return null
        val rect = transform.photoContentRect
        if (!rect.isValid()) return null
        val detectorCenter = transform.mapBox(detectorBox)
        val entry = nearestPerimeterAnchor(rect, detectorCenter) ?: return null
        // Advance a short measured distance into the photo toward the real Detector box.
        val target = DisplayPoint(
            entry.x + (detectorCenter.x - entry.x) * .25f,
            entry.y + (detectorCenter.y - entry.y) * .25f,
        )
        if (!target.isInside(rect)) return null
        return WaterLightPathGeometryFactory.between(
            start = releaseEndpoint,
            end = WaterLightPathPoint(target.x, target.y),
            seed = seed,
        )?.takeIf(WaterLightPathGeometry::isValid)
    }

    fun fishContact(
        transform: RecognitionImageTransform,
        contactPointNormalizedSource: WaterLightPathPoint,
        seed: Int,
    ): WaterLightPathGeometry? {
        if (!contactPointNormalizedSource.isValid() ||
            contactPointNormalizedSource.x !in 0f..1f || contactPointNormalizedSource.y !in 0f..1f
        ) return null
        val rect = transform.photoContentRect
        if (!rect.isValid()) return null
        val contact = transform.mapNormalized(contactPointNormalizedSource.x, contactPointNormalizedSource.y)
        if (!contact.isInside(rect)) return null
        val perimeter = nearestPerimeterAnchor(rect, contact) ?: return null
        return WaterLightPathGeometryFactory.between(
            start = WaterLightPathPoint(perimeter.x, perimeter.y),
            end = WaterLightPathPoint(contact.x, contact.y),
            seed = seed,
        )?.takeIf(WaterLightPathGeometry::isValid)
    }

    private fun nearestPerimeterAnchor(rect: DisplayRect, point: DisplayPoint): DisplayPoint? {
        if (!rect.isValid() || !point.x.isFinite() || !point.y.isFinite()) return null
        return listOf(
            DisplayPoint(point.x.coerceIn(rect.left, rect.right), rect.top),
            DisplayPoint(rect.right, point.y.coerceIn(rect.top, rect.bottom)),
            DisplayPoint(point.x.coerceIn(rect.left, rect.right), rect.bottom),
            DisplayPoint(rect.left, point.y.coerceIn(rect.top, rect.bottom)),
        ).minByOrNull { candidate ->
            val dx = candidate.x - point.x
            val dy = candidate.y - point.y
            dx * dx + dy * dy
        }
    }

    private fun DisplayRect.isValid(): Boolean =
        listOf(left, top, right, bottom).all(Float::isFinite) && width > 1f && height > 1f

    private fun DisplayPoint.isInside(rect: DisplayRect): Boolean =
        x.isFinite() && y.isFinite() && x > rect.left && x < rect.right && y > rect.top && y < rect.bottom
}
