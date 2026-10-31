package com.yujian.ai.ai.subject

import android.graphics.Matrix
import android.graphics.Rect

enum class SubjectStatus { IDLE, PROCESSING, READY, FAILED }

enum class FishSubjectQuality { GOOD, WARNING, INVALID }

data class FishSubjectResult(
    val status: SubjectStatus,
    val bitmapPath: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val exceptionClass: String? = null,
    val mlKitErrorCode: Int? = null,
    val rootCause: String? = null,
    val processingMs: Long = 0L,
    val roiWidth: Int = 0,
    val roiHeight: Int = 0,
    val maskSize: Int = 0,
    val expectedMaskSize: Int = 0,
    val maskAreaRatio: Float = 0f,
    val quality: FishSubjectQuality? = null,
    /** Model decoder output for traceability; it is recorded but not treated as calibrated quality. */
    val maskIouScore: Float? = null,
    /** Actual inference geometry, never reconstructed from a later detector BBox. */
    val geometry: MaskGeometryMetadata? = null,
)

data class MaskGeometryMetadata(
    val sessionId: String,
    val attemptId: String,
    val imageId: String,
    val photoSha256: String,
    val canonicalWidth: Int,
    val canonicalHeight: Int,
    val roiPx: Rect,
    val modelInputSize: Int,
    val resizedWidth: Int,
    val resizedHeight: Int,
    val padLeft: Int,
    val padTop: Int,
    val padRight: Int,
    val padBottom: Int,
    val maskGridWidth: Int,
    val maskGridHeight: Int,
    /** Maps the model decoder grid to the EXIF-normalized bitmap's pixels. */
    val modelToPhotoTransform: Matrix,
    val pixelCenterConvention: String,
    val threshold: Float,
    val contourVersion: String,
    val modelSha256: String,
) {
    fun matches(session: String, attempt: String, image: String, sha: String, width: Int, height: Int): Boolean =
        sessionId == session && attemptId == attempt && imageId == image &&
            photoSha256 == sha && canonicalWidth == width && canonicalHeight == height &&
            roiPx.left >= 0 && roiPx.top >= 0 && roiPx.right <= width && roiPx.bottom <= height &&
            roiPx.width() > 0 && roiPx.height() > 0

    fun normalizedRoi(): com.yujian.ai.ai.NormalizedFishBox = com.yujian.ai.ai.NormalizedFishBox(
        roiPx.left.toFloat() / canonicalWidth,
        roiPx.top.toFloat() / canonicalHeight,
        roiPx.right.toFloat() / canonicalWidth,
        roiPx.bottom.toFloat() / canonicalHeight,
    )
}
