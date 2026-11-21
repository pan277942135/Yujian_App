package com.yujian.ai.ui.screens

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.subject.FishSubjectResult
import com.yujian.ai.ai.subject.SubjectStatus
import com.yujian.ai.ui.recognition.reveal.RecognitionGeometryTrustState
import com.yujian.ai.ui.recognition.reveal.RecognitionResultSnapshot
import com.yujian.ai.ui.recognition.reveal.RecognitionGlyphPathDescriptor
import com.yujian.ai.ui.recognition.reveal.RecognitionGlyphPathInspector
import com.yujian.ai.ui.recognition.reveal.RecognitionGlyphDrawReceipt
import com.yujian.ai.ui.recognition.reveal.RecognitionGlyphReadiness
import com.yujian.ai.ui.recognition.reveal.RecognitionGlyphReadinessContract
import com.yujian.ai.ui.recognition.reveal.RecognitionResultInfoRenderReceipt
import com.yujian.ai.ui.recognition.diagnostics.RecognitionSessionEvidenceRecorder
import com.yujian.ai.model.RecognitionCandidate
import com.yujian.ai.model.RecognitionPrediction
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.components.FishIllustration
import com.yujian.ai.ui.components.FishPhotoBitmapImage
import com.yujian.ai.ui.components.RemoteImage
import com.yujian.ai.ui.designsystem.components.YuJianActionButtonVariant
import com.yujian.ai.ui.designsystem.components.YuJianPrimaryButton
import com.yujian.ai.ui.designsystem.components.YuJianTextAction
import com.yujian.ai.ui.designsystem.components.YuJianTextActionRole
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.radius.YuJianRadius
import com.yujian.ai.ui.identify.RecognitionUiState
import com.yujian.ai.ui.identify.NormalizedSourceRect
import com.yujian.ai.ui.imagery.PhotoCropRect
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealEligibility
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealDecisionState
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealFrame
import com.yujian.ai.ui.waterlight.core.UnifiedWaterLightEngine
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightPhase
import com.yujian.ai.ui.waterlight.core.WaterLightHost
import com.yujian.ai.ui.waterlight.core.WaterLightHostBinding
import com.yujian.ai.ui.waterlight.core.WaterLightEnergy
import com.yujian.ai.ui.waterlight.core.WaterLightHostGeometry
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometry
import com.yujian.ai.ui.waterlight.core.WaterLightPathPoint
import com.yujian.ai.ui.waterlight.core.WaterLightGeometryVersion
import com.yujian.ai.ui.waterlight.core.RecognitionProgressDisplayMode
import com.yujian.ai.ui.waterlight.core.RecognitionSceneFrame
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.SceneMotionCoordinator
import com.yujian.ai.ui.waterlight.core.ScenePlaybackMode
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealGeometry
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealHandoff
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealMotionPolicy
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealOpticalOverlay
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealPhotoGeometry
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealPhase
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealTitleMode
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealWindowRect
import com.yujian.ai.ui.recognition.RecognitionStepDiagnosticControls
import com.yujian.ai.ui.recognition.result.RecognitionResultGeometryResolver
import com.yujian.ai.ui.recognition.result.RecognitionResultInputValidation
import com.yujian.ai.ui.recognition.result.resultNumericEditorInitialValue
import com.yujian.ai.ui.recognition.result.resultSpeciesDisplayName
import com.yujian.ai.ui.imagery.FishPhotoBoundingBox
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.MutedInk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class RecognitionSaveDestination { HOME, MEMORY }

data class RecognitionResultOpticalLayerModel(
    val image: SelectedImage?,
    val diagnosticFrame: RecognitionSceneFrame?,
    val sceneFrameOverrideForTest: RecognitionSceneFrame?,
    val photoTransitionGeometry: RecognitionRevealPhotoGeometry?,
    val presentedHandoff: RecognitionRevealHandoff?,
    val revealFrameState: State<RecognitionRevealFrame>,
    val revealMotionPolicy: RecognitionRevealMotionPolicy,
    val resultSceneIdentity: RecognitionSceneIdentity,
    val revealGeometry: RecognitionRevealGeometry?,
    val diagnosticPlaybackController: SceneMotionCoordinator?,
    val recognitionEvidenceRecorder: RecognitionSessionEvidenceRecorder?,
    val revealPresentationReady: Boolean,
    val heroRenderReady: Boolean,
    val diagnosticUiAvailable: Boolean,
    val diagnosticStage: RecognitionSceneStage,
    val diagnosticReferenceMs: Long?,
    val diagnosticPreviewElapsedMs: Long,
    val diagnosticPreviewCompleted: Boolean,
    val diagnosticPaused: Boolean,
    val revealStartReady: Boolean,
    val diagnosticDisplayMode: RecognitionProgressDisplayMode,
    val visualHandoff: RecognitionRevealHandoff?,
    val productionResult: ProductionRecognitionResult?,
    val revealHandoff: RecognitionRevealHandoff?,
    val subjectResult: FishSubjectResult,
)

data class RecognitionResultOpticalLayerActions(
    val setDiagnosticUserPaused: (Boolean) -> Unit,
    val setDiagnosticNowMs: (Long) -> Unit,
    val onReturnToProcessing: () -> Unit,
    val onDiagnosticDisplayModeChange: (RecognitionProgressDisplayMode) -> Unit,
)

