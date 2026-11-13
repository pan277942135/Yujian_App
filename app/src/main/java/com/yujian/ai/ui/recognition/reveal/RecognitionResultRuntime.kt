package com.yujian.ai.ui.recognition.reveal

import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.subject.FishSubjectResult
import com.yujian.ai.inference.InferenceAsset
import com.yujian.ai.model.RecognitionPrediction
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.RecognitionFishPathSnapshot
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometry
import com.yujian.ai.ui.waterlight.core.WaterLightPathPoint

/** Business milestones produced from measured Result UI and consumed by the shared scene runtime. */
enum class ResultHandoffSubstage {
    NAME_STABLE,
    PHOTO_SETTLE,
    RESULT_SCENE_HANDOFF,
    RESULT_INFO_ARRIVAL,
    RESULT_SCENE_STABLE;

    val contractId: String get() = "13.${ordinal + 1}"
}

/** Single ordered transition contract shared by the controller, session store and tests. */
object RecognitionResultHandoffContract {
    fun isNext(
        requested: ResultHandoffSubstage,
        completed: Set<ResultHandoffSubstage>,
    ): Boolean {
        if (completed.size >= ResultHandoffSubstage.entries.size) return false
        val prefix = ResultHandoffSubstage.entries.take(completed.size)
        if (prefix.toSet() != completed) return false
        return ResultHandoffSubstage.entries[completed.size] == requested
    }

    fun isComplete(completed: Set<ResultHandoffSubstage>): Boolean =
        completed == ResultHandoffSubstage.entries.toSet()
}

enum class RecognitionHandoffTransactionStatus {
    CREATED,
    PROCESSING_COMPLETE,
    HANDOFF_READY,
    NAVIGATION_REQUESTED,
    RESULT_ATTACHED,
    HANDOFF_COMPLETED,
    HANDOFF_REJECTED,
    NAVIGATION_FAILED,
    INTERRUPTED,
}

enum class RevealPlaybackStatus {
    NOT_STARTED,
    CLAIMED,
    IN_PROGRESS,
    COMPLETED,
    INTERRUPTED,
    FAILED,
}

/**
 * Trace payload for an actual Result milestone. The geometry version is the Result host version;
 * mask/path versions continue to identify the original Processing path.
 */
data class RecognitionResultHandoffEvent(
    val identity: RecognitionSceneIdentity,
    val maskSha256: String?,
    val maskVersion: Long?,
    val geometryVersion: Long,
    val pathId: String?,
    val pathGeometryVersion: Long?,
    val pStarNormalizedSource: WaterLightPathPoint?,
    val pathSegments: List<WaterLightPathGeometry>,
    val stage: RecognitionSceneStage,
    val substage: ResultHandoffSubstage,
    val eventSequence: Long,
    val monotonicTimestampMs: Long,
    val eventProducer: String,
    val eventConsumer: String = "RecognitionSceneAnimationController",
    val source: String,
    val target: String,
    val feedback: String,
    val progress: Float,
    val renderStatus: String,
    val resultIdentity: String,
)

data class RecognitionResultHandoffReceipt(
    val accepted: Boolean,
    val rejectionReason: String? = null,
    val resultSceneStable: Boolean = false,
)

data class RecognitionResultHandoffTraceEntry(
    val event: RecognitionResultHandoffEvent,
    val accepted: Boolean,
    val rejectionReason: String? = null,
)

data class RecognitionResultHandoffState(
    val transactionStatus: RecognitionHandoffTransactionStatus = RecognitionHandoffTransactionStatus.CREATED,
    val playbackStatus: RevealPlaybackStatus = RevealPlaybackStatus.NOT_STARTED,
    val lastEventSequence: Long = 0L,
    val acceptedEvents: List<RecognitionResultHandoffEvent> = emptyList(),
    val eventTrace: List<RecognitionResultHandoffTraceEntry> = emptyList(),
    val completedSubstages: Set<ResultHandoffSubstage> = emptySet(),
    val resultSceneStable: Boolean = false,
    val playbackProgressStage: RecognitionSceneStage? = null,
    val playbackProgress: Float = 0f,
    val animationGeometryReleased: Boolean = false,
) {
    fun has(substage: ResultHandoffSubstage): Boolean = substage in completedSubstages
}

/** One immutable view of all data rendered or saved by the Result route. */
data class RecognitionResultSnapshot(
    val key: RecognitionRevealKey,
    val image: SelectedImage,
    val productionResult: ProductionRecognitionResult,
    val prediction: RecognitionPrediction,
    val subjectResult: FishSubjectResult,
    val inferenceAsset: InferenceAsset?,
    val subjectGeometry: RecognitionSubjectGeometrySnapshot?,
    val handoff: RecognitionRevealHandoff,
    val fishPathSnapshot: RecognitionFishPathSnapshot?,
    val captureTimestampEpochMs: Long,
    val captureTimestampSource: String,
    val glyphSpeciesKey: String,
    val glyphSpeciesName: String,
    val revealEligibility: RecognitionRevealEligibility,
    val handoffState: RecognitionResultHandoffState,
    val resultDataReady: Boolean,
    val resultSceneStable: Boolean,
)
