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
import androidx.compose.ui.platform.testTag
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
    phaseElapsedMs: Long = Long.MAX_VALUE,
    resolveProgress: Float = 0f,
    reduceMotion: Boolean = false,
    lowPerformance: Boolean = false,
) {
    val active = phase == RecognitionPhase.OUTLINE || phase == RecognitionPhase.CLASSIFYING
    if (!active || focusBox == null) return
    val levelAAvailable = subjectBitmap != null && subjectBox != null && contour.isNotEmpty()
    val focusTag = when {
        lowPerformance -> "recognition-fish-focus-level-b-low-performance"
        levelAAvailable -> "recognition-fish-focus-level-a"
        else -> "recognition-fish-focus-level-b"
    }
    Canvas(modifier.fillMaxSize().testTag(focusTag)) {
        val normalized = focusBox.normalized()
        val point = transform.mapBox(normalized)
        val center = Offset(point.x, point.y)
        val radiusX = transform.drawnWidth * normalized.width * .62f + 14.dp.toPx()
        val radiusY = transform.drawnHeight * normalized.height * .72f + 14.dp.toPx()
        val fade = 1f - resolveProgress.coerceIn(0f, 1f)
        val reveal = if (phase == RecognitionPhase.OUTLINE) (phaseElapsedMs / 360f).coerceIn(0f, 1f) else 1f
        val breathing = if (phase == RecognitionPhase.CLASSIFYING) {
            if (reduceMotion) {
                .15f
            } else {
                val t = ((visualClockMs ?: System.currentTimeMillis()) % 1_900L) / 1_900f
                .15f + ((sin(t * 2f * PI - PI / 2f) + 1f) / 2f).toFloat() * .05f
            }
        } else .19f * reveal
        val haloAlpha = if (phase == RecognitionPhase.CLASSIFYING) breathing.coerceIn(.12f, .18f) else .18f * reveal
        drawOval(
            brush = Brush.radialGradient(
                0f to Color.Transparent,
                .58f to Color.Transparent,
                .82f to Color(0x2EF6D99B).copy(alpha = haloAlpha * fade),
                1f to Color(0x00F6D99B),
                center = center,
                radius = maxOf(radiusX, radiusY) * 1.18f,
            ),
            topLeft = Offset(center.x - radiusX * 1.18f, center.y - radiusY * 1.18f),
            size = androidx.compose.ui.geometry.Size(radiusX * 2.36f, radiusY * 2.36f),
        )
        // The fish interior stays transparent: only a restrained elliptical response is drawn.
        drawOval(
            color = Color(0xFFF6D99B).copy(alpha = breathing * fade),
            topLeft = Offset(center.x - radiusX, center.y - radiusY),
            size = androidx.compose.ui.geometry.Size(radiusX * 2f, radiusY * 2f),
            style = Stroke(width = 1.2.dp.toPx()),
        )
        if (!lowPerformance && levelAAvailable) {
            val crop = subjectBox.normalized()
            // Use the upper half of the frozen contour-alpha range
            // so the located fish remains legible on a real phone display
            // without introducing a detector box or HUD treatment.
            val contourAlpha = if (phase == RecognitionPhase.CLASSIFYING) {
                if (reduceMotion) .36f else .36f + ((breathing - .15f) / .05f).coerceIn(0f, 1f) * .06f
            } else .42f * reveal
            contour.forEach { segment ->
                val start = transform.mapNormalized(crop.x1 + segment.startX * crop.width, crop.y1 + segment.startY * crop.height)
                val end = transform.mapNormalized(crop.x1 + segment.endX * crop.width, crop.y1 + segment.endY * crop.height)
                drawLine(Color(0x66FFE7AE).copy(alpha = contourAlpha * .42f * fade), Offset(start.x, start.y), Offset(end.x, end.y), 8.dp.toPx(), StrokeCap.Round)
                drawLine(Color(0xFFFFE7AE).copy(alpha = contourAlpha * fade), Offset(start.x, start.y), Offset(end.x, end.y), 1.5.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}
