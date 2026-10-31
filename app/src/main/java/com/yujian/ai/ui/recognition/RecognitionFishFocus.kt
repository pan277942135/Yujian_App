package com.yujian.ai.ui.recognition

import android.graphics.Bitmap
import android.graphics.Path as AndroidPath
import android.graphics.PathMeasure as AndroidPathMeasure
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ui.identify.RecognitionImageTransform
import com.yujian.ai.ui.waterlight.core.WaterLightFrame

internal fun recognitionMaskContourEnabled(
    phase: RecognitionPhase,
    maskTrusted: Boolean,
): Boolean = maskTrusted &&
    (phase == RecognitionPhase.OUTLINE || phase == RecognitionPhase.CLASSIFYING)

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
    val keys = longArrayOf(0L, 350L, 950L, 1_200L, 1_550L, 2_050L, 2_600L, 2_800L)
    val values = floatArrayOf(0f, 0f, .15f, .45f, 1f, 1f, 1f, 1f)
    if (t <= keys.first()) return values.first()
    val upper = keys.indexOfFirst { it >= t }.let { if (it < 0) keys.lastIndex else it }
    if (upper == 0) return values[0]
    val lower = upper - 1
    val p = smoothFraction((t - keys[lower]).toFloat() / (keys[upper] - keys[lower]).toFloat())
    return values[lower] + (values[upper] - values[lower]) * p
}

internal const val RECOGNITION_MASK_CONTOUR_REVEAL_MS = 750L

internal fun recognitionMaskAcceptanceOpen(phase: RecognitionPhase): Boolean = when (phase) {
    RecognitionPhase.CAPTURED,
    RecognitionPhase.DETECTING,
    RecognitionPhase.OUTLINE,
    RecognitionPhase.CLASSIFYING -> true
    RecognitionPhase.RESULT,
    RecognitionPhase.FAILURE -> false
}

internal fun recognitionMaskContourRevealProgress(
    processingElapsedMs: Long,
    revealStartedAtMs: Long?,
    reduceMotion: Boolean = false,
): Float {
    if (reduceMotion) return 1f
    val start = revealStartedAtMs ?: return 0f
    val elapsed = (processingElapsedMs - start).coerceAtLeast(0L)
    return smoothFraction(elapsed.toFloat() / RECOGNITION_MASK_CONTOUR_REVEAL_MS)
}

internal const val FISH_FOCUS_A_PROMOTION_DELAY_MS = 420L

/** Reveal every closed island in the trusted mask, including fins separated by the raster grid. */
internal fun revealedRecognitionContour(path: AndroidPath, progress: Float): AndroidPath {
    val out = AndroidPath()
    val fraction = progress.coerceIn(0f, 1f)
    if (fraction <= 0f) return out
    val measure = AndroidPathMeasure(path, false)
    var total = 0f
    do { total += measure.length } while (measure.nextContour())
    var remaining = total * fraction
    measure.setPath(path, false)
    do {
        val length = measure.length
        if (remaining > 0f && length > 0f) measure.getSegment(0f, remaining.coerceAtMost(length), out, true)
        remaining -= length
    } while (remaining > 0f && measure.nextContour())
    return out
}

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

/** Draws the fish layer only from the current trusted SlimSAM contour. */
@Composable
fun RecognitionFishFocus(
    phase: RecognitionPhase,
    focusBox: NormalizedFishBox?,
    subjectBitmap: Bitmap?,
    subjectBox: NormalizedFishBox?,
    contour: List<RecognitionContourSegment>,
    transform: RecognitionImageTransform,
    modifier: Modifier = Modifier,
    processingElapsedMs: Long = 0L,
    opticalReferenceElapsedMs: Long = processingElapsedMs,
    maskRevealProgress: Float = 0f,
    reduceMotion: Boolean = false,
    lowPerformance: Boolean = false,
    trustedMask: Boolean = false,
    waterLightFrame: WaterLightFrame? = null,
    contourPhotoSpace: Boolean = false,
) {
    if (waterLightFrame != null && waterLightFrame.phase != phase) return
    if (!recognitionMaskContourEnabled(phase, trustedMask) || focusBox == null) return
    if (subjectBitmap == null || subjectBox == null || contour.isEmpty()) return

    val cachedContourPath = remember(transform, subjectBox, contour, contourPhotoSpace) {
        if (subjectBox != null && contour.isNotEmpty()) {
            buildRecognitionContourPath(transform,
                if (contourPhotoSpace) NormalizedFishBox(0f, 0f, 1f, 1f) else subjectBox,
                contour)
        } else {
            null
        }
    }
    val contourBlurPaints = remember { RecognitionBlurPaintCache() }
    val subjectImage = remember(subjectBitmap) { subjectBitmap.asImageBitmap() }
    val warmGoldMaskFilter = remember {
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    1.06f, 0f, 0f, 0f, 0f,
                    0f, 1.02f, 0f, 0f, 0f,
                    0f, 0f, .90f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }

    Canvas(
        modifier
            .fillMaxSize()
            .testTag(if (lowPerformance) "recognition-fish-focus-trusted-mask-low-performance" else "recognition-fish-focus-trusted-mask"),
    ) {
        // The contour path already reveals spatially through maskRevealProgress.
        // Multiplying the optical intensity by that progress a second time made
        // the trusted mask nearly invisible during the reveal.
        val goldStrength = recognitionFishGoldStrength(opticalReferenceElapsedMs, reduceMotion)

        val realContour = requireNotNull(cachedContourPath)
            val pathBounds = realContour.getBounds()
            val fishAxis = maxOf(pathBounds.width, pathBounds.height).coerceAtLeast(1f)
            val coreWidth = (fishAxis * .008f).coerceIn(1f, 2.5f)
            val softWidth = (fishAxis * .025f).coerceIn(4f, 10f)
            val haloWidth = (fishAxis * .05f).coerceIn(12f, 28f)
            val growthProgress = maskRevealProgress.coerceIn(0f, 1f)
            val pathToDraw = if (growthProgress >= .999f) realContour else if (growthProgress <= .001f) null else {
                revealedRecognitionContour(realContour.asAndroidPath(), growthProgress).asComposePath()
            }
            val coreAlpha = (.58f * goldStrength).coerceIn(0f, .70f)
            val softGlowAlpha = (.25f * goldStrength).coerceIn(0f, .32f)
            val outerHaloAlpha = (.105f * goldStrength).coerceIn(0f, .15f)

            // Re-sample the original-photo pixels through SlimSAM's alpha mask. The
            // low-opacity channel warmth keeps scales, eye and fin texture intact.
            val roi = subjectBox.normalized()
            val roiTopLeft = transform.mapNormalized(roi.x1, roi.y1)
            val roiBottomRight = transform.mapNormalized(roi.x2, roi.y2)
            val roiOffset = IntOffset(roiTopLeft.x.toInt(), roiTopLeft.y.toInt())
            val roiSize = IntSize(
                (roiBottomRight.x - roiTopLeft.x).toInt().coerceAtLeast(1),
                (roiBottomRight.y - roiTopLeft.y).toInt().coerceAtLeast(1),
            )
            if (goldStrength > 0f) {
                drawImage(
                    image = subjectImage,
                    dstOffset = roiOffset,
                    dstSize = roiSize,
                    alpha = (.14f * goldStrength).coerceIn(0f, .14f),
                    colorFilter = warmGoldMaskFilter,
                )
            }

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
