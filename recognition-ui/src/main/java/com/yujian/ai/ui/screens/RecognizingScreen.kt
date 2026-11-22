package com.yujian.ai.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.Choreographer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import com.yujian.ai.ai.FishInputAssessment
import com.yujian.ai.recognition.ui.BuildConfig
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ui.recognition.Stage045EnergyStreamGlOverlay
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometry
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.RecognitionAttemptDeadline
import com.yujian.ai.ai.RecognitionAttemptFailureCode
import com.yujian.ai.ai.RecognitionFailureCode
import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ai.RecognitionProgress
import com.yujian.ai.ai.RecognitionStageFailureException
import com.yujian.ai.ai.subject.FishSubjectResult
import com.yujian.ai.ai.subject.FishSubjectQuality
import com.yujian.ai.ai.subject.SubjectStatus
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.identify.RecognitionImageTransform
import com.yujian.ai.ui.identify.NormalizedSourceRect
import com.yujian.ai.ui.identify.DisplayRect
import com.yujian.ai.ui.identify.RecognitionUiState
import com.yujian.ai.ui.identify.resolveRecognitionUiState
import com.yujian.ai.ui.recognition.PhotoShockwaveTransform
import com.yujian.ai.ui.recognition.RecognitionAmbientField
import com.yujian.ai.ui.recognition.photoShockwaveTransform
import com.yujian.ai.ui.recognition.RecognitionContourSegment
import com.yujian.ai.ui.recognition.RecognitionMaskContourExtractor
import com.yujian.ai.ui.recognition.RecognitionFishFocus
import com.yujian.ai.ui.recognition.RecognitionFishFocusLevel
import com.yujian.ai.ui.recognition.RecognitionMotionPolicy
import com.yujian.ai.ui.recognition.RecognitionMotionTraceSample
import com.yujian.ai.ui.recognition.RecognitionPerimeterShaderOverlay
import com.yujian.ai.ui.designsystem.components.aiorb.AIOrbCore
import com.yujian.ai.ui.designsystem.components.aiorb.OrbRenderIsolationMode
import com.yujian.ai.ui.designsystem.components.aiorb.orbDebugIsolationModeWithHostPriority
import com.yujian.ai.ui.designsystem.components.aiorb.aiOrbOpticalEffectEnabled
import com.yujian.ai.ui.designsystem.components.aiorb.aiOrbPresentationCompletion
import com.yujian.ai.ui.designsystem.components.aiorb.aiOrbProcessingState
import com.yujian.ai.ui.designsystem.lakeglass.LakeGlassBackgroundHost
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightPhase
import com.yujian.ai.ui.waterlight.core.SceneMotionCoordinator
import com.yujian.ai.ui.waterlight.core.RecognitionProgressDisplayMode
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.DIAGNOSTIC_STEP_DURATION_MS
import com.yujian.ai.ui.waterlight.core.RecognitionFishPathSnapshot
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionFishGoldRenderReceipt
import com.yujian.ai.ui.waterlight.core.RecognitionContourRenderReceipt
import com.yujian.ai.ui.waterlight.core.RecognitionFishPathBindingStatus
import com.yujian.ai.ui.waterlight.core.RecognitionSceneTimelineProjection
import com.yujian.ai.ui.waterlight.core.ScenePlaybackMode
import com.yujian.ai.ui.waterlight.core.WaterLightEnergy
import com.yujian.ai.ui.waterlight.core.EnergyEvent
import com.yujian.ai.ui.waterlight.core.EnergyFeedback
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometryFactory
import com.yujian.ai.ui.waterlight.core.WaterLightPathPoint
import com.yujian.ai.ui.waterlight.core.WaterLightPathLedger
import com.yujian.ai.ui.waterlight.core.WaterLightPerimeterArrivalSnapshot
import com.yujian.ai.ui.waterlight.mask.WaterLightMaskAdapter
import com.yujian.ai.ui.waterlight.core.UnifiedWaterLightEngine
import com.yujian.ai.ui.waterlight.core.WaterLightHost
import com.yujian.ai.ui.waterlight.core.WaterLightHostBinding
import com.yujian.ai.ui.waterlight.core.WaterLightGeometryVersion
import com.yujian.ai.ui.waterlight.core.WaterLightHostGeometry
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
import com.yujian.ai.ui.waterlight.renderer.WaterDropletRenderer
import com.yujian.ai.ui.waterlight.renderer.WaterLightEnergyPathRenderer
import com.yujian.ai.ui.waterlight.renderer.WaterGrassField
import com.yujian.ai.ui.waterlight.renderer.OrbRuntimeProfileV1
import com.yujian.ai.ui.waterlight.renderer.OrbFilamentRendererMode
import com.yujian.ai.ui.waterlight.renderer.OrbStage02VisualProfile
import com.yujian.ai.ui.waterlight.renderer.OrbStage02LayerIsolation
import com.yujian.ai.ui.waterlight.renderer.OrbStage02MaxVisualProfile
import com.yujian.ai.ui.waterlight.renderer.OrbInteriorOpticalField
import com.yujian.ai.ui.waterlight.renderer.OrbStage02RenderTrace
import com.yujian.ai.ui.waterlight.renderer.OrbR06RenderPass
import com.yujian.ai.ui.waterlight.renderer.OrbR06PathDiagnostic
import com.yujian.ai.ui.waterlight.renderer.OrbShaderRoute
import com.yujian.ai.ui.waterlight.renderer.OrbResidentFilamentMotionV1

import com.yujian.ai.ui.recognition.processingOrbMotionFrame
import com.yujian.ai.ui.recognition.RecognitionVisualStateController
import com.yujian.ai.ui.recognition.recognitionMaskContourRevealProgress
import com.yujian.ai.ui.recognition.reveal.RecognitionProcessingPhotoSnapshot
import com.yujian.ai.ui.recognition.reveal.RecognitionGeometryTrustState
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealPolicy
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealPathGeometry
import com.yujian.ai.ui.recognition.reveal.RecognitionMaskIntegrity
import com.yujian.ai.ui.recognition.reveal.RecognitionClassifierTaskState
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealDecisionGate
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealDecisionState
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealWindowRect
import com.yujian.ai.ui.recognition.reveal.RecognitionSubjectGeometrySnapshot
import com.yujian.ai.ui.recognition.diagnostics.RecognitionEvidenceZipExporter
import com.yujian.ai.ui.recognition.diagnostics.RecognitionEvidenceZipExportResult
import com.yujian.ai.ui.recognition.FISH_FOCUS_A_PROMOTION_DELAY_MS
import com.yujian.ai.ui.recognition.recognitionDisplayedFishFocusLevel
import com.yujian.ai.ui.recognition.recognitionMaskAcceptanceOpen
import com.yujian.ai.ui.recognition.rememberRecognitionMotionPolicy
import com.yujian.ai.ui.recognition.RecognitionPhotoFlowPlan
import com.yujian.ai.ui.recognition.planRecognitionPhotoFlow
import com.yujian.ai.ui.recognition.interpolateRecognitionPhotoTransform
import com.yujian.ai.ui.recognition.RecognitionStepDiagnosticControls
import com.yujian.ai.ui.recognition.OrbStage02VisualEvidenceExport
import com.yujian.ai.ui.recognition.diagnostics.RecognitionSessionEvidenceRecorder
import com.yujian.ai.ui.recognition.diagnostics.recognitionEvidenceWindow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val LOG_TAG = "RecognitionProcessingScene"

private fun recognitionMonotonicNowMs(): Long = SystemClock.uptimeMillis()

private fun evidenceGeometry(geometry: com.yujian.ai.ui.waterlight.core.WaterLightPathGeometry): Map<String, Any?> = mapOf(
    "start" to listOf(geometry.start.x, geometry.start.y),
    "control1" to listOf(geometry.control1.x, geometry.control1.y),
    "control2" to listOf(geometry.control2.x, geometry.control2.y),
    "end" to listOf(geometry.end.x, geometry.end.y),
    "coordinateSpace" to geometry.coordinateSpace,
)

@Composable
fun RecognizingScreen(
    image: SelectedImage?, onBack: () -> Unit,
    recognize: suspend ((RecognitionProgress) -> Unit) -> ProductionRecognitionResult,
    generateSubject: (suspend (SelectedImage, NormalizedFishBox) -> FishSubjectResult)? = null,
    onFinished: (ProductionRecognitionResult) -> Unit, onFailure: (Throwable) -> Unit = {},
    phaseOverride: RecognitionPhase? = null, visualClockOverrideMs: Long? = null,
    motionPolicyOverride: RecognitionMotionPolicy? = null,
    onSubjectGeometry: (FishSubjectResult, RecognitionSubjectGeometrySnapshot?) -> Unit = { _, _ -> },
    onProcessingPhotoSnapshot: (RecognitionProcessingPhotoSnapshot) -> Unit = {},
    onFishPathSnapshot: (RecognitionFishPathSnapshot) -> Unit = {},
    recognitionSessionId: String? = null,
    recognitionAttemptId: String? = null,
    recognitionEvidenceRecorder: RecognitionSessionEvidenceRecorder? = null,
    diagnosticPlaybackController: SceneMotionCoordinator? = null,
    diagnosticDisplayMode: RecognitionProgressDisplayMode? = null,
    onDiagnosticDisplayModeChange: ((RecognitionProgressDisplayMode) -> Unit)? = null,
    onDiagnosticRevealRequested: ((ProductionRecognitionResult) -> String?)? = null,
    diagnosticReturnRevision: Int = 0,
    generateSubjectWithConfidence: (suspend (SelectedImage, NormalizedFishBox, Float) -> FishSubjectResult)? = null,
    recognitionAttemptDeadline: RecognitionAttemptDeadline? = null,
    prepareSubject: (suspend (SelectedImage) -> Unit)? = null,
    onFinishedAccepted: (ProductionRecognitionResult) -> Boolean = { true },
) = RecognitionProcessingScene(
    image, onBack, recognize, generateSubject, onFinished, onFailure,
    phaseOverride, visualClockOverrideMs, motionPolicyOverride = motionPolicyOverride,
    onSubjectGeometry = onSubjectGeometry,
    onProcessingPhotoSnapshot = onProcessingPhotoSnapshot,
    onFishPathSnapshot = onFishPathSnapshot,
    recognitionSessionId = recognitionSessionId,
    recognitionAttemptId = recognitionAttemptId,
    recognitionEvidenceRecorder = recognitionEvidenceRecorder,
    diagnosticPlaybackController = diagnosticPlaybackController,
    diagnosticDisplayMode = diagnosticDisplayMode,
    onDiagnosticDisplayModeChange = onDiagnosticDisplayModeChange,
    onDiagnosticRevealRequested = onDiagnosticRevealRequested,
    diagnosticReturnRevision = diagnosticReturnRevision,
    generateSubjectWithConfidence = generateSubjectWithConfidence,
    recognitionAttemptDeadline = recognitionAttemptDeadline,
    prepareSubject = prepareSubject,
    onFinishedAccepted = onFinishedAccepted,
)