@Composable
fun RecognitionResultOpticalLayer(
    model: RecognitionResultOpticalLayerModel,
    actions: RecognitionResultOpticalLayerActions,
) {
    with(model) {
        fun changeDiagnosticDisplayMode(mode: RecognitionProgressDisplayMode) {
            if (mode == diagnosticDisplayMode) return
            // Switching display modes must not seek or restart the shared scene clock.
            actions.onDiagnosticDisplayModeChange(mode)
        }
        val resultPhoto = image?.bitmap
        val sharedResultSceneFrame = diagnosticFrame ?: sceneFrameOverrideForTest
        val hostType = if (sharedResultSceneFrame?.stage == RecognitionSceneStage.RESIDUE_SETTLE) {
            WaterLightHost.RESIDUE
        } else WaterLightHost.HANDOFF
        val hostBounds = if (hostType == WaterLightHost.RESIDUE) photoTransitionGeometry?.heroPhotoClip
            else photoTransitionGeometry?.processingPhotoClip
        val geometryVersion = photoTransitionGeometry?.heroSourceTransform?.let { transform ->
            val base = WaterLightGeometryVersion.of(transform)
            hostBounds?.let { WaterLightGeometryVersion.withHostBounds(base, it.left, it.top, it.right, it.bottom) }
                ?: base
        } ?: 0L
        val resultSceneFrame = sharedResultSceneFrame?.copy(geometryVersion = geometryVersion)
        val resultFrame = WaterLightFrame(
            sessionId = presentedHandoff?.key?.recognitionSessionId.orEmpty(),
            photoSha256 = image?.photoSha256,
            elapsedMs = resultSceneFrame?.sceneElapsedMs ?: 0L,
            opticalReferenceElapsedMs = resultSceneFrame?.opticalReferenceElapsedMs ?: 0L,
            phase = if (hostType == WaterLightHost.RESIDUE) WaterLightPhase.RESIDUE else WaterLightPhase.HANDOFF,
            completion = if (revealFrameState.value.isComplete) 1f else 0f,
            reducedMotion = revealMotionPolicy.reduceMotion,
            geometryVersion = geometryVersion,
            attemptId = presentedHandoff?.key?.recognitionAttemptId.orEmpty(),
            imageId = image?.imageId.orEmpty(),
            photoContentVersion = resultPhoto?.generationId?.toLong() ?: 0L,
            eventSequence = resultSceneFrame?.eventSequence ?: 0L,
            sceneFrameOverride = resultSceneFrame,
            sceneStageOverride = resultSceneFrame?.stage,
            sceneStageElapsedMsOverride = resultSceneFrame?.stageElapsedMs,
            sceneStageProgressOverride = resultSceneFrame?.stageProgress,
        )
        val resultWaterEngine = remember(presentedHandoff?.key, image?.imageId) { UnifiedWaterLightEngine() }
        resultWaterEngine.activate(resultFrame)
        DisposableEffect(resultWaterEngine) { onDispose { resultWaterEngine.release() } }
        val resultHost = remember(resultWaterEngine, hostType, resultSceneIdentity) {
            WaterLightHostBinding(
                hostType, WaterLightMaterialProfile.forHost(hostType), resultWaterEngine, resultSceneIdentity,
            )
        }
        val activeEnergyCurve = when (revealFrameState.value.phase) {
            RecognitionRevealPhase.PHOTO_CONTINUITY,
            RecognitionRevealPhase.ORB_RETURN -> revealGeometry?.orbReturnPathLocal
            RecognitionRevealPhase.ORB_TO_NAME -> revealGeometry?.orbToGlyphPathLocal
            RecognitionRevealPhase.SPECIES_NAME_REVEAL -> revealGeometry?.glyphResiduePathLocal
            RecognitionRevealPhase.RESIDUE_SETTLE -> revealGeometry?.glyphResiduePathLocal
            RecognitionRevealPhase.COMPLETE -> null
        }
        val resultPathGeometry = activeEnergyCurve?.let { curve ->
            WaterLightPathGeometry(
                start = WaterLightPathPoint(curve.start.x, curve.start.y),
                control1 = WaterLightPathPoint(curve.control1.x, curve.control1.y),
                control2 = WaterLightPathPoint(curve.control2.x, curve.control2.y),
                end = WaterLightPathPoint(curve.end.x, curve.end.y),
            )
        }
        val processingFishPathSnapshot = visualHandoff?.fishPathSnapshot
            ?.takeIf { it.isValid() && it.imageId == image?.imageId && it.photoSha256 == image?.photoSha256 }
        val trustedMaskReveal = visualHandoff?.eligibility == RecognitionRevealEligibility.FULL_REVEAL &&
            processingFishPathSnapshot != null
        val orbOnlyReveal = visualHandoff != null &&
            visualHandoff.revealDecision == RecognitionRevealDecisionState.ORB_ONLY_READY &&
            visualHandoff.eligibility == RecognitionRevealEligibility.TITLE_FADE_ONLY &&
            revealGeometry?.orbReturnPathLocal != null
        val resultEnergyEvent = if (trustedMaskReveal || orbOnlyReveal) {
            WaterLightEnergy.at(
                resultFrame, trustedMask = trustedMaskReveal, successfulRevealAllowed = true,
                pathGeometry = resultPathGeometry,
                orbOnlyRevealAllowed = orbOnlyReveal,
                pathSegments = if (resultSceneFrame?.stage == RecognitionSceneStage.ORB_RETURN && trustedMaskReveal) {
                    requireNotNull(processingFishPathSnapshot).returnPathSegments
                } else emptyList(),
                pathIdOverride = if (resultSceneFrame?.stage == RecognitionSceneStage.ORB_RETURN && trustedMaskReveal) {
                    requireNotNull(processingFishPathSnapshot).returnPathId
                } else null,
                pathMaskSha256 = processingFishPathSnapshot?.maskSha256
                    ?.takeIf { resultSceneFrame?.stage == RecognitionSceneStage.ORB_RETURN && trustedMaskReveal },
                pathMaskVersion = processingFishPathSnapshot?.maskVersion
                    ?.takeIf { resultSceneFrame?.stage == RecognitionSceneStage.ORB_RETURN && trustedMaskReveal },
                pathGeometryVersion = processingFishPathSnapshot?.geometryVersion
                    ?.takeIf { resultSceneFrame?.stage == RecognitionSceneStage.ORB_RETURN && trustedMaskReveal },
                pathContactPointNormalizedSource = processingFishPathSnapshot?.pStarNormalizedSource
                    ?.takeIf { resultSceneFrame?.stage == RecognitionSceneStage.ORB_RETURN && trustedMaskReveal },
            )
        } else null
        LaunchedEffect(
            resultFrame.sessionId,
            resultFrame.attemptId,
            resultFrame.imageId,
            resultEnergyEvent?.feedback,
            resultEnergyEvent?.pathId,
            resultEnergyEvent?.eventSequence,
            resultEnergyEvent?.level?.let { it >= .999f },
        ) {
            val event = resultEnergyEvent ?: return@LaunchedEffect
            if (event.level >= .999f && resultSceneFrame != null) {
                val activeController = diagnosticPlaybackController
                val receipt = activeController?.recordEnergyEventWithReason(resultSceneFrame, event)
                if (receipt?.accepted == true) {
                    recognitionEvidenceRecorder?.recordEnergyEvent(
                        event = event,
                        accepted = true,
                        taskEnergyMergedBeforeEvent = resultSceneFrame.taskEnergyMerged,
                    )
                } else if (activeController != null && receipt?.rejectionReason != null) {
                    recognitionEvidenceRecorder?.recordEnergyEventRejection(
                        frame = resultSceneFrame,
                        event = event,
                        reason = requireNotNull(receipt.rejectionReason),
                        modelGates = activeController.modelGates,
                    )
                }
                Log.i(
                    "WaterLightEnergy",
                    "RESULT_ENERGY_EVENT session=${event.sessionId} attempt=${event.attemptId} " +
                        "stage=${event.stage} feedback=${event.feedback} path=${event.pathId} " +
                        "geometry=${event.geometryVersion} accepted=${receipt?.accepted ?: "NOT_RECORDED"} " +
                        "reject=${receipt?.rejectionReason}",
                )
            }
            Log.i(
                "WaterLightEnergy",
                "ENERGY_EVENT session=${event.sessionId} attempt=${event.attemptId} image=${event.imageId} " +
                    "sha256=${event.photoSha256} feedback=${event.feedback} source=${event.source} target=${event.target} " +
                    "reference_ms=${event.referenceMs} level=${event.level} path=${event.pathId} " +
                    "start=${event.pathGeometry?.start} end=${event.pathGeometry?.end} geometry=${event.geometryVersion}",
            )
        }
        // The Hero owns the photo while shared geometry is still measuring. Only
        // present the shared optical photo after the Hero can yield ownership.
        if (revealPresentationReady && hostBounds != null && resultHost.accepts(resultFrame, WaterLightHostGeometry(
                hostType, resultFrame.geometryVersion,
                hostBounds.left, hostBounds.top, hostBounds.right, hostBounds.bottom,
            ))) RecognitionRevealOpticalOverlay(
            modifier = Modifier.fillMaxSize(),
            bitmap = image?.bitmap,
            handoff = presentedHandoff,
            photoGeometry = photoTransitionGeometry,
            geometry = revealGeometry,
            frameState = revealFrameState,
            holdPhotoUntilHeroReady = !heroRenderReady,
            material = resultHost.material,
            energyEvent = resultEnergyEvent,
            waterLightFrame = resultFrame,
            sceneIdentity = resultSceneIdentity,
            onOrbCoreDissolveRendered = { receipt ->
                if (receipt.identity == resultSceneIdentity) {
                    diagnosticPlaybackController?.onOrbCoreDissolveRendered(receipt) ?: true
                } else false
            },
            onResidueRenderComplete = { receipt ->
                if (receipt.identity == resultSceneIdentity) {
                    diagnosticPlaybackController?.onResidueRenderComplete(receipt, resultSceneIdentity) ?: true
                } else false
            },
        )
        if (diagnosticUiAvailable) {
            val playback = requireNotNull(diagnosticPlaybackController)
            val stage = diagnosticStage
            val reference = diagnosticReferenceMs ?: 0L
            val stepStatus = when {
                diagnosticFrame?.modelGates?.waitingReason == "WAITING_FOR_RESULT_GEOMETRY" -> "等待 Result / Orb / Glyph 几何"
                diagnosticFrame?.modelGates?.waitingReason == "WAITING_FOR_REAL_CLASSIFIER_GLYPH" -> "等待真实鱼种 Glyph"
                diagnosticFrame?.modelGates?.waitingReason != null -> "等待：${diagnosticFrame?.modelGates?.waitingReason}"
                !revealStartReady && diagnosticDisplayMode == RecognitionProgressDisplayMode.STEP_DIAGNOSTIC -> "等待 Result 几何"
                visualHandoff?.eligibility != RecognitionRevealEligibility.FULL_REVEAL -> "真实HIGH降级/等待 Trusted Mask"
                else -> "进行中"
            }
            RecognitionStepDiagnosticControls(
                displayMode = diagnosticDisplayMode,
                stage = stage,
                playbackMode = playback.mode,
                previewElapsedMs = diagnosticPreviewElapsedMs,
                previewCompleted = diagnosticPreviewCompleted,
                playbackPaused = diagnosticPaused,
                mappedReferenceMs = reference,
                modelStatus = if (productionResult?.ready == true) "真实模型已完成" else "真实模型等待",
                detectorStatus = if (productionResult?.assessment?.primary?.box != null) "就绪" else "失败",
                classifierStatus = when {
                    productionResult?.ready == true -> "就绪"
                    productionResult != null -> "未通过"
                    else -> "等待"
                },
                slimSamStatus = diagnosticFrame?.modelGates?.slimSam?.name ?: "PENDING",
                maskStatus = when {
                    revealHandoff?.geometryTrustState == RecognitionGeometryTrustState.TRUSTED_MASK -> "Mask TRUSTED"
                    subjectResult.status == SubjectStatus.PROCESSING -> "Mask 计算中"
                    subjectResult.status == SubjectStatus.FAILED -> "Mask 失败 · BBOX_ONLY"
                    else -> revealHandoff?.geometryTrustState?.name ?: "Mask不可用"
                },
                geometryStatus = diagnosticFrame?.modelGates?.geometry?.name ?: "PENDING",
                sceneStatus = diagnosticFrame?.modelGates?.runState?.name ?: "WAITING",
                stepStatus = stepStatus,
                normalProgressText = if (diagnosticDisplayMode == RecognitionProgressDisplayMode.NORMAL_PROGRESS) {
                    "真实识别阶段 · ${diagnosticStage.displayName} · ${(diagnosticFrame?.stageProgress ?: 0f).times(100).toInt()}%"
                } else "",
                canGoPrevious = playback.canControl() && stage.ordinal > RecognitionSceneStage.PHOTO_STABLE.ordinal,
                canGoNext = playback.canControl() && stage != RecognitionSceneStage.RESIDUE_SETTLE,
                canControl = playback.canControl(),
                onDisplayModeChange = ::changeDiagnosticDisplayMode,
                onPrevious = {
                    val now = SystemClock.uptimeMillis()
                    if (playback.mode == ScenePlaybackMode.MANUAL) playback.returnToPreviousStage(now)
                    else {
                        val target = RecognitionSceneStage.entries[(stage.ordinal - 1).coerceAtLeast(0)]
                        playback.beginStepPreview(target, now)
                    }
                    actions.setDiagnosticUserPaused(false)
                    actions.setDiagnosticNowMs(now)
                    if (stage == RecognitionSceneStage.ORB_RETURN) actions.onReturnToProcessing()
                },
                onNext = {
                    val now = SystemClock.uptimeMillis()
                    if (playback.mode == ScenePlaybackMode.MANUAL) playback.advanceToNextStage(now)
                    else {
                        val targetIndex = (stage.ordinal + 1).coerceAtMost(RecognitionSceneStage.entries.lastIndex)
                        playback.beginStepPreview(RecognitionSceneStage.entries[targetIndex], now)
                        actions.onDiagnosticDisplayModeChange(RecognitionProgressDisplayMode.STEP_DIAGNOSTIC)
                    }
                    actions.setDiagnosticUserPaused(false)
                    actions.setDiagnosticNowMs(now)
                },
                onPauseResume = {
                    val now = SystemClock.uptimeMillis()
                    if (playback.paused) {
                        actions.setDiagnosticUserPaused(false)
                        playback.resume(reference, now)
                    } else {
                        actions.setDiagnosticUserPaused(true)
                        playback.pause(reference, now)
                    }
                    actions.setDiagnosticNowMs(now)
                },
                onReplay = {
                    val now = SystemClock.uptimeMillis()
                    actions.setDiagnosticUserPaused(false)
                    playback.restartCurrentStage(now)
                    actions.setDiagnosticNowMs(now)
                },
                onAutoPlay = {
                    val now = SystemClock.uptimeMillis()
                    playback.playDiagnosticAuto(reference, now)
                    actions.setDiagnosticNowMs(now)
                },
            )
        }
    }
}

