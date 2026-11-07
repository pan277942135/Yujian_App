package com.yujian.ai.ui.screens

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import com.yujian.ai.ai.RecognitionAttemptDeadline
import com.yujian.ai.ai.RecognitionAttemptFailureCode
import com.yujian.ai.ai.RecognitionFailureCode
import com.yujian.ai.ai.RecognitionModelIdentity
import com.yujian.ai.ai.RecognitionStageFailureException
import com.yujian.ai.ai.RecognitionModelWatchdog
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ai.subject.FishSubjectPreviewEngine
import com.yujian.ai.ai.subject.FishSubjectQuality
import com.yujian.ai.ai.subject.FishSubjectResult
import com.yujian.ai.ai.subject.SubjectStatus
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.identify.NormalizedSourceRect
import com.yujian.ai.ui.recognition.reveal.RecognitionGeometryTrustState
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealPolicy
import com.yujian.ai.ui.recognition.reveal.RecognitionSubjectGeometrySnapshot
import com.yujian.ai.ui.waterlight.mask.WaterLightMaskAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers

/** Owns one real SlimSAM task across Processing and Result composition lifetimes. */
class RecognitionSubjectAttemptRunner(
    private val scope: CoroutineScope,
    private val engine: FishSubjectPreviewEngine,
) {
    private var identity: String? = null
    private var pending: Deferred<FishSubjectResult>? = null

    fun cancel() {
        pending?.cancel()
        pending = null
        identity = null
    }

    fun request(
        selected: SelectedImage,
        box: NormalizedFishBox,
        confidence: Float,
        sessionId: String,
        attemptId: String,
        deadline: RecognitionAttemptDeadline,
        onResolved: (FishSubjectResult, RecognitionSubjectGeometrySnapshot?) -> Unit,
    ): Deferred<FishSubjectResult> {
        val key = "$sessionId|$attemptId|${selected.imageId}|${selected.photoSha256}|$box"
        if (identity == key) pending?.takeIf { it.isActive }?.let { return it }
        cancel()
        identity = key
        return scope.async {
            var result = try {
                val remainingMs = deadline.remainingMs(SystemClock.uptimeMillis())
                if (remainingMs == 0L) {
                    FishSubjectResult(SubjectStatus.FAILED, errorCode = RecognitionAttemptFailureCode.SLIMSAM_TIMEOUT)
                } else withTimeout(remainingMs) {
                    RecognitionModelWatchdog.execute(
                        modelName = "SLIMSAM",
                        identity = RecognitionModelIdentity(
                            sessionId = sessionId,
                            attemptId = attemptId,
                            imageId = selected.imageId,
                            photoSha256 = selected.photoSha256,
                        ),
                    ) { engine.generate(selected, box, sessionId, attemptId) }
                }
            } catch (timeout: TimeoutCancellationException) {
                FishSubjectResult(SubjectStatus.FAILED, errorCode = RecognitionAttemptFailureCode.SLIMSAM_TIMEOUT)
            } catch (cancelled: CancellationException) {
                Log.i("RecognitionSubjectRunner", "SLIMSAM_CANCEL session=$sessionId attempt=$attemptId " +
                    "image=${selected.imageId} failure_code=${RecognitionFailureCode.CANCELLED}")
                throw cancelled
            } catch (error: Throwable) {
                Log.e("RecognitionSubjectRunner", "SLIMSAM_ERROR session=$sessionId attempt=$attemptId image=${selected.imageId}", error)
                val failureCode = (error as? RecognitionStageFailureException)?.failureCode?.name
                    ?: RecognitionAttemptFailureCode.MODEL_INFERENCE_FAILED
                FishSubjectResult(SubjectStatus.FAILED, errorCode = failureCode)
            }
            currentCoroutineContext().ensureActive()
            if (deadline.isExpired(SystemClock.uptimeMillis())) {
                result = FishSubjectResult(SubjectStatus.FAILED, errorCode = RecognitionAttemptFailureCode.SLIMSAM_TIMEOUT)
            }
            val geometry = if (result.status == SubjectStatus.READY) {
                val remainingMs = deadline.remainingMs(SystemClock.uptimeMillis())
                if (remainingMs == 0L) {
                    result = FishSubjectResult(SubjectStatus.FAILED, errorCode = RecognitionAttemptFailureCode.SLIMSAM_TIMEOUT)
                    null
                } else try {
                    withTimeout(remainingMs) {
                        withContext(Dispatchers.Default) {
                            trustedGeometry(selected, box, confidence, sessionId, attemptId, result)
                        }
                    }
                } catch (timeout: TimeoutCancellationException) {
                    result = FishSubjectResult(SubjectStatus.FAILED, errorCode = RecognitionAttemptFailureCode.SLIMSAM_TIMEOUT)
                    null
                }
            } else null
            currentCoroutineContext().ensureActive()
            if (result.status == SubjectStatus.READY && geometry == null) {
                result = result.copy(errorCode = RecognitionAttemptFailureCode.MASK_UNTRUSTED)
            } else if (result.status == SubjectStatus.FAILED && result.errorCode.isNullOrBlank()) {
                result = result.copy(errorCode = RecognitionAttemptFailureCode.MODEL_INFERENCE_FAILED)
            }
            if (identity == key && !deadline.isExpired(SystemClock.uptimeMillis())) {
                Log.i("RecognitionSubjectRunner", "MASK_GATE session=$sessionId attempt=$attemptId image=${selected.imageId} " +
                    "sha256=${selected.photoSha256} status=${result.status} quality=${result.quality} " +
                    "trust=${if (geometry == null) "UNTRUSTED" else "TRUSTED_MASK"}")
                onResolved(result, geometry)
            } else if (identity == key) {
                val expired = FishSubjectResult(SubjectStatus.FAILED, errorCode = RecognitionAttemptFailureCode.SLIMSAM_TIMEOUT)
                onResolved(expired, null)
            } else {
                Log.i("RecognitionSubjectRunner", "SLIMSAM_STALE_RESULT session=$sessionId attempt=$attemptId " +
                    "image=${selected.imageId} failure_code=${RecognitionFailureCode.STALE_ATTEMPT}")
            }
            result
        }.also { pending = it }
    }

    private fun trustedGeometry(
        image: SelectedImage, box: NormalizedFishBox, confidence: Float,
        sessionId: String, attemptId: String, result: FishSubjectResult,
    ): RecognitionSubjectGeometrySnapshot? {
        if (result.status != SubjectStatus.READY || result.quality != FishSubjectQuality.GOOD ||
            result.maskSize <= 0 || result.maskSize != result.expectedMaskSize) return null
        val metadata = result.geometry ?: return null
        if (!metadata.matches(sessionId, attemptId, image.imageId, image.photoSha256,
                image.bitmap.width, image.bitmap.height)) return null
        val bitmap = BitmapFactory.decodeFile(result.bitmapPath ?: return null) ?: return null
        try {
            if (bitmap.width != result.roiWidth || bitmap.height != result.roiHeight ||
                bitmap.width != metadata.roiPx.width() || bitmap.height != metadata.roiPx.height()) return null
            val roi = metadata.normalizedRoi()
            val contour = WaterLightMaskAdapter.toPhotoSpace(metadata, extractContour(bitmap))
            if (contour.isEmpty()) return null
            val geometry = RecognitionSubjectGeometrySnapshot(
                imageId = image.imageId, recognitionSessionId = sessionId, recognitionAttemptId = attemptId,
                photoSha256 = image.photoSha256, sourceWidthPx = image.bitmap.width,
                sourceHeightPx = image.bitmap.height, detectorBox = box, detectorConfidence = confidence,
                subjectBox = NormalizedSourceRect(roi.x1, roi.y1, roi.x2, roi.y2),
                contour = contour, contourSourceSpace = "CANONICAL_PHOTO_NORMALIZED",
                quality = result.quality, maskSize = result.maskSize,
                expectedMaskSize = result.expectedMaskSize,
                maskVersion = bitmap.generationId.toLong(),
                maskSha256 = result.maskSha256,
            )
            return geometry.takeIf {
                RecognitionRevealPolicy.geometryTrust(box, it, image.imageId, image.bitmap.width,
                    image.bitmap.height, confidence, sessionId, attemptId,
                    image.photoSha256) == RecognitionGeometryTrustState.TRUSTED_MASK
            }
        } finally {
            bitmap.recycle()
        }
    }
}
