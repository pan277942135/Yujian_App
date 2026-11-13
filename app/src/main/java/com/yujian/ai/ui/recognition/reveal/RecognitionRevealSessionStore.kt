package com.yujian.ai.ui.recognition.reveal

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.subject.FishSubjectResult
import com.yujian.ai.ai.subject.SubjectStatus
import com.yujian.ai.inference.InferenceAsset
import com.yujian.ai.model.RecognitionPrediction
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.waterlight.core.RecognitionFishPathSnapshot
import com.yujian.ai.ui.waterlight.core.WaterLightPathPoint

data class RecognitionRevealSessionSnapshot(
    val recognitionSessionId: String,
    val recognitionAttemptId: String? = null,
    val image: SelectedImage? = null,
    val productionResult: ProductionRecognitionResult? = null,
    val prediction: RecognitionPrediction? = null,
    val subjectResult: FishSubjectResult = FishSubjectResult(SubjectStatus.IDLE),
    val inferenceAsset: InferenceAsset? = null,
    val subjectGeometry: RecognitionSubjectGeometrySnapshot? = null,
    val processingPhoto: RecognitionProcessingPhotoSnapshot? = null,
    val fishPathSnapshot: RecognitionFishPathSnapshot? = null,
    val handoff: RecognitionRevealHandoff? = null,
    val highConfidenceResult: Boolean = false,
    val revealDecision: RecognitionRevealDecisionState = RecognitionRevealDecisionState.WAITING_BOTH,
)

internal fun RecognitionRevealSessionSnapshot.matches(key: RecognitionRevealKey): Boolean =
    recognitionSessionId == key.recognitionSessionId && recognitionAttemptId == key.recognitionAttemptId

/** Holds the active recognition payload in memory across Activity recreation. */
class RecognitionRevealSessionStore : ViewModel() {
    var snapshot by mutableStateOf<RecognitionRevealSessionSnapshot?>(null)
        private set

    private val playedKeys = RecognitionRevealPlaybackLedger()

    fun ensureSession(sessionId: String): RecognitionRevealSessionSnapshot {
        val current = snapshot?.takeIf { it.recognitionSessionId == sessionId }
        if (current != null) return current
        return RecognitionRevealSessionSnapshot(recognitionSessionId = sessionId).also { snapshot = it }
    }

    fun beginAttempt(sessionId: String, attemptId: String, image: SelectedImage) {
        snapshot = RecognitionRevealSessionSnapshot(
            recognitionSessionId = sessionId,
            recognitionAttemptId = attemptId,
            image = image,
        )
    }

    fun updateSubject(
        key: RecognitionRevealKey,
        result: FishSubjectResult,
        geometry: RecognitionSubjectGeometrySnapshot?,
    ) {
        val current = snapshot ?: return
        if (!current.matches(key)) return
        if (current.subjectResult == result && current.subjectGeometry == geometry) return
        val candidate = current.copy(subjectResult = result, subjectGeometry = geometry)
        val updatedHandoff = rebuildHandoff(candidate)
        snapshot = candidate.copy(
            handoff = updatedHandoff,
            revealDecision = decisionFor(candidate),
        )
    }

    fun updateProcessingPhoto(key: RecognitionRevealKey, photo: RecognitionProcessingPhotoSnapshot) {
        val current = snapshot ?: return
        if (!current.matches(key) || current.image?.imageId != photo.imageId) return
        if (current.processingPhoto == photo) return
        val candidate = current.copy(processingPhoto = photo)
        snapshot = candidate.copy(
            handoff = rebuildHandoff(candidate),
            revealDecision = decisionFor(candidate),
        )
    }

    /** Save only the exact P* route exported by the active Processing PathLedger. */
    fun updateFishPathSnapshot(
        key: RecognitionRevealKey,
        pathSnapshot: RecognitionFishPathSnapshot,
    ): Boolean {
        val current = snapshot ?: return false
        val image = current.image ?: return false
        if (!current.matches(key) || !pathSnapshot.isValid() ||
            pathSnapshot.sessionId != key.recognitionSessionId || pathSnapshot.attemptId != key.recognitionAttemptId ||
            pathSnapshot.imageId != image.imageId || pathSnapshot.photoSha256 != image.photoSha256 ||
            pathSnapshot.photoContentVersion != image.bitmap.generationId.toLong().coerceAtLeast(0L)
        ) return false
        val subject = current.subjectGeometry ?: return false
        val photo = current.processingPhoto ?: return false
        val photoContact = photo.contactPointNormalizedSource?.let { WaterLightPathPoint(it.x, it.y) }
        if (current.subjectResult.status != SubjectStatus.READY ||
            pathSnapshot.maskSha256 != subject.maskSha256 || pathSnapshot.maskVersion != subject.maskVersion ||
            pathSnapshot.pStarNormalizedSource != photoContact ||
            pathSnapshot.maskSha256 != photo.contactPointMaskSha256 ||
            pathSnapshot.maskVersion != photo.contactPointMaskVersion ||
            pathSnapshot.geometryVersion != photo.contactPointGeometryVersion
        ) return false
        val existing = current.fishPathSnapshot
        if (existing != null && existing != pathSnapshot) return false
        if (existing == pathSnapshot) return true
        val candidate = current.copy(fishPathSnapshot = pathSnapshot)
        snapshot = candidate.copy(handoff = rebuildHandoff(candidate))
        return true
    }

    fun fishPathSnapshot(key: RecognitionRevealKey): RecognitionFishPathSnapshot? =
        snapshot?.takeIf { it.matches(key) }?.fishPathSnapshot

