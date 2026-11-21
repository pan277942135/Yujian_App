package com.yujian.ai.ui.recognition.reveal

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

const val RECOGNITION_RESULT_HANDOFF_EVENT_PRODUCER =
    "RecognitionResultScreen.emitResultHandoffMilestone"
const val RECOGNITION_RESULT_HANDOFF_EVENT_CONSUMER =
    "RecognitionResultHandoffConsumer.consume"

/** Single ordered transition contract shared by the controller, session store and tests. */
object RecognitionResultHandoffContract {
    fun isNextEventSequence(requested: Long, lastAccepted: Long): Boolean =
        lastAccepted >= 0L && requested == lastAccepted + 1L

    fun hasStrictlyLaterTimestamp(requested: Long, lastAccepted: Long): Boolean =
        lastAccepted >= 0L && requested > lastAccepted

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

    fun mayExposeResultInfo(
        resultHandoffMotionActive: Boolean,
        completed: Set<ResultHandoffSubstage>,
    ): Boolean = !resultHandoffMotionActive || ResultHandoffSubstage.RESULT_SCENE_HANDOFF in completed
}

data class RecognitionResultInfoRenderReceipt(
    val identity: RecognitionSceneIdentity,
    val resultIdentity: String,
    val geometryVersion: Long,
    val renderedProgress: Float,
    val layoutWidthPx: Int,
    val layoutHeightPx: Int,
    val contentDrawn: Boolean,
    val drawPassSequence: Long,
)

object RecognitionResultInfoRenderContract {
    fun isComplete(
        expectedIdentity: RecognitionSceneIdentity,
        expectedResultIdentity: String,
        expectedGeometryVersion: Long,
        receipt: RecognitionResultInfoRenderReceipt?,
    ): Boolean = receipt != null && receipt.identity == expectedIdentity &&
        receipt.resultIdentity == expectedResultIdentity && expectedResultIdentity.isNotBlank() &&
        receipt.geometryVersion == expectedGeometryVersion && expectedGeometryVersion > 0L &&
        receipt.renderedProgress.isFinite() && receipt.renderedProgress >= .999f &&
        receipt.layoutWidthPx > 0 && receipt.layoutHeightPx > 0 && receipt.contentDrawn &&
        receipt.drawPassSequence > 0L
}

data class RecognitionResultStableRenderReceipt(
    val identity: RecognitionSceneIdentity,
    val resultIdentity: String,
    val geometryVersion: Long,
    val completedPrerequisites: Set<ResultHandoffSubstage>,
    val photoProgress: Float,
    val resultInfoProgress: Float,
    val opticalResidueComplete: Boolean,
    val layoutWidthPx: Int,
    val layoutHeightPx: Int,
    val contentDrawn: Boolean,
    val drawPassSequence: Long,
)

object RecognitionResultStableRenderContract {
    private val REQUIRED = setOf(
        ResultHandoffSubstage.NAME_STABLE,
        ResultHandoffSubstage.PHOTO_SETTLE,
        ResultHandoffSubstage.RESULT_SCENE_HANDOFF,
        ResultHandoffSubstage.RESULT_INFO_ARRIVAL,
    )

    fun isComplete(
        expectedIdentity: RecognitionSceneIdentity,
        expectedResultIdentity: String,
        expectedGeometryVersion: Long,
        receipt: RecognitionResultStableRenderReceipt?,
    ): Boolean = receipt != null && receipt.identity == expectedIdentity &&
        receipt.resultIdentity == expectedResultIdentity && expectedResultIdentity.isNotBlank() &&
        receipt.geometryVersion == expectedGeometryVersion && expectedGeometryVersion > 0L &&
        receipt.completedPrerequisites.containsAll(REQUIRED) &&
        receipt.photoProgress.isFinite() && receipt.photoProgress >= .999f &&
        receipt.resultInfoProgress.isFinite() && receipt.resultInfoProgress >= .999f &&
        receipt.opticalResidueComplete && receipt.layoutWidthPx > 0 && receipt.layoutHeightPx > 0 &&
        receipt.contentDrawn && receipt.drawPassSequence > 0L
}

enum class RecognitionHandoffTransactionStatus {
    CREATED,
    PROCESSING_COMPLETE,
    HANDOFF_READY,
    NON_ANIMATED_RESULT_READY,
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


data class RecognitionGlyphBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val isValid: Boolean
        get() = left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
            right - left > 1f && bottom - top > 1f
}

data class RecognitionGlyphPathDescriptor(
    val identity: RecognitionSceneIdentity,
    val speciesKey: String,
    val speciesName: String,
    val pathId: String,
    val geometryVersion: Long,
    val expectedCharacterCount: Int,
    val builtCharacterCount: Int,
    val contourCount: Int,
    val totalPathLengthPx: Float,
    val bounds: RecognitionGlyphBounds,
) {
    fun matches(identity: RecognitionSceneIdentity, speciesKey: String, speciesName: String): Boolean =
        this.identity == identity && this.speciesKey == speciesKey && this.speciesName == speciesName &&
            speciesKey.isNotBlank() && speciesName.isNotBlank() && pathId.isNotBlank() &&
            geometryVersion > 0L && expectedCharacterCount > 0 &&
            builtCharacterCount == expectedCharacterCount && contourCount > 0 &&
            totalPathLengthPx.isFinite() && totalPathLengthPx > 1f && bounds.isValid
}

