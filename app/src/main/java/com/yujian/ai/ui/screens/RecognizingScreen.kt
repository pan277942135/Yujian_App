package com.yujian.ai.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.yujian.ai.ai.FishInputAssessment
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ai.RecognitionProgress
import com.yujian.ai.ai.subject.FishSubjectResult
import com.yujian.ai.ai.subject.SubjectStatus
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.identify.calculateRecognitionImageTransform
import com.yujian.ai.ui.recognition.RecognitionAmbientField
import com.yujian.ai.ui.recognition.RecognitionContourSegment
import com.yujian.ai.ui.recognition.RecognitionFishFocus
import com.yujian.ai.ui.recognition.RecognitionStatusOverlay
import com.yujian.ai.ui.recognition.RecognitionVisualStateController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.min

private const val OUTLINE_THRESHOLD = 36
private const val OUTLINE_STRIDE = 4
private const val LOG_TAG = "RecognitionProcessingScene"

@Composable
fun RecognizingScreen(
    image: SelectedImage?, onBack: () -> Unit,
    recognize: suspend ((RecognitionProgress) -> Unit) -> ProductionRecognitionResult,
    generateSubject: (suspend (SelectedImage, NormalizedFishBox) -> FishSubjectResult)? = null,
    onFinished: (ProductionRecognitionResult) -> Unit, onFailure: (Throwable) -> Unit = {},
    phaseOverride: RecognitionPhase? = null, visualClockOverrideMs: Long? = null,
) = RecognitionProcessingScene(image, onBack, recognize, generateSubject, onFinished, onFailure, phaseOverride, visualClockOverrideMs)

/** Presentation-only shell: it never changes detector, crop, classifier, or result semantics. */
@Composable
fun RecognitionProcessingScene(
    image: SelectedImage?, onBack: () -> Unit,
    recognize: suspend ((RecognitionProgress) -> Unit) -> ProductionRecognitionResult,
    generateSubject: (suspend (SelectedImage, NormalizedFishBox) -> FishSubjectResult)? = null,
    onFinished: (ProductionRecognitionResult) -> Unit, onFailure: (Throwable) -> Unit = {},
    phaseOverride: RecognitionPhase? = null, visualClockOverrideMs: Long? = null,
) {
    var realPhase by remember(image?.imageId) { mutableStateOf(RecognitionPhase.CAPTURED) }
    var visualPhase by remember(image?.imageId) { mutableStateOf(RecognitionPhase.CAPTURED) }
    var assessment by remember(image?.imageId) { mutableStateOf<FishInputAssessment?>(null) }
    var subjectBitmap by remember(image?.imageId) { mutableStateOf<Bitmap?>(null) }
    var subjectBox by remember(image?.imageId) { mutableStateOf<NormalizedFishBox?>(null) }
    var contour by remember(image?.imageId) { mutableStateOf(emptyList<RecognitionContourSegment>()) }
    var finishedResult by remember(image?.imageId) { mutableStateOf<ProductionRecognitionResult?>(null) }
    var delivered by remember(image?.imageId) { mutableStateOf(false) }
    var visualNowMs by remember(image?.imageId) { mutableStateOf(SystemClock.uptimeMillis()) }
    val controller = remember(image?.imageId) { RecognitionVisualStateController().also { it.reset(SystemClock.uptimeMillis()) } }

    LaunchedEffect(image?.imageId, assessment?.primary?.box) {
        val selected = image ?: return@LaunchedEffect
        val primary = assessment?.primary ?: return@LaunchedEffect
        val generator = generateSubject ?: return@LaunchedEffect
        subjectBitmap = null; subjectBox = null; contour = emptyList()
        val result = runCatching { generator(selected, primary.box) }.getOrNull()
        if (result?.status != SubjectStatus.READY || result.bitmapPath.isNullOrBlank()) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(result.bitmapPath) } ?: return@LaunchedEffect
        subjectBitmap = loaded; subjectBox = primary.box.expand(.12f)
        contour = withContext(Dispatchers.Default) { extractContour(loaded) }
    }

    LaunchedEffect(image?.imageId) {
        if (image == null) { onFailure(IllegalStateException("recognition image is unavailable")); return@LaunchedEffect }
        controller.reset(SystemClock.uptimeMillis()); realPhase = RecognitionPhase.CAPTURED; visualPhase = RecognitionPhase.CAPTURED; visualNowMs = SystemClock.uptimeMillis()
        assessment = null
        runCatching {
            recognize { progress ->
                assessment = progress.assessment ?: assessment
                realPhase = progress.phase
                controller.onPipelinePhase(progress.phase, SystemClock.uptimeMillis())
            }
        }.onSuccess { result ->
            assessment = result.assessment; finishedResult = result
            if (result.ready) {
                realPhase = RecognitionPhase.RESULT
                controller.onPipelinePhase(RecognitionPhase.RESULT, SystemClock.uptimeMillis())
            } else if (!delivered) { delivered = true; onFinished(result) }
        }.onFailure { error ->
            Log.e(LOG_TAG, "Recognition runtime failed", error)
            realPhase = RecognitionPhase.FAILURE; controller.onPipelinePhase(RecognitionPhase.FAILURE, SystemClock.uptimeMillis()); onFailure(error)
        }
    }

    LaunchedEffect(realPhase, image?.imageId) {
        while (isActive && phaseOverride == null && !delivered) {
            visualNowMs = SystemClock.uptimeMillis()
            visualPhase = controller.current(visualNowMs)
            if (visualPhase == RecognitionPhase.RESULT && finishedResult?.ready == true) {
                delivered = true; onFinished(requireNotNull(finishedResult))
            }
            delay(16L)
        }
    }

    val rendered = phaseOverride ?: visualPhase
    val phaseElapsedMs = if (phaseOverride != null) 1_000L else controller.phaseElapsedMs(visualNowMs)
    val resolveProgress = if (phaseOverride != null) 0f else controller.resolveProgress(visualNowMs)
    Box(Modifier.fillMaxSize().background(Color(0xFF102D35))) {
        if (image != null) RecognitionPhoto(image.bitmap, rendered, assessment?.primary?.box, subjectBitmap, subjectBox, contour, visualClockOverrideMs, phaseElapsedMs, resolveProgress, Modifier.fillMaxSize())
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(top = 18.dp, start = 12.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
        }
        RecognitionStatusOverlay(
            rendered,
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = if (rendered == RecognitionPhase.DETECTING) 84.dp else 34.dp),
            resolveProgress,
        )
    }
}

