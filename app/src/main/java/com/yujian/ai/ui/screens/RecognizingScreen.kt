package com.yujian.ai.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
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
import com.yujian.ai.ai.RecognitionRuntimeContract
import com.yujian.ai.ai.subject.FishSubjectResult
import com.yujian.ai.ai.subject.SubjectStatus
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.theme.CardWhite
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.SoftWater
import com.yujian.ai.ui.theme.WarmBackground
import com.yujian.ai.ui.theme.WaterTeal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.min

private const val OUTLINE_THRESHOLD = 36
private const val OUTLINE_STRIDE = 4

private data class ContourSegment(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
)

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
    var contour by remember(image?.filePath) { mutableStateOf(emptyList<ContourSegment>()) }

    LaunchedEffect(image?.filePath, assessment?.primary?.box) {
        val selected = image ?: return@LaunchedEffect
        val primary = assessment?.primary ?: return@LaunchedEffect
        val generator = generateSubject ?: return@LaunchedEffect
        subjectBitmap = null
        subjectBox = null
        contour = emptyList()
        val result = runCatching { generator(selected, primary.box) }.getOrNull()
        if (result?.status != SubjectStatus.READY || result.bitmapPath.isNullOrBlank()) {
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(result.bitmapPath)
        } ?: return@LaunchedEffect
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
        coroutineScope {
            val startedAt = SystemClock.elapsedRealtime()
            val recognition = async {
                runCatching {
                    // Recognition progress is consumed as data only. The
                    // production timeline below controls what the user sees.
                    recognize { progress ->
                        progress.assessment?.let { assessment = it }
                    }
                }
            }

            while (!recognition.isCompleted) {
                phase = RecognitionRuntimeContract.phaseAt(SystemClock.elapsedRealtime() - startedAt)
                delay(40)
            }

            val result = recognition.await()
            val remaining = RecognitionRuntimeContract.RESULT_START_MS -
                (SystemClock.elapsedRealtime() - startedAt)
            if (remaining > 0L) delay(remaining)

            phase = RecognitionPhase.RESULT
            result
                .onSuccess { completed ->
                    assessment = completed.assessment
                    onFinished(completed)
                }
                .onFailure { error = it.message ?: "识别失败，请重新拍摄或选择照片" }
        }
    }

    val renderedPhase = phaseOverride ?: phase

    Column(
        Modifier
            .fillMaxSize()
            .background(WarmBackground)
            .padding(top = 18.dp),
    ) {
        Text(
            text = if (phase == RecognitionPhase.RESULT) "正在认识这条鱼" else "正在认识这条鱼",
            color = DeepInk,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Text(
            text = when {
                error != null -> "这次没有完成识别"
                phase == RecognitionPhase.CAPTURED || phase == RecognitionPhase.DETECTING -> "正在寻找鱼体"
                else -> "正在认识这条鱼"
            },
            color = MutedInk,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )

        if (image != null) {
            RecognitionPhoto(
                bitmap = image.bitmap,
                focusBox = assessment?.primary?.box,
                subjectBitmap = subjectBitmap,
                subjectBox = subjectBox,
                contour = contour,
                focusActive = renderedPhase.ordinal >= RecognitionPhase.OUTLINE.ordinal,
                phase = renderedPhase,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(390.dp)
                    .clip(RoundedCornerShape(28.dp)),
            )
        }

        if (error != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(error.orEmpty(), color = Color(0xFFB24A3A), fontSize = 13.sp)
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WaterTeal),
                ) { Text("重新拍摄或选择照片", fontWeight = FontWeight.SemiBold) }
            }
        } else {
            Text(
                text = RecognitionRuntimeContract.labelFor(renderedPhase),
                color = WaterTeal,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 18.dp),
            )
            Text(
                text = "请稍等片刻",
                color = MutedInk,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),
            )
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
    phase: RecognitionPhase,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier.background(Color(0xFF123D3D))) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val scale = min(widthPx / bitmap.width.toFloat(), heightPx / bitmap.height.toFloat())
        val drawnWidth = bitmap.width * scale
        val drawnHeight = bitmap.height * scale
        val left = (widthPx - drawnWidth) / 2f
        val top = (heightPx - drawnHeight) / 2f

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(18.dp).graphicsLayerForRecognition(0.28f),
            contentScale = ContentScale.Crop,
        )
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "正在识别的鱼获照片",
            modifier = Modifier
                .offset(with(density) { left.toDp() }, with(density) { top.toDp() })
                .size(with(density) { drawnWidth.toDp() }, with(density) { drawnHeight.toDp() }),
            contentScale = ContentScale.FillBounds,
        )

        Canvas(Modifier.fillMaxSize()) {
            val box = focusBox?.normalized()
            if (focusActive && box != null) {
                val center = Offset(
                    left + (box.x1 + box.width / 2f) * drawnWidth,
                    top + (box.y1 + box.height / 2f) * drawnHeight,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x35FFE0A0), Color.Transparent),
                        center = center,
                        radius = min(drawnWidth, drawnHeight) * 0.40f,
                    ),
                    radius = min(drawnWidth, drawnHeight) * 0.40f,
                    center = center,
                )
            }
            if (subjectBitmap != null && subjectBox != null && contour.isNotEmpty()) {
                val crop = subjectBox.normalized()
                contour.forEach { segment ->
                    val x1 = left + (crop.x1 + segment.startX * crop.width) * drawnWidth
                    val y1 = top + (crop.y1 + segment.startY * crop.height) * drawnHeight
                    val x2 = left + (crop.x1 + segment.endX * crop.width) * drawnWidth
                    val y2 = top + (crop.y1 + segment.endY * crop.height) * drawnHeight
                    drawLine(
                        color = Color(0x35F6D79B),
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 7.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = Color(0xE6F4D08E),
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 2.2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }

        Text(
            text = RecognitionRuntimeContract.labelFor(phase),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

private fun Modifier.graphicsLayerForRecognition(alpha: Float): Modifier =
    this.graphicsLayer { this.alpha = alpha }

private fun extractContour(bitmap: Bitmap): List<ContourSegment> {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 1 || height <= 1) return emptyList()
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    fun foreground(x: Int, y: Int): Boolean =
        AndroidColor.alpha(pixels[y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)]) >= OUTLINE_THRESHOLD

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
