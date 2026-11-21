package com.yujian.ai.ui.screens

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.dp
import com.yujian.ai.ai.FishInputStatus
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.model.RecognitionPrediction
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.identify.RecognitionUiState
import com.yujian.ai.ui.imagery.FishBoxCoordinateSpace
import com.yujian.ai.ui.imagery.FishPhotoBoundingBox
import com.yujian.ai.ui.recognition.diagnostics.RecognitionSessionEvidenceRecorder
import com.yujian.ai.ui.recognition.diagnostics.recognitionEvidenceWindow
import com.yujian.ai.ui.recognition.reveal.OpticalResidueStatus
import com.yujian.ai.ui.recognition.reveal.RecognitionGeometryTrustState
import com.yujian.ai.ui.recognition.reveal.RecognitionGlyphReadiness
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealClock
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealDecisionState
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealEligibility
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealFrame
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealGeometry
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealGeometryResolution
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealGeometryResolver
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealHandoff
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealHeroSnapshot
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealKey
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealMotionPolicy
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealPhotoGeometry
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealPolicy
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealWindowRect
import com.yujian.ai.ui.recognition.reveal.RecognitionResultHandoffContract
import com.yujian.ai.ui.recognition.reveal.RecognitionResultHandoffEvent
import com.yujian.ai.ui.recognition.reveal.RECOGNITION_RESULT_HANDOFF_EVENT_CONSUMER
import com.yujian.ai.ui.recognition.reveal.RECOGNITION_RESULT_HANDOFF_EVENT_PRODUCER
import com.yujian.ai.ui.recognition.reveal.RecognitionResultHandoffReceipt
import com.yujian.ai.ui.recognition.reveal.RecognitionResultInfoRenderReceipt
import com.yujian.ai.ui.recognition.reveal.RecognitionResultStableRenderReceipt
import com.yujian.ai.ui.recognition.reveal.RecognitionResultStableRenderContract
import com.yujian.ai.ui.recognition.reveal.ResultHandoffSubstage
import com.yujian.ai.ui.recognition.reveal.ResultSceneStableStatus
import com.yujian.ai.ui.recognition.reveal.ResultStableReadiness
import com.yujian.ai.ui.waterlight.core.DIAGNOSTIC_STEP_DURATION_MS
import com.yujian.ai.ui.waterlight.core.RecognitionProgressDisplayMode
import com.yujian.ai.ui.waterlight.core.RecognitionSceneCoordinator
import com.yujian.ai.ui.waterlight.core.RecognitionSceneFrame
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.SceneGeometryState
import com.yujian.ai.ui.waterlight.core.SceneMotionCoordinator
import com.yujian.ai.ui.waterlight.core.ScenePlaybackMode
import com.yujian.ai.ui.waterlight.core.WaterLightGeometryVersion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive


data class RecognitionResultRevealCoordinatorInput(
    val image: SelectedImage?,
    val prediction: RecognitionPrediction,
    val productionResult: ProductionRecognitionResult?,
    val uiState: RecognitionUiState,
    val revealHandoff: RecognitionRevealHandoff?,
    val resultSnapshot: com.yujian.ai.ui.recognition.reveal.RecognitionResultSnapshot?,
    val initialResultHandoffEventSequence: Long,
    val initialResultHandoffEventTimestampMs: Long,
    val onResultHandoffEvent: (RecognitionResultHandoffEvent) -> RecognitionResultHandoffReceipt,
    val evidenceRecorder: RecognitionSessionEvidenceRecorder?,
    val sceneFrameOverrideForTest: RecognitionSceneFrame?,
    val revealAlreadyPlayed: Boolean,
    val onClaimReveal: (RecognitionRevealKey) -> Boolean,
    val onRevealFinished: (RecognitionRevealKey) -> Unit,
    val diagnosticPlaybackController: SceneMotionCoordinator?,
    val diagnosticDisplayMode: RecognitionProgressDisplayMode,
    val onDiagnosticDisplayModeChange: (RecognitionProgressDisplayMode) -> Unit,
    val revealMotionPolicy: RecognitionRevealMotionPolicy,
    val revealGeometryResolutionObserver: ((RecognitionRevealGeometryResolution?) -> Unit)?,
    val onPlaybackStart: (RecognitionRevealKey) -> Boolean,
    val onPlaybackProgress: (RecognitionRevealKey, RecognitionSceneStage, Float) -> Unit,
    val onPlaybackInterrupted: (RecognitionRevealKey) -> Unit,
    val rootBounds: RecognitionRevealWindowRect?,
    val heroSnapshot: RecognitionRevealHeroSnapshot?,
    val revealTitleBounds: RecognitionRevealWindowRect?,
    val resultInfoLayoutReady: Boolean,
    val resultInfoEntranceStartedAtUptimeMs: Long?,
    val resultInfoRenderReceipt: RecognitionResultInfoRenderReceipt?,
    val resultStableRenderReceipt: RecognitionResultStableRenderReceipt?,
    val resultFirstPresentedAtMs: Long?,
    val revealStartAllowed: Boolean,
    val onRevealStartAllowed: (Boolean) -> Unit,
    val diagnosticNowMs: Long,
    val glyphReadinessState: RecognitionGlyphReadiness,
    val selectedSpeciesKey: String,
    val selectedSpeciesName: String,
    val onGlyphReadinessChanged: (RecognitionGlyphReadiness) -> Unit,
    val onDiagnosticNowMs: (Long) -> Unit,
    val onResultInfoEntranceStarted: (Long) -> Unit,
)

