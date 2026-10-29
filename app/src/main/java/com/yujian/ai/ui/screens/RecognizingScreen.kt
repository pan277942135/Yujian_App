package com.yujian.ai.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.Choreographer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yujian.ai.ai.FishInputAssessment
import com.yujian.ai.ai.FishDetectionQualityGate
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ai.RecognitionProgress
import com.yujian.ai.ai.subject.FishSubjectResult
import com.yujian.ai.ai.subject.FishSubjectQuality
import com.yujian.ai.ai.subject.SubjectStatus
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.identify.RecognitionImageTransform
import com.yujian.ai.ui.identify.NormalizedSourceRect
import com.yujian.ai.ui.recognition.PhotoShockwaveTransform
import com.yujian.ai.ui.recognition.RecognitionAmbientField
import com.yujian.ai.ui.recognition.photoShockwaveTransform
import com.yujian.ai.ui.recognition.RecognitionContourSegment
import com.yujian.ai.ui.recognition.RecognitionFishFocus
import com.yujian.ai.ui.recognition.RecognitionFishFocusLevel
import com.yujian.ai.ui.recognition.RecognitionMotionPolicy
import com.yujian.ai.ui.recognition.RecognitionMotionTraceSample
import com.yujian.ai.ui.recognition.RecognitionPerimeterShaderOverlay
import com.yujian.ai.ui.recognition.RecognitionStatusOverlay
import com.yujian.ai.ui.recognition.RecognitionVisualStateController
import com.yujian.ai.ui.recognition.RECOGNITION_MASK_CONTOUR_REVEAL_MS
import com.yujian.ai.ui.recognition.recognitionMaskContourRevealProgress
import com.yujian.ai.ui.recognition.reveal.RecognitionProcessingPhotoSnapshot
import com.yujian.ai.ui.recognition.reveal.RecognitionGeometryTrustState
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealPolicy
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealWindowRect
import com.yujian.ai.ui.recognition.reveal.RecognitionSubjectGeometrySnapshot
import com.yujian.ai.ui.recognition.FISH_FOCUS_A_PROMOTION_DELAY_MS
import com.yujian.ai.ui.recognition.recognitionDisplayedFishFocusLevel
import com.yujian.ai.ui.recognition.recognitionMaskAcceptanceOpen
import com.yujian.ai.ui.recognition.rememberRecognitionMotionPolicy
import com.yujian.ai.ui.recognition.RecognitionPhotoFlowPlan
import com.yujian.ai.ui.recognition.planRecognitionPhotoFlow
import com.yujian.ai.ui.recognition.interpolateRecognitionPhotoTransform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.min
import java.util.ArrayDeque
import java.util.LinkedHashSet
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