data class RecognitionResultDecisionContentModel(
    val uiState: RecognitionUiState,
    val geometry: com.yujian.ai.ui.recognition.result.RecognitionResultGeometry,
    val adaptiveProfile: com.yujian.ai.ui.adaptive.AdaptiveLayoutProfile,
    val candidateFontScale: Float,
    val revealFrameState: State<RecognitionRevealFrame>,
    val resultInteractionsEnabled: Boolean,
    val revealAlreadyPlayed: Boolean,
    val revealHandoff: RecognitionRevealHandoff?,
    val resultSceneIdentity: RecognitionSceneIdentity,
    val prediction: RecognitionPrediction,
    val resultSnapshot: RecognitionResultSnapshot?,
    val glyphReadinessState: RecognitionGlyphReadiness,
    val diagnosticPlaybackController: SceneMotionCoordinator?,
    val selectedKey: String,
    val selectedName: String,
    val selectedScientificName: String?,
    val resultInfoAlpha: Float,
    val resultInfoTranslationY: Dp,
    val resultInfoDrawPassSequence: AtomicLong,
    val diagnosticFrame: RecognitionSceneFrame?,
    val resultSceneHandoffAccepted: Boolean,
    val resultGeometryReady: Boolean,
    val resultInfoLayoutReady: Boolean,
    val resultInfoEntranceProgress: Float,
    val resultInfoProgressBucket: AtomicInteger,
    val resultInfoRenderReceipt: RecognitionResultInfoRenderReceipt?,
    val resultGeometryVersion: Long,
    val resultInfoReceiptInFlight: AtomicBoolean,
    val resultFrameHandler: Handler,
    val recognitionEvidenceRecorder: RecognitionSessionEvidenceRecorder?,
    val lengthText: String,
    val weightText: String,
    val locationText: String,
    val resolvingLocation: Boolean,
    val storyText: String,
    val safeInlineErrorMessage: String?,
    val inlineErrorActionLabel: String?,
    val inlineErrorAction: (() -> Unit)?,
    val saveBusy: Boolean,
    val activeLoadingDestination: RecognitionSaveDestination?,
    val compactResultActions: Boolean,
    val candidates: List<RecognitionCandidate>,
    val speciesImages: Map<String, String?>,
)

data class RecognitionResultDecisionContentActions(
    val onTitleBounds: (RecognitionRevealWindowRect?) -> Unit,
    val onGlyphReadinessChanged: (RecognitionGlyphReadiness) -> Unit,
    val onSelectCandidate: (RecognitionCandidate) -> Unit,
    val onStoryTextChange: (String) -> Unit,
    val onFieldChange: (ResultEditableField) -> Unit,
    val onInfoLayoutReady: (Boolean) -> Unit,
    val onInfoRenderReceipt: (RecognitionResultInfoRenderReceipt) -> Unit,
    val openSpeciesSelector: () -> Unit,
    val save: (RecognitionSaveDestination) -> Unit,
    val requestExit: (PendingExitAction) -> Unit,
)