/** Presentation-only shell: it never changes detector, crop, classifier, or result semantics. */
@Composable
fun RecognitionProcessingScene(
    image: SelectedImage?, onBack: () -> Unit,
    recognize: suspend ((RecognitionProgress) -> Unit) -> ProductionRecognitionResult,
    generateSubject: (suspend (SelectedImage, NormalizedFishBox) -> FishSubjectResult)? = null,
    onFinished: (ProductionRecognitionResult) -> Unit, onFailure: (Throwable) -> Unit = {},
    phaseOverride: RecognitionPhase? = null, visualClockOverrideMs: Long? = null,
    onVisualPhasePresented: (RecognitionPhase, Long) -> Unit = { _, _ -> },
    motionPolicyOverride: RecognitionMotionPolicy? = null,
    onMotionFrame: ((RecognitionMotionTraceSample) -> Unit)? = null,
    onFishFocusTransform: ((RecognitionImageTransform) -> Unit)? = null,
    onSubjectGeometry: (FishSubjectResult, RecognitionSubjectGeometrySnapshot?) -> Unit = { _, _ -> },
    onProcessingPhotoSnapshot: (RecognitionProcessingPhotoSnapshot) -> Unit = {},
    onFishPathSnapshot: (RecognitionFishPathSnapshot) -> Unit = {},
    recognitionSessionId: String? = null,
    recognitionAttemptId: String? = null,
    recognitionEvidenceRecorder: RecognitionSessionEvidenceRecorder? = null,
    diagnosticPlaybackController: SceneMotionCoordinator? = null,
    diagnosticDisplayMode: RecognitionProgressDisplayMode? = null,
    onDiagnosticDisplayModeChange: ((RecognitionProgressDisplayMode) -> Unit)? = null,
    onDiagnosticRevealRequested: ((ProductionRecognitionResult) -> String?)? = null,
    diagnosticReturnRevision: Int = 0,
    generateSubjectWithConfidence: (suspend (SelectedImage, NormalizedFishBox, Float) -> FishSubjectResult)? = null,
    recognitionAttemptDeadline: RecognitionAttemptDeadline? = null,
    prepareSubject: (suspend (SelectedImage) -> Unit)? = null,
    onPhotoFramePresented: ((Long) -> Unit)? = null,
    onFinishedAccepted: (ProductionRecognitionResult) -> Boolean = { true },
) {
    val motionPolicy = motionPolicyOverride ?: rememberRecognitionMotionPolicy()
    val pageContext = LocalContext.current
    val pageView = LocalView.current
    val orbEvidenceScope = rememberCoroutineScope()
    DisposableEffect(pageView) {
        val controller = (pageContext as? Activity)?.window?.let { WindowCompat.getInsetsController(it, pageView) }
        val oldStatus = controller?.isAppearanceLightStatusBars
        val oldNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (oldStatus != null) controller?.isAppearanceLightStatusBars = oldStatus
            if (oldNavigation != null) controller?.isAppearanceLightNavigationBars = oldNavigation
        }
    }
    val phasePresentedCallback by rememberUpdatedState(onVisualPhasePresented)
    val prepareSubjectCallback = rememberUpdatedState(prepareSubject)
    val photoFramePresentedCallback = rememberUpdatedState(onPhotoFramePresented)
    val fishPathSnapshotCallback = rememberUpdatedState(onFishPathSnapshot)
    val sceneKey = remember(
        image?.imageId,
        image?.photoSha256,
        image?.bitmap?.width,
        image?.bitmap?.height,
        recognitionSessionId,
        recognitionAttemptId,
    ) {
        "${image?.imageId.orEmpty()}|${image?.photoSha256.orEmpty()}|${recognitionSessionId.orEmpty()}|${recognitionAttemptId.orEmpty()}"
    }
    val attemptDeadline = recognitionAttemptDeadline ?: remember(sceneKey) {
        RecognitionAttemptDeadline(recognitionMonotonicNowMs())
    }
    var localDisplayMode by remember(sceneKey) {
        mutableStateOf(RecognitionProgressDisplayMode.NORMAL_PROGRESS)
    }
    val displayMode = diagnosticDisplayMode ?: localDisplayMode
    val diagnosticUiAvailable = false
    var debugOrbIsolationMode by remember(sceneKey) {
        mutableStateOf(OrbRenderIsolationMode.NORMAL)
    }
    var debugOrbVisualProfile by remember(sceneKey) {
        mutableStateOf(OrbStage02VisualProfile.PRODUCTION_V1)
    }
    var debugOrbLayerIsolation by remember(sceneKey) {
        mutableStateOf(OrbStage02LayerIsolation.R03_R06)
    }
    var debugOrbShaderStage by remember(sceneKey) { mutableStateOf("PHOTO_STABLE") }
    var debugOrbShaderRoute by remember(sceneKey) { mutableStateOf("UNAVAILABLE") }
    var debugOrbShaderStatus by remember(sceneKey) { mutableStateOf("UNAVAILABLE") }
    var debugR06BackPaths by remember(sceneKey) { mutableStateOf<Set<String>>(emptySet()) }
    var debugR06BackTransmissionPaths by remember(sceneKey) { mutableStateOf<Set<String>>(emptySet()) }
    var debugR06FrontPaths by remember(sceneKey) { mutableStateOf<Set<String>>(emptySet()) }
    var debugR06Diagnostics by remember(sceneKey) { mutableStateOf<List<OrbR06PathDiagnostic>>(emptyList()) }
    val r06EvidenceSampleBuckets = remember(sceneKey) { mutableMapOf<String, Long>() }
    var debugOrbEvidenceBounds by remember(sceneKey) { mutableStateOf(android.graphics.Rect()) }
    var debugOrbEvidenceCoreSizePx by remember(sceneKey) { mutableFloatStateOf(0f) }
    var debugOrbEvidenceProgress by remember(sceneKey) { mutableFloatStateOf(0f) }
    var debugOrbOpticalSampleMs by remember(sceneKey) { mutableStateOf<Long?>(null) }
    var debugOrbCaptureId by remember(sceneKey) { mutableStateOf<String?>(null) }
    var debugOrbExcludedPathId by remember(sceneKey) { mutableStateOf<String?>(null) }
    var debugOrbIsolationHostAccepted by remember(sceneKey) { mutableStateOf(false) }
    LaunchedEffect(sceneKey, displayMode, diagnosticUiAvailable) {
        if (!diagnosticUiAvailable || displayMode != RecognitionProgressDisplayMode.STEP_DIAGNOSTIC) {
            debugOrbIsolationMode = OrbRenderIsolationMode.NORMAL
        }
    }
    val stepDiagnosticActive = diagnosticUiAvailable && displayMode == RecognitionProgressDisplayMode.STEP_DIAGNOSTIC
    // The diagnostic shell is a fixed Debug-session policy, independent of its display toggle.
    val diagnosticModeState = rememberUpdatedState(false)
    val playbackController = diagnosticPlaybackController ?: remember(sceneKey) { SceneMotionCoordinator() }
    var playbackRevision by remember(sceneKey) { mutableIntStateOf(0) }
    val diagnosticStepDisplayState = rememberUpdatedState(
        diagnosticUiAvailable && displayMode == RecognitionProgressDisplayMode.STEP_DIAGNOSTIC,
    )
    val playbackMode = playbackController.mode
    val playbackPaused = playbackController.paused
    // Orb presentation ends only when the accepted Result handoff navigates away.
    val orbCompletion = 0f
    var rootOrigin by remember(sceneKey) { mutableStateOf(Offset.Zero) }
    var realPhase by remember(sceneKey) { mutableStateOf(RecognitionPhase.CAPTURED) }
    var visualPhase by remember(sceneKey) { mutableStateOf(RecognitionPhase.CAPTURED) }
    var productionSceneStage by remember(sceneKey) { mutableStateOf(RecognitionSceneStage.PHOTO_STABLE) }
    var productionStageElapsedMs by remember(sceneKey) { mutableLongStateOf(0L) }
    var productionStageProgress by remember(sceneKey) { mutableFloatStateOf(0f) }
    var assessment by remember(sceneKey) { mutableStateOf<FishInputAssessment?>(null) }
    var subjectBitmap by remember(sceneKey) { mutableStateOf<Bitmap?>(null) }
    var subjectBox by remember(sceneKey) { mutableStateOf<NormalizedFishBox?>(null) }
    var contour by remember(sceneKey) { mutableStateOf(emptyList<RecognitionContourSegment>()) }
    var trustedSubjectMask by remember(sceneKey) { mutableStateOf(false) }
    var maskFileGenerated by remember(sceneKey) { mutableStateOf(false) }
    var maskFileExists by remember(sceneKey) { mutableStateOf(false) }
    var maskBitmapDecodeStatus by remember(sceneKey) { mutableStateOf("NOT_STARTED") }
    var maskFileSha256 by remember(sceneKey) { mutableStateOf<String?>(null) }
    var maskShaMatchesGeometry by remember(sceneKey) { mutableStateOf(false) }
    var maskPhotoIdentityMatches by remember(sceneKey) { mutableStateOf(false) }
    var maskGeometryMatches by remember(sceneKey) { mutableStateOf(false) }
    var maskRoiDimensions by remember(sceneKey) { mutableStateOf(0 to 0) }
    var maskRuntimeRejectReason by remember(sceneKey) { mutableStateOf("NOT_STARTED") }
    var pStarBindRejectionReason by remember(sceneKey) { mutableStateOf<String?>(null) }
    var pStarPathId by remember(sceneKey) { mutableStateOf<String?>(null) }
    var processingPhotoSnapshotForReveal by remember(sceneKey) {
        mutableStateOf<RecognitionProcessingPhotoSnapshot?>(null)
    }
    var subjectResult by remember(sceneKey) { mutableStateOf(FishSubjectResult(SubjectStatus.IDLE)) }
    var finishedResult by remember(sceneKey) { mutableStateOf<ProductionRecognitionResult?>(null) }
    var pipelineError by remember(sceneKey) { mutableStateOf<Throwable?>(null) }
    var delivered by remember(sceneKey) { mutableStateOf(false) }
    var deliveryInProgress by remember(sceneKey) { mutableStateOf(false) }
    var photoPresentedAtMs by remember(sceneKey) { mutableStateOf<Long?>(null) }
    var photoGeometryVersion by remember(sceneKey) { mutableLongStateOf(0L) }
    var visualNowMs by remember(sceneKey) { mutableStateOf(0L) }
    // The controller injected by YujianApp owns playback, stage identity, gates and
    // the monotonic scene clock. Recognition and Result only sample this instance.
    val controller = playbackController
    val sceneIdentity = remember(sceneKey) {
        RecognitionSceneIdentity(
            sessionId = recognitionSessionId.orEmpty(),
            attemptId = recognitionAttemptId.orEmpty(),
            imageId = image?.imageId.orEmpty(),
            photoSha256 = image?.photoSha256,
            photoContentVersion = image?.bitmap?.generationId?.toLong()?.coerceAtLeast(0L) ?: 0L,
        )
    }
    val latestPhotoPresentedAtMs by rememberUpdatedState(photoPresentedAtMs)
    val currentFinishedResult = rememberUpdatedState(finishedResult)
    val currentPipelineError = rememberUpdatedState(pipelineError)
    val currentSubjectStatus = rememberUpdatedState(subjectResult.status)
    val currentRealPhase = rememberUpdatedState(realPhase)
    val currentDelivered = rememberUpdatedState(delivered)
    val currentFailureCallback = rememberUpdatedState(onFailure)
    val currentFinishedCallback = rememberUpdatedState(onFinished)
    val currentFinishedAcceptedCallback = rememberUpdatedState(onFinishedAccepted)
    val currentSubjectGeometryCallback = rememberUpdatedState(onSubjectGeometry)
    suspend fun dispatchFinished(result: ProductionRecognitionResult): Boolean {
        if (delivered || deliveryInProgress) {
            Log.w(LOG_TAG, "RESULT_HANDOFF_REJECTED reason=${if (delivered) "ALREADY_DELIVERED" else "DELIVERY_IN_PROGRESS"} stage=${controller.currentStage}")
            return delivered
        }
        deliveryInProgress = true
        try {
            while (latestPhotoPresentedAtMs == null && currentCoroutineContext().isActive) withFrameNanos { }
            Log.i(LOG_TAG, "RESULT_HANDOFF_ATTEMPT session=${sceneIdentity.sessionId} attempt=${sceneIdentity.attemptId} stage=${controller.currentStage} gate=${controller.gateSnapshot(recognitionMonotonicNowMs())}")
            currentFinishedCallback.value(result)
            val accepted = currentFinishedAcceptedCallback.value(result)
            delivered = accepted
            Log.i(LOG_TAG, "RESULT_HANDOFF_${if (accepted) "ACCEPTED" else "REJECTED"} attempt=${sceneIdentity.attemptId} reason=${if (accepted) "NONE" else "APP_CALLBACK_REJECTED"} stage=${controller.currentStage}")
            return accepted
        } finally {
            deliveryInProgress = false
        }
    }
    val contourClosed = trustedSubjectMask && contour.lastOrNull()?.closesContour == true
    val processingContact = processingPhotoSnapshotForReveal?.contactPointNormalizedSource
    val processingContactIdentityMatches = trustedSubjectMask && processingContact != null &&
        processingPhotoSnapshotForReveal?.contactPointMaskSha256 == subjectResult.maskSha256 &&
        processingPhotoSnapshotForReveal?.contactPointMaskVersion == subjectBitmap?.let { RecognitionMaskIntegrity.versionFromSha256(subjectResult.maskSha256) } &&
        processingPhotoSnapshotForReveal?.contactPointGeometryVersion == photoGeometryVersion &&
        photoGeometryVersion > 0L
    val pStarPathCandidateReady = processingContactIdentityMatches && processingPhotoSnapshotForReveal?.let { photo ->
        RecognitionRevealPathGeometry.fishContact(
            transform = photo.sourceToViewport,
            contactPointNormalizedSource = WaterLightPathPoint(processingContact!!.x, processingContact.y),
            seed = sceneIdentity.sessionId.hashCode() xor sceneIdentity.imageId.hashCode(),
        ) != null
    } == true
    val pStarPathTerminalUnavailable = subjectResult.status == SubjectStatus.READY && trustedSubjectMask &&
        contourClosed && photoGeometryVersion > 0L && processingPhotoSnapshotForReveal != null &&
        !pStarPathCandidateReady && attemptDeadline.isExpired(visualNowMs)
    val revealDecision = RecognitionRevealDecisionGate.decide(
        classifier = when {
            pipelineError != null || realPhase == RecognitionPhase.FAILURE -> RecognitionClassifierTaskState.FAILED
            finishedResult?.ready == true -> RecognitionClassifierTaskState.READY
            else -> RecognitionClassifierTaskState.WAITING
        },
        subject = subjectResult.status,
        maskTrusted = trustedSubjectMask,
        contourValid = trustedSubjectMask && subjectBitmap != null && subjectBox != null && contour.isNotEmpty(),
        contourClosed = contourClosed,
        pStarPathReady = pStarPathCandidateReady,
        pStarPathTerminalUnavailable = pStarPathTerminalUnavailable,
        highConfidenceResult = finishedResult?.let { resolveRecognitionUiState(it) == RecognitionUiState.RESULT_HIGH } == true,
        identityCurrent = !recognitionSessionId.isNullOrBlank() && !recognitionAttemptId.isNullOrBlank() &&
            !image?.imageId.isNullOrBlank() && !image?.photoSha256.isNullOrBlank(),
    )
    val latestRevealDecision = rememberUpdatedState(revealDecision)
    val latestSubjectStatus = rememberUpdatedState(subjectResult.status)
    val latestTrustedMask = rememberUpdatedState(trustedSubjectMask)
    val latestFinishedResult = rememberUpdatedState(finishedResult)
    val diagnosticRevealCallback = rememberUpdatedState(onDiagnosticRevealRequested)
    val manualStepDiagnosticState = rememberUpdatedState(
        stepDiagnosticActive && playbackController.mode == ScenePlaybackMode.MANUAL,
    )
    var diagnosticActionStatus by remember(sceneKey) { mutableStateOf<String?>(null) }
    var evidenceExportStatus by remember(sceneKey) { mutableStateOf("READY") }
    var pendingEvidenceExport by remember(sceneKey) { mutableStateOf<RecognitionEvidenceZipExportResult?>(null) }
    val saveEvidenceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val pending = pendingEvidenceExport
        if (uri == null) {
            evidenceExportStatus = "CANCELLED · 已取消保存，可使用备用分享"
            Log.i(LOG_TAG, "EXPORT_SAVE_CANCELLED stage=${controller.currentStage} attempt=${sceneIdentity.attemptId}")
        } else if (pending == null) {
            evidenceExportStatus = "FAILED · ZIP 已丢失，请重新生成"
            Log.e(LOG_TAG, "EXPORT_FAILED phase=SAVE reason=NO_PENDING_ZIP attempt=${sceneIdentity.attemptId}")
        } else {
            evidenceExportStatus = "SAVING"
            orbEvidenceScope.launch {
                try {
                    RecognitionEvidenceZipExporter.saveToUri(pageContext, pending, uri)
                    evidenceExportStatus = "SAVED · 已保存 ZIP · ${pending.sha256.take(12)}"
                    Log.i(LOG_TAG, "EXPORT_SAVE_COMPLETE stage=${controller.currentStage} attempt=${sceneIdentity.attemptId} sha256=${pending.sha256}")
                } catch (failure: Throwable) {
                    if (failure is CancellationException) throw failure
                    evidenceExportStatus = "FAILED · 保存失败 · ${failure.message ?: failure.javaClass.simpleName}"
                    Log.e(LOG_TAG, "EXPORT_FAILED phase=SAVE stage=${controller.currentStage} attempt=${sceneIdentity.attemptId}", failure)
                }
            }
        }
    }

    LaunchedEffect(
        sceneKey, photoPresentedAtMs, assessment?.primary?.box, finishedResult, subjectResult.status,
        subjectResult.maskSha256, subjectBitmap?.generationId, trustedSubjectMask, contour,
        processingPhotoSnapshotForReveal, photoGeometryVersion,
    ) {
        val detectorReady = assessment?.primary?.box?.let { box ->
            listOf(box.x1, box.y1, box.x2, box.y2).all(Float::isFinite) && box.x1 < box.x2 && box.y1 < box.y2
        } == true
        val classifierReady = finishedResult?.ready == true && finishedResult?.assessment?.primary != null
        val slimSamTerminal = subjectResult.status == SubjectStatus.READY || subjectResult.status == SubjectStatus.FAILED
        val contourValid = trustedSubjectMask && contour.isNotEmpty()
        controller.onDetectorTargetAvailable(detectorReady, sceneIdentity)
        controller.onPhotoTransformAvailable(
            photoGeometryVersion > 0L, sceneIdentity, geometryVersion = photoGeometryVersion,
        )
        controller.updateModelJoin(
            detectorReady = detectorReady,
            classifierReady = classifierReady,
            slimSamTerminal = slimSamTerminal,
            maskTrusted = trustedSubjectMask,
            contourValid = contourValid,
            contourClosed = contourValid && contour.lastOrNull()?.closesContour == true,
            slimSamFailed = subjectResult.status == SubjectStatus.FAILED,
            currentIdentity = sceneIdentity,
            maskSha256 = subjectResult.maskSha256.orEmpty(),
            maskVersion = subjectBitmap?.let { RecognitionMaskIntegrity.versionFromSha256(subjectResult.maskSha256) } ?: -1L,
        )
        val glyphReady = classifierReady && !finishedResult?.prediction?.top1?.speciesName.isNullOrBlank()
        controller.onGlyphAvailable(glyphReady, sceneIdentity)
    }

    LaunchedEffect(sceneKey, revealDecision) {
        Log.i(
            LOG_TAG,
            "REVEAL_DECISION session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                "image=${image?.imageId.orEmpty()} sha256=${image?.photoSha256.orEmpty()} " +
                "classifier=${finishedResult?.let { if (it.ready) "READY" else "FAILED" } ?: "WAITING"} " +
                "slimsam=${subjectResult.status} mask_trusted=$trustedSubjectMask contour_valid=${contour.isNotEmpty()} " +
                "decision=$revealDecision uptime_ms=${SystemClock.uptimeMillis()}",
        )
    }

    LaunchedEffect(
        sceneKey, trustedSubjectMask, contourClosed, photoGeometryVersion,
        processingPhotoSnapshotForReveal, pStarPathCandidateReady, subjectResult.status,
        pStarPathTerminalUnavailable,
    ) {
        when {
            trustedSubjectMask && contour.isNotEmpty() && !contourClosed ->
                maskRuntimeRejectReason = "CONTOUR_NOT_CLOSED"
            trustedSubjectMask && contourClosed && photoGeometryVersion > 0L &&
                processingPhotoSnapshotForReveal != null && !pStarPathCandidateReady &&
                attemptDeadline.isExpired(visualNowMs) ->
                maskRuntimeRejectReason = "PSTAR_PATH_UNAVAILABLE"
        }
    }

    LaunchedEffect(sceneKey) {
        image?.let {
            Log.i(
                LOG_TAG,
                "PHOTO_DECODE_READY session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                    "image=${it.imageId} sha256=${it.photoSha256} source=${it.bitmap.width}x${it.bitmap.height} " +
                    "exif_normalized=true exif_orientation=${it.sourceExifOrientation} orientation_degrees_applied=${it.sourceOrientationDegrees} " +
                    "uptime_ms=${recognitionMonotonicNowMs()}",
            )
        }
    }

    // Start the photo-only SlimSAM work while the detector/classifier runs. The
    // shared attempt deadline remains authoritative; late native results are rejected.
    LaunchedEffect(sceneKey, attemptDeadline) {
        val selected = image ?: return@LaunchedEffect
        val prepare = prepareSubjectCallback.value ?: return@LaunchedEffect
        val remainingMs = attemptDeadline.remainingMs(recognitionMonotonicNowMs())
        if (remainingMs <= 0L) return@LaunchedEffect
        val startedAtMs = recognitionMonotonicNowMs()
        Log.i(LOG_TAG, "SLIMSAM_PREWARM_REQUEST session=${recognitionSessionId.orEmpty()} " +
            "attempt=${recognitionAttemptId.orEmpty()} image=${selected.imageId} remaining_ms=$remainingMs")
        try {
            withTimeout(remainingMs) { prepare(selected) }
            Log.i(LOG_TAG, "SLIMSAM_PREWARM_READY session=${recognitionSessionId.orEmpty()} " +
                "attempt=${recognitionAttemptId.orEmpty()} image=${selected.imageId} " +
                "duration_ms=${recognitionMonotonicNowMs() - startedAtMs}")
        } catch (timeout: TimeoutCancellationException) {
            Log.w(LOG_TAG, "SLIMSAM_PREWARM_DEADLINE session=${recognitionSessionId.orEmpty()} " +
                "attempt=${recognitionAttemptId.orEmpty()} image=${selected.imageId} " +
                "attempt_timeout_ms=${RecognitionAttemptDeadline.DEFAULT_TIMEOUT_MS}")
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Log.w(LOG_TAG, "SLIMSAM_PREWARM_FAILED session=${recognitionSessionId.orEmpty()} " +
                "attempt=${recognitionAttemptId.orEmpty()} image=${selected.imageId}", error)
        }
    }

    // A synchronous ONNX call cannot be interrupted by coroutine cancellation.
    // Keep the visible outcome and SessionStore terminal state on the fixed deadline.
    LaunchedEffect(sceneKey, attemptDeadline) {
        val remainingMs = attemptDeadline.remainingMs(recognitionMonotonicNowMs())
        if (remainingMs > 0L) delay(remainingMs)
        if (!attemptDeadline.isExpired(recognitionMonotonicNowMs())) return@LaunchedEffect
        val completed = currentFinishedResult.value
        if (completed?.ready == true) {
            if (currentSubjectStatus.value == SubjectStatus.IDLE || currentSubjectStatus.value == SubjectStatus.PROCESSING) {
                val failed = FishSubjectResult(
                    SubjectStatus.FAILED,
                    errorCode = RecognitionAttemptFailureCode.SLIMSAM_TIMEOUT,
                )
                Log.e(LOG_TAG, "ATTEMPT_DEADLINE_EXCEEDED stage=SLIMSAM session=${recognitionSessionId.orEmpty()} " +
                    "attempt=${recognitionAttemptId.orEmpty()} image=${image?.imageId.orEmpty()} " +
                    "attempt_timeout_ms=${RecognitionAttemptDeadline.DEFAULT_TIMEOUT_MS} " +
                    "resolution=CLASSIFIER_RESULT_WITHOUT_MASK")
                subjectResult = failed
                currentSubjectGeometryCallback.value(failed, null)
                controller.onMaskTerminal(
                    trusted = false, contourValid = false, contourClosed = false,
                    failedTerminal = true, currentIdentity = sceneIdentity,
                )
            }
            return@LaunchedEffect
        }
        if (completed != null || currentPipelineError.value != null) return@LaunchedEffect
        val failureCode = when (currentRealPhase.value) {
            RecognitionPhase.CLASSIFYING -> RecognitionAttemptFailureCode.CLASSIFIER_TIMEOUT
            else -> RecognitionAttemptFailureCode.DETECTOR_TIMEOUT
        }
        val failure = IllegalStateException(
            "RECOGNITION_ATTEMPT_DEADLINE_EXCEEDED_${failureCode}_" +
                "${RecognitionAttemptDeadline.DEFAULT_TIMEOUT_MS}MS",
        )
        Log.e(LOG_TAG, "ATTEMPT_DEADLINE_EXCEEDED stage=${failureCode.removeSuffix("_TIMEOUT")} " +
            "session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
            "image=${image?.imageId.orEmpty()} attempt_timeout_ms=${RecognitionAttemptDeadline.DEFAULT_TIMEOUT_MS} " +
            "resolution=VISIBLE_FAILURE")
        realPhase = RecognitionPhase.FAILURE
        pipelineError = failure
        controller.onPipelinePhase(RecognitionPhase.FAILURE, recognitionMonotonicNowMs(), currentIdentity = sceneIdentity)
        if (!manualStepDiagnosticState.value && !currentDelivered.value) {
            delivered = true
            currentFailureCallback.value(failure)
        }
    }

    // `subjectBitmap` is borrowed by RecognitionFishFocus through ImageBitmap.
    // Compose may still be drawing the old reference after this state changes or
    // this scene leaves composition, so this UI scope must never recycle it.

    LaunchedEffect(sceneKey, assessment?.primary?.box) {
        val selected = image ?: return@LaunchedEffect
        val primary = assessment?.primary ?: return@LaunchedEffect
        if (generateSubject == null && generateSubjectWithConfidence == null) return@LaunchedEffect
        subjectBitmap = null; subjectBox = null; contour = emptyList(); trustedSubjectMask = false
        maskFileGenerated = false
        maskFileExists = false
        maskBitmapDecodeStatus = "NOT_STARTED"
        maskFileSha256 = null
        maskShaMatchesGeometry = false
        maskPhotoIdentityMatches = false
        maskGeometryMatches = false
        maskRoiDimensions = 0 to 0
        maskRuntimeRejectReason = "SLIMSAM_PENDING"
        pStarBindRejectionReason = null
        subjectResult = FishSubjectResult(SubjectStatus.PROCESSING)
        val startedAtMs = recognitionMonotonicNowMs()
        Log.i(
            LOG_TAG,
            "SLIMSAM_START session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                "image=${selected.imageId} sha256=${selected.photoSha256} bbox=${primary.box} uptime_ms=$startedAtMs",
        )
        val result = try {
            withTimeout(attemptDeadline.remainingMs(recognitionMonotonicNowMs())) {
                generateSubjectWithConfidence?.invoke(selected, primary.box, primary.confidence)
                    ?: generateSubject?.invoke(selected, primary.box)
                    ?: return@withTimeout null
            }
        } catch (cancellation: CancellationException) {
            if (cancellation is TimeoutCancellationException) {
                val failureCode = RecognitionAttemptFailureCode.SLIMSAM_TIMEOUT
                val timeout = IllegalStateException(
                    "RECOGNITION_ATTEMPT_DEADLINE_EXCEEDED_${failureCode}_" +
                        "${RecognitionAttemptDeadline.DEFAULT_TIMEOUT_MS}MS",
                    cancellation,
                )
                Log.e(LOG_TAG, "ATTEMPT_DEADLINE_EXCEEDED stage=SLIMSAM session=${recognitionSessionId.orEmpty()} " +
                    "attempt=${recognitionAttemptId.orEmpty()} image=${selected.imageId} " +
                    "attempt_timeout_ms=${RecognitionAttemptDeadline.DEFAULT_TIMEOUT_MS} remaining_ms=0 " +
                    "failure_code=$failureCode", timeout)
                subjectResult = FishSubjectResult(SubjectStatus.FAILED, errorCode = failureCode)
                onSubjectGeometry(subjectResult, null)
                controller.onMaskTerminal(
                    trusted = false, contourValid = false, contourClosed = false,
                    failedTerminal = true, currentIdentity = sceneIdentity,
                )
                return@LaunchedEffect
            }
            Log.i(LOG_TAG, "SLIMSAM_CANCEL session=${recognitionSessionId.orEmpty()} image=${selected.imageId} duration_ms=${recognitionMonotonicNowMs() - startedAtMs}")
            throw cancellation
        } catch (error: Throwable) {
            Log.e(LOG_TAG, "SLIMSAM_ERROR session=${recognitionSessionId.orEmpty()} image=${selected.imageId}", error)
            null
        }
        currentCoroutineContext().ensureActive()
        if (attemptDeadline.isExpired(recognitionMonotonicNowMs())) {
            val failureCode = RecognitionAttemptFailureCode.SLIMSAM_TIMEOUT
            Log.e(
                LOG_TAG,
                "ATTEMPT_DEADLINE_EXCEEDED stage=SLIMSAM session=${recognitionSessionId.orEmpty()} " +
                    "attempt=${recognitionAttemptId.orEmpty()} image=${selected.imageId} " +
                    "attempt_timeout_ms=${RecognitionAttemptDeadline.DEFAULT_TIMEOUT_MS} " +
                    "failure_code=$failureCode late_native_return=true",
            )
            subjectResult = FishSubjectResult(SubjectStatus.FAILED, errorCode = failureCode)
            onSubjectGeometry(subjectResult, null)
            controller.onMaskTerminal(
                trusted = false, contourValid = false, contourClosed = false,
                failedTerminal = true, currentIdentity = sceneIdentity,
            )
            return@LaunchedEffect
        }
        Log.i(
            LOG_TAG,
            "SLIMSAM_END session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                "image=${selected.imageId} uptime_ms=${recognitionMonotonicNowMs()} duration_ms=${result?.processingMs ?: (recognitionMonotonicNowMs() - startedAtMs)} " +
                "status=${result?.status ?: SubjectStatus.FAILED} quality=${result?.quality} " +
                "mask_area_ratio=${result?.maskAreaRatio} iou=${result?.maskIouScore?.toString() ?: "NOT_AVAILABLE"}",
        )
        // Model READY is not Mask acceptance. Keep the visual join pending until
        // the PNG bytes, geometry and contour have all been checked below.
        val generatedSubject = result ?: FishSubjectResult(SubjectStatus.FAILED, errorCode = "SUBJECT_GENERATION_NULL")
        maskFileGenerated = !result?.bitmapPath.isNullOrBlank()
        maskFileExists = result?.bitmapPath?.let { path -> java.io.File(path).isFile } == true
        maskRoiDimensions = (result?.roiWidth ?: 0) to (result?.roiHeight ?: 0)
        if (result?.status != SubjectStatus.READY || result.bitmapPath.isNullOrBlank()) {
            val rejectCode = result?.errorCode ?: "MASK_FILE_NOT_GENERATED"
            maskRuntimeRejectReason = rejectCode
            subjectResult = generatedSubject.copy(status = SubjectStatus.FAILED, errorCode = rejectCode)
            onSubjectGeometry(subjectResult, null)
            controller.onMaskTerminal(
                trusted = false, contourValid = false, contourClosed = false,
                failedTerminal = true, currentIdentity = sceneIdentity,
            )
            Log.i(
                LOG_TAG,
                "MASK_REJECT session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                    "image=${selected.imageId} sha256=${selected.photoSha256} trust=UNTRUSTED " +
                    "reason=$rejectCode quality=${result?.quality} contours=0",
            )
            logFocusDiagnostic(subjectResult, contour.size, null, false, false)
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(
                result.bitmapPath,
                BitmapFactory.Options().apply { inScaled = false },
            )
        }
        if (loaded == null) {
            maskBitmapDecodeStatus = "MASK_BITMAP_DECODE_FAILED"
            maskRuntimeRejectReason = "MASK_BITMAP_DECODE_FAILED"
            subjectResult = generatedSubject.copy(status = SubjectStatus.FAILED, errorCode = "MASK_BITMAP_DECODE_FAILED")
            onSubjectGeometry(subjectResult, null)
            controller.onMaskTerminal(
                trusted = false, contourValid = false, contourClosed = false,
                failedTerminal = true, currentIdentity = sceneIdentity,
            )
            Log.i(
                LOG_TAG,
                "MASK_REJECT session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                    "image=${selected.imageId} sha256=${selected.photoSha256} trust=UNTRUSTED reason=MASK_BITMAP_DECODE_FAILED contours=0",
            )
            return@LaunchedEffect
        }
        var maskBitmapPublishedToCompose = false
        try {
            currentCoroutineContext().ensureActive()
            val metadata = result.geometry
            maskBitmapDecodeStatus = "DECODED"
            val actualMaskSha256 = withContext(Dispatchers.IO) {
                RecognitionMaskIntegrity.sha256File(java.io.File(requireNotNull(result.bitmapPath)))
            }
            maskFileSha256 = actualMaskSha256
            val maskArtifactRecorded = if (actualMaskSha256 != null) withContext(Dispatchers.IO) {
                recognitionEvidenceRecorder?.recordMaskArtifact(
                    java.io.File(requireNotNull(result.bitmapPath)), loaded, actualMaskSha256, sceneIdentity,
                ) == true
            } else false
            Log.i(LOG_TAG, "MASK_ARTIFACT_RECORDED=$maskArtifactRecorded sha256=${actualMaskSha256 ?: "NOT_AVAILABLE"}")
            val maskShaValid = RecognitionMaskIntegrity.matches(
                declaredSha256 = result.maskSha256,
                geometrySha256 = metadata?.maskSha256,
                fileSha256 = actualMaskSha256,
            )
            maskShaMatchesGeometry = maskShaValid
            val photoIdentityMatches = metadata != null &&
                metadata.sessionId == recognitionSessionId.orEmpty() &&
                metadata.attemptId == recognitionAttemptId.orEmpty() &&
                metadata.imageId == selected.imageId && metadata.photoSha256 == selected.photoSha256
            val geometryMatchesPhoto = metadata?.matches(
                recognitionSessionId.orEmpty(), recognitionAttemptId.orEmpty(),
                selected.imageId, selected.photoSha256, selected.bitmap.width, selected.bitmap.height,
            ) == true
            Log.i(
                LOG_TAG,
                "MASK_IDENTITY_CHECK session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                    "image=${selected.imageId} photoSha=${selected.photoSha256} " +
                    "metadataPresent=${metadata != null} metadataSession=${metadata?.sessionId.orEmpty()} " +
                    "metadataAttempt=${metadata?.attemptId.orEmpty()} metadataImage=${metadata?.imageId.orEmpty()} " +
                    "metadataPhotoSha=${metadata?.photoSha256.orEmpty()} " +
                    "metadataSource=${metadata?.canonicalWidth}x${metadata?.canonicalHeight} " +
                    "selectedSource=${selected.bitmap.width}x${selected.bitmap.height} " +
                    "declaredMaskSha=${result.maskSha256} metadataMaskSha=${metadata?.maskSha256.orEmpty()} " +
                    "actualMaskSha=${actualMaskSha256.orEmpty()} roi=${metadata?.roiPx} " +
                    "decoded=${loaded.width}x${loaded.height} shaMatch=$maskShaValid " +
                    "photoMatch=$photoIdentityMatches geometryMatch=$geometryMatchesPhoto",
            )
            maskPhotoIdentityMatches = photoIdentityMatches
            maskGeometryMatches = geometryMatchesPhoto
            val roiBox = metadata?.takeIf { geometryMatchesPhoto }?.normalizedRoi()
            val roi = roiBox?.let { NormalizedSourceRect(it.x1, it.y1, it.x2, it.y2) }
            val cropDimensionsMatch = loaded.width == result.roiWidth &&
                loaded.height == result.roiHeight &&
                loaded.width == metadata?.roiPx?.width() && loaded.height == metadata?.roiPx?.height()
            val maskShapeValid = result.quality == FishSubjectQuality.GOOD &&
                result.maskSize > 0 && result.maskSize == result.expectedMaskSize &&
                maskShaValid && cropDimensionsMatch && geometryMatchesPhoto && roi != null
            val contourStartedAtMs = recognitionMonotonicNowMs()
            val extractedContour = if (maskShapeValid) {
                try {
                    withContext(Dispatchers.Default) {
                        WaterLightMaskAdapter.toPhotoSpace(requireNotNull(metadata), extractContour(loaded))
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Log.e(LOG_TAG, "MASK_CONTOUR_EXTRACTION_FAILED maskSha=${result.maskSha256}", error)
                    emptyList()
                }
            } else {
                emptyList()
            }
            Log.i(LOG_TAG, "MASK_CONTOUR_EXTRACTION maskSha=${result.maskSha256} " +
                "input=${loaded.width}x${loaded.height} attempted=$maskShapeValid " +
                "segments=${extractedContour.size} closed=${extractedContour.lastOrNull()?.closesContour == true} " +
                "durationMs=${recognitionMonotonicNowMs() - contourStartedAtMs}")
            currentCoroutineContext().ensureActive()
            val geometry = if (maskShapeValid && extractedContour.isNotEmpty()) RecognitionSubjectGeometrySnapshot(
                imageId = selected.imageId,
                recognitionSessionId = recognitionSessionId.orEmpty(),
                recognitionAttemptId = recognitionAttemptId.orEmpty(),
                photoSha256 = selected.photoSha256,
                sourceWidthPx = selected.bitmap.width,
                sourceHeightPx = selected.bitmap.height,
                detectorBox = primary.box,
                detectorConfidence = primary.confidence,
                subjectBox = requireNotNull(roi),
                contour = extractedContour,
                contourSourceSpace = "CANONICAL_PHOTO_NORMALIZED",
                quality = result.quality,
                maskSize = result.maskSize,
                expectedMaskSize = result.expectedMaskSize,
                maskVersion = RecognitionMaskIntegrity.versionFromSha256(result.maskSha256),
                maskSha256 = result.maskSha256,
            ) else null
            val trustState = RecognitionRevealPolicy.geometryTrust(
                detectorBox = primary.box,
                subject = geometry,
                imageId = selected.imageId,
                sourceWidthPx = selected.bitmap.width,
                sourceHeightPx = selected.bitmap.height,
                detectorConfidence = primary.confidence,
                recognitionSessionId = recognitionSessionId,
                recognitionAttemptId = recognitionAttemptId,
                photoSha256 = selected.photoSha256,
            )
            val isTrustedForCurrentPhoto = trustState == RecognitionGeometryTrustState.TRUSTED_MASK &&
                !recognitionSessionId.isNullOrBlank() && !recognitionAttemptId.isNullOrBlank() &&
                selected.imageId.isNotBlank() && selected.photoSha256.isNotBlank()
            // Use the actually presented phase as the acceptance boundary. Calling
            // controller.current() here would advance the visual state from the
            // asynchronous SlimSAM coroutine and desynchronize the shared clock.
            val maskAcceptancePhase = if (diagnosticModeState.value) {
                // Manual inspection must not close the mask window while the real model runs.
                RecognitionPhase.OUTLINE
            } else if (latestPhotoPresentedAtMs == null) {
                RecognitionPhase.CAPTURED
            } else {
                controller.presentedPhase()
            }
            // Trust is a property of the actual Mask and photo identity, not of the UI phase.
            // A late SlimSAM result is still valid for the same attempt and Result handoff.
            val trustedForReveal = isTrustedForCurrentPhoto
            if (trustedForReveal) {
                subjectBitmap = loaded
                maskBitmapPublishedToCompose = true
                subjectBox = requireNotNull(roiBox)
                contour = extractedContour
                trustedSubjectMask = true
                val acceptedAtMs = recognitionMonotonicNowMs()
                latestPhotoPresentedAtMs?.let { photoStartMs ->
                    Log.i(
                        LOG_TAG,
                        "MASK_TRUSTED_ACCEPT session=${recognitionSessionId.orEmpty()} " +
                            "attempt=${recognitionAttemptId.orEmpty()} image=${selected.imageId} " +
                            "acceptance_phase=$maskAcceptancePhase uptime_ms=$acceptedAtMs " +
                            "processing_elapsed_ms=${acceptedAtMs - photoStartMs}",
                    )
                }
            }
            if (trustedForReveal) {
                Log.i(
                    LOG_TAG,
                    "MASK_CONTOUR_READY session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                        "image=${selected.imageId} sha256=${selected.photoSha256} points=${extractedContour.size} trust=TRUSTED_MASK",
                )
            }
            val maskRejectReason = when {
                result.quality == FishSubjectQuality.WARNING -> "MASK_QUALITY_WARNING"
                result.quality != FishSubjectQuality.GOOD -> "MASK_QUALITY_${result.quality}"
                result.maskSize <= 0 || result.maskSize != result.expectedMaskSize -> "MASK_SIZE_MISMATCH"
                !maskShaValid -> "MASK_SHA_MISMATCH"
                !photoIdentityMatches -> "MASK_IDENTITY_MISMATCH"
                !geometryMatchesPhoto -> "MASK_GEOMETRY_MISMATCH"
                !cropDimensionsMatch -> "MASK_SIZE_MISMATCH"
                extractedContour.isEmpty() -> "EMPTY_CONTOUR"
                extractedContour.lastOrNull()?.closesContour != true -> "CONTOUR_NOT_CLOSED"
                recognitionSessionId.isNullOrBlank() || recognitionAttemptId.isNullOrBlank() -> "SESSION_IDENTITY_MISSING"
                selected.imageId.isBlank() || selected.photoSha256.isBlank() -> "PHOTO_IDENTITY_MISSING"
                trustState != RecognitionGeometryTrustState.TRUSTED_MASK -> "MASK_GEOMETRY_MISMATCH"
                else -> "NONE"
            }
            maskRuntimeRejectReason = maskRejectReason
            subjectResult = if (trustedForReveal) generatedSubject else generatedSubject.copy(
                status = SubjectStatus.FAILED, errorCode = maskRejectReason,
            )
            onSubjectGeometry(subjectResult, geometry.takeIf { trustedForReveal })
            if (!trustedForReveal) {
                controller.onMaskTerminal(
                    trusted = false, contourValid = false, contourClosed = false,
                    failedTerminal = true,
                    currentIdentity = sceneIdentity,
                )
                Log.i(
                    LOG_TAG,
                    "MASK_REJECT session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                        "image=${selected.imageId} sha256=${selected.photoSha256} quality=${result.quality} " +
                        "mask=${result.maskSize}/${result.expectedMaskSize} roi=${loaded.width}x${loaded.height} " +
                        "mask_area_ratio=${result.maskAreaRatio} iou=${result.maskIouScore?.toString() ?: "NOT_AVAILABLE"} " +
                        "contours=0 acceptance_phase=$maskAcceptancePhase trust=${RecognitionGeometryTrustState.UNTRUSTED} " +
                        "reject_reason=$maskRejectReason",
                )
            }
        logFocusDiagnostic(result, if (trustedForReveal) extractedContour.size else 0, primary.box, false, false)
        } finally {
            // This decoded bitmap is still local unless it was handed to Compose.
            // Only the unpublished temporary is ours to release here.
            if (!maskBitmapPublishedToCompose) loaded.recycle()
        }
    }

    LaunchedEffect(sceneKey, photoPresentedAtMs) {
        val selected = image ?: return@LaunchedEffect
        val startedAtMs = photoPresentedAtMs ?: return@LaunchedEffect
        visualPhase = RecognitionPhase.CAPTURED
        productionSceneStage = RecognitionSceneStage.PHOTO_STABLE
        productionStageElapsedMs = 0L
        productionStageProgress = 0f
        visualNowMs = startedAtMs
        if (realPhase != RecognitionPhase.CAPTURED) {
            controller.onPipelinePhase(
                phase = realPhase,
                nowMs = startedAtMs,
                hasRealFishFocus = assessment?.primary?.box != null,
                currentIdentity = sceneIdentity,
            )
        }
        if (finishedResult?.ready == true) {
            controller.onPipelinePhase(
                RecognitionPhase.RESULT,
                startedAtMs,
                hasRealFishFocus = finishedResult?.assessment?.primary?.box != null,
                currentIdentity = sceneIdentity,
            )
        }
        Log.i(
            LOG_TAG,
            "PROCESSING_P0 session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                "image=${selected.imageId} sha256=${selected.photoSha256} source=${selected.bitmap.width}x${selected.bitmap.height} " +
                "exif_normalized=true exif_orientation=${selected.sourceExifOrientation} orientation_degrees_applied=${selected.sourceOrientationDegrees} uptime_ms=$startedAtMs " +
                "model_phase_at_photo_start=$realPhase model_ready_at_photo_start=${finishedResult?.ready == true}",
        )
        if (phaseOverride == null) phasePresentedCallback(RecognitionPhase.CAPTURED, startedAtMs)
    }

    // Real inference starts as soon as the PhotoSession is ready and runs beside
    // the photo's settling motion; its elapsed time never consumes visual time.
    LaunchedEffect(sceneKey) {
        if (image == null) {
            onFailure(IllegalStateException("recognition image is unavailable"))
            return@LaunchedEffect
        }
        val selected = image
        Log.i(
            LOG_TAG,
            "PIPELINE_START session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                "image=${selected.imageId} sha256=${selected.photoSha256} uptime_ms=${recognitionMonotonicNowMs()}",
        )
        assessment = null
        var activeModelTimeoutCode = RecognitionAttemptFailureCode.DETECTOR_TIMEOUT
        val result = try {
            var lastPipelinePhase: RecognitionPhase? = null
            var detectorStartedAtMs: Long? = null
            var classifierStartedAtMs: Long? = null
            withTimeout(attemptDeadline.remainingMs(recognitionMonotonicNowMs())) {
            recognize { progress ->
                assessment = progress.assessment ?: assessment
                realPhase = progress.phase
                if (progress.phase != lastPipelinePhase) {
                    lastPipelinePhase = progress.phase
                    val eventAtMs = recognitionMonotonicNowMs()
                    Log.i(
                        LOG_TAG,
                        "PIPELINE_PHASE session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                            "image=${image.imageId} sha256=${image.photoSha256} phase=${progress.phase} uptime_ms=$eventAtMs",
                    )
                    when (progress.phase) {
                        RecognitionPhase.DETECTING -> {
                            activeModelTimeoutCode = RecognitionAttemptFailureCode.DETECTOR_TIMEOUT
                            detectorStartedAtMs = eventAtMs
                            Log.i(
                                LOG_TAG,
                                "DETECTOR_START session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                                    "image=${image.imageId} uptime_ms=$eventAtMs",
                            )
                        }
                        RecognitionPhase.OUTLINE -> {
                            detectorStartedAtMs?.let { started ->
                                Log.i(
                                    LOG_TAG,
                                    "DETECTOR_END session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                                        "image=${image.imageId} uptime_ms=$eventAtMs duration_ms=${eventAtMs - started}",
                                )
                            }
                        }
                        RecognitionPhase.CLASSIFYING -> {
                            activeModelTimeoutCode = RecognitionAttemptFailureCode.CLASSIFIER_TIMEOUT
                            if (classifierStartedAtMs == null) classifierStartedAtMs = eventAtMs
                            Log.i(
                                LOG_TAG,
                                "CLASSIFIER_START session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                                    "image=${image.imageId} uptime_ms=$eventAtMs",
                            )
                        }
                        RecognitionPhase.RESULT -> classifierStartedAtMs?.let { started ->
                            Log.i(
                                LOG_TAG,
                                "CLASSIFIER_END session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                                    "image=${image.imageId} uptime_ms=$eventAtMs duration_ms=${eventAtMs - started}",
                            )
                        }
                        RecognitionPhase.FAILURE -> {
                            if (classifierStartedAtMs == null) {
                                detectorStartedAtMs?.let { started ->
                                    Log.i(
                                        LOG_TAG,
                                        "DETECTOR_END session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                                            "image=${image.imageId} uptime_ms=$eventAtMs duration_ms=${eventAtMs - started} terminal=true",
                                    )
                                }
                            } else {
                                classifierStartedAtMs?.let { started ->
                                    Log.i(
                                        LOG_TAG,
                                        "CLASSIFIER_END session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                                            "image=${image.imageId} uptime_ms=$eventAtMs duration_ms=${eventAtMs - started} terminal=true",
                                    )
                                }
                            }
                        }
                        RecognitionPhase.CAPTURED -> Unit
                    }
                }
                if (latestPhotoPresentedAtMs != null) {
                    controller.onPipelinePhase(
                        phase = progress.phase,
                        nowMs = recognitionMonotonicNowMs(),
                        hasRealFishFocus = assessment?.primary?.box != null,
                        currentIdentity = sceneIdentity,
                    )
                }
            }
            }
        } catch (timeout: TimeoutCancellationException) {
            val stage = activeModelTimeoutCode.removeSuffix("_TIMEOUT")
            Log.e(LOG_TAG, "ATTEMPT_DEADLINE_EXCEEDED stage=$stage session=${recognitionSessionId.orEmpty()} " +
                "attempt=${recognitionAttemptId.orEmpty()} image=${image.imageId} " +
                "attempt_timeout_ms=${RecognitionAttemptDeadline.DEFAULT_TIMEOUT_MS} " +
                "remaining_ms=${attemptDeadline.remainingMs(recognitionMonotonicNowMs())} " +
                "failure_code=$activeModelTimeoutCode", timeout)
            val failure = IllegalStateException(
                "RECOGNITION_ATTEMPT_DEADLINE_EXCEEDED_${activeModelTimeoutCode}_" +
                    "${RecognitionAttemptDeadline.DEFAULT_TIMEOUT_MS}MS",
                timeout,
            )
            realPhase = RecognitionPhase.FAILURE
            pipelineError = failure
            controller.onPipelinePhase(RecognitionPhase.FAILURE, recognitionMonotonicNowMs(), currentIdentity = sceneIdentity)
            if (!manualStepDiagnosticState.value && !delivered) {
                delivered = true
                onFailure(failure)
            }
            return@LaunchedEffect
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            val failureCode = (error as? RecognitionStageFailureException)?.failureCode?.name
                ?: RecognitionFailureCode.MODEL_INFERENCE_FAILED.name
            Log.e(LOG_TAG, "Recognition runtime failed failure_code=$failureCode", error)
            realPhase = RecognitionPhase.FAILURE
            pipelineError = error
            if (latestPhotoPresentedAtMs != null) {
                controller.onPipelinePhase(RecognitionPhase.FAILURE, recognitionMonotonicNowMs(), currentIdentity = sceneIdentity)
            }
            if (!manualStepDiagnosticState.value && !delivered) {
                delivered = true
                onFailure(error)
            }
            return@LaunchedEffect
        }
        if (attemptDeadline.isExpired(recognitionMonotonicNowMs())) {
            val failureCode = activeModelTimeoutCode
            val failure = IllegalStateException(
                "RECOGNITION_ATTEMPT_DEADLINE_EXCEEDED_${failureCode}_" +
                    "${RecognitionAttemptDeadline.DEFAULT_TIMEOUT_MS}MS_LATE_NATIVE_RETURN",
            )
            Log.e(LOG_TAG, "ATTEMPT_DEADLINE_EXCEEDED stage=${failureCode.removeSuffix("_TIMEOUT")} " +
                "session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                "image=${image.imageId} late_native_return=true failure_code=$failureCode", failure)
            realPhase = RecognitionPhase.FAILURE
            pipelineError = failure
            controller.onPipelinePhase(RecognitionPhase.FAILURE, recognitionMonotonicNowMs(), currentIdentity = sceneIdentity)
            if (!manualStepDiagnosticState.value && !delivered) {
                delivered = true
                onFailure(failure)
            }
            return@LaunchedEffect
        }
        assessment = result.assessment; finishedResult = result
        if (result.ready) {
            realPhase = RecognitionPhase.RESULT
            if (latestPhotoPresentedAtMs != null) {
                controller.onPipelinePhase(
                    RecognitionPhase.RESULT,
                    recognitionMonotonicNowMs(),
                    hasRealFishFocus = result.assessment.primary?.box != null,
                    currentIdentity = sceneIdentity,
                )
            }
        } else if (!delivered && !manualStepDiagnosticState.value) {
            realPhase = RecognitionPhase.FAILURE
            controller.onPipelinePhase(RecognitionPhase.FAILURE, recognitionMonotonicNowMs(), currentIdentity = sceneIdentity)
            while (isActive && !delivered && !dispatchFinished(result)) delay(250L)
        }
    }

    LaunchedEffect(displayMode, playbackRevision, pipelineError, finishedResult) {
        if (manualStepDiagnosticState.value || delivered || deliveryInProgress) return@LaunchedEffect
        pipelineError?.let { error ->
            delivered = true
            onFailure(error)
            return@LaunchedEffect
        }
        finishedResult?.takeIf { !it.ready }?.let { result ->
            while (isActive && !delivered && !dispatchFinished(result)) delay(250L)
        }
    }

    // A real MEDIUM/LOW classifier result is a completed business outcome. Route it
    // through the normal Result transaction without advancing or fabricating visual stages.
    LaunchedEffect(sceneKey, finishedResult, revealDecision, photoPresentedAtMs, delivered, displayMode) {
        if (manualStepDiagnosticState.value || delivered || deliveryInProgress) return@LaunchedEffect
        val result = finishedResult ?: return@LaunchedEffect
        if (!result.ready || revealDecision != RecognitionRevealDecisionState.STATIC_RESULT) return@LaunchedEffect
        while (isActive && !delivered && !dispatchFinished(result)) delay(250L)
    }

    // The visual clock must remain stable while the real detector/classifier advances.
    // realPhase is intentionally NOT a LaunchedEffect key: restarting this loop on every
    // progress callback can cancel the presentation before it reaches RESULT.
    LaunchedEffect(sceneKey, phaseOverride, photoPresentedAtMs, displayMode, diagnosticReturnRevision) {
        if (stepDiagnosticActive && playbackController.mode == ScenePlaybackMode.MANUAL &&
            finishedResult?.ready == true
        ) delivered = false
        if (photoPresentedAtMs == null && phaseOverride == null) return@LaunchedEffect
        var lastGateReason: String? = null
        var lastGateLogMs = 0L
        var stalledStage: RecognitionSceneStage? = null
        try {
            while (isActive && phaseOverride == null && !delivered) {
                withFrameNanos { _ ->
                    // Use one uptime-based source for phase events and all visual layers.
                    visualNowMs = recognitionMonotonicNowMs()
                }
                val currentSubjectStatus = latestSubjectStatus.value
                val subjectTerminal = currentSubjectStatus == SubjectStatus.READY ||
                    currentSubjectStatus == SubjectStatus.FAILED
                val currentResult = latestFinishedResult.value
                val successfulRevealReady = currentResult?.let { result ->
                    result.ready && resolveRecognitionUiState(result) == RecognitionUiState.RESULT_HIGH
                } == true
                val previousSceneStage = productionSceneStage
                val nextSceneStage = controller.currentSceneStage(
                    visualNowMs,
                    trustedMask = latestTrustedMask.value,
                    subjectTerminal = subjectTerminal,
                    successfulRevealAllowed = successfulRevealReady,
                )
                productionSceneStage = nextSceneStage
                productionStageElapsedMs = controller.stageElapsedMs(visualNowMs)
                productionStageProgress = controller.stageProgress(visualNowMs)
                if (controller.mode == ScenePlaybackMode.AUTO) {
                    val gate = controller.gateSnapshot(visualNowMs, successfulRevealReady)
                    @Suppress("UNCHECKED_CAST")
                    val missing = gate["unmetConditions"] as List<String>
                    val blocked = missing.isNotEmpty() && productionStageElapsedMs >= (gate["minVisibleMs"] as Long)
                    val reason = if (blocked) "${nextSceneStage.name}:${missing.joinToString(",")}" else null
                    if (reason != null && (reason != lastGateReason || visualNowMs - lastGateLogMs >= 5_000L)) {
                        val payload = gate + mapOf("sessionId" to sceneIdentity.sessionId,
                            "attemptId" to sceneIdentity.attemptId, "imageId" to sceneIdentity.imageId)
                        Log.w(LOG_TAG, "SCENE_GATE_BLOCKED $payload")
                        recognitionEvidenceRecorder?.recordGateDiagnostic("SCENE_GATE_BLOCKED", payload)
                        lastGateLogMs = visualNowMs
                    }
                    if (reason != null && gate["modelTerminal"] == true && productionStageElapsedMs >= 8_000L && stalledStage != nextSceneStage) {
                        val payload = gate + mapOf("lastAcceptedEvent" to controller.acceptedEnergyEventsSnapshot().lastOrNull()?.toString(),
                            "lastRejectedEvent" to controller.lastRejectedEnergyEvent?.toString())
                        Log.e(LOG_TAG, "STAGE_GATE_STALL $payload")
                        recognitionEvidenceRecorder?.recordGateDiagnostic("STAGE_GATE_STALL", payload)
                        stalledStage = nextSceneStage
                    }
                    if (reason == null) stalledStage = null
                    lastGateReason = reason
                }
                val nextVisualPhase = controller.presentedPhase()
                if (nextSceneStage != previousSceneStage) {
                    val stageElapsed = controller.stageElapsedMs(visualNowMs)
                    val referenceMs = controller.opticalReferenceElapsedMs(visualNowMs)
                    val sessionName = recognitionSessionId.orEmpty()
                    val attemptName = recognitionAttemptId.orEmpty()
                    val imageName = image?.imageId.orEmpty()
                    Log.i(
                        LOG_TAG,
                        "VISUAL_STAGE session=$sessionName attempt=$attemptName image=$imageName " +
                            "stage=${previousSceneStage.name}->${nextSceneStage.name} " +
                            "stage_started_uptime_ms=$visualNowMs stage_elapsed_ms=$stageElapsed " +
                            "optical_reference_ms=$referenceMs trusted_mask=" + latestTrustedMask.value + " real_phase=$realPhase",
                    )
                }
                if (nextVisualPhase != visualPhase) {
                    visualPhase = nextVisualPhase
                    phasePresentedCallback(nextVisualPhase, visualNowMs)
                }
                val fishPathReady = latestRevealDecision.value != RecognitionRevealDecisionState.FISH_FOCUS_READY ||
                    controller.fishPathSnapshotReady()
                if (nextSceneStage == RecognitionSceneStage.ORB_RETURN && latestFinishedResult.value?.ready == true &&
                    controller.resultVisualReady(visualNowMs) && fishPathReady && !delivered && !diagnosticStepDisplayState.value) {
                    // The adaptive beats and real model result are both ready. Result
                    // continues from this same controller frame at ORB_RETURN.
                    Log.i(LOG_TAG, "delivering RESULT_READY at monotonic=$visualNowMs")
                    Log.i(LOG_TAG, "ORB_COMPLETION_START image=${image?.imageId} sha256=${image?.photoSha256}")
                    val accepted = dispatchFinished(requireNotNull(latestFinishedResult.value))
                    if (accepted) break
                    Log.w(LOG_TAG, "RESULT_HANDOFF_REJECTED; retrying while the same Attempt remains active")
                    delay(250L)
                }
            }
        } finally {
            Log.i(
                LOG_TAG,
                "visual loop ended active=$isActive delivered=$delivered override=$phaseOverride " +
                    "visual=$visualPhase real=$realPhase finishedReady=${finishedResult?.ready}",
            )
        }
    }

    val visualClockMs = visualClockOverrideMs ?: visualNowMs
    val deterministicMotion = visualClockOverrideMs != null
    val processingElapsedMs = if (deterministicMotion) {
        visualClockMs.coerceAtLeast(0L)
    } else {
        photoPresentedAtMs?.let { (visualClockMs - it).coerceAtLeast(0L) } ?: 0L
    }
    val authoritativeOpticalReferenceElapsedMs = when {
        deterministicMotion || phaseOverride != null -> visualClockMs.coerceAtLeast(0L)
        photoPresentedAtMs == null -> 0L
        else -> controller.opticalReferenceElapsedMs(
            visualNowMs,
            trustedMask = trustedSubjectMask,
            subjectTerminal = subjectResult.status == SubjectStatus.READY || subjectResult.status == SubjectStatus.FAILED,
        )
    }
    val opticalReferenceElapsedMs = if (deterministicMotion || phaseOverride != null) {
        authoritativeOpticalReferenceElapsedMs
    } else {
        controller.opticalReferenceElapsedMs(visualNowMs, trustedMask = trustedSubjectMask,
            subjectTerminal = subjectResult.status == SubjectStatus.READY || subjectResult.status == SubjectStatus.FAILED)
    }
    val sceneElapsedMs = if (deterministicMotion || phaseOverride != null) visualClockMs.coerceAtLeast(0L)
        else controller.sceneElapsedMs(visualNowMs)

    fun changeDiagnosticDisplayMode(mode: RecognitionProgressDisplayMode) {
        if (mode == displayMode) return
        // Display selection is presentation-only. The shared AUTO/MANUAL motion
        // controller changes only when the user presses a playback control.
        if (onDiagnosticDisplayModeChange != null) onDiagnosticDisplayModeChange.invoke(mode)
        else localDisplayMode = mode
        playbackRevision++
        diagnosticActionStatus = null
        Log.i(LOG_TAG, "DIAGNOSTIC_DISPLAY_MODE previous=$displayMode next=$mode reference_ms=$authoritativeOpticalReferenceElapsedMs " +
            "session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
            "image=${image?.imageId.orEmpty()} sha256=${image?.photoSha256.orEmpty()}")
    }
    // Photo settling, Orb entrance and every optical host are projections of one sampled time.
    val entranceElapsedMs = RecognitionSceneTimelineProjection.orbEntranceElapsedMs(sceneElapsedMs)
    val orbStage = when {
        entranceElapsedMs < 150L -> "BUTTON_DISSOLVE"
        entranceElapsedMs < 350L -> "WATER_CORE_FORM"
        entranceElapsedMs < 650L -> "RIBBON_OPEN"
        entranceElapsedMs < 900L -> "ARRIVING"
        else -> "UNDERSTANDING"
    }
    LaunchedEffect(sceneKey, orbStage) {
        Log.i(LOG_TAG, "ORB_STAGE image=${image?.imageId} sha256=${image?.photoSha256} stage=$orbStage elapsed_ms=$entranceElapsedMs")
    }
    val authoritativeSceneFrame = if (!deterministicMotion && phaseOverride == null && photoPresentedAtMs != null) {
        controller.currentFrame(sceneIdentity, visualNowMs, photoGeometryVersion, motionPolicy.reduceMotion)
    } else null
    val sceneBaseFrame = WaterLightFrame(
        sessionId = recognitionSessionId.orEmpty(), photoSha256 = image?.photoSha256,
        elapsedMs = sceneElapsedMs, opticalReferenceElapsedMs = opticalReferenceElapsedMs,
        phase = WaterLightPhase.PROCESSING, completion = orbCompletion.coerceIn(0f, 1f),
        reducedMotion = motionPolicy.reduceMotion,
        geometryVersion = photoGeometryVersion,
        attemptId = recognitionAttemptId.orEmpty(), imageId = image?.imageId.orEmpty(),
        photoContentVersion = image?.bitmap?.generationId?.toLong() ?: 0L,
        playbackMode = playbackMode,
        playbackPaused = playbackPaused,
        eventSequence = authoritativeSceneFrame?.eventSequence ?: 0L,
        sceneFrameOverride = authoritativeSceneFrame,
        sceneStageOverride = if (stepDiagnosticActive || phaseOverride != null || deterministicMotion) null else productionSceneStage,
        sceneStageElapsedMsOverride = if (stepDiagnosticActive || phaseOverride != null || deterministicMotion) null else productionStageElapsedMs,
        sceneStageProgressOverride = if (stepDiagnosticActive || phaseOverride != null || deterministicMotion) null else productionStageProgress,
    )
    val diagnosticSceneFrame = authoritativeSceneFrame ?: sceneBaseFrame.sceneFrame
    val latestEvidenceSceneFrame = rememberUpdatedState(diagnosticSceneFrame)
    LaunchedEffect(sceneKey, diagnosticSceneFrame.stage) {
        Log.i(
            LOG_TAG,
            "SCENE_STAGE_ENTER session=${diagnosticSceneFrame.identity.sessionId} " +
                "attempt=${diagnosticSceneFrame.identity.attemptId} image=${diagnosticSceneFrame.identity.imageId} " +
                "sha256=${diagnosticSceneFrame.identity.photoSha256.orEmpty()} " +
                "stage=${diagnosticSceneFrame.stage.ordinal + 1}/13 name=${diagnosticSceneFrame.stage.name} " +
                "reference_ms=${diagnosticSceneFrame.opticalReferenceElapsedMs} uptime_ms=${diagnosticSceneFrame.monotonicTimestampMs} " +
                "playback=${diagnosticSceneFrame.playbackMode} " +
                "paused=${diagnosticSceneFrame.paused} detector=${assessment?.status} " +
                "bbox=${assessment?.primary?.box} slimsam=${subjectResult.status} trusted_mask=$trustedSubjectMask",
        )
    }
    LaunchedEffect(sceneKey, recognitionEvidenceRecorder) {
        while (isActive) {
            recognitionEvidenceRecorder?.recordHeartbeat(latestEvidenceSceneFrame.value)
            delay(1_000L)
        }
    }
    LaunchedEffect(
        sceneKey,
        recognitionEvidenceRecorder,
        finishedResult,
        assessment?.primary?.box,
        subjectResult.status,
        subjectResult.maskSha256,
        trustedSubjectMask,
        contour.size,
        maskFileGenerated,
        maskFileExists,
        maskBitmapDecodeStatus,
        maskFileSha256,
        maskShaMatchesGeometry,
        maskPhotoIdentityMatches,
        maskGeometryMatches,
        maskRuntimeRejectReason,
        pStarBindRejectionReason,
        pStarPathId,
        revealDecision,
        diagnosticSceneFrame.stage,
    ) {
        val recorder = recognitionEvidenceRecorder ?: return@LaunchedEffect
        val result = finishedResult
        val detector = result?.detectorRun
        val prediction = result?.prediction
        val geometry = subjectResult.geometry
        recorder.recordModelState(
            stage = diagnosticSceneFrame.stage,
            sceneElapsedMs = diagnosticSceneFrame.sceneElapsedMs,
            detectorStatus = when {
                detector != null -> "READY"
                realPhase == RecognitionPhase.FAILURE -> "FAILED"
                else -> "WAITING"
            },
            classifierStatus = when {
                prediction != null -> "READY"
                result != null -> "FAILED"
                else -> "WAITING"
            },
            slimSamStatus = subjectResult.status.name,
            maskStatus = if (trustedSubjectMask) "TRUSTED" else "BBOX_ONLY",
            waitingReason = diagnosticSceneFrame.modelGates.waitingReason,
            extra = mapOf(
                "detectorVersion" to (detector?.modelVersion ?: "NOT_RECORDED"),
                "detectorSha256" to (detector?.onnxSha256 ?: "NOT_RECORDED"),
                "detectorInputSize" to detector?.inputSize,
                "detectorInputTensorShape" to detector?.inputTensorShape,
                "detectorOutputTensorShape" to detector?.outputTensorShape,
                "detectorDrawSize" to detector?.let { listOf(it.inputDrawWidth, it.inputDrawHeight) },
                "orientationRetryPolicy" to (detector?.retryPolicyVersion ?: "NOT_RECORDED"),
                "orientationAttempts" to detector?.attemptTrace?.map {
                    mapOf(
                        "orientationAttempt" to it.orientationAttempt,
                        "detectionCount" to it.detectionCount,
                        "topConfidence" to it.topConfidence,
                        "qualityStatus" to it.qualityStatus,
                        "qualityLevel" to it.qualityLevel,
                    )
                },
                "orientationSelectedAttempt" to (detector?.selectedAttempt ?: "NOT_RECORDED"),
                "bbox" to assessment?.primary?.box?.let { listOf(it.x1, it.y1, it.x2, it.y2) },
                "crop" to assessment?.cropBox?.let { listOf(it.x1, it.y1, it.x2, it.y2) },
                "cropPixelCount" to result?.cropPixels?.size,
                "classifierVersion" to (prediction?.modelVersion ?: "NOT_RECORDED"),
                "classifierSha256" to (prediction?.modelSha256 ?: "NOT_RECORDED"),
                "classifierInputSize" to prediction?.modelInputBitmap?.let { listOf(it.width, it.height) },
                "top1" to (prediction?.top1?.speciesName ?: "NOT_RECORDED"),
                "confidence" to prediction?.top1?.confidence,
                "slimSamModelSha256" to (geometry?.modelSha256 ?: "NOT_RECORDED"),
                "maskVersion" to subjectBitmap?.let { RecognitionMaskIntegrity.versionFromSha256(subjectResult.maskSha256) },
                "maskGeometryVersion" to diagnosticSceneFrame.geometryVersion,
                "maskFallbackReason" to if (trustedSubjectMask) "NONE" else (subjectResult.errorCode ?: "MASK_UNTRUSTED"),
                "contourPointCount" to contour.size,
                "slimSamInferenceStatus" to subjectResult.status.name,
                "maskFileGenerated" to maskFileGenerated,
                "maskFileExists" to maskFileExists,
                "maskBitmapDecodeStatus" to maskBitmapDecodeStatus,
                "maskSha256" to subjectResult.maskSha256.ifBlank { "NOT_RECORDED" },
                "maskFileSha256" to maskFileSha256,
                "maskShaMatchesGeometry" to maskShaMatchesGeometry,
                "maskQuality" to subjectResult.quality?.name,
                "maskRoiDimensions" to listOf(maskRoiDimensions.first, maskRoiDimensions.second),
                "photoIdentityMatch" to maskPhotoIdentityMatches,
                "geometryMatch" to (maskGeometryMatches && diagnosticSceneFrame.geometryVersion > 0L),
                "contourClosed" to (contour.lastOrNull()?.closesContour == true),
                "maskTrusted" to trustedSubjectMask,
                "revealDecision" to revealDecision.name,
                "recognitionStage" to diagnosticSceneFrame.stage.name,
                "maskRejectReason" to maskRuntimeRejectReason,
                "pStarBindRejectionReason" to pStarBindRejectionReason,
                "pStarNormalizedSource" to processingContact?.let { listOf(it.x, it.y) },
                "pStarMaskSha256" to processingPhotoSnapshotForReveal?.contactPointMaskSha256,
                "pStarMaskVersion" to processingPhotoSnapshotForReveal?.contactPointMaskVersion,
                "pStarGeometryVersion" to processingPhotoSnapshotForReveal?.contactPointGeometryVersion,
                "pStarPathId" to pStarPathId,
            ),
        )
        recorder.recordMaskRuntime(
            status = subjectResult.status.name,
            trusted = trustedSubjectMask,
            maskSha256 = subjectResult.maskSha256.takeIf(String::isNotBlank),
            maskVersion = subjectBitmap?.let { RecognitionMaskIntegrity.versionFromSha256(subjectResult.maskSha256) },
            geometryVersion = diagnosticSceneFrame.geometryVersion,
            fallbackReason = if (trustedSubjectMask) null else subjectResult.errorCode ?: "MASK_UNTRUSTED",
            extra = mapOf(
                "slimSamInferenceStatus" to subjectResult.status.name,
                "maskFileGenerated" to maskFileGenerated,
                "maskFileExists" to maskFileExists,
                "maskBitmapDecodeStatus" to maskBitmapDecodeStatus,
                "maskFileSha256" to maskFileSha256,
                "maskShaMatchesGeometry" to maskShaMatchesGeometry,
                "quality" to subjectResult.quality?.name,
                "maskSize" to subjectResult.maskSize,
                "expectedMaskSize" to subjectResult.expectedMaskSize,
                "roiDimensions" to listOf(maskRoiDimensions.first, maskRoiDimensions.second),
                "photoIdentityMatch" to maskPhotoIdentityMatches,
                "geometryMatch" to maskGeometryMatches,
                "contourPointCount" to contour.size,
                "contourClosed" to (contour.lastOrNull()?.closesContour == true),
                "maskTrusted" to trustedSubjectMask,
                "revealDecision" to revealDecision.name,
                "recognitionStage" to diagnosticSceneFrame.stage.name,
                "rejectReason" to maskRuntimeRejectReason,
                "pStarBindRejectionReason" to pStarBindRejectionReason,
                "pStarPathId" to pStarPathId,
            ),
        )
    }
    var diagnosticHandoffDelivered by remember(sceneKey) { mutableStateOf(false) }
    LaunchedEffect(
        diagnosticUiAvailable,
        displayMode,
        playbackController.mode,
        diagnosticSceneFrame.stage,
        finishedResult,
        subjectResult,
        trustedSubjectMask,
    ) {
        if (!diagnosticUiAvailable || displayMode != RecognitionProgressDisplayMode.STEP_DIAGNOSTIC ||
            diagnosticSceneFrame.stage != RecognitionSceneStage.ORB_RETURN ||
            finishedResult == null || diagnosticHandoffDelivered) return@LaunchedEffect
        val result = requireNotNull(finishedResult)
        if (result.ready && (!controller.resultVisualReady(visualNowMs) ||
                diagnosticSceneFrame.identity != sceneIdentity ||
                sceneIdentity.sessionId != recognitionSessionId ||
                sceneIdentity.attemptId != recognitionAttemptId ||
                sceneIdentity.imageId != image?.imageId ||
                sceneIdentity.photoSha256 != image?.photoSha256)
        ) return@LaunchedEffect
        diagnosticHandoffDelivered = true
        if (!result.ready) {
            onFinished(result)
            return@LaunchedEffect
        }
        val error = diagnosticRevealCallback.value?.invoke(result)
        if (error != null) {
            diagnosticHandoffDelivered = false
            diagnosticActionStatus = error
            Log.w(LOG_TAG, "HANDOFF_UPDATE_BLOCKED session=${recognitionSessionId.orEmpty()} " +
                "attempt=${recognitionAttemptId.orEmpty()} reason=$error")
        } else {
            Log.i(LOG_TAG, "HANDOFF_UPDATE session=${recognitionSessionId.orEmpty()} " +
                "attempt=${recognitionAttemptId.orEmpty()} stage=${diagnosticSceneFrame.stage.name}")
        }
    }
    val rendered = phaseOverride ?: if (stepDiagnosticActive) {
        diagnosticSceneFrame.stage.toRecognitionPhase()
    } else if (visualPhase == RecognitionPhase.FAILURE) {
        RecognitionPhase.FAILURE
    } else {
        productionSceneStage.toRecognitionPhase()
    }
    val waterLightPhase = when (rendered) {
        RecognitionPhase.CAPTURED -> WaterLightPhase.PHOTO_ENTRY
        RecognitionPhase.DETECTING,
        RecognitionPhase.OUTLINE,
        RecognitionPhase.CLASSIFYING -> WaterLightPhase.PROCESSING
        RecognitionPhase.RESULT -> WaterLightPhase.HANDOFF
        RecognitionPhase.FAILURE -> WaterLightPhase.SETTLED
    }
    val waterLightFrame = sceneBaseFrame.copy(phase = waterLightPhase)
    val diagnosticStageStatus = diagnosticSceneFrame.modelGates.waitingReason?.let { reason ->
        when (reason) {
            "WAITING_FOR_PHOTO_FIRST_FRAME" -> "等待照片真实首帧呈现"
            "WAITING_FOR_RESULT_GEOMETRY" -> "等待 Result / Orb / Glyph 几何"
            "RESULT_GEOMETRY_INVALID" -> "Result 几何无效 · 揭晓已静态降级"
            "WAITING_FOR_MODEL_JOIN" -> "等待 Classifier 与 SlimSAM 汇合"
            "WAITING_FOR_TRUSTED_MASK" -> "等待可信鱼体 Mask"
            "WAITING_FOR_REAL_CLASSIFIER_GLYPH" -> "等待真实鱼种 Glyph"
            "WAITING_FOR_ORB_ANCHOR" -> "等待原始 Orb 锚点"
            "WAITING_FOR_RELEASE_PATH_AND_PERIMETER_ARRIVAL" -> "等待 Orb 释放路径到达照片外围"
            "WAITING_FOR_DETECTOR_TARGET" -> "等待真实 Detector 目标"
            "WAITING_FOR_PHOTO_TRANSFORM" -> "等待当前照片变换"
            "WAITING_FOR_PERIMETER_ARRIVAL_PATH" -> "等待照片外围进入路径"
            "WAITING_FOR_PERIMETER_ARRIVAL_EVENT" -> "等待照片外围到照片内部事件"
            "CONTOUR_NOT_CLOSED" -> "真实 Mask 轮廓未闭合 · 降级 ORB_ONLY"
            "MASK_GEOMETRY_INVALID" -> "真实 Mask 几何身份无效 · 降级 ORB_ONLY"
            "WAITING_FOR_PSTAR_PATH_UNAVAILABLE" -> "等待真实 Mask 绑定 P* 路径"
            "WAITING_FOR_CLASSIFIER_AND_SLIMSAM_TERMINAL_JOIN" -> "等待 Classifier 与 SlimSAM 终态"
            "WAITING_FOR_TRUSTED_MASK_AND_CONTOUR" -> "等待可信 Mask 和真实轮廓"
            "WAITING_FOR_RETURN_EVENT_OR_RESULT_GEOMETRY" -> "等待真实回馈事件与 Result 几何"
            "WAITING_FOR_ORB_TO_GLYPH_COMPLETION" -> "等待原始 Orb 向 Glyph 完成转换"
            "WAITING_FOR_GLYPH_REVEAL_COMPLETION" -> "等待真实鱼种名称揭晓完成"
            "WAITING_FOR_RESIDUE_SETTLE_COMPLETION" -> "等待水光余辉收束完成"
            "MODEL_OR_ATTEMPT_FAILED" -> "模型或识别尝试失败"
            else -> "等待：$reason"
        }
    } ?: when {
        finishedResult?.ready == true && revealDecision == RecognitionRevealDecisionState.WAITING_SLIMSAM ->
            "鱼种已识别 · 等待 SlimSAM 最终质量判断"
        finishedResult?.ready == true && manualStepDiagnosticState.value &&
            diagnosticSceneFrame.stage.ordinal < RecognitionSceneStage.ORB_RETURN.ordinal ->
            "真实识别结果已就绪 · 等待分步诊断推进"
        diagnosticSceneFrame.stage.ordinal >= RecognitionSceneStage.ORB_TO_NAME.ordinal && finishedResult?.ready != true ->
            if (finishedResult == null) "等待真实分类结果" else "分类结果不可用"
        diagnosticSceneFrame.stage.ordinal >= RecognitionSceneStage.CONTOUR_A.ordinal && !trustedSubjectMask &&
            assessment?.primary?.box != null -> "等待真实 Mask · BBOX_ONLY"
        diagnosticSceneFrame.stage.ordinal >= RecognitionSceneStage.CONTOUR_A.ordinal && !trustedSubjectMask -> "等待鱼体检测与 Mask"
        diagnosticSceneFrame.stageProgress >= 1f -> "已完成"
        else -> "进行中"
    }
    val normalProgressLabel = when {
        realPhase == RecognitionPhase.FAILURE || (finishedResult != null && finishedResult?.ready != true) -> "识别未完成"
        finishedResult?.ready == true && subjectResult.status == SubjectStatus.FAILED -> "鱼种已识别 · 轮廓暂不可用，继续展示结果"
        finishedResult?.ready == true && revealDecision == RecognitionRevealDecisionState.WAITING_SLIMSAM ->
            "鱼种已识别 · 正在确认真实鱼体轮廓"
        finishedResult?.ready == true -> "识别结果已就绪，正在呈现水光揭晓"
        realPhase >= RecognitionPhase.OUTLINE && !trustedSubjectMask && assessment?.primary?.box != null -> "鱼体已定位 · BBOX_ONLY"
        realPhase == RecognitionPhase.CLASSIFYING && !trustedSubjectMask -> "正在识别鱼种 · BBOX_ONLY"
        realPhase == RecognitionPhase.CLASSIFYING -> "正在识别鱼种"
        realPhase == RecognitionPhase.OUTLINE && trustedSubjectMask -> "正在解析鱼体轮廓"
        realPhase == RecognitionPhase.OUTLINE -> "鱼体已定位，轮廓待确认"
        realPhase == RecognitionPhase.DETECTING -> "正在寻找鱼体"
        else -> "正在理解照片"
    }
    val successfulRevealAllowed = finishedResult?.let { result ->
        result.ready && resolveRecognitionUiState(result) == RecognitionUiState.RESULT_HIGH
    } == true
    LaunchedEffect(sceneKey, diagnosticSceneFrame.stage, playbackRevision, photoPresentedAtMs, playbackController.previewCompleted) {
        if (diagnosticUiAvailable && photoPresentedAtMs != null) Log.i(
            LOG_TAG,
            "DIAGNOSTIC_PRESENT session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                "image=${image?.imageId.orEmpty()} sha256=${image?.photoSha256.orEmpty()} " +
                "stage=${diagnosticSceneFrame.stage.ordinal + 1}/13 name=${diagnosticSceneFrame.stage.name} " +
                "status=$diagnosticStageStatus phase=$rendered optical_reference_ms=${diagnosticSceneFrame.opticalReferenceElapsedMs} " +
                "playback=${playbackMode.name} paused=$playbackPaused " +
                "preview_ms=" + playbackController.previewElapsedMs(recognitionMonotonicNowMs()) +
                " preview_remaining_ms=" + playbackController.previewRemainingMs(recognitionMonotonicNowMs()) +
                " preview_completed=" + playbackController.previewCompleted +
                " detector_box=${assessment?.primary?.box} slimsam_status=${subjectResult.status} " +
                "mask_trusted=$trustedSubjectMask contours=${contour.size} uptime_ms=${recognitionMonotonicNowMs()}",
        )
    }
    val waterLightEngine = remember(sceneKey) { UnifiedWaterLightEngine() }
    val energyPathLedger = remember(sceneKey) { WaterLightPathLedger() }
    energyPathLedger.bind(waterLightFrame)
    waterLightEngine.activate(waterLightFrame)
    DisposableEffect(waterLightEngine) { onDispose { waterLightEngine.release() } }
    val activeSceneStage = if (stepDiagnosticActive) diagnosticSceneFrame.stage else productionSceneStage
    val activeStageProgress = if (stepDiagnosticActive) diagnosticSceneFrame.stageProgress else productionStageProgress
    val orbPresentationCompletion = aiOrbPresentationCompletion(activeSceneStage, orbCompletion)
    val maskContourRevealProgress = recognitionMaskContourRevealProgress(
        stage = activeSceneStage,
        stageProgress = activeStageProgress,
        trustedMask = trustedSubjectMask,
        reduceMotion = motionPolicy.reduceMotion,
    )
    val photoShockwave = photoShockwaveTransform(diagnosticSceneFrame, motionPolicy.reduceMotion)
    val phaseElapsedMs = when {
        stepDiagnosticActive -> diagnosticSceneFrame.stageElapsedMs
        phaseOverride != null -> 1_000L
        photoPresentedAtMs == null -> 0L
        else -> controller.stageElapsedMs(visualNowMs)
    }
    val levelAAvailable = subjectBitmap != null && subjectBox != null && contour.isNotEmpty()
    val displayedFocusLevel = recognitionDisplayedFishFocusLevel(
        requestedLevel = motionPolicy.fishFocusLevel,
        levelAAvailable = levelAAvailable,
        phaseElapsedMs = phaseElapsedMs,
        phaseOverrideActive = phaseOverride != null,
    )
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val safeHorizontal = maxOf(
        24.dp,
        safeInsets.calculateLeftPadding(layoutDirection),
        safeInsets.calculateRightPadding(layoutDirection),
    )
    val safeLeftPx = with(density) { safeInsets.calculateLeftPadding(layoutDirection).toPx() }
    val safeTopPx = with(density) { safeInsets.calculateTopPadding().toPx() }
    val safeRightPx = with(density) { safeInsets.calculateRightPadding(layoutDirection).toPx() }
    val safeBottomPx = with(density) { safeInsets.calculateBottomPadding().toPx() }
    val skipPhotoEntry = phaseOverride != null
    val photoEntryProgress = RecognitionSceneTimelineProjection.photoEntryProgress(
        sceneElapsedMs,
        settled = skipPhotoEntry || motionPolicy.reduceMotion,
    )
    LaunchedEffect(rendered, subjectResult, contour.size, assessment?.primary?.box, motionPolicy) {
        if (rendered == RecognitionPhase.OUTLINE || rendered == RecognitionPhase.CLASSIFYING) {
            logFocusDiagnostic(
                subject = subjectResult,
                contourSegments = contour.size,
                box = assessment?.primary?.box,
                lowPerformance = motionPolicy.lowPerformance,
                reduceMotion = motionPolicy.reduceMotion,
                focusLevel = displayedFocusLevel,
                levelAAvailable = levelAAvailable,
            )
        }
    }
    LaunchedEffect(rendered, displayedFocusLevel, levelAAvailable) {
        if (rendered == RecognitionPhase.OUTLINE || rendered == RecognitionPhase.CLASSIFYING) {
            Log.i(
                LOG_TAG,
                "FOCUS_READINESS phase=$rendered level=${displayedFocusLevel.name} " +
                    "level_a_available=$levelAAvailable phase_elapsed_ms=$phaseElapsedMs " +
                    "requested_level=${motionPolicy.fishFocusLevel.name} " +
                    "promotion_delay_ms=$FISH_FOCUS_A_PROMOTION_DELAY_MS",
            )
        }
    }
    val evidenceWindow = recognitionEvidenceWindow(pageContext)
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .drawWithContent {
                drawContent()
                recognitionEvidenceRecorder?.onFramePresented(
                    window = evidenceWindow,
                    frame = waterLightFrame.sceneFrame,
                    latestFrame = { latestEvidenceSceneFrame.value },
                    allowPixelCopyCapture = BuildConfig.DEBUG,
                    onCheckpointCaptureGateChanged = { stage, pending ->
                        controller.setVisualCheckpointCapturePending(stage, pending, sceneIdentity)
                    },
                )
            }
            .onGloballyPositioned { rootOrigin = it.boundsInWindow().topLeft },
    ) {
        LakeGlassBackgroundHost(Modifier.fillMaxSize())
        val viewportWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val viewportHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val diagnosticReservePx = if (stepDiagnosticActive) with(density) { 88.dp.toPx() } else 0f
        val photoFlowPlan = remember(
            image?.imageId,
            image?.photoSha256,
            image?.bitmap?.width,
            image?.bitmap?.height,
            image?.source,
            image?.capturePresentation, rootOrigin,
            viewportWidthPx,
            viewportHeightPx,
            density.density, density.fontScale,
            safeLeftPx,
            safeTopPx,
            safeRightPx,
            safeBottomPx,
            stepDiagnosticActive,
            diagnosticReservePx,
        ) {
            image?.let {
                planRecognitionPhotoFlow(
                    sourceWidth = it.bitmap.width,
                    sourceHeight = it.bitmap.height,
                    viewportWidthPx = viewportWidthPx,
                    viewportHeightPx = viewportHeightPx,
                    density = density.density,
                    safeLeftPx = safeLeftPx,
                    safeTopPx = safeTopPx,
                    safeRightPx = safeRightPx,
                    safeBottomPx = safeBottomPx,
                    cameraSource = it.source.contains("camera", ignoreCase = true),
                    cameraPreviewRect = it.capturePresentation?.let { capture ->
                        com.yujian.ai.ui.identify.DisplayRect(capture.previewLeft - rootOrigin.x,
                            capture.previewTop - rootOrigin.y, capture.previewRight - rootOrigin.x,
                            capture.previewBottom - rootOrigin.y)
                    },
                    diagnosticReservePx = diagnosticReservePx,
                    fontScale = density.fontScale,
                )
            }
        }
        LaunchedEffect(sceneKey, photoFlowPlan) {
            photoFlowPlan?.let { plan ->
                Log.i(LOG_TAG, "PROCESSING_LAYOUT image=${image?.imageId} sha256=${image?.photoSha256} viewport=${viewportWidthPx}x${viewportHeightPx} landscape=${plan.landscape} start=${plan.startTransform.photoContentRect} target=${plan.targetPhotoRect} orb_center=${plan.statusCenterXPx},${plan.orbCenterYPx} orb_core=${plan.orbCoreSizePx}")
            }
        }
        val producerOrbGeometry = if (image != null && photoFlowPlan != null) {
            val captureForOrb = image.capturePresentation
            val eased = processingOrbMotionFrame(
                entranceElapsedMs, captureForOrb != null, motionPolicy.reduceMotion, orbPresentationCompletion,
            ).destinationProgress
            val fromX = captureForOrb?.shutterCenterX?.minus(rootOrigin.x)
                ?.takeIf(Float::isFinite) ?: photoFlowPlan.statusCenterXPx
            val fromY = captureForOrb?.shutterCenterY?.minus(rootOrigin.y)
                ?.takeIf(Float::isFinite) ?: photoFlowPlan.orbCenterYPx
            val initialCorePx = captureForOrb?.shutterSizePx?.takeIf { it.isFinite() && it > 0f }
                ?: photoFlowPlan.orbCoreSizePx
            val corePx = initialCorePx + (photoFlowPlan.orbCoreSizePx - initialCorePx) * eased
            Triple(
                Offset(
                    fromX + (photoFlowPlan.statusCenterXPx - fromX) * eased,
                    fromY + (photoFlowPlan.orbCenterYPx - fromY) * eased,
                ),
                corePx * .5f,
                photoFlowPlan.targetTransform,
            )
        } else null
        val energyProducerSnapshot = if (image != null && photoFlowPlan != null && producerOrbGeometry != null) {
            val maskSha = subjectResult.maskSha256.orEmpty()
            val maskVersion = subjectBitmap?.let { RecognitionMaskIntegrity.versionFromSha256(subjectResult.maskSha256) } ?: -1L
            val pStarCandidate = if (trustedSubjectMask && subjectBitmap != null) {
                recognitionMaskContactPointSource(
                    subjectBox, contour, producerOrbGeometry.third, producerOrbGeometry.first,
                    image.bitmap.width, image.bitmap.height,
                )
            } else null
            createRecognitionEnergyProducerSnapshot(
                frame = waterLightFrame,
                ledger = energyPathLedger,
                classifier = when {
                    pipelineError != null || realPhase == RecognitionPhase.FAILURE -> RecognitionClassifierTaskState.FAILED
                    finishedResult?.ready == true -> RecognitionClassifierTaskState.READY
                    else -> RecognitionClassifierTaskState.WAITING
                },
                subjectStatus = subjectResult.status,
                detectorBox = assessment?.primary?.box,
                maskTrusted = trustedSubjectMask,
                subjectBox = subjectBox,
                contour = contour,
                transform = producerOrbGeometry.third,
                pStarCandidate = pStarCandidate,
                orbCenter = producerOrbGeometry.first,
                orbRadiusPx = producerOrbGeometry.second,
                maskSha256 = maskSha,
                maskVersion = maskVersion,
                highConfidenceResult = successfulRevealAllowed,
                pStarDeadlineExpired = attemptDeadline.isExpired(visualNowMs),
            )
        } else null
        val energyProducerEvent = energyProducerSnapshot?.event
        var lastPStarDiagnosticKey by remember(sceneKey) { mutableStateOf<String?>(null) }
        LaunchedEffect(
            sceneIdentity, subjectResult.maskSha256, trustedSubjectMask,
            contour.size, photoGeometryVersion, energyProducerSnapshot?.contactPointNormalizedSource,
        ) {
            if (!trustedSubjectMask || contour.isEmpty() || photoGeometryVersion <= 0L) return@LaunchedEffect
            val key = "${sceneIdentity.attemptId}:${subjectResult.maskSha256}:$photoGeometryVersion:" +
                "${energyProducerSnapshot?.contactPointNormalizedSource}"
            if (key == lastPStarDiagnosticKey) return@LaunchedEffect
            lastPStarDiagnosticKey = key
            val transform = producerOrbGeometry?.third
            val center = producerOrbGeometry?.first
            recognitionEvidenceRecorder?.recordOrbRuntime(
                fileName = "waterlight_paths.jsonl",
                type = "PSTAR_CANDIDATE_DIAGNOSTIC",
                payload = mapOf(
                    "attemptIdHash" to com.yujian.ai.ui.recognition.diagnostics.sha256(sceneIdentity.attemptId),
                    "maskSha256" to subjectResult.maskSha256,
                    "maskVersion" to subjectBitmap?.let { RecognitionMaskIntegrity.versionFromSha256(subjectResult.maskSha256) },
                    "geometryVersion" to photoGeometryVersion,
                    "contourSegments" to contour.size,
                    "contourClosed" to (contour.lastOrNull()?.closesContour == true),
                    "roi" to subjectBox?.let { listOf(it.x1, it.y1, it.x2, it.y2) },
                    "orbNormalizedSource" to if (transform != null && center != null &&
                        transform.drawnWidth > 1f && transform.drawnHeight > 1f) listOf(
                        (center.x - transform.translationX) / transform.drawnWidth,
                        (center.y - transform.translationY) / transform.drawnHeight,
                    ) else null,
                    "contactNormalizedSource" to energyProducerSnapshot?.contactPointNormalizedSource?.let {
                        listOf(it.x, it.y)
                    },
                    "producerDecision" to energyProducerSnapshot?.decision?.name,
                    "pathBindingStatus" to energyProducerSnapshot?.pathBindingStatus?.name,
                    "fishPathPresent" to (energyProducerEvent?.feedback == EnergyFeedback.ABSORB &&
                        energyProducerEvent.pathGeometry != null),
                ),
            )
            energyProducerSnapshot?.contactPointNormalizedSource?.let { contact ->
                recognitionEvidenceRecorder?.recordOrbRuntime(
                    fileName = "waterlight_paths.jsonl",
                    type = "PSTAR_RESOLUTION",
                    payload = mapOf(
                        "sessionIdHash" to com.yujian.ai.ui.recognition.diagnostics.sha256(sceneIdentity.sessionId),
                        "attemptIdHash" to com.yujian.ai.ui.recognition.diagnostics.sha256(sceneIdentity.attemptId),
                        "imageIdHash" to com.yujian.ai.ui.recognition.diagnostics.sha256(sceneIdentity.imageId),
                        "photoSha256" to sceneIdentity.photoSha256,
                        "maskSha256" to subjectResult.maskSha256,
                        "maskVersion" to subjectBitmap?.let { RecognitionMaskIntegrity.versionFromSha256(subjectResult.maskSha256) },
                        "geometryVersion" to photoGeometryVersion,
                        "pStarNormalizedSource" to listOf(contact.x, contact.y),
                        "contourClosed" to (contour.lastOrNull()?.closesContour == true),
                    ),
                )
            }
        }
        var lastPathBindingEvidenceKey by remember(sceneKey) { mutableStateOf<String?>(null) }
        var lastRecordedOrbOnlyPathNotApplicableKey by remember(sceneKey) { mutableStateOf<String?>(null) }
        LaunchedEffect(
            sceneIdentity,
            energyProducerSnapshot?.decision,
            energyProducerSnapshot?.pathBindingStatus,
            energyProducerSnapshot?.pathApplicability,
            energyProducerEvent?.stage,
            energyProducerEvent?.feedback,
            energyProducerEvent?.pathId,
            energyProducerEvent?.renderStatus,
            energyProducerEvent?.eventSequence,
            energyProducerEvent?.geometryVersion,
            waterLightFrame.sceneFrame.stageProgress >= .999f,
            energyProducerSnapshot?.fishPathSnapshot,
            trustedSubjectMask,
            subjectResult.maskSha256,
            subjectBitmap?.generationId,
            photoGeometryVersion,
            waterLightFrame.sceneFrame.modelGates,
            pStarBindRejectionReason,
        ) {
            val producer = energyProducerSnapshot ?: return@LaunchedEffect
            val nowMs = recognitionMonotonicNowMs()
            controller.onFishPathBindingStatus(producer.pathBindingStatus, sceneIdentity)
            val bindingEvidenceKey = "${sceneIdentity.attemptId}:${waterLightFrame.geometryVersion}:" +
                "${producer.pathBindingStatus.name}:${producer.contactPointNormalizedSource}"
            if (bindingEvidenceKey != lastPathBindingEvidenceKey) {
                lastPathBindingEvidenceKey = bindingEvidenceKey
                recognitionEvidenceRecorder?.recordOrbRuntime(
                    fileName = "waterlight_paths.jsonl",
                    type = "FISH_PATH_BINDING_STATE",
                    payload = mapOf(
                        "sessionIdHash" to com.yujian.ai.ui.recognition.diagnostics.sha256(sceneIdentity.sessionId),
                        "attemptIdHash" to com.yujian.ai.ui.recognition.diagnostics.sha256(sceneIdentity.attemptId),
                        "imageIdHash" to com.yujian.ai.ui.recognition.diagnostics.sha256(sceneIdentity.imageId),
                        "photoSha256" to sceneIdentity.photoSha256,
                        "maskSha256" to subjectResult.maskSha256.takeIf(String::isNotBlank),
                        "maskVersion" to subjectBitmap?.let { RecognitionMaskIntegrity.versionFromSha256(subjectResult.maskSha256) },
                        "geometryVersion" to waterLightFrame.geometryVersion,
                        "pStarNormalizedSource" to producer.contactPointNormalizedSource?.let { listOf(it.x, it.y) },
                        "pathId" to producer.event?.pathId,
                        "status" to producer.pathBindingStatus.name,
                        "decision" to producer.decision.name,
                        "deadlineExpired" to attemptDeadline.isExpired(nowMs),
                    ),
                )
            }
            if (producer.pathApplicability == "NOT_APPLICABLE_ORB_ONLY") {
                val traceKey = "${sceneIdentity.attemptId}:${waterLightFrame.geometryVersion}:${RecognitionSceneStage.PATH_TO_FISH.name}"
                if (lastRecordedOrbOnlyPathNotApplicableKey != traceKey) {
                    lastRecordedOrbOnlyPathNotApplicableKey = traceKey
                    recognitionEvidenceRecorder?.recordOrbRuntime(
                        fileName = "waterlight_paths.jsonl",
                        type = "PATH_TO_FISH_NOT_APPLICABLE",
                        payload = mapOf(
                            "sessionIdHash" to com.yujian.ai.ui.recognition.diagnostics.sha256(sceneIdentity.sessionId),
                            "attemptIdHash" to com.yujian.ai.ui.recognition.diagnostics.sha256(sceneIdentity.attemptId),
                            "imageIdHash" to com.yujian.ai.ui.recognition.diagnostics.sha256(sceneIdentity.imageId),
                            "decision" to producer.decision.name,
                            "applicability" to producer.pathApplicability,
                            "geometryVersion" to waterLightFrame.geometryVersion,
                        ),
                    )
                }
            }
            val event = producer.event
            if (event == null) {
                controller.onRevealDecision(producer.decision, nowMs, sceneIdentity)
                return@LaunchedEffect
            }
            pStarPathId = event.takeIf {
                it.feedback == EnergyFeedback.ABSORB &&
                    producer.decision == RecognitionRevealDecisionState.FISH_FOCUS_READY
            }?.pathId
            producer.perimeterArrivalPath?.let { arrival ->
                val registered = controller.onPerimeterArrivalPathAvailable(
                    pathId = arrival.pathId,
                    pathGeometry = arrival.geometry,
                    geometryVersion = arrival.geometryVersion,
                    currentIdentity = sceneIdentity,
                )
                if (!registered) Log.w(
                    "WaterLightEnergy",
                    "ARRIVAL_PATH_BIND_REJECTED session=${event.sessionId} attempt=${event.attemptId} " +
                        "path=${arrival.pathId} geometry=${arrival.geometryVersion}",
                )
            }
            val eventMaskSha256 = event.pathMaskSha256
            val eventMaskVersion = event.pathMaskVersion
            if (event.feedback == EnergyFeedback.ABSORB && producer.contactPointNormalizedSource != null &&
                eventMaskSha256 != null && eventMaskVersion != null
            ) {
                val registered = controller.onFishContactPathAvailable(
                    pathId = event.pathId,
                    maskSha256 = eventMaskSha256,
                    maskVersion = eventMaskVersion,
                    geometryVersion = event.pathGeometryVersion ?: event.geometryVersion,
                    contactPointNormalizedSource = producer.contactPointNormalizedSource,
                    currentIdentity = sceneIdentity,
                )
                recognitionEvidenceRecorder?.recordOrbRuntime(
                    fileName = "waterlight_paths.jsonl",
                    type = if (registered) "PATH_BIND_ACCEPTED" else "PATH_BIND_REJECTED",
                    payload = mapOf(
                        "sessionIdHash" to com.yujian.ai.ui.recognition.diagnostics.sha256(event.sessionId),
                        "attemptIdHash" to com.yujian.ai.ui.recognition.diagnostics.sha256(event.attemptId),
                        "imageIdHash" to com.yujian.ai.ui.recognition.diagnostics.sha256(event.imageId),
                        "photoSha256" to event.photoSha256,
                        "maskSha256" to eventMaskSha256,
                        "maskVersion" to eventMaskVersion,
                        "geometryVersion" to event.pathGeometryVersion,
                        "pathId" to event.pathId,
                        "pStarNormalizedSource" to producer.contactPointNormalizedSource.let { listOf(it.x, it.y) },
                        "eventSequence" to event.eventSequence,
                        "result" to if (registered) "ACCEPTED" else "REJECTED",
                    ),
                )
                if (!registered) {
                    val terminal = controller.currentRevealDecision() in setOf(
                        RecognitionRevealDecisionState.ORB_ONLY_READY,
                        RecognitionRevealDecisionState.STATIC_RESULT,
                        RecognitionRevealDecisionState.RECOGNITION_FAILED,
                        RecognitionRevealDecisionState.CANCELLED,
                    )
                    pStarBindRejectionReason = if (terminal) "PSTAR_BIND_TERMINAL_REJECTED" else "PSTAR_BIND_DEFERRED"
                    Log.w(
                        "WaterLightEnergy",
                        "${pStarBindRejectionReason} session=${event.sessionId} attempt=${event.attemptId} " +
                            "mask_sha256=${eventMaskSha256.take(16)} path=${event.pathId}",
                    )
                } else {
                    pStarBindRejectionReason = null
                }
            }
            // A valid Fish Path must be bound before the reveal decision can
            // become terminal. Otherwise a late Mask may be rejected as an
            // ORB_ONLY route even though its actual P* was just produced.
            controller.onRevealDecision(producer.decision, nowMs, sceneIdentity)
            // Binding can happen during the stage, but its completion event must
            // only be submitted on a frame that has actually reached the gate.
            if (waterLightFrame.sceneFrame.stageProgress < .999f) return@LaunchedEffect
            val eventEvidence = mapOf<String, Any?>(
                "eventType" to event.feedback.name, "sessionId" to event.sessionId,
                "attemptId" to event.attemptId, "imageId" to event.imageId,
                "maskSha256" to event.pathMaskSha256, "maskVersion" to event.pathMaskVersion,
                "pathId" to event.pathId, "geometryVersion" to event.geometryVersion,
                "eventSequence" to event.eventSequence, "renderReceipt" to event.renderStatus,
                "stage" to event.stage.name,
            )
            recognitionEvidenceRecorder?.recordGateDiagnostic("EVENT_PRODUCED", eventEvidence)
            val result = controller.recordEnergyEventWithReason(waterLightFrame.sceneFrame, event)
            if (result.accepted) {
                recognitionEvidenceRecorder?.recordGateDiagnostic("EVENT_ACCEPTED", eventEvidence)
                recognitionEvidenceRecorder?.recordEnergyEvent(
                    event = event,
                    accepted = true,
                    taskEnergyMergedBeforeEvent = waterLightFrame.sceneFrame.taskEnergyMerged,
                )
                recognitionEvidenceRecorder?.recordGateDiagnostic("EVENT_CONSUMED", eventEvidence)
                if (event.feedback == EnergyFeedback.ABSORB && !controller.fishPathSnapshotReady()) {
                    val snapshot = producer.fishPathSnapshot
                    if (snapshot == null) {
                        recognitionEvidenceRecorder?.recordGateDiagnostic(
                            "FISH_PATH_SNAPSHOT_EXPORT_PENDING",
                            mapOf(
                                "stage" to event.stage.name,
                                "pathBindingStatus" to producer.pathBindingStatus.name,
                                "pathId" to event.pathId,
                                "maskSha256" to event.pathMaskSha256,
                                "maskVersion" to event.pathMaskVersion,
                                "geometryVersion" to event.geometryVersion,
                            ),
                        )
                    } else if (controller.onFishPathSnapshot(snapshot, sceneIdentity)) {
                        val snapshotEvidence = mapOf(
                            "sessionIdHash" to com.yujian.ai.ui.recognition.diagnostics.sha256(snapshot.sessionId),
                            "attemptIdHash" to com.yujian.ai.ui.recognition.diagnostics.sha256(snapshot.attemptId),
                            "imageIdHash" to com.yujian.ai.ui.recognition.diagnostics.sha256(snapshot.imageId),
                            "fishPathId" to snapshot.fishPathId,
                            "returnPathId" to snapshot.returnPathId,
                            "maskSha256" to snapshot.maskSha256,
                            "maskVersion" to snapshot.maskVersion,
                            "geometryVersion" to snapshot.geometryVersion,
                            "pStarNormalizedSource" to listOf(snapshot.pStarNormalizedSource.x, snapshot.pStarNormalizedSource.y),
                            "fishPathGeometry" to evidenceGeometry(snapshot.fishPathGeometry),
                            "perimeterBranch" to evidenceGeometry(snapshot.perimeterBranch),
                            "orbReleasePath" to evidenceGeometry(snapshot.orbReleasePath),
                            "returnPathSegments" to snapshot.returnPathSegments.map(::evidenceGeometry),
                            "sourceCoordinateSpace" to snapshot.sourceCoordinateSpace,
                            "creationTimestampMonotonicMs" to snapshot.creationTimestampMonotonicMs,
                            "creationEventSequence" to snapshot.creationEventSequence,
                            "valid" to snapshot.isValid(),
                            "acceptedAfterEvent" to event.feedback.name,
                            "acceptedEventSequence" to event.eventSequence,
                        )
                        recognitionEvidenceRecorder?.recordOrbRuntime(
                            fileName = "waterlight_paths.jsonl",
                            type = "TRUSTED_FISH_PATH_SNAPSHOT",
                            payload = snapshotEvidence,
                        )
                        recognitionEvidenceRecorder?.recordGateDiagnostic("FISH_PATH_SNAPSHOT_ACCEPTED", snapshotEvidence)
                        fishPathSnapshotCallback.value.invoke(snapshot)
                    } else {
                        recognitionEvidenceRecorder?.recordGateDiagnostic(
                            "FISH_PATH_SNAPSHOT_REJECTED",
                            mapOf(
                                "reason" to "CONTROLLER_IDENTITY_OR_MASK_PATH_MISMATCH",
                                "fishPathId" to snapshot.fishPathId,
                                "returnPathId" to snapshot.returnPathId,
                                "eventPathId" to event.pathId,
                                "eventSequence" to event.eventSequence,
                                "maskSha256" to snapshot.maskSha256,
                                "maskVersion" to snapshot.maskVersion,
                                "geometryVersion" to snapshot.geometryVersion,
                            ),
                        )
                    }
                }
            } else {
                val reason = requireNotNull(result.rejectionReason)
                recognitionEvidenceRecorder?.recordGateDiagnostic("EVENT_REJECTED", eventEvidence + ("reason" to reason.name))
                recognitionEvidenceRecorder?.recordEnergyEventRejection(
                    frame = waterLightFrame.sceneFrame,
                    event = event,
                    reason = reason,
                    modelGates = controller.modelGates,
                )
            }
            Log.i(
                "WaterLightEnergy",
                "ENERGY_EVENT session=${event.sessionId} attempt=${event.attemptId} image=${event.imageId} " +
                    "stage=${event.stage} feedback=${event.feedback} path=${event.pathId} geometry=${event.geometryVersion} " +
                    "render_status=${event.renderStatus} accepted=${result.accepted} reject=${result.rejectionReason}",
            )
        }
        image?.capturePresentation?.let { capture ->
            if (photoEntryProgress < 1f) Canvas(Modifier.fillMaxSize()) {
                val panelTop = (capture.previewBottom - rootOrigin.y).coerceIn(0f, size.height)
                drawRoundRect(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color(0xFF3C464C), Color(0xFF121619)), startY = panelTop, endY = size.height),
                    topLeft = Offset(0f, panelTop), size = Size(size.width, size.height - panelTop),
                    cornerRadius = CornerRadius(32.dp.toPx()), alpha = 1f - photoEntryProgress)
            }
        }
        var stage045GlPresented by remember(sceneKey) { mutableStateOf(false) }
        var cachedFishPath by remember(sceneIdentity.attemptId, waterLightFrame.geometryVersion) {
            mutableStateOf<WaterLightPathGeometry?>(null)
        }
        val liveFishPath = energyProducerEvent?.takeIf {
            it.feedback == EnergyFeedback.ABSORB && it.pathGeometry != null
        }?.pathGeometry
        LaunchedEffect(liveFishPath) {
            if (liveFishPath != null && cachedFishPath != liveFishPath) {
                cachedFishPath = liveFishPath
            }
        }
        val activeFishPath = liveFishPath ?: cachedFishPath
        if (image != null && photoFlowPlan != null) RecognitionPhoto(
            waterLightFrame = waterLightFrame,
            waterLightEngine = waterLightEngine,
            energyEvent = energyProducerEvent,
            energyContactPointNormalizedSource = energyProducerSnapshot?.contactPointNormalizedSource,
            revealDecision = energyProducerSnapshot?.decision ?: revealDecision,
            bitmap = image.bitmap,
            photoFlowPlan = photoFlowPlan,
            orbCenter = producerOrbGeometry!!.first,
            orbAnchorReady = motionPolicy.reduceMotion || entranceElapsedMs >= RecognitionSceneTimelineProjection.ORB_ENTRANCE_DURATION_MS,
            orbRadiusPx = producerOrbGeometry.second,
            photoEntryProgress = photoEntryProgress,
            phase = rendered,
            focusBox = assessment?.primary?.box,
            subjectBitmap = subjectBitmap,
            subjectBox = subjectBox,
            contour = contour,
                visualClockMs = if (deterministicMotion) visualClockMs else sceneElapsedMs,
            deterministicMotion = deterministicMotion,
            processingElapsedMs = sceneElapsedMs,
            opticalReferenceElapsedMs = opticalReferenceElapsedMs,
            photoShockwave = photoShockwave,
            motionSessionKey = sceneKey,
            phaseElapsedMs = phaseElapsedMs,
            motionPolicy = motionPolicy,
            onMotionFrame = onMotionFrame,
            focusLevel = displayedFocusLevel,
            onFishFocusTransform = onFishFocusTransform,
            onPhotoGeometryChanged = {
                photoGeometryVersion = it
                controller.onPhotoTransformAvailable(it != 0L, sceneIdentity, geometryVersion = it)
            },
            modifier = Modifier.fillMaxSize(),
            processingClockStarted = photoPresentedAtMs != null || deterministicMotion,
            recognitionSessionId = recognitionSessionId.orEmpty(),
            recognitionAttemptId = recognitionAttemptId.orEmpty(),
            imageId = image.imageId,
            photoSha256 = image.photoSha256,
            photoContentVersion = image.bitmap.generationId.toLong().coerceAtLeast(0L),
            maskSha256 = subjectResult?.maskSha256.orEmpty(),
            maskVersion = subjectBitmap?.let { RecognitionMaskIntegrity.versionFromSha256(subjectResult.maskSha256) } ?: -1L,
            activeFishPathId = pStarPathId,
            maskRevealProgress = maskContourRevealProgress,
            trustedMask = trustedSubjectMask,
            stage045GlPresented = stage045GlPresented,
            onGoldRenderComplete = { receipt -> controller.onFishGoldRenderComplete(receipt, sceneIdentity) },
            onContourRenderComplete = { receipt -> controller.onContourRenderComplete(receipt, sceneIdentity) },
            onProcessingPhotoSnapshot = { transform, bounds, photoBounds, cornerRadiusPx, actualOrbCenter, orbCoreDiameterPx, contactPointSource, contactMaskSha256, contactMaskVersion, contactGeometryVersion ->
                controller.onOrbAnchorAvailable(
                    actualOrbCenter.x.isFinite() && actualOrbCenter.y.isFinite() && orbCoreDiameterPx.isFinite() && orbCoreDiameterPx > 1f,
                    sceneIdentity,
                )
                val processingSnapshot = RecognitionProcessingPhotoSnapshot(
                        imageId = image.imageId,
                        viewportBoundsInWindow = bounds,
                        sourceToViewport = transform,
                        photoBoundsInWindow = photoBounds,
                        cornerRadiusPx = cornerRadiusPx,
                        processingOrbCenterInWindow = com.yujian.ai.ui.identify.DisplayPoint(
                            bounds.left + actualOrbCenter.x,
                            bounds.top + actualOrbCenter.y,
                        ),
                        processingOrbCoreDiameterPx = orbCoreDiameterPx,
                        contactPointNormalizedSource = contactPointSource?.let {
                            com.yujian.ai.ui.identify.DisplayPoint(it.x, it.y)
                        },
                        contactPointMaskSha256 = contactMaskSha256,
                        contactPointMaskVersion = contactMaskVersion,
                        contactPointGeometryVersion = contactGeometryVersion,
                    )
                processingPhotoSnapshotForReveal = processingSnapshot
                onProcessingPhotoSnapshot(processingSnapshot)
            },
            onPhotoFirstFramePresented = { presentedAtMs ->
                if (photoPresentedAtMs == null) {
                    Log.i(
                        LOG_TAG,
                        "PHOTO_FIRST_FRAME_PRESENTED session=${recognitionSessionId.orEmpty()} " +
                            "attempt=${recognitionAttemptId.orEmpty()} image=${image.imageId} uptime_ms=$presentedAtMs",
                    )
                    photoPresentedAtMs = presentedAtMs
                    photoFramePresentedCallback.value?.invoke(presentedAtMs)
                    controller.markPhotoFirstFramePresented(sceneIdentity, presentedAtMs)
                    if (diagnosticUiAvailable && displayMode == RecognitionProgressDisplayMode.STEP_DIAGNOSTIC) playbackRevision++
                }
            },
        )
        if (image != null && photoFlowPlan != null && producerOrbGeometry != null) {
            Stage045EnergyStreamGlOverlay(
                waterLightFrame = waterLightFrame,
                orbCenter = producerOrbGeometry.first,
                orbRadiusPx = producerOrbGeometry.second,
                photoRect = photoFlowPlan.targetTransform.photoContentRect,
                cornerRadiusPx = with(density) { 16.dp.toPx() },
                releasePath = energyPathLedger.releasePath(waterLightFrame),
                arrivalPath = energyPathLedger.perimeterArrivalPath(waterLightFrame)?.geometry,
                fishPath = activeFishPath,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx,
                globalOpacity = 1f - orbPresentationCompletion.coerceIn(0f, 1f),
                lowPerformance = motionPolicy.lowPerformance,
                onFirstFramePresented = { stage045GlPresented = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(
                    top = safeInsets.calculateTopPadding() + 4.dp,
                    start = safeHorizontal / 2f,
                ),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color(0xFF123F48))
        }
        Text("照片识别", color = Color(0xFF123F48), fontSize = 18.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = safeInsets.calculateTopPadding() + 14.dp))
        photoFlowPlan?.let { plan ->
            val capture = image?.capturePresentation
            val orbEntranceMs = entranceElapsedMs
            val eased = processingOrbMotionFrame(orbEntranceMs, capture != null, motionPolicy.reduceMotion, orbPresentationCompletion).destinationProgress
            val fromX = capture?.shutterCenterX?.minus(rootOrigin.x)?.takeIf { it.isFinite() } ?: plan.statusCenterXPx
            val fromY = capture?.shutterCenterY?.minus(rootOrigin.y)?.takeIf { it.isFinite() } ?: plan.orbCenterYPx
            val initialCorePx = capture?.shutterSizePx?.takeIf { it.isFinite() && it > 0f } ?: plan.orbCoreSizePx
            val currentCorePx = initialCorePx + (plan.orbCoreSizePx - initialCorePx) * eased
            val core = with(density) { currentCorePx.toDp() }
            val outerPx = currentCorePx + with(density) { 28.dp.toPx() }
            val orbCenter = Offset(
                fromX + (plan.statusCenterXPx - fromX) * eased,
                fromY + (plan.orbCenterYPx - fromY) * eased,
            )
            val orbX = orbCenter.x - outerPx / 2f
            val orbY = orbCenter.y - outerPx / 2f
            SideEffect {
                val nextBounds = android.graphics.Rect(
                    orbX.roundToInt(),
                    orbY.roundToInt(),
                    (orbX + outerPx).roundToInt(),
                    (orbY + outerPx).roundToInt(),
                )
                if (debugOrbEvidenceBounds != nextBounds) debugOrbEvidenceBounds = nextBounds
                if (debugOrbEvidenceCoreSizePx != currentCorePx) debugOrbEvidenceCoreSizePx = currentCorePx
            }
            val statusWidth = if (plan.landscape) maxWidth * .34f else maxWidth
            val progressLabel = if (BuildConfig.DEBUG && displayMode == RecognitionProgressDisplayMode.STEP_DIAGNOSTIC) "正在理解照片" else normalProgressLabel
            val orbHost = remember(waterLightEngine, sceneIdentity) {
                WaterLightHostBinding(
                    WaterLightHost.ORB, WaterLightMaterialProfile.ORB_LEGACY, waterLightEngine, sceneIdentity,
                )
            }
            val orbGeometry = WaterLightHostGeometry(
                WaterLightHost.ORB, waterLightFrame.geometryVersion, orbX, orbY, orbX + outerPx, orbY + outerPx,
            )
            val orbHostRejectReason = orbHost.rejectionReason(waterLightFrame, orbGeometry)
            val hostOrbRenderIsolationMode = when {
                orbHostRejectReason == null -> OrbRenderIsolationMode.NORMAL
                orbHost.canUseSourceCoreFallback(waterLightFrame, orbGeometry) -> OrbRenderIsolationMode.RAW_CORE
                else -> OrbRenderIsolationMode.SUPPRESSED
            }
            val debugOrbIsolationEligible = BuildConfig.DEBUG && diagnosticUiAvailable &&
                displayMode == RecognitionProgressDisplayMode.STEP_DIAGNOSTIC &&
                waterLightFrame.sceneFrame.stage == RecognitionSceneStage.ORB_WAKE &&
                orbHostRejectReason == null
            val orbRenderIsolationMode = orbDebugIsolationModeWithHostPriority(
                hostRejectReason = orbHostRejectReason,
                hostMode = hostOrbRenderIsolationMode,
                requestedDebugMode = debugOrbIsolationMode,
                debugSelectionEligible = debugOrbIsolationEligible,
            )
            SideEffect {
                debugOrbIsolationHostAccepted = orbHostRejectReason == null &&
                    waterLightFrame.sceneFrame.stage == RecognitionSceneStage.ORB_WAKE
            }
            val orbLifeState = if (waterLightFrame.sceneFrame.stage == RecognitionSceneStage.PHOTO_STABLE ||
                (waterLightFrame.sceneFrame.stage == RecognitionSceneStage.ORB_WAKE &&
                    waterLightFrame.sceneFrame.stageProgress < .5f)
            ) OrbRuntimeProfileV1.LifeState.S1_IDLE else OrbRuntimeProfileV1.LifeState.S2_ACTIVE
            LaunchedEffect(
                sceneIdentity, waterLightFrame.sceneFrame.stage, orbHostRejectReason, orbRenderIsolationMode,
            ) {
                val frame = waterLightFrame.sceneFrame
                val completionAlpha = 1f - orbPresentationCompletion.coerceIn(0f, 1f)
                val opticalGateEligible = orbHostRejectReason == null &&
                    aiOrbOpticalEffectEnabled(frame.stage, frame.stageProgress)
                Log.i("YujianOrbVisibility", "ORB_HOST_GATE sessionId=${sceneIdentity.sessionId} " +
                    "attemptId=${sceneIdentity.attemptId} imageId=${sceneIdentity.imageId} stage=${frame.stage.name} " +
                    "stageProgress=${frame.stageProgress} semanticLocalMs=${frame.stageProgress * frame.stage.minimumVisibleMs} " +
                    "hostAccepted=${orbHostRejectReason == null} " +
                    "rejectReason=${orbHostRejectReason ?: "NONE"} geometryVersion=${waterLightFrame.geometryVersion} " +
                    "orbCenterX=${orbCenter.x} orbCenterY=${orbCenter.y} orbCoreSizePx=${currentCorePx} " +
                    "orbOuterBounds=${orbGeometry.leftPx},${orbGeometry.topPx},${orbGeometry.rightPx},${orbGeometry.bottomPx} " +
                    "completion=${orbPresentationCompletion} completionAlpha=${completionAlpha} " +
                    "opticalGateEligible=${opticalGateEligible} renderIsolationMode=${orbRenderIsolationMode.name} " +
                    "orbLifeState=${orbLifeState.name} " +
                    "r06RendererMode=${OrbFilamentRendererMode.ORB_V1_CANDIDATE.name}")
            }
            AIOrbCore(
                state = aiOrbProcessingState(orbEntranceMs, capture != null, motionPolicy.reduceMotion, orbPresentationCompletion),
                coreSize = core,
                entranceElapsedMs = orbEntranceMs,
                elapsedMs = sceneElapsedMs,
                completion = orbPresentationCompletion,
                reduceMotion = motionPolicy.reduceMotion,
                lowPerformance = motionPolicy.lowPerformance,
                fromCamera = capture != null,
                orbLifeState = orbLifeState,
                waterLightFrame = waterLightFrame,
                energyEvent = energyProducerEvent,
                waterLightMaterial = orbHost.material,
                // Source-core fallback requires the expected scene identity and geometry to validate.
                renderIsolationMode = orbRenderIsolationMode,
                hostRejectReason = orbHostRejectReason,
                visualProfile = OrbStage02VisualProfile.PRODUCTION_V1,
                stage02LayerIsolation = debugOrbLayerIsolation,
                debugOpticalSampleMs = debugOrbOpticalSampleMs,
                debugCaptureId = debugOrbCaptureId,
                debugExcludedPathId = debugOrbExcludedPathId,
                onShaderRuntimeStatus = { stage, route, status ->
                    debugOrbShaderStage = stage
                    debugOrbShaderRoute = route
                    debugOrbShaderStatus = status
                },
                onOrbRuntimeDiagnostics = { payload ->
                    recognitionEvidenceRecorder?.recordOrbRuntime(
                        fileName = "orb_render_trace.jsonl",
                        type = payload["eventType"]?.toString() ?: "ORB_RUNTIME_SAMPLE",
                        payload = payload + mapOf(
                            "sessionIdHash" to com.yujian.ai.ui.recognition.diagnostics.sha256(sceneIdentity.sessionId),
                            "attemptIdHash" to com.yujian.ai.ui.recognition.diagnostics.sha256(sceneIdentity.attemptId),
                            "imageIdHash" to com.yujian.ai.ui.recognition.diagnostics.sha256(sceneIdentity.imageId),
                        ),
                    )
                },
                onGlRenderDiagnostics = { identity, payload ->
                    recognitionEvidenceRecorder?.recordGpuRenderResult(identity, payload)
                },
                onR06RenderedPaths = { front, renderPass, pathIds ->
                    if (BuildConfig.DEBUG && waterLightFrame.sceneFrame.stage == RecognitionSceneStage.ORB_WAKE) {
                        val actual = pathIds.toSet()
                        if (front) {
                            if (debugR06FrontPaths != actual) debugR06FrontPaths = actual
                        } else if (renderPass == OrbR06RenderPass.GLASS_TRANSMISSION) {
                            if (debugR06BackTransmissionPaths != actual) debugR06BackTransmissionPaths = actual
                        } else if (debugR06BackPaths != actual) {
                            debugR06BackPaths = actual
                        }
                    }
                },
                onR06PathDiagnostics = { front, renderPass, metrics ->
                    val frame = waterLightFrame.sceneFrame
                    val sampleBucket = frame.sceneElapsedMs / 250L
                    val throttleKey = "${frame.stage.name}|$front|${renderPass.name}"
                    if (r06EvidenceSampleBuckets[throttleKey] != sampleBucket) {
                        r06EvidenceSampleBuckets[throttleKey] = sampleBucket
                        val resident = OrbResidentFilamentMotionV1.lifecycle(
                            frame.stage,
                            frame.stageProgress,
                            frame.orbToGlyphAccepted,
                        )
                        recognitionEvidenceRecorder?.recordOrbRuntime(
                            fileName = "orb_render_trace.jsonl",
                            type = "R06_RESIDENT_PATH_RENDER_SAMPLE",
                            payload = mapOf(
                                "stage" to frame.stage.name,
                                "stageProgress" to frame.stageProgress,
                                "sceneElapsedMs" to frame.sceneElapsedMs,
                                "geometryVersion" to frame.geometryVersion,
                                "front" to front,
                                "renderPass" to renderPass.name,
                                "residentLifecycle" to resident.lifecycle.name,
                                "residentAlpha" to resident.effectiveAlpha,
                                "orbToGlyphAccepted" to frame.orbToGlyphAccepted,
                                "pathDataSource" to "CANVAS_RENDER_METRICS_NOT_PIXEL_READBACK",
                                "paths" to metrics.map { metric ->
                                    val spec = OrbRuntimeProfileV1.paths.firstOrNull { it.id == metric.pathId }
                                    val motion = spec?.let(OrbResidentFilamentMotionV1::parameters)
                                    mapOf(
                                        "pathId" to metric.pathId,
                                        "role" to OrbResidentFilamentMotionV1.role(metric.pathId).name,
                                        "periodMs" to motion?.periodMs,
                                        "phase" to motion?.phase,
                                        "direction" to motion?.direction,
                                        "validSampleCount" to metric.validSampleCount,
                                        "bodyValidSegmentCount" to metric.bodyValidSegmentCount,
                                        "drawnRunCount" to metric.bodyDrawnRunCount,
                                        "bodyPaintAlpha" to metric.bodyPaintAlpha,
                                        "bodyStrokeWidthPx" to metric.bodyStrokeWidthPx,
                                        "bodyAlpha" to metric.bodyFinalAlpha,
                                        "transmissionFactor" to metric.transmissionFactor,
                                        "edgeFade" to metric.edgeFade,
                                        "pathPhaseU" to metric.pathPhaseU,
                                        "trailStartU" to metric.effectiveTrailStartU,
                                        "trailEndU" to metric.effectiveTrailEndU,
                                    )
                                },
                            ),
                        )
                    }
                    if (BuildConfig.DEBUG && waterLightFrame.sceneFrame.stage == RecognitionSceneStage.ORB_WAKE) {
                        val next = debugR06Diagnostics.filterNot {
                            it.front == front && it.renderPass == renderPass
                        } + metrics
                        if (debugR06Diagnostics != next) debugR06Diagnostics = next
                    }
                },
                modifier = Modifier.offset { IntOffset(orbX.roundToInt(), orbY.roundToInt()) },
            )
            Text(progressLabel, color = Color(0xFF123F48), fontSize = if (maxHeight < 600.dp) 14.sp else 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset { IntOffset((plan.statusCenterXPx - with(density) { statusWidth.toPx() } / 2f).roundToInt(),
                    plan.statusPanelTopPx.roundToInt()) }.size(width = statusWidth, height = (24f * density.fontScale).dp))

        }
        if (diagnosticUiAvailable) {
            val currentStage = diagnosticSceneFrame.stage
            RecognitionStepDiagnosticControls(
                displayMode = displayMode,
                stage = currentStage,
                playbackMode = playbackController.mode,
                previewElapsedMs = playbackController.previewElapsedMs(visualNowMs),
                previewCompleted = playbackController.previewCompleted,
                playbackPaused = playbackController.paused,
                mappedReferenceMs = diagnosticSceneFrame.opticalReferenceElapsedMs,
                modelStatus = when {
                    pipelineError != null -> "模型失败"
                    finishedResult?.ready == true && !RecognitionRevealDecisionGate.isReady(revealDecision) -> "Classifier 已返回 · 等待 Mask 终态"
                    finishedResult?.ready == true -> "模型已完成"
                    else -> "模型处理中"
                },
                detectorStatus = when {
                    pipelineError != null || realPhase == RecognitionPhase.FAILURE -> "失败"
                    assessment?.primary?.box != null -> "就绪"
                    realPhase == RecognitionPhase.DETECTING -> "运行中"
                    else -> "等待"
                },
                classifierStatus = when {
                    pipelineError != null || realPhase == RecognitionPhase.FAILURE -> "失败"
                    finishedResult?.ready == true -> "就绪"
                    finishedResult != null -> "已返回"
                    realPhase == RecognitionPhase.CLASSIFYING -> "运行中"
                    assessment?.primary?.box != null -> "排队"
                    else -> "等待"
                },
                slimSamStatus = diagnosticSceneFrame.modelGates.slimSam.name,
                maskStatus = when {
                    trustedSubjectMask -> "Mask TRUSTED"
                    subjectResult.status == SubjectStatus.PROCESSING -> "Mask 计算中"
                    subjectResult.status == SubjectStatus.FAILED -> "Mask 失败 · BBOX_ONLY"
                    assessment?.primary?.box != null && subjectResult.status == SubjectStatus.READY -> "Mask BBOX_ONLY"
                    else -> "Mask 等待"
                },
                geometryStatus = diagnosticSceneFrame.modelGates.geometry.name,
                sceneStatus = diagnosticSceneFrame.modelGates.runState.name,
                stepStatus = diagnosticActionStatus ?: diagnosticStageStatus,
                normalProgressText = "",
                canGoPrevious = playbackController.canControl() && currentStage != RecognitionSceneStage.entries.first(),
                canGoNext = playbackController.canControl() && currentStage != RecognitionSceneStage.entries.last(),
                canControl = playbackController.canControl(),
                onDisplayModeChange = ::changeDiagnosticDisplayMode,
                onPrevious = {
                    val now = recognitionMonotonicNowMs()
                    playbackController.returnToPreviousStage(now)
                    diagnosticActionStatus = null
                    playbackRevision++
                },
                onNext = {
                    val now = recognitionMonotonicNowMs()
                    val next = playbackController.advanceToNextStage(now)
                    diagnosticActionStatus = if (next == null) "已到末步" else null
                    if (next != null) playbackRevision++
                },
                onPauseResume = {
                    val now = recognitionMonotonicNowMs()
                    if (playbackController.paused) playbackController.resume(authoritativeOpticalReferenceElapsedMs, now)
                    else playbackController.pause(authoritativeOpticalReferenceElapsedMs, now)
                    playbackRevision++
                },
                onReplay = {
                    playbackController.restartCurrentStage(recognitionMonotonicNowMs())
                    diagnosticActionStatus = null
                    playbackRevision++
                },
                onAutoPlay = {
                    playbackController.playDiagnosticAuto(
                        authoritativeOpticalReferenceElapsedMs,
                        recognitionMonotonicNowMs(),
                    )
                    diagnosticActionStatus = null
                    playbackRevision++
                },
                orbIsolationMode = if (
                    diagnosticUiAvailable &&
                    displayMode == RecognitionProgressDisplayMode.STEP_DIAGNOSTIC &&
                    currentStage == RecognitionSceneStage.ORB_WAKE &&
                    debugOrbIsolationHostAccepted
                ) debugOrbIsolationMode else null,
                onOrbIsolationModeChange = { mode ->
                    if (debugOrbIsolationHostAccepted && mode != OrbRenderIsolationMode.SUPPRESSED) {
                        debugOrbIsolationMode = mode
                        debugR06BackPaths = emptySet()
                        debugR06BackTransmissionPaths = emptySet()
                        debugR06FrontPaths = emptySet()
                        debugR06Diagnostics = emptyList()
                    }
                },
                orbVisualProfile = if (
                    diagnosticUiAvailable && displayMode == RecognitionProgressDisplayMode.STEP_DIAGNOSTIC &&
                    currentStage == RecognitionSceneStage.ORB_WAKE && debugOrbIsolationHostAccepted
                ) debugOrbVisualProfile else null,
                onOrbVisualProfileChange = { profile ->
                    if (debugOrbIsolationHostAccepted) {
                        debugOrbVisualProfile = profile
                        if (profile == OrbStage02VisualProfile.BASELINE) {
                            debugR06BackTransmissionPaths = emptySet()
                            debugR06Diagnostics = debugR06Diagnostics.filterNot {
                                it.renderPass == OrbR06RenderPass.GLASS_TRANSMISSION
                            }
                        }
                    }
                },
                orbLayerIsolation = if (
                    diagnosticUiAvailable && displayMode == RecognitionProgressDisplayMode.STEP_DIAGNOSTIC &&
                    currentStage == RecognitionSceneStage.ORB_WAKE && debugOrbIsolationHostAccepted
                ) debugOrbLayerIsolation else null,
                onOrbLayerIsolationChange = { isolation ->
                    if (debugOrbIsolationHostAccepted) {
                        debugOrbLayerIsolation = isolation
                        debugR06BackPaths = emptySet()
                        debugR06BackTransmissionPaths = emptySet()
                        debugR06FrontPaths = emptySet()
                        debugR06Diagnostics = emptyList()
                    }
                },
                orbEvidenceProgress = if (
                    BuildConfig.DEBUG && diagnosticUiAvailable &&
                    displayMode == RecognitionProgressDisplayMode.STEP_DIAGNOSTIC &&
                    currentStage == RecognitionSceneStage.ORB_WAKE && debugOrbIsolationHostAccepted
                ) debugOrbEvidenceProgress else null,
                onOrbEvidenceProgressChange = { progress ->
                    if (debugOrbIsolationHostAccepted && progress in 0f..1f) {
                        val nowMs = recognitionMonotonicNowMs()
                        val targetPreviewMs = (progress * DIAGNOSTIC_STEP_DURATION_MS).roundToLong()
                        // First anchor the scene clock to real uptime, then use its existing
                        // manual-preview start point to select an exact paused diagnostic frame.
                        playbackController.seek(RecognitionSceneStage.ORB_WAKE, nowMs)
                        playbackController.seek(RecognitionSceneStage.ORB_WAKE, nowMs - targetPreviewMs)
                        playbackController.pause(authoritativeOpticalReferenceElapsedMs, nowMs)
                        debugOrbEvidenceProgress = progress
                        diagnosticActionStatus = null
                        playbackRevision++
                    }
                },
                orbRuntimeStatus = if (
                    diagnosticUiAvailable && displayMode == RecognitionProgressDisplayMode.STEP_DIAGNOSTIC &&
                    currentStage == RecognitionSceneStage.ORB_WAKE && debugOrbIsolationHostAccepted
                ) {
                    val requestedField = OrbInteriorOpticalField.frame(
                        currentStage,
                        diagnosticSceneFrame.stageProgress,
                        motionPolicy.reduceMotion,
                        debugOrbVisualProfile,
                        waterLightFrame.elapsedMs,
                    )
                    val fluidRuntimeEligible = debugOrbIsolationMode == OrbRenderIsolationMode.NORMAL &&
                        debugOrbLayerIsolation != OrbStage02LayerIsolation.R06_ONLY &&
                        debugOrbLayerIsolation != OrbStage02LayerIsolation.R06_BACK_TRANSMISSION_ONLY &&
                        debugOrbVisualProfile == OrbStage02VisualProfile.MAX &&
                        debugOrbShaderRoute == OrbShaderRoute.FLUID_GRASS_V1_2_CANDIDATE.name &&
                        debugOrbShaderStatus == "READY"
                    val flowStrength = if (fluidRuntimeEligible) requestedField.wakeStrength else 0f
                    val displacementD = if (fluidRuntimeEligible) requestedField.maxDisplacementD else 0f
                    "${debugOrbShaderStage} ${(diagnosticSceneFrame.stageProgress * 100f).roundToInt()}% · " +
                        "${debugOrbVisualProfile.name} · ${debugOrbShaderRoute}/${debugOrbShaderStatus} · " +
                        "reduce=${motionPolicy.reduceMotion} low=${motionPolicy.lowPerformance} · " +
                        "runtimeEligibility(inferred)=$fluidRuntimeEligible flow=$flowStrength d=$displacementD · " +
                        "R06VisiblePathCount=${debugR06BackPaths.union(debugR06BackTransmissionPaths).union(debugR06FrontPaths).size} " +
                        "back=${debugR06BackPaths.joinToString()} glass=${debugR06BackTransmissionPaths.joinToString()} " +
                        "front=${debugR06FrontPaths.joinToString()}"
                } else null,
                onExportOrbVisualEvidence = if (
                    BuildConfig.DEBUG && diagnosticUiAvailable &&
                    displayMode == RecognitionProgressDisplayMode.STEP_DIAGNOSTIC &&
                    currentStage == RecognitionSceneStage.ORB_WAKE && debugOrbIsolationHostAccepted
                ) {
                    {
                        orbEvidenceScope.launch {
                            val wasPaused = playbackController.paused
                            val pinnedOpticalMs = authoritativeOpticalReferenceElapsedMs
                            if (!wasPaused) playbackController.pause(pinnedOpticalMs, recognitionMonotonicNowMs())
                            playbackRevision++
                            val originalMode = debugOrbIsolationMode
                            val originalLayer = debugOrbLayerIsolation
                            val originalSampleMs = debugOrbOpticalSampleMs
                            val originalCaptureId = debugOrbCaptureId
                            val originalExcludedPathId = debugOrbExcludedPathId
                            try {
                                val requestedField = OrbInteriorOpticalField.frame(
                                    diagnosticSceneFrame.stage,
                                    diagnosticSceneFrame.stageProgress,
                                    motionPolicy.reduceMotion,
                                    debugOrbVisualProfile,
                                    waterLightFrame.elapsedMs,
                                )
                                val fluidRuntimeEligibilityInferred = debugOrbIsolationMode == OrbRenderIsolationMode.NORMAL &&
                                    debugOrbLayerIsolation != OrbStage02LayerIsolation.R06_ONLY &&
                                    debugOrbLayerIsolation != OrbStage02LayerIsolation.R06_BACK_TRANSMISSION_ONLY &&
                                    debugOrbVisualProfile == OrbStage02VisualProfile.MAX &&
                                    debugOrbShaderRoute == OrbShaderRoute.FLUID_GRASS_V1_2_CANDIDATE.name &&
                                    debugOrbShaderStatus == "READY"
                                OrbStage02VisualEvidenceExport.share(
                                    context = pageContext,
                                    view = pageView,
                                    orbBoundsInView = android.graphics.Rect(debugOrbEvidenceBounds),
                                    metadata = OrbStage02VisualEvidenceExport.Metadata(
                                        sessionId = waterLightFrame.sessionId,
                                        attemptId = waterLightFrame.attemptId,
                                        imageId = waterLightFrame.imageId,
                                        stage = diagnosticSceneFrame.stage,
                                        stageProgress = diagnosticSceneFrame.stageProgress,
                                        sceneElapsedMs = waterLightFrame.elapsedMs,
                                        geometryVersion = diagnosticSceneFrame.geometryVersion,
                                        visualProfile = debugOrbVisualProfile,
                                        renderIsolationMode = debugOrbIsolationMode,
                                        layerIsolation = debugOrbLayerIsolation,
                                        shaderRoute = debugOrbShaderRoute,
                                        shaderStatus = debugOrbShaderStatus,
                                        coreSizePx = debugOrbEvidenceCoreSizePx,
                                        reducedMotion = motionPolicy.reduceMotion,
                                        lowPerformance = motionPolicy.lowPerformance,
                                        interiorFlowStrength = requestedField.wakeStrength,
                                        interiorDisplacementD = requestedField.maxDisplacementD,
                                        fluidRuntimeEligibilityInferred = fluidRuntimeEligibilityInferred,
                                        r06BackPathIds = debugR06BackPaths,
                                        r06BackTransmissionPathIds = debugR06BackTransmissionPaths,
                                        r06FrontPathIds = debugR06FrontPaths,
                                        r06PathDiagnostics = debugR06Diagnostics.filter {
                                            it.stage == diagnosticSceneFrame.stage &&
                                                kotlin.math.abs(it.stageProgress - diagnosticSceneFrame.stageProgress) <= .01f &&
                                                it.geometryVersion == diagnosticSceneFrame.geometryVersion
                                        },
                                    ),
                                ) { capture ->
                                    debugOrbIsolationMode = capture.mode
                                    debugOrbLayerIsolation = capture.layer
                                    debugOrbOpticalSampleMs = capture.sampledSceneElapsedMs
                                    debugOrbCaptureId = capture.captureId
                                    debugOrbExcludedPathId = capture.excludedPathId
                                }
                            } catch (failure: Throwable) {
                                if (failure is CancellationException) throw failure
                                val errorCode = failure::class.simpleName ?: "EXPORT_FAILURE"
                                diagnosticActionStatus = "Orb证据导出失败 · $errorCode"
                                android.util.Log.e(
                                    "ORB_CAPTURE",
                                    "eventType=PIXEL_COPY_FAILED errorCode=$errorCode stage=${diagnosticSceneFrame.stage.name} " +
                                        "stageProgress=${diagnosticSceneFrame.stageProgress} geometryVersion=${waterLightFrame.geometryVersion}",
                                    failure,
                                )
                                OrbStage02RenderTrace.record(
                                    eventType = "PIXEL_COPY_FAILED",
                                    dataSource = "RUNTIME_STATE",
                                    fields = mapOf(
                                        "exceptionType" to (failure::class.qualifiedName ?: failure.javaClass.name),
                                        "reason" to failure.message,
                                        "stage" to diagnosticSceneFrame.stage.name,
                                        "stageProgress" to diagnosticSceneFrame.stageProgress,
                                        "geometryVersion" to waterLightFrame.geometryVersion,
                                    ),
                                )
                            } finally {
                                debugOrbIsolationMode = originalMode
                                debugOrbLayerIsolation = originalLayer
                                debugOrbOpticalSampleMs = originalSampleMs
                                debugOrbCaptureId = originalCaptureId
                                debugOrbExcludedPathId = originalExcludedPathId
                                if (!wasPaused) {
                                    playbackController.resume(pinnedOpticalMs, recognitionMonotonicNowMs())
                                    playbackRevision++
                                }
                            }
                        }
                    }
                } else null,
            )
        }

        if (BuildConfig.DEBUG) {
            val debugStage = diagnosticSceneFrame.stage
            val debugWaiting = diagnosticSceneFrame.modelGates.waitingReason ?: "NONE"
            val debugClassifier = diagnosticSceneFrame.modelGates.classifier.name
            val debugSlimSam = diagnosticSceneFrame.modelGates.slimSam.name
            val debugMaskTrust = if (trustedSubjectMask) "TRUSTED" else "UNTRUSTED"
            val debugRejectedEvent = controller.lastRejectedEnergyEvent?.let {
                "${it.event.feedback}/${it.reason.name}"
            } ?: "NONE"
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = safeInsets.calculateBottomPadding() + 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (BuildConfig.DEBUG && !diagnosticUiAvailable) Text(
                    "${debugStage.name} · Gate: ${controller.gateSnapshot(visualNowMs)["unmetConditions"]}",
                    color = Color(0xFF31545A), fontSize = 10.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                if (diagnosticUiAvailable) Text(
                    "${debugStage.ordinal + 1}/13 ${debugStage.name} · wait=$debugWaiting · " +
                        "Classifier=$debugClassifier SlimSAM=$debugSlimSam Mask=$debugMaskTrust · " +
                        "Reveal=$revealDecision · reject=$debugRejectedEvent",
                    color = Color(0xFF31545A),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (recognitionEvidenceRecorder != null) {
                    TextButton(
                        enabled = evidenceExportStatus != "EXPORTING" && evidenceExportStatus != "SAVING",
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("processing_export_diagnostics_button"),
                        onClick = {
                            if (evidenceExportStatus == "EXPORTING" || evidenceExportStatus == "SAVING") return@TextButton
                            Log.i(LOG_TAG, "EVIDENCE_EXPORT_TAP session=${sceneIdentity.sessionId} attempt=${sceneIdentity.attemptId} stage=${controller.currentStage} state=$evidenceExportStatus atMs=${SystemClock.uptimeMillis()} sceneKey=$sceneKey")
                            evidenceExportStatus = "EXPORTING"
                            orbEvidenceScope.launch {
                                try {
                                    val exported = RecognitionEvidenceZipExporter.export(
                                        pageContext,
                                        recognitionEvidenceRecorder,
                                        stage = controller.currentStage.name,
                                    )
                                    pendingEvidenceExport = exported
                                    val kb = (exported.sizeBytes / 1024L).coerceAtLeast(1L)
                                    evidenceExportStatus = "PACKAGED · ${exported.integrityStatus} · ${kb}KB · 请选择保存位置"
                                    saveEvidenceLauncher.launch(exported.file.name)
                                    Log.i(
                                        LOG_TAG,
                                        "PROCESSING_EVIDENCE_EXPORTED sha256=${exported.sha256} " +
                                            "sizeBytes=${exported.sizeBytes} integrity=${exported.integrityStatus} " +
                                            "savePickerLaunched=true degraded=${exported.degradedReasons}",
                                    )
                                } catch (failure: Throwable) {
                                    if (failure is CancellationException) throw failure
                                    evidenceExportStatus = "FAILED · 导出失败 · ${failure.message ?: failure::class.simpleName}"
                                    Log.e(
                                        LOG_TAG,
                                        "PROCESSING_EVIDENCE_EXPORT_FAILED stage=${debugStage.name} " +
                                            "session=${sceneIdentity.sessionId} attempt=${sceneIdentity.attemptId}",
                                        failure,
                                    )
                                }
                            }
                        },
                    ) {
                        Text(if (evidenceExportStatus == "EXPORTING" || evidenceExportStatus == "SAVING") "正在导出本次识鱼诊断 ZIP…" else "导出本次识鱼诊断包")
                    }
                    if (pendingEvidenceExport != null) TextButton(onClick = {
                        val outcome = RecognitionEvidenceZipExporter.share(pageContext, pendingEvidenceExport!!)
                        Log.i(LOG_TAG, "EXPORT_SHARE_FALLBACK outcome=$outcome stage=${controller.currentStage}")
                        if (!outcome.chooserLaunched) evidenceExportStatus = "FAILED · 分享失败 · ${outcome.errorReason}"
                    }) { Text("备用：分享已生成的 ZIP") }
                    Text(
                        text = when {
                            evidenceExportStatus == "READY" -> "诊断包状态：待导出"
                            evidenceExportStatus == "EXPORTING" -> "诊断包状态：正在冻结快照并打包导出…"
                            evidenceExportStatus == "SAVING" -> "诊断包状态：正在保存到所选目录…"
                            else -> "诊断包状态：$evidenceExportStatus"
                        },
                        color = Color(0xFF31545A),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("processing_export_diagnostics_status"),
                    )
                }
            }
        }
    }
}
private enum class RecognitionLayerMode { PHOTO_ONLY, PHOTO_GLES, PHOTO_GLES_AMBIENT, FULL }

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
    RecognitionSceneStage.ORB_RETURN,
    RecognitionSceneStage.ORB_TO_NAME,
    RecognitionSceneStage.SPECIES_NAME_REVEAL,
    RecognitionSceneStage.RESIDUE_SETTLE -> RecognitionPhase.CLASSIFYING
}