private const val OUTLINE_THRESHOLD = 36
private const val PHOTO_ENTRY_DURATION_MS = 420L
// Two-pixel sampling keeps the contour faithful to fins and tail. Rendering is
// still bounded to three local path draws in RecognitionFishFocus.
private const val OUTLINE_STRIDE = 2
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
) = RecognitionProcessingScene(
    image, onBack, recognize, generateSubject, onFinished, onFailure,
    phaseOverride, visualClockOverrideMs, motionPolicyOverride = motionPolicyOverride,
    onSubjectGeometry = onSubjectGeometry,
    onProcessingPhotoSnapshot = onProcessingPhotoSnapshot,
    recognitionSessionId = recognitionSessionId,
    recognitionAttemptId = recognitionAttemptId,
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
) {
    val phasePresentedCallback by rememberUpdatedState(onVisualPhasePresented)
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
    var realPhase by remember(sceneKey) { mutableStateOf(RecognitionPhase.CAPTURED) }
    var visualPhase by remember(sceneKey) { mutableStateOf(RecognitionPhase.CAPTURED) }
    var assessment by remember(sceneKey) { mutableStateOf<FishInputAssessment?>(null) }
    var subjectBitmap by remember(sceneKey) { mutableStateOf<Bitmap?>(null) }
    var subjectBox by remember(sceneKey) { mutableStateOf<NormalizedFishBox?>(null) }
    var contour by remember(sceneKey) { mutableStateOf(emptyList<RecognitionContourSegment>()) }
    var trustedSubjectMask by remember(sceneKey) { mutableStateOf(false) }
    var trustedMaskRevealStartedAtMs by remember(sceneKey) { mutableStateOf<Long?>(null) }
    var subjectResult by remember(sceneKey) { mutableStateOf(FishSubjectResult(SubjectStatus.IDLE)) }
    var finishedResult by remember(sceneKey) { mutableStateOf<ProductionRecognitionResult?>(null) }
    var delivered by remember(sceneKey) { mutableStateOf(false) }
    var photoPresentedAtMs by remember(sceneKey) { mutableStateOf<Long?>(null) }
    var visualNowMs by remember(sceneKey) { mutableStateOf(0L) }
    val controller = remember(sceneKey) { RecognitionVisualStateController() }
    val latestPhotoPresentedAtMs by rememberUpdatedState(photoPresentedAtMs)

    LaunchedEffect(sceneKey) {
        image?.let {
            Log.i(
                LOG_TAG,
                "PHOTO_DECODE_READY session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                    "image=${it.imageId} sha256=${it.photoSha256} source=${it.bitmap.width}x${it.bitmap.height} " +
                    "exif_normalized=true orientation_degrees_applied=${it.sourceOrientationDegrees} " +
                    "uptime_ms=${recognitionMonotonicNowMs()}",
            )
        }
    }

    DisposableEffect(subjectBitmap) {
        onDispose { subjectBitmap?.takeUnless { it.isRecycled }?.recycle() }
    }

    LaunchedEffect(sceneKey, assessment?.primary?.box) {
        val selected = image ?: return@LaunchedEffect
        val primary = assessment?.primary ?: return@LaunchedEffect
        val generator = generateSubject ?: return@LaunchedEffect
        subjectBitmap = null; subjectBox = null; contour = emptyList(); trustedSubjectMask = false
        subjectResult = FishSubjectResult(SubjectStatus.PROCESSING)
        val startedAtMs = recognitionMonotonicNowMs()
        Log.i(
            LOG_TAG,
            "SLIMSAM_START session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                "image=${selected.imageId} sha256=${selected.photoSha256} bbox=${primary.box} uptime_ms=$startedAtMs",
        )
        val result = try {
            generator(selected, primary.box)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        }
        currentCoroutineContext().ensureActive()
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
                "MASK_GATE session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
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
                "MASK_GATE session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                    "image=${selected.imageId} sha256=${selected.photoSha256} trust=UNTRUSTED reason=MASK_BITMAP_DECODE_FAILED contours=0",
            )
            return@LaunchedEffect
        }
        currentCoroutineContext().ensureActive()
        val crop = FishDetectionQualityGate.cropBoxPixels(
            primary.box.expand(.12f), selected.bitmap.width, selected.bitmap.height,
        )
        val roi = NormalizedSourceRect(
            left = crop[0].toFloat() / selected.bitmap.width,
            top = crop[1].toFloat() / selected.bitmap.height,
            right = crop[2].toFloat() / selected.bitmap.width,
            bottom = crop[3].toFloat() / selected.bitmap.height,
        )
        val cropDimensionsMatch = loaded.width == result.roiWidth &&
            loaded.height == result.roiHeight &&
            loaded.width == crop[2] - crop[0] && loaded.height == crop[3] - crop[1]
        val maskShapeValid = result.quality == FishSubjectQuality.GOOD &&
            result.maskSize > 0 && result.maskSize == result.expectedMaskSize &&
            cropDimensionsMatch
        val extractedContour = if (maskShapeValid) {
            withContext(Dispatchers.Default) { extractContour(loaded) }
        } else {
            emptyList()
        }
        currentCoroutineContext().ensureActive()
        val geometry = RecognitionSubjectGeometrySnapshot(
            imageId = selected.imageId,
            recognitionSessionId = recognitionSessionId.orEmpty(),
            recognitionAttemptId = recognitionAttemptId.orEmpty(),
            photoSha256 = selected.photoSha256,
            sourceWidthPx = selected.bitmap.width,
            sourceHeightPx = selected.bitmap.height,
            detectorBox = primary.box,
            detectorConfidence = primary.confidence,
            subjectBox = roi,
            contour = extractedContour,
            quality = result.quality,
            maskSize = result.maskSize,
            expectedMaskSize = result.expectedMaskSize,
        ).takeIf { maskShapeValid && extractedContour.isNotEmpty() }
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
        val maskAcceptancePhase = if (latestPhotoPresentedAtMs == null) {
            RecognitionPhase.CAPTURED
        } else {
            controller.presentedPhase()
        }
        val maskWindowOpen = recognitionMaskAcceptanceOpen(maskAcceptancePhase)
        val trustedForReveal = isTrustedForCurrentPhoto && maskWindowOpen
        if (trustedForReveal) {
            subjectBitmap = loaded
            subjectBox = NormalizedFishBox(roi.left, roi.top, roi.right, roi.bottom)
            contour = extractedContour
            trustedSubjectMask = true
            val acceptedAtMs = recognitionMonotonicNowMs()
            latestPhotoPresentedAtMs?.let { photoStartMs ->
                controller.onTrustedMaskAvailable(acceptedAtMs)
                if (trustedMaskRevealStartedAtMs == null) {
                    trustedMaskRevealStartedAtMs = (acceptedAtMs - photoStartMs).coerceAtLeast(0L)
                }
                Log.i(
                    LOG_TAG,
                    "MASK_TRUSTED_ACCEPT session=${recognitionSessionId.orEmpty()} " +
                        "attempt=${recognitionAttemptId.orEmpty()} image=${selected.imageId} " +
                        "acceptance_phase=$maskAcceptancePhase uptime_ms=$acceptedAtMs " +
                        "processing_elapsed_ms=${acceptedAtMs - photoStartMs}",
                )
            }
        } else {
            loaded.recycle()
        }
        onSubjectGeometry(subjectResult, geometry.takeIf { trustedForReveal })
        val maskRejectReason = when {
            result.quality != FishSubjectQuality.GOOD -> "QUALITY_${result.quality}"
            result.maskSize <= 0 || result.maskSize != result.expectedMaskSize -> "MASK_SIZE_MISMATCH"
            !cropDimensionsMatch -> "ROI_DIMENSIONS_MISMATCH"
            extractedContour.isEmpty() -> "EMPTY_CONTOUR"
            recognitionSessionId.isNullOrBlank() || recognitionAttemptId.isNullOrBlank() -> "SESSION_IDENTITY_MISSING"
            selected.imageId.isBlank() || selected.photoSha256.isBlank() -> "PHOTO_IDENTITY_MISSING"
            trustState != RecognitionGeometryTrustState.TRUSTED_MASK -> "GEOMETRY_OR_IDENTITY_MISMATCH"
            !maskWindowOpen -> "S3_MASK_ACCEPTANCE_WINDOW_CLOSED"
            else -> "NONE"
        }
        Log.i(
            LOG_TAG,
            "MASK_GATE session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                "image=${selected.imageId} sha256=${selected.photoSha256} quality=${result.quality} " +
                "mask=${result.maskSize}/${result.expectedMaskSize} roi=${loaded.width}x${loaded.height} " +
                "mask_area_ratio=${result.maskAreaRatio} iou=${result.maskIouScore?.toString() ?: "NOT_AVAILABLE"} " +
                "contours=${if (trustedForReveal) extractedContour.size else 0} " +
                "acceptance_phase=$maskAcceptancePhase trust=${if (trustedForReveal) trustState else RecognitionGeometryTrustState.UNTRUSTED} " +
                "reject_reason=$maskRejectReason",
        )
        logFocusDiagnostic(result, if (trustedForReveal) extractedContour.size else 0, primary.box, false, false)
    }

    LaunchedEffect(sceneKey, photoPresentedAtMs) {
        val selected = image ?: return@LaunchedEffect
        val startedAtMs = photoPresentedAtMs ?: return@LaunchedEffect
        controller.reset(startedAtMs)
        visualPhase = RecognitionPhase.CAPTURED
        visualNowMs = startedAtMs
        if (realPhase != RecognitionPhase.CAPTURED) {
            controller.onPipelinePhase(
                phase = realPhase,
                nowMs = startedAtMs,
                hasRealFishFocus = assessment?.primary?.box != null,
            )
        }
        if (finishedResult?.ready == true) {
            controller.onPipelinePhase(
                RecognitionPhase.RESULT,
                startedAtMs,
                hasRealFishFocus = finishedResult?.assessment?.primary?.box != null,
            )
        }
        if (trustedSubjectMask) controller.onTrustedMaskAvailable(startedAtMs)
        Log.i(
            LOG_TAG,
            "PROCESSING_P0 session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                "image=${selected.imageId} sha256=${selected.photoSha256} source=${selected.bitmap.width}x${selected.bitmap.height} " +
                "exif_normalized=true orientation_degrees_applied=${selected.sourceOrientationDegrees} uptime_ms=$startedAtMs " +
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
        val result = try {
            var lastPipelinePhase: RecognitionPhase? = null
            var detectorStartedAtMs: Long? = null
            var classifierStartedAtMs: Long? = null
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
                    )
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Log.e(LOG_TAG, "Recognition runtime failed", error)
            realPhase = RecognitionPhase.FAILURE
            if (latestPhotoPresentedAtMs != null) {
                controller.onPipelinePhase(RecognitionPhase.FAILURE, recognitionMonotonicNowMs())
            }
            onFailure(error)
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
                )
            }
        } else if (!delivered) {
            delivered = true
            onFinished(result)
        }
    }

    // The visual clock must remain stable while the real detector/classifier advances.
    // realPhase is intentionally NOT a LaunchedEffect key: restarting this loop on every
    // progress callback can cancel the presentation before it reaches RESULT.
    LaunchedEffect(sceneKey, phaseOverride, photoPresentedAtMs) {
        if (photoPresentedAtMs == null && phaseOverride == null) return@LaunchedEffect
        try {
            while (isActive && phaseOverride == null && !delivered) {
                withFrameNanos { _ ->
                    // Use one uptime-based source for phase events and all visual layers.
                    visualNowMs = recognitionMonotonicNowMs()
                }
                val nextVisualPhase = controller.current(visualNowMs, trustedMask = trustedSubjectMask)
                if (nextVisualPhase != visualPhase) {
                    val stageElapsed = controller.phaseElapsedMs(visualNowMs)
                    val referenceMs = controller.opticalReferenceElapsedMs(visualNowMs, trustedSubjectMask)
                    Log.i(
                        LOG_TAG,
                        "VISUAL_STAGE session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                            "image=${image?.imageId.orEmpty()} phase=$visualPhase->$nextVisualPhase " +
                            "stage_started_uptime_ms=$visualNowMs stage_elapsed_ms=$stageElapsed " +
                            "optical_reference_ms=$referenceMs trusted_mask=$trustedSubjectMask real_phase=$realPhase",
                    )
                    visualPhase = nextVisualPhase
                    if (nextVisualPhase == RecognitionPhase.OUTLINE && trustedSubjectMask &&
                        trustedMaskRevealStartedAtMs == null
                    ) {
                        trustedMaskRevealStartedAtMs = (visualNowMs - requireNotNull(photoPresentedAtMs))
                            .coerceAtLeast(0L)
                    }
                    phasePresentedCallback(nextVisualPhase, visualNowMs)
                }
                if (nextVisualPhase == RecognitionPhase.RESULT && finishedResult?.ready == true && !delivered) {
                    // The adaptive visual beats and real model result are both ready.
                    // RR09 starts from Result's first presented frame.
                    delivered = true
                    Log.i(LOG_TAG, "delivering RESULT_READY at monotonic=$visualNowMs")
                    onFinished(requireNotNull(finishedResult))
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

    val rendered = phaseOverride ?: visualPhase
    val visualClockMs = visualClockOverrideMs ?: visualNowMs
    val deterministicMotion = visualClockOverrideMs != null
    val motionPolicy = motionPolicyOverride ?: rememberRecognitionMotionPolicy()
    val processingElapsedMs = if (deterministicMotion) {
        visualClockMs.coerceAtLeast(0L)
    } else {
        photoPresentedAtMs?.let { (visualClockMs - it).coerceAtLeast(0L) } ?: 0L
    }
    val opticalReferenceElapsedMs = when {
        deterministicMotion || phaseOverride != null -> visualClockMs.coerceAtLeast(0L)
        photoPresentedAtMs == null -> 0L
        else -> controller.opticalReferenceElapsedMs(visualNowMs, trustedSubjectMask)
    }
    LaunchedEffect(sceneKey, rendered, trustedSubjectMask, photoPresentedAtMs) {
        if (photoPresentedAtMs != null && trustedSubjectMask &&
            (rendered == RecognitionPhase.OUTLINE || rendered == RecognitionPhase.CLASSIFYING) &&
            trustedMaskRevealStartedAtMs == null
        ) {
            trustedMaskRevealStartedAtMs = processingElapsedMs
            Log.i(
                LOG_TAG,
                "MASK_CONTOUR_REVEAL_START session=${recognitionSessionId.orEmpty()} " +
                    "attempt=${recognitionAttemptId.orEmpty()} image=${image?.imageId.orEmpty()} " +
                    "processing_elapsed_ms=$processingElapsedMs duration_ms=$RECOGNITION_MASK_CONTOUR_REVEAL_MS",
            )
        }
    }
    val maskContourRevealProgress = recognitionMaskContourRevealProgress(
        processingElapsedMs = processingElapsedMs,
        revealStartedAtMs = trustedMaskRevealStartedAtMs,
        reduceMotion = motionPolicy.reduceMotion,
    )
    val photoShockwave = photoShockwaveTransform(processingElapsedMs, motionPolicy.reduceMotion)
    val phaseElapsedMs = when {
        phaseOverride != null -> 1_000L
        photoPresentedAtMs == null -> 0L
        else -> controller.phaseElapsedMs(visualNowMs)
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
    val skipPhotoEntry = phaseOverride != null || visualClockOverrideMs != null
    var photoEntryProgress by remember(sceneKey, skipPhotoEntry) {
        mutableFloatStateOf(if (skipPhotoEntry) 1f else 0f)
    }
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
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF102D35))) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val viewportHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val photoFlowPlan = remember(
            image?.imageId,
            image?.photoSha256,
            image?.bitmap?.width,
            image?.bitmap?.height,
            image?.source,
            viewportWidthPx,
            viewportHeightPx,
            density.density,
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
                )
            }
        }
        LaunchedEffect(sceneKey, photoFlowPlan, skipPhotoEntry) {
            val plan = photoFlowPlan ?: return@LaunchedEffect
            if (skipPhotoEntry) {
                photoEntryProgress = 1f
                return@LaunchedEffect
            }
            val startedAtMs = recognitionMonotonicNowMs()
            photoEntryProgress = 0f
            Log.i(
                LOG_TAG,
                "PHOTO_ENTRY_START session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                    "image=${image?.imageId.orEmpty()} source=${image?.source.orEmpty()} " +
                    "start_rect=${plan.startTransform.photoContentRect} target_rect=${plan.targetPhotoRect} " +
                    "uptime_ms=$startedAtMs duration_candidate_ms=$PHOTO_ENTRY_DURATION_MS",
            )
            while (isActive && photoEntryProgress < 1f) {
                withFrameNanos { _ ->
                    photoEntryProgress = ((recognitionMonotonicNowMs() - startedAtMs).toFloat() /
                        PHOTO_ENTRY_DURATION_MS.toFloat()).coerceIn(0f, 1f)
                }
            }
            Log.i(
                LOG_TAG,
                "PHOTO_ENTRY_STABLE session=${recognitionSessionId.orEmpty()} attempt=${recognitionAttemptId.orEmpty()} " +
                    "image=${image?.imageId.orEmpty()} uptime_ms=${recognitionMonotonicNowMs()} " +
                    "duration_ms=${recognitionMonotonicNowMs() - startedAtMs}",
            )
        }
        val statusPanelTop = with(density) {
            (photoFlowPlan?.statusPanelTopPx ?: (safeTopPx + 68f * density.density)).toDp()
        }
        if (image != null && photoFlowPlan != null) RecognitionPhoto(
            bitmap = image.bitmap,
            photoFlowPlan = photoFlowPlan,
            photoEntryProgress = photoEntryProgress,
            phase = rendered,
            focusBox = assessment?.primary?.box,
            subjectBitmap = subjectBitmap,
            subjectBox = subjectBox,
            contour = contour,
                visualClockMs = if (deterministicMotion) visualClockMs else processingElapsedMs,
            deterministicMotion = deterministicMotion,
            processingElapsedMs = processingElapsedMs,
            opticalReferenceElapsedMs = opticalReferenceElapsedMs,
            photoShockwave = photoShockwave,
            motionSessionKey = sceneKey,
            phaseElapsedMs = phaseElapsedMs,
            motionPolicy = motionPolicy,
            onMotionFrame = onMotionFrame,
            focusLevel = displayedFocusLevel,
            onFishFocusTransform = onFishFocusTransform,
            modifier = Modifier.fillMaxSize(),
            processingClockStarted = photoPresentedAtMs != null || deterministicMotion,
            recognitionSessionId = recognitionSessionId.orEmpty(),
            recognitionAttemptId = recognitionAttemptId.orEmpty(),
            imageId = image.imageId,
            photoSha256 = image.photoSha256,
            maskRevealProgress = maskContourRevealProgress,
            trustedMask = trustedSubjectMask,
            onProcessingPhotoSnapshot = { transform, bounds, photoBounds, cornerRadiusPx ->
                onProcessingPhotoSnapshot(
                    RecognitionProcessingPhotoSnapshot(
                        imageId = image.imageId,
                        viewportBoundsInWindow = bounds,
                        sourceToViewport = transform,
                        photoBoundsInWindow = photoBounds,
                        cornerRadiusPx = cornerRadiusPx,
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
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
        }
        RecognitionStatusOverlay(
            rendered,
            Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = safeHorizontal)
                .padding(top = statusPanelTop),
            reduceMotion = motionPolicy.reduceMotion,
            visualClockMs = visualClockMs,
        )
    }
}
@Composable
private fun RecognitionPhoto(
    bitmap: Bitmap,
    photoFlowPlan: RecognitionPhotoFlowPlan,
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
    modifier: Modifier,
    onProcessingPhotoSnapshot: (
        RecognitionImageTransform,
        RecognitionRevealWindowRect,
        RecognitionRevealWindowRect,
        Float,
    ) -> Unit,
    onPhotoFirstFramePresented: (Long) -> Unit,
    processingClockStarted: Boolean,
    recognitionSessionId: String,
    recognitionAttemptId: String,
    imageId: String,
    photoSha256: String,
    maskRevealProgress: Float,
    trustedMask: Boolean,
) {
    val transform = remember(photoFlowPlan, photoEntryProgress) {
        interpolateRecognitionPhotoTransform(
            photoFlowPlan.startTransform,
            photoFlowPlan.targetTransform,
            photoEntryProgress,
        )
    }
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
    var viewportBounds by remember(bitmap, motionSessionKey) { mutableStateOf<RecognitionRevealWindowRect?>(null) }

    LaunchedEffect(photoEntryProgress, viewportBounds, photoFlowPlan.targetTransform) {
        if (photoEntryProgress < 1f) return@LaunchedEffect
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
                    if (photoEntryProgress >= 1f && reportedFirstFrame.compareAndSet(false, true)) {
                        // Wait for the Compose frame containing the stable final photo
                        // transform before starting the visual clock.
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
                clipPath(photoClipPath) {
                    drawImage(
                        image = photoImage,
                        dstOffset = IntOffset(transform.translationX.roundToInt(), transform.translationY.roundToInt()),
                        dstSize = IntSize(transform.drawnWidth.roundToInt(), transform.drawnHeight.roundToInt()),
                    )
                }
            }
            RecognitionPerimeterShaderOverlay(
                bitmap = bitmap,
                photoTransform = transform,
                elapsedMs = processingElapsedMs,
                opticalReferenceElapsedMs = opticalReferenceElapsedMs,
                processingClockStarted = processingClockStarted,
                sessionKey = motionSessionKey,
                recognitionSessionId = recognitionSessionId,
                recognitionAttemptId = recognitionAttemptId,
                imageId = imageId,
                photoSha256 = photoSha256,
                phase = phase,
                reduceMotion = motionPolicy.reduceMotion,
                modifier = Modifier.fillMaxSize().semantics {
                    contentDescription = "正在识别的鱼获照片"
                },
            )
            RecognitionAmbientField(
                phase,
                Modifier.fillMaxSize(),
                visualClockMs,
                motionSessionKey = motionSessionKey,
                deterministicMotion = deterministicMotion,
                processingElapsedMs = opticalReferenceElapsedMs,
                lowPerformance = motionPolicy.lowPerformance,
                reduceMotion = motionPolicy.reduceMotion,
                qualityLevel = motionPolicy.qualityLevel,
                onMotionFrame = onMotionFrame,
                photoTransform = transform,
                includeEdgeField = true,
            )
            RecognitionFishFocus(
                phase,
                focusBox,
                subjectBitmap,
                subjectBox,
                contour,
                transform,
                Modifier.fillMaxSize(),
                processingElapsedMs = processingElapsedMs,
                opticalReferenceElapsedMs = opticalReferenceElapsedMs,
                maskRevealProgress = maskRevealProgress,
                reduceMotion = motionPolicy.reduceMotion,
                lowPerformance = motionPolicy.lowPerformance,
                trustedMask = trustedMask,
            )
        }
    }
}

