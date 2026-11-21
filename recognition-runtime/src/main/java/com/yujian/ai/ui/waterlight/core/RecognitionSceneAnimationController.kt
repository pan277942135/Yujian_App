package com.yujian.ai.ui.waterlight.core

import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ui.recognition.reveal.RecognitionGlyphReadiness
import com.yujian.ai.ui.recognition.reveal.RecognitionGlyphReadinessContract
import com.yujian.ai.ui.recognition.reveal.RecognitionOrbCoreDissolveContract
import com.yujian.ai.ui.recognition.reveal.RecognitionOrbCoreDissolveReceipt
import com.yujian.ai.ui.recognition.reveal.RecognitionResultHandoffEvent
import com.yujian.ai.ui.recognition.reveal.RecognitionResultHandoffContract
import com.yujian.ai.ui.recognition.reveal.RecognitionResultHandoffReceipt
import com.yujian.ai.ui.recognition.reveal.RecognitionResultInfoRenderContract
import com.yujian.ai.ui.recognition.reveal.RecognitionResultStableRenderContract
import com.yujian.ai.ui.recognition.reveal.RECOGNITION_RESULT_HANDOFF_EVENT_CONSUMER
import com.yujian.ai.ui.recognition.reveal.ResultHandoffSubstage
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealDecisionState
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealDecisionGate

enum class EnergyEventRejectionReason {
    EVENT_STAGE_MISMATCH,
    EVENT_SEQUENCE_MISMATCH,
    EVENT_TIMESTAMP_MISMATCH,
    EVENT_GEOMETRY_MISMATCH,
    EVENT_PATH_PENDING,
    EVENT_PATH_INVALID,
    EVENT_MASK_IDENTITY_MISMATCH,
    EVENT_PSTAR_BIND_MISMATCH,
    EVENT_DUPLICATE,
    EVENT_NOT_AT_STAGE_COMPLETION,
    EVENT_IDENTITY_MISMATCH,
    EVENT_INVALID,
}

data class EnergyEventAcceptanceResult(
    val accepted: Boolean,
    val rejectionReason: EnergyEventRejectionReason? = null,
)

data class EnergyEventRejectionDiagnostic(
    val event: EnergyEvent,
    val reason: EnergyEventRejectionReason,
    val stageProgress: Float,
    val modelGates: RecognitionSceneModelGates,
)

/**
 * The single owner of scene stage, stage-local time, playback controls and model/geometry gates.
 * Screens and renderers only sample frames from this controller; none of them infer a production
 * stage from an absolute elapsed timestamp.
 */