@Composable
fun RecognitionResultDecisionContent(
    model: RecognitionResultDecisionContentModel,
    actions: RecognitionResultDecisionContentActions,
) {
    val sideMargin = model.geometry.horizontalMarginDp.dp
    with(model) {
                when (uiState) {
                    RecognitionUiState.RESULT_HIGH -> {
                        SpeciesIdentityRow(
                            speciesName = selectedName,
                            scientificName = selectedScientificName,
                            sideMargin = sideMargin,
                            widthDp = geometry.heroWidthDp,
                            enabled = !saveBusy && resultInteractionsEnabled,
                            revealFrameState = revealFrameState,
                            holdNameUntilHandoff = !revealAlreadyPlayed &&
                                (revealHandoff == null || revealHandoff.revealDecision !in setOf(
                                    RecognitionRevealDecisionState.FISH_FOCUS_READY,
                                    RecognitionRevealDecisionState.ORB_ONLY_READY,
                                )),
                            onTitleBounds = actions.onTitleBounds,
                            glyphSceneIdentity = resultSceneIdentity,
                            glyphSpeciesKey = prediction.top1.speciesKey,
                            onGlyphPathReady = { descriptor ->
                                val expectedName = resultSpeciesDisplayName(prediction.top1.speciesName)
                                val speciesResultReady = resultSnapshot != null && resultSnapshot.key == revealHandoff?.key &&
                                    resultSnapshot.resultDataReady &&
                                    resultSnapshot.glyphSpeciesKey == prediction.top1.speciesKey &&
                                    resultSnapshot.glyphSpeciesName == prediction.top1.speciesName
                                val pathReady = speciesResultReady && descriptor?.matches(
                                    resultSceneIdentity, prediction.top1.speciesKey, expectedName,
                                ) == true
                                val readiness = RecognitionGlyphReadiness(
                                    speciesResultReady = speciesResultReady,
                                    glyphPathReady = pathReady,
                                    descriptor = descriptor.takeIf { pathReady },
                                )
                                actions.onGlyphReadinessChanged(readiness)
                                diagnosticPlaybackController?.onGlyphPathReady(readiness, resultSceneIdentity)
                            },
                            onGlyphRevealRendered = { receipt ->
                                val descriptor = glyphReadinessState.descriptor
                                if (descriptor != null && receipt.identity == resultSceneIdentity &&
                                    receipt.pathId == descriptor.pathId &&
                                    receipt.geometryVersion == descriptor.geometryVersion
                                ) {
                                    val readiness = glyphReadinessState.copy(
                                        glyphRevealRendered = true,
                                        nameStable = false,
                                        renderReceipt = receipt,
                                    )
                                    if (RecognitionGlyphReadinessContract.isRendered(
                                            resultSceneIdentity, descriptor.speciesKey, descriptor.speciesName, readiness,
                                        )
                                    ) {
                                        val accepted = diagnosticPlaybackController?.onGlyphRevealRendered(readiness, resultSceneIdentity)
                                            ?: true
                                        if (accepted) actions.onGlyphReadinessChanged(readiness)
                                        accepted
                                    } else false
                                } else false
                            },
                            onChange = { actions.openSpeciesSelector() },
                        )
                        Spacer(Modifier.height(8.dp))
                        Column(
                            Modifier.fillMaxWidth()
                                .graphicsLayer {
                                    alpha = resultInfoAlpha
                                    translationY = resultInfoTranslationY.toPx()
                                }
                                .drawWithContent {
                                    drawContent()
                                    val infoDrawSequence = resultInfoDrawPassSequence.incrementAndGet()
                                    val frame = diagnosticFrame
                                    if (frame?.stage == RecognitionSceneStage.RESIDUE_SETTLE &&
                                        resultSceneHandoffAccepted && resultGeometryReady && resultInfoLayoutReady
                                    ) {
                                        val progress = resultInfoEntranceProgress.coerceIn(0f, 1f)
                                        val bucket = (progress * 10f).toInt().coerceIn(0, 10)
                                        val lastBucket = resultInfoProgressBucket.get()
                                        if (bucket > lastBucket && resultInfoProgressBucket.compareAndSet(lastBucket, bucket)) {
                                            recognitionEvidenceRecorder?.recordResultInfoEntranceCheckpoint(
                                                checkpoint = "INFO_ENTRANCE_PROGRESS",
                                                identity = resultSceneIdentity,
                                                resultIdentity = prediction.top1.speciesKey,
                                                stage = frame.stage,
                                                stageProgress = frame.stageProgress,
                                                entranceProgress = progress,
                                                geometryVersion = resultGeometryVersion,
                                                drawPassSequence = infoDrawSequence,
                                                renderStatus = "CONTENT_DRAW_SUBMITTED",
                                            )
                                        }
                                        if (progress >= .999f &&
                                            resultInfoRenderReceipt?.geometryVersion != resultGeometryVersion &&
                                            resultInfoReceiptInFlight.compareAndSet(false, true)
                                        ) {
                                            val receipt = RecognitionResultInfoRenderReceipt(
                                                identity = resultSceneIdentity,
                                                resultIdentity = prediction.top1.speciesKey,
                                                geometryVersion = resultGeometryVersion,
                                                renderedProgress = progress,
                                                layoutWidthPx = size.width.toInt(),
                                                layoutHeightPx = size.height.toInt(),
                                                contentDrawn = true,
                                                drawPassSequence = infoDrawSequence,
                                            )
                                            resultFrameHandler.post {
                                                actions.onInfoRenderReceipt(receipt)
                                                recognitionEvidenceRecorder?.recordResultInfoEntranceCheckpoint(
                                                    checkpoint = "INFO_RENDER_COMPLETED",
                                                    identity = receipt.identity,
                                                    resultIdentity = receipt.resultIdentity,
                                                    stage = frame.stage,
                                                    stageProgress = frame.stageProgress,
                                                    entranceProgress = receipt.renderedProgress,
                                                    geometryVersion = receipt.geometryVersion,
                                                    drawPassSequence = receipt.drawPassSequence,
                                                    renderStatus = "DRAW_RECEIPT_READY",
                                                )
                                                resultInfoReceiptInFlight.set(false)
                                            }
                                        }
                                    }
                                },
                        ) {
                        ResultMetadataStrip(
                            length = lengthText,
                            weight = weightText,
                            location = locationText,
                            resolvingLocation = resolvingLocation,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = sideMargin)
                                .onGloballyPositioned { coordinates ->
                                    actions.onInfoLayoutReady(coordinates.size.width > 0 && coordinates.size.height > 0)
                                },
                            accessibilityFontScale = adaptiveProfile.accessibilityFontScale,
                            enabled = !saveBusy && resultInteractionsEnabled,
                            onField = actions.onFieldChange,
                        )
                        Spacer(Modifier.height(8.dp))
                        ResultMemoryNote(
                            value = storyText,
                            onValueChange = actions.onStoryTextChange,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = sideMargin),
                            enabled = !saveBusy && resultInteractionsEnabled,
                            accessibilityFontScale = adaptiveProfile.accessibilityFontScale,
                        )
                        Spacer(Modifier.height(16.dp))
                        ResultInlineError(
                            safeInlineErrorMessage,
                            sideMargin,
                            actionLabel = inlineErrorActionLabel,
                            onAction = inlineErrorAction,
                            enabled = !saveBusy && resultInteractionsEnabled,
                            modifier = Modifier,
                        )
                        ResultDualActions(
                            useResultSurfaceActions = true,
                            saving = saveBusy,
                            loadingDestination = activeLoadingDestination,
                            accessibilityFontScale = adaptiveProfile.accessibilityFontScale,
                            compactLayout = compactResultActions,
                            sideMargin = if (compactResultActions) 16.dp else 24.dp,
                            interactiveEnabled = resultInteractionsEnabled,
                            modifier = Modifier,
                            onContinue = { actions.save(RecognitionSaveDestination.MEMORY) },
                            onSave = { actions.save(RecognitionSaveDestination.HOME) },
                        )
                        }
                    }
                    RecognitionUiState.RESULT_MEDIUM -> {
                        ResultInformationGlass(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = sideMargin),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    "帮我确认一下，这条鱼更像哪一种？",
                                    color = DeepInk,
                                    fontSize = 20.sp,
                                    lineHeight = 28.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = if (adaptiveProfile.accessibilityFontScale || geometry.heroWidthDp <= 320) 2 else 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                CandidateRow(
                                    candidates = candidates,
                                    selectedKey = selectedKey,
                                    widthDp = geometry.heroWidthDp,
                                    accessibilityFontScale = candidateFontScale,
                                    saving = saveBusy,
                                    speciesImages = speciesImages,
                                    onSelect = actions.onSelectCandidate,
                                )
                                YuJianTextAction(
                                    text = "都不是？选择其他鱼种",
                                    onClick = { actions.openSpeciesSelector() },
                                    role = YuJianTextActionRole.MUTED,
                                    enabled = !saveBusy,
                                    showChevron = true,
                                )
                            }
                        }
                        if (selectedKey.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            SpeciesIdentityRow(
                                speciesName = selectedName,
                                sideMargin = sideMargin,
                                widthDp = geometry.heroWidthDp,
                                scientificName = selectedScientificName,
                                enabled = !saveBusy,
                                onChange = { actions.openSpeciesSelector() },
                            )
                            Spacer(Modifier.height(8.dp))
                            ResultMetadataStrip(lengthText, weightText, locationText, resolvingLocation, Modifier.fillMaxWidth().padding(horizontal = sideMargin), accessibilityFontScale = adaptiveProfile.accessibilityFontScale, enabled = !saveBusy) { actions.onFieldChange(it) }
                            Spacer(Modifier.height(12.dp))
                            ResultMemoryNote(storyText, actions.onStoryTextChange, Modifier.fillMaxWidth().padding(horizontal = sideMargin), enabled = !saveBusy, accessibilityFontScale = adaptiveProfile.accessibilityFontScale)
                            Spacer(Modifier.height(16.dp))
                            ResultInlineError(
                                safeInlineErrorMessage,
                                sideMargin,
                                actionLabel = inlineErrorActionLabel,
                                onAction = inlineErrorAction,
                                enabled = !saveBusy,
                            )
                            ResultDualActions(
                                saving = saveBusy,
                                loadingDestination = activeLoadingDestination,
                                accessibilityFontScale = adaptiveProfile.accessibilityFontScale,
                                compactLayout = compactResultActions,
                                sideMargin = if (compactResultActions) 16.dp else 24.dp,
                                onContinue = { actions.save(RecognitionSaveDestination.MEMORY) },
                                onSave = { actions.save(RecognitionSaveDestination.HOME) },
                            )
                        }
                    }
                    RecognitionUiState.RESULT_LOW -> {
                        Column(Modifier.fillMaxWidth().padding(horizontal = sideMargin), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("无法确认是什么鱼", color = DeepInk, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold)
                            if (selectedKey.isBlank()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(if (compactResultActions) 8.dp else 12.dp)) {
                                    YuJianPrimaryButton(
                                        text = "手动选择鱼种", onClick = { actions.openSpeciesSelector() },
                                        modifier = Modifier.weight(1f),
                                        enabled = !saveBusy,
                                        variant = YuJianActionButtonVariant.RESULT_SAVE,
                                        leadingIcon = { Icon(Icons.Rounded.SetMeal, contentDescription = null, modifier = Modifier.size(if (compactResultActions) 16.dp else 18.dp)) },
                                        contentPadding = if (compactResultActions) PaddingValues(horizontal = 8.dp) else PaddingValues(horizontal = 12.dp),
                                        leadingIconSpacing = 4.dp,
                                    )
                                    YuJianPrimaryButton(
                                        text = "重新拍摄", onClick = { actions.requestExit(PendingExitAction.RETAKE) },
                                        modifier = Modifier.weight(1f),
                                        enabled = !saveBusy,
                                        variant = YuJianActionButtonVariant.RESULT_CONTINUE,
                                        leadingIcon = { Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(if (compactResultActions) 16.dp else 18.dp)) },
                                        contentPadding = if (compactResultActions) PaddingValues(horizontal = 8.dp) else PaddingValues(horizontal = 12.dp),
                                        leadingIconSpacing = 4.dp,
                                    )
                                }
                            } else {
                                SpeciesIdentityRow(
                                    speciesName = selectedName,
                                    sideMargin = sideMargin,
                                    widthDp = geometry.heroWidthDp,
                                    scientificName = selectedScientificName,
                                    enabled = !saveBusy,
                                    onChange = { actions.openSpeciesSelector() },
                                )
                                ResultMetadataStrip(lengthText, weightText, locationText, resolvingLocation, Modifier.fillMaxWidth(), accessibilityFontScale = adaptiveProfile.accessibilityFontScale, enabled = !saveBusy) { actions.onFieldChange(it) }
                                ResultMemoryNote(storyText, actions.onStoryTextChange, Modifier.fillMaxWidth(), enabled = !saveBusy, accessibilityFontScale = adaptiveProfile.accessibilityFontScale)
                                ResultInlineError(
                                    safeInlineErrorMessage,
                                    0.dp,
                                    actionLabel = inlineErrorActionLabel,
                                    onAction = inlineErrorAction,
                                    enabled = !saveBusy,
                                )
                                ResultDualActions(
                                    saving = saveBusy, sideMargin = 0.dp,
                                    loadingDestination = activeLoadingDestination,
                                    accessibilityFontScale = adaptiveProfile.accessibilityFontScale,
                                    compactLayout = compactResultActions,
                                    onContinue = { actions.save(RecognitionSaveDestination.MEMORY) },
                                    onSave = { actions.save(RecognitionSaveDestination.HOME) },
                                )
                            }
                        }
                    }
                    else -> Unit
                }
    }
}