internal const val GENERIC_RECOGNITION_FAILURE_MESSAGE = "识别没有完成\n请重新拍摄或选择照片"
internal fun recognitionFailureMessage(@Suppress("UNUSED_PARAMETER") error: Throwable): String = GENERIC_RECOGNITION_FAILURE_MESSAGE

internal fun extractContour(bitmap: Bitmap): List<RecognitionContourSegment> {
    val width = bitmap.width; val height = bitmap.height
    if (width <= 1 || height <= 1) return emptyList()
    val pixels = IntArray(width * height); bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val gridWidth = (width + OUTLINE_STRIDE - 1) / OUTLINE_STRIDE
    val gridHeight = (height + OUTLINE_STRIDE - 1) / OUTLINE_STRIDE
    val foreground = BooleanArray(gridWidth * gridHeight)
    for (gridY in 0 until gridHeight) {
        for (gridX in 0 until gridWidth) {
            val x = (gridX * OUTLINE_STRIDE).coerceAtMost(width - 1)
            val y = (gridY * OUTLINE_STRIDE).coerceAtMost(height - 1)
            foreground[gridY * gridWidth + gridX] =
                android.graphics.Color.alpha(pixels[y * width + x]) >= OUTLINE_THRESHOLD
        }
    }

    // Keep the largest connected subject and discard tiny alpha islands before
    // raster edges become visible. This preserves fins/tail while removing
    // segmentation speckles that otherwise read as broken neon noise.
    val labels = IntArray(foreground.size) { -1 }
    val componentSizes = ArrayList<Int>()
    var nextLabel = 0
    val queue = ArrayDeque<Int>()
    for (index in foreground.indices) {
        if (!foreground[index] || labels[index] >= 0) continue
        labels[index] = nextLabel
        queue.add(index)
        var size = 0
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            size += 1
            val x = current % gridWidth
            val y = current / gridWidth
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until gridWidth || ny !in 0 until gridHeight) continue
                val neighbor = ny * gridWidth + nx
                if (foreground[neighbor] && labels[neighbor] < 0) {
                    labels[neighbor] = nextLabel
                    queue.add(neighbor)
                }
            }
        }
        componentSizes += size
        nextLabel += 1
    }
    val largest = componentSizes.maxOrNull() ?: return emptyList()
    val minimumComponent = maxOf(4, largest / 200)
    val keep = componentSizes.map { it >= minimumComponent }

    val boundaryEdges = ArrayList<GridBoundaryEdge>()
    fun kept(gridX: Int, gridY: Int): Boolean {
        if (gridX !in 0 until gridWidth || gridY !in 0 until gridHeight) return false
        val label = labels[gridY * gridWidth + gridX]
        return label >= 0 && keep[label]
    }
    for (gridY in 0 until gridHeight) {
        for (gridX in 0 until gridWidth) {
            if (!kept(gridX, gridY)) continue
            if (!kept(gridX, gridY - 1)) {
                boundaryEdges += GridBoundaryEdge(GridPoint(gridX, gridY), GridPoint(gridX + 1, gridY))
            }
            if (!kept(gridX + 1, gridY)) {
                boundaryEdges += GridBoundaryEdge(GridPoint(gridX + 1, gridY), GridPoint(gridX + 1, gridY + 1))
            }
            if (!kept(gridX, gridY + 1)) {
                boundaryEdges += GridBoundaryEdge(GridPoint(gridX + 1, gridY + 1), GridPoint(gridX, gridY + 1))
            }
            if (!kept(gridX - 1, gridY)) {
                boundaryEdges += GridBoundaryEdge(GridPoint(gridX, gridY + 1), GridPoint(gridX, gridY))
            }
        }
    }
    fun normalized(point: GridPoint) = ContourPoint(
        x = min(point.x * OUTLINE_STRIDE, width) / width.toFloat(),
        y = min(point.y * OUTLINE_STRIDE, height) / height.toFloat(),
    )
    return traceGridContours(boundaryEdges).flatMap { gridLoop ->
        val points = simplifyGridLoop(gridLoop).map(::normalized)
        if (points.size < 4) return@flatMap emptyList()
        points.indices.map { index ->
            val previous = points[(index - 1 + points.size) % points.size]
            val current = points[index]
            val next = points[(index + 1) % points.size]
            val start = midpoint(previous, current)
            val end = midpoint(current, next)
            val control1 = interpolate(start, current, 2f / 3f)
            val control2 = interpolate(end, current, 2f / 3f)
            RecognitionContourSegment(
                startX = start.x,
                startY = start.y,
                endX = end.x,
                endY = end.y,
                control1X = control1.x,
                control1Y = control1.y,
                control2X = control2.x,
                control2Y = control2.y,
                beginsContour = index == 0,
                closesContour = index == points.lastIndex,
            )
        }
    }
}

