package com.yujian.ai.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.yujian.ai.ai.FishInputAssessment
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ai.RecognitionProgress
import com.yujian.ai.ai.subject.FishSubjectResult
import com.yujian.ai.ai.subject.SubjectStatus
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.identify.RecognitionImageTransform
import com.yujian.ai.ui.identify.RecognitionSourcePhoto
import com.yujian.ai.ui.recognition.RecognitionAmbientField
import com.yujian.ai.ui.recognition.RecognitionContourSegment
import com.yujian.ai.ui.recognition.RecognitionFishFocus
import com.yujian.ai.ui.recognition.RecognitionFishFocusLevel
import com.yujian.ai.ui.recognition.RecognitionMotionPolicy
import com.yujian.ai.ui.recognition.RecognitionMotionTraceSample
import com.yujian.ai.ui.recognition.RecognitionStatusOverlay
import com.yujian.ai.ui.recognition.RecognitionVisualStateController
import com.yujian.ai.ui.recognition.FISH_FOCUS_A_PROMOTION_DELAY_MS
import com.yujian.ai.ui.recognition.recognitionDisplayedFishFocusLevel
import com.yujian.ai.ui.recognition.result.RecognitionMediaPlanner
import com.yujian.ai.ui.recognition.rememberRecognitionMotionPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.min
import java.util.ArrayDeque

private const val OUTLINE_THRESHOLD = 36
// Two-pixel sampling keeps the contour faithful to fins and tail. Rendering is
// still bounded to three local path draws in RecognitionFishFocus.
private const val OUTLINE_STRIDE = 2
private const val LOG_TAG = "RecognitionProcessingScene"