data class RecognitionGlyphDrawResult(
    val renderedProgress: Float,
    val fillPathDrawn: Boolean,
    val contourPathDrawn: Boolean,
    val drawPassSequence: Long,
)

data class RecognitionOrbCoreDissolveReceipt(
    val identity: RecognitionSceneIdentity,
    val stage: RecognitionSceneStage,
    val horizontalStretchProgress: Float,
    val coreAlpha: Float,
    val drawPassSequence: Long,
)

object RecognitionOrbCoreDissolveContract {
    fun isComplete(
        expectedIdentity: RecognitionSceneIdentity,
        receipt: RecognitionOrbCoreDissolveReceipt,
    ): Boolean = receipt.identity == expectedIdentity &&
        receipt.stage == RecognitionSceneStage.ORB_TO_NAME &&
        receipt.horizontalStretchProgress.isFinite() && receipt.horizontalStretchProgress >= .999f &&
        receipt.coreAlpha.isFinite() && receipt.coreAlpha <= .001f &&
        receipt.drawPassSequence > 0L
}

data class RecognitionGlyphDrawReceipt(
    val identity: RecognitionSceneIdentity,
    val speciesKey: String,
    val speciesName: String,
    val pathId: String,
    val geometryVersion: Long,
    val renderedProgress: Float,
    val fillPathDrawn: Boolean,
    val contourPathDrawn: Boolean,
    val drawPassSequence: Long,
)

data class RecognitionGlyphReadiness(
    val speciesResultReady: Boolean = false,
    val glyphPathReady: Boolean = false,
    val glyphRevealRendered: Boolean = false,
    val nameStable: Boolean = false,
    val descriptor: RecognitionGlyphPathDescriptor? = null,
    val renderReceipt: RecognitionGlyphDrawReceipt? = null,
)

object RecognitionGlyphReadinessContract {
    fun isPathReady(
        identity: RecognitionSceneIdentity,
        speciesKey: String,
        speciesName: String,
        state: RecognitionGlyphReadiness?,
    ): Boolean {
        val readiness = state ?: return false
        val descriptor = readiness.descriptor ?: return false
        return readiness.speciesResultReady && readiness.glyphPathReady &&
            descriptor.matches(identity, speciesKey, speciesName)
    }

    fun isRendered(
        identity: RecognitionSceneIdentity,
        speciesKey: String,
        speciesName: String,
        state: RecognitionGlyphReadiness?,
    ): Boolean {
        val readiness = state ?: return false
        if (!isPathReady(identity, speciesKey, speciesName, readiness) || !readiness.glyphRevealRendered) return false
        val descriptor = readiness.descriptor ?: return false
        val receipt = readiness.renderReceipt ?: return false
        return receipt.identity == identity && receipt.speciesKey == speciesKey &&
            receipt.speciesName == speciesName && receipt.pathId == descriptor.pathId &&
            receipt.geometryVersion == descriptor.geometryVersion &&
            receipt.renderedProgress.isFinite() && receipt.renderedProgress >= .999f &&
            receipt.fillPathDrawn && receipt.contourPathDrawn && receipt.drawPassSequence > 0L
    }

    fun isNameStableClaim(
        identity: RecognitionSceneIdentity,
        speciesKey: String,
        speciesName: String,
        state: RecognitionGlyphReadiness?,
    ): Boolean = state?.nameStable == true && isRendered(identity, speciesKey, speciesName, state)
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
    val eventConsumer: String = RECOGNITION_RESULT_HANDOFF_EVENT_CONSUMER,
    val source: String,
    val target: String,
    val feedback: String,
    val progress: Float,
    val renderStatus: String,
    val resultIdentity: String,
    val glyphReadiness: RecognitionGlyphReadiness? = null,
    val resultInfoRenderReceipt: RecognitionResultInfoRenderReceipt? = null,
    val resultStableRenderReceipt: RecognitionResultStableRenderReceipt? = null,
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
    val lastEventTimestampMs: Long = 0L,
    val acceptedEvents: List<RecognitionResultHandoffEvent> = emptyList(),
    val eventTrace: List<RecognitionResultHandoffTraceEntry> = emptyList(),
    val completedSubstages: Set<ResultHandoffSubstage> = emptySet(),
    val resultSceneStable: Boolean = false,
    val glyphReadiness: RecognitionGlyphReadiness = RecognitionGlyphReadiness(),
    val resultStableTimestampMs: Long? = null,
    val geometryReleasedTimestampMs: Long? = null,
    val navigationTrace: List<RecognitionResultNavigationTraceEntry> = emptyList(),
    val playbackProgressStage: RecognitionSceneStage? = null,
    val playbackProgress: Float = 0f,
    val animationGeometryReleased: Boolean = false,
) {
    fun has(substage: ResultHandoffSubstage): Boolean = substage in completedSubstages
}

/** Actual state transition emitted by the production SessionStore/NavHost transaction. */
data class RecognitionResultNavigationTraceEntry(
    val status: RecognitionHandoffTransactionStatus,
    val monotonicTimestampMs: Long,
    val source: String,
    val eventSequence: Long,
)
