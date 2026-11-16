package com.yujian.ai.ui.recognition.reveal

import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
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
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

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
    ): Boolean = !resultHandoffMotionActive || ResultHandoffSubstage.RESULT_INFO_ARRIVAL in completed
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
            geometryVersion != 0L && expectedCharacterCount > 0 &&
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

object RecognitionGlyphPathInspector {
    fun inspect(
        identity: RecognitionSceneIdentity,
        speciesKey: String,
        speciesName: String,
        path: Path,
        fullTextRangeStart: Int,
        fullTextRangeEnd: Int,
        builtCharacterCount: Int,
    ): RecognitionGlyphPathDescriptor? {
        val expectedCharacterCount = speciesName.codePointCount(0, speciesName.length)
        if (speciesKey.isBlank() || speciesName.isBlank() || fullTextRangeStart != 0 ||
            fullTextRangeEnd != speciesName.length || expectedCharacterCount <= 0 ||
            builtCharacterCount != expectedCharacterCount || path.isEmpty
        ) return null
        val bounds = RectF()
        path.computeBounds(bounds, true)
        val glyphBounds = RecognitionGlyphBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
        if (!glyphBounds.isValid) return null

        val measure = PathMeasure(path, false)
        val contourMetrics = mutableListOf<String>()
        var totalLength = 0f
        var contourCount = 0
        do {
            val length = measure.length
            if (!length.isFinite() || length < 0f) return null
            if (length > 0f) {
                val position = FloatArray(2)
                val tangent = FloatArray(2)
                measure.getPosTan(length * .5f, position, tangent)
                if (!position[0].isFinite() || !position[1].isFinite()) return null
                totalLength += length
                contourCount++
                contourMetrics += listOf(
                    length.toBits(), position[0].toBits(), position[1].toBits(),
                    tangent[0].toBits(), tangent[1].toBits(),
                ).joinToString(":")
            }
        } while (measure.nextContour())
        if (contourCount == 0 || !totalLength.isFinite() || totalLength <= 1f) return null

        val signature = buildString {
            append(identity.sessionId).append('|').append(identity.attemptId).append('|')
            append(identity.imageId).append('|').append(identity.photoSha256.orEmpty()).append('|')
            append(speciesKey).append('|').append(speciesName).append('|')
            append(glyphBounds.left.toBits()).append('|').append(glyphBounds.top.toBits()).append('|')
            append(glyphBounds.right.toBits()).append('|').append(glyphBounds.bottom.toBits()).append('|')
            append(contourMetrics.joinToString(";"))
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return RecognitionGlyphPathDescriptor(
            identity = identity,
            speciesKey = speciesKey,
            speciesName = speciesName,
            pathId = "glyph:" + digest,
            geometryVersion = digest.take(15).toLong(16).coerceAtLeast(1L),
            expectedCharacterCount = expectedCharacterCount,
            builtCharacterCount = builtCharacterCount,
            contourCount = contourCount,
            totalPathLengthPx = totalLength,
            bounds = glyphBounds,
        )
    }
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
