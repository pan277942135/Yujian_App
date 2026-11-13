package com.yujian.ai.ui.recognition.reveal

import androidx.lifecycle.ViewModel
import android.os.SystemClock
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
    val resultHandoffState: RecognitionResultHandoffState = RecognitionResultHandoffState(),
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

    fun beginAttempt(
        sessionId: String,
        attemptId: String,
        image: SelectedImage,
        createdTimestampUptimeMs: Long = 0L,
    ) {
        snapshot = RecognitionRevealSessionSnapshot(
            recognitionSessionId = sessionId,
            recognitionAttemptId = attemptId,
            image = image,
            resultHandoffState = RecognitionResultHandoffState(
                navigationTrace = listOf(
                    RecognitionResultNavigationTraceEntry(
                        status = RecognitionHandoffTransactionStatus.CREATED,
                        monotonicTimestampMs = createdTimestampUptimeMs.coerceAtLeast(0L),
                        source = "RecognitionRevealSessionStore.beginAttempt",
                        eventSequence = 0L,
                    ),
                ),
            ),
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
        val existingHandoff = current.handoff?.takeIf {
            it.key == key && it.imageId == image.imageId &&
                it.resultIdentity == result.prediction?.top1?.speciesKey &&
                RecognitionRevealDecisionGate.isReady(it.revealDecision ?: RecognitionRevealDecisionState.WAITING_BOTH)
        }
        if (existingHandoff != null) {
            if (current.resultHandoffState.transactionStatus == RecognitionHandoffTransactionStatus.CREATED ||
                current.resultHandoffState.transactionStatus == RecognitionHandoffTransactionStatus.PROCESSING_COMPLETE ||
                current.resultHandoffState.transactionStatus == RecognitionHandoffTransactionStatus.HANDOFF_REJECTED ||
                current.resultHandoffState.transactionStatus == RecognitionHandoffTransactionStatus.NAVIGATION_FAILED
            ) {
                val state = current.resultHandoffState
                val trace = if (state.transactionStatus == RecognitionHandoffTransactionStatus.CREATED) {
                    state.navigationTrace + transition(
                        RecognitionHandoffTransactionStatus.PROCESSING_COMPLETE,
                        handoffTimestampUptimeMs,
                        "RecognitionRevealSessionStore.completeAttempt",
                        state.lastEventSequence,
                    )
                } else state.navigationTrace
                snapshot = current.copy(
                    resultHandoffState = state.copy(
                        transactionStatus = RecognitionHandoffTransactionStatus.HANDOFF_READY,
                        navigationTrace = trace + transition(
                            RecognitionHandoffTransactionStatus.HANDOFF_READY,
                            handoffTimestampUptimeMs,
                            "RecognitionRevealSessionStore.completeAttempt",
                            state.lastEventSequence,
                        ),
                    ),
                )
            }
            return existingHandoff
        }
        val processingTrace = current.resultHandoffState.navigationTrace + transition(
            RecognitionHandoffTransactionStatus.PROCESSING_COMPLETE,
            handoffTimestampUptimeMs,
            "RecognitionRevealSessionStore.completeAttempt",
            current.resultHandoffState.lastEventSequence,
        )
        val candidate = current.copy(
            productionResult = result,
            prediction = result.prediction,
            inferenceAsset = inferenceAsset,
            highConfidenceResult = highConfidenceResult,
            resultHandoffState = current.resultHandoffState.copy(
                transactionStatus = RecognitionHandoffTransactionStatus.PROCESSING_COMPLETE,
                navigationTrace = processingTrace,
            ),
        )
        snapshot = candidate
        val decision = decisionFor(candidate)
        if (!RecognitionRevealDecisionGate.isReady(decision)) {
            snapshot = candidate.copy(resultHandoffState = candidate.resultHandoffState.copy(
                transactionStatus = RecognitionHandoffTransactionStatus.HANDOFF_REJECTED,
                navigationTrace = processingTrace + transition(
                    RecognitionHandoffTransactionStatus.HANDOFF_REJECTED,
                    handoffTimestampUptimeMs,
                    "RecognitionRevealSessionStore.completeAttempt",
                    current.resultHandoffState.lastEventSequence,
                ),
            ))
            return null
        }
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
            snapshot = candidate.copy(resultHandoffState = candidate.resultHandoffState.copy(
                transactionStatus = RecognitionHandoffTransactionStatus.HANDOFF_REJECTED,
                navigationTrace = processingTrace + transition(
                    RecognitionHandoffTransactionStatus.HANDOFF_REJECTED,
                    handoffTimestampUptimeMs,
                    "RecognitionRevealSessionStore.completeAttempt",
                    current.resultHandoffState.lastEventSequence,
                ),
            ))
            return null
        }
        snapshot = candidate.copy(
            handoff = handoff,
            revealDecision = handoff.revealDecision ?: decision,
            resultHandoffState = candidate.resultHandoffState.copy(
                transactionStatus = RecognitionHandoffTransactionStatus.HANDOFF_READY,
                navigationTrace = processingTrace + transition(
                    RecognitionHandoffTransactionStatus.HANDOFF_READY,
                    handoffTimestampUptimeMs,
                    "RecognitionRevealSessionStore.completeAttempt",
                    current.resultHandoffState.lastEventSequence,
                ),
            ),
        )
        return handoff
    }

    /** Idempotent Processing → navigation transaction. Rejected handoffs remain retryable. */
    fun requestResultNavigation(
        key: RecognitionRevealKey,
        monotonicTimestampMs: Long = SystemClock.uptimeMillis(),
    ): Boolean {
        val current = snapshot?.takeIf { it.matches(key) } ?: return false
        if (current.handoff?.key != key) return false
        when (current.resultHandoffState.transactionStatus) {
            RecognitionHandoffTransactionStatus.NAVIGATION_REQUESTED,
            RecognitionHandoffTransactionStatus.RESULT_ATTACHED,
            RecognitionHandoffTransactionStatus.HANDOFF_COMPLETED -> return false
            else -> Unit
        }
        snapshot = current.copy(
            resultHandoffState = current.resultHandoffState.copy(
                transactionStatus = RecognitionHandoffTransactionStatus.NAVIGATION_REQUESTED,
                navigationTrace = current.resultHandoffState.navigationTrace + transition(
                    RecognitionHandoffTransactionStatus.NAVIGATION_REQUESTED,
                    monotonicTimestampMs,
                    "YujianApp.deliverRecognitionAttempt",
                    current.resultHandoffState.lastEventSequence,
                ),
            ),
        )
        return true
    }

    fun markNavigationFailed(
        key: RecognitionRevealKey,
        monotonicTimestampMs: Long = SystemClock.uptimeMillis(),
    ): Boolean {
        val current = snapshot?.takeIf { it.matches(key) } ?: return false
        if (current.resultHandoffState.transactionStatus !in setOf(
                RecognitionHandoffTransactionStatus.NAVIGATION_REQUESTED,
                RecognitionHandoffTransactionStatus.NAVIGATION_FAILED,
            )
        ) return false
        snapshot = current.copy(resultHandoffState = current.resultHandoffState.copy(
            transactionStatus = RecognitionHandoffTransactionStatus.NAVIGATION_FAILED,
            navigationTrace = current.resultHandoffState.navigationTrace + transition(
                RecognitionHandoffTransactionStatus.NAVIGATION_FAILED,
                monotonicTimestampMs,
                "YujianApp.deliverRecognitionAttempt",
                current.resultHandoffState.lastEventSequence,
            ),
        ))
        return true
    }

    fun markResultAttached(
        key: RecognitionRevealKey,
        monotonicTimestampMs: Long = SystemClock.uptimeMillis(),
    ): Boolean {
        val current = snapshot?.takeIf { it.matches(key) } ?: return false
        if (current.handoff?.key != key || current.image?.imageId != current.handoff.imageId) return false
        if (current.resultHandoffState.transactionStatus == RecognitionHandoffTransactionStatus.RESULT_ATTACHED ||
            current.resultHandoffState.transactionStatus == RecognitionHandoffTransactionStatus.HANDOFF_COMPLETED
        ) return true
        if (current.resultHandoffState.transactionStatus != RecognitionHandoffTransactionStatus.NAVIGATION_REQUESTED) return false
        snapshot = current.copy(
            resultHandoffState = current.resultHandoffState.copy(
                transactionStatus = RecognitionHandoffTransactionStatus.RESULT_ATTACHED,
                navigationTrace = current.resultHandoffState.navigationTrace + transition(
                    RecognitionHandoffTransactionStatus.RESULT_ATTACHED,
                    monotonicTimestampMs,
                    "YujianApp.resultDestination",
                    current.resultHandoffState.lastEventSequence,
                ),
            ),
        )
        return true
    }

    fun navigationAccepted(key: RecognitionRevealKey): Boolean {
        val status = snapshot?.takeIf { it.matches(key) }?.resultHandoffState?.transactionStatus ?: return false
        return status in setOf(
            RecognitionHandoffTransactionStatus.NAVIGATION_REQUESTED,
            RecognitionHandoffTransactionStatus.RESULT_ATTACHED,
            RecognitionHandoffTransactionStatus.HANDOFF_COMPLETED,
        )
    }

    fun canAcceptResultHandoffEvent(key: RecognitionRevealKey, event: RecognitionResultHandoffEvent): String? {
        val current = snapshot?.takeIf { it.matches(key) } ?: return "STALE_ATTEMPT"
        val image = current.image ?: return "IMAGE_MISSING"
        val handoff = current.handoff ?: return "HANDOFF_MISSING"
        val prediction = current.prediction ?: return "CLASSIFIER_RESULT_MISSING"
        if (event.identity.sessionId != key.recognitionSessionId || event.identity.attemptId != key.recognitionAttemptId ||
            event.identity.imageId != image.imageId || event.identity.photoSha256 != image.photoSha256 ||
            event.identity.photoContentVersion != image.bitmap.generationId.toLong().coerceAtLeast(0L)
        ) return "IDENTITY_MISMATCH"
        if (handoff.imageId != image.imageId || event.resultIdentity != prediction.top1.speciesKey) return "RESULT_IDENTITY_MISMATCH"
        if (current.resultHandoffState.transactionStatus !in setOf(
                RecognitionHandoffTransactionStatus.RESULT_ATTACHED,
                RecognitionHandoffTransactionStatus.HANDOFF_COMPLETED,
            )
        ) return "RESULT_ROUTE_NOT_ATTACHED"
        if (!RecognitionResultHandoffContract.isNextEventSequence(
                event.eventSequence,
                current.resultHandoffState.lastEventSequence,
            )
        ) return "EVENT_SEQUENCE_NOT_CONTIGUOUS"
        if (event.geometryVersion == 0L) return "RESULT_GEOMETRY_INVALID"
        if (!RecognitionResultHandoffContract.hasStrictlyLaterTimestamp(
                event.monotonicTimestampMs,
                current.resultHandoffState.lastEventTimestampMs,
            ) ||
            event.eventProducer != RECOGNITION_RESULT_HANDOFF_EVENT_PRODUCER ||
            event.eventConsumer != RECOGNITION_RESULT_HANDOFF_EVENT_CONSUMER ||
            event.renderStatus != "READY" || !event.progress.isFinite() || event.progress < .999f ||
            event.stage.ordinal < com.yujian.ai.ui.waterlight.core.RecognitionSceneStage.ORB_RETURN.ordinal
        ) return "EVENT_NOT_COMPLETE"
        if (!RecognitionResultHandoffContract.isNext(event.substage, current.resultHandoffState.completedSubstages)) {
            return "RESULT_SUBSTAGE_OUT_OF_ORDER"
        }
        if (event.substage == ResultHandoffSubstage.NAME_STABLE) {
            if (event.stage != com.yujian.ai.ui.waterlight.core.RecognitionSceneStage.SPECIES_NAME_REVEAL) {
                return "NAME_STABILITY_STAGE_MISMATCH"
            }
        } else if (event.stage != com.yujian.ai.ui.waterlight.core.RecognitionSceneStage.RESIDUE_SETTLE) {
            return "RESULT_HANDOFF_STAGE_MISMATCH"
        }
        if (handoff.eligibility == RecognitionRevealEligibility.FULL_REVEAL) {
            val path = current.fishPathSnapshot ?: return "PROCESSING_PATH_SNAPSHOT_MISSING"
            if (event.maskSha256 != path.maskSha256 || event.maskVersion != path.maskVersion ||
                event.pathId != path.returnPathId || event.pathGeometryVersion != path.geometryVersion ||
                event.pStarNormalizedSource != path.pStarNormalizedSource || event.pathSegments != path.returnPathSegments
            ) {
                return "PROCESSING_PATH_IDENTITY_MISMATCH"
            }
        }
        if (event.substage == ResultHandoffSubstage.RESULT_SCENE_STABLE) {
            val required = setOf(
                ResultHandoffSubstage.NAME_STABLE,
                ResultHandoffSubstage.PHOTO_SETTLE,
                ResultHandoffSubstage.RESULT_SCENE_HANDOFF,
                ResultHandoffSubstage.RESULT_INFO_ARRIVAL,
            )
            if (!current.resultHandoffState.completedSubstages.containsAll(required) ||
                event.stage != com.yujian.ai.ui.waterlight.core.RecognitionSceneStage.RESIDUE_SETTLE
            ) return "RESULT_STABLE_PREREQUISITES_MISSING"
            if (current.resultHandoffState.playbackStatus != RevealPlaybackStatus.IN_PROGRESS) return "PLAYBACK_NOT_IN_PROGRESS"
        }
        return null
    }

    fun recordResultHandoffEvent(key: RecognitionRevealKey, event: RecognitionResultHandoffEvent): RecognitionResultHandoffReceipt {
        val rejection = canAcceptResultHandoffEvent(key, event)
        if (rejection != null) return RecognitionResultHandoffReceipt(false, rejection, snapshot?.resultHandoffState?.resultSceneStable == true)
        val current = snapshot ?: return RecognitionResultHandoffReceipt(false, "SESSION_MISSING")
        val nextSubstages = current.resultHandoffState.completedSubstages + event.substage
        val stable = event.substage == ResultHandoffSubstage.RESULT_SCENE_STABLE
        val transaction = if (stable) RecognitionHandoffTransactionStatus.HANDOFF_COMPLETED
            else RecognitionHandoffTransactionStatus.RESULT_ATTACHED
        val navigationTrace = if (stable) current.resultHandoffState.navigationTrace + transition(
            RecognitionHandoffTransactionStatus.HANDOFF_COMPLETED,
            event.monotonicTimestampMs,
            "RecognitionRevealSessionStore.recordResultHandoffEvent",
            event.eventSequence,
        ) else current.resultHandoffState.navigationTrace
        val nextState = current.resultHandoffState.copy(
            transactionStatus = transaction,
            lastEventSequence = event.eventSequence,
            lastEventTimestampMs = event.monotonicTimestampMs,
            acceptedEvents = current.resultHandoffState.acceptedEvents + event,
            eventTrace = current.resultHandoffState.eventTrace + RecognitionResultHandoffTraceEntry(event, accepted = true),
            navigationTrace = navigationTrace,
            completedSubstages = nextSubstages,
            resultSceneStable = stable,
            resultStableTimestampMs = event.monotonicTimestampMs.takeIf { stable },
            playbackStatus = if (stable) RevealPlaybackStatus.COMPLETED else current.resultHandoffState.playbackStatus,
            animationGeometryReleased = stable && current.resultHandoffState.animationGeometryReleased,
        )
        snapshot = current.copy(resultHandoffState = nextState)
        if (stable) playedKeys.complete(key, resultSceneStable = true)
        return RecognitionResultHandoffReceipt(true, resultSceneStable = stable)
    }

    fun recordRejectedResultHandoffEvent(key: RecognitionRevealKey, event: RecognitionResultHandoffEvent, reason: String) {
        val current = snapshot?.takeIf { it.matches(key) } ?: return
        if (event.identity.sessionId != key.recognitionSessionId || event.identity.attemptId != key.recognitionAttemptId) return
        snapshot = current.copy(resultHandoffState = current.resultHandoffState.copy(
            eventTrace = current.resultHandoffState.eventTrace + RecognitionResultHandoffTraceEntry(
                event = event,
                accepted = false,
                rejectionReason = reason,
            ),
        ))
    }

    fun resultHandoffTraceJson(key: RecognitionRevealKey): String? {
        val state = snapshot?.takeIf { it.matches(key) }?.resultHandoffState ?: return null
        val entries = org.json.JSONArray()
        state.eventTrace.forEach { trace ->
            val event = trace.event
            val identity = event.identity
            val row = org.json.JSONObject()
                .put("sessionId", identity.sessionId)
                .put("attemptId", identity.attemptId)
                .put("imageId", identity.imageId)
                .put("photoSha", identity.photoSha256)
                .put("photoContentVersion", identity.photoContentVersion)
                .put("maskSha", event.maskSha256 ?: org.json.JSONObject.NULL)
                .put("maskVersion", event.maskVersion ?: org.json.JSONObject.NULL)
                .put("geometryVersion", event.geometryVersion)
                .put("pathId", event.pathId ?: org.json.JSONObject.NULL)
                .put("pathGeometryVersion", event.pathGeometryVersion ?: org.json.JSONObject.NULL)
                .put("pStarNormalizedSource", event.pStarNormalizedSource?.let {
                    org.json.JSONObject().put("x", it.x.toDouble()).put("y", it.y.toDouble())
                } ?: org.json.JSONObject.NULL)
                .put("pathSegments", org.json.JSONArray().apply {
                    event.pathSegments.forEach { segment -> put(org.json.JSONObject()
                        .put("start", org.json.JSONObject().put("x", segment.start.x.toDouble()).put("y", segment.start.y.toDouble()))
                        .put("control1", org.json.JSONObject().put("x", segment.control1.x.toDouble()).put("y", segment.control1.y.toDouble()))
                        .put("control2", org.json.JSONObject().put("x", segment.control2.x.toDouble()).put("y", segment.control2.y.toDouble()))
                        .put("end", org.json.JSONObject().put("x", segment.end.x.toDouble()).put("y", segment.end.y.toDouble()))
                        .put("coordinateSpace", segment.coordinateSpace)) }
                })
                .put("stage", event.stage.name)
                .put("substage", event.substage.name)
                .put("contractId", event.substage.contractId)
                .put("eventSequence", event.eventSequence)
                .put("timestamp", event.monotonicTimestampMs)
                .put("eventProducer", event.eventProducer)
                .put("eventConsumer", event.eventConsumer)
                .put("source", event.source)
                .put("target", event.target)
                .put("feedback", event.feedback)
                .put("progress", event.progress.toDouble())
                .put("renderStatus", event.renderStatus)
                .put("resultIdentity", event.resultIdentity)
                .put("accepted", trace.accepted)
                .put("rejectionReason", trace.rejectionReason ?: org.json.JSONObject.NULL)
            entries.put(row)
        }
        return org.json.JSONObject()
            .put("sessionId", key.recognitionSessionId)
            .put("attemptId", key.recognitionAttemptId)
            .put("transactionStatus", state.transactionStatus.name)
            .put("playbackStatus", state.playbackStatus.name)
            .put("lastEventSequence", state.lastEventSequence)
            .put("lastEventTimestampMs", state.lastEventTimestampMs)
            .put("resultStableTimestampMs", state.resultStableTimestampMs ?: org.json.JSONObject.NULL)
            .put("geometryReleasedTimestampMs", state.geometryReleasedTimestampMs ?: org.json.JSONObject.NULL)
            .put("navigation", org.json.JSONArray().apply {
                state.navigationTrace.forEach { item -> put(org.json.JSONObject()
                    .put("status", item.status.name)
                    .put("monotonicTimestampMs", item.monotonicTimestampMs)
                    .put("source", item.source)
                    .put("eventSequence", item.eventSequence)) }
            })
            .put("events", entries)
            .put("resultSceneStable", state.resultSceneStable)
            .toString()
    }

    fun resultSnapshot(key: RecognitionRevealKey): RecognitionResultSnapshot? {
        val current = snapshot?.takeIf { it.matches(key) } ?: return null
        val image = current.image ?: return null
        val result = current.productionResult?.takeIf { it.ready } ?: return null
        val prediction = current.prediction ?: return null
        val handoff = current.handoff?.takeIf { it.key == key } ?: return null
        if (prediction.top1.speciesKey != handoff.resultIdentity || prediction.top1.speciesName.isBlank() ||
            handoff.captureTimestampEpochMs != image.captureTimestampEpochMs ||
            handoff.captureTimestampSource != image.captureTimestampSource
        ) return null
        return RecognitionResultSnapshot(
            key = key,
            image = image,
            productionResult = result,
            prediction = prediction,
            subjectResult = current.subjectResult,
            inferenceAsset = current.inferenceAsset,
            subjectGeometry = current.subjectGeometry,
            handoff = handoff,
            fishPathSnapshot = current.fishPathSnapshot,
            captureTimestampEpochMs = image.captureTimestampEpochMs,
            captureTimestampSource = image.captureTimestampSource,
            glyphSpeciesKey = prediction.top1.speciesKey,
            glyphSpeciesName = prediction.top1.speciesName,
            revealEligibility = handoff.eligibility,
            handoffState = current.resultHandoffState,
            resultDataReady = true,
            resultSceneStable = current.resultHandoffState.resultSceneStable,
        )
    }

    fun hasPlayed(key: RecognitionRevealKey): Boolean = playedKeys.hasPlayed(key)

    fun claimPlayback(key: RecognitionRevealKey): Boolean {
        val claimed = playedKeys.claim(key)
        if (claimed) updatePlaybackStatus(key, RevealPlaybackStatus.CLAIMED)
        return claimed
    }

    fun startPlayback(key: RecognitionRevealKey): Boolean = playedKeys.start(key).also {
        if (it) updatePlaybackStatus(key, RevealPlaybackStatus.IN_PROGRESS)
    }

    fun resumePlayback(key: RecognitionRevealKey): Boolean = playedKeys.resume(key).also {
        if (it) updatePlaybackStatus(key, RevealPlaybackStatus.IN_PROGRESS)
    }

    fun updatePlaybackProgress(key: RecognitionRevealKey, stage: com.yujian.ai.ui.waterlight.core.RecognitionSceneStage, progress: Float): Boolean {
        val updated = playedKeys.updateProgress(key, stage, progress)
        if (updated) {
            val current = snapshot?.takeIf { it.matches(key) } ?: return false
            snapshot = current.copy(resultHandoffState = current.resultHandoffState.copy(
                playbackStatus = RevealPlaybackStatus.IN_PROGRESS,
                playbackProgressStage = stage,
                playbackProgress = progress,
            ))
        }
        return updated
    }

    fun interruptPlayback(key: RecognitionRevealKey): Boolean = playedKeys.interrupt(key).also {
        if (it) updatePlaybackStatus(key, RevealPlaybackStatus.INTERRUPTED)
    }

    fun failPlayback(key: RecognitionRevealKey): Boolean = playedKeys.fail(key).also {
        if (it) updatePlaybackStatus(key, RevealPlaybackStatus.FAILED)
    }

    fun releaseAnimationGeometry(key: RecognitionRevealKey, monotonicTimestampMs: Long = 0L) {
        val current = snapshot ?: return
        if (!current.matches(key)) return
        if (!current.resultHandoffState.resultSceneStable) return
        if (current.resultHandoffState.animationGeometryReleased) return
        snapshot = current.copy(resultHandoffState = current.resultHandoffState.copy(
            animationGeometryReleased = true,
            geometryReleasedTimestampMs = monotonicTimestampMs.coerceAtLeast(0L),
        ))
    }

    private fun updatePlaybackStatus(key: RecognitionRevealKey, status: RevealPlaybackStatus) {
        val current = snapshot?.takeIf { it.matches(key) } ?: return
        snapshot = current.copy(resultHandoffState = current.resultHandoffState.copy(playbackStatus = status))
    }

    private fun transition(
        status: RecognitionHandoffTransactionStatus,
        timestampMs: Long,
        source: String,
        eventSequence: Long,
    ) = RecognitionResultNavigationTraceEntry(
        status = status,
        monotonicTimestampMs = timestampMs.coerceAtLeast(0L),
        source = source,
        eventSequence = eventSequence,
    )

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