@Composable
fun RecognizingScreen(
    image: SelectedImage?, onBack: () -> Unit,
    recognize: suspend ((RecognitionProgress) -> Unit) -> ProductionRecognitionResult,
    generateSubject: (suspend (SelectedImage, NormalizedFishBox) -> FishSubjectResult)? = null,
    onFinished: (ProductionRecognitionResult) -> Unit, onFailure: (Throwable) -> Unit = {},
    phaseOverride: RecognitionPhase? = null, visualClockOverrideMs: Long? = null,
    motionPolicyOverride: RecognitionMotionPolicy? = null,
    resolveProgressOverride: Float? = null,
) = RecognitionProcessingScene(
    image, onBack, recognize, generateSubject, onFinished, onFailure,
    phaseOverride, visualClockOverrideMs, motionPolicyOverride = motionPolicyOverride,
    resolveProgressOverride = resolveProgressOverride,
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
    resolveProgressOverride: Float? = null,
) {
    var realPhase by remember(image?.imageId) { mutableStateOf(RecognitionPhase.CAPTURED) }
    var visualPhase by remember(image?.imageId) { mutableStateOf(RecognitionPhase.CAPTURED) }
    var assessment by remember(image?.imageId) { mutableStateOf<FishInputAssessment?>(null) }
    var subjectBitmap by remember(image?.imageId) { mutableStateOf<Bitmap?>(null) }
    var subjectBox by remember(image?.imageId) { mutableStateOf<NormalizedFishBox?>(null) }
    var contour by remember(image?.imageId) { mutableStateOf(emptyList<RecognitionContourSegment>()) }
    var subjectResult by remember(image?.imageId) { mutableStateOf(FishSubjectResult(SubjectStatus.IDLE)) }
    var finishedResult by remember(image?.imageId) { mutableStateOf<ProductionRecognitionResult?>(null) }
    var delivered by remember(image?.imageId) { mutableStateOf(false) }
    var visualNowMs by remember(image?.imageId) { mutableStateOf(SystemClock.uptimeMillis()) }
    val controller = remember(image?.imageId) { RecognitionVisualStateController().also { it.reset(SystemClock.uptimeMillis()) } }

    LaunchedEffect(image?.imageId, assessment?.primary?.box) {
        val selected = image ?: return@LaunchedEffect
        val primary = assessment?.primary ?: return@LaunchedEffect
        val generator = generateSubject ?: return@LaunchedEffect
        subjectBitmap = null; subjectBox = null; contour = emptyList(); subjectResult = FishSubjectResult(SubjectStatus.PROCESSING)
        val result = try {
            generator(selected, primary.box)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        }
        subjectResult = result ?: FishSubjectResult(SubjectStatus.FAILED, errorCode = "SUBJECT_GENERATION_NULL")
        if (result?.status != SubjectStatus.READY || result.bitmapPath.isNullOrBlank()) {
            logFocusDiagnostic(subjectResult, contour.size, null, false, false)
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(result.bitmapPath) } ?: return@LaunchedEffect
        val extractedContour = withContext(Dispatchers.Default) { extractContour(loaded) }
        subjectBitmap = loaded; subjectBox = primary.box.expand(.12f); contour = extractedContour
        logFocusDiagnostic(result, extractedContour.size, primary.box, false, false)
    }

    LaunchedEffect(image?.imageId) {
        if (image == null) { onFailure(IllegalStateException("recognition image is unavailable")); return@LaunchedEffect }
        controller.reset(SystemClock.uptimeMillis()); realPhase = RecognitionPhase.CAPTURED; visualPhase = RecognitionPhase.CAPTURED; visualNowMs = SystemClock.uptimeMillis()
        assessment = null
        val result = try {
            recognize { progress ->
                assessment = progress.assessment ?: assessment
                realPhase = progress.phase
                controller.onPipelinePhase(progress.phase, SystemClock.uptimeMillis())
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Log.e(LOG_TAG, "Recognition runtime failed", error)
            realPhase = RecognitionPhase.FAILURE
            controller.onPipelinePhase(RecognitionPhase.FAILURE, SystemClock.uptimeMillis())
            onFailure(error)
            return@LaunchedEffect
        }
        assessment = result.assessment; finishedResult = result
        if (result.ready) {
            realPhase = RecognitionPhase.RESULT
            controller.onPipelinePhase(RecognitionPhase.RESULT, SystemClock.uptimeMillis())
        } else if (!delivered) {
            delivered = true
            onFinished(result)
        }
    }

    // The visual clock must remain stable while the real detector/classifier advances.
    // realPhase is intentionally NOT a LaunchedEffect key: restarting this loop on every
    // progress callback can cancel the presentation before it reaches RESULT.
    LaunchedEffect(image?.imageId, phaseOverride) {
        try {
            while (isActive && phaseOverride == null && !delivered) {
                visualNowMs = SystemClock.uptimeMillis()
                val nextVisualPhase = controller.current(visualNowMs)
                if (nextVisualPhase != visualPhase) {
                    Log.i(LOG_TAG, "visual phase $visualPhase -> $nextVisualPhase real=$realPhase")
                    visualPhase = nextVisualPhase
                }
                delay(16L)
            }
        } finally {
            Log.i(
                LOG_TAG,
                "visual loop ended active=$isActive delivered=$delivered override=$phaseOverride " +
                    "visual=$visualPhase real=$realPhase finishedReady=${finishedResult?.ready}",
            )
        }
    }

    LaunchedEffect(visualPhase, image?.imageId, phaseOverride) {
        if (phaseOverride == null) {
            // Observe what Compose actually presents, independently from the
            // fast detector/classifier callback cadence. Deliver RESULT only
            // after the new state reaches Compose presentation.
            val presentedAtMs = SystemClock.elapsedRealtime()
            onVisualPhasePresented(visualPhase, presentedAtMs)
            if (visualPhase == RecognitionPhase.RESULT && finishedResult?.ready == true && !delivered) {
                delivered = true
                Log.i(LOG_TAG, "delivering RESULT after presentation")
                onFinished(requireNotNull(finishedResult))
            }
        }
    }

    val rendered = phaseOverride ?: visualPhase
    val phaseElapsedMs = if (phaseOverride != null) 1_000L else controller.phaseElapsedMs(visualNowMs)
    val resolveProgress = resolveProgressOverride ?: if (phaseOverride != null) {
        0f
    } else {
        controller.resolveProgress(visualNowMs)
    }
    val resolveActive = resolveProgressOverride?.let { it > 0f } == true ||
        (phaseOverride == null && controller.isResolveActive(visualNowMs))
    val motionPolicy = motionPolicyOverride ?: rememberRecognitionMotionPolicy()
    val levelAAvailable = subjectBitmap != null && subjectBox != null && contour.isNotEmpty()
    val displayedFocusLevel = recognitionDisplayedFishFocusLevel(
        requestedLevel = motionPolicy.fishFocusLevel,
        levelAAvailable = levelAAvailable,
        phaseElapsedMs = phaseElapsedMs,
        phaseOverrideActive = phaseOverride != null,
    )
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val safeHorizontal = maxOf(
        24.dp,
        safeInsets.calculateLeftPadding(layoutDirection),
        safeInsets.calculateRightPadding(layoutDirection),
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
    Box(Modifier.fillMaxSize().background(Color(0xFF102D35))) {
        if (image != null) RecognitionPhoto(
            image.bitmap, rendered, assessment?.primary?.box, subjectBitmap, subjectBox, contour,
            visualClockOverrideMs, phaseElapsedMs, resolveProgress, resolveActive, motionPolicy, onMotionFrame,
            displayedFocusLevel, onFishFocusTransform, Modifier.fillMaxSize(),
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
                .align(Alignment.BottomCenter)
                .padding(horizontal = safeHorizontal)
                .padding(
                    bottom = safeInsets.calculateBottomPadding() + if (
                        rendered == RecognitionPhase.CAPTURED ||
                        rendered == RecognitionPhase.DETECTING
                    ) 84.dp else 34.dp,
                ),
            resolveProgress = resolveProgress,
            reduceMotion = motionPolicy.reduceMotion,
        )
    }
}

@Composable
private fun RecognitionPhoto(
    bitmap: Bitmap, phase: RecognitionPhase, focusBox: NormalizedFishBox?, subjectBitmap: Bitmap?,
    subjectBox: NormalizedFishBox?, contour: List<RecognitionContourSegment>, visualClockOverrideMs: Long?,
    phaseElapsedMs: Long, resolveProgress: Float, resolveActive: Boolean, motionPolicy: RecognitionMotionPolicy,
    onMotionFrame: ((RecognitionMotionTraceSample) -> Unit)?,
    focusLevel: RecognitionFishFocusLevel,
    onFishFocusTransform: ((RecognitionImageTransform) -> Unit)?, modifier: Modifier,
) = BoxWithConstraints(modifier) {
    val density = LocalDensity.current
    val mediaPlan = remember(bitmap, maxWidth, maxHeight, density) {
        RecognitionMediaPlanner.planProcessing(
            viewportWidthPx = with(density) { maxWidth.toPx() },
            viewportHeightPx = with(density) { maxHeight.toPx() },
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
        )
    }
    val transform = mediaPlan.primaryTransform
    if (onFishFocusTransform != null) {
        LaunchedEffect(transform, onFishFocusTransform) {
            onFishFocusTransform(transform)
        }
    }
    // The same source may fill the letterboxed bands decoratively, but this
    // transform is never used by focus geometry and never changes by phase.
    mediaPlan.decorativeBackgroundTransform?.let { backgroundTransform ->
        RecognitionSourcePhoto(
            bitmap = bitmap.asImageBitmap(),
            transform = backgroundTransform,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer(alpha = .16f),
        )
    }
    // The captured image is opaque on the first Recognition frame; only visual overlays animate.
    RecognitionSourcePhoto(
        bitmap = bitmap.asImageBitmap(),
        transform = transform,
        contentDescription = "正在识别的鱼获照片",
        modifier = Modifier.fillMaxSize(),
    )
    RecognitionAmbientField(
        phase, Modifier.fillMaxSize(), visualClockOverrideMs,
        lowPerformance = motionPolicy.lowPerformance,
        reduceMotion = motionPolicy.reduceMotion,
        resolveProgress = resolveProgress,
        resolveActive = resolveActive,
        qualityLevel = motionPolicy.qualityLevel,
        onMotionFrame = onMotionFrame,
    )
    RecognitionFishFocus(
        phase, focusBox, subjectBitmap, subjectBox, contour, transform, Modifier.fillMaxSize(),
        visualClockOverrideMs, phaseElapsedMs, resolveProgress,
        reduceMotion = motionPolicy.reduceMotion,
        lowPerformance = motionPolicy.lowPerformance,
        focusLevel = focusLevel,
    )
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

    val segments = ArrayList<RecognitionContourSegment>()
    fun kept(gridX: Int, gridY: Int): Boolean {
        if (gridX !in 0 until gridWidth || gridY !in 0 until gridHeight) return false
        val label = labels[gridY * gridWidth + gridX]
        return label >= 0 && keep[label]
    }
    fun addOrMerge(segment: RecognitionContourSegment) {
        val previous = segments.lastOrNull()
        if (previous != null &&
            previous.endX == segment.startX && previous.endY == segment.startY &&
            ((previous.startX == previous.endX && segment.startX == segment.endX) ||
                (previous.startY == previous.endY && segment.startY == segment.endY))
        ) {
            segments[segments.lastIndex] = previous.copy(endX = segment.endX, endY = segment.endY)
        } else {
            segments += segment
        }
    }
    for (gridY in 0 until gridHeight) {
        for (gridX in 0 until gridWidth) {
            if (!kept(gridX, gridY)) continue
            val left = gridX * OUTLINE_STRIDE / width.toFloat()
            val top = gridY * OUTLINE_STRIDE / height.toFloat()
            val right = min((gridX + 1) * OUTLINE_STRIDE, width) / width.toFloat()
            val bottom = min((gridY + 1) * OUTLINE_STRIDE, height) / height.toFloat()
            if (!kept(gridX - 1, gridY)) addOrMerge(RecognitionContourSegment(left, top, left, bottom))
            if (!kept(gridX + 1, gridY)) addOrMerge(RecognitionContourSegment(right, top, right, bottom))
            if (!kept(gridX, gridY - 1)) addOrMerge(RecognitionContourSegment(left, top, right, top))
            if (!kept(gridX, gridY + 1)) addOrMerge(RecognitionContourSegment(left, bottom, right, bottom))
        }
    }
    return segments
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