enum class ResultEditableField { LENGTH, WEIGHT, LOCATION }
enum class PendingExitAction { BACK, RETAKE }

@Composable
fun ResultHeroViewport(
    bitmap: ImageBitmap,
    bbox: FishPhotoBoundingBox?,
    primaryAmbiguous: Boolean = false,
    widthDp: Int,
    heightDp: Int,
    evidenceFirst: Boolean = false,
    revealFrameState: State<RecognitionRevealFrame>? = null,
    sharedPhotoGeometryReady: Boolean = false,
    holdPhotoUntilReady: Boolean = false,
    onHeroSnapshot: ((RecognitionRevealWindowRect, NormalizedSourceRect?, Boolean) -> Unit)? = null,
) {
    val shape = YuJianRadius.resultHero
    val revealFrame = revealFrameState?.value
    val hideForSharedPhotoTransition = sharedPhotoGeometryReady && revealFrame != null &&
        (revealFrame.photoProgress < 1f || holdPhotoUntilReady)
    var heroBounds by remember(bitmap) { mutableStateOf<RecognitionRevealWindowRect?>(null) }
    var plannedCrop by remember(bitmap) { mutableStateOf<PhotoCropRect?>(null) }
    var renderReady by remember(bitmap) { mutableStateOf(false) }
    LaunchedEffect(heroBounds, plannedCrop, renderReady) {
        val bounds = heroBounds ?: return@LaunchedEffect
        onHeroSnapshot?.invoke(
            bounds,
            plannedCrop?.let { NormalizedSourceRect(it.left, it.top, it.right, it.bottom) },
            renderReady,
        )
    }
    Box(
        Modifier
            .width(widthDp.dp)
            .height(heightDp.dp)
            .clip(shape)
            .graphicsLayer(alpha = if (hideForSharedPhotoTransition) 0f else 1f)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                heroBounds = RecognitionRevealWindowRect(bounds.left, bounds.top, bounds.right, bounds.bottom)
            }
            .background(Color(0xFF16242C), shape)
            .border(1.dp, Color.White.copy(alpha = 0.42f), shape)
            .testTag("recognition-result-hero"),
    ) {
        FishPhotoBitmapImage(
            bitmap = bitmap,
            modifier = Modifier.fillMaxSize(),
            contentDescription = "识别鱼获照片",
            boundingBox = bbox,
            primaryAmbiguous = primaryAmbiguous,
            allowSubjectCrop = !evidenceFirst,
            onRenderGeometry = { crop, ready ->
                plannedCrop = crop
                renderReady = ready
            },
        )
    }
}

