package com.yujian.ai.ui.recognition

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

/**
 * Recognition V1.2 fish focus.
 *
 * Level A follows the real subject alpha contour and is the main AI moment.
 * Level B remains an unobtrusive bbox-derived halo/perimeter fallback and never
 * becomes a detector rectangle. The fish interior is never tinted.
 */
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

        val remaining = 1f - resolveProgress.coerceIn(0f, 1f)
        val fade = remaining * remaining

        val revealRaw = if (phase == RecognitionPhase.OUTLINE) {
            (phaseElapsedMs / 370f).coerceIn(0f, 1f)
        } else {
            1f
        }
        val reveal = revealRaw * revealRaw * (3f - 2f * revealRaw)

        val pulse = if (phase == RecognitionPhase.CLASSIFYING) {
            if (reduceMotion) {
                .90f
            } else {
                val t = ((visualClockMs ?: System.currentTimeMillis()) % 1_900L) / 1_900f
                val wave = ((sin(t * 2f * PI - PI / 2f) + 1f) / 2f).toFloat()
                .86f + wave * .14f
            }
        } else {
            reveal
        }

        // A usable subject contour is the visual authority. Keep only a small
        // local locating cue behind it, never an ellipse the eye can read first.
        val fallbackHaloStrength = if (levelAAvailable && !lowPerformance) .08f else .20f
        val haloStrength = fallbackHaloStrength * pulse * fade

        // A restrained bbox-derived local bloom supports the real contour but
        // never becomes the primary focus when Level A is available.
        drawOval(
            brush = Brush.radialGradient(
                0f to Color.Transparent,
                .56f to Color.Transparent,
                .76f to Color(0x00F6D99B),
                .88f to Color(0xFFF6D99B).copy(alpha = haloStrength),
                1f to Color.Transparent,
                center = center,
                radius = maxOf(radiusX, radiusY) * 1.18f,
            ),
            topLeft = Offset(center.x - radiusX * 1.18f, center.y - radiusY * 1.18f),
            size = androidx.compose.ui.geometry.Size(radiusX * 2.36f, radiusY * 2.36f),
        )

        // Level B / structural perimeter. It remains visible enough to show that
        // a fish was located even when subject alpha is unavailable.
        val perimeterAlpha = if (levelAAvailable && !lowPerformance) {
            .08f * pulse * fade
        } else {
            .32f * pulse * fade
        }
        drawOval(
            color = Color(0xFFFFE7AE).copy(alpha = perimeterAlpha),
            topLeft = Offset(center.x - radiusX, center.y - radiusY),
            size = androidx.compose.ui.geometry.Size(radiusX * 2f, radiusY * 2f),
            style = Stroke(width = if (levelAAvailable && !lowPerformance) .9.dp.toPx() else 1.35.dp.toPx()),
        )

        if (!lowPerformance && levelAAvailable) {
            val crop = requireNotNull(subjectBox).normalized()
            val contourStrength = when (phase) {
                RecognitionPhase.OUTLINE -> .92f * reveal
                RecognitionPhase.CLASSIFYING -> .78f * pulse
                else -> 0f
            }

            // Building one local path turns three passes into three draw calls,
            // rather than three calls per mask edge. This makes the detailed
            // real contour practical on API28 without adding a global effect.
            val realContour = Path()
            contour.forEach { segment ->
                val start = transform.mapNormalized(
                    crop.x1 + segment.startX * crop.width,
                    crop.y1 + segment.startY * crop.height,
                )
                val end = transform.mapNormalized(
                    crop.x1 + segment.endX * crop.width,
                    crop.y1 + segment.endY * crop.height,
                )
                realContour.moveTo(start.x, start.y)
                realContour.lineTo(end.x, end.y)
            }
            // Three restrained, local passes: 9dp outer bloom, 3.6dp mid glow,
            // and a 1.65dp hot core. The interior is never filled or tinted.
            drawPath(
                realContour,
                Color(0xFFFFD887).copy(alpha = (contourStrength * .17f * fade).coerceAtMost(.17f)),
                style = Stroke(9.dp.toPx(), cap = StrokeCap.Round),
            )
            drawPath(
                realContour,
                Color(0xFFFFDE9B).copy(alpha = (contourStrength * .46f * fade).coerceAtMost(.44f)),
                style = Stroke(3.6.dp.toPx(), cap = StrokeCap.Round),
            )
            drawPath(
                realContour,
                Color(0xFFFFE7AE).copy(alpha = (contourStrength * 1.0f * fade).coerceAtMost(.94f)),
                style = Stroke(1.65.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}
