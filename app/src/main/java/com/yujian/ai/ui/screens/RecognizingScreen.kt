package com.yujian.ai.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.Choreographer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.yujian.ai.R
import com.yujian.ai.BuildConfig
import com.yujian.ai.ai.NormalizedFishBox
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
import com.yujian.ai.ui.designsystem.components.aiorb.aiOrbProcessingState
import com.yujian.ai.ui.designsystem.lakeglass.LakeGlassBackgroundHost
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightPhase
import com.yujian.ai.ui.waterlight.core.SceneMotionCoordinator
import com.yujian.ai.ui.waterlight.core.RecognitionProgressDisplayMode
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneTimelineProjection
import com.yujian.ai.ui.waterlight.core.ScenePlaybackMode
import com.yujian.ai.ui.waterlight.core.WaterLightEnergy
import com.yujian.ai.ui.waterlight.core.EnergyFeedback
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometryFactory
import com.yujian.ai.ui.waterlight.core.WaterLightPathPoint
import com.yujian.ai.ui.waterlight.core.WaterLightPathLedger
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

import com.yujian.ai.ui.recognition.processingOrbMotionFrame
import com.yujian.ai.ui.recognition.RecognitionVisualStateController
import com.yujian.ai.ui.recognition.recognitionMaskContourRevealProgress
import com.yujian.ai.ui.recognition.reveal.RecognitionProcessingPhotoSnapshot
import com.yujian.ai.ui.recognition.reveal.RecognitionGeometryTrustState
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealPolicy
import com.yujian.ai.ui.recognition.reveal.RecognitionClassifierTaskState
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealDecisionGate
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealDecisionState
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealWindowRect
import com.yujian.ai.ui.recognition.reveal.RecognitionSubjectGeometrySnapshot
import com.yujian.ai.ui.recognition.FISH_FOCUS_A_PROMOTION_DELAY_MS
import com.yujian.ai.ui.recognition.recognitionDisplayedFishFocusLevel
import com.yujian.ai.ui.recognition.recognitionMaskAcceptanceOpen
import com.yujian.ai.ui.recognition.rememberRecognitionMotionPolicy
import com.yujian.ai.ui.recognition.RecognitionPhotoFlowPlan
import com.yujian.ai.ui.recognition.planRecognitionPhotoFlow
import com.yujian.ai.ui.recognition.interpolateRecognitionPhotoTransform
import com.yujian.ai.ui.recognition.RecognitionStepDiagnosticControls
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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

private const val LOG_TAG = "RecognitionProcessingScene"

