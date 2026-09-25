package com.yujian.ai.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.ai.FishInputAssessment
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ai.RecognitionProgress
import com.yujian.ai.ai.subject.FishSubjectResult
import com.yujian.ai.ai.subject.SubjectStatus
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.identify.calculateRecognitionImageTransform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

private const val OUTLINE_THRESHOLD = 36
private const val OUTLINE_STRIDE = 4
private const val LOG_TAG = "RecognitionProcessingScene"

private data class ContourSegment(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
)

/** Compatibility entry point retained for the navigation graph. */
@Composable
fun RecognizingScreen(
    image: SelectedImage?,
    onBack: () -> Unit,
    recognize: suspend ((RecognitionProgress) -> Unit) -> ProductionRecognitionResult,
    generateSubject: (suspend (SelectedImage, NormalizedFishBox) -> FishSubjectResult)? = null,
    onFinished: (ProductionRecognitionResult) -> Unit,
    onFailure: (Throwable) -> Unit = {},
    phaseOverride: RecognitionPhase? = null,
) {
    RecognitionProcessingScene(
        image = image,
        onBack = onBack,
        recognize = recognize,
        generateSubject = generateSubject,
        onFinished = onFinished,
        onFailure = onFailure,
        phaseOverride = phaseOverride,
    )
}

/** Shared photo + ambient layer + fish focus layer + frozen status card. */
@Composable
fun RecognitionProcessingScene(
    image: SelectedImage?,
    onBack: () -> Unit,
    recognize: suspend ((RecognitionProgress) -> Unit) -> ProductionRecognitionResult,
    generateSubject: (suspend (SelectedImage, NormalizedFishBox) -> FishSubjectResult)? = null,
    onFinished: (ProductionRecognitionResult) -> Unit,
    onFailure: (Throwable) -> Unit = {},
    phaseOverride: RecognitionPhase? = null,
) {
    var phase by remember(image?.imageId) { mutableStateOf(RecognitionPhase.CAPTURED) }
    var assessment by remember(image?.imageId) { mutableStateOf<FishInputAssessment?>(null) }
    var subjectBitmap by remember(image?.imageId) { mutableStateOf<Bitmap?>(null) }
    var subjectBox by remember(image?.imageId) { mutableStateOf<NormalizedFishBox?>(null) }
    var contour by remember(image?.imageId) { mutableStateOf(emptyList<ContourSegment>()) }

    LaunchedEffect(image?.imageId, assessment?.primary?.box) {
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

    LaunchedEffect(image?.imageId) {
        val selected = image
        if (selected == null) {
            val error = IllegalStateException("recognition image is unavailable")
            Log.e(LOG_TAG, "Recognition image unavailable", error)
            phase = RecognitionPhase.FAILURE
            onFailure(error)
            return@LaunchedEffect
        }
        phase = RecognitionPhase.CAPTURED
        assessment = null
        runCatching {
            recognize { progress ->
                phase = progress.phase
                progress.assessment?.let { assessment = it }
            }
        }.onSuccess { result ->
            assessment = result.assessment
            phase = if (result.prediction != null) RecognitionPhase.RESULT else RecognitionPhase.FAILURE
            onFinished(result)
        }.onFailure { error ->
            Log.e(LOG_TAG, "Recognition runtime failed", error)
            phase = RecognitionPhase.FAILURE
            onFailure(error)
        }
    }

    val renderedPhase = phaseOverride ?: phase
    Box(Modifier.fillMaxSize().background(Color(0xFF102D35))) {
        if (image != null) {
            RecognitionPhoto(
                bitmap = image.bitmap,
                focusBox = assessment?.primary?.box,
                subjectBitmap = subjectBitmap,
                subjectBox = subjectBox,
                contour = contour,
                focusActive = renderedPhase == RecognitionPhase.OUTLINE ||
                    renderedPhase == RecognitionPhase.CLASSIFYING,
                modifier = Modifier.fillMaxSize(),
            )
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(top = 18.dp, start = 12.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
        }

        RecognitionStatusCard(
            phase = renderedPhase,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 34.dp),
        )
    }
}

@Composable
private fun RecognitionStatusCard(phase: RecognitionPhase, modifier: Modifier = Modifier) {
    val copy = when (phase) {
        RecognitionPhase.CAPTURED -> "正在准备识别" to "AI 已获取这张照片"
        RecognitionPhase.DETECTING -> "正在理解照片" to "查找鱼获线索"
        RecognitionPhase.OUTLINE -> "已定位到鱼体" to "正在分析这次鱼获"
        RecognitionPhase.CLASSIFYING -> "正在认识这条鱼" to "分析鱼体特征"
        RecognitionPhase.RESULT -> "识别完成" to "正在整理识别结果"
        RecognitionPhase.FAILURE -> "识别没有完成" to GENERIC_RECOGNITION_FAILURE_MESSAGE.replace('\n', ' ')
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xD91A2C35))
            .border(1.dp, Color(0x80F6D79B), RoundedCornerShape(30.dp))
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(54.dp)) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFFFFD77E),
                trackColor = Color(0x668A979B),
                strokeWidth = 4.dp,
            )
            Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFFFFE7A8)))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(copy.first, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text(copy.second, color = Color(0xFFBFD0D7), fontSize = 14.sp)
        }
    }
}

