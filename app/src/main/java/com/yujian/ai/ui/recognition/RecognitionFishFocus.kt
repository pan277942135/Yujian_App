package com.yujian.ai.ui.recognition

import android.graphics.Bitmap
import android.graphics.Path as AndroidPath
import android.graphics.PathMeasure as AndroidPathMeasure
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ui.identify.RecognitionImageTransform

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

internal fun recognitionFishGoldStrength(elapsedMs: Long, reduceMotion: Boolean = false): Float {
    if (reduceMotion) return 0.18f
    val t = elapsedMs.coerceAtLeast(0L)
    val keys = longArrayOf(950L, 1_200L, 1_550L, 2_800L)
    val values = floatArrayOf(.15f, .45f, 1f, 1f)
    if (t < keys.first()) return 0f
    val upper = keys.indexOfFirst { it >= t }.let { if (it < 0) keys.lastIndex else it }
    if (upper == 0) return values[0]
    val lower = upper - 1
    val p = smoothFraction((t - keys[lower]).toFloat() / (keys[upper] - keys[lower]).toFloat())
    return values[lower] + (values[upper] - values[lower]) * p
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
    val control1X: Float = startX,
    val control1Y: Float = startY,
    val control2X: Float = endX,
    val control2Y: Float = endY,
    val beginsContour: Boolean = false,
    val closesContour: Boolean = false,
)

internal fun buildRecognitionContourPath(
    transform: RecognitionImageTransform,
    subjectBox: NormalizedFishBox,
    contour: List<RecognitionContourSegment>,
): Path {
    val crop = subjectBox.normalized()
    return Path().apply {
        contour.forEach { segment ->
            val start = transform.mapNormalized(
                crop.x1 + segment.startX * crop.width,
                crop.y1 + segment.startY * crop.height,
            )
            val end = transform.mapNormalized(
                crop.x1 + segment.endX * crop.width,
                crop.y1 + segment.endY * crop.height,
            )
            if (segment.beginsContour) moveTo(start.x, start.y) else lineTo(start.x, start.y)
            val control1 = transform.mapNormalized(
                crop.x1 + segment.control1X * crop.width,
                crop.y1 + segment.control1Y * crop.height,
            )
            val control2 = transform.mapNormalized(
                crop.x1 + segment.control2X * crop.width,
                crop.y1 + segment.control2Y * crop.height,
            )
            cubicTo(control1.x, control1.y, control2.x, control2.y, end.x, end.y)
            if (segment.closesContour) close()
        }
    }
}

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
    processingElapsedMs: Long = phaseElapsedMs,
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

    val cachedContourPath = remember(transform, subjectBox, contour) {
        if (subjectBox != null && contour.isNotEmpty()) {
            buildRecognitionContourPath(transform, subjectBox, contour)
        } else {
            null
        }
    }
    val contourBlurPaints = remember { RecognitionBlurPaintCache() }

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
        val visualRadiusX = radii.radiusX
        val visualRadiusY = radii.radiusY

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

        val goldStrength = recognitionFishGoldStrength(processingElapsedMs, reduceMotion)

        // A real detector box is already enough to acknowledge the fish. Show
        // its local receiving halo on the first OUTLINE frame; contour detail
        // continues to reveal independently as Level A data arrives.
        val haloReveal = if (phase == RecognitionPhase.OUTLINE) reveal.coerceAtLeast(.72f) else 1f
        val haloAlpha = (.08f * goldStrength * haloReveal).coerceIn(0f, .15f)

        val radiusScale =
            if (effectiveLevel == RecognitionFishFocusLevel.C) .88f else 1f
        val radiusX = visualRadiusX * radiusScale
        val radiusY = visualRadiusY * radiusScale

        if (effectiveLevel != RecognitionFishFocusLevel.A) drawOval(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    .64f to Color.Transparent,
                    .84f to Color(0x00DBA34D),
                    .94f to Color(0xFFDBA34D).copy(alpha = haloAlpha),
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

        if (
            effectiveLevel == RecognitionFishFocusLevel.A &&
            levelAAvailable
        ) {
            val realContour = requireNotNull(cachedContourPath)
            val pathBounds = realContour.getBounds()
            val fishAxis = maxOf(pathBounds.width, pathBounds.height).coerceAtLeast(1f)
            val coreWidth = (fishAxis * .008f).coerceIn(1f, 2.5f)
            val softWidth = (fishAxis * .025f).coerceIn(4f, 10f)
            val haloWidth = (fishAxis * .05f).coerceIn(12f, 28f)
            val t = processingElapsedMs.coerceAtLeast(0L)
            val growthProgress = when {
                reduceMotion -> 1f
                t <= 1_200L -> 0f
                t >= 1_550L -> 1f
                else -> smoothFraction((t - 1_200L).toFloat() / 350f)
            }
            val pathToDraw = if (growthProgress >= .999f) realContour else if (growthProgress <= .001f) null else {
                val androidPath = AndroidPath()
                val measure = AndroidPathMeasure(realContour.asAndroidPath(), false)
                measure.getSegment(0f, measure.length * growthProgress, androidPath, true)
                androidPath.asComposePath()
            }
            val coreAlpha = (.58f * goldStrength).coerceIn(0f, .70f)
            val softGlowAlpha = (.25f * goldStrength).coerceIn(0f, .32f)
            val outerHaloAlpha = (.105f * goldStrength).coerceIn(0f, .15f)

            if (pathToDraw != null) {
                // Gold Halo, width scales with the real Mask contour bounds.
                if (outerHaloAlpha > 0.005f) {
                    drawLocalContourBlur(
                        pathToDraw,
                        Color(0xFFDBA34D).copy(alpha = outerHaloAlpha),
                        widthPx = haloWidth,
                        blurRadiusPx = haloWidth,
                        blurPaints = contourBlurPaints,
                    )
                }

                // Gold Soft Glow.
                if (softGlowAlpha > 0.005f) {
                    drawLocalContourBlur(
                        pathToDraw,
                        Color(0xFFF6C771).copy(alpha = softGlowAlpha),
                        widthPx = softWidth,
                        blurRadiusPx = softWidth,
                        blurPaints = contourBlurPaints,
                    )
                }

                // Gold Core.
                if (coreAlpha > 0.005f) {
                    drawPath(
                        pathToDraw,
                        Color(0xFFFFE3A6).copy(alpha = coreAlpha),
                        style = Stroke(
                            width = coreWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawLocalContourBlur(
    path: Path,
    color: Color,
    widthPx: Float,
    blurRadiusPx: Float,
    blurPaints: RecognitionBlurPaintCache,
) {
    val paint = blurPaints.obtain(color, widthPx, blurRadiusPx) ?: return
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
    }
}