@Composable
private fun RecognitionPhoto(
    bitmap: Bitmap, phase: RecognitionPhase, focusBox: NormalizedFishBox?, subjectBitmap: Bitmap?,
    subjectBox: NormalizedFishBox?, contour: List<RecognitionContourSegment>, visualClockOverrideMs: Long?,
    phaseElapsedMs: Long, resolveProgress: Float, modifier: Modifier,
) = BoxWithConstraints(modifier) {
    val density = LocalDensity.current
    val transform = calculateRecognitionImageTransform(with(density) { maxWidth.toPx() }, with(density) { maxHeight.toPx() }, bitmap.width, bitmap.height)
    // The captured image is opaque on the first Recognition frame; only visual overlays animate.
    Image(bitmap.asImageBitmap(), "正在识别的鱼获照片", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    RecognitionAmbientField(phase, Modifier.fillMaxSize(), visualClockOverrideMs, resolveProgress = resolveProgress)
    RecognitionFishFocus(phase, focusBox, subjectBitmap, subjectBox, contour, transform, Modifier.fillMaxSize(), visualClockOverrideMs, phaseElapsedMs, resolveProgress)
}

internal const val GENERIC_RECOGNITION_FAILURE_MESSAGE = "识别没有完成\n请重新拍摄或选择照片"
internal fun recognitionFailureMessage(@Suppress("UNUSED_PARAMETER") error: Throwable): String = GENERIC_RECOGNITION_FAILURE_MESSAGE

private fun extractContour(bitmap: Bitmap): List<RecognitionContourSegment> {
    val width = bitmap.width; val height = bitmap.height
    if (width <= 1 || height <= 1) return emptyList()
    val pixels = IntArray(width * height); bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    fun foreground(x: Int, y: Int) = android.graphics.Color.alpha(pixels[y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)]) >= OUTLINE_THRESHOLD
    val segments = ArrayList<RecognitionContourSegment>(); var y = 0
    while (y < height) {
        var x = 0; val yStep = min(OUTLINE_STRIDE, height - y)
        while (x < width) {
            val xStep = min(OUTLINE_STRIDE, width - x)
            if (foreground(x, y)) {
                if (x == 0 || !foreground(x - 1, y)) segments += RecognitionContourSegment(x / width.toFloat(), y / height.toFloat(), x / width.toFloat(), (y + yStep) / height.toFloat())
                if (x + xStep >= width || !foreground(x + xStep, y)) { val edge = (x + xStep).coerceAtMost(width) / width.toFloat(); segments += RecognitionContourSegment(edge, y / height.toFloat(), edge, (y + yStep) / height.toFloat()) }
                if (y == 0 || !foreground(x, y - 1)) segments += RecognitionContourSegment(x / width.toFloat(), y / height.toFloat(), (x + xStep) / width.toFloat(), y / height.toFloat())
                if (y + yStep >= height || !foreground(x, y + yStep)) { val edge = (y + yStep).coerceAtMost(height) / height.toFloat(); segments += RecognitionContourSegment(x / width.toFloat(), edge, (x + xStep) / width.toFloat(), edge) }
            }
            x += xStep
        }; y += yStep
    }
    return segments
}