data class RecognitionResultRevealCoordinatorState(
    val bbox: FishPhotoBoundingBox?,
    val primaryFishAmbiguous: Boolean,
    val decisionHandoff: RecognitionRevealHandoff?,
    val photoTransitionGeometry: RecognitionRevealPhotoGeometry?,
    val revealGeometryResolution: RecognitionRevealGeometryResolution?,
    val revealGeometry: RecognitionRevealGeometry?,
    val visualHandoff: RecognitionRevealHandoff?,
    val revealPhotoIdentityMatches: Boolean,
    val revealStartReady: Boolean,
    val resultGeometryReady: Boolean,
    val diagnosticUiAvailable: Boolean,
    val diagnosticManualActive: Boolean,
    val resultSceneIdentity: RecognitionSceneIdentity,
    val resultSceneStage: RecognitionSceneStage,
    val resultHostBounds: RecognitionRevealWindowRect?,
    val resultGeometryVersion: Long,
    val resultGeometryVersions: Set<Long>,
    val resultHandoffEventSequence: Long,
    val glyphReadinessState: RecognitionGlyphReadiness,
    val diagnosticReferenceMs: Long?,
    val diagnosticPreviewElapsedMs: Long,
    val diagnosticPreviewCompleted: Boolean,
    val diagnosticPaused: Boolean,
    val diagnosticFrame: RecognitionSceneFrame?,
    val latestEvidenceResultFrame: State<RecognitionSceneFrame?>,
    val diagnosticStage: RecognitionSceneStage,
    val geometryResolutionReason: String?,
    val geometryInvalid: Boolean,
    val resultHandoffMotionActive: Boolean,
    val resultSceneHandoffAccepted: Boolean,
    val resultInteractionsEnabled: Boolean,
    val resultContentAlpha: Float,
    val resultInfoArrivalPermitted: Boolean,
    val resultInfoEntranceProgress: Float,
    val resultInfoAlpha: Float,
    val resultInfoTranslationY: androidx.compose.ui.unit.Dp,
    val revealPresentationReady: Boolean,
    val revealPending: Boolean,
    val presentedHandoff: RecognitionRevealHandoff?,
    val frameMotionPolicy: RecognitionRevealMotionPolicy,
    val activeRevealSceneFrame: RecognitionSceneFrame,
    val revealFrameState: State<RecognitionRevealFrame>,
    val photoTransformAtTarget: Boolean,
    val opticalResidueStatus: OpticalResidueStatus?,
    val resultSceneStableStatus: ResultSceneStableStatus?,
)