@Composable
private fun RecognitionPhoto(
    waterLightFrame: WaterLightFrame,
    waterLightEngine: UnifiedWaterLightEngine,
    energyEvent: EnergyEvent?,
    energyContactPointNormalizedSource: WaterLightPathPoint?,
    revealDecision: RecognitionRevealDecisionState,
    bitmap: Bitmap,
    photoFlowPlan: RecognitionPhotoFlowPlan,
    orbCenter: Offset,
    orbAnchorReady: Boolean,
    orbRadiusPx: Float,
    photoEntryProgress: Float,
    phase: RecognitionPhase,
    focusBox: NormalizedFishBox?,
    subjectBitmap: Bitmap?,
    subjectBox: NormalizedFishBox?,
    contour: List<RecognitionContourSegment>,
    visualClockMs: Long,
    deterministicMotion: Boolean,
    processingElapsedMs: Long,
    opticalReferenceElapsedMs: Long,
    photoShockwave: PhotoShockwaveTransform,
    motionSessionKey: Any?,
    phaseElapsedMs: Long,
    motionPolicy: RecognitionMotionPolicy,
    onMotionFrame: ((RecognitionMotionTraceSample) -> Unit)?,
    focusLevel: RecognitionFishFocusLevel,
    onFishFocusTransform: ((RecognitionImageTransform) -> Unit)?,
    onPhotoGeometryChanged: (Long) -> Unit,
    modifier: Modifier,
    onProcessingPhotoSnapshot: (
        RecognitionImageTransform,
        RecognitionRevealWindowRect,
        RecognitionRevealWindowRect,
        Float,
        Offset,
        Float,
        WaterLightPathPoint?,
        String,
        Long,
        Long,
    ) -> Unit,
    onPhotoFirstFramePresented: (Long) -> Unit,
    processingClockStarted: Boolean,
    recognitionSessionId: String,
    recognitionAttemptId: String,
    imageId: String,
    photoSha256: String,
    photoContentVersion: Long,
    maskSha256: String,
    maskVersion: Long,
    activeFishPathId: String?,
    maskRevealProgress: Float,
    trustedMask: Boolean,
    stage045GlPresented: Boolean = false,
    onGoldRenderComplete: ((RecognitionFishGoldRenderReceipt) -> Boolean)? = null,
    onContourRenderComplete: ((RecognitionContourRenderReceipt) -> Boolean)? = null,
) {
    val transform = remember(photoFlowPlan, photoEntryProgress) {
        interpolateRecognitionPhotoTransform(
            photoFlowPlan.startTransform,
            photoFlowPlan.targetTransform,
            photoEntryProgress,
        )
    }
    val settledGeometryVersion = remember(photoFlowPlan.targetTransform) {
        WaterLightGeometryVersion.of(photoFlowPlan.targetTransform)
    }
    LaunchedEffect(settledGeometryVersion) { onPhotoGeometryChanged(settledGeometryVersion) }
    val waterHosts = remember(waterLightEngine, recognitionSessionId, recognitionAttemptId, imageId, photoSha256, photoContentVersion) {
        listOf(WaterLightHost.PERIMETER, WaterLightHost.PATH, WaterLightHost.CONTOUR).associateWith { host ->
            WaterLightHostBinding(
                host, WaterLightMaterialProfile.forHost(host), waterLightEngine,
                RecognitionSceneIdentity(
                    recognitionSessionId, recognitionAttemptId, imageId, photoSha256, photoContentVersion,
                ),
            )
        }
    }
    val photoBounds = transform.photoContentRect
    fun hostReady(host: WaterLightHost, left: Float, top: Float, right: Float, bottom: Float): Boolean =
        waterHosts.getValue(host).accepts(waterLightFrame,
            WaterLightHostGeometry(host, waterLightFrame.geometryVersion, left, top, right, bottom))
    val perimeterReady = hostReady(WaterLightHost.PERIMETER,
        photoBounds.left, photoBounds.top, photoBounds.right, photoBounds.bottom)
    val pathReady = hostReady(WaterLightHost.PATH,
        photoBounds.left, photoBounds.top, photoBounds.right, photoBounds.bottom)
    val contourStart = focusBox?.let { transform.mapNormalized(it.x1, it.y1) }
    val contourEnd = focusBox?.let { transform.mapNormalized(it.x2, it.y2) }
    val fishEnergyAllowed = trustedMask && revealDecision == RecognitionRevealDecisionState.FISH_FOCUS_READY
    val contourReady = fishEnergyAllowed && contourStart != null && contourEnd != null && hostReady(WaterLightHost.CONTOUR,
        contourStart.x, contourStart.y, contourEnd.x, contourEnd.y)
    val waterScope = rememberCoroutineScope()
    val stableContactSource = energyContactPointNormalizedSource
    val dropletRenderer = remember(waterLightEngine) { WaterDropletRenderer() }
    DisposableEffect(dropletRenderer) {
        onDispose { dropletRenderer.release() }
    }
    // Candidate additive material only after the frozen P0/P2/P3 samples.
    val additiveMaterialActive = pathReady && waterLightFrame.phase == WaterLightPhase.PROCESSING &&
        waterLightFrame.sceneFrame.stage.ordinal in RecognitionSceneStage.PATH_TO_FISH.ordinal..RecognitionSceneStage.FISH_GOLD_PEAK.ordinal &&
        !waterLightFrame.reducedMotion && !motionPolicy.lowPerformance
    val photoImage = remember(bitmap) { bitmap.asImageBitmap() }
    val reportedFirstFrame = remember(bitmap, motionSessionKey) { AtomicBoolean(false) }
    val firstFrameCallback by rememberUpdatedState(onPhotoFirstFramePresented)
    val photoSnapshotCallback by rememberUpdatedState(onProcessingPhotoSnapshot)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    DisposableEffect(mainHandler) {
        onDispose { mainHandler.removeCallbacksAndMessages(null) }
    }
    val density = LocalDensity.current
    val viewportWidthPx = photoFlowPlan.targetTransform.viewportWidthPx.coerceAtLeast(1f)
    val viewportHeightPx = photoFlowPlan.targetTransform.viewportHeightPx.coerceAtLeast(1f)
    val photoPivot = TransformOrigin(
        pivotFractionX = photoFlowPlan.targetPhotoRect.center.x.div(viewportWidthPx).coerceIn(0f, 1f),
        pivotFractionY = photoFlowPlan.targetPhotoRect.center.y.div(viewportHeightPx).coerceIn(0f, 1f),
    )
    val photoClipPath = remember { Path() }
    val additiveClipPath = remember { Path() }
    var selectedLayerMode by remember(bitmap, motionSessionKey) { mutableStateOf(RecognitionLayerMode.FULL) }
    val layerMode = selectedLayerMode
    var viewportBounds by remember(bitmap, motionSessionKey) { mutableStateOf<RecognitionRevealWindowRect?>(null) }

    LaunchedEffect(
        photoEntryProgress, viewportBounds, photoFlowPlan.targetTransform, orbCenter, orbAnchorReady,
        stableContactSource, maskSha256, maskVersion, waterLightFrame.geometryVersion,
    ) {
        if (photoEntryProgress < 1f || !orbAnchorReady) return@LaunchedEffect
        val bounds = viewportBounds ?: return@LaunchedEffect
        val photoRect = photoFlowPlan.targetTransform.photoContentRect
        val photoBounds = RecognitionRevealWindowRect(
            left = bounds.left + photoRect.left,
            top = bounds.top + photoRect.top,
            right = bounds.left + photoRect.right,
            bottom = bounds.top + photoRect.bottom,
        )
        photoSnapshotCallback(
            photoFlowPlan.targetTransform,
            bounds,
            photoBounds,
            with(density) { 16.dp.toPx() },
            orbCenter,
            orbRadiusPx * 2f,
            stableContactSource,
            maskSha256,
            maskVersion,
            if (stableContactSource != null) waterLightFrame.geometryVersion else 0L,
        )
    }
    if (onFishFocusTransform != null) {
        LaunchedEffect(photoEntryProgress, photoFlowPlan.targetTransform, onFishFocusTransform) {
            if (photoEntryProgress >= 1f) onFishFocusTransform(photoFlowPlan.targetTransform)
        }
    }

    Box(
        modifier.fillMaxSize().onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            viewportBounds = RecognitionRevealWindowRect(bounds.left, bounds.top, bounds.right, bounds.bottom)
        },
    ) {
        // The outer measured container stays fixed. Only this shared photo + optical group
        // receives the bounded shockwave; status UI and handoff geometry stay stationary.
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                transformOrigin = photoPivot
                translationY = with(density) { photoShockwave.translationYDp.dp.toPx() }
                rotationZ = photoShockwave.rotationDegrees
                scaleX = photoShockwave.scale
                scaleY = photoShockwave.scale
            },
        ) {
            Canvas(
                Modifier.fillMaxSize().drawWithContent {
                    drawContent()
                    if (reportedFirstFrame.compareAndSet(false, true)) {
                        // The first frame containing the real PhotoSession starts the sole scene clock.
                        mainHandler.post {
                            Choreographer.getInstance().postFrameCallback {
                                val presentedAtMs = recognitionMonotonicNowMs()
                                firstFrameCallback(presentedAtMs)
                            }
                        }
                    }
                },
            ) {
                val rect = transform.photoContentRect
                val corner = 16.dp.toPx() * photoEntryProgress.coerceIn(0f, 1f)
                photoClipPath.reset()
                photoClipPath.addRoundRect(
                    RoundRect(
                        rect.left,
                        rect.top,
                        rect.right,
                        rect.bottom,
                        CornerRadius(corner, corner),
                    ),
                )
                drawRoundRect(Color(0xFF2D6C75).copy(alpha = .08f * photoEntryProgress),
                    topLeft = Offset(rect.left - 2.dp.toPx(), rect.top + 3.dp.toPx()),
                    size = Size(rect.width + 4.dp.toPx(), rect.height + 4.dp.toPx()),
                    cornerRadius = CornerRadius(corner + 2.dp.toPx()))
                drawRoundRect(Color.White.copy(alpha = .62f * photoEntryProgress),
                    topLeft = Offset(rect.left - 1.dp.toPx(), rect.top - 1.dp.toPx()),
                    size = Size(rect.width + 2.dp.toPx(), rect.height + 2.dp.toPx()),
                    cornerRadius = CornerRadius(corner + 1.dp.toPx()), style = Stroke(1.dp.toPx()))
                clipPath(photoClipPath) {
                    drawImage(
                        image = photoImage,
                        dstOffset = IntOffset(transform.translationX.roundToInt(), transform.translationY.roundToInt()),
                        dstSize = IntSize(transform.drawnWidth.roundToInt(), transform.drawnHeight.roundToInt()),
                    )
                }
            }
            // Stage045 owns the perimeter once its GL surface has presented a frame.
            // Drawing the photo-sized legacy TextureView as well creates a second
            // independently positioned perimeter, visible as a detached light box.
            if (perimeterReady && !stage045GlPresented &&
                (!BuildConfig.DEBUG || layerMode != RecognitionLayerMode.PHOTO_ONLY)) RecognitionPerimeterShaderOverlay(
                waterLightFrame = waterLightFrame,
                bitmap = bitmap,
                photoTransform = transform,
                elapsedMs = processingElapsedMs,
                processingClockStarted = processingClockStarted,
                sessionKey = motionSessionKey,
                recognitionSessionId = recognitionSessionId,
                recognitionAttemptId = recognitionAttemptId,
                imageId = imageId,
                photoSha256 = photoSha256,
                phase = phase,
                material = WaterLightMaterialProfile.PERIMETER,
                energyEvent = energyEvent,
                reduceMotion = motionPolicy.reduceMotion,
                modifier = Modifier.semantics {
                    contentDescription = "正在识别的鱼获照片"
                },
            )
            if (pathReady && (!BuildConfig.DEBUG || layerMode == RecognitionLayerMode.PHOTO_GLES_AMBIENT || layerMode == RecognitionLayerMode.FULL)) RecognitionAmbientField(
                phase,
                Modifier.fillMaxSize(),
                visualClockMs,
                motionSessionKey = motionSessionKey,
                deterministicMotion = deterministicMotion,
                lowPerformance = motionPolicy.lowPerformance,
                reduceMotion = motionPolicy.reduceMotion,
                qualityLevel = motionPolicy.qualityLevel,
                onMotionFrame = onMotionFrame,
                photoTransform = transform,
                includeEdgeField = false,
                waterLightFrame = waterLightFrame,
            )
            if (pathReady && (energyEvent?.pathGeometry != null || energyEvent?.pathSegments?.isNotEmpty() == true) &&
                (!stage045GlPresented || energyEvent?.feedback == EnergyFeedback.RETURN) &&
                (!BuildConfig.DEBUG || layerMode == RecognitionLayerMode.FULL)
            ) WaterLightEnergyPathRenderer(
                frame = waterLightFrame,
                event = energyEvent,
                material = waterHosts.getValue(WaterLightHost.PATH).material,
                modifier = Modifier.fillMaxSize(),
            )
            if (additiveMaterialActive && !stage045GlPresented && (!BuildConfig.DEBUG || layerMode == RecognitionLayerMode.FULL)) {
                Canvas(Modifier.fillMaxSize()) {
                    val rect = transform.photoContentRect
                    additiveClipPath.reset()
                    additiveClipPath.addRoundRect(RoundRect(rect.left, rect.top, rect.right, rect.bottom,
                        CornerRadius(16.dp.toPx(), 16.dp.toPx())))
                    clipPath(additiveClipPath) {
                        drawIntoCanvas { canvas -> dropletRenderer.draw(canvas.nativeCanvas, waterLightFrame,
                            android.graphics.RectF(rect.left, rect.top, rect.right, rect.bottom),
                            seed = waterLightFrame.sessionId.hashCode().toLong() * 31 + photoSha256.hashCode(),
                            count = if (energyEvent?.feedback == EnergyFeedback.ABSORB) 3 else 1,
                            opacity = .45f)
                        }
                    }
                }
            }
            if (!BuildConfig.DEBUG || layerMode == RecognitionLayerMode.FULL) {
                val grassBounds = androidx.compose.ui.geometry.Rect(
                    photoBounds.left,
                    photoBounds.top + photoBounds.height * .86f,
                    photoBounds.right,
                    photoBounds.bottom,
                )
                val fishAvoidBounds = focusBox?.let { box ->
                    val topLeft = transform.mapNormalized(box.x1, box.y1)
                    val bottomRight = transform.mapNormalized(box.x2, box.y2)
                    androidx.compose.ui.geometry.Rect(
                        minOf(topLeft.x, bottomRight.x), minOf(topLeft.y, bottomRight.y),
                        maxOf(topLeft.x, bottomRight.x), maxOf(topLeft.y, bottomRight.y),
                    )
                }
                WaterGrassField(
                    frame = waterLightFrame,
                    modifier = Modifier.fillMaxSize(),
                    material = waterHosts.getValue(WaterLightHost.PATH).material,
                    opacity = .32f,
                    seed = waterLightFrame.sessionId.hashCode().toLong() xor imageId.hashCode().toLong(),
                    drawBounds = grassBounds,
                    avoidBounds = fishAvoidBounds,
                    energyEvent = energyEvent,
                )
            }
            if (contourReady && (!BuildConfig.DEBUG || layerMode == RecognitionLayerMode.FULL)) RecognitionFishFocus(
                phase,
                focusBox,
                subjectBitmap,
                subjectBox,
                contour,
                maskSha256,
                transform,
                Modifier.fillMaxSize(),
                processingElapsedMs = processingElapsedMs,
                opticalReferenceElapsedMs = opticalReferenceElapsedMs,
                maskRevealProgress = maskRevealProgress,
                reduceMotion = motionPolicy.reduceMotion,
                lowPerformance = motionPolicy.lowPerformance,
                trustedMask = trustedMask,
                maskVersion = subjectBitmap?.let { RecognitionMaskIntegrity.versionFromSha256(maskSha256) } ?: -1L,
                fishPathId = activeFishPathId,
                onGoldRenderComplete = onGoldRenderComplete,
                onContourRenderComplete = onContourRenderComplete,
                waterLightFrame = waterLightFrame,
                energyEvent = energyEvent,
                material = waterHosts.getValue(WaterLightHost.CONTOUR).material,
                contourPhotoSpace = true,
            )
        }
        if (BuildConfig.DEBUG) Text(
            text = layerMode.name,
            color = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).background(Color(0xAA10252A))
                .clickable {
                    selectedLayerMode = RecognitionLayerMode.entries[(selectedLayerMode.ordinal + 1) % RecognitionLayerMode.entries.size]
                    Log.i(LOG_TAG, "COMPOSITOR_MODE=$selectedLayerMode")
                }.padding(8.dp),
        )
    }
}

