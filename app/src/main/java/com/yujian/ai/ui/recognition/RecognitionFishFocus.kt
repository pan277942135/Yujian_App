package com.yujian.ai.ui.recognition

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
 * Fish Focus A/B/C renderer.
 *
 * A = real detector bbox + subject contour + halo
 * B = real bbox-derived halo + restrained perimeter, no contour
 * C = real bbox center-biased halo only
 *
 * Capability/data fallback may use B when a contour is unavailable. Performance
 * degradation must still follow D0/D1/D2 -> D3 -> D4 in RecognitionMotionPolicy.
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
    focusLevel: RecognitionFishFocusLevel =
        if (lowPerformance) RecognitionFishFocusLevel.B else RecognitionFishFocusLevel.A,
) {
    val active =
        phase == RecognitionPhase.OUTLINE ||
            phase == RecognitionPhase.CLASSIFYING
    if (!active || focusBox == null) return

    val levelAAvailable =
        subjectBitmap != null &&
            subjectBox != null &&
            contour.isNotEmpty()

    val effectiveLevel = when {
        focusLevel == RecognitionFishFocusLevel.C ->
            RecognitionFishFocusLevel.C
        focusLevel == RecognitionFishFocusLevel.B ->
            RecognitionFishFocusLevel.B
        levelAAvailable ->
            RecognitionFishFocusLevel.A
        else ->
            RecognitionFishFocusLevel.B
    }

    val focusTag =
        "recognition-fish-focus-level-${effectiveLevel.name.lowercase()}" +
            if (lowPerformance) "-low-performance" else ""

    Canvas(
        modifier
            .fillMaxSize()
            .testTag(focusTag),
    ) {
        val normalized = focusBox.normalized()
        val mapped = transform.mapBox(normalized)
        val center = Offset(mapped.x, mapped.y)

        val baseRadiusX =
            transform.drawnWidth * normalized.width * .62f +
                14.dp.toPx()
        val baseRadiusY =
            transform.drawnHeight * normalized.height * .72f +
                14.dp.toPx()

        val remaining =
            1f - resolveProgress.coerceIn(0f, 1f)
        val resolveStrength = remaining * remaining

        val revealDuration =
            if (reduceMotion) 180f else 380f
        val revealRaw =
            if (phase == RecognitionPhase.OUTLINE) {
                (phaseElapsedMs / revealDuration).coerceIn(0f, 1f)
            } else {
                1f
            }
        val reveal =
            revealRaw * revealRaw * (3f - 2f * revealRaw)

        val wave =
            if (
                phase == RecognitionPhase.CLASSIFYING &&
                !reduceMotion
            ) {
                val t =
                    ((visualClockMs ?: System.currentTimeMillis()) % 1_900L) /
                        1_900f
                (
                    (sin(t * 2f * PI - PI / 2f) + 1f) /
                        2f
                    ).toFloat()
            } else {
                .5f
            }

        val haloTarget = when {
            reduceMotion -> .15f
            phase == RecognitionPhase.OUTLINE -> .16f
            else -> .12f + .06f * wave
        }
        val contourCoreTarget = when {
            reduceMotion -> .39f
            phase == RecognitionPhase.OUTLINE -> .36f
            else -> .30f + .12f * wave
        }

        val haloAlpha =
            haloTarget * reveal * resolveStrength

        val radiusScale =
            if (effectiveLevel == RecognitionFishFocusLevel.C) .88f else 1f
        val radiusX = baseRadiusX * radiusScale
        val radiusY = baseRadiusY * radiusScale

        drawOval(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    .52f to Color.Transparent,
                    .75f to Color(0x00F6D99B),
                    .88f to Color(0xFFF6D99B).copy(alpha = haloAlpha),
                    1f to Color.Transparent,
                ),
                center = center,
                radius = maxOf(radiusX, radiusY) * 1.18f,
            ),
            topLeft = Offset(
                center.x - radiusX * 1.18f,
                center.y - radiusY * 1.18f,
            ),
            size = Size(
                radiusX * 2.36f,
                radiusY * 2.36f,
            ),
        )

        if (effectiveLevel == RecognitionFishFocusLevel.B) {
            drawOval(
                color = Color(0xFFFFE7AE).copy(
                    alpha = (.22f * reveal * resolveStrength)
                        .coerceAtMost(.22f),
                ),
                topLeft = Offset(
                    center.x - baseRadiusX,
                    center.y - baseRadiusY,
                ),
                size = Size(
                    baseRadiusX * 2f,
                    baseRadiusY * 2f,
                ),
                style = Stroke(
                    width = 1.2.dp.toPx(),
                ),
            )
        }

        if (
            effectiveLevel == RecognitionFishFocusLevel.A &&
            levelAAvailable
        ) {
            val crop = requireNotNull(subjectBox).normalized()
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

            val coreAlpha =
                contourCoreTarget * reveal * resolveStrength

            drawPath(
                realContour,
                Color(0xFFFFD887).copy(
                    alpha = (coreAlpha * .20f).coerceAtMost(.09f),
                ),
                style = Stroke(
                    width = 9.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
            )
            drawPath(
                realContour,
                Color(0xFFFFDE9B).copy(
                    alpha = (coreAlpha * .50f).coerceAtMost(.21f),
                ),
                style = Stroke(
                    width = 3.6.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
            )
            drawPath(
                realContour,
                Color(0xFFFFE7AE).copy(
                    alpha = coreAlpha.coerceAtMost(.42f),
                ),
                style = Stroke(
                    width = 1.4.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
            )
        }
    }
}