@Composable
private fun SpeciesIdentityRow(
    speciesName: String,
    sideMargin: Dp,
    widthDp: Int,
    scientificName: String? = null,
    enabled: Boolean = true,
    revealFrameState: State<RecognitionRevealFrame>? = null,
    holdNameUntilHandoff: Boolean = false,
    onTitleBounds: ((RecognitionRevealWindowRect) -> Unit)? = null,
    glyphSceneIdentity: RecognitionSceneIdentity? = null,
    glyphSpeciesKey: String? = null,
    onGlyphPathReady: ((RecognitionGlyphPathDescriptor?) -> Unit)? = null,
    onGlyphRevealRendered: ((RecognitionGlyphDrawReceipt) -> Boolean)? = null,
    onChange: () -> Unit,
) {
    val compactTitle = widthDp <= 359
    val revealFrame = revealFrameState?.value
    val revealTitleMode = revealFrame?.titleMode ?: RecognitionRevealTitleMode.VISIBLE
    val revealFormationProgress = revealFrame?.titleFormationProgress ?: 1f
    val revealProgress = revealFrame?.titleRevealProgress ?: 1f
    val speciesDisplayName = resultSpeciesDisplayName(speciesName)
    var speciesGlyphPath by remember(
        speciesDisplayName, compactTitle, widthDp, glyphSceneIdentity, glyphSpeciesKey,
    ) { mutableStateOf<android.graphics.Path?>(null) }
    var speciesGlyphDescriptor by remember(
        speciesDisplayName, compactTitle, widthDp, glyphSceneIdentity, glyphSpeciesKey,
    ) { mutableStateOf<RecognitionGlyphPathDescriptor?>(null) }
    val glyphDrawReceiptAccepted = remember(
        speciesDisplayName, compactTitle, widthDp, glyphSceneIdentity, glyphSpeciesKey,
    ) { AtomicBoolean(false) }
    val glyphDrawReceiptInFlight = remember(
        speciesDisplayName, compactTitle, widthDp, glyphSceneIdentity, glyphSpeciesKey,
    ) { AtomicBoolean(false) }
    val glyphReveal = remember(glyphSceneIdentity, glyphSpeciesKey, speciesDisplayName) {
        com.yujian.ai.ui.recognition.reveal.SpeciesNameReveal()
    }
    val glyphReceiptHandler = remember { Handler(Looper.getMainLooper()) }
    val latestOnGlyphRevealRendered = rememberUpdatedState(onGlyphRevealRendered)
    DisposableEffect(glyphReceiptHandler, speciesDisplayName, compactTitle, widthDp, glyphSceneIdentity, glyphSpeciesKey) {
        onDispose { glyphReceiptHandler.removeCallbacksAndMessages(null) }
    }
    val showScientificName = !holdNameUntilHandoff && (revealFrame == null ||
        revealFrame.isComplete || revealTitleMode == RecognitionRevealTitleMode.VISIBLE ||
        revealTitleMode == RecognitionRevealTitleMode.STATIC)
    val visibleTitleAlpha = if (showScientificName) 1f else 0f
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = sideMargin)
            .heightIn(min = 44.dp)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                onTitleBounds?.invoke(RecognitionRevealWindowRect(bounds.left, bounds.top, bounds.right, bounds.bottom))
            }
            .testTag("recognition-result-species"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                speciesDisplayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithContent {
                        if (holdNameUntilHandoff) return@drawWithContent
                        val settled = revealTitleMode == RecognitionRevealTitleMode.STATIC ||
                            revealTitleMode == RecognitionRevealTitleMode.VISIBLE
                        if (settled) drawContent() else speciesGlyphPath?.let { glyph ->
                            val drawResult = glyphReveal.draw(
                                drawContext.canvas.nativeCanvas, glyph, size.width, size.height,
                                mode = revealTitleMode,
                                formationProgress = revealFormationProgress,
                                revealProgress = revealProgress,
                                sweepProgress = revealFrame?.titleSweepProgress ?: revealProgress,
                            )
                            val descriptor = speciesGlyphDescriptor
                            if (revealFrame?.stage == RecognitionSceneStage.SPECIES_NAME_REVEAL &&
                                drawResult?.renderedProgress?.let { it >= .999f } == true &&
                                descriptor != null && glyphSceneIdentity != null && !glyphSpeciesKey.isNullOrBlank() &&
                                !glyphDrawReceiptAccepted.get() &&
                                glyphDrawReceiptInFlight.compareAndSet(false, true)
                            ) {
                                val receipt = RecognitionGlyphDrawReceipt(
                                    identity = descriptor.identity,
                                    speciesKey = descriptor.speciesKey,
                                    speciesName = descriptor.speciesName,
                                    pathId = descriptor.pathId,
                                    geometryVersion = descriptor.geometryVersion,
                                    renderedProgress = drawResult.renderedProgress,
                                    fillPathDrawn = drawResult.fillPathDrawn,
                                    contourPathDrawn = drawResult.contourPathDrawn,
                                    drawPassSequence = drawResult.drawPassSequence,
                                )
                                glyphReceiptHandler.post {
                                    val accepted = try {
                                        latestOnGlyphRevealRendered.value?.invoke(receipt) == true
                                    } finally {
                                        glyphDrawReceiptInFlight.set(false)
                                    }
                                    if (accepted) glyphDrawReceiptAccepted.set(true)
                                }
                            }
                        }
                    }
                    .testTag("recognition-result-species-name"),
                color = DeepInk,
                fontSize = if (compactTitle) 28.sp else 30.sp,
                lineHeight = if (compactTitle) 34.sp else 36.sp,
                fontWeight = FontWeight.SemiBold,
                overflow = TextOverflow.Clip,
                onTextLayout = { layout ->
                    if (speciesGlyphPath == null || speciesGlyphDescriptor == null) {
                        val path = layout.getPathForRange(0, speciesDisplayName.length).asAndroidPath()
                        var characterOffset = 0
                        var builtCharacterCount = 0
                        while (characterOffset < speciesDisplayName.length) {
                            val nextOffset = characterOffset + Character.charCount(Character.codePointAt(speciesDisplayName, characterOffset))
                            val characterPath = layout.getPathForRange(characterOffset, nextOffset).asAndroidPath()
                            val characterBounds = android.graphics.RectF()
                            characterPath.computeBounds(characterBounds, true)
                            if (!characterPath.isEmpty && characterBounds.width() > 1f && characterBounds.height() > 1f) {
                                builtCharacterCount++
                            }
                            characterOffset = nextOffset
                        }
                        val descriptor = if (glyphSceneIdentity != null && !glyphSpeciesKey.isNullOrBlank()) {
                            RecognitionGlyphPathInspector.inspect(
                                identity = glyphSceneIdentity,
                                speciesKey = glyphSpeciesKey,
                                speciesName = speciesDisplayName,
                                path = path,
                                fullTextRangeStart = 0,
                                fullTextRangeEnd = speciesDisplayName.length,
                                builtCharacterCount = builtCharacterCount,
                            )
                        } else null
                        speciesGlyphPath = path
                        speciesGlyphDescriptor = descriptor
                        onGlyphPathReady?.invoke(descriptor)
                    }
                },
            )
            scientificName?.takeIf { showScientificName && it.isNotBlank() }?.let { latinName ->
                Text(
                    latinName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(alpha = visibleTitleAlpha)
                        .drawWithContent {
                            val fraction = when (revealTitleMode) {
                                RecognitionRevealTitleMode.HIDDEN -> 0f
                                RecognitionRevealTitleMode.FORMING -> .04f + .38f * revealFormationProgress.coerceIn(0f, 1f)
                                RecognitionRevealTitleMode.REVEALING -> revealProgress.coerceIn(0f, 1f)
                                RecognitionRevealTitleMode.STATIC,
                                RecognitionRevealTitleMode.VISIBLE -> 1f
                            }
                            if (fraction > 0f) {
                                val canvas = drawContext.canvas.nativeCanvas
                                val saveCount = canvas.save()
                                try {
                                    canvas.clipRect(0f, 0f, size.width * fraction, size.height)
                                    drawContent()
                                } finally {
                                    canvas.restoreToCount(saveCount)
                                }
                            }
                        }
                        .testTag("recognition-result-scientific-name"),
                    color = MutedInk,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Normal,
                    softWrap = true,
                    overflow = TextOverflow.Clip,
                )
            }
        }
        Row(
            modifier = Modifier
                .clickable(enabled = enabled, role = Role.Button, onClick = onChange)
                .padding(start = 8.dp, end = 2.dp, top = 6.dp, bottom = 6.dp)
                .testTag("recognition-result-species-edit"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "修改鱼种",
                color = YuJianColors.DeepLakeBlue,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.padding(start = 2.dp).size(16.dp),
                tint = YuJianColors.DeepLakeBlue.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun ResultMetadataStrip(
    length: String,
    weight: String,
    location: String,
    resolvingLocation: Boolean,
    modifier: Modifier,
    accessibilityFontScale: Boolean = false,
    enabled: Boolean = true,
    onField: (ResultEditableField) -> Unit,
) {
    ResultMetadataSurface(
        modifier = modifier.testTag("recognition-result-metadata"),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            CatchFactRow(Icons.Rounded.Straighten, "长度", if (length.isBlank()) "请输入" else "$length cm", enabled, accessibilityFontScale) { onField(ResultEditableField.LENGTH) }
            CatchFactRow(Icons.Rounded.Scale, "重量", if (weight.isBlank()) "请输入" else "$weight kg", enabled, accessibilityFontScale) { onField(ResultEditableField.WEIGHT) }
            CatchFactRow(Icons.Rounded.LocationOn, "地点", if (resolvingLocation) "正在获取位置…" else location.ifBlank { "请选择" }, enabled, accessibilityFontScale) { onField(ResultEditableField.LOCATION) }
        }
    }
}

@Composable
private fun ResultMetadataSurface(modifier: Modifier, content: @Composable () -> Unit) {
    val shape = YuJianRadius.resultGlass
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.78f), Color.White.copy(alpha = 0.72f)),
                ),
                shape,
            )
            .border(1.dp, Color.White.copy(alpha = 0.72f), shape),
    ) {
        content()
    }
}