internal const val GENERIC_RECOGNITION_FAILURE_MESSAGE = "识别没有完成\n请重新拍摄或选择照片"
internal fun recognitionFailureMessage(@Suppress("UNUSED_PARAMETER") error: Throwable): String = GENERIC_RECOGNITION_FAILURE_MESSAGE

private fun recognitionMaskContactPointSource(
    subjectRoiInSource: NormalizedFishBox?,
    contour: List<RecognitionContourSegment>,
    transform: RecognitionImageTransform,
    orbCenter: Offset,
    sourceWidthPx: Int,
    sourceHeightPx: Int,
): WaterLightPathPoint? {
    val roi = subjectRoiInSource ?: return null
    if (contour.isEmpty() || sourceWidthPx <= 0 || sourceHeightPx <= 0 ||
        !transform.drawnWidth.isFinite() || !transform.drawnHeight.isFinite() ||
        transform.drawnWidth <= 1f || transform.drawnHeight <= 1f
    ) return null
    val orbSourceNormalized = com.yujian.ai.ui.identify.DisplayPoint(
        (orbCenter.x - transform.translationX) / transform.drawnWidth,
        (orbCenter.y - transform.translationY) / transform.drawnHeight,
    )
    val contactSource = com.yujian.ai.ui.recognition.reveal.RecognitionNearestContourContactPoint.resolve(
        contour = contour,
        subjectRoiInSource = NormalizedSourceRect(
            minOf(roi.x1, roi.x2),
            minOf(roi.y1, roi.y2),
            maxOf(roi.x1, roi.x2),
            maxOf(roi.y1, roi.y2),
        ),
        contourIsCanonicalPhoto = true,
        sourceWidthPx = sourceWidthPx,
        sourceHeightPx = sourceHeightPx,
        orbSourceNormalized = orbSourceNormalized,
    ) ?: return null
    return WaterLightPathPoint(contactSource.x, contactSource.y).takeIf {
        it.isValid() && it.x in 0f..1f && it.y in 0f..1f
    }
}

