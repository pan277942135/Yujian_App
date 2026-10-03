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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ui.identify.RecognitionImageTransform
import kotlin.math.PI
import kotlin.math.sin

internal fun recognitionOutlineHaloAlpha(phaseElapsedMs: Long, reduceMotion: Boolean): Float {
    if (reduceMotion) return .15f
    val elapsed = phaseElapsedMs.coerceAtLeast(0L)
    val envelope = when {
        elapsed < OUTLINE_HALO_ATTACK_MS -> smoothFraction(elapsed.toFloat() / OUTLINE_HALO_ATTACK_MS)
        elapsed < OUTLINE_HALO_SETTLE_MS -> 1f - smoothFraction(
            (elapsed - OUTLINE_HALO_ATTACK_MS).toFloat() /
                (OUTLINE_HALO_SETTLE_MS - OUTLINE_HALO_ATTACK_MS),
        )
        else -> 0f
    }
    return .10f + .08f * envelope
}

internal fun recognitionLevelBHaloAlpha(phaseElapsedMs: Long, reduceMotion: Boolean): Float {
    if (reduceMotion) return .28f
    val elapsed = phaseElapsedMs.coerceAtLeast(0L)
    val envelope = when {
        elapsed < LEVEL_B_HALO_ATTACK_MS -> smoothFraction(elapsed.toFloat() / LEVEL_B_HALO_ATTACK_MS)
        elapsed < LEVEL_B_HALO_SETTLE_MS -> 1f - smoothFraction(
            (elapsed - LEVEL_B_HALO_ATTACK_MS).toFloat() /
                (LEVEL_B_HALO_SETTLE_MS - LEVEL_B_HALO_ATTACK_MS),
        )
        else -> 0f
    }
    return .24f + .12f * envelope
}

internal fun recognitionLevelBPerimeterAlpha(phaseElapsedMs: Long, reduceMotion: Boolean): Float {
    if (reduceMotion) return .48f
    val elapsed = phaseElapsedMs.coerceAtLeast(0L)
    val envelope = when {
        elapsed < LEVEL_B_HALO_ATTACK_MS -> smoothFraction(elapsed.toFloat() / LEVEL_B_HALO_ATTACK_MS)
        elapsed < LEVEL_B_HALO_SETTLE_MS -> 1f - smoothFraction(
            (elapsed - LEVEL_B_HALO_ATTACK_MS).toFloat() /
                (LEVEL_B_HALO_SETTLE_MS - LEVEL_B_HALO_ATTACK_MS),
        )
        else -> 0f
    }
    return .42f + .14f * envelope
}

internal fun recognitionDisplayedFishFocusLevel(
    requestedLevel: RecognitionFishFocusLevel,
    levelAAvailable: Boolean,
    phaseElapsedMs: Long,
    phaseOverrideActive: Boolean,
): RecognitionFishFocusLevel = when {
    requestedLevel == RecognitionFishFocusLevel.C -> RecognitionFishFocusLevel.C
    requestedLevel == RecognitionFishFocusLevel.B -> RecognitionFishFocusLevel.B
    !levelAAvailable -> RecognitionFishFocusLevel.B
    phaseOverrideActive || phaseElapsedMs >= FISH_FOCUS_A_PROMOTION_DELAY_MS ->
        RecognitionFishFocusLevel.A
    else -> RecognitionFishFocusLevel.B
}

