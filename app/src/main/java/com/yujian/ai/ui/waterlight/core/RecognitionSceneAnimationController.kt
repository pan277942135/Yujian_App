package com.yujian.ai.ui.waterlight.core

import com.yujian.ai.ai.RecognitionPhase

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
    private var photoTransformReady = false
    private var activePhotoGeometryVersion: Long? = null
    private var activeMaskSha256: String? = null
    private var activeMaskVersion: Long? = null
    private var fishContactPathId: String? = null
    private var fishContactPathReady = false
    private var fishGoldComplete = false
    private var orbReturnComplete = false
    private var orbToGlyphComplete = false
    private var glyphRevealComplete = false
    private var residueSettleComplete = false
    private var resultGeometryState = SceneGeometryState.PENDING
    private var failed = false

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
                paused -> SceneRunState.PAUSED
                geometryGateStartedAtMs != null -> SceneRunState.WAITING
                !photoFirstFramePresented -> SceneRunState.WAITING
                mode == ScenePlaybackMode.AUTO && waitingReason() != null -> SceneRunState.WAITING
                else -> SceneRunState.RUNNING
            },
            waitingReason = waitingReason(),
        )

    /** Begin a fresh PhotoSession timeline. Debug starts MANUAL; Release starts event-driven AUTO. */
    fun beginNewSession(
        identity: RecognitionSceneIdentity,
        debugBuild: Boolean,
        nowMs: Long,
    ) {
        this.identity = identity
        resetClockState(nowMs)
        photoFirstFramePresented = false
        sceneStartedAtMs = UNSET
        sceneElapsedBeforePauseMs = 0L
        sceneClockStartedAtMs = null
        stageStartedAtMs = UNSET
        mode = if (debugBuild) ScenePlaybackMode.MANUAL else ScenePlaybackMode.AUTO
        if (debugBuild) prepareInitialStepPreview()
    }

    /** Compatibility reset for deterministic unit fixtures; production uses beginNewSession. */
    fun reset(nowMs: Long) {
        identity = null
        resetClockState(nowMs)
        photoFirstFramePresented = true
        sceneStartedAtMs = nowMs
        sceneClockStartedAtMs = nowMs.takeIf { !paused }
        stageStartedAtMs = nowMs
        mode = ScenePlaybackMode.AUTO
    }

    fun cancelSession() {
        identity = null
        resetClockState(0L)
        photoFirstFramePresented = false
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
        sceneStartedAtMs = nowMs
        stageStartedAtMs = nowMs
        previewStartedAtMs = nowMs.takeIf { mode == ScenePlaybackMode.MANUAL && !paused }
        if (!paused && geometryGateStartedAtMs == null) sceneClockStartedAtMs = nowMs
        bumpEventSequence()
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

    fun onPhotoTransformAvailable(ready: Boolean, currentIdentity: RecognitionSceneIdentity? = null) {
        if (isCurrent(currentIdentity)) photoTransformReady = ready
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
        maskTrusted = trusted
        this.contourValid = trusted && contourValid
        this.contourClosed = trusted && contourClosed
    }

    fun onTrustedMaskAvailable(
        @Suppress("UNUSED_PARAMETER") nowMs: Long,
        currentIdentity: RecognitionSceneIdentity? = null,
    ) = onMaskTerminal(true, contourValid = true, contourClosed = true, currentIdentity = currentIdentity)

    fun onRevealDecisionReady(currentIdentity: RecognitionSceneIdentity? = null) {
        if (isCurrent(currentIdentity)) revealDecisionReady = true
    }

    private var revealDecisionReady = false

    fun onFishGoldComplete(ready: Boolean, currentIdentity: RecognitionSceneIdentity? = null) {
        if (isCurrent(currentIdentity)) fishGoldComplete = ready
    }

    fun onGlyphAvailable(ready: Boolean, currentIdentity: RecognitionSceneIdentity? = null) {
        if (isCurrent(currentIdentity)) glyphAvailable = ready
    }

    private var glyphAvailable = false

    fun onSceneTransitionComplete(
        stage: RecognitionSceneStage,
        currentIdentity: RecognitionSceneIdentity? = null,
    ) {
        if (!isCurrent(currentIdentity)) return
        when (stage) {
            RecognitionSceneStage.ORB_TO_NAME -> orbToGlyphComplete = true
            RecognitionSceneStage.SPECIES_NAME_REVEAL -> glyphRevealComplete = true
            RecognitionSceneStage.RESIDUE_SETTLE -> residueSettleComplete = true
            else -> Unit
        }
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
    ): Boolean {
        val active = identity
        if (!frame.isIdentityValid || (active != null && frame.identity != active)) return false
        if (event.eventSequence != frame.eventSequence || event.eventSequence != eventSequence) return false
        if (event.sessionId != frame.identity.sessionId || event.attemptId != frame.identity.attemptId ||
            event.imageId != frame.identity.imageId || event.photoSha256 != frame.identity.photoSha256 ||
            event.photoContentVersion != frame.identity.photoContentVersion || event.geometryVersion != frame.geometryVersion
        ) return false
        when (event.feedback) {
            EnergyFeedback.ABSORB -> {
                if (event.stage == RecognitionSceneStage.PATH_TO_FISH) {
                    if (!maskTrusted || !matchesActiveMaskPathId(event.pathId)) return false
                    val acceptedPathId = fishContactPathId
                    if (acceptedPathId != null && acceptedPathId != event.pathId) return false
                    fishContactPathId = event.pathId
                    fishContactPathReady = true
                } else if (event.stage in setOf(
                        RecognitionSceneStage.CONTOUR_A,
                        RecognitionSceneStage.CONTOUR_B,
                        RecognitionSceneStage.CONTOUR_C,
                    ) && event.pathId != fishContactPathId
                ) {
                    return false
                }
            }
            EnergyFeedback.RETURN -> if (event.source == EnergyAnchor.FISH_CONTOUR &&
                event.pathId != fishContactPathId
            ) return false
            else -> Unit
        }
        if (event.monotonicTimestampMs < 0L || event.monotonicTimestampMs > frame.monotonicTimestampMs ||
            event.monotonicTimestampMs != frame.monotonicTimestampMs ||
            event.referenceMs != frame.opticalReferenceElapsedMs || event.pathId.isNullOrBlank() ||
            event.sourceHost != event.source.name || event.targetHost != event.target.name ||
            event.renderStatus != "READY" || !event.hasRenderablePath() || event.level < .999f ||
            event.stage != frame.stage || currentStage != frame.stage || event.eventSequence <= lastAcceptedEventSequence ||
            event.monotonicTimestampMs < lastAcceptedEventTimestampMs ||
            (frame.stage.ordinal >= RecognitionSceneStage.ORB_RETURN.ordinal &&
                (resultGeometryState != SceneGeometryState.READY || event.geometryVersion !in resultGeometryVersions))
        ) return false
        val eventKey = "${frame.identity.sessionId}|${frame.identity.attemptId}|${frame.identity.imageId}|${frame.geometryVersion}|${event.stage}|${event.feedback}|${event.pathId}"
        if (!acceptedEnergyEvents.add(eventKey)) return false
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
            EnergyFeedback.TRANSLATE -> orbToGlyphComplete = event.stage == RecognitionSceneStage.ORB_TO_NAME &&
                event.source == EnergyAnchor.ORB_CORE && event.target == EnergyAnchor.REVEAL_TEXT
            EnergyFeedback.REVEAL -> glyphRevealComplete = event.stage == RecognitionSceneStage.SPECIES_NAME_REVEAL &&
                event.target == EnergyAnchor.REVEAL_TEXT
            EnergyFeedback.SETTLE -> residueSettleComplete = event.stage == RecognitionSceneStage.RESIDUE_SETTLE &&
                event.target == EnergyAnchor.RESIDUE
        }
        bumpEventSequence()
        return true
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
        val nextMaskSha = maskSha256.takeIf {
            maskTrusted && slimSamTerminal && it.matches(Regex("(?i)^[a-f0-9]{64}$")) && maskVersion >= 0L
        }?.lowercase()
        val nextMaskVersion = nextMaskSha?.let { maskVersion }
        if (nextMaskSha != activeMaskSha256 || nextMaskVersion != activeMaskVersion) {
            activeMaskSha256 = nextMaskSha
            activeMaskVersion = nextMaskVersion
            fishContactPathId = null
            fishContactPathReady = false
        }
        this.maskTrusted = maskTrusted && slimSamTerminal && nextMaskSha != null
        this.contourValid = this.maskTrusted && contourValid
        this.contourClosed = this.maskTrusted && contourClosed
    }

    /** Samples and advances at most one event-gated stage. MANUAL never advances here. */
    fun currentSceneStage(
        nowMs: Long,
        trustedMask: Boolean = false,
        subjectTerminal: Boolean = false,
        successfulRevealAllowed: Boolean = true,
    ): RecognitionSceneStage {
        if (trustedMask && activeMaskSha256 != null && activeMaskVersion != null) {
            onMaskTerminal(true, contourValid = true, contourClosed = true, currentIdentity = identity)
        }
        if (subjectTerminal && !slimSamTerminal) onMaskTerminal(false, contourValid = false, contourClosed = false, currentIdentity = identity)
        if (mode == ScenePlaybackMode.MANUAL) {
            updatePreviewCompletion(nowMs)
            return currentStage
        }
        if (!photoFirstFramePresented || paused || geometryGateStartedAtMs != null || failed) return currentStage
        val elapsed = stageElapsedMs(nowMs)
        val required = if (diagnosticAuto) maxOf(DIAGNOSTIC_STEP_DURATION_MS, minimum(currentStage)) else minimum(currentStage)
        if (elapsed < required || !canAdvance(currentStage, successfulRevealAllowed)) return currentStage
        val next = next(currentStage, successfulRevealAllowed)
        if (next == currentStage) return currentStage
        currentStage = next
        stageStartedAtMs = nowMs
        stageElapsedBeforePauseMs = 0L
        autoElapsedBeforePauseMs = 0L
        autoStartedAtMs = nowMs.takeIf { diagnosticAuto }
        previewCompleted = false
        bumpEventSequence()
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
        )
    }

    /** Compatibility accessor projects the selected reveal stage into its stage-local time domain. */
    fun revealElapsedMs(nowMs: Long): Long = if (currentStage.ordinal >= RecognitionSceneStage.ORB_RETURN.ordinal) {
        (minimum(currentStage) * stageProgress(nowMs)).toLong()
    } else 0L

    /** Handoff eligibility is an exact stage-10 check plus the real model join and original Orb anchor. */
    fun resultVisualReady(nowMs: Long): Boolean = !failed && photoFirstFramePresented &&
        currentStage == RecognitionSceneStage.ORB_RETURN && classifierReady && slimSamTerminal &&
        revealDecisionReady && orbAnchorReady && (!maskTrusted || contourValid) && stageElapsedMs(nowMs) >= 0L

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
        if (!photoFirstFramePresented || currentStage == RecognitionSceneStage.entries.last()) return null
        val next = RecognitionSceneStage.entries[currentStage.ordinal + 1]
        beginStepPreview(next, nowMs)
        return next
    }

    fun returnToPreviousStage(nowMs: Long): RecognitionSceneStage? {
        if (!photoFirstFramePresented || currentStage == RecognitionSceneStage.entries.first()) return null
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
        RecognitionSceneStage.ORB_RELEASE -> releasePathReady && orbAnchorReady
        RecognitionSceneStage.PERIMETER_ARRIVAL -> perimeterArrivalPresented && detectorReady && photoTransformReady
        RecognitionSceneStage.PATH_TO_FISH -> detectorReady && photoTransformReady && classifierReady &&
            slimSamTerminal && revealDecisionReady && (!maskTrusted || fishContactPathReady)
        RecognitionSceneStage.CONTOUR_A -> maskTrusted && contourValid
        RecognitionSceneStage.CONTOUR_B -> maskTrusted && contourValid
        RecognitionSceneStage.CONTOUR_C -> maskTrusted && contourValid && contourClosed
        RecognitionSceneStage.FISH_GOLD_PEAK -> maskTrusted && fishGoldComplete && classifierReady && revealDecisionReady
        RecognitionSceneStage.ORB_RETURN -> successfulRevealAllowed && orbReturnComplete && orbAnchorReady && glyphAvailable &&
            resultGeometryState == SceneGeometryState.READY
        RecognitionSceneStage.ORB_TO_NAME -> successfulRevealAllowed && orbToGlyphComplete && glyphAvailable
        RecognitionSceneStage.SPECIES_NAME_REVEAL -> successfulRevealAllowed && glyphRevealComplete && glyphAvailable
        RecognitionSceneStage.RESIDUE_SETTLE -> successfulRevealAllowed && residueSettleComplete
    }

    private fun stageEventComplete(stage: RecognitionSceneStage): Boolean = when (stage) {
        RecognitionSceneStage.PHOTO_STABLE -> photoFirstFramePresented
        RecognitionSceneStage.ORB_WAKE -> orbAnchorReady
        RecognitionSceneStage.ORB_RELEASE -> releasePathReady
        RecognitionSceneStage.PERIMETER_ARRIVAL -> perimeterArrivalPresented
        RecognitionSceneStage.PATH_TO_FISH -> if (maskTrusted) fishContactPathReady else revealDecisionReady
        RecognitionSceneStage.CONTOUR_A,
        RecognitionSceneStage.CONTOUR_B -> maskTrusted && contourValid
        RecognitionSceneStage.CONTOUR_C -> maskTrusted && contourValid && contourClosed
        RecognitionSceneStage.FISH_GOLD_PEAK -> fishGoldComplete
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
        RecognitionSceneStage.PATH_TO_FISH -> if (successfulRevealAllowed && slimSamTerminal && classifierReady && revealDecisionReady && !maskTrusted) {
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
                !releasePathReady -> "WAITING_FOR_RENDERED_RELEASE_EVENT"
                else -> null
            }
            RecognitionSceneStage.PERIMETER_ARRIVAL -> when {
                !perimeterArrivalPresented -> "WAITING_FOR_PERIMETER_ARRIVAL_EVENT"
                !detectorReady -> "WAITING_FOR_DETECTOR_TARGET"
                !photoTransformReady -> "WAITING_FOR_PHOTO_TRANSFORM"
                else -> null
            }
            RecognitionSceneStage.PATH_TO_FISH -> when {
                !detectorReady -> "WAITING_FOR_DETECTOR_TARGET"
                !photoTransformReady -> "WAITING_FOR_PHOTO_TRANSFORM"
                !classifierReady -> "WAITING_FOR_CLASSIFIER"
                !slimSamTerminal -> "WAITING_FOR_SLIMSAM_TERMINAL"
                !revealDecisionReady -> "WAITING_FOR_REVEAL_BRANCH_DECISION"
                maskTrusted && !fishContactPathReady -> "WAITING_FOR_MASK_BOUND_PSTAR_ABSORB_EVENT"
                else -> null
            }
            RecognitionSceneStage.CONTOUR_A,
            RecognitionSceneStage.CONTOUR_B,
            RecognitionSceneStage.CONTOUR_C,
            RecognitionSceneStage.FISH_GOLD_PEAK -> when {
                !classifierReady -> "WAITING_FOR_CLASSIFIER"
                !slimSamTerminal -> "WAITING_FOR_SLIMSAM_TERMINAL"
                !maskTrusted -> "WAITING_FOR_TRUSTED_MASK"
                !contourValid -> "WAITING_FOR_REAL_MASK_CONTOUR"
                currentStage == RecognitionSceneStage.CONTOUR_C && !contourClosed -> "WAITING_FOR_CLOSED_CONTOUR"
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
                    RecognitionSceneStage.ORB_RELEASE -> "WAITING_FOR_RELEASE_PATH_AND_PERIMETER_ARRIVAL"
                    RecognitionSceneStage.PERIMETER_ARRIVAL -> "WAITING_FOR_DETECTOR_TARGET_AND_PHOTO_TRANSFORM"
                    RecognitionSceneStage.PATH_TO_FISH -> "WAITING_FOR_CLASSIFIER_AND_SLIMSAM_TERMINAL_JOIN"
                    RecognitionSceneStage.CONTOUR_A,
                    RecognitionSceneStage.CONTOUR_B,
                    RecognitionSceneStage.CONTOUR_C,
                    RecognitionSceneStage.FISH_GOLD_PEAK -> "WAITING_FOR_TRUSTED_MASK_AND_CONTOUR"
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

    private fun matchesActiveMaskPathId(pathId: String?): Boolean {
        val sha = activeMaskSha256 ?: return false
        val version = activeMaskVersion ?: return false
        return !pathId.isNullOrBlank() && pathId.contains(":mask:" + sha.take(16) + ":" + version + ":")
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
        photoTransformReady = false
        activePhotoGeometryVersion = null
        activeMaskSha256 = null
        activeMaskVersion = null
        fishContactPathId = null
        fishContactPathReady = false
        fishGoldComplete = false
        orbReturnComplete = false
        orbToGlyphComplete = false
        glyphRevealComplete = false
        residueSettleComplete = false
        glyphAvailable = false
        revealDecisionReady = false
        resultGeometryState = SceneGeometryState.PENDING
        failed = false
        lastNowMs = nowMs
    }

    private fun bumpEventSequence() {
        eventSequence += 1L
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
