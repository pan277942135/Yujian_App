package com.yujian.ai.ui.recognition

import android.graphics.Bitmap
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.ai.FishInputAssessment
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.theme.WaterTeal

data class RecognitionContourSegment(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
)

@Composable
fun RecognitionProcessingContent(
    image: SelectedImage,
    phase: RecognitionPhase,
    assessment: FishInputAssessment?,
    subjectBitmap: Bitmap?,
    subjectBox: NormalizedFishBox?,
    contour: List<RecognitionContourSegment>,
    error: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val reducedMotion = remember {
        runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
    val presentation = RecognitionPresentation.processing(phase)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        RecognitionPhotoStage(
            image = image,
            phase = phase,
            focusBox = assessment?.primary?.box,
            subjectBitmap = subjectBitmap,
            subjectBox = subjectBox,
            contour = contour,
            reducedMotion = reducedMotion,
            modifier = Modifier.fillMaxSize(),
        )
        RecognitionAmbientLight(phase = phase)
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(top = 16.dp, start = 10.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
        }

        if (error == null) {
            RecognitionStatusCard(
                presentation = presentation,
                reducedMotion = reducedMotion,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp),
            )
        } else {
            RecognitionTechnicalError(
                message = error,
                onBack = onBack,
                modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
            )
        }
    }
}

@Composable
private fun RecognitionPhotoStage(
    image: SelectedImage,
    phase: RecognitionPhase,
    focusBox: NormalizedFishBox?,
    subjectBitmap: Bitmap?,
    subjectBox: NormalizedFishBox?,
    contour: List<RecognitionContourSegment>,
    reducedMotion: Boolean,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val viewportWidth = with(density) { maxWidth.toPx() }
        val viewportHeight = with(density) { maxHeight.toPx() }
        val transform = remember(image.bitmap, viewportWidth, viewportHeight) {
            DisplayedImageTransform.crop(
                image.bitmap.width,
                image.bitmap.height,
                viewportWidth,
                viewportHeight,
            )
        }
        val focusActive = phase == RecognitionPhase.OUTLINE || phase == RecognitionPhase.CLASSIFYING || phase == RecognitionPhase.RESULT

        Image(
            bitmap = image.bitmap.asImageBitmap(),
            contentDescription = "正在识别的鱼获照片",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (focusActive && focusBox != null) {
            FishHighlightLayer(
                transform = transform,
                focusBox = focusBox,
                subjectBitmap = subjectBitmap,
                subjectBox = subjectBox,
                contour = contour,
                reducedMotion = reducedMotion,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.10f), Color.Transparent, Color.Black.copy(alpha = 0.18f)),
                    ),
                ),
        )
        Text(
            text = RecognitionPresentation.processing(phase).title,
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 76.dp, start = 20.dp)
                .background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun FishHighlightLayer(
    transform: DisplayedImageTransform,
    focusBox: NormalizedFishBox,
    subjectBitmap: Bitmap?,
    subjectBox: NormalizedFishBox?,
    contour: List<RecognitionContourSegment>,
    reducedMotion: Boolean,
    modifier: Modifier,
) {
    val pulse by if (reducedMotion) {
        remember { mutableFloatStateOf(1f) }
    } else {
        rememberInfiniteTransition(label = "recognition-focus").animateFloat(
            initialValue = 0.84f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "recognition-focus-alpha",
        )
    }
    Canvas(modifier) {
        val rect = transform.map(focusBox)
        val center = androidx.compose.ui.geometry.Offset(rect.left + rect.width / 2f, rect.top + rect.height / 2f)
        val radius = maxOf(rect.width, rect.height) * 0.72f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x55FFE0A0).copy(alpha = pulse), Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
        if (subjectBitmap != null && subjectBox != null && contour.isNotEmpty()) {
            val crop = subjectBox.normalized()
            contour.forEach { segment ->
                val x1 = transform.left + (crop.x1 + segment.startX * crop.width) * transform.displayedWidth
                val y1 = transform.top + (crop.y1 + segment.startY * crop.height) * transform.displayedHeight
                val x2 = transform.left + (crop.x1 + segment.endX * crop.width) * transform.displayedWidth
                val y2 = transform.top + (crop.y1 + segment.endY * crop.height) * transform.displayedHeight
                drawLine(
                    color = Color(0x45FFE0A0).copy(alpha = pulse),
                    start = androidx.compose.ui.geometry.Offset(x1, y1),
                    end = androidx.compose.ui.geometry.Offset(x2, y2),
                    strokeWidth = 7.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color(0xE8F8D59A).copy(alpha = pulse),
                    start = androidx.compose.ui.geometry.Offset(x1, y1),
                    end = androidx.compose.ui.geometry.Offset(x2, y2),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun RecognitionAmbientLight(phase: RecognitionPhase) {
    val alpha = when (phase) {
        RecognitionPhase.CAPTURED -> 0.10f
        RecognitionPhase.DETECTING -> 0.14f
        RecognitionPhase.OUTLINE -> 0.20f
        RecognitionPhase.CLASSIFYING, RecognitionPhase.RESULT -> 0.16f
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color.Transparent, WaterTeal.copy(alpha = alpha)),
                    radius = 900f,
                ),
            ),
    )
}

@Composable
private fun RecognitionStatusCard(
    presentation: RecognitionProcessingPresentation,
    reducedMotion: Boolean,
    modifier: Modifier,
) {
    val pulse by if (reducedMotion) {
        remember { mutableFloatStateOf(1f) }
    } else {
        rememberInfiniteTransition(label = "recognition-status").animateFloat(
            initialValue = 0.72f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
            label = "recognition-status-alpha",
        )
    }
    Row(
        modifier
            .fillMaxWidth(0.84f)
            .height(116.dp)
            .clip(RoundedCornerShape(58.dp))
            .background(Color(0xCC0A1C26))
            .border(1.dp, Color(0xA8FFE1A2).copy(alpha = pulse), RoundedCornerShape(58.dp))
            .padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawArc(
                    color = Color(0xFFFFD878).copy(alpha = pulse),
                    startAngle = -58f,
                    sweepAngle = 290f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx(), cap = StrokeCap.Round),
                )
                drawCircle(Color.White.copy(alpha = 0.20f), radius = 22.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(presentation.title, color = Color.White, fontSize = 21.sp)
            Text(presentation.subtitle, color = Color(0xFFBFD4DF), fontSize = 14.sp)
        }
    }
}

@Composable
private fun RecognitionTechnicalError(message: String, onBack: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xE60A1C26))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("识别没有完成", color = Color.White, fontSize = 22.sp)
        Text(message, color = Color(0xFFBFD4DF), fontSize = 14.sp)
        androidx.compose.material3.Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("重新拍摄或选择照片")
        }
    }
}