    /** Retain the real Classifier result immediately while the independent SlimSAM task continues. */
    fun recordClassifierResult(
        key: RecognitionRevealKey,
        result: ProductionRecognitionResult,
        inferenceAsset: InferenceAsset?,
        highConfidenceResult: Boolean,
    ): RecognitionRevealDecisionState? {
        val current = snapshot ?: return null
        val image = current.image ?: return null
        if (!current.matches(key) || image.imageId.isBlank() || image.photoSha256.isBlank()) return null
        val candidate = current.copy(
            productionResult = result,
            prediction = result.prediction,
            inferenceAsset = inferenceAsset,
            highConfidenceResult = highConfidenceResult,
        )
        val updated = candidate.copy(
            handoff = rebuildHandoff(candidate),
            revealDecision = decisionFor(candidate),
        )
        snapshot = updated
        return updated.revealDecision
    }

    fun revealDecisionFor(key: RecognitionRevealKey): RecognitionRevealDecisionState? {
        val current = snapshot?.takeIf { it.matches(key) } ?: return null
        return decisionFor(current)
    }

    fun completeAttempt(
        key: RecognitionRevealKey,
        result: ProductionRecognitionResult,
        highConfidenceResult: Boolean,
        inferenceAsset: InferenceAsset?,
        handoffTimestampUptimeMs: Long,
    ): RecognitionRevealHandoff? {
        val current = snapshot ?: return null
        if (!current.matches(key)) return null
        val image = current.image ?: return null
        val candidate = current.copy(
            productionResult = result,
            prediction = result.prediction,
            inferenceAsset = inferenceAsset,
            highConfidenceResult = highConfidenceResult,
        )
        val decision = decisionFor(candidate)
        if (!RecognitionRevealDecisionGate.isReady(decision)) return null
        val handoff = RecognitionRevealHandoffFactory.create(
            recognitionSessionId = key.recognitionSessionId,
            recognitionAttemptId = key.recognitionAttemptId,
            image = image,
            result = result,
            subject = current.subjectGeometry,
            processingPhoto = current.processingPhoto,
            highConfidenceResult = highConfidenceResult,
            handoffTimestampUptimeMs = handoffTimestampUptimeMs,
            subjectStatus = current.subjectResult.status,
            fishPathSnapshot = current.fishPathSnapshot,
        )
        if (handoff.eligibility == RecognitionRevealEligibility.FULL_REVEAL && handoff.fishPathSnapshot == null) {
            return null
        }
        snapshot = candidate.copy(
            handoff = handoff,
            revealDecision = handoff.revealDecision ?: decision,
        )
        return handoff
    }

    fun hasPlayed(key: RecognitionRevealKey): Boolean = playedKeys.hasPlayed(key)

    fun claimPlayback(key: RecognitionRevealKey): Boolean {
        return playedKeys.claim(key)
    }

    fun releaseAnimationGeometry(key: RecognitionRevealKey) {
        val current = snapshot ?: return
        if (!current.matches(key)) return
        // These compact identity-bound snapshots are needed while SlimSAM or host geometry
        // is still resolving. They are released with the recognition attempt.
    }

    private fun rebuildHandoff(
        current: RecognitionRevealSessionSnapshot,
        subject: RecognitionSubjectGeometrySnapshot? = current.subjectGeometry,
        processingPhoto: RecognitionProcessingPhotoSnapshot? = current.processingPhoto,
    ): RecognitionRevealHandoff? {
        val handoff = current.handoff ?: return null
        val image = current.image ?: return handoff
        val production = current.productionResult ?: return handoff
        return RecognitionRevealHandoffFactory.create(
            recognitionSessionId = handoff.key.recognitionSessionId,
            recognitionAttemptId = handoff.key.recognitionAttemptId,
            image = image,
            result = production,
            subject = subject,
            processingPhoto = processingPhoto,
            highConfidenceResult = current.highConfidenceResult,
            handoffTimestampUptimeMs = handoff.handoffTimestampUptimeMs,
            subjectStatus = current.subjectResult.status,
            fishPathSnapshot = current.fishPathSnapshot,
        )
    }

    private fun decisionFor(current: RecognitionRevealSessionSnapshot): RecognitionRevealDecisionState {
        val result = current.productionResult
        val image = current.image
        val classifier = when {
            result == null -> RecognitionClassifierTaskState.WAITING
            result.ready -> RecognitionClassifierTaskState.READY
            else -> RecognitionClassifierTaskState.FAILED
        }
        val primary = result?.assessment?.primary
        val trust = if (image != null && primary != null) RecognitionRevealPolicy.geometryTrust(
            detectorBox = primary.box,
            subject = current.subjectGeometry,
            imageId = image.imageId,
            sourceWidthPx = image.bitmap.width,
            sourceHeightPx = image.bitmap.height,
            detectorConfidence = primary.confidence,
            recognitionSessionId = current.recognitionSessionId,
            recognitionAttemptId = current.recognitionAttemptId,
            photoSha256 = image.photoSha256,
        ) else RecognitionGeometryTrustState.MISSING
        return RecognitionRevealDecisionGate.decide(
            classifier = classifier,
            subject = current.subjectResult.status,
            maskTrusted = trust == RecognitionGeometryTrustState.TRUSTED_MASK,
            contourValid = current.subjectGeometry?.contour?.isNotEmpty() == true,
            identityCurrent = image != null && !current.recognitionSessionId.isBlank() &&
                !current.recognitionAttemptId.isNullOrBlank() && image.imageId.isNotBlank() &&
                image.photoSha256.isNotBlank(),
        )
    }

    /** Releases bitmap and inference references when the user leaves this recognition attempt. */
    fun clearAttempt(preserveSession: Boolean) {
        val current = snapshot
        snapshot = if (preserveSession && current != null) {
            RecognitionRevealSessionSnapshot(recognitionSessionId = current.recognitionSessionId)
        } else {
            null
        }
    }

}