@Composable
fun RecognitionResultRevealCoordinator(
    input: RecognitionResultRevealCoordinatorInput,
): RecognitionResultRevealCoordinatorState {
    val image = input.image
    val prediction = input.prediction
    val productionResult = input.productionResult
    val uiState = input.uiState
    val revealHandoff = input.revealHandoff
    val resultSnapshot = input.resultSnapshot
    val initialResultHandoffEventSequence = input.initialResultHandoffEventSequence
    val initialResultHandoffEventTimestampMs = input.initialResultHandoffEventTimestampMs
    val onResultHandoffEvent = input.onResultHandoffEvent
    val recognitionEvidenceRecorder = input.evidenceRecorder
    val sceneFrameOverrideForTest = input.sceneFrameOverrideForTest
    val revealAlreadyPlayed = input.revealAlreadyPlayed
    val onClaimReveal = input.onClaimReveal
    val onRevealFinished = input.onRevealFinished
    val diagnosticPlaybackController = input.diagnosticPlaybackController
    val diagnosticDisplayMode = input.diagnosticDisplayMode
    val onDiagnosticDisplayModeChange = input.onDiagnosticDisplayModeChange
    val revealMotionPolicy = input.revealMotionPolicy
    val revealGeometryResolutionObserver = input.revealGeometryResolutionObserver
    val onPlaybackStart = input.onPlaybackStart
    val onPlaybackProgress = input.onPlaybackProgress
    val onPlaybackInterrupted = input.onPlaybackInterrupted
    val rootBounds = input.rootBounds
    val heroSnapshot = input.heroSnapshot
    val revealTitleBounds = input.revealTitleBounds
    val resultInfoLayoutReady = input.resultInfoLayoutReady
    val resultInfoEntranceStartedAtUptimeMs = input.resultInfoEntranceStartedAtUptimeMs
    val resultInfoRenderReceipt = input.resultInfoRenderReceipt
    val resultStableRenderReceipt = input.resultStableRenderReceipt
    val resultFirstPresentedAtMs = input.resultFirstPresentedAtMs
    val revealStartAllowed = input.revealStartAllowed
    val diagnosticNowMs = input.diagnosticNowMs
    val glyphReadinessState = input.glyphReadinessState
    val selectedKey = input.selectedSpeciesKey
    val selectedName = input.selectedSpeciesName
    val onResultInfoEntranceStarted = input.onResultInfoEntranceStarted

    val resultAssessment = productionResult?.assessment
    val bbox = resultAssessment?.primary?.let { detection ->
        FishPhotoBoundingBox(
            box = detection.box,
            confidence = detection.confidence,
            coordinateSpace = FishBoxCoordinateSpace.DISPLAY_ORIENTED,
            version = "${productionResult?.detectorRun?.modelVersion.orEmpty()}-${resultAssessment.status.wireName}",
        )
    }
    val primaryFishAmbiguous = resultAssessment?.let {
        it.status == FishInputStatus.MULTIPLE_FISH || it.strongDetections.size > 1
    } == true
    val decisionHandoff = when (revealHandoff?.revealDecision) {
        RecognitionRevealDecisionState.FISH_FOCUS_READY -> revealHandoff
        RecognitionRevealDecisionState.ORB_ONLY_READY -> revealHandoff?.let { handoff ->
            if (handoff.eligibility == RecognitionRevealEligibility.FULL_REVEAL) {
                handoff.copy(eligibility = RecognitionRevealEligibility.TITLE_FADE_ONLY)
            } else handoff
        }
        null -> revealHandoff
        else -> null
    }

    val photoTransitionGeometry = remember(decisionHandoff, heroSnapshot, rootBounds) {
        if (decisionHandoff == null || heroSnapshot == null || rootBounds == null) null
        else RecognitionRevealGeometryResolver.resolvePhoto(
            handoff = decisionHandoff,
            hero = requireNotNull(heroSnapshot),
            rootBoundsInWindow = requireNotNull(rootBounds),
        )
    }
    val revealGeometryResolution = remember(decisionHandoff, heroSnapshot, revealTitleBounds, rootBounds) {
        if (decisionHandoff == null) null
        else when {
            rootBounds == null -> RecognitionRevealGeometryResolution(null, "LAYOUT_ROOT_PENDING")
            heroSnapshot == null -> RecognitionRevealGeometryResolution(null, "LAYOUT_HERO_PENDING")
            revealTitleBounds == null -> RecognitionRevealGeometryResolution(null, "LAYOUT_TITLE_PENDING")
            else -> {
                val root = requireNotNull(rootBounds)
                val hero = requireNotNull(heroSnapshot)
                val title = requireNotNull(revealTitleBounds)
                val primary = RecognitionRevealGeometryResolver.resolveDetailed(decisionHandoff, hero, title, root)
                val hasTrustedContour = primary.geometry?.sourceContourPath?.isEmpty() == false
                if (decisionHandoff.eligibility == RecognitionRevealEligibility.FULL_REVEAL && !hasTrustedContour) {
                    val degraded = RecognitionRevealGeometryResolver.resolveDetailed(
                        handoff = decisionHandoff.copy(eligibility = RecognitionRevealEligibility.TITLE_FADE_ONLY),
                        hero = hero,
                        titleBoundsInWindow = title,
                        rootBoundsInWindow = root,
                    )
                    if (degraded.geometry != null) degraded.copy(
                        failureReason = "FULL_REVEAL_DEGRADED:${primary.failureReason ?: "TRUSTED_CONTOUR_EMPTY"}",
                    ) else primary.copy(
                        failureReason = "FULL_REVEAL_DEGRADED:${primary.failureReason ?: "TRUSTED_CONTOUR_EMPTY"};" +
                            "TITLE_ONLY:${degraded.failureReason}",
                    )
                } else primary
            }
        }
    }
    val revealGeometry = revealGeometryResolution?.geometry
    SideEffect {
        if (revealHandoff != null) {
            revealGeometryResolutionObserver?.invoke(revealGeometryResolution)
        }
    }
    val visualHandoff = decisionHandoff
    val revealPhotoIdentityMatches = revealHandoff != null && image != null &&
        image.imageId == revealHandoff.imageId &&
        image.bitmap.width == revealHandoff.normalizedSourceWidth &&
        image.bitmap.height == revealHandoff.normalizedSourceHeight &&
        image.photoSha256.isNotBlank() &&
        revealHandoff.photoSourceIdentity.endsWith("|sha256=${image.photoSha256}") &&
        prediction.top1.speciesKey == revealHandoff.resultIdentity
    val revealStartReady = RecognitionRevealPolicy.canStartOnFirstResultFrame(
        eligibility = visualHandoff?.eligibility ?: RecognitionRevealEligibility.STATIC_RESULT,
        photoIdentityMatches = revealPhotoIdentityMatches,
        photoGeometryReady = photoTransitionGeometry != null,
        heroReady = heroSnapshot?.renderReady == true,
        titleBoundsReady = revealTitleBounds?.isValid() == true,
        revealGeometryReady = revealGeometry != null,
        reduceMotion = revealMotionPolicy.reduceMotion,
    )
    val resultGeometryReady = revealPhotoIdentityMatches && photoTransitionGeometry != null &&
        heroSnapshot?.renderReady == true && revealTitleBounds?.isValid() == true && revealGeometry != null
    val diagnosticUiAvailable = false
    val diagnosticManualActive = diagnosticUiAvailable &&
        diagnosticPlaybackController?.mode == ScenePlaybackMode.MANUAL
    val resultSceneIdentity = remember(visualHandoff?.key, image?.imageId, image?.bitmap?.generationId) {
        RecognitionSceneIdentity(
            sessionId = visualHandoff?.key?.recognitionSessionId.orEmpty(),
            attemptId = visualHandoff?.key?.recognitionAttemptId.orEmpty(),
            imageId = image?.imageId.orEmpty(),
            photoSha256 = image?.photoSha256,
            photoContentVersion = image?.bitmap?.generationId?.toLong()?.coerceAtLeast(0L) ?: 0L,
        )
    }
    val resultSceneStage = sceneFrameOverrideForTest?.stage
        ?: diagnosticPlaybackController?.currentStage
        ?: RecognitionSceneStage.PHOTO_STABLE
    val resultHostBounds = if (resultSceneStage == RecognitionSceneStage.RESIDUE_SETTLE) {
        photoTransitionGeometry?.heroPhotoClip
    } else photoTransitionGeometry?.processingPhotoClip
    val resultGeometryVersion = photoTransitionGeometry?.heroSourceTransform?.let { transform ->
        val base = WaterLightGeometryVersion.of(transform)
        resultHostBounds?.let { WaterLightGeometryVersion.withHostBounds(base, it.left, it.top, it.right, it.bottom) } ?: base
    } ?: 0L
    val resultGeometryVersions = remember(photoTransitionGeometry) {
        val photoGeometry = photoTransitionGeometry
        val base = photoGeometry?.heroSourceTransform?.let(WaterLightGeometryVersion::of)
        if (photoGeometry == null || base == null) emptySet() else buildSet {
            photoGeometry.processingPhotoClip.let { bounds ->
                add(WaterLightGeometryVersion.withHostBounds(base, bounds.left, bounds.top, bounds.right, bounds.bottom))
            }
            photoGeometry.heroPhotoClip.let { bounds ->
                add(WaterLightGeometryVersion.withHostBounds(base, bounds.left, bounds.top, bounds.right, bounds.bottom))
            }
        }
    }
    var resultHandoffEventSequence by remember(revealHandoff?.key) {
        mutableLongStateOf(initialResultHandoffEventSequence)
    }
    var resultHandoffEventTimestampMs by remember(revealHandoff?.key) {
        mutableLongStateOf(initialResultHandoffEventTimestampMs)
    }
    val latestResultHandoffEvent = rememberUpdatedState(onResultHandoffEvent)
    val latestResultHandoffFinished = rememberUpdatedState(onRevealFinished)
    val latestPlaybackProgress = rememberUpdatedState(onPlaybackProgress)
    fun emitResultHandoffMilestone(
        substage: ResultHandoffSubstage,
        frame: RecognitionSceneFrame,
        progress: Float,
        infoRenderReceipt: RecognitionResultInfoRenderReceipt? = null,
        stableRenderReceipt: RecognitionResultStableRenderReceipt? = null,
    ): RecognitionResultHandoffReceipt {
        val handoff = visualHandoff ?: return RecognitionResultHandoffReceipt(false, "HANDOFF_MISSING")
        val path = handoff.fishPathSnapshot
        val eventSequence = resultHandoffEventSequence + 1L
        val eventTimestampMs = maxOf(SystemClock.uptimeMillis(), resultHandoffEventTimestampMs + 1L)
        val sourceTargetFeedback = when (substage) {
            ResultHandoffSubstage.NAME_STABLE -> Triple("GLYPH", "RESULT_NAME", "NAME_STABLE")
            ResultHandoffSubstage.PHOTO_SETTLE -> Triple("PROCESSING_PHOTO", "RESULT_HERO", "PHOTO_SETTLE")
            ResultHandoffSubstage.RESULT_SCENE_HANDOFF -> Triple("PHOTO_SETTLED_RESULT", "RESULT_SCENE", "RESULT_CONTENT_COMMITTED")
            ResultHandoffSubstage.RESULT_INFO_ARRIVAL -> Triple("RESULT_SNAPSHOT", "RESULT_INFO_LAYOUT", "INFO_ARRIVAL")
            ResultHandoffSubstage.RESULT_SCENE_STABLE -> Triple("RESULT_HOST", "RESULT_SCENE", "SCENE_STABLE")
        }
        val eventGlyphReadiness = if (substage == ResultHandoffSubstage.NAME_STABLE) {
            glyphReadinessState.copy(nameStable = true)
        } else glyphReadinessState
        val event = RecognitionResultHandoffEvent(
            identity = resultSceneIdentity,
            maskSha256 = path?.maskSha256,
            maskVersion = path?.maskVersion,
            geometryVersion = frame.geometryVersion,
            pathId = path?.returnPathId,
            pathGeometryVersion = path?.geometryVersion,
            pStarNormalizedSource = path?.pStarNormalizedSource,
            pathSegments = path?.returnPathSegments.orEmpty(),
            stage = frame.stage,
            substage = substage,
            eventSequence = eventSequence,
            monotonicTimestampMs = eventTimestampMs,
            eventProducer = RECOGNITION_RESULT_HANDOFF_EVENT_PRODUCER,
            eventConsumer = RECOGNITION_RESULT_HANDOFF_EVENT_CONSUMER,
            source = sourceTargetFeedback.first,
            target = sourceTargetFeedback.second,
            feedback = sourceTargetFeedback.third,
            progress = progress.coerceIn(0f, 1f),
            renderStatus = "READY",
            resultIdentity = resultSnapshot?.glyphSpeciesKey ?: prediction.top1.speciesKey,
            glyphReadiness = eventGlyphReadiness,
            resultInfoRenderReceipt = infoRenderReceipt,
            resultStableRenderReceipt = stableRenderReceipt,
        )
        resultHandoffEventTimestampMs = eventTimestampMs
        if (resultGeometryReady) {
            diagnosticPlaybackController?.setResultGeometryState(
                SceneGeometryState.READY,
                event.monotonicTimestampMs,
                event.identity,
                resultGeometryVersions,
            )
        }
        val receipt = latestResultHandoffEvent.value(event)
        if (receipt.accepted) {
            resultHandoffEventSequence = eventSequence
            if (substage == ResultHandoffSubstage.NAME_STABLE) input.onGlyphReadinessChanged(eventGlyphReadiness)
            if (substage == ResultHandoffSubstage.RESULT_INFO_ARRIVAL) {
                recognitionEvidenceRecorder?.recordResultInfoEntranceCheckpoint(
                    checkpoint = "RESULT_INFO_ARRIVAL_ACCEPTED",
                    identity = resultSceneIdentity,
                    resultIdentity = event.resultIdentity,
                    stage = frame.stage,
                    stageProgress = frame.stageProgress,
                    entranceProgress = infoRenderReceipt?.renderedProgress ?: progress,
                    geometryVersion = event.geometryVersion,
                    drawPassSequence = infoRenderReceipt?.drawPassSequence,
                    renderStatus = "ACCEPTED",
                )
            }
            Log.i(
                "RecognitionResultHandoff",
                "accepted=true substage=${substage.name} session=${event.identity.sessionId} attempt=${event.identity.attemptId} " +
                    "image=${event.identity.imageId} mask=${event.maskSha256} maskVersion=${event.maskVersion} " +
                    "path=${event.pathId} pathGeometry=${event.pathGeometryVersion} pStar=${event.pStarNormalizedSource} " +
                    "geometry=${event.geometryVersion} sequence=${event.eventSequence} " +
                    "timestamp=${event.monotonicTimestampMs}",
            )
            if (receipt.resultSceneStable) latestResultHandoffFinished.value(handoff.key)
        } else {
            Log.i(
                "RecognitionResultHandoff",
                "accepted=false substage=${substage.name} reason=${receipt.rejectionReason} " +
                    "sequence=${event.eventSequence} attempt=${event.identity.attemptId}",
            )
        }
        return receipt
    }
    val diagnosticReferenceMs = diagnosticPlaybackController?.opticalReferenceElapsedMs(diagnosticNowMs)
    val diagnosticPreviewElapsedMs = diagnosticPlaybackController?.previewElapsedMs(diagnosticNowMs) ?: 0L
    val diagnosticPreviewCompleted = diagnosticPlaybackController?.previewCompleted == true
    val diagnosticPaused = diagnosticPlaybackController?.paused == true
    LaunchedEffect(diagnosticPlaybackController, revealHandoff?.key, uiState) {
        val playback = diagnosticPlaybackController ?: return@LaunchedEffect
        if (revealHandoff == null) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { it }
            val now = SystemClock.uptimeMillis()
            playback.currentSceneStage(
                nowMs = now,
                trustedMask = revealHandoff.geometryTrustState == RecognitionGeometryTrustState.TRUSTED_MASK,
                subjectTerminal = revealHandoff.geometryTrustState != RecognitionGeometryTrustState.MISSING,
                successfulRevealAllowed = uiState == RecognitionUiState.RESULT_HIGH,
            )
            input.onDiagnosticNowMs(now)
        }
    }
    val diagnosticFrame = if (diagnosticPlaybackController != null && revealHandoff != null && image != null) {
        diagnosticPlaybackController.currentFrame(resultSceneIdentity, diagnosticNowMs, resultGeometryVersion, revealMotionPolicy.reduceMotion)
    } else null
    val latestEvidenceResultFrame = rememberUpdatedState(diagnosticFrame ?: sceneFrameOverrideForTest)
    val diagnosticStage = diagnosticFrame?.stage ?: diagnosticPlaybackController?.currentStage ?: RecognitionSceneStage.ORB_RETURN
    LaunchedEffect(revealHandoff?.key, diagnosticStage, diagnosticDisplayMode, diagnosticPreviewCompleted, diagnosticUiAvailable) {
        if (diagnosticUiAvailable && diagnosticFrame != null) {
            Log.i(
                "RecognitionReveal",
                "DIAGNOSTIC_PRESENT session=${revealHandoff?.key?.recognitionSessionId.orEmpty()} " +
                    "attempt=${revealHandoff?.key?.recognitionAttemptId.orEmpty()} image=${image?.imageId.orEmpty()} " +
                    "sha256=${image?.photoSha256.orEmpty()} stage=${diagnosticStage.ordinal + 1}/13 name=${diagnosticStage.name} " +
                    "reference_ms=${diagnosticFrame.opticalReferenceElapsedMs} preview_ms=$diagnosticPreviewElapsedMs " +
                    "remaining_ms=${DIAGNOSTIC_STEP_DURATION_MS - diagnosticPreviewElapsedMs} " +
                    "completed=$diagnosticPreviewCompleted paused=$diagnosticPaused " +
                    "geometry_ready=$revealStartReady eligibility=${revealHandoff?.eligibility} " +
                    "mask=${revealHandoff?.geometryTrustState} result=${prediction.top1.speciesKey}",
            )
        }
    }
    fun changeDiagnosticDisplayMode(mode: RecognitionProgressDisplayMode) {
        if (mode == diagnosticDisplayMode) return
        // Switching the two display modes must not seek or restart the shared scene clock.
        onDiagnosticDisplayModeChange(mode)
    }
    var geometryWaitExpired by remember(revealHandoff?.key) { mutableStateOf(false) }
    LaunchedEffect(revealHandoff?.key, resultGeometryReady, visualHandoff?.eligibility) {
        if (visualHandoff?.eligibility in setOf(RecognitionRevealEligibility.FULL_REVEAL, RecognitionRevealEligibility.TITLE_FADE_ONLY) &&
            !resultGeometryReady
        ) {
            delay(350L)
            geometryWaitExpired = true
        }
    }
    val geometryResolutionReason = revealGeometryResolution?.failureReason ?: when {
        !revealPhotoIdentityMatches && revealHandoff != null && image != null -> "PHOTO_IDENTITY_MISMATCH"
        !revealPhotoIdentityMatches -> "PHOTO_IDENTITY_PENDING"
        photoTransitionGeometry == null -> "PHOTO_TRANSITION_GEOMETRY_PENDING"
        heroSnapshot?.renderReady != true -> "RESULT_HERO_PENDING"
        revealTitleBounds?.isValid() != true -> "RESULT_TITLE_BOUNDS_PENDING"
        revealGeometry == null -> "REVEAL_GEOMETRY_PENDING"
        else -> null
    }
    val geometryStillMeasuring = geometryResolutionReason in setOf(
        "LAYOUT_ROOT_PENDING", "LAYOUT_HERO_PENDING", "LAYOUT_TITLE_PENDING",
        "PHOTO_IDENTITY_PENDING", "PHOTO_TRANSITION_GEOMETRY_PENDING",
        "RESULT_HERO_PENDING", "RESULT_TITLE_BOUNDS_PENDING", "REVEAL_GEOMETRY_PENDING",
    )
    // 350ms is an advisory threshold only. Keep the shared scene clock held while
    // Compose measures; only an explicit geometry rejection enters static fallback.
    val geometryInvalid = !resultGeometryReady && geometryResolutionReason != null && !geometryStillMeasuring
    LaunchedEffect(revealHandoff?.key, geometryResolutionReason, resultGeometryReady, resultGeometryVersion) {
        if (revealHandoff != null) {
            Log.i(
                "RecognitionResultGeometry",
                "reason=${geometryResolutionReason ?: "NONE"} stage=${resultSceneStage.name} " +
                    "session=${resultSceneIdentity.sessionId} attempt=${resultSceneIdentity.attemptId} " +
                    "image=${resultSceneIdentity.imageId} photoSha=${resultSceneIdentity.photoSha256} " +
                    "maskSha=${visualHandoff?.maskSha256} maskVersion=${visualHandoff?.maskVersion} " +
                    "path=${visualHandoff?.fishPathSnapshot?.returnPathId} geometry=$resultGeometryVersion " +
                    "result=${prediction.top1.speciesKey} ready=$resultGeometryReady " +
                    "photoIdentity=$revealPhotoIdentityMatches transition=${photoTransitionGeometry != null} " +
                    "hero=${heroSnapshot?.renderReady == true} title=${revealTitleBounds?.isValid() == true} " +
                    "reveal=${revealGeometry != null}",
            )
            recognitionEvidenceRecorder?.recordResultGeometryDiagnostic(
                resultSceneIdentity, geometryResolutionReason, resultGeometryReady,
                resultGeometryVersion, visualHandoff?.fishPathSnapshot?.returnPathId,
                visualHandoff?.maskSha256, visualHandoff?.maskVersion ?: -1L,
                prediction.top1.speciesKey,
                mapOf(
                    "photoIdentityMatches" to revealPhotoIdentityMatches,
                    "transitionGeometryReady" to (photoTransitionGeometry != null),
                    "heroRenderReady" to (heroSnapshot?.renderReady == true),
                    "titleBoundsReady" to (revealTitleBounds?.isValid() == true),
                    "revealGeometryReady" to (revealGeometry != null),
                    "stage" to resultSceneStage.name,
                ),
            )
        }
    }
    val resultHandoffMotionActive = !revealMotionPolicy.reduceMotion && !revealAlreadyPlayed &&
        !geometryInvalid &&
        uiState == RecognitionUiState.RESULT_HIGH &&
        visualHandoff?.eligibility in setOf(
            RecognitionRevealEligibility.FULL_REVEAL,
            RecognitionRevealEligibility.TITLE_FADE_ONLY,
        ) && !geometryInvalid
    val resultSceneHandoffAccepted = resultSnapshot?.handoffState?.has(ResultHandoffSubstage.RESULT_SCENE_HANDOFF) == true
    val resultInteractionsEnabled = !resultHandoffMotionActive || resultSnapshot?.resultSceneStable == true
    // Keep the photo and classifier Glyph in the same visible Result host during stages 10–12.
    // Detail controls remain gated by the accepted 13.4 event.
    val resultContentAlpha = 1f
    val resultInfoArrivalPermitted = RecognitionResultHandoffContract.mayExposeResultInfo(
        resultHandoffMotionActive,
        resultSnapshot?.handoffState?.completedSubstages.orEmpty(),
    )
    val resultInfoEntranceProgress = when {
        !resultInfoArrivalPermitted -> 0f
        !resultHandoffMotionActive -> 1f
        !resultSceneHandoffAccepted || !resultInfoLayoutReady || resultInfoEntranceStartedAtUptimeMs == null -> 0f
        diagnosticFrame?.stage != RecognitionSceneStage.RESIDUE_SETTLE -> 0f
        else -> {
            val start = requireNotNull(resultInfoEntranceStartedAtUptimeMs)
            val t = (diagnosticNowMs - start).coerceAtLeast(0L).toFloat() / RESULT_INFO_ENTRANCE_DURATION_MS
            val clamped = t.coerceIn(0f, 1f)
            clamped * clamped * (3f - 2f * clamped)
        }
    }
    val resultInfoAlpha = if (resultHandoffMotionActive) resultInfoEntranceProgress else 1f
    val resultInfoTranslationY = if (resultHandoffMotionActive) 8.dp * (1f - resultInfoEntranceProgress) else 0.dp
    LaunchedEffect(revealHandoff?.key, resultGeometryReady, geometryInvalid, resultGeometryVersions) {
        val playback = diagnosticPlaybackController ?: return@LaunchedEffect
        if (revealHandoff == null) return@LaunchedEffect
        val state = when {
            resultGeometryReady -> SceneGeometryState.READY
            geometryInvalid -> SceneGeometryState.INVALID
            else -> SceneGeometryState.PENDING
        }
        playback.setResultGeometryState(
            state,
            SystemClock.uptimeMillis(),
            resultSceneIdentity,
            geometryVersions = resultGeometryVersions.takeIf { state == SceneGeometryState.READY }.orEmpty(),
        )
        input.onDiagnosticNowMs(SystemClock.uptimeMillis())
    }
    DisposableEffect(diagnosticPlaybackController, revealHandoff?.key, resultSceneIdentity) {
        onDispose {
            diagnosticPlaybackController?.setResultGeometryState(
                SceneGeometryState.PENDING,
                SystemClock.uptimeMillis(),
                resultSceneIdentity,
            )
        }
    }
    val latestRevealKey = rememberUpdatedState(revealHandoff?.key)
    val latestImageId = rememberUpdatedState(image?.imageId)
    val latestRevealFinished = rememberUpdatedState(onRevealFinished)
    var revealClaimResolved by remember(revealHandoff?.key) { mutableStateOf(revealAlreadyPlayed) }
    LaunchedEffect(revealHandoff?.key, resultFirstPresentedAtMs, revealStartReady, revealAlreadyPlayed) {
        val key = revealHandoff?.key ?: return@LaunchedEffect
        if (resultFirstPresentedAtMs != null && revealStartReady && !revealAlreadyPlayed && !revealStartAllowed &&
            latestRevealKey.value == key
        ) {
            input.onRevealStartAllowed(onClaimReveal(key) && onPlaybackStart(key))
            revealClaimResolved = true
        }
    }
    val revealPresentationReady = resultFirstPresentedAtMs != null && resultGeometryReady &&
        (revealMotionPolicy.reduceMotion || revealStartAllowed || revealAlreadyPlayed)
    val revealPending = visualHandoff?.eligibility in setOf(RecognitionRevealEligibility.FULL_REVEAL, RecognitionRevealEligibility.TITLE_FADE_ONLY) &&
        uiState == RecognitionUiState.RESULT_HIGH && !revealAlreadyPlayed && !geometryInvalid &&
        !(resultFirstPresentedAtMs != null && resultGeometryReady &&
            (revealMotionPolicy.reduceMotion || revealClaimResolved))
    val presentedHandoff = visualHandoff.takeIf { revealPresentationReady || revealPending }
    val frameMotionPolicy = if (revealPending && !revealPresentationReady) revealMotionPolicy.copy(reduceMotion = false) else revealMotionPolicy
    val activeRevealSceneFrame = diagnosticFrame ?: sceneFrameOverrideForTest ?: RecognitionSceneCoordinator.frameForStage(
        identity = resultSceneIdentity,
        sceneElapsedMs = 0L,
        opticalReferenceElapsedMs = 0L,
        geometryVersion = resultGeometryVersion,
        stage = RecognitionSceneStage.PHOTO_STABLE,
        stageElapsedMs = 0L,
        stageProgress = 0f,
        playbackMode = ScenePlaybackMode.AUTO,
        reducedMotion = revealMotionPolicy.reduceMotion,
    )
    val revealFrameState = remember(presentedHandoff, frameMotionPolicy, activeRevealSceneFrame) {
        derivedStateOf {
            RecognitionRevealClock.evaluate(
                scene = activeRevealSceneFrame,
                handoff = presentedHandoff,
                policy = frameMotionPolicy,
            )
        }
    }

    val photoTransformAtTarget = remember(photoTransitionGeometry, revealFrameState.value.photoProgress) {
        val photo = photoTransitionGeometry
        val progress = revealFrameState.value.photoProgress
        val current = photo?.photoMatrix(progress)
        val target = photo?.photoMatrix(1f)
        val clip = photo?.photoClip(progress)
        val expectedClip = photo?.let {
            RecognitionRevealWindowRect(
                it.heroPhotoClip.left - it.rootWindowOrigin.x,
                it.heroPhotoClip.top - it.rootWindowOrigin.y,
                it.heroPhotoClip.right - it.rootWindowOrigin.x,
                it.heroPhotoClip.bottom - it.rootWindowOrigin.y,
            )
        }
        current != null && target != null && clip != null && expectedClip != null &&
            kotlin.math.abs(current.scale - target.scale) < 1f &&
            kotlin.math.abs(current.translationX - target.translationX) < 1f &&
            kotlin.math.abs(current.translationY - target.translationY) < 1f &&
            kotlin.math.abs(clip.left - expectedClip.left) < 1f &&
            kotlin.math.abs(clip.top - expectedClip.top) < 1f &&
            kotlin.math.abs(clip.right - expectedClip.right) < 1f &&
            kotlin.math.abs(clip.bottom - expectedClip.bottom) < 1f
    }
    RecognitionResultHandoffMilestoneEffects(
        state = RecognitionResultHandoffMilestoneState(
            handoffKey = revealHandoff?.key,
            frame = diagnosticFrame,
            resultSnapshot = resultSnapshot,
            resultFirstPresentedAtMs = resultFirstPresentedAtMs,
            resultGeometryReady = resultGeometryReady,
            photoProgress = revealFrameState.value.photoProgress,
            photoTransformAtTarget = photoTransformAtTarget,
            heroRenderReady = heroSnapshot?.renderReady,
            revealTitleReady = revealTitleBounds?.isValid() == true,
            resultInfoLayoutReady = resultInfoLayoutReady,
            resultSceneHandoffAccepted = resultSceneHandoffAccepted,
            resultInfoEntranceStartedAtUptimeMs = resultInfoEntranceStartedAtUptimeMs,
            resultInfoRenderReceipt = resultInfoRenderReceipt,
            eventSequence = resultHandoffEventSequence,
            sceneIdentity = resultSceneIdentity,
            geometryVersion = resultGeometryVersion,
            expectedSpeciesKey = prediction.top1.speciesKey,
            expectedSpeciesName = prediction.top1.speciesName,
            selectedSpeciesKey = selectedKey,
            selectedSpeciesName = selectedName,
            glyphReadiness = glyphReadinessState,
        ),
        diagnosticPlaybackController = diagnosticPlaybackController,
        evidenceRecorder = recognitionEvidenceRecorder,
        onResultInfoEntranceStarted = { startedAtMs ->
            input.onResultInfoEntranceStarted(startedAtMs)
        },
        onEmitMilestone = { substage, frame, progress, infoReceipt ->
            emitResultHandoffMilestone(
                substage = substage,
                frame = frame,
                progress = progress,
                infoRenderReceipt = infoReceipt,
            )
        },
    )
    val opticalResidueStatus = diagnosticFrame?.let { frame ->
        ResultStableReadiness.evaluateOpticalResidue(
            stage = frame.stage,
            stageEventComplete = frame.stageEventComplete,
            stageProgress = frame.stageProgress,
            afterglowAlpha = revealFrameState.value.afterglowAlpha,
        )
    }
    val resultSceneStableStatus = if (opticalResidueStatus != null) {
        ResultStableReadiness.evaluateResultSceneStable(
            opticalResidue = opticalResidueStatus,
            resultInfoLayoutReady = resultInfoLayoutReady,
            resultGeometryReady = resultGeometryReady,
            photoTransformAtTarget = photoTransformAtTarget,
            heroRenderReady = heroSnapshot?.renderReady == true,
            photoProgress = revealFrameState.value.photoProgress,
            resultInfoEntranceProgress = resultInfoEntranceProgress,
            eventSequenceSynced = resultHandoffEventSequence == (resultSnapshot?.handoffState?.lastEventSequence ?: -1L),
            completedSubstages = resultSnapshot?.handoffState?.completedSubstages ?: emptySet(),
            alreadyStable = resultSnapshot?.resultSceneStable == true,
        )
    } else null
    LaunchedEffect(
        revealHandoff?.key,
        diagnosticFrame?.stage,
        resultStableRenderReceipt,
        resultHandoffEventSequence,
        diagnosticPlaybackController?.resultSceneStable,
        resultSnapshot?.resultSceneStable,
        resultSnapshot?.handoffState?.completedSubstages,
    ) {
        val frame = diagnosticFrame ?: return@LaunchedEffect
        val receipt = resultStableRenderReceipt ?: return@LaunchedEffect
        if (frame.stage == RecognitionSceneStage.RESIDUE_SETTLE &&
            ResultHandoffSubstage.RESULT_SCENE_STABLE !in (resultSnapshot?.handoffState?.completedSubstages ?: emptySet()) &&
            RecognitionResultStableRenderContract.isComplete(
                expectedIdentity = resultSceneIdentity,
                expectedResultIdentity = prediction.top1.speciesKey,
                expectedGeometryVersion = resultGeometryVersion,
                receipt = receipt,
            )
        ) {
            emitResultHandoffMilestone(
                ResultHandoffSubstage.RESULT_SCENE_STABLE,
                frame,
                1f,
                stableRenderReceipt = receipt,
            )
        }
    }
    LaunchedEffect(
        revealHandoff?.key,
        revealStartAllowed,
        diagnosticFrame?.stage,
        diagnosticFrame?.stageProgress?.let { (it * 10f).toInt() },
    ) {
        val key = revealHandoff?.key ?: return@LaunchedEffect
        val frame = diagnosticFrame ?: return@LaunchedEffect
        if (revealStartAllowed) latestPlaybackProgress.value(key, frame.stage, frame.stageProgress)
    }
    DisposableEffect(revealHandoff?.key) {
        val key = revealHandoff?.key
        onDispose { if (key != null) onPlaybackInterrupted(key) }
    }

    return RecognitionResultRevealCoordinatorState(
        bbox = bbox,
        primaryFishAmbiguous = primaryFishAmbiguous,
        decisionHandoff = decisionHandoff,
        photoTransitionGeometry = photoTransitionGeometry,
        revealGeometryResolution = revealGeometryResolution,
        revealGeometry = revealGeometry,
        visualHandoff = visualHandoff,
        revealPhotoIdentityMatches = revealPhotoIdentityMatches,
        revealStartReady = revealStartReady,
        resultGeometryReady = resultGeometryReady,
        diagnosticUiAvailable = diagnosticUiAvailable,
        diagnosticManualActive = diagnosticManualActive,
        resultSceneIdentity = resultSceneIdentity,
        resultSceneStage = resultSceneStage,
        resultHostBounds = resultHostBounds,
        resultGeometryVersion = resultGeometryVersion,
        resultGeometryVersions = resultGeometryVersions,
        resultHandoffEventSequence = resultHandoffEventSequence,
        glyphReadinessState = glyphReadinessState,
        diagnosticReferenceMs = diagnosticReferenceMs,
        diagnosticPreviewElapsedMs = diagnosticPreviewElapsedMs,
        diagnosticPreviewCompleted = diagnosticPreviewCompleted,
        diagnosticPaused = diagnosticPaused,
        diagnosticFrame = diagnosticFrame,
        latestEvidenceResultFrame = latestEvidenceResultFrame,
        diagnosticStage = diagnosticStage,
        geometryResolutionReason = geometryResolutionReason,
        geometryInvalid = geometryInvalid,
        resultHandoffMotionActive = resultHandoffMotionActive,
        resultSceneHandoffAccepted = resultSceneHandoffAccepted,
        resultInteractionsEnabled = resultInteractionsEnabled,
        resultContentAlpha = resultContentAlpha,
        resultInfoArrivalPermitted = resultInfoArrivalPermitted,
        resultInfoEntranceProgress = resultInfoEntranceProgress,
        resultInfoAlpha = resultInfoAlpha,
        resultInfoTranslationY = resultInfoTranslationY,
        revealPresentationReady = revealPresentationReady,
        revealPending = revealPending,
        presentedHandoff = presentedHandoff,
        frameMotionPolicy = frameMotionPolicy,
        activeRevealSceneFrame = activeRevealSceneFrame,
        revealFrameState = revealFrameState,
        photoTransformAtTarget = photoTransformAtTarget,
        opticalResidueStatus = opticalResidueStatus,
        resultSceneStableStatus = resultSceneStableStatus,
    )
}