private fun nearestPhotoPerimeterAnchor(
    rect: DisplayRect,
    point: Offset,
): Offset? {
    if (!rect.width.isFinite() || !rect.height.isFinite() || rect.width <= 1f || rect.height <= 1f) return null
    val candidates = listOf(
        Offset(point.x.coerceIn(rect.left, rect.right), rect.top),
        Offset(rect.right, point.y.coerceIn(rect.top, rect.bottom)),
        Offset(point.x.coerceIn(rect.left, rect.right), rect.bottom),
        Offset(rect.left, point.y.coerceIn(rect.top, rect.bottom)),
    )
    return candidates.minByOrNull { candidate ->
        val dx = candidate.x - point.x
        val dy = candidate.y - point.y
        dx * dx + dy * dy
    }
}

private fun orbSurfaceAnchor(center: Offset, target: Offset, radiusPx: Float): Offset {
    val dx = target.x - center.x
    val dy = target.y - center.y
    val distance = kotlin.math.hypot(dx, dy)
    if (!distance.isFinite() || distance < 1f) return center
    val offset = radiusPx.coerceAtLeast(0f).coerceAtMost(distance * .72f)
    return Offset(center.x + dx / distance * offset, center.y + dy / distance * offset)
}

private data class RecognitionEnergyProducerSnapshot(
    val decision: RecognitionRevealDecisionState,
    val event: EnergyEvent?,
    val contactPointNormalizedSource: WaterLightPathPoint?,
    val fishPathSnapshot: RecognitionFishPathSnapshot?,
    val perimeterArrivalPath: WaterLightPerimeterArrivalSnapshot?,
    val pathBindingStatus: RecognitionFishPathBindingStatus,
    val pathApplicability: String = "APPLICABLE",
)