private fun recognitionMonotonicNowMs(): Long = SystemClock.uptimeMillis()

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
    recognitionSessionId: String? = null,
    recognitionAttemptId: String? = null,
    diagnosticPlaybackController: SceneMotionCoordinator? = null,
    diagnosticDisplayMode: RecognitionProgressDisplayMode? = null,
    onDiagnosticDisplayModeChange: ((RecognitionProgressDisplayMode) -> Unit)? = null,
    onDiagnosticRevealRequested: ((ProductionRecognitionResult) -> String?)? = null,
    diagnosticReturnRevision: Int = 0,
    generateSubjectWithConfidence: (suspend (SelectedImage, NormalizedFishBox, Float) -> FishSubjectResult)? = null,
    recognitionAttemptDeadline: RecognitionAttemptDeadline? = null,
    prepareSubject: (suspend (SelectedImage) -> Unit)? = null,
) = RecognitionProcessingScene(
    image, onBack, recognize, generateSubject, onFinished, onFailure,
    phaseOverride, visualClockOverrideMs, motionPolicyOverride = motionPolicyOverride,
    onSubjectGeometry = onSubjectGeometry,
    onProcessingPhotoSnapshot = onProcessingPhotoSnapshot,
    recognitionSessionId = recognitionSessionId,
    recognitionAttemptId = recognitionAttemptId,
    diagnosticPlaybackController = diagnosticPlaybackController,
    diagnosticDisplayMode = diagnosticDisplayMode,
    onDiagnosticDisplayModeChange = onDiagnosticDisplayModeChange,
    onDiagnosticRevealRequested = onDiagnosticRevealRequested,
    diagnosticReturnRevision = diagnosticReturnRevision,
    generateSubjectWithConfidence = generateSubjectWithConfidence,
    recognitionAttemptDeadline = recognitionAttemptDeadline,
    prepareSubject = prepareSubject,
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
    recognitionSessionId: String? = null,
    recognitionAttemptId: String? = null,
    diagnosticPlaybackController: SceneMotionCoordinator? = null,
    diagnosticDisplayMode: RecognitionProgressDisplayMode? = null,
    onDiagnosticDisplayModeChange: ((RecognitionProgressDisplayMode) -> Unit)? = null,
    onDiagnosticRevealRequested: ((ProductionRecognitionResult) -> String?)? = null,
    diagnosticReturnRevision: Int = 0,
    generateSubjectWithConfidence: (suspend (SelectedImage, NormalizedFishBox, Float) -> FishSubjectResult)? = null,
    recognitionAttemptDeadline: RecognitionAttemptDeadline? = null,
    prepareSubject: (suspend (SelectedImage) -> Unit)? = null,
    onPhotoFramePresented: ((Long) -> Unit)? = null,
) {
    val motionPolicy = motionPolicyOverride ?: rememberRecognitionMotionPolicy()
    val pageContext = LocalContext.current
    val pageView = LocalView.current
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
        mutableStateOf(if (BuildConfig.DEBUG) RecognitionProgressDisplayMode.STEP_DIAGNOSTIC else RecognitionProgressDisplayMode.NORMAL_PROGRESS)
    }
    val displayMode = diagnosticDisplayMode ?: localDisplayMode
    val diagnosticUiAvailable = BuildConfig.DEBUG && phaseOverride == null && visualClockOverrideMs == null
    val stepDiagnosticActive = diagnosticUiAvailable && displayMode == RecognitionProgressDisplayMode.STEP_DIAGNOSTIC
    // The diagnostic shell is a fixed Debug-session policy, independent of its display toggle.
    val diagnosticModeState = rememberUpdatedState(BuildConfig.DEBUG)
    val playbackController = diagnosticPlaybackController ?: remember(sceneKey) { SceneMotionCoordinator() }
    var playbackRevision by remember(sceneKey) { mutableIntStateOf(0) }
    val diagnosticStepDisplayState = rememberUpdatedState(
        diagnosticUiAvailable && displayMode == RecognitionProgressDisplayMode.STEP_DIAGNOSTIC,
    )
    val playbackMode = playbackController.mode
    val playbackPaused = playbackController.paused
    val orbCompletion = remember(sceneKey) { Animatable(0f) }
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
    var subjectResult by remember(sceneKey) { mutableStateOf(FishSubjectResult(SubjectStatus.IDLE)) }
    var finishedResult by remember(sceneKey) { mutableStateOf<ProductionRecognitionResult?>(null) }
    var pipelineError by remember(sceneKey) { mutableStateOf<Throwable?>(null) }
    var delivered by remember(sceneKey) { mutableStateOf(false) }
    var photoPresentedAtMs by remember(sceneKey) { mutableStateOf<Long?>(null) }
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
    val currentSubjectGeometryCallback = rememberUpdatedState(onSubjectGeometry)
    val revealDecision = RecognitionRevealDecisionGate.decide(
        classifier = when {
            pipelineError != null || realPhase == RecognitionPhase.FAILURE -> RecognitionClassifierTaskState.FAILED
            finishedResult?.ready == true -> RecognitionClassifierTaskState.READY
            else -> RecognitionClassifierTaskState.WAITING
        },
        subject = subjectResult.status,
        maskTrusted = trustedSubjectMask,
        contourValid = trustedSubjectMask && subjectBitmap != null && subjectBox != null && contour.isNotEmpty(),
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
    var photoGeometryVersion by remember(sceneKey) { mutableLongStateOf(0L) }

    LaunchedEffect(
        sceneKey, photoPresentedAtMs, assessment?.primary?.box, finishedResult, subjectResult.status,
        subjectResult.maskSha256, subjectBitmap?.generationId, trustedSubjectMask, contour, photoGeometryVersion,
    ) {
        val detectorReady = assessment?.primary?.box?.let { box ->
            listOf(box.x1, box.y1, box.x2, box.y2).all(Float::isFinite) && box.x1 < box.x2 && box.y1 < box.y2
        } == true
        val classifierReady = finishedResult?.ready == true && finishedResult?.assessment?.primary != null
        val slimSamTerminal = subjectResult.status == SubjectStatus.READY || subjectResult.status == SubjectStatus.FAILED
        val contourValid = trustedSubjectMask && contour.isNotEmpty()
        controller.onDetectorTargetAvailable(detectorReady, sceneIdentity)
        controller.onPhotoTransformAvailable(
            photoGeometryVersion != 0L, sceneIdentity, geometryVersion = photoGeometryVersion,
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
            maskVersion = subjectBitmap?.generationId?.toLong() ?: -1L,
        )
        controller.onRevealDecisionReady(sceneIdentity.takeIf { RecognitionRevealDecisionGate.isReady(revealDecision) })
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
                "decision=$revealDecision",
        )
    }

    LaunchedEffect(sceneKey, diagnosticPlaybackController, diagnosticUiAvailable) {
        if (diagnosticPlaybackController == null && diagnosticUiAvailable) {
            // Every Debug session opens on step 01. The clock starts only after the
            // actual PhotoSession first frame is presented; choosing the diagnostic
            // display must not silently switch playback to AUTO.
            playbackController.beginNewSession(sceneIdentity, debugBuild = true, nowMs = recognitionMonotonicNowMs())
            playbackRevision++
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
        subjectResult = result ?: FishSubjectResult(SubjectStatus.FAILED, errorCode = "SUBJECT_GENERATION_NULL")
        if (result?.status != SubjectStatus.READY || result.bitmapPath.isNullOrBlank()) {
            onSubjectGeometry(subjectResult, null)
            Log.i(
                LOG_TAG,
                "MASK_REJECT session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                    "image=${selected.imageId} sha256=${selected.photoSha256} trust=UNTRUSTED " +
                    "reason=${result?.errorCode ?: "SUBJECT_RESULT_NOT_READY"} quality=${result?.quality} contours=0",
            )
            logFocusDiagnostic(subjectResult, contour.size, null, false, false)
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(result.bitmapPath) }
        if (loaded == null) {
            onSubjectGeometry(subjectResult, null)
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
            val geometryMatchesPhoto = metadata?.matches(
                recognitionSessionId.orEmpty(), recognitionAttemptId.orEmpty(),
                selected.imageId, selected.photoSha256, selected.bitmap.width, selected.bitmap.height,
            ) == true
            val roiBox = metadata?.takeIf { geometryMatchesPhoto }?.normalizedRoi()
            val roi = roiBox?.let { NormalizedSourceRect(it.x1, it.y1, it.x2, it.y2) }
            val cropDimensionsMatch = loaded.width == result.roiWidth &&
                loaded.height == result.roiHeight &&
                loaded.width == metadata?.roiPx?.width() && loaded.height == metadata?.roiPx?.height()
            val maskShapeValid = result.quality == FishSubjectQuality.GOOD &&
                result.maskSize > 0 && result.maskSize == result.expectedMaskSize &&
                cropDimensionsMatch && geometryMatchesPhoto && roi != null
            val extractedContour = if (maskShapeValid) {
                withContext(Dispatchers.Default) {
                    WaterLightMaskAdapter.toPhotoSpace(requireNotNull(metadata), extractContour(loaded))
                }
            } else {
                emptyList()
            }
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
                maskVersion = loaded.generationId.toLong(),
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
            onSubjectGeometry(subjectResult, geometry.takeIf { trustedForReveal })
            if (trustedForReveal) {
                Log.i(
                    LOG_TAG,
                    "MASK_CONTOUR_READY session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                        "image=${selected.imageId} sha256=${selected.photoSha256} points=${extractedContour.size} trust=TRUSTED_MASK",
                )
            }
            val maskRejectReason = when {
                result.quality != FishSubjectQuality.GOOD -> "QUALITY_${result.quality}"
                result.maskSize <= 0 || result.maskSize != result.expectedMaskSize -> "MASK_SIZE_MISMATCH"
                !geometryMatchesPhoto -> "MASK_GEOMETRY_OR_IDENTITY_MISMATCH"
                !cropDimensionsMatch -> "ROI_DIMENSIONS_MISMATCH"
                extractedContour.isEmpty() -> "EMPTY_CONTOUR"
                recognitionSessionId.isNullOrBlank() || recognitionAttemptId.isNullOrBlank() -> "SESSION_IDENTITY_MISSING"
                selected.imageId.isBlank() || selected.photoSha256.isBlank() -> "PHOTO_IDENTITY_MISSING"
                trustState != RecognitionGeometryTrustState.TRUSTED_MASK -> "GEOMETRY_OR_IDENTITY_MISMATCH"
                else -> "NONE"
            }
            Log.i(
                LOG_TAG,
                "MASK_REJECT session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                    "image=${selected.imageId} sha256=${selected.photoSha256} quality=${result.quality} " +
                    "mask=${result.maskSize}/${result.expectedMaskSize} roi=${loaded.width}x${loaded.height} " +
                    "mask_area_ratio=${result.maskAreaRatio} iou=${result.maskIouScore?.toString() ?: "NOT_AVAILABLE"} " +
                    "contours=${if (trustedForReveal) extractedContour.size else 0} " +
                    "acceptance_phase=$maskAcceptancePhase trust=${if (trustedForReveal) trustState else RecognitionGeometryTrustState.UNTRUSTED} " +
                    "reject_reason=$maskRejectReason",
            )
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
            delivered = true
            while (latestPhotoPresentedAtMs == null && isActive) withFrameNanos { }
            orbCompletion.animateTo(1f, tween(if (motionPolicy.reduceMotion) 0 else 280))
            delivered = true
            onFinished(result)
        }
    }

    LaunchedEffect(displayMode, playbackRevision, pipelineError, finishedResult) {
        if (manualStepDiagnosticState.value || delivered) return@LaunchedEffect
        pipelineError?.let { error ->
            delivered = true
            onFailure(error)
            return@LaunchedEffect
        }
        finishedResult?.takeIf { !it.ready }?.let { result ->
            delivered = true
            while (latestPhotoPresentedAtMs == null && isActive) withFrameNanos { }
            orbCompletion.animateTo(1f, tween(if (motionPolicy.reduceMotion) 0 else 280))
            onFinished(result)
        }
    }

    // The visual clock must remain stable while the real detector/classifier advances.
    // realPhase is intentionally NOT a LaunchedEffect key: restarting this loop on every
    // progress callback can cancel the presentation before it reaches RESULT.
    LaunchedEffect(sceneKey, phaseOverride, photoPresentedAtMs, displayMode, diagnosticReturnRevision) {
        if (stepDiagnosticActive && playbackController.mode == ScenePlaybackMode.MANUAL &&
            finishedResult?.ready == true
        ) delivered = false
        if (photoPresentedAtMs == null && phaseOverride == null) return@LaunchedEffect
        try {
            while (isActive && phaseOverride == null && !delivered) {
                withFrameNanos { _ ->
                    // Use one uptime-based source for phase events and all visual layers.
                    visualNowMs = recognitionMonotonicNowMs()
                }
                if (RecognitionRevealDecisionGate.isReady(latestRevealDecision.value)) {
                    controller.onRevealDecisionReady(sceneIdentity)
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
                if (nextSceneStage == RecognitionSceneStage.ORB_RETURN && latestFinishedResult.value?.ready == true &&
                    controller.resultVisualReady(visualNowMs) && !delivered && !diagnosticStepDisplayState.value) {
                    // The adaptive beats and real model result are both ready. Result
                    // continues from this same controller frame at ORB_RETURN.
                    delivered = true
                    Log.i(LOG_TAG, "delivering RESULT_READY at monotonic=$visualNowMs")
                    Log.i(LOG_TAG, "ORB_COMPLETION_START image=${image?.imageId} sha256=${image?.photoSha256}")
                    orbCompletion.animateTo(1f, tween(if (motionPolicy.reduceMotion) 0 else 280))
                    onFinished(requireNotNull(latestFinishedResult.value))
                    break
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
        phase = WaterLightPhase.PROCESSING, completion = orbCompletion.value.coerceIn(0f, 1f),
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
    LaunchedEffect(sceneKey, diagnosticSceneFrame.stage) {
        Log.i(
            LOG_TAG,
            "SCENE_STAGE_ENTER session=${diagnosticSceneFrame.identity.sessionId} " +
                "attempt=${diagnosticSceneFrame.identity.attemptId} image=${diagnosticSceneFrame.identity.imageId} " +
                "sha256=${diagnosticSceneFrame.identity.photoSha256.orEmpty()} " +
                "stage=${diagnosticSceneFrame.stage.ordinal + 1}/13 name=${diagnosticSceneFrame.stage.name} " +
                "reference_ms=${diagnosticSceneFrame.opticalReferenceElapsedMs} playback=${diagnosticSceneFrame.playbackMode} " +
                "paused=${diagnosticSceneFrame.paused} detector=${assessment?.status} " +
                "bbox=${assessment?.primary?.box} slimsam=${subjectResult.status} trusted_mask=$trustedSubjectMask",
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
            "WAITING_FOR_DETECTOR_TARGET_AND_PHOTO_TRANSFORM" -> "等待 Detector 目标与照片变换"
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
    LaunchedEffect(sceneKey, diagnosticSceneFrame.stage, diagnosticSceneFrame.stageProgress, trustedSubjectMask, successfulRevealAllowed) {
        if (diagnosticSceneFrame.stage == RecognitionSceneStage.FISH_GOLD_PEAK &&
            diagnosticSceneFrame.stageProgress >= .999f && trustedSubjectMask && successfulRevealAllowed
        ) controller.onFishGoldComplete(true, sceneIdentity)
    }
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
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned {
        rootOrigin = it.boundsInWindow().topLeft
    }) {
        LakeGlassBackgroundHost(Modifier.fillMaxSize())
        val viewportWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val viewportHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
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
                    diagnosticReservePx = 0f,
                    fontScale = density.fontScale,
                )
            }
        }
        LaunchedEffect(sceneKey, photoFlowPlan) {
            photoFlowPlan?.let { plan ->
                Log.i(LOG_TAG, "PROCESSING_LAYOUT image=${image?.imageId} sha256=${image?.photoSha256} viewport=${viewportWidthPx}x${viewportHeightPx} landscape=${plan.landscape} start=${plan.startTransform.photoContentRect} target=${plan.targetPhotoRect} orb_center=${plan.statusCenterXPx},${plan.orbCenterYPx} orb_core=${plan.orbCoreSizePx}")
            }
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
        if (image != null && photoFlowPlan != null) RecognitionPhoto(
            waterLightFrame = waterLightFrame,
            waterLightEngine = waterLightEngine,
            energyPathLedger = energyPathLedger,
            revealDecision = revealDecision,
            bitmap = image.bitmap,
            photoFlowPlan = photoFlowPlan,
            orbCenter = run {
                val captureForOrb = image.capturePresentation
                val eased = processingOrbMotionFrame(entranceElapsedMs, captureForOrb != null,
                    motionPolicy.reduceMotion, orbCompletion.value).destinationProgress
                val startX = captureForOrb?.shutterCenterX?.minus(rootOrigin.x)
                    ?.takeIf(Float::isFinite) ?: photoFlowPlan.statusCenterXPx
                val startY = captureForOrb?.shutterCenterY?.minus(rootOrigin.y)
                    ?.takeIf(Float::isFinite) ?: photoFlowPlan.orbCenterYPx
                Offset(
                    startX + (photoFlowPlan.statusCenterXPx - startX) * eased,
                    startY + (photoFlowPlan.orbCenterYPx - startY) * eased,
                )
            },
            orbAnchorReady = motionPolicy.reduceMotion || entranceElapsedMs >= RecognitionSceneTimelineProjection.ORB_ENTRANCE_DURATION_MS,
            orbRadiusPx = run {
                val captureForOrb = image.capturePresentation
                val eased = processingOrbMotionFrame(entranceElapsedMs, captureForOrb != null,
                    motionPolicy.reduceMotion, orbCompletion.value).destinationProgress
                val initial = captureForOrb?.shutterSizePx?.takeIf { it.isFinite() && it > 0f }
                    ?: photoFlowPlan.orbCoreSizePx
                (initial + (photoFlowPlan.orbCoreSizePx - initial) * eased) * .5f
            },
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
            maskSha256 = subjectResult?.maskSha256.orEmpty(),
            maskVersion = subjectBitmap?.generationId?.toLong() ?: -1L,
            maskRevealProgress = maskContourRevealProgress,
            trustedMask = trustedSubjectMask,
            successfulRevealAllowed = successfulRevealAllowed,
            allowDiagnosticContourPreview = diagnosticUiAvailable && playbackMode == ScenePlaybackMode.MANUAL,
            onProcessingPhotoSnapshot = { transform, bounds, photoBounds, cornerRadiusPx, actualOrbCenter, orbCoreDiameterPx, contactPointSource, contactMaskSha256, contactMaskVersion, contactGeometryVersion ->
                controller.onOrbAnchorAvailable(
                    actualOrbCenter.x.isFinite() && actualOrbCenter.y.isFinite() && orbCoreDiameterPx.isFinite() && orbCoreDiameterPx > 1f,
                    sceneIdentity,
                )
                onProcessingPhotoSnapshot(
                    RecognitionProcessingPhotoSnapshot(
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
                    ),
                )
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
            val eased = processingOrbMotionFrame(orbEntranceMs, capture != null, motionPolicy.reduceMotion, orbCompletion.value).destinationProgress
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
            val currentPhotoTransform = interpolateRecognitionPhotoTransform(
                plan.startTransform, plan.targetTransform, photoEntryProgress,
            )
            val fishEnergyAllowed = trustedSubjectMask && (successfulRevealAllowed ||
                (diagnosticUiAvailable && playbackMode == ScenePlaybackMode.MANUAL))
            val orbOnlyEnergyAllowed = revealDecision == RecognitionRevealDecisionState.ORB_ONLY_READY
            val baseOrbEvent = WaterLightEnergy.at(
                waterLightFrame, fishEnergyAllowed, successfulRevealAllowed,
                orbOnlyRevealAllowed = orbOnlyEnergyAllowed,
            )
            val maskSha256 = subjectResult?.maskSha256.orEmpty()
            val maskVersion = subjectBitmap?.generationId?.toLong() ?: -1L
            val proposedContactSource = if (fishEnergyAllowed)
                recognitionMaskContactPointSource(
                    subjectBox, contour, currentPhotoTransform, orbCenter,
                    image?.bitmap?.width ?: 0, image?.bitmap?.height ?: 0,
                ) else null
            val stableContactSource = if (fishEnergyAllowed && maskSha256.isNotBlank() && maskVersion >= 0L) {
                energyPathLedger.contactPointNormalizedSource(waterLightFrame, maskSha256, maskVersion)
                    ?: proposedContactSource
            } else null
            val mappedContact = stableContactSource?.let {
                currentPhotoTransform.mapNormalized(it.x, it.y)
            }
            val fishEnergyAnchor = mappedContact?.let { Offset(it.x, it.y) }
            // P-star is a canonical photo-space point bound to this Mask SHA/version.
            // It is frozen by the ledger on the first real Stage-05 contact event.
            val perimeterEnergyAnchor = fishEnergyAnchor?.let {
                nearestPhotoPerimeterAnchor(currentPhotoTransform.photoContentRect, it)
            }
            val proposedFishPath = if (fishEnergyAnchor != null && perimeterEnergyAnchor != null) {
                WaterLightPathGeometryFactory.between(
                    WaterLightPathPoint(perimeterEnergyAnchor.x, perimeterEnergyAnchor.y),
                    WaterLightPathPoint(fishEnergyAnchor.x, fishEnergyAnchor.y),
                    waterLightFrame.sessionId.hashCode() xor waterLightFrame.imageId.hashCode(),
                )
            } else null
            if (baseOrbEvent?.feedback == EnergyFeedback.ABSORB && fishEnergyAllowed &&
                proposedFishPath != null && stableContactSource != null &&
                maskSha256.isNotBlank() && maskVersion >= 0L
            ) {
                energyPathLedger.recordFishPath(
                    frame = waterLightFrame,
                    geometry = proposedFishPath,
                    trustedMask = true,
                    maskSha256 = maskSha256,
                    maskVersion = maskVersion,
                    contactPointNormalizedSource = stableContactSource,
                )
            }
            val recordedFishPath = if (fishEnergyAllowed && stableContactSource != null &&
                maskSha256.isNotBlank() && maskVersion >= 0L
            ) energyPathLedger.fishPath(
                waterLightFrame, maskSha256, maskVersion, stableContactSource,
            ) else null
            val orbPathGeometry = when (baseOrbEvent?.feedback) {
                EnergyFeedback.RELEASE -> nearestPhotoPerimeterAnchor(currentPhotoTransform.photoContentRect, orbCenter)?.let { edge ->
                    val surface = orbSurfaceAnchor(orbCenter, edge, currentCorePx * .5f)
                    WaterLightPathGeometryFactory.between(
                        WaterLightPathPoint(surface.x, surface.y), WaterLightPathPoint(edge.x, edge.y),
                        waterLightFrame.sessionId.hashCode() xor waterLightFrame.imageId.hashCode(),
                    )?.also { energyPathLedger.recordRelease(waterLightFrame, it) }
                }
                EnergyFeedback.ARRIVE -> proposedFishPath
                EnergyFeedback.ABSORB -> recordedFishPath
                EnergyFeedback.RETURN -> null
                else -> null
            }
            val orbReturnRoute = if (baseOrbEvent?.feedback == EnergyFeedback.RETURN) {
                if (fishEnergyAllowed && stableContactSource != null) {
                    energyPathLedger.returnRoute(
                        waterLightFrame, true, maskSha256, maskVersion, stableContactSource,
                    )
                } else {
                    energyPathLedger.returnRoute(waterLightFrame, trustedMask = false)
                }
            } else null
            val eventPathId = when (baseOrbEvent?.feedback) {
                EnergyFeedback.ABSORB, EnergyFeedback.RETURN -> if (fishEnergyAllowed && stableContactSource != null) {
                    energyPathLedger.pathId(waterLightFrame, maskSha256, maskVersion, stableContactSource)
                } else {
                    energyPathLedger.pathId(waterLightFrame)
                }
                else -> energyPathLedger.pathId(waterLightFrame)
            }
            val orbEnergyEvent = if (baseOrbEvent?.feedback == EnergyFeedback.RETURN &&
                orbReturnRoute?.isValid() != true
            ) null else WaterLightEnergy.at(
                waterLightFrame,
                fishEnergyAllowed,
                successfulRevealAllowed,
                pathGeometry = orbPathGeometry,
                orbOnlyRevealAllowed = orbOnlyEnergyAllowed,
                pathSegments = orbReturnRoute?.segments.orEmpty(),
                pathIdOverride = orbReturnRoute?.id ?: eventPathId,
            )
            LaunchedEffect(
                waterLightFrame.sessionId,
                waterLightFrame.attemptId,
                waterLightFrame.imageId,
                orbEnergyEvent?.feedback,
                orbEnergyEvent?.pathId,
                orbEnergyEvent?.renderStatus,
                orbEnergyEvent?.eventSequence,
                waterLightFrame.sceneFrame.stageProgress >= .999f,
                stableContactSource,
                maskSha256,
                maskVersion,
                waterLightFrame.geometryVersion,
            ) {
                val event = orbEnergyEvent ?: return@LaunchedEffect
                if (event.feedback == EnergyFeedback.ABSORB && fishEnergyAllowed && stableContactSource != null) {
                    val registered = controller.onFishContactPathAvailable(
                        pathId = event.pathId,
                        maskSha256 = maskSha256,
                        maskVersion = maskVersion,
                        geometryVersion = waterLightFrame.geometryVersion,
                        contactPointNormalizedSource = stableContactSource,
                        currentIdentity = sceneIdentity,
                    )
                    if (!registered) Log.w(
                        "WaterLightEnergy",
                        "PSTAR_BIND_REJECTED session=${event.sessionId} attempt=${event.attemptId} " +
                            "image=${event.imageId} mask_sha256=${maskSha256.take(16)} mask_version=$maskVersion " +
                            "geometry_version=${waterLightFrame.geometryVersion} path=${event.pathId}",
                    )
                }
                // Event.level is a cumulative optical envelope (PATH_TO_FISH tops at .25),
                // not the stage completion signal. The controller owns the stage-local clock.
                val accepted = if (waterLightFrame.sceneFrame.stageProgress >= .999f) {
                    controller.recordEnergyEvent(waterLightFrame.sceneFrame, event)
                } else null
                Log.i(
                    "WaterLightEnergy",
                    "ENERGY_EVENT session=${event.sessionId} attempt=${event.attemptId} image=${event.imageId} " +
                        "sha256=${event.photoSha256} feedback=${event.feedback} source=${event.source} target=${event.target} " +
                        "reference_ms=${event.referenceMs} level=${event.level} path=${event.pathId} " +
                        "start=${event.pathGeometry?.start} end=${event.pathGeometry?.end} geometry=${event.geometryVersion} " +
                        "render_status=${event.renderStatus} accepted=${accepted ?: "NOT_AT_COMPLETION"}",
                )
            }
            val statusWidth = if (plan.landscape) maxWidth * .34f else maxWidth
            val progressLabel = if (BuildConfig.DEBUG && displayMode == RecognitionProgressDisplayMode.STEP_DIAGNOSTIC) "正在理解照片" else normalProgressLabel
            Text(progressLabel, color = Color(0xFF123F48), fontSize = if (maxHeight < 600.dp) 14.sp else 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset { IntOffset((plan.statusCenterXPx - with(density) { statusWidth.toPx() } / 2f).roundToInt(),
                    plan.statusPanelTopPx.roundToInt()) }.size(width = statusWidth, height = (24f * density.fontScale).dp))
            val orbHost = remember(waterLightEngine) {
                WaterLightHostBinding(WaterLightHost.ORB, WaterLightMaterialProfile.ORB_LEGACY, waterLightEngine)
            }
            if (orbHost.accepts(waterLightFrame, WaterLightHostGeometry(
                    WaterLightHost.ORB, waterLightFrame.geometryVersion, orbX, orbY, orbX + outerPx, orbY + outerPx,
                ))) AIOrbCore(
                state = aiOrbProcessingState(orbEntranceMs, capture != null, motionPolicy.reduceMotion, orbCompletion.value),
                coreSize = core,
                entranceElapsedMs = orbEntranceMs,
                elapsedMs = sceneElapsedMs,
                completion = orbCompletion.value,
                reduceMotion = motionPolicy.reduceMotion,
                lowPerformance = motionPolicy.lowPerformance,
                fromCamera = capture != null,
                orbLifeState = if (waterLightFrame.sceneFrame.stage == RecognitionSceneStage.PHOTO_STABLE ||
                    (waterLightFrame.sceneFrame.stage == RecognitionSceneStage.ORB_WAKE &&
                        waterLightFrame.sceneFrame.stageProgress < .5f)
                ) {
                    OrbRuntimeProfileV1.LifeState.S1_IDLE
                } else OrbRuntimeProfileV1.LifeState.S2_ACTIVE,
                waterLightFrame = waterLightFrame,
                energyEvent = orbEnergyEvent,
                waterLightMaterial = orbHost.material,
                modifier = Modifier.offset { IntOffset(orbX.roundToInt(), orbY.roundToInt()) },
            )

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
            )
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
    energyPathLedger: WaterLightPathLedger,
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
    maskSha256: String,
    maskVersion: Long,
    maskRevealProgress: Float,
    trustedMask: Boolean,
    successfulRevealAllowed: Boolean,
    allowDiagnosticContourPreview: Boolean,
) {
    val transform = remember(photoFlowPlan, photoEntryProgress) {
        interpolateRecognitionPhotoTransform(
            photoFlowPlan.startTransform,
            photoFlowPlan.targetTransform,
            photoEntryProgress,
        )
    }
    LaunchedEffect(transform) { onPhotoGeometryChanged(WaterLightGeometryVersion.of(transform)) }
    val waterHosts = remember(waterLightEngine) {
        listOf(WaterLightHost.PERIMETER, WaterLightHost.PATH, WaterLightHost.CONTOUR).associateWith { host ->
            WaterLightHostBinding(host, WaterLightMaterialProfile.forHost(host), waterLightEngine)
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
    val fishEnergyAllowed = trustedMask && (successfulRevealAllowed || allowDiagnosticContourPreview)
    val contourReady = fishEnergyAllowed && contourStart != null && contourEnd != null && hostReady(WaterLightHost.CONTOUR,
        contourStart.x, contourStart.y, contourEnd.x, contourEnd.y)
    val waterScope = rememberCoroutineScope()
    val orbOnlyEnergyAllowed = revealDecision == RecognitionRevealDecisionState.ORB_ONLY_READY
    val baseEnergyEvent = WaterLightEnergy.at(
        waterLightFrame, fishEnergyAllowed, successfulRevealAllowed,
        orbOnlyRevealAllowed = orbOnlyEnergyAllowed,
    )
    val proposedContactSource = if (fishEnergyAllowed && maskSha256.isNotBlank() && maskVersion >= 0L)
        recognitionMaskContactPointSource(subjectBox, contour, transform, orbCenter, bitmap.width, bitmap.height)
    else null
    val stableContactSource = if (fishEnergyAllowed && maskSha256.isNotBlank() && maskVersion >= 0L) {
        energyPathLedger.contactPointNormalizedSource(waterLightFrame, maskSha256, maskVersion)
            ?: proposedContactSource
    } else null
    val mappedContact = stableContactSource?.let { transform.mapNormalized(it.x, it.y) }
    val fishAnchor = mappedContact?.let { Offset(it.x, it.y) }
    val perimeterAnchor = fishAnchor?.let { nearestPhotoPerimeterAnchor(photoBounds, it) }
    val proposedFishPath = if (fishAnchor != null && perimeterAnchor != null)
        WaterLightPathGeometryFactory.between(
            WaterLightPathPoint(perimeterAnchor.x, perimeterAnchor.y),
            WaterLightPathPoint(fishAnchor.x, fishAnchor.y),
            waterLightFrame.sessionId.hashCode() xor imageId.hashCode(),
        )
    else null
    if (baseEnergyEvent?.feedback == EnergyFeedback.ABSORB && fishEnergyAllowed &&
        proposedFishPath != null && stableContactSource != null
    ) {
        energyPathLedger.recordFishPath(
            frame = waterLightFrame,
            geometry = proposedFishPath,
            trustedMask = true,
            maskSha256 = maskSha256,
            maskVersion = maskVersion,
            contactPointNormalizedSource = stableContactSource,
        )
    }
    val recordedFishPath = if (fishEnergyAllowed && stableContactSource != null &&
        maskSha256.isNotBlank() && maskVersion >= 0L
    ) energyPathLedger.fishPath(waterLightFrame, maskSha256, maskVersion, stableContactSource) else null
    val pathGeometry = when (baseEnergyEvent?.feedback) {
        EnergyFeedback.RELEASE -> nearestPhotoPerimeterAnchor(photoBounds, orbCenter)?.let { edge ->
            WaterLightPathGeometryFactory.between(
                orbSurfaceAnchor(orbCenter, edge, orbRadiusPx).let { WaterLightPathPoint(it.x, it.y) },
                WaterLightPathPoint(edge.x, edge.y),
                waterLightFrame.sessionId.hashCode() xor imageId.hashCode(),
            )?.also { energyPathLedger.recordRelease(waterLightFrame, it) }
        }
        EnergyFeedback.ARRIVE -> proposedFishPath
        EnergyFeedback.ABSORB -> recordedFishPath
        EnergyFeedback.RETURN -> null
        else -> null
    }
    val returnRoute = if (baseEnergyEvent?.feedback == EnergyFeedback.RETURN) {
        if (fishEnergyAllowed && stableContactSource != null) {
            energyPathLedger.returnRoute(waterLightFrame, true, maskSha256, maskVersion, stableContactSource)
        } else {
            energyPathLedger.returnRoute(waterLightFrame, trustedMask = false)
        }
    } else null
    val eventPathId = when (baseEnergyEvent?.feedback) {
        EnergyFeedback.ABSORB, EnergyFeedback.RETURN -> if (fishEnergyAllowed && stableContactSource != null) {
            energyPathLedger.pathId(waterLightFrame, maskSha256, maskVersion, stableContactSource)
        } else {
            energyPathLedger.pathId(waterLightFrame)
        }
        else -> energyPathLedger.pathId(waterLightFrame)
    }
    val energyEvent = if (baseEnergyEvent?.feedback == EnergyFeedback.RETURN &&
        returnRoute?.isValid() != true
    ) null else WaterLightEnergy.at(
        waterLightFrame,
        fishEnergyAllowed,
        successfulRevealAllowed,
        pathGeometry = pathGeometry,
        orbOnlyRevealAllowed = orbOnlyEnergyAllowed,
        pathSegments = returnRoute?.segments.orEmpty(),
        pathIdOverride = returnRoute?.id ?: eventPathId,
    )
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
            if (stableContactSource != null) waterLightFrame.geometryVersion else -1L,
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
            if (perimeterReady && (!BuildConfig.DEBUG || layerMode != RecognitionLayerMode.PHOTO_ONLY)) RecognitionPerimeterShaderOverlay(
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
                (!BuildConfig.DEBUG || layerMode == RecognitionLayerMode.FULL)
            ) WaterLightEnergyPathRenderer(
                frame = waterLightFrame,
                event = energyEvent,
                material = waterHosts.getValue(WaterLightHost.PATH).material,
                modifier = Modifier.fillMaxSize(),
            )
            if (additiveMaterialActive && (!BuildConfig.DEBUG || layerMode == RecognitionLayerMode.FULL)) {
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

internal fun extractContour(bitmap: Bitmap): List<RecognitionContourSegment> {
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