class RecognitionSceneAnimationController(
    private val minVisibleMs: Map<RecognitionSceneStage, Long> = DEFAULT_MIN_VISIBLE_MS,
) {
    var mode: ScenePlaybackMode = ScenePlaybackMode.AUTO
        private set
    var paused: Boolean = false
        private set
    var currentStage: RecognitionSceneStage = RecognitionSceneStage.PHOTO_STABLE
        private set
    var previewCompleted: Boolean = false
        private set

    private var identity: RecognitionSceneIdentity? = null
    private var photoFirstFramePresented = false
    private var firstFramePresentedTimestampMs: Long? = null
    private var sceneStartedAtMs = UNSET
    private var sceneElapsedBeforePauseMs = 0L
    private var sceneClockStartedAtMs: Long? = null
    private var stageStartedAtMs = UNSET
    private var stageElapsedBeforePauseMs = 0L
    private var previewStartedAtMs: Long? = null
    private var previewElapsedBeforePauseMs = 0L
    private var diagnosticAuto = false
    private var autoElapsedBeforePauseMs = 0L
    private var autoStartedAtMs: Long? = null
    private var geometryGateStartedAtMs: Long? = null
    private var geometryFrozenElapsedMs = 0L
    private var resultGeometryVersions: Set<Long> = emptySet()
    private var eventSequence = 0L
    private var lastAcceptedEventSequence = 0L
    private var lastAcceptedEventTimestampMs = 0L
    private val acceptedEnergyEvents = mutableSetOf<String>()
    private val acceptedEnergyEventTrace = mutableListOf<EnergyEvent>()
    private val acceptedResultHandoffEvents = mutableListOf<RecognitionResultHandoffEvent>()
    private val completedResultHandoffSubstages = mutableSetOf<ResultHandoffSubstage>()
    private var lastResultHandoffEventSequence = 0L
    private var lastResultHandoffTimestampMs = 0L
    private val stageTimeline = mutableListOf<RecognitionSceneStageTraceEntry>()
    private var stageGateWaitStartedAtMs: Long? = null
    private var stageGateWaitReason: String? = null
    private var stageEventObserver: ((RecognitionSceneStageEvent) -> Unit)? = null
    private var lastObservedGeometryVersion = 0L
    var resultSceneStable: Boolean = false
        private set

    /** Immutable readout for integration evidence; only controller-accepted events are exposed. */
    fun acceptedEnergyEventsSnapshot(): List<EnergyEvent> = acceptedEnergyEventTrace.toList()
    fun acceptedResultHandoffEventsSnapshot(): List<RecognitionResultHandoffEvent> = acceptedResultHandoffEvents.toList()
    fun completedResultHandoffSubstagesSnapshot(): Set<ResultHandoffSubstage> = completedResultHandoffSubstages.toSet()
    fun stageTimelineSnapshot(): List<RecognitionSceneStageTraceEntry> = stageTimeline.toList()
    fun setStageEventObserver(observer: ((RecognitionSceneStageEvent) -> Unit)?) {
        stageEventObserver = observer
    }
    fun firstFramePresentedTimestampMs(): Long? = firstFramePresentedTimestampMs
    fun resultGeometryVersionsSnapshot(): Set<Long> = resultGeometryVersions.toSet()
    fun resultGeometryIsReady(): Boolean = resultGeometryState == SceneGeometryState.READY
    fun sceneComplete(): Boolean = RecognitionSceneCompletionContract.isComplete(
        opticalResidueComplete = residueSettleComplete,
        completedResultHandoffEvents = completedResultHandoffSubstages,
        resultSceneStable = resultSceneStable,
    )

    private var detectorReady = false
    private var classifierReady = false
    private var slimSamTerminal = false
    private var slimSamFailed = false
    private var maskTrusted = false
    private var contourValid = false
    private var contourClosed = false
    private var orbAnchorReady = false
    private var releasePathReady = false
    private var perimeterArrivalPresented = false
    private var perimeterArrivalPathReady = false
    private var perimeterArrivalPathId: String? = null
    private var perimeterArrivalGeometryVersion: Long? = null
    private var perimeterArrivalPathGeometry: WaterLightPathGeometry? = null
    private var photoTransformReady = false
    private var activePhotoGeometryVersion: Long? = null
    private var activeMaskSha256: String? = null
    private var activeMaskVersion: Long? = null
    private var activeMaskGeometryVersion: Long? = null
    private var activeMaskContactPointNormalizedSource: WaterLightPathPoint? = null
    private var activeFishContactPathId: String? = null
    private var fishContactPathId: String? = null
    private var fishPathSnapshot: RecognitionFishPathSnapshot? = null
    private var revealDecisionState = RecognitionRevealDecisionState.WAITING_BOTH
    private var fishContactPathReady = false
    private var fishPathBindingStatus = RecognitionFishPathBindingStatus.WAITING_FOR_PHOTO_GEOMETRY
    private val contourRenderReceipts = mutableMapOf<RecognitionSceneStage, RecognitionContourRenderReceipt>()
    private var lastContourDrawPassSequence = 0L
    private var fishGoldComplete = false
    private var orbReturnComplete = false
    private var orbToGlyphComplete = false
    private var orbCoreDissolveReceipt: RecognitionOrbCoreDissolveReceipt? = null
    private var glyphRevealComplete = false
    private var glyphReadiness = RecognitionGlyphReadiness()
    private var residueSettleComplete = false
    private var resultGeometryState = SceneGeometryState.PENDING
    private var residueRenderReceipt: RecognitionResidueRenderReceipt? = null
    private var failed = false
    var lastRejectedEnergyEvent: EnergyEventRejectionDiagnostic? = null
        private set

    val modelGates: RecognitionSceneModelGates
        get() = RecognitionSceneModelGates(
            detector = when {
                detectorReady -> SceneResourceState.READY
                failed -> SceneResourceState.FAILED
                else -> SceneResourceState.WAITING
            },
            classifier = if (classifierReady) SceneResourceState.READY else if (failed) SceneResourceState.FAILED else SceneResourceState.WAITING,
            slimSam = when {
                maskTrusted -> SceneMaskState.TRUSTED
                slimSamFailed -> SceneMaskState.FAILED
                slimSamTerminal -> SceneMaskState.UNTRUSTED
                else -> SceneMaskState.PENDING
            },
            mask = when {
                maskTrusted -> SceneMaskState.TRUSTED
                slimSamFailed -> SceneMaskState.FAILED
                slimSamTerminal -> SceneMaskState.UNTRUSTED
                detectorReady -> SceneMaskState.PENDING
                else -> SceneMaskState.NONE
            },
            geometry = resultGeometryState,
            runState = when {
                failed -> SceneRunState.FAILED
                sceneComplete() -> SceneRunState.COMPLETE
                paused -> SceneRunState.PAUSED
                geometryGateStartedAtMs != null -> SceneRunState.WAITING
                !photoFirstFramePresented -> SceneRunState.WAITING
                mode == ScenePlaybackMode.AUTO && waitingReason() != null -> SceneRunState.WAITING
                else -> SceneRunState.RUNNING
            },
            waitingReason = waitingReason(),
        )

    /** Begin a fresh PhotoSession timeline. Normal application sessions always use event-gated AUTO. */
    fun beginNewSession(
        identity: RecognitionSceneIdentity,
        @Suppress("UNUSED_PARAMETER") debugBuild: Boolean,
        nowMs: Long,
    ) {
        this.identity = identity
        resetClockState(nowMs)
        photoFirstFramePresented = false
        firstFramePresentedTimestampMs = null
        sceneStartedAtMs = UNSET
        sceneElapsedBeforePauseMs = 0L
        sceneClockStartedAtMs = null
        stageStartedAtMs = UNSET
        mode = ScenePlaybackMode.AUTO
        currentStage = RecognitionSceneStage.PHOTO_STABLE
        paused = false
        previewCompleted = false
        diagnosticAuto = false
    }

    /** Compatibility reset for deterministic unit fixtures; production uses beginNewSession. */
    fun reset(nowMs: Long) {
        identity = null
        resetClockState(nowMs)
        photoFirstFramePresented = true
        firstFramePresentedTimestampMs = nowMs
        sceneStartedAtMs = nowMs
        sceneClockStartedAtMs = nowMs.takeIf { !paused }
        stageStartedAtMs = nowMs
        mode = ScenePlaybackMode.AUTO
    }

    fun cancelSession() {
        identity = null
        resetClockState(0L)
        photoFirstFramePresented = false
        firstFramePresentedTimestampMs = null
        sceneStartedAtMs = UNSET
        stageStartedAtMs = UNSET
        mode = ScenePlaybackMode.AUTO
    }

    /** PHOTO_STABLE and the scene clock start only after the current source photo is presented. */
    fun markPhotoFirstFramePresented(
        currentIdentity: RecognitionSceneIdentity,
        nowMs: Long,
    ): Boolean {
        if (!isCurrent(currentIdentity) || photoFirstFramePresented) return false
        photoFirstFramePresented = true
        firstFramePresentedTimestampMs = nowMs
        sceneStartedAtMs = nowMs
        stageStartedAtMs = nowMs
        stageGateWaitStartedAtMs = null
        stageGateWaitReason = null
        previewStartedAtMs = nowMs.takeIf { mode == ScenePlaybackMode.MANUAL && !paused }
        if (!paused && geometryGateStartedAtMs == null) sceneClockStartedAtMs = nowMs
        bumpEventSequence()
        emitStageEvent(
            action = RecognitionSceneStageEventAction.ENTERED,
            stage = RecognitionSceneStage.PHOTO_STABLE,
            nowMs = nowMs,
            progress = 0f,
            waitingReason = null,
        )
        return true
    }

    /** Prepare 01/13 without starting its local 3s until the real photo frame arrives. */
    fun prepareInitialStepPreview() {
        mode = ScenePlaybackMode.MANUAL
        currentStage = RecognitionSceneStage.PHOTO_STABLE
        paused = false
        previewCompleted = false
        previewStartedAtMs = null
        previewElapsedBeforePauseMs = 0L
        diagnosticAuto = false
        autoStartedAtMs = null
        autoElapsedBeforePauseMs = 0L
        geometryGateStartedAtMs = null
        geometryFrozenElapsedMs = 0L
        resultGeometryVersions = emptySet()
        sceneClockStartedAtMs = null
        bumpEventSequence()
    }

    fun startPreparedStepPreview(nowMs: Long) {
        if (mode != ScenePlaybackMode.MANUAL || paused || previewCompleted || previewStartedAtMs != null) return
        if (!photoFirstFramePresented) return
        previewStartedAtMs = nowMs
        if (sceneStartedAtMs == UNSET) sceneStartedAtMs = nowMs
        if (stageStartedAtMs == UNSET) stageStartedAtMs = nowMs
    }

    fun onPipelinePhase(
        phase: RecognitionPhase,
        nowMs: Long,
        hasRealFishFocus: Boolean = false,
        currentIdentity: RecognitionSceneIdentity? = null,
    ) {
        if (!isCurrent(currentIdentity)) return
        when (phase) {
            RecognitionPhase.DETECTING -> detectorReady = detectorReady || hasRealFishFocus
            RecognitionPhase.OUTLINE,
            RecognitionPhase.CLASSIFYING -> detectorReady = detectorReady || hasRealFishFocus
            RecognitionPhase.RESULT -> {
                classifierReady = true
                detectorReady = detectorReady || hasRealFishFocus
            }
            RecognitionPhase.FAILURE -> failed = true
            RecognitionPhase.CAPTURED -> Unit
        }
        if (stageStartedAtMs == UNSET && photoFirstFramePresented) stageStartedAtMs = nowMs
    }

    fun onDetectorTargetAvailable(ready: Boolean, currentIdentity: RecognitionSceneIdentity? = null) {
        if (isCurrent(currentIdentity)) detectorReady = ready
    }

    fun onPhotoTransformAvailable(
        ready: Boolean,
        currentIdentity: RecognitionSceneIdentity? = null,
        geometryVersion: Long? = null,
    ) {
        if (!isCurrent(currentIdentity)) return
        // Geometry versions use the positive fingerprint namespace; zero is unmeasured.
        val acceptedVersion = geometryVersion?.takeIf { it > 0L }
        val nextReady = ready && (geometryVersion == null || acceptedVersion != null)
        if (!nextReady) {
            photoTransformReady = false
            perimeterArrivalPathReady = false
            perimeterArrivalPathId = null
            perimeterArrivalPathGeometry = null
            perimeterArrivalGeometryVersion = null
            perimeterArrivalPresented = false
            if (activePhotoGeometryVersion != null) {
                activePhotoGeometryVersion = null
                invalidateMaskContactPath()
            }
            return
        }
        if (activePhotoGeometryVersion != acceptedVersion) {
            activePhotoGeometryVersion = acceptedVersion
            perimeterArrivalPathReady = false
            perimeterArrivalPathId = null
            perimeterArrivalPathGeometry = null
            perimeterArrivalGeometryVersion = null
            perimeterArrivalPresented = false
            invalidateMaskContactPath()
        }
        photoTransformReady = true
    }

    fun onOrbAnchorAvailable(ready: Boolean, currentIdentity: RecognitionSceneIdentity? = null) {
        if (isCurrent(currentIdentity)) orbAnchorReady = ready
    }

    fun onReleasePathAvailable(ready: Boolean, currentIdentity: RecognitionSceneIdentity? = null) {
        if (isCurrent(currentIdentity)) releasePathReady = ready
    }

    fun onPerimeterArrivalPresented(ready: Boolean, currentIdentity: RecognitionSceneIdentity? = null) {
        if (isCurrent(currentIdentity)) perimeterArrivalPresented = ready
    }

    fun onPerimeterArrivalPathAvailable(
        pathId: String?,
        pathGeometry: WaterLightPathGeometry?,
        geometryVersion: Long,
        currentIdentity: RecognitionSceneIdentity? = null,
    ): Boolean {
        if (!isCurrent(currentIdentity) || !photoTransformReady || geometryVersion <= 0L ||
            geometryVersion != activePhotoGeometryVersion || pathId.isNullOrBlank() ||
            pathGeometry?.isValid() != true
        ) return false
        val existing = perimeterArrivalPathId
        if (existing != null && (existing != pathId || perimeterArrivalGeometryVersion != geometryVersion ||
                perimeterArrivalPathGeometry != pathGeometry)
        ) return false
        perimeterArrivalPathReady = true
        perimeterArrivalPathId = pathId
        perimeterArrivalGeometryVersion = geometryVersion
        perimeterArrivalPathGeometry = pathGeometry
        return true
    }

    fun onMaskTerminal(
        trusted: Boolean,
        contourValid: Boolean,
        contourClosed: Boolean,
        failedTerminal: Boolean = false,
        currentIdentity: RecognitionSceneIdentity? = null,
    ) {
        if (!isCurrent(currentIdentity)) return
        slimSamTerminal = true
        slimSamFailed = failedTerminal
        if (!trusted || failedTerminal) {
            activeMaskSha256 = null
            activeMaskVersion = null
            invalidateMaskContactPath()
        }
        // A terminal callback cannot promote an anonymous bitmap to a trusted Mask.
        // updateModelJoin supplies the exact SHA/version pair before trust is granted.
        maskTrusted = trusted && !failedTerminal && activeMaskSha256 != null && activeMaskVersion != null
        this.contourValid = maskTrusted && contourValid
        this.contourClosed = maskTrusted && contourClosed
    }

    fun onRevealDecisionReady(currentIdentity: RecognitionSceneIdentity? = null) {
        if (!isCurrent(currentIdentity)) return
        // Compatibility for deterministic controller fixtures. Production supplies the
        // explicit decision through onRevealDecision after validating the real P* path.
        revealDecisionState = if (maskTrusted && contourValid && contourClosed) {
            RecognitionRevealDecisionState.FISH_FOCUS_READY
        } else {
            RecognitionRevealDecisionState.ORB_ONLY_READY
        }
        revealDecisionReady = true
    }

    fun currentRevealDecision(): RecognitionRevealDecisionState = revealDecisionState

    fun onRevealDecision(
        decision: RecognitionRevealDecisionState,
        nowMs: Long = lastNowMs,
        currentIdentity: RecognitionSceneIdentity? = null,
    ) {
        if (!isCurrent(currentIdentity)) return
        revealDecisionState = decision
        revealDecisionReady = RecognitionRevealDecisionGate.isReady(decision)
        if (decision == RecognitionRevealDecisionState.ORB_ONLY_READY &&
            currentStage in setOf(
                RecognitionSceneStage.CONTOUR_A,
                RecognitionSceneStage.CONTOUR_B,
                RecognitionSceneStage.CONTOUR_C,
                RecognitionSceneStage.FISH_GOLD_PEAK,
            )
        ) {
            // Terminate an invalidated contour route visibly without recording false stage exits.
            emitStageEvent(
                action = RecognitionSceneStageEventAction.BLOCKED,
                stage = currentStage,
                nowMs = nowMs,
                progress = stageProgress(nowMs),
                waitingReason = "FULL_REVEAL_PATH_INVALIDATED_OR_DOWNGRADED",
            )
            currentStage = RecognitionSceneStage.ORB_RETURN
            stageStartedAtMs = nowMs
            stageElapsedBeforePauseMs = 0L
            stageGateWaitStartedAtMs = null
            stageGateWaitReason = null
            bumpEventSequence()
            emitStageEvent(
                action = RecognitionSceneStageEventAction.ENTERED,
                stage = RecognitionSceneStage.ORB_RETURN,
                nowMs = nowMs,
                progress = 0f,
                waitingReason = null,
            )
        }
    }

    fun onFishPathBindingStatus(
        status: RecognitionFishPathBindingStatus,
        currentIdentity: RecognitionSceneIdentity? = null,
    ) {
        if (isCurrent(currentIdentity)) fishPathBindingStatus = status
    }

    private var revealDecisionReady = false

    fun onFishGoldRenderComplete(
        receipt: RecognitionFishGoldRenderReceipt,
        currentIdentity: RecognitionSceneIdentity? = null,
    ): Boolean {
        if (!isCurrent(currentIdentity) || currentStage != RecognitionSceneStage.FISH_GOLD_PEAK) return false
        val activeIdentity = identity ?: return false
        val maskSha256 = activeMaskSha256 ?: return false
        val maskVersion = activeMaskVersion ?: return false
        val geometryVersion = activeMaskGeometryVersion ?: return false
        val pathId = activeFishContactPathId ?: return false
        if (!RecognitionFishGoldRenderContract.isComplete(
                identity = activeIdentity,
                maskSha256 = maskSha256,
                maskVersion = maskVersion,
                geometryVersion = geometryVersion,
                fishPathId = pathId,
                receipt = receipt,
            )
        ) return false
        fishGoldComplete = true
        return true
    }

    fun onContourRenderComplete(
        receipt: RecognitionContourRenderReceipt,
        currentIdentity: RecognitionSceneIdentity? = null,
    ): Boolean {
        if (!isCurrent(currentIdentity) || currentStage != receipt.stage ||
            receipt.identity != identity || !RecognitionContourRenderContract.isComplete(receipt)
        ) return false
        val activeMaskSha = activeMaskSha256 ?: return false
        val activeMaskVersion = activeMaskVersion ?: return false
        val activeGeometryVersion = activeMaskGeometryVersion ?: return false
        val activePathId = activeFishContactPathId ?: return false
        if (!maskTrusted || !contourValid || !contourClosed || receipt.maskSha256 != activeMaskSha ||
            receipt.maskVersion != activeMaskVersion || receipt.geometryVersion != activeGeometryVersion ||
            receipt.fishPathId != activePathId || receipt.drawPassSequence <= lastContourDrawPassSequence
        ) return false
        contourRenderReceipts[receipt.stage] = receipt
        lastContourDrawPassSequence = receipt.drawPassSequence
        bumpEventSequence()
        return true
    }

    fun onGlyphAvailable(ready: Boolean, currentIdentity: RecognitionSceneIdentity? = null) {
        if (isCurrent(currentIdentity)) {
            glyphReadiness = if (ready) glyphReadiness.copy(speciesResultReady = true) else RecognitionGlyphReadiness()
            val active = identity
            glyphAvailable = active != null && RecognitionGlyphReadinessContract.isPathReady(
                active, glyphReadiness.descriptor?.speciesKey.orEmpty(),
                glyphReadiness.descriptor?.speciesName.orEmpty(), glyphReadiness,
            )
        }
    }

    fun onGlyphPathReady(readiness: RecognitionGlyphReadiness, currentIdentity: RecognitionSceneIdentity? = null) {
        if (!isCurrent(currentIdentity)) return
        val descriptor = readiness.descriptor
        val valid = descriptor != null &&
            RecognitionGlyphReadinessContract.isPathReady(descriptor.identity, descriptor.speciesKey, descriptor.speciesName, readiness) &&
            descriptor.identity == identity
        glyphReadiness = if (valid) readiness.copy(
            glyphRevealRendered = false,
            nameStable = false,
            renderReceipt = null,
        ) else RecognitionGlyphReadiness(speciesResultReady = readiness.speciesResultReady)
        glyphAvailable = valid
        glyphRevealComplete = false
    }

    fun onGlyphRevealRendered(readiness: RecognitionGlyphReadiness, currentIdentity: RecognitionSceneIdentity? = null): Boolean {
        if (!isCurrent(currentIdentity)) return false
        if (currentStage != RecognitionSceneStage.SPECIES_NAME_REVEAL) return false
        val descriptor = glyphReadiness.descriptor ?: return false
        if (readiness.descriptor != descriptor ||
            !RecognitionGlyphReadinessContract.isRendered(
                descriptor.identity, descriptor.speciesKey, descriptor.speciesName, readiness,
            )
        ) return false
        glyphReadiness = readiness.copy(nameStable = false)
        glyphAvailable = true
        glyphRevealComplete = true
        bumpEventSequence()
        return true
    }

    private var glyphAvailable = false

    fun onOrbCoreDissolveRendered(receipt: RecognitionOrbCoreDissolveReceipt): Boolean {
        val active = identity ?: return false
        if (!isCurrent(receipt.identity) || currentStage != RecognitionSceneStage.ORB_TO_NAME ||
            !RecognitionOrbCoreDissolveContract.isComplete(active, receipt)
        ) return false
        orbCoreDissolveReceipt = receipt
        orbToGlyphComplete = true
        bumpEventSequence()
        return true
    }

    fun orbCoreDissolveReceiptSnapshot(): RecognitionOrbCoreDissolveReceipt? = orbCoreDissolveReceipt

    fun onSceneTransitionComplete(
        stage: RecognitionSceneStage,
        currentIdentity: RecognitionSceneIdentity? = null,
    ) {
        if (!isCurrent(currentIdentity)) return
        when (stage) {
            RecognitionSceneStage.ORB_TO_NAME -> Unit
            RecognitionSceneStage.SPECIES_NAME_REVEAL -> Unit
            RecognitionSceneStage.RESIDUE_SETTLE -> Unit
            else -> Unit
        }
    }

    fun onResidueRenderComplete(
        receipt: RecognitionResidueRenderReceipt,
        currentIdentity: RecognitionSceneIdentity? = null,
    ): Boolean {
        if (!isCurrent(currentIdentity) || currentStage != RecognitionSceneStage.RESIDUE_SETTLE ||
            receipt.identity != identity || !RecognitionResidueRenderContract.isComplete(receipt)
        ) return false
        val existing = residueRenderReceipt
        if (existing != null && receipt.drawPassSequence <= existing.drawPassSequence) return false
        residueRenderReceipt = receipt
        residueSettleComplete = true
        bumpEventSequence()
        return true
    }

    /** Non-mutating preflight used by the Attempt-level SessionStore/Controller transaction. */
    fun canAcceptResultHandoffEvent(event: RecognitionResultHandoffEvent): String? {
        val active = identity ?: return "SCENE_IDENTITY_MISSING"
        if (event.identity != active) return "STALE_OR_MISMATCHED_ATTEMPT"
        if (!RecognitionResultHandoffContract.isNextEventSequence(event.eventSequence, lastResultHandoffEventSequence)) {
            return "EVENT_SEQUENCE_NOT_CONTIGUOUS"
        }
        if (!RecognitionResultHandoffContract.hasStrictlyLaterTimestamp(event.monotonicTimestampMs, lastResultHandoffTimestampMs) ||
            event.geometryVersion <= 0L ||
            event.geometryVersion !in resultGeometryVersions || resultGeometryState != SceneGeometryState.READY
        ) return "RESULT_GEOMETRY_NOT_CURRENT"
        if (event.stage != currentStage || event.renderStatus != "READY" || event.progress < .999f ||
            !event.progress.isFinite() || event.eventProducer.isBlank() ||
            event.eventConsumer != RECOGNITION_RESULT_HANDOFF_EVENT_CONSUMER ||
            event.source.isBlank() || event.target.isBlank() || event.feedback.isBlank() || event.resultIdentity.isBlank()
        ) return "EVENT_NOT_COMPLETE_OR_STAGE_MISMATCH"
        if (event.identity.photoSha256.isNullOrBlank() || event.identity.photoContentVersion < 0L) {
            return "PHOTO_IDENTITY_INVALID"
        }
        val originalPath = fishPathSnapshot
        if (originalPath != null && (event.pathId != originalPath.returnPathId ||
                event.maskSha256 != originalPath.maskSha256 || event.maskVersion != originalPath.maskVersion ||
                event.pathGeometryVersion != originalPath.geometryVersion ||
                event.pStarNormalizedSource != originalPath.pStarNormalizedSource ||
                event.pathSegments != originalPath.returnPathSegments)
        ) return "PROCESSING_PATH_IDENTITY_MISMATCH"
        if (originalPath == null && (event.pathId != null || event.maskSha256 != null || event.maskVersion != null ||
                event.pathGeometryVersion != null || event.pStarNormalizedSource != null || event.pathSegments.isNotEmpty())
        ) {
            return "UNTRUSTED_PATH_MUST_NOT_BE_PROMOTED"
        }
        if (!RecognitionResultHandoffContract.isNext(event.substage, completedResultHandoffSubstages)) {
            return "RESULT_SUBSTAGE_OUT_OF_ORDER"
        }
        val descriptor = glyphReadiness.descriptor
            ?: return "GLYPH_PATH_NOT_READY"
        val expectedReadiness = glyphReadiness.copy(
            nameStable = event.substage == ResultHandoffSubstage.NAME_STABLE || glyphReadiness.nameStable,
        )
        if (event.glyphReadiness != expectedReadiness ||
            event.resultIdentity != descriptor.speciesKey ||
            !RecognitionGlyphReadinessContract.isRendered(
                active, descriptor.speciesKey, descriptor.speciesName, expectedReadiness,
            )
        ) return "GLYPH_PATH_OR_RENDER_IDENTITY_MISMATCH"
        if (event.substage == ResultHandoffSubstage.NAME_STABLE &&
            !RecognitionGlyphReadinessContract.isNameStableClaim(
                active, descriptor.speciesKey, descriptor.speciesName, expectedReadiness,
            )
        ) return "GLYPH_NOT_RENDERED"
        if (event.substage != ResultHandoffSubstage.NAME_STABLE && !glyphReadiness.nameStable) {
            return "NAME_STABLE_NOT_ACCEPTED"
        }
        val prerequisites = when (event.substage) {
            ResultHandoffSubstage.NAME_STABLE -> currentStage == RecognitionSceneStage.SPECIES_NAME_REVEAL &&
                event.stage == RecognitionSceneStage.SPECIES_NAME_REVEAL && event.progress >= .999f &&
                glyphAvailable && glyphRevealComplete
            ResultHandoffSubstage.PHOTO_SETTLE -> currentStage == RecognitionSceneStage.RESIDUE_SETTLE &&
                event.stage == RecognitionSceneStage.RESIDUE_SETTLE && event.progress >= .999f
            ResultHandoffSubstage.RESULT_SCENE_HANDOFF -> photoFirstFramePresented &&
                currentStage == RecognitionSceneStage.RESIDUE_SETTLE && event.stage == RecognitionSceneStage.RESIDUE_SETTLE &&
                ResultHandoffSubstage.PHOTO_SETTLE in completedResultHandoffSubstages
            ResultHandoffSubstage.RESULT_INFO_ARRIVAL ->
                currentStage == RecognitionSceneStage.RESIDUE_SETTLE && event.stage == RecognitionSceneStage.RESIDUE_SETTLE &&
                    ResultHandoffSubstage.RESULT_SCENE_HANDOFF in completedResultHandoffSubstages &&
                    RecognitionResultInfoRenderContract.isComplete(
                        active, event.resultIdentity, event.geometryVersion, event.resultInfoRenderReceipt,
                    )
            ResultHandoffSubstage.RESULT_SCENE_STABLE -> {
                val settleAccepted = acceptedEnergyEventTrace.any {
                    it.feedback == EnergyFeedback.SETTLE && it.stage == RecognitionSceneStage.RESIDUE_SETTLE
                }
                currentStage == RecognitionSceneStage.RESIDUE_SETTLE && event.stage == RecognitionSceneStage.RESIDUE_SETTLE &&
                    event.progress >= .999f && residueSettleComplete && settleAccepted &&
                    RecognitionResultStableRenderContract.isComplete(
                        active, event.resultIdentity, event.geometryVersion, event.resultStableRenderReceipt,
                    ) &&
                    completedResultHandoffSubstages.containsAll(
                        ResultHandoffSubstage.entries.dropLast(1).toSet(),
                    )
            }
        }
        if (!prerequisites) return "SUBSTAGE_PREREQUISITES_MISSING"
        return null
    }

    /** Result milestones are accepted only against this Attempt's measured geometry and path. */
    fun acceptResultHandoffEvent(event: RecognitionResultHandoffEvent): RecognitionResultHandoffReceipt {
        val rejection = canAcceptResultHandoffEvent(event)
        if (rejection != null) return RecognitionResultHandoffReceipt(false, rejection, resultSceneStable)
        val expectedReadiness = glyphReadiness.copy(
            nameStable = event.substage == ResultHandoffSubstage.NAME_STABLE || glyphReadiness.nameStable,
        )
        completedResultHandoffSubstages += event.substage
        if (event.substage == ResultHandoffSubstage.NAME_STABLE) glyphReadiness = expectedReadiness
        acceptedResultHandoffEvents += event
        lastResultHandoffEventSequence = event.eventSequence
        lastResultHandoffTimestampMs = event.monotonicTimestampMs
        if (event.substage == ResultHandoffSubstage.RESULT_SCENE_STABLE) {
            resultSceneStable = true
            bumpEventSequence()
            closeCurrentStage(event.monotonicTimestampMs, eventSequence)
        }
        return RecognitionResultHandoffReceipt(true, resultSceneStable = resultSceneStable)
    }

    /** Set a geometry gate independently from user pause. Late geometry resumes from the held offset. */
    fun setResultGeometryState(
        state: SceneGeometryState,
        nowMs: Long,
        currentIdentity: RecognitionSceneIdentity? = null,
        geometryVersions: Set<Long> = emptySet(),
    ) {
        if (!isCurrent(currentIdentity)) return
        val acceptedVersions = geometryVersions.takeIf { state == SceneGeometryState.READY }.orEmpty()
        val versionChanged = state == SceneGeometryState.READY && resultGeometryVersions != acceptedVersions
        if (state == resultGeometryState && !versionChanged &&
            (state != SceneGeometryState.PENDING || geometryGateStartedAtMs != null)
        ) return
        when (state) {
            SceneGeometryState.PENDING -> {
                resultGeometryVersions = emptySet()
                if (geometryGateStartedAtMs == null) {
                    geometryFrozenElapsedMs = stageElapsedMs(nowMs)
                    geometryGateStartedAtMs = nowMs
                    stopSceneClock(nowMs)
                }
            }
            SceneGeometryState.READY,
            SceneGeometryState.INVALID -> {
                resultGeometryVersions = acceptedVersions
                val startedAt = geometryGateStartedAtMs
                if (startedAt != null) {
                    val heldFor = (nowMs - startedAt).coerceAtLeast(0L)
                    stageStartedAtMs = stageStartedAtMs.takeIf { it != UNSET }?.plus(heldFor) ?: UNSET
                    previewStartedAtMs = previewStartedAtMs?.plus(heldFor)
                    autoStartedAtMs = autoStartedAtMs?.plus(heldFor)
                    geometryGateStartedAtMs = null
                    if (!paused && photoFirstFramePresented) startSceneClock(nowMs)
                }
            }
        }
        resultGeometryState = state
    }

    /** Accept one current, non-duplicate event and use it to unlock the next semantic state. */
    fun recordEnergyEvent(
        frame: RecognitionSceneFrame,
        event: EnergyEvent,
    ): Boolean = recordEnergyEventWithReason(frame, event).accepted

    fun recordEnergyEventWithReason(
        frame: RecognitionSceneFrame,
        event: EnergyEvent,
    ): EnergyEventAcceptanceResult {
        fun reject(reason: EnergyEventRejectionReason): EnergyEventAcceptanceResult {
            lastRejectedEnergyEvent = EnergyEventRejectionDiagnostic(event, reason, frame.stageProgress, modelGates)
            return EnergyEventAcceptanceResult(false, reason)
        }
        val active = identity
        if (!frame.isIdentityValid || (active != null && frame.identity != active)) {
            return reject(EnergyEventRejectionReason.EVENT_IDENTITY_MISMATCH)
        }
        val eventKey = "${frame.identity.sessionId}|${frame.identity.attemptId}|${frame.identity.imageId}|${frame.geometryVersion}|${event.stage}|${event.feedback}|${event.pathId}"
        if (eventKey in acceptedEnergyEvents) return reject(EnergyEventRejectionReason.EVENT_DUPLICATE)
        if (event.stage != frame.stage || currentStage != frame.stage) {
            return reject(EnergyEventRejectionReason.EVENT_STAGE_MISMATCH)
        }
        if (frame.stageProgress < .999f) return reject(EnergyEventRejectionReason.EVENT_NOT_AT_STAGE_COMPLETION)
        if (event.eventSequence != frame.eventSequence || event.eventSequence != eventSequence ||
            event.eventSequence <= lastAcceptedEventSequence
        ) return reject(EnergyEventRejectionReason.EVENT_SEQUENCE_MISMATCH)
        if (event.sessionId != frame.identity.sessionId || event.attemptId != frame.identity.attemptId ||
            event.imageId != frame.identity.imageId || event.photoSha256 != frame.identity.photoSha256 ||
            event.photoContentVersion != frame.identity.photoContentVersion
        ) return reject(EnergyEventRejectionReason.EVENT_IDENTITY_MISMATCH)
        if (event.monotonicTimestampMs < 0L || event.monotonicTimestampMs > frame.monotonicTimestampMs ||
            event.monotonicTimestampMs != frame.monotonicTimestampMs ||
            event.referenceMs != frame.opticalReferenceElapsedMs ||
            event.monotonicTimestampMs < lastAcceptedEventTimestampMs
        ) return reject(EnergyEventRejectionReason.EVENT_TIMESTAMP_MISMATCH)
        if (event.geometryVersion != frame.geometryVersion ||
            (frame.stage.ordinal >= RecognitionSceneStage.ORB_RETURN.ordinal &&
                (resultGeometryState != SceneGeometryState.READY || event.geometryVersion !in resultGeometryVersions))
        ) return reject(EnergyEventRejectionReason.EVENT_GEOMETRY_MISMATCH)
        if (event.pathId.isNullOrBlank() || event.renderStatus in setOf("PENDING", "PATH_PENDING")) {
            return reject(EnergyEventRejectionReason.EVENT_PATH_PENDING)
        }
        if (event.sourceHost != event.source.name || event.targetHost != event.target.name ||
            event.renderStatus != "READY" || !event.hasRenderablePath()
        ) return reject(EnergyEventRejectionReason.EVENT_PATH_INVALID)
        if (!event.level.isFinite() || event.level <= 0f || event.level > 1f) {
            return reject(EnergyEventRejectionReason.EVENT_INVALID)
        }
        if (event.feedback == EnergyFeedback.ARRIVE && event.stage == RecognitionSceneStage.PERIMETER_ARRIVAL) {
            if (!perimeterArrivalPathReady || perimeterArrivalPathId == null) {
                return reject(EnergyEventRejectionReason.EVENT_PATH_PENDING)
            }
            if (event.pathId != perimeterArrivalPathId ||
                event.geometryVersion != perimeterArrivalGeometryVersion ||
                event.pathGeometry != perimeterArrivalPathGeometry
            ) return reject(EnergyEventRejectionReason.EVENT_PATH_INVALID)
        }
        if (event.feedback == EnergyFeedback.ABSORB && event.stage in setOf(
                RecognitionSceneStage.PATH_TO_FISH,
                RecognitionSceneStage.CONTOUR_A,
                RecognitionSceneStage.CONTOUR_B,
                RecognitionSceneStage.CONTOUR_C,
            )
        ) {
            if (revealDecisionState == RecognitionRevealDecisionState.ORB_ONLY_READY ||
                !maskTrusted || event.pathId != activeFishContactPathId ||
                activeMaskGeometryVersion != frame.geometryVersion
            ) return reject(EnergyEventRejectionReason.EVENT_MASK_IDENTITY_MISMATCH)
            if (activeMaskContactPointNormalizedSource == null ||
                (event.pathMaskSha256 != null && event.pathMaskSha256 != activeMaskSha256) ||
                (event.pathMaskVersion != null && event.pathMaskVersion != activeMaskVersion) ||
                (event.pathContactPointNormalizedSource != null &&
                    event.pathContactPointNormalizedSource != activeMaskContactPointNormalizedSource)
            ) return reject(EnergyEventRejectionReason.EVENT_PSTAR_BIND_MISMATCH)
            val acceptedPathId = fishContactPathId
            if (acceptedPathId != null && acceptedPathId != event.pathId) {
                return reject(EnergyEventRejectionReason.EVENT_MASK_IDENTITY_MISMATCH)
            }
        }
        if (event.feedback == EnergyFeedback.RETURN && event.source == EnergyAnchor.FISH_CONTOUR) {
            val snapshot = fishPathSnapshot
                ?: return reject(EnergyEventRejectionReason.EVENT_PSTAR_BIND_MISMATCH)
            if (event.pathId != fishContactPathId || event.pathId != snapshot.returnPathId ||
                event.pathSegments != snapshot.returnPathSegments ||
                event.pathMaskSha256 != snapshot.maskSha256 ||
                event.pathMaskVersion != snapshot.maskVersion ||
                event.pathGeometryVersion != snapshot.geometryVersion ||
                event.pathContactPointNormalizedSource != snapshot.pStarNormalizedSource
            ) return reject(EnergyEventRejectionReason.EVENT_PSTAR_BIND_MISMATCH)
        }
        if (!acceptedEnergyEvents.add(eventKey)) return reject(EnergyEventRejectionReason.EVENT_DUPLICATE)
        if (event.feedback == EnergyFeedback.ABSORB && event.stage in setOf(
                RecognitionSceneStage.PATH_TO_FISH,
                RecognitionSceneStage.CONTOUR_A,
                RecognitionSceneStage.CONTOUR_B,
                RecognitionSceneStage.CONTOUR_C,
            )
        ) {
            fishContactPathId = event.pathId
            fishContactPathReady = true
        }
        acceptedEnergyEventTrace += event
        lastAcceptedEventSequence = event.eventSequence
        lastAcceptedEventTimestampMs = event.monotonicTimestampMs
        when (event.feedback) {
            EnergyFeedback.RELEASE -> {
                releasePathReady = event.stage == RecognitionSceneStage.ORB_RELEASE &&
                    event.source == EnergyAnchor.ORB_RIBBON && event.target == EnergyAnchor.PHOTO_PERIMETER
            }
            EnergyFeedback.ARRIVE -> perimeterArrivalPresented = event.stage == RecognitionSceneStage.PERIMETER_ARRIVAL &&
                event.source == EnergyAnchor.PHOTO_PERIMETER && event.target == EnergyAnchor.PHOTO_PATH
            EnergyFeedback.ABSORB -> if (maskTrusted && event.source == EnergyAnchor.PHOTO_PATH &&
                event.target == EnergyAnchor.FISH_CONTOUR
            ) contourValid = true
            EnergyFeedback.RETURN -> orbReturnComplete = event.stage == RecognitionSceneStage.ORB_RETURN &&
                event.target == EnergyAnchor.ORB_CORE && event.source in setOf(EnergyAnchor.FISH_CONTOUR, EnergyAnchor.PHOTO_PERIMETER)
            EnergyFeedback.TRANSLATE -> Unit
            EnergyFeedback.REVEAL -> Unit
            EnergyFeedback.SETTLE -> Unit // Optical completion requires a Canvas render receipt.
        }
        bumpEventSequence()
        return EnergyEventAcceptanceResult(true)
    }

    fun updateModelJoin(
        detectorReady: Boolean,
        classifierReady: Boolean,
        slimSamTerminal: Boolean,
        maskTrusted: Boolean,
        contourValid: Boolean,
        contourClosed: Boolean,
        slimSamFailed: Boolean = false,
        currentIdentity: RecognitionSceneIdentity? = null,
        maskSha256: String = "",
        maskVersion: Long = -1L,
    ) {
        if (!isCurrent(currentIdentity)) return
        this.detectorReady = detectorReady
        this.classifierReady = classifierReady
        this.slimSamTerminal = slimSamTerminal
        this.slimSamFailed = slimSamFailed && slimSamTerminal
        val suppliedMaskSha = maskSha256.trim()
        val nextMaskSha = suppliedMaskSha.takeIf {
            maskTrusted && slimSamTerminal && maskVersion >= 0L &&
                it.matches(Regex("(?i)^[a-f0-9]{64}$"))
        }?.lowercase()
        val nextMaskVersion = nextMaskSha?.let { maskVersion }
        if (nextMaskSha != activeMaskSha256 || nextMaskVersion != activeMaskVersion) {
            activeMaskSha256 = nextMaskSha
            activeMaskVersion = nextMaskVersion
            invalidateMaskContactPath()
        }
        this.maskTrusted = maskTrusted && slimSamTerminal && nextMaskSha != null && nextMaskVersion != null
        this.contourValid = this.maskTrusted && contourValid
        this.contourClosed = this.maskTrusted && contourClosed
    }

    /**
     * Bind the rendered Stage-05 P* route to the exact trusted Mask and current photo-transform
     * fingerprint. The path ID must be the deterministic ID emitted by WaterLightPathLedger.
     */
    fun onFishContactPathAvailable(
        pathId: String?,
        maskSha256: String,
        maskVersion: Long,
        geometryVersion: Long,
        contactPointNormalizedSource: WaterLightPathPoint,
        currentIdentity: RecognitionSceneIdentity? = null,
    ): Boolean {
        if (!isCurrent(currentIdentity)) return false
        if (revealDecisionState in setOf(
                RecognitionRevealDecisionState.ORB_ONLY_READY,
                RecognitionRevealDecisionState.STATIC_RESULT,
                RecognitionRevealDecisionState.RECOGNITION_FAILED,
                RecognitionRevealDecisionState.CANCELLED,
            )
        ) return false
        val activeIdentity = identity ?: return false
        val suppliedMaskSha = maskSha256.trim()
        val normalizedMaskSha = suppliedMaskSha.takeIf {
            it.matches(Regex("(?i)^[a-f0-9]{64}$"))
        }?.lowercase() ?: return false
        if (!maskTrusted || activeMaskSha256 != normalizedMaskSha || activeMaskVersion != maskVersion ||
            maskVersion < 0L || geometryVersion <= 0L || !photoTransformReady ||
            activePhotoGeometryVersion != geometryVersion ||
            !contactPointNormalizedSource.isValid() ||
            contactPointNormalizedSource.x !in 0f..1f ||
            contactPointNormalizedSource.y !in 0f..1f
        ) return false
        val expectedPathId = "waterlight:${activeIdentity.sessionId}:${activeIdentity.attemptId}:${activeIdentity.imageId}:" +
            "${activeIdentity.photoContentVersion}:$geometryVersion:mask:${suppliedMaskSha.take(16)}:$maskVersion:" +
            "${String.format(java.util.Locale.US, "%.6f", contactPointNormalizedSource.x)}:" +
            String.format(java.util.Locale.US, "%.6f", contactPointNormalizedSource.y)
        if (pathId.isNullOrBlank() || pathId != expectedPathId) return false
        val existingPathId = activeFishContactPathId
        val existingPoint = activeMaskContactPointNormalizedSource
        if ((existingPathId != null && existingPathId != pathId) ||
            (existingPoint != null && existingPoint != contactPointNormalizedSource)
        ) return false
        activeMaskGeometryVersion = geometryVersion
        activeMaskContactPointNormalizedSource = contactPointNormalizedSource
        activeFishContactPathId = pathId
        return true
    }

    /** Bind the immutable RETURN route exported by the same Processing PathLedger. */
    fun onFishPathSnapshot(
        snapshot: RecognitionFishPathSnapshot,
        currentIdentity: RecognitionSceneIdentity? = null,
    ): Boolean {
        if (!isCurrent(currentIdentity)) return false
        val active = identity ?: return false
        if (!snapshot.isValid() || snapshot.sessionId != active.sessionId ||
            snapshot.attemptId != active.attemptId || snapshot.imageId != active.imageId ||
            snapshot.photoSha256 != active.photoSha256 || snapshot.photoContentVersion != active.photoContentVersion ||
            !maskTrusted || snapshot.maskSha256 != activeMaskSha256 || snapshot.maskVersion != activeMaskVersion ||
            snapshot.geometryVersion != activeMaskGeometryVersion ||
            snapshot.pStarNormalizedSource != activeMaskContactPointNormalizedSource ||
            snapshot.fishPathId != activeFishContactPathId || snapshot.fishPathId != fishContactPathId ||
            snapshot.returnPathId != fishContactPathId
        ) return false
        val current = fishPathSnapshot
        if (current != null && current != snapshot) return false
        fishPathSnapshot = snapshot
        return true
    }

    /** Samples and advances at most one event-gated stage. MANUAL never advances here. */
    fun currentSceneStage(
        nowMs: Long,
        trustedMask: Boolean = false,
        subjectTerminal: Boolean = false,
        successfulRevealAllowed: Boolean = true,
    ): RecognitionSceneStage {
        if (subjectTerminal && !slimSamTerminal) onMaskTerminal(false, contourValid = false, contourClosed = false, currentIdentity = identity)
        if (mode == ScenePlaybackMode.MANUAL) {
            updatePreviewCompletion(nowMs)
            return currentStage
        }
        if (!photoFirstFramePresented || paused || geometryGateStartedAtMs != null || failed) return currentStage
        val elapsed = stageElapsedMs(nowMs)
        val progress = stageProgress(nowMs)
        val required = if (diagnosticAuto) maxOf(DIAGNOSTIC_STEP_DURATION_MS, minimum(currentStage)) else minimum(currentStage)
        if (elapsed < required) return currentStage
        if (!canAdvance(currentStage, successfulRevealAllowed)) {
            val nextWaitReason = modelGates.waitingReason ?: "WAITING_FOR_STAGE_EVENT_OR_MODEL_GATE"
            if (stageGateWaitStartedAtMs == null || stageGateWaitReason != nextWaitReason) {
                stageGateWaitStartedAtMs = nowMs
                stageGateWaitReason = nextWaitReason
                emitStageEvent(
                    action = RecognitionSceneStageEventAction.BLOCKED,
                    stage = currentStage,
                    nowMs = nowMs,
                    progress = progress,
                    waitingReason = nextWaitReason,
                )
            }
            return currentStage
        }
        val next = next(currentStage, successfulRevealAllowed)
        if (next == currentStage) return currentStage
        val previousStage = currentStage
        bumpEventSequence()
        closeStage(
            stage = previousStage,
            endedAtMs = nowMs,
            sequence = eventSequence,
        )
        currentStage = next
        stageStartedAtMs = nowMs
        stageGateWaitStartedAtMs = null
        stageGateWaitReason = null
        lastObservedGeometryVersion = 0L
        stageElapsedBeforePauseMs = 0L
        autoElapsedBeforePauseMs = 0L
        autoStartedAtMs = nowMs.takeIf { diagnosticAuto }
        previewCompleted = false
        emitStageEvent(
            action = RecognitionSceneStageEventAction.ENTERED,
            stage = next,
            nowMs = nowMs,
            progress = 0f,
            waitingReason = null,
        )
        return currentStage
    }

    fun presentedStage(): RecognitionSceneStage = currentStage

    fun presentedPhase(): RecognitionPhase = if (failed) RecognitionPhase.FAILURE else currentStage.toRecognitionPhase()

    fun phaseElapsedMs(nowMs: Long): Long = stageElapsedMs(nowMs)

    fun stageElapsedMs(nowMs: Long): Long {
        if (geometryGateStartedAtMs != null) return geometryFrozenElapsedMs.coerceAtLeast(0L)
        if (mode == ScenePlaybackMode.MANUAL) return previewElapsedMsWithoutUpdate(nowMs)
        if (diagnosticAuto) return autoStageElapsedMs(nowMs)
        if (paused) return stageElapsedBeforePauseMs
        return if (stageStartedAtMs == UNSET) 0L else (nowMs - stageStartedAtMs).coerceAtLeast(0L)
    }

    fun stageProgress(nowMs: Long): Float {
        val duration = if (mode == ScenePlaybackMode.MANUAL || diagnosticAuto) DIAGNOSTIC_STEP_DURATION_MS else minimum(currentStage)
        if (duration <= 0L) return if (currentStage == RecognitionSceneStage.PHOTO_STABLE) 1f else 0f
        return (stageElapsedMs(nowMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    fun opticalReferenceElapsedMs(
        nowMs: Long,
        @Suppress("UNUSED_PARAMETER") trustedMask: Boolean = false,
        @Suppress("UNUSED_PARAMETER") subjectTerminal: Boolean = false,
    ): Long = sceneElapsedMs(nowMs)

    fun currentFrame(
        currentIdentity: RecognitionSceneIdentity,
        nowMs: Long,
        geometryVersion: Long,
        reducedMotion: Boolean = false,
        energyEvent: EnergyEvent? = null,
    ): RecognitionSceneFrame {
        val elapsed = stageElapsedMs(nowMs)
        val progress = stageProgress(nowMs)
        val reference = opticalReferenceElapsedMs(nowMs)
        val sceneElapsed = sceneElapsedMs(nowMs)
        val gates = modelGates
        lastObservedGeometryVersion = geometryVersion
        val resultSubstage = acceptedResultHandoffEvents.lastOrNull()?.substage
        return RecognitionSceneCoordinator.frameForStage(
            identity = currentIdentity,
            sceneElapsedMs = sceneElapsed,
            opticalReferenceElapsedMs = reference,
            geometryVersion = geometryVersion,
            stage = currentStage,
            stageElapsedMs = elapsed,
            stageProgress = progress,
            playbackMode = mode,
            paused = paused || geometryGateStartedAtMs != null,
            reducedMotion = reducedMotion,
            modelGates = gates,
            eventSequence = eventSequence,
            monotonicTimestampMs = nowMs.coerceAtLeast(0L),
            energyEvent = energyEvent,
            stageEventComplete = stageEventComplete(currentStage),
            resultHandoffSubstage = resultSubstage,
            resultInfoArrivalProgress = if (ResultHandoffSubstage.RESULT_INFO_ARRIVAL in completedResultHandoffSubstages || resultSceneStable) 1f else 0f,
            resultSceneStable = resultSceneStable,
            taskEnergyMerged = orbReturnComplete,
            orbToGlyphAccepted = orbToGlyphComplete,
        )
    }

    /** Compatibility accessor projects the selected reveal stage into its stage-local time domain. */
    fun revealElapsedMs(nowMs: Long): Long = if (currentStage.ordinal >= RecognitionSceneStage.ORB_RETURN.ordinal) {
        (minimum(currentStage) * stageProgress(nowMs)).toLong()
    } else 0L

    fun fishPathSnapshotReady(): Boolean = fishPathSnapshot != null

    /** Handoff eligibility is an exact stage-10 check plus the real model join, fish path snapshot when trusted, and original Orb anchor. */
    fun resultVisualReady(nowMs: Long): Boolean = !failed && photoFirstFramePresented &&
        currentStage == RecognitionSceneStage.ORB_RETURN && classifierReady && slimSamTerminal &&
        revealDecisionReady && orbAnchorReady &&
        (!maskTrusted || revealDecisionState == RecognitionRevealDecisionState.ORB_ONLY_READY ||
            (contourValid && fishPathSnapshot != null)) &&
        stageElapsedMs(nowMs) >= 0L

    fun sample(authoritativeReferenceMs: Long, nowMs: Long): Long {
        @Suppress("UNUSED_VARIABLE") val compatibilityReference = authoritativeReferenceMs.coerceAtLeast(0L)
        if (mode == ScenePlaybackMode.MANUAL) {
            updatePreviewCompletion(nowMs)
            return sceneElapsedMs(nowMs)
        }
        return opticalReferenceElapsedMs(nowMs)
    }

    fun prepareInitialStepPreviewForPhoto(identity: RecognitionSceneIdentity, debugBuild: Boolean, nowMs: Long) =
        beginNewSession(identity, debugBuild, nowMs)

    fun beginStepPreview(
        stage: RecognitionSceneStage,
        nowMs: Long,
        startImmediately: Boolean = true,
    ) {
        mode = ScenePlaybackMode.MANUAL
        currentStage = stage
        paused = !startImmediately
        previewCompleted = false
        previewElapsedBeforePauseMs = 0L
        previewStartedAtMs = nowMs.takeIf { startImmediately && photoFirstFramePresented }
        if (startImmediately && photoFirstFramePresented && geometryGateStartedAtMs == null) startSceneClock(nowMs)
        diagnosticAuto = false
        autoStartedAtMs = null
        autoElapsedBeforePauseMs = 0L
        if (photoFirstFramePresented && stageStartedAtMs == UNSET) stageStartedAtMs = nowMs
        bumpEventSequence()
    }

    fun advanceToNextStage(nowMs: Long): RecognitionSceneStage? {
        if (mode != ScenePlaybackMode.MANUAL || !photoFirstFramePresented ||
            currentStage == RecognitionSceneStage.entries.last()
        ) return null
        val next = RecognitionSceneStage.entries[currentStage.ordinal + 1]
        beginStepPreview(next, nowMs)
        return next
    }

    fun returnToPreviousStage(nowMs: Long): RecognitionSceneStage? {
        if (mode != ScenePlaybackMode.MANUAL || !photoFirstFramePresented ||
            currentStage == RecognitionSceneStage.entries.first()
        ) return null
        val previous = RecognitionSceneStage.entries[currentStage.ordinal - 1]
        beginStepPreview(previous, nowMs)
        return previous
    }

    fun restartCurrentStage(nowMs: Long) = beginStepPreview(currentStage, nowMs)

    fun previewElapsedMs(nowMs: Long): Long {
        updatePreviewCompletion(nowMs)
        return previewElapsedMsWithoutUpdate(nowMs)
    }

    fun previewProgress(nowMs: Long): Float =
        (previewElapsedMs(nowMs).toFloat() / DIAGNOSTIC_STEP_DURATION_MS).coerceIn(0f, 1f)

    fun previewRemainingMs(nowMs: Long): Long =
        (DIAGNOSTIC_STEP_DURATION_MS - previewElapsedMs(nowMs)).coerceAtLeast(0L)

    fun seek(stage: RecognitionSceneStage, nowMs: Long) = beginStepPreview(stage, nowMs)

    fun step(delta: Int, @Suppress("UNUSED_PARAMETER") currentStage: RecognitionSceneStage, nowMs: Long) {
        val nextIndex = (this.currentStage.ordinal + delta).coerceIn(0, RecognitionSceneStage.entries.lastIndex)
        beginStepPreview(RecognitionSceneStage.entries[nextIndex], nowMs)
    }

    fun pause(@Suppress("UNUSED_PARAMETER") authoritativeReferenceMs: Long, nowMs: Long) {
        if (paused) return
        if (mode == ScenePlaybackMode.MANUAL) {
            previewElapsedBeforePauseMs = (if (geometryGateStartedAtMs != null) {
                geometryFrozenElapsedMs
            } else {
                previewElapsedMsWithoutUpdate(nowMs)
            }).coerceIn(0L, DIAGNOSTIC_STEP_DURATION_MS)
            previewStartedAtMs = null
            previewCompleted = previewElapsedBeforePauseMs >= DIAGNOSTIC_STEP_DURATION_MS
        } else if (diagnosticAuto) {
            autoElapsedBeforePauseMs = (if (geometryGateStartedAtMs != null) {
                geometryFrozenElapsedMs
            } else {
                autoStageElapsedMs(nowMs)
            }).coerceAtMost(DIAGNOSTIC_STEP_DURATION_MS)
            autoStartedAtMs = null
        } else {
            stageElapsedBeforePauseMs = stageElapsedMs(nowMs)
            stageStartedAtMs = UNSET
        }
        paused = true
        stopSceneClock(nowMs)
    }

    fun resume(@Suppress("UNUSED_PARAMETER") authoritativeReferenceMs: Long, nowMs: Long) {
        if (!paused || previewCompleted) return
        if (mode == ScenePlaybackMode.MANUAL) previewStartedAtMs = nowMs
        else if (diagnosticAuto) autoStartedAtMs = nowMs
        else stageStartedAtMs = nowMs - stageElapsedBeforePauseMs
        paused = false
        if (geometryGateStartedAtMs == null) startSceneClock(nowMs)
    }

    fun playAuto(authoritativeReferenceMs: Long, nowMs: Long) = playProductionTimeline(authoritativeReferenceMs, nowMs)

    /** Switch to production pace while retaining the selected stage and its current visual progress. */
    fun playProductionTimeline(@Suppress("UNUSED_PARAMETER") authoritativeReferenceMs: Long, nowMs: Long) {
        val progress = stageProgress(nowMs)
        val elapsed = (minimum(currentStage) * progress).toLong()
        mode = ScenePlaybackMode.AUTO
        diagnosticAuto = false
        paused = false
        previewCompleted = false
        previewStartedAtMs = null
        previewElapsedBeforePauseMs = 0L
        stageElapsedBeforePauseMs = elapsed
        stageStartedAtMs = if (photoFirstFramePresented) nowMs - elapsed else UNSET
        if (photoFirstFramePresented) startSceneClock(nowMs)
        autoStartedAtMs = null
        autoElapsedBeforePauseMs = 0L
    }

    /** Debug AUTO uses the same stage and event gates with a 3s minimum per stage. */
    fun playDiagnosticAuto(@Suppress("UNUSED_PARAMETER") authoritativeReferenceMs: Long, nowMs: Long) {
        val elapsed = previewElapsedMsWithoutUpdate(nowMs)
        mode = ScenePlaybackMode.AUTO
        diagnosticAuto = true
        paused = false
        previewCompleted = false
        previewStartedAtMs = null
        previewElapsedBeforePauseMs = 0L
        autoElapsedBeforePauseMs = elapsed
        autoStartedAtMs = nowMs.takeIf { photoFirstFramePresented }
        if (photoFirstFramePresented) startSceneClock(nowMs)
    }

    fun switchToStepDiagnostic(@Suppress("UNUSED_PARAMETER") authoritativeReferenceMs: Long, nowMs: Long) {
        val progress = stageProgress(nowMs)
        beginStepPreview(currentStage, nowMs, startImmediately = photoFirstFramePresented)
        previewElapsedBeforePauseMs = (progress * DIAGNOSTIC_STEP_DURATION_MS).toLong()
    }

    /** Compatibility hook; Result geometry now uses setResultGeometryState so USER_PAUSE is preserved. */
    fun holdCurrentPreviewAtStart() {
        setResultGeometryState(SceneGeometryState.PENDING, lastNowMs)
    }

    fun canControl(): Boolean = photoFirstFramePresented

    fun sceneElapsedMs(nowMs: Long): Long {
        val startedAt = sceneClockStartedAtMs ?: return sceneElapsedBeforePauseMs.coerceAtLeast(0L)
        return (sceneElapsedBeforePauseMs + (nowMs - startedAt).coerceAtLeast(0L)).coerceAtLeast(0L)
    }

    private fun canAdvance(stage: RecognitionSceneStage, successfulRevealAllowed: Boolean): Boolean = when (stage) {
        RecognitionSceneStage.PHOTO_STABLE -> photoFirstFramePresented
        RecognitionSceneStage.ORB_WAKE -> orbAnchorReady
        RecognitionSceneStage.ORB_RELEASE -> releasePathReady && orbAnchorReady && photoTransformReady
        RecognitionSceneStage.PERIMETER_ARRIVAL -> detectorReady && photoTransformReady &&
            perimeterArrivalPathReady && perimeterArrivalPresented
        RecognitionSceneStage.PATH_TO_FISH -> detectorReady && photoTransformReady && classifierReady &&
            slimSamTerminal && revealDecisionReady && when (revealDecisionState) {
                RecognitionRevealDecisionState.FISH_FOCUS_READY -> maskTrusted && fishContactPathReady
                RecognitionRevealDecisionState.ORB_ONLY_READY -> true
                else -> false
            }
        RecognitionSceneStage.CONTOUR_A -> revealDecisionState == RecognitionRevealDecisionState.FISH_FOCUS_READY &&
            maskTrusted && fishContactPathReady && contourValid && contourClosed
        RecognitionSceneStage.CONTOUR_B -> revealDecisionState == RecognitionRevealDecisionState.FISH_FOCUS_READY &&
            maskTrusted && fishContactPathReady && contourValid && contourClosed
        RecognitionSceneStage.CONTOUR_C -> revealDecisionState == RecognitionRevealDecisionState.FISH_FOCUS_READY &&
            maskTrusted && fishContactPathReady && contourValid && contourClosed
        RecognitionSceneStage.FISH_GOLD_PEAK -> revealDecisionState == RecognitionRevealDecisionState.FISH_FOCUS_READY &&
            maskTrusted && fishContactPathReady && contourValid && contourClosed &&
            fishGoldComplete && classifierReady && revealDecisionReady
        RecognitionSceneStage.ORB_RETURN -> successfulRevealAllowed &&
            revealDecisionState in setOf(RecognitionRevealDecisionState.FISH_FOCUS_READY, RecognitionRevealDecisionState.ORB_ONLY_READY) &&
            orbReturnComplete && orbAnchorReady && glyphAvailable &&
            resultGeometryState == SceneGeometryState.READY
        RecognitionSceneStage.ORB_TO_NAME -> successfulRevealAllowed &&
            revealDecisionState in setOf(RecognitionRevealDecisionState.FISH_FOCUS_READY, RecognitionRevealDecisionState.ORB_ONLY_READY) &&
            orbToGlyphComplete && glyphAvailable
        RecognitionSceneStage.SPECIES_NAME_REVEAL -> successfulRevealAllowed &&
            revealDecisionState in setOf(RecognitionRevealDecisionState.FISH_FOCUS_READY, RecognitionRevealDecisionState.ORB_ONLY_READY) &&
            glyphRevealComplete && glyphAvailable &&
            ResultHandoffSubstage.NAME_STABLE in completedResultHandoffSubstages
        RecognitionSceneStage.RESIDUE_SETTLE -> successfulRevealAllowed &&
            revealDecisionState in setOf(RecognitionRevealDecisionState.FISH_FOCUS_READY, RecognitionRevealDecisionState.ORB_ONLY_READY) &&
            residueSettleComplete
    }

    private fun stageEventComplete(stage: RecognitionSceneStage): Boolean = when (stage) {
        RecognitionSceneStage.PHOTO_STABLE -> photoFirstFramePresented
        RecognitionSceneStage.ORB_WAKE -> orbAnchorReady
        RecognitionSceneStage.ORB_RELEASE -> releasePathReady
        RecognitionSceneStage.PERIMETER_ARRIVAL -> perimeterArrivalPathReady && perimeterArrivalPresented
        RecognitionSceneStage.PATH_TO_FISH -> if (revealDecisionState == RecognitionRevealDecisionState.FISH_FOCUS_READY) {
            fishContactPathReady
        } else {
            revealDecisionState == RecognitionRevealDecisionState.ORB_ONLY_READY && revealDecisionReady
        }
        RecognitionSceneStage.CONTOUR_A,
        RecognitionSceneStage.CONTOUR_B,
        RecognitionSceneStage.CONTOUR_C -> maskTrusted && fishContactPathReady && contourValid && contourClosed &&
            contourRenderReceipts[stage]?.let(RecognitionContourRenderContract::isComplete) == true
        RecognitionSceneStage.FISH_GOLD_PEAK -> fishContactPathReady && fishGoldComplete
        RecognitionSceneStage.ORB_RETURN -> orbReturnComplete
        RecognitionSceneStage.ORB_TO_NAME -> orbToGlyphComplete
        RecognitionSceneStage.SPECIES_NAME_REVEAL -> glyphRevealComplete
        RecognitionSceneStage.RESIDUE_SETTLE -> residueSettleComplete
    }

    private fun next(stage: RecognitionSceneStage, successfulRevealAllowed: Boolean): RecognitionSceneStage = when (stage) {
        RecognitionSceneStage.PHOTO_STABLE -> RecognitionSceneStage.ORB_WAKE
        RecognitionSceneStage.ORB_WAKE -> RecognitionSceneStage.ORB_RELEASE
        RecognitionSceneStage.ORB_RELEASE -> RecognitionSceneStage.PERIMETER_ARRIVAL
        RecognitionSceneStage.PERIMETER_ARRIVAL -> RecognitionSceneStage.PATH_TO_FISH
        RecognitionSceneStage.PATH_TO_FISH -> if (revealDecisionState == RecognitionRevealDecisionState.ORB_ONLY_READY) {
            RecognitionSceneStage.ORB_RETURN
        } else RecognitionSceneStage.CONTOUR_A
        RecognitionSceneStage.CONTOUR_A -> RecognitionSceneStage.CONTOUR_B
        RecognitionSceneStage.CONTOUR_B -> RecognitionSceneStage.CONTOUR_C
        RecognitionSceneStage.CONTOUR_C -> if (successfulRevealAllowed) RecognitionSceneStage.FISH_GOLD_PEAK else RecognitionSceneStage.ORB_RETURN
        RecognitionSceneStage.FISH_GOLD_PEAK -> RecognitionSceneStage.ORB_RETURN
        RecognitionSceneStage.ORB_RETURN -> RecognitionSceneStage.ORB_TO_NAME
        RecognitionSceneStage.ORB_TO_NAME -> RecognitionSceneStage.SPECIES_NAME_REVEAL
        RecognitionSceneStage.SPECIES_NAME_REVEAL -> RecognitionSceneStage.RESIDUE_SETTLE
        RecognitionSceneStage.RESIDUE_SETTLE -> RecognitionSceneStage.RESIDUE_SETTLE
    }

    private fun minimum(stage: RecognitionSceneStage): Long = minVisibleMs[stage] ?: DEFAULT_MIN_VISIBLE_MS[stage] ?: 0L

    private fun waitingReason(): String? {
        if (failed) return "MODEL_OR_ATTEMPT_FAILED"
        if (!photoFirstFramePresented) return "WAITING_FOR_PHOTO_FIRST_FRAME"
        if (geometryGateStartedAtMs != null) return "WAITING_FOR_RESULT_GEOMETRY"
        if (currentStage.ordinal >= RecognitionSceneStage.ORB_RETURN.ordinal && resultGeometryState == SceneGeometryState.INVALID) {
            return "RESULT_GEOMETRY_INVALID"
        }
        if (mode == ScenePlaybackMode.MANUAL) return when (currentStage) {
            RecognitionSceneStage.PHOTO_STABLE -> null
            RecognitionSceneStage.ORB_WAKE -> if (!orbAnchorReady) "WAITING_FOR_ORB_ANCHOR" else null
            RecognitionSceneStage.ORB_RELEASE -> when {
                !orbAnchorReady -> "WAITING_FOR_ORB_ANCHOR"
                !photoTransformReady -> "WAITING_FOR_PHOTO_TRANSFORM"
                !releasePathReady -> "WAITING_FOR_RENDERED_RELEASE_EVENT"
                else -> null
            }
            RecognitionSceneStage.PERIMETER_ARRIVAL -> when {
                !detectorReady -> "WAITING_FOR_DETECTOR_TARGET"
                !photoTransformReady -> "WAITING_FOR_PHOTO_TRANSFORM"
                !perimeterArrivalPathReady -> "WAITING_FOR_PERIMETER_ARRIVAL_PATH"
                !perimeterArrivalPresented -> "WAITING_FOR_PERIMETER_ARRIVAL_EVENT"
                else -> null
            }
            RecognitionSceneStage.PATH_TO_FISH -> when {
                !detectorReady -> "WAITING_FOR_DETECTOR_TARGET"
                !photoTransformReady -> "WAITING_FOR_PHOTO_TRANSFORM"
                !classifierReady -> "WAITING_FOR_CLASSIFIER"
                !slimSamTerminal -> "WAITING_FOR_SLIMSAM_TERMINAL"
                revealDecisionState == RecognitionRevealDecisionState.WAITING_PSTAR_PATH -> fishPathBindingStatus.name
                revealDecisionState == RecognitionRevealDecisionState.STATIC_RESULT -> "WAITING_FOR_STATIC_RESULT_NAVIGATION"
                !revealDecisionReady -> "WAITING_FOR_REVEAL_BRANCH_DECISION"
                revealDecisionState == RecognitionRevealDecisionState.FISH_FOCUS_READY && !fishContactPathReady ->
                    "WAITING_FOR_MASK_BOUND_PSTAR_ABSORB_EVENT"
                else -> null
            }
            RecognitionSceneStage.CONTOUR_A,
            RecognitionSceneStage.CONTOUR_B,
            RecognitionSceneStage.CONTOUR_C,
            RecognitionSceneStage.FISH_GOLD_PEAK -> when {
                !classifierReady -> "WAITING_FOR_CLASSIFIER"
                !slimSamTerminal -> "WAITING_FOR_SLIMSAM_TERMINAL"
                !maskTrusted -> "WAITING_FOR_TRUSTED_MASK"
                !fishContactPathReady -> "WAITING_FOR_MASK_BOUND_PSTAR_ABSORB_EVENT"
                !contourValid -> "WAITING_FOR_REAL_MASK_CONTOUR"
                !contourClosed -> "CONTOUR_NOT_CLOSED"
                !activeMaskGeometryVersion.let { it != null && it == activePhotoGeometryVersion } -> "MASK_GEOMETRY_INVALID"
                currentStage == RecognitionSceneStage.FISH_GOLD_PEAK && !fishGoldComplete -> "WAITING_FOR_FISH_GOLD_EVENT"
                else -> null
            }
            RecognitionSceneStage.ORB_RETURN -> when {
                !classifierReady || !slimSamTerminal -> "WAITING_FOR_MODEL_JOIN"
                !revealDecisionReady -> "WAITING_FOR_REVEAL_BRANCH_DECISION"
                !orbAnchorReady -> "WAITING_FOR_ORB_ANCHOR"
                maskTrusted && !contourValid -> "WAITING_FOR_TRUSTED_CONTOUR"
                !orbReturnComplete -> "WAITING_FOR_RETURN_ENERGY_EVENT"
                else -> null
            }
            RecognitionSceneStage.ORB_TO_NAME -> when {
                !orbReturnComplete -> "WAITING_FOR_ORB_ENERGY_RECEIVE_EVENT"
                !glyphAvailable -> "WAITING_FOR_REAL_CLASSIFIER_GLYPH"
                !orbToGlyphComplete -> "WAITING_FOR_ORB_TO_GLYPH_COMPLETION"
                else -> null
            }
            RecognitionSceneStage.SPECIES_NAME_REVEAL -> when {
                !glyphAvailable -> "WAITING_FOR_REAL_CLASSIFIER_GLYPH"
                !glyphRevealComplete -> "WAITING_FOR_GLYPH_REVEAL_COMPLETION"
                else -> null
            }
            RecognitionSceneStage.RESIDUE_SETTLE -> when {
                !glyphAvailable -> "WAITING_FOR_REAL_CLASSIFIER_GLYPH"
                !residueSettleComplete -> "WAITING_FOR_RESIDUE_SETTLE_COMPLETION"
                else -> null
            }
        }
        if (stageElapsedBeforePauseMs >= minimum(currentStage) || stageElapsedMs(lastNowMs) >= minimum(currentStage)) {
            if (!canAdvance(currentStage, successfulRevealAllowed = classifierReady && revealDecisionReady)) {
                return when (currentStage) {
                    RecognitionSceneStage.ORB_WAKE -> "WAITING_FOR_ORB_ANCHOR"
                    RecognitionSceneStage.ORB_RELEASE -> when {
                        !photoTransformReady -> "WAITING_FOR_PHOTO_TRANSFORM"
                        !releasePathReady -> "WAITING_FOR_RENDERED_RELEASE_EVENT"
                        else -> "WAITING_FOR_RELEASE_PATH_AND_PERIMETER_ARRIVAL"
                    }
                    RecognitionSceneStage.PERIMETER_ARRIVAL -> when {
                        !detectorReady -> "WAITING_FOR_DETECTOR_TARGET"
                        !photoTransformReady -> "WAITING_FOR_PHOTO_TRANSFORM"
                        !perimeterArrivalPathReady -> "WAITING_FOR_PERIMETER_ARRIVAL_PATH"
                        else -> "WAITING_FOR_PERIMETER_ARRIVAL_EVENT"
                    }
                    RecognitionSceneStage.PATH_TO_FISH -> when {
                        !classifierReady -> "WAITING_FOR_CLASSIFIER"
                        !slimSamTerminal -> "WAITING_FOR_SLIMSAM_TERMINAL"
                        revealDecisionState == RecognitionRevealDecisionState.WAITING_PSTAR_PATH -> "WAITING_FOR_PSTAR_PATH"
                        revealDecisionState == RecognitionRevealDecisionState.STATIC_RESULT ->
                            "WAITING_FOR_STATIC_RESULT_NAVIGATION"
                        !revealDecisionReady -> "WAITING_FOR_REVEAL_BRANCH_DECISION"
                        revealDecisionState == RecognitionRevealDecisionState.FISH_FOCUS_READY && !fishContactPathReady ->
                            "WAITING_FOR_MASK_BOUND_PSTAR_ABSORB_EVENT"
                        else -> null
                    }
                    RecognitionSceneStage.CONTOUR_A,
                    RecognitionSceneStage.CONTOUR_B,
                    RecognitionSceneStage.CONTOUR_C,
                    RecognitionSceneStage.FISH_GOLD_PEAK -> when {
                        !maskTrusted -> "WAITING_FOR_TRUSTED_MASK"
                        !contourValid -> "WAITING_FOR_REAL_MASK_CONTOUR"
                        !contourClosed -> "CONTOUR_NOT_CLOSED"
                        !fishContactPathReady -> "WAITING_FOR_MASK_BOUND_PSTAR_ABSORB_EVENT"
                        else -> "MASK_GEOMETRY_INVALID"
                    }
                    RecognitionSceneStage.ORB_RETURN -> "WAITING_FOR_RETURN_EVENT_OR_RESULT_GEOMETRY"
                    RecognitionSceneStage.ORB_TO_NAME -> "WAITING_FOR_ORB_TO_GLYPH_COMPLETION"
                    RecognitionSceneStage.SPECIES_NAME_REVEAL -> "WAITING_FOR_GLYPH_REVEAL_COMPLETION"
                    RecognitionSceneStage.RESIDUE_SETTLE -> "WAITING_FOR_RESIDUE_SETTLE_COMPLETION"
                    else -> null
                }
            }
        }
        return null
    }

    private var lastNowMs: Long = 0L

    private fun matchesActiveMaskPathId(pathId: String?, geometryVersion: Long): Boolean =
        !pathId.isNullOrBlank() &&
            pathId == activeFishContactPathId &&
            activeMaskGeometryVersion == geometryVersion &&
            activePhotoGeometryVersion == geometryVersion &&
            activeMaskContactPointNormalizedSource != null

    private fun invalidateMaskContactPath() {
        val routeWasActive = revealDecisionState == RecognitionRevealDecisionState.FISH_FOCUS_READY &&
            currentStage in setOf(
                RecognitionSceneStage.CONTOUR_A,
                RecognitionSceneStage.CONTOUR_B,
                RecognitionSceneStage.CONTOUR_C,
                RecognitionSceneStage.FISH_GOLD_PEAK,
            )
        activeMaskGeometryVersion = null
        activeMaskContactPointNormalizedSource = null
        activeFishContactPathId = null
        fishContactPathId = null
        fishPathSnapshot = null
        fishContactPathReady = false
        fishPathBindingStatus = RecognitionFishPathBindingStatus.WAITING_FOR_PHOTO_GEOMETRY
        contourRenderReceipts.clear()
        lastContourDrawPassSequence = 0L
        fishGoldComplete = false
        orbReturnComplete = false
        orbToGlyphComplete = false
        orbCoreDissolveReceipt = null
        glyphRevealComplete = false
        glyphReadiness = RecognitionGlyphReadiness()
        glyphAvailable = false
        residueSettleComplete = false
        residueRenderReceipt = null
        if (routeWasActive) onRevealDecision(RecognitionRevealDecisionState.ORB_ONLY_READY, lastNowMs, identity)
    }

    private fun isCurrent(candidate: RecognitionSceneIdentity?): Boolean =
        if (identity == null) candidate == null else candidate == identity

    private fun resetClockState(nowMs: Long) {
        currentStage = RecognitionSceneStage.PHOTO_STABLE
        paused = false
        previewCompleted = false
        previewStartedAtMs = null
        sceneElapsedBeforePauseMs = 0L
        sceneClockStartedAtMs = null
        previewElapsedBeforePauseMs = 0L
        diagnosticAuto = false
        autoElapsedBeforePauseMs = 0L
        autoStartedAtMs = null
        geometryGateStartedAtMs = null
        geometryFrozenElapsedMs = 0L
        resultGeometryVersions = emptySet()
        stageStartedAtMs = nowMs
        sceneStartedAtMs = nowMs
        stageElapsedBeforePauseMs = 0L
        eventSequence = 1L
        lastAcceptedEventSequence = 0L
        lastAcceptedEventTimestampMs = 0L
        acceptedEnergyEvents.clear()
        acceptedEnergyEventTrace.clear()
        acceptedResultHandoffEvents.clear()
        completedResultHandoffSubstages.clear()
        lastResultHandoffEventSequence = 0L
        lastResultHandoffTimestampMs = 0L
        stageTimeline.clear()
        stageGateWaitStartedAtMs = null
        stageGateWaitReason = null
        lastObservedGeometryVersion = 0L
        resultSceneStable = false
        detectorReady = false
        classifierReady = false
        slimSamTerminal = false
        slimSamFailed = false
        maskTrusted = false
        contourValid = false
        contourClosed = false
        orbAnchorReady = false
        releasePathReady = false
        perimeterArrivalPresented = false
        perimeterArrivalPathReady = false
        perimeterArrivalPathId = null
        perimeterArrivalPathGeometry = null
        perimeterArrivalGeometryVersion = null
        photoTransformReady = false
        activePhotoGeometryVersion = null
        activeMaskSha256 = null
        activeMaskVersion = null
        activeMaskGeometryVersion = null
        activeMaskContactPointNormalizedSource = null
        activeFishContactPathId = null
        fishContactPathId = null
        fishPathSnapshot = null
        revealDecisionState = RecognitionRevealDecisionState.WAITING_BOTH
        contourRenderReceipts.clear()
        lastContourDrawPassSequence = 0L
        revealDecisionReady = false
        lastRejectedEnergyEvent = null
        fishContactPathReady = false
        fishGoldComplete = false
        orbReturnComplete = false
        orbToGlyphComplete = false
        orbCoreDissolveReceipt = null
        glyphRevealComplete = false
        residueSettleComplete = false
        glyphAvailable = false
        revealDecisionReady = false
        resultGeometryState = SceneGeometryState.PENDING
        residueRenderReceipt = null
        failed = false
        lastNowMs = nowMs
    }

    private fun bumpEventSequence() {
        eventSequence += 1L
    }

    private fun closeCurrentStage(endedAtMs: Long, sequence: Long) {
        closeStage(currentStage, endedAtMs, sequence)
    }

    private fun closeStage(stage: RecognitionSceneStage, endedAtMs: Long, sequence: Long) {
        if (stageTimeline.lastOrNull()?.stage == stage) return
        val activeIdentity = identity ?: return
        val start = stageStartedAtMs.takeIf { it != UNSET } ?: endedAtMs
        val duration = (endedAtMs - start).coerceAtLeast(0L)
        val gateWait = stageGateWaitStartedAtMs?.let { (endedAtMs - it).coerceAtLeast(0L) }
            ?.coerceAtMost(duration) ?: 0L
        stageTimeline += RecognitionSceneStageTraceEntry(
            stage = stage,
            stageOrdinal = stage.ordinal,
            stageStartTimestampMs = start,
            stageEndTimestampMs = endedAtMs,
            stageDurationMs = duration,
            requiredDurationMs = minimum(stage),
            gateWaitDurationMs = gateWait,
            gateWaitReason = stageGateWaitReason,
            localProgress = 1f,
            sessionId = activeIdentity.sessionId,
            attemptId = activeIdentity.attemptId,
            eventSequence = sequence,
        )
        emitStageEvent(
            action = RecognitionSceneStageEventAction.EXITED,
            stage = stage,
            nowMs = endedAtMs,
            elapsedMs = duration,
            progress = 1f,
            waitingReason = null,
            sequence = sequence,
        )
    }

    private fun emitStageEvent(
        action: RecognitionSceneStageEventAction,
        stage: RecognitionSceneStage,
        nowMs: Long,
        progress: Float,
        waitingReason: String?,
        elapsedMs: Long = if (stage == currentStage) stageElapsedMs(nowMs) else 0L,
        sequence: Long = eventSequence,
    ) {
        val activeIdentity = identity ?: return
        stageEventObserver?.invoke(
            RecognitionSceneStageEvent(
                action = action,
                stage = stage,
                sceneElapsedMs = sceneElapsedMs(nowMs),
                stageElapsedMs = elapsedMs.coerceAtLeast(0L),
                stageProgress = progress.coerceIn(0f, 1f),
                geometryVersion = lastObservedGeometryVersion,
                playbackMode = mode,
                modelGates = modelGates,
                waitingReason = waitingReason,
                eventSequence = sequence,
                sessionId = activeIdentity.sessionId,
                attemptId = activeIdentity.attemptId,
            ),
        )
    }

    private fun stopSceneClock(nowMs: Long) {
        sceneElapsedBeforePauseMs = sceneElapsedMs(nowMs)
        sceneClockStartedAtMs = null
    }

    private fun startSceneClock(nowMs: Long) {
        if (photoFirstFramePresented && sceneClockStartedAtMs == null && geometryGateStartedAtMs == null && !paused) {
            sceneClockStartedAtMs = nowMs
        }
    }

    private fun updatePreviewCompletion(nowMs: Long) {
        lastNowMs = nowMs
        if (mode != ScenePlaybackMode.MANUAL || paused || previewCompleted || previewStartedAtMs == null || geometryGateStartedAtMs != null) return
        if (previewElapsedMsWithoutUpdate(nowMs) >= DIAGNOSTIC_STEP_DURATION_MS) {
            previewElapsedBeforePauseMs = DIAGNOSTIC_STEP_DURATION_MS
            previewStartedAtMs = null
            paused = true
            previewCompleted = true
            stopSceneClock(nowMs)
        }
    }

    private fun autoStageElapsedMs(nowMs: Long): Long {
        lastNowMs = nowMs
        if (geometryGateStartedAtMs != null || paused) return autoElapsedBeforePauseMs
        val startedAt = autoStartedAtMs ?: return autoElapsedBeforePauseMs
        return (autoElapsedBeforePauseMs + (nowMs - startedAt).coerceAtLeast(0L)).coerceAtLeast(0L)
    }

    private fun previewElapsedMsWithoutUpdate(nowMs: Long): Long {
        lastNowMs = nowMs
        if (mode != ScenePlaybackMode.MANUAL || paused || previewCompleted || geometryGateStartedAtMs != null) {
            return previewElapsedBeforePauseMs.coerceIn(0L, DIAGNOSTIC_STEP_DURATION_MS)
        }
        val startedAt = previewStartedAtMs ?: return previewElapsedBeforePauseMs.coerceIn(0L, DIAGNOSTIC_STEP_DURATION_MS)
        return (previewElapsedBeforePauseMs + (nowMs - startedAt).coerceAtLeast(0L)).coerceIn(0L, DIAGNOSTIC_STEP_DURATION_MS)
    }

    private fun RecognitionSceneStage.toRecognitionPhase(): RecognitionPhase = when (this) {
        RecognitionSceneStage.PHOTO_STABLE -> RecognitionPhase.CAPTURED
        RecognitionSceneStage.ORB_WAKE,
        RecognitionSceneStage.ORB_RELEASE,
        RecognitionSceneStage.PERIMETER_ARRIVAL,
        RecognitionSceneStage.PATH_TO_FISH -> RecognitionPhase.DETECTING
        RecognitionSceneStage.CONTOUR_A,
        RecognitionSceneStage.CONTOUR_B,
        RecognitionSceneStage.CONTOUR_C -> RecognitionPhase.OUTLINE
        RecognitionSceneStage.FISH_GOLD_PEAK,
        RecognitionSceneStage.ORB_RETURN -> RecognitionPhase.CLASSIFYING
        RecognitionSceneStage.ORB_TO_NAME,
        RecognitionSceneStage.SPECIES_NAME_REVEAL,
        RecognitionSceneStage.RESIDUE_SETTLE -> RecognitionPhase.RESULT
    }

    companion object {
        const val PHOTO_STABLE_MIN_MS = 0L
        const val ORB_WAKE_MIN_MS = 350L
        const val ORB_RELEASE_MIN_MS = 600L
        const val PERIMETER_ARRIVAL_MIN_MS = 125L
        const val PATH_TO_FISH_MIN_MS = 125L
        const val CONTOUR_STAGE_MIN_MS = 450L
        const val GOLD_PEAK_HOLD_MS = 1_000L
        const val ORB_RETURN_MIN_MS = 350L
        const val ORB_TO_NAME_MIN_MS = 850L
        const val NAME_REVEAL_MIN_MS = 800L
        const val RESIDUE_SETTLE_MIN_MS = 450L
        private const val UNSET = Long.MIN_VALUE

        val DEFAULT_MIN_VISIBLE_MS = mapOf(
            RecognitionSceneStage.PHOTO_STABLE to PHOTO_STABLE_MIN_MS,
            RecognitionSceneStage.ORB_WAKE to ORB_WAKE_MIN_MS,
            RecognitionSceneStage.ORB_RELEASE to ORB_RELEASE_MIN_MS,
            RecognitionSceneStage.PERIMETER_ARRIVAL to PERIMETER_ARRIVAL_MIN_MS,
            RecognitionSceneStage.PATH_TO_FISH to PATH_TO_FISH_MIN_MS,
            RecognitionSceneStage.CONTOUR_A to CONTOUR_STAGE_MIN_MS,
            RecognitionSceneStage.CONTOUR_B to CONTOUR_STAGE_MIN_MS,
            RecognitionSceneStage.CONTOUR_C to CONTOUR_STAGE_MIN_MS,
            RecognitionSceneStage.FISH_GOLD_PEAK to GOLD_PEAK_HOLD_MS,
            RecognitionSceneStage.ORB_RETURN to ORB_RETURN_MIN_MS,
            RecognitionSceneStage.ORB_TO_NAME to ORB_TO_NAME_MIN_MS,
            RecognitionSceneStage.SPECIES_NAME_REVEAL to NAME_REVEAL_MIN_MS,
            RecognitionSceneStage.RESIDUE_SETTLE to RESIDUE_SETTLE_MIN_MS,
        )
    }
}

private fun EnergyEvent.hasRenderablePath(): Boolean =
    (pathGeometry?.isValid() == true || pathSegments.any(WaterLightPathGeometry::isValid)) && level.isFinite() && level >= 0f
