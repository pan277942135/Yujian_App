package com.yujian.ai.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.yujian.ai.ai.FishInputAssessment
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ai.RecognitionProgress
import com.yujian.ai.ai.subject.FishSubjectResult
import com.yujian.ai.ai.subject.SubjectStatus
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.recognition.RecognitionContourSegment
import com.yujian.ai.ui.recognition.RecognitionProcessingContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val OUTLINE_THRESHOLD = 36
private const val OUTLINE_STRIDE = 4

@Composable
fun RecognizingScreen(
    image: SelectedImage?,
    onBack: () -> Unit,
    recognize: suspend ((RecognitionProgress) -> Unit) -> ProductionRecognitionResult,
    generateSubject: (suspend (SelectedImage, NormalizedFishBox) -> FishSubjectResult)? = null,
    onFinished: (ProductionRecognitionResult) -> Unit,
    phaseOverride: RecognitionPhase? = null,
) {
    var phase by remember { mutableStateOf(RecognitionPhase.CAPTURED) }
    var assessment by remember { mutableStateOf<FishInputAssessment?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var subjectBitmap by remember(image?.filePath) { mutableStateOf<Bitmap?>(null) }
    var subjectBox by remember(image?.filePath) { mutableStateOf<NormalizedFishBox?>(null) }
    var contour by remember(image?.filePath) { mutableStateOf(emptyList<RecognitionContourSegment>()) }

    LaunchedEffect(image?.filePath, assessment?.primary?.box) {
        val selected = image ?: return@LaunchedEffect
        val primary = assessment?.primary ?: return@LaunchedEffect
        val generator = generateSubject ?: return@LaunchedEffect
        subjectBitmap = null
        subjectBox = null
        contour = emptyList()
        val result = runCatching { generator(selected, primary.box) }.getOrNull()
        if (result?.status != SubjectStatus.READY || result.bitmapPath.isNullOrBlank()) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(result.bitmapPath) }
            ?: return@LaunchedEffect
        subjectBitmap = loaded
        subjectBox = primary.box.expand(0.12f)
        contour = withContext(Dispatchers.Default) { extractContour(loaded) }
    }

    LaunchedEffect(image?.filePath) {
        if (image == null) {
            error = "没有可识别的照片"
            return@LaunchedEffect
        }
        phase = RecognitionPhase.CAPTURED
        assessment = null
        error = null
        try {
            val completed = recognize { progress ->
                phase = progress.phase
                progress.assessment?.let { assessment = it }
            }
            phase = RecognitionPhase.RESULT
            assessment = completed.assessment
            onFinished(completed)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "识别失败，请重新拍摄或选择照片"
        }
    }

    image?.let { selected ->
        RecognitionProcessingContent(
            image = selected,
            phase = phaseOverride ?: phase,
            assessment = assessment,
            subjectBitmap = subjectBitmap,
            subjectBox = subjectBox,
            contour = contour,
            error = error,
            onBack = onBack,
        )
    }
}

private fun extractContour(bitmap: Bitmap): List<RecognitionContourSegment> {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 1 || height <= 1) return emptyList()
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    fun foreground(x: Int, y: Int): Boolean =
        AndroidColor.alpha(pixels[y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)]) >= OUTLINE_THRESHOLD

    val segments = ArrayList<RecognitionContourSegment>()
    var y = 0
    while (y < height) {
        var x = 0
        val yStep = minOf(OUTLINE_STRIDE, height - y)
        while (x < width) {
            val xStep = minOf(OUTLINE_STRIDE, width - x)
            if (foreground(x, y)) {
                if (x == 0 || !foreground(x - 1, y)) {
                    segments += RecognitionContourSegment(x / width.toFloat(), y / height.toFloat(), x / width.toFloat(), (y + yStep) / height.toFloat())
                }
                if (x + xStep >= width || !foreground(x + xStep, y)) {
                    val edge = (x + xStep).coerceAtMost(width) / width.toFloat()
                    segments += RecognitionContourSegment(edge, y / height.toFloat(), edge, (y + yStep) / height.toFloat())
                }
                if (y == 0 || !foreground(x, y - 1)) {
                    segments += RecognitionContourSegment(x / width.toFloat(), y / height.toFloat(), (x + xStep) / width.toFloat(), y / height.toFloat())
                }
                if (y + yStep >= height || !foreground(x, y + yStep)) {
                    val edge = (y + yStep).coerceAtMost(height) / height.toFloat()
                    segments += RecognitionContourSegment(x / width.toFloat(), edge, (x + xStep) / width.toFloat(), edge)
                }
            }
            x += xStep
        }
        y += yStep
    }
    return segments
}
