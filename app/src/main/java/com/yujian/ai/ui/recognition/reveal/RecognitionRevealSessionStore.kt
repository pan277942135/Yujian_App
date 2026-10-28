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
    val handoff: RecognitionRevealHandoff? = null,
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
        snapshot = current.copy(subjectResult = result, subjectGeometry = geometry)
    }

    fun updateProcessingPhoto(key: RecognitionRevealKey, photo: RecognitionProcessingPhotoSnapshot) {
        val current = snapshot ?: return
        if (!current.matches(key) || current.image?.imageId != photo.imageId) return
        if (current.processingPhoto == photo) return
        snapshot = current.copy(processingPhoto = photo)
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
        val handoff = RecognitionRevealHandoffFactory.create(
            recognitionSessionId = key.recognitionSessionId,
            recognitionAttemptId = key.recognitionAttemptId,
            image = image,
            result = result,
            subject = current.subjectGeometry,
            processingPhoto = current.processingPhoto,
            highConfidenceResult = highConfidenceResult,
            handoffTimestampUptimeMs = handoffTimestampUptimeMs,
        )
        snapshot = current.copy(
            productionResult = result,
            prediction = result.prediction,
            inferenceAsset = inferenceAsset,
            handoff = handoff,
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
        snapshot = current.copy(subjectGeometry = null, processingPhoto = null, handoff = null)
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