@Composable
private fun RecognitionPhoto(
    bitmap: Bitmap,
    focusBox: NormalizedFishBox?,
    subjectBitmap: Bitmap?,
    subjectBox: NormalizedFishBox?,
    contour: List<ContourSegment>,
    focusActive: Boolean,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val transform = calculateRecognitionImageTransform(widthPx, heightPx, bitmap.width, bitmap.height)

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(22.dp).graphicsLayer { alpha = 0.25f },
            contentScale = ContentScale.Crop,
        )
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "正在识别的鱼获照片",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Canvas(Modifier.fillMaxSize()) {
            val box = focusBox?.normalized()
            if (focusActive && box != null) {
                val center = Offset(
                    transform.mapBox(box).x,
                    transform.mapBox(box).y,
                )
                val radius = min(transform.drawnWidth * box.width, transform.drawnHeight * box.height).coerceAtLeast(90f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xB5FFE1A0), Color(0x24FFE1A0), Color.Transparent),
                        center = center,
                        radius = radius * 1.55f,
                    ),
                    radius = radius * 1.55f,
                    center = center,
                )
            }
            if (subjectBitmap != null && subjectBox != null && contour.isNotEmpty()) {
                val crop = subjectBox.normalized()
                contour.forEach { segment ->
                    val start = transform.mapNormalized(
                        crop.x1 + segment.startX * crop.width,
                        crop.y1 + segment.startY * crop.height,
                    )
                    val end = transform.mapNormalized(
                        crop.x1 + segment.endX * crop.width,
                        crop.y1 + segment.endY * crop.height,
                    )
                    drawLine(
                        color = Color(0x66FFE0A0),
                        start = Offset(start.x, start.y),
                        end = Offset(end.x, end.y),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

internal const val GENERIC_RECOGNITION_FAILURE_MESSAGE = "识别没有完成\n请重新拍摄或选择照片"

internal fun recognitionFailureMessage(@Suppress("UNUSED_PARAMETER") error: Throwable): String =
    GENERIC_RECOGNITION_FAILURE_MESSAGE

private fun extractContour(bitmap: Bitmap): List<ContourSegment> {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 1 || height <= 1) return emptyList()
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    fun foreground(x: Int, y: Int): Boolean =
        android.graphics.Color.alpha(pixels[y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)]) >= OUTLINE_THRESHOLD

    val segments = ArrayList<ContourSegment>()
    var y = 0
    while (y < height) {
        var x = 0
        val yStep = min(OUTLINE_STRIDE, height - y)
        while (x < width) {
            val xStep = min(OUTLINE_STRIDE, width - x)
            if (foreground(x, y)) {
                if (x == 0 || !foreground(x - 1, y)) {
                    segments += ContourSegment(x / width.toFloat(), y / height.toFloat(), x / width.toFloat(), (y + yStep) / height.toFloat())
                }
                if (x + xStep >= width || !foreground(x + xStep, y)) {
                    val edge = (x + xStep).coerceAtMost(width) / width.toFloat()
                    segments += ContourSegment(edge, y / height.toFloat(), edge, (y + yStep) / height.toFloat())
                }
                if (y == 0 || !foreground(x, y - 1)) {
                    segments += ContourSegment(x / width.toFloat(), y / height.toFloat(), (x + xStep) / width.toFloat(), y / height.toFloat())
                }
                if (y + yStep >= height || !foreground(x, y + yStep)) {
                    val edge = (y + yStep).coerceAtMost(height) / height.toFloat()
                    segments += ContourSegment(x / width.toFloat(), edge, (x + xStep) / width.toFloat(), edge)
                }
            }
            x += xStep
        }
        y += yStep
    }
    return segments
}