/** Single path and event producer shared by the Processing renderer and scene Controller. */
private fun createRecognitionEnergyProducerSnapshot(
    frame: WaterLightFrame,
    ledger: WaterLightPathLedger,
    classifier: RecognitionClassifierTaskState,
    subjectStatus: SubjectStatus,
    detectorBox: NormalizedFishBox?,
    maskTrusted: Boolean,
    subjectBox: NormalizedFishBox?,
    contour: List<RecognitionContourSegment>,
    transform: RecognitionImageTransform,
    pStarCandidate: WaterLightPathPoint?,
    orbCenter: Offset,
    orbRadiusPx: Float,
    maskSha256: String,
    maskVersion: Long,
    highConfidenceResult: Boolean,
    pStarDeadlineExpired: Boolean,
): RecognitionEnergyProducerSnapshot {
    val validContour = maskTrusted && maskVersion >= 0L && subjectBox != null && contour.isNotEmpty()
    val contourClosed = validContour && contour.lastOrNull()?.closesContour == true
    val proposedContact = pStarCandidate?.takeIf {
        validContour && maskSha256.isNotBlank() && frame.geometryVersion > 0L &&
            it.isValid() && it.x in 0f..1f && it.y in 0f..1f
    }
    val stableContact = if (maskTrusted && maskSha256.isNotBlank() && maskVersion >= 0L) {
        ledger.contactPointNormalizedSource(frame, maskSha256, maskVersion) ?: proposedContact
    } else null
    val arrivalSnapshot = ledger.perimeterArrivalPath(frame)
    val proposedFishPath = stableContact?.let {
        RecognitionRevealPathGeometry.fishContact(
            transform = transform,
            contactPointNormalizedSource = it,
            seed = frame.sessionId.hashCode() xor frame.imageId.hashCode(),
            taskSplitPoint = arrivalSnapshot?.geometry?.end,
        )
    }
    val scene = frame.sceneFrame
    val fishPathRegistrationAttempted = subjectStatus == SubjectStatus.READY && maskTrusted && contourClosed &&
        stableContact != null && proposedFishPath != null && maskSha256.isNotBlank() && maskVersion >= 0L &&
        arrivalSnapshot != null && frame.geometryVersion > 0L &&
        scene.stage.ordinal >= RecognitionSceneStage.PATH_TO_FISH.ordinal
    val fishPathRegistered = if (fishPathRegistrationAttempted) {
        ledger.recordFishPath(
            frame = frame,
            geometry = proposedFishPath,
            trustedMask = true,
            maskSha256 = maskSha256,
            maskVersion = maskVersion,
            contactPointNormalizedSource = stableContact,
        ) && ledger.fishPath(frame, maskSha256, maskVersion, stableContact) != null
    } else false
    val pStarUnavailable = subjectStatus == SubjectStatus.READY && maskTrusted && contourClosed &&
        frame.geometryVersion > 0L && pStarDeadlineExpired && !fishPathRegistered
    val pathBindingStatus = when {
        scene.stage.ordinal < RecognitionSceneStage.PATH_TO_FISH.ordinal -> RecognitionFishPathBindingStatus.NOT_APPLICABLE
        frame.geometryVersion <= 0L || !transform.photoContentRect.hasValidArea() ->
            RecognitionFishPathBindingStatus.WAITING_FOR_PHOTO_GEOMETRY
        subjectStatus != SubjectStatus.READY || !maskTrusted || !contourClosed || maskSha256.isBlank() || maskVersion < 0L ->
            RecognitionFishPathBindingStatus.WAITING_FOR_MASK_BINDING
        fishPathRegistered -> RecognitionFishPathBindingStatus.PATH_BOUND
        pStarUnavailable -> RecognitionFishPathBindingStatus.PATH_TERMINAL_UNAVAILABLE
        fishPathRegistrationAttempted && arrivalSnapshot != null -> RecognitionFishPathBindingStatus.PATH_IDENTITY_MISMATCH
        else -> RecognitionFishPathBindingStatus.WAITING_FOR_PATH_REGISTRATION
    }
    var decision = RecognitionRevealDecisionGate.decide(
        classifier = classifier,
        subject = subjectStatus,
        maskTrusted = maskTrusted,
        contourValid = validContour,
        identityCurrent = scene.isIdentityValid,
        contourClosed = contourClosed,
        pStarPathReady = fishPathRegistered,
        pStarPathTerminalUnavailable = pStarUnavailable,
        highConfidenceResult = highConfidenceResult,
    )
    val fishEnergyAllowed = decision == RecognitionRevealDecisionState.FISH_FOCUS_READY && maskTrusted && fishPathRegistered
    val orbOnlyAllowed = decision == RecognitionRevealDecisionState.ORB_ONLY_READY
    val pathApplicability = if (orbOnlyAllowed && scene.stage == RecognitionSceneStage.PATH_TO_FISH) {
        "NOT_APPLICABLE_ORB_ONLY"
    } else "APPLICABLE"
    val baseEvent = WaterLightEnergy.at(
        frame = frame,
        trustedMask = fishEnergyAllowed,
        successfulRevealAllowed = highConfidenceResult,
        orbOnlyRevealAllowed = orbOnlyAllowed,
    )

    var arrivalPath: WaterLightPerimeterArrivalSnapshot? = null
    // Freeze the exact ledger route as soon as trusted Stage-05 path identity exists.
    // Result navigation must not wait until Stage 10 to create the identity it needs.
    var fishPathSnapshot: RecognitionFishPathSnapshot? = if (
        fishEnergyAllowed && fishPathRegistered && stableContact != null &&
        maskSha256.isNotBlank() && maskVersion >= 0L
    ) {
        ledger.exportRecognitionFishPathSnapshot(frame, maskSha256, maskVersion, stableContact)
    } else null
    var pathGeometry: com.yujian.ai.ui.waterlight.core.WaterLightPathGeometry? = null
    var pathSegments = emptyList<com.yujian.ai.ui.waterlight.core.WaterLightPathGeometry>()
    var pathId: String? = ledger.pathId(frame)
    var eventMaskSha: String? = null
    var eventMaskVersion: Long? = null
    var eventPathGeometryVersion: Long? = null
    var eventContact: WaterLightPathPoint? = null

    when (baseEvent?.feedback) {
        EnergyFeedback.RELEASE -> {
            pathGeometry = ledger.releasePath(frame)
            if (pathGeometry == null) {
                pathGeometry = nearestPhotoPerimeterAnchor(transform.photoContentRect, orbCenter)?.let { edge ->
                    val surface = orbSurfaceAnchor(orbCenter, edge, orbRadiusPx)
                    WaterLightPathGeometryFactory.between(
                        WaterLightPathPoint(surface.x, surface.y),
                        WaterLightPathPoint(edge.x, edge.y),
                        frame.sessionId.hashCode() xor frame.imageId.hashCode(),
                    )?.takeIf { ledger.recordRelease(frame, it) }
                }
            }
            pathGeometry = ledger.releasePath(frame)
        }
        EnergyFeedback.ARRIVE -> {
            val release = ledger.releasePath(frame)
            if (release != null && detectorBox != null) {
                val proposedArrival = RecognitionRevealPathGeometry.perimeterArrival(
                    transform = transform,
                    detectorBox = detectorBox,
                    releaseEndpoint = release.end,
                    seed = frame.sessionId.hashCode() xor frame.imageId.hashCode(),
                )
                if (proposedArrival != null) ledger.recordPerimeterArrivalPath(frame, proposedArrival)
                arrivalPath = ledger.perimeterArrivalPath(frame)
                pathGeometry = arrivalPath?.geometry
                pathId = arrivalPath?.pathId
            }
        }
        EnergyFeedback.ABSORB -> {
            if (fishEnergyAllowed && stableContact != null && proposedFishPath != null &&
                maskSha256.isNotBlank() && maskVersion >= 0L
            ) {
                val fishPath = ledger.fishPath(frame, maskSha256, maskVersion, stableContact)
                val fishPathId = ledger.pathId(frame, maskSha256, maskVersion, stableContact)
                if (fishPathRegistered && fishPath != null && !fishPathId.isNullOrBlank()) {
                    pathGeometry = fishPath
                    pathId = fishPathId
                    eventMaskSha = maskSha256
                    eventMaskVersion = maskVersion
                    eventPathGeometryVersion = frame.geometryVersion
                    eventContact = stableContact
                } else {
                    // Ledger registration can lag the callback that supplies measured Photo
                    // geometry. Keep waiting until the real Attempt deadline or explicit error.
                    decision = if (pStarDeadlineExpired) RecognitionRevealDecisionState.ORB_ONLY_READY
                    else RecognitionRevealDecisionState.WAITING_PSTAR_PATH
                }
            } else if (decision == RecognitionRevealDecisionState.FISH_FOCUS_READY) {
                decision = if (pStarDeadlineExpired) RecognitionRevealDecisionState.ORB_ONLY_READY
                else RecognitionRevealDecisionState.WAITING_PSTAR_PATH
            }
        }
        EnergyFeedback.RETURN -> {
            val fullRoute = if (fishEnergyAllowed && stableContact != null &&
                maskSha256.isNotBlank() && maskVersion >= 0L
            ) ledger.returnRoute(frame, true, maskSha256, maskVersion, stableContact) else null
            if (fullRoute?.isValid() == true) {
                fishPathSnapshot = fishPathSnapshot ?: ledger.exportRecognitionFishPathSnapshot(
                    frame, maskSha256, maskVersion, requireNotNull(stableContact),
                )
                if (fishPathSnapshot?.isValid() == true) {
                    pathSegments = requireNotNull(fishPathSnapshot).returnPathSegments
                    pathId = requireNotNull(fishPathSnapshot).returnPathId
                    eventMaskSha = fishPathSnapshot?.maskSha256
                    eventMaskVersion = fishPathSnapshot?.maskVersion
                    eventPathGeometryVersion = fishPathSnapshot?.geometryVersion
                    eventContact = fishPathSnapshot?.pStarNormalizedSource
                } else {
                    decision = if (pStarDeadlineExpired) RecognitionRevealDecisionState.ORB_ONLY_READY
                    else RecognitionRevealDecisionState.WAITING_PSTAR_PATH
                }
            } else if (fishEnergyAllowed) {
                decision = if (pStarDeadlineExpired) RecognitionRevealDecisionState.ORB_ONLY_READY
                else RecognitionRevealDecisionState.WAITING_PSTAR_PATH
            }
            if (decision == RecognitionRevealDecisionState.ORB_ONLY_READY) {
                val perimeterRoute = ledger.returnRoute(frame, trustedMask = false)
                pathSegments = perimeterRoute?.segments.orEmpty()
                pathId = perimeterRoute?.id ?: pathId
            }
        }
        else -> Unit
    }
    val event = if (baseEvent?.feedback == EnergyFeedback.RETURN && pathSegments.isEmpty()) {
        null
    } else if (baseEvent == null) {
        null
    } else {
        WaterLightEnergy.at(
            frame = frame,
            trustedMask = fishEnergyAllowed && decision != RecognitionRevealDecisionState.ORB_ONLY_READY,
            successfulRevealAllowed = highConfidenceResult,
            pathGeometry = pathGeometry,
            orbOnlyRevealAllowed = decision == RecognitionRevealDecisionState.ORB_ONLY_READY,
            pathSegments = pathSegments,
            pathIdOverride = pathId,
            pathMaskSha256 = eventMaskSha,
            pathMaskVersion = eventMaskVersion,
            pathGeometryVersion = eventPathGeometryVersion,
            pathContactPointNormalizedSource = eventContact,
        )
    }
    return RecognitionEnergyProducerSnapshot(
        decision = decision,
        event = event,
        contactPointNormalizedSource = stableContact,
        fishPathSnapshot = fishPathSnapshot,
        perimeterArrivalPath = arrivalPath ?: arrivalSnapshot,
        pathBindingStatus = pathBindingStatus,
        pathApplicability = pathApplicability,
    )
}