private fun smoothFraction(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private const val OUTLINE_HALO_ATTACK_MS = 120L
private const val OUTLINE_HALO_SETTLE_MS = 420L
private const val LEVEL_B_HALO_ATTACK_MS = 120L
private const val LEVEL_B_HALO_SETTLE_MS = 420L
internal const val FISH_FOCUS_A_PROMOTION_DELAY_MS = 420L

data class RecognitionContourSegment(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
)

internal data class RecognitionFishFocusRadii(
    val radiusX: Float,
    val radiusY: Float,
)

internal fun recognitionFishFocusRadii(
    transform: RecognitionImageTransform,
    focusBox: NormalizedFishBox,
    focusLevel: RecognitionFishFocusLevel,
    paddingPx: Float,
): RecognitionFishFocusRadii {
    val mapped = transform.mapBoxRect(focusBox.normalized())
    return RecognitionFishFocusRadii(
        radiusX = mapped.width * (if (focusLevel == RecognitionFishFocusLevel.B) .54f else .62f) + paddingPx,
        radiusY = mapped.height * (if (focusLevel == RecognitionFishFocusLevel.B) .60f else .72f) + paddingPx,
    )
}

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
    focusLevel: RecognitionFishFocusLevel = RecognitionFishFocusLevel.A,
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
        val mappedRect = transform.mapBoxRect(normalized)
        val center = Offset(mappedRect.center.x, mappedRect.center.y)
        val radii = recognitionFishFocusRadii(
            transform = transform,
            focusBox = normalized,
            focusLevel = effectiveLevel,
            paddingPx = 14.dp.toPx(),
        )
        val baseRadiusX = mappedRect.width * if (effectiveLevel == RecognitionFishFocusLevel.B) .54f else .62f
        val baseRadiusY = mappedRect.height * if (effectiveLevel == RecognitionFishFocusLevel.B) .60f else .72f
        val visualRadiusX = radii.radiusX
        val visualRadiusY = radii.radiusY

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
                !reduceMotion &&
                resolveProgress <= 0f
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

        val haloTarget = if (phase == RecognitionPhase.OUTLINE) {
            if (effectiveLevel == RecognitionFishFocusLevel.B) {
                recognitionLevelBHaloAlpha(phaseElapsedMs, reduceMotion)
            } else {
                recognitionOutlineHaloAlpha(phaseElapsedMs, reduceMotion)
            }
        } else if (reduceMotion) {
            .15f
        } else {
            .12f + .06f * wave
        }
        val contourCoreTarget = when {
            reduceMotion -> .39f
            phase == RecognitionPhase.OUTLINE -> .36f
            else -> .30f + .12f * wave
        }

        // A real detector box is already enough to acknowledge the fish. Show
        // its local receiving halo on the first OUTLINE frame; contour detail
        // continues to reveal independently as Level A data arrives.
        val haloReveal = if (
            phase == RecognitionPhase.OUTLINE && effectiveLevel == RecognitionFishFocusLevel.B
        ) {
            1f
        } else if (phase == RecognitionPhase.OUTLINE) {
            reveal.coerceAtLeast(.72f)
        } else {
            reveal
        }
        val haloAlpha = haloTarget * haloReveal * resolveStrength

        val radiusScale =
            if (effectiveLevel == RecognitionFishFocusLevel.C) .88f else 1f
        val radiusX = visualRadiusX * radiusScale
        val radiusY = visualRadiusY * radiusScale

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
                    alpha = (
                        if (phase == RecognitionPhase.OUTLINE) {
                            recognitionLevelBPerimeterAlpha(phaseElapsedMs, reduceMotion)
                        } else {
                            .30f + .12f * wave
                        }
                    ) * haloReveal * resolveStrength,
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
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(
                            maxOf(baseRadiusX, baseRadiusY) * 1.95f,
                            maxOf(baseRadiusX, baseRadiusY) * 4.55f,
                        ),
                        0f,
                    ),
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
                    alpha = (coreAlpha * .30f).coerceAtMost(.12f),
                ),
                style = Stroke(
                    width = 11.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
            )
            drawPath(
                realContour,
                Color(0xFFFFD887).copy(
                    alpha = (coreAlpha * .68f).coerceAtMost(.24f),
                ),
                style = Stroke(
                    width = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
            )
            drawPath(
                realContour,
                Color(0xFFFFE7AE).copy(
                    alpha = coreAlpha.coerceAtMost(.42f),
                ),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
            )
        }
    }
}