private data class GridPoint(val x: Int, val y: Int)

private data class GridBoundaryEdge(val start: GridPoint, val end: GridPoint) {
    val direction: Int
        get() = when {
            end.x > start.x -> 0 // east
            end.y > start.y -> 1 // south
            end.x < start.x -> 2 // west
            else -> 3 // north
        }
}

private data class ContourPoint(val x: Float, val y: Float)

private fun traceGridContours(edges: List<GridBoundaryEdge>): List<List<GridPoint>> {
    if (edges.isEmpty()) return emptyList()
    val outgoing = edges.groupBy(GridBoundaryEdge::start)
    val remaining = LinkedHashSet(edges)
    val comparator = compareBy<GridBoundaryEdge> { it.start.y }
        .thenBy { it.start.x }
        .thenBy { it.direction }
    val contours = ArrayList<List<GridPoint>>()

    while (remaining.isNotEmpty()) {
        val first = remaining.minWithOrNull(comparator) ?: break
        val points = ArrayList<GridPoint>()
        points += first.start
        var edge = first
        var closed = false
        var visited = 0
        while (visited <= edges.size) {
            if (!remaining.remove(edge)) break
            points += edge.end
            visited += 1
            if (edge.end == first.start) {
                closed = true
                break
            }
            val candidates = outgoing[edge.end].orEmpty().filter(remaining::contains)
            if (candidates.isEmpty()) break
            edge = candidates.minBy { candidate ->
                val turn = (candidate.direction - edge.direction + 4) % 4
                when (turn) {
                    1 -> 0 // keep the foreground on the right at diagonal contacts
                    0 -> 1
                    3 -> 2
                    else -> 3
                }
            }
        }
        if (closed && points.size >= 5) contours += points.dropLast(1)
    }
    return contours
}

private fun simplifyGridLoop(points: List<GridPoint>): List<GridPoint> {
    if (points.size < 4) return points
    return points.filterIndexed { index, point ->
        val previous = points[(index - 1 + points.size) % points.size]
        val next = points[(index + 1) % points.size]
        val vertical = previous.x == point.x && point.x == next.x
        val horizontal = previous.y == point.y && point.y == next.y
        !vertical && !horizontal
    }
}

private fun midpoint(left: ContourPoint, right: ContourPoint) = ContourPoint(
    x = (left.x + right.x) / 2f,
    y = (left.y + right.y) / 2f,
)

private fun interpolate(start: ContourPoint, end: ContourPoint, fraction: Float) = ContourPoint(
    x = start.x + (end.x - start.x) * fraction,
    y = start.y + (end.y - start.y) * fraction,
)

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