fun extractContour(bitmap: Bitmap): List<RecognitionContourSegment> {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 1 || height <= 1) return emptyList()
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val alpha = ByteArray(pixels.size) { index -> android.graphics.Color.alpha(pixels[index]).toByte() }
    return RecognitionMaskContourExtractor.extract(width, height, alpha)
}

private fun logFocusDiagnostic(
    subject: FishSubjectResult,
    contourSegments: Int,
    box: NormalizedFishBox?,
    lowPerformance: Boolean,
    reduceMotion: Boolean,
    focusLevel: RecognitionFishFocusLevel = RecognitionFishFocusLevel.A,
    levelAAvailable: Boolean = false,
) {
    val bbox = box?.normalized()
    val mode = when {
        focusLevel == RecognitionFishFocusLevel.C -> "LEVEL_C"
        focusLevel == RecognitionFishFocusLevel.B -> "LEVEL_B"
        levelAAvailable -> "LEVEL_A"
        else -> "LEVEL_B"
    }
    Log.i(
        LOG_TAG,
        "SUBJECT_STATUS=${subject.status} SUBJECT_QUALITY=${subject.quality ?: "UNKNOWN"} " +
            "SUBJECT_MASK_AREA=${subject.maskAreaRatio} SUBJECT_WIDTH=${subject.width} " +
            "SUBJECT_HEIGHT=${subject.height} CONTOUR_SEGMENTS=$contourSegments " +
            "FOCUS_RENDER_MODE=$mode FOCUS_LEVEL_REQUESTED=${focusLevel.name} " +
            "REAL_BBOX=${bbox?.x1 ?: "NONE"},${bbox?.y1 ?: "NONE"}," +
            "${bbox?.x2 ?: "NONE"},${bbox?.y2 ?: "NONE"} LOW_PERFORMANCE=$lowPerformance " +
        "REDUCE_MOTION=$reduceMotion",
    )
}
