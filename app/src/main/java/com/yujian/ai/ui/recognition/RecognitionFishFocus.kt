package com.yujian.ai.ui.recognition

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ui.identify.RecognitionImageTransform
import kotlin.math.PI
import kotlin.math.sin

data class RecognitionContourSegment(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
)

/** Optional, visual-only subject response. A missing contour always falls back to halo. */
@Composable
fun RecognitionFishFocus(
    phase: RecognitionPhase,
    focusBox: NormalizedFishBox?,
    subjectBitmap: Bitmap?,
    subjectBox: NormalizedFishBox?,
    contour: List<RecognitionContourSegment>,
    transform: RecognitionImageTransform,
    modifier: Modifier = Modifier,
    visualClockMs: Long? = null,
) {
    val active = phase == RecognitionPhase.OUTLINE || phase == RecognitionPhase.CLASSIFYING
    if (!active || focusBox == null) return
    Canvas(modifier.fillMaxSize()) {
        val normalized = focusBox.normalized()
        val point = transform.mapBox(normalized)
        val center = Offset(point.x, point.y)
        val radiusX = transform.drawnWidth * normalized.width * .62f + 14.dp.toPx()
        val radiusY = transform.drawnHeight * normalized.height * .72f + 14.dp.toPx()
        val breathing = if (phase == RecognitionPhase.CLASSIFYING) {
            val t = ((visualClockMs ?: System.currentTimeMillis()) % 1_900L) / 1_900f
            .12f + ((sin(t * 2f * PI - PI / 2f) + 1f) / 2f).toFloat() * .06f
        } else .16f
        drawOval(
            brush = Brush.radialGradient(
                0f to Color.Transparent,
                .58f to Color.Transparent,
                .82f to Color(0x2EF6D99B),
                1f to Color(0x00F6D99B),
                center = center,
                radius = maxOf(radiusX, radiusY) * 1.18f,
            ),
            topLeft = Offset(center.x - radiusX * 1.18f, center.y - radiusY * 1.18f),
            size = androidx.compose.ui.geometry.Size(radiusX * 2.36f, radiusY * 2.36f),
        )
        // The fish interior stays transparent: only a restrained elliptical response is drawn.
        drawOval(
            color = Color(0xFFF6D99B).copy(alpha = breathing),
            topLeft = Offset(center.x - radiusX, center.y - radiusY),
            size = androidx.compose.ui.geometry.Size(radiusX * 2f, radiusY * 2f),
            style = Stroke(width = 1.2.dp.toPx()),
        )
        if (subjectBitmap != null && subjectBox != null && contour.isNotEmpty()) {
            val crop = subjectBox.normalized()
            val contourAlpha = if (phase == RecognitionPhase.CLASSIFYING) breathing.coerceIn(.30f, .42f) else .36f
            contour.forEach { segment ->
                val start = transform.mapNormalized(crop.x1 + segment.startX * crop.width, crop.y1 + segment.startY * crop.height)
                val end = transform.mapNormalized(crop.x1 + segment.endX * crop.width, crop.y1 + segment.endY * crop.height)
                drawLine(Color(0x66FFE7AE).copy(alpha = contourAlpha * .35f), Offset(start.x, start.y), Offset(end.x, end.y), 8.dp.toPx(), StrokeCap.Round)
                drawLine(Color(0xFFFFE7AE).copy(alpha = contourAlpha), Offset(start.x, start.y), Offset(end.x, end.y), 1.4.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}