@Composable
private fun ResultInformationGlass(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    ResultContentDrivenSurface(
        modifier = modifier,
        fill = Color.White.copy(alpha = 0.86f),
        border = Color.White.copy(alpha = 0.78f),
        content = content,
    )
}

/**
 * Recovery needs a stronger, readable substrate than the ordinary content
 * glass because the lake image remains visible behind the Result page.
 */
@Composable
fun ResultRecoverySurface(
    modifier: Modifier,
    fillAlpha: Float = 0.91f,
    borderAlpha: Float = 0.84f,
    content: @Composable () -> Unit,
) {
    ResultContentDrivenSurface(
        modifier = modifier,
        fill = Color.White.copy(alpha = fillAlpha),
        border = Color.White.copy(alpha = borderAlpha),
        content = content,
    )
}

@Composable
private fun ResultContentDrivenSurface(
    modifier: Modifier,
    fill: Color,
    border: Color,
    content: @Composable () -> Unit,
) {
    val shape = YuJianRadius.resultGlass
    Box(
        modifier = modifier
            .shadow(4.dp, shape, clip = false)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(fill, fill.copy(alpha = (fill.alpha - 0.08f).coerceAtLeast(0.70f))),
                ),
                shape,
            )
            .border(1.dp, border, shape),
    ) {
        content()
    }
}

@Composable
private fun CatchFactRow(
    icon: ImageVector,
    label: String,
    value: String,
    enabled: Boolean,
    accessibilityFontScale: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (accessibilityFontScale) 68.dp else 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = YuJianColors.DeepLakeBlue.copy(alpha = 0.78f),
            )
            Text(
                label,
                color = MutedInk,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 10.dp),
            )
            Spacer(Modifier.weight(1f))
            val placeholder = value == "请输入" || value == "请选择" || value == "正在获取位置…"
            Text(
                value,
                color = if (placeholder) MutedInk else DeepInk,
                fontSize = if (placeholder) 15.sp else 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.padding(start = 6.dp).size(18.dp),
                tint = YuJianColors.DeepLakeBlue.copy(alpha = 0.62f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultMemoryNote(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
    accessibilityFontScale: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    ResultInformationGlass(
        modifier = modifier
            .heightIn(min = if (accessibilityFontScale) 144.dp else 132.dp)
            .testTag("recognition-result-story"),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "写下这次鱼获的故事",
                color = DeepInk,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    // 外层 ResultInformationGlass 已提供 White 0.86 半透明底，
                    // 内层再叠白会让编辑区与卡片 padding 处透明度不一致，只保留描边。
                    .border(1.dp, Color.White.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("recognition-story-editor"),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                    if (value.isBlank()) {
                        Text("记录这一刻的感受……", color = MutedInk, fontSize = 15.sp, lineHeight = 22.sp)
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth()
                            .testTag("recognition-story-input")
                            .bringIntoViewRequester(bringIntoViewRequester)
                            .onFocusChanged {
                                focused = it.isFocused
                                if (it.isFocused) coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                            },
                        minLines = 1,
                        maxLines = if (focused || accessibilityFontScale) 4 else 2,
                        enabled = enabled,
                        textStyle = TextStyle(color = DeepInk, fontSize = 15.sp, lineHeight = 22.sp),
                    )
                }
                Text(
                    "${value.codePointCount(0, value.length)}/300",
                    modifier = Modifier.fillMaxWidth().testTag("recognition-story-counter"),
                    textAlign = TextAlign.End,
                    color = MutedInk,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun CandidateRow(
    candidates: List<RecognitionCandidate>, selectedKey: String, widthDp: Int,
    accessibilityFontScale: Float, saving: Boolean,
    speciesImages: Map<String, String?>,
    onSelect: (RecognitionCandidate) -> Unit,
) {
    val accessibilityScroll = RecognitionResultGeometryResolver.usesScrollableCandidateRow(accessibilityFontScale)
    val cardWidth = if (accessibilityScroll) 104f else if (candidates.size == 2) 136f else ((widthDp - 16f) / 3f).coerceIn(88f, 116f)
    Row(
        Modifier.fillMaxWidth().then(
            if (accessibilityScroll) Modifier.horizontalScroll(rememberScrollState()) else Modifier,
        ),
        horizontalArrangement = if (accessibilityScroll) {
            Arrangement.spacedBy(8.dp)
        } else {
            Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        },
    ) {
        candidates.take(3).forEachIndexed { index, candidate ->
            val selected = candidate.speciesKey == selectedKey
            val suggested = index == 0 && !selected
            ResultInformationGlass(
                modifier = Modifier.width(cardWidth.dp)
                    .graphicsLayer { alpha = if (saving) 0.42f else 1f }
                    .then(if (selected) Modifier.border(2.dp, YuJianColors.MorningGold, YuJianRadius.resultGlass) else Modifier)
                    .semantics {
                        this.selected = selected
                        if (suggested) stateDescription = "模型建议"
                    }
                    .clickable(enabled = !saving, role = Role.RadioButton) { onSelect(candidate) },
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                val mediaSize = if (widthDp <= 320) 44.dp else 48.dp
                Box(
                    Modifier.size(mediaSize).clip(RoundedCornerShape(50)).background(Color(0x337A9C9A)),
                    contentAlignment = Alignment.Center,
                ) {
                    val imageUrl = speciesImages[candidate.speciesKey]
                    if (!imageUrl.isNullOrBlank()) {
                        RemoteImage(
                            url = imageUrl,
                            modifier = Modifier.size(mediaSize),
                            contentDescription = candidate.speciesName,
                            contentScale = ContentScale.Fit,
                            placeholder = { FishIllustration(modifier = Modifier.size(mediaSize), size = mediaSize, bodyColor = Color(0xFF789795)) },
                        )
                    } else {
                        FishIllustration(modifier = Modifier.size(mediaSize), size = mediaSize, bodyColor = Color(0xFF789795))
                    }
                }
                Text(candidate.speciesName, color = DeepInk, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ResultDualActions(
    saving: Boolean,
    loadingDestination: RecognitionSaveDestination?,
    interactiveEnabled: Boolean = true,
    accessibilityFontScale: Boolean,
    compactLayout: Boolean,
    sideMargin: Dp,
    modifier: Modifier = Modifier,
    onContinue: () -> Unit,
    onSave: () -> Unit,
    useResultSurfaceActions: Boolean = false,
) {
    // The callbacks are supplied by the enclosing page so metadata and species are validated together.
    val blocked = saving || loadingDestination != null || !interactiveEnabled
    if (accessibilityFontScale) {
        Column(
            modifier.fillMaxWidth().padding(horizontal = sideMargin),
            verticalArrangement = Arrangement.spacedBy(if (compactLayout) 8.dp else 12.dp),
        ) {
            ResultDualActionButton(
                destination = RecognitionSaveDestination.MEMORY,
                loadingDestination = loadingDestination,
                enabled = !blocked,
                compactLayout = compactLayout,
                useResultSurfaceActions = useResultSurfaceActions,
                modifier = Modifier.fillMaxWidth(),
                onClick = onContinue,
            )
            ResultDualActionButton(
                destination = RecognitionSaveDestination.HOME,
                loadingDestination = loadingDestination,
                enabled = !blocked,
                compactLayout = compactLayout,
                useResultSurfaceActions = useResultSurfaceActions,
                modifier = Modifier.fillMaxWidth(),
                onClick = onSave,
            )
        }
    } else {
        Row(
            modifier.fillMaxWidth().padding(horizontal = sideMargin),
            horizontalArrangement = Arrangement.spacedBy(if (compactLayout) 8.dp else 12.dp),
        ) {
            ResultDualActionButton(
                destination = RecognitionSaveDestination.MEMORY,
                loadingDestination = loadingDestination,
                enabled = !blocked,
                compactLayout = compactLayout,
                useResultSurfaceActions = useResultSurfaceActions,
                modifier = Modifier.weight(1f),
                onClick = onContinue,
            )
            ResultDualActionButton(
                destination = RecognitionSaveDestination.HOME,
                loadingDestination = loadingDestination,
                enabled = !blocked,
                compactLayout = compactLayout,
                useResultSurfaceActions = useResultSurfaceActions,
                modifier = Modifier.weight(1f),
                onClick = onSave,
            )
        }
    }
}

@Composable
private fun ResultDualActionButton(
    destination: RecognitionSaveDestination,
    loadingDestination: RecognitionSaveDestination?,
    enabled: Boolean,
    compactLayout: Boolean,
    useResultSurfaceActions: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val isContinue = destination == RecognitionSaveDestination.MEMORY
    val text = if (isContinue) "继续记忆" else "保存本次鱼获"
    val icon = if (isContinue) Icons.Rounded.PhotoLibrary else Icons.Rounded.Save
    val loading = loadingDestination == destination
    if (useResultSurfaceActions) {
        ResultSurfaceActionButton(
            text = text,
            icon = icon,
            modifier = modifier,
            enabled = enabled,
            loading = loading,
            compactLayout = compactLayout,
            emphasized = !isContinue,
            testTag = if (isContinue) "recognition-result-continue" else "recognition-result-save",
            onClick = onClick,
        )
    } else {
        YuJianPrimaryButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            loading = loading,
            variant = if (isContinue) YuJianActionButtonVariant.SECONDARY_STRONG else YuJianActionButtonVariant.PRIMARY,
            leadingIcon = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (compactLayout) 16.dp else 18.dp),
                )
            },
            contentPadding = if (compactLayout) PaddingValues(horizontal = 8.dp) else PaddingValues(horizontal = 12.dp),
            leadingIconSpacing = 4.dp,
        )
    }
}

@Composable
fun ResultSurfaceActionButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier,
    enabled: Boolean,
    loading: Boolean,
    compactLayout: Boolean,
    emphasized: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val surface = if (emphasized) Color.White.copy(alpha = 0.96f) else Color.White.copy(alpha = 0.74f)
    val edge = if (emphasized) YuJianColors.MorningGold else YuJianColors.DeepLakeBlue.copy(alpha = 0.24f)
    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(surface, shape)
            .border(if (emphasized) 1.4.dp else 1.dp, edge, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag(testTag)
            .semantics {
                if (loading) stateDescription = "正在加载"
            }
            .padding(horizontal = if (compactLayout) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(if (compactLayout) 16.dp else 18.dp),
                color = YuJianColors.DeepLakeBlue,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(if (compactLayout) 16.dp else 18.dp),
                tint = YuJianColors.DeepLakeBlue,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            color = YuJianColors.DeepLakeBlue,
            fontSize = if (compactLayout) 13.sp else 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ResultInlineError(
    message: String?,
    sideMargin: Dp,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!message.isNullOrBlank()) {
        Column(modifier.fillMaxWidth().padding(horizontal = sideMargin).padding(bottom = 8.dp)) {
            Text(message, color = Color(0xFF9E4035), fontSize = 13.sp, lineHeight = 18.sp)
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction, enabled = enabled, contentPadding = PaddingValues(0.dp)) {
                    Text(actionLabel, color = YuJianColors.DeepLakeBlue, fontSize = 13.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultFieldEditorSheet(
    title: String, label: String, unit: String, value: String, keyboardType: KeyboardType,
    validation: (String) -> String?, onDismiss: () -> Unit, onSave: (String) -> Unit,
) {
    val initialValue = remember(title, value) { resultNumericEditorInitialValue(value) }
    var text by remember(title, value) { mutableStateOf(initialValue) }
    var error by remember(title) { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember(title) { FocusRequester() }
    fun finishEditing() {
        val invalid = validation(text.text)
        if (invalid != null) {
            error = invalid
        } else {
            onSave(text.text.trim())
            keyboard?.hide()
        }
    }
    LaunchedEffect(title) {
        delay(220L)
        focusRequester.requestFocus()
        keyboard?.show()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = DeepInk, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = text, onValueChange = { next ->
                    val cleaned = next.text.filter { char -> char.isDigit() || char == '.' || char == ',' }.replace(',', '.').take(8)
                    text = TextFieldValue(cleaned, TextRange(cleaned.length))
                    error = null
                },
                modifier = Modifier.widthIn(max = 216.dp).fillMaxWidth().heightIn(min = 64.dp)
                    .align(Alignment.CenterHorizontally).focusRequester(focusRequester)
                    .testTag("recognition-numeric-${label.lowercase()}"), singleLine = true,
                label = { Text(label) }, suffix = { Text(unit) }, isError = error != null,
                supportingText = error?.let { { Text(it) } },
                textStyle = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, color = DeepInk),
                trailingIcon = if (text.text.isNotEmpty()) {
                    { IconButton(onClick = { text = TextFieldValue(""); error = null }, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Close, contentDescription = "清除输入", tint = MutedInk) } }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { finishEditing() }),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (initialValue.text.isNotBlank()) {
                    YuJianTextAction(
                        text = "清除", onClick = { onSave(""); keyboard?.hide() },
                        role = YuJianTextActionRole.MUTED,
                    )
                } else Spacer(Modifier.width(64.dp))
                YuJianTextAction(text = "完成", onClick = { finishEditing() }, role = YuJianTextActionRole.NORMAL)
            }
        }
    }
}

fun String.takeUnicodeCodePoints(limit: Int): String = RecognitionResultInputValidation.takeUnicodeCodePoints(this, limit)
