package com.yujian.ai.ui.recognition

import android.graphics.Bitmap
import android.graphics.Path as AndroidPath
import android.graphics.PathMeasure as AndroidPathMeasure
import android.os.SystemClock
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
        val baseRadiusX = mappedRect.width * if (effectiveLevel == RecognitionFishFocusLevel.B) .54f else .62f
        val baseRadiusY = mappedRect.height * if (effectiveLevel == RecognitionFishFocusLevel.B) .60f else .72f
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

        val wave =
            if (
                phase == RecognitionPhase.CLASSIFYING &&
                !reduceMotion
            ) {
                val t =
                    ((visualClockMs ?: SystemClock.uptimeMillis()) % 1_900L) /
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
        val haloAlpha = haloTarget * haloReveal

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
                    ) * haloReveal,
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
            val realContour = requireNotNull(cachedContourPath)
            val t = processingElapsedMs.coerceAtLeast(0L)

            // 1. 核心描边路径生长进度 (0–1000ms smoothstep)
            val growthProgress = when {
                reduceMotion -> 1.0f
                t <= 0L -> 0.0f
                t >= 1000L -> 1.0f
                else -> {
                    val p = t.toFloat() / 1000f
                    3f * p * p - 2f * p * p * p
                }
            }

            // 2. 识别确认高光 (Confirmation Highlight: 1600ms–1850ms, 1680ms 达峰值)
            val confirmationFactor = when {
                reduceMotion || t < 1600L || t > 1850L -> 0f
                t <= 1680L -> {
                    val k = (t - 1600L).toFloat() / 80f
                    k * k * (3f - 2f * k)
                }
                t <= 1760L -> {
                    val k = (t - 1680L).toFloat() / 80f
                    val s = k * k * (3f - 2f * k)
                    1.0f - 0.5f * s
                }
                else -> {
                    val k = (t - 1760L).toFloat() / 90f
                    val s = k * k * (3f - 2f * k)
                    0.5f * (1.0f - s)
                }
            }

            // 3. 基础柔光与 Halo 平滑淡入 (1150–1400ms)
            // 阶段严格互斥：0-1150ms 柔光与反光彻底关闭
            val fadeFactor = when {
                reduceMotion -> 1.0f
                t < 1150L -> 0.0f
                t >= 1400L -> 1.0f
                else -> {
                    val u = (t - 1150L).toFloat() / 250f
                    u * u * (3f - 2f * u)
                }
            }

            // 4. 柔光呼吸微调制 (1400ms 起，维持柔美流动，振幅≤±3%)
            val loopModulation = if (!reduceMotion && t >= 1400L) {
                sin(((t - 1400L) % 2200L).toFloat() / 2200f * 2f * PI.toFloat()) * 0.03f
            } else {
                0.0f
            }

            // 计算各光学层 Alpha (金色轮廓 V1.1 参数)
            // 核心描边 Core: 线宽 1.2dp, #F5DFB0, 稳定Alpha 0.75, 1.15x boost at 1680ms
            val coreAlpha = (0.75f * (1.0f + 0.15f * confirmationFactor) * (if (phase == RecognitionPhase.OUTLINE) reveal else 1.0f)).coerceIn(0f, 1f)

            // 外围双层柔光 Soft Glow: 5dp, #E8C882, 稳定Alpha 0.24, 1.25x boost at 1680ms
            val softGlowAlpha = if (fadeFactor <= 0f) 0f else {
                (0.24f * (1.0f + loopModulation) * (1.0f + 0.25f * confirmationFactor) * fadeFactor).coerceIn(0f, 0.95f)
            }

            // 外发散光晕 Halo: 10dp, #C9A66B, 稳定Alpha 0.08, 1.15x boost at 1680ms
            val outerHaloAlpha = if (fadeFactor <= 0f) 0f else {
                (0.08f * (1.0f + loopModulation) * (1.0f + 0.15f * confirmationFactor) * fadeFactor).coerceIn(0f, 0.60f)
            }

            // 鱼体内部局部反光 Internal Glint: #F0DCA5, 峰值Alpha 0.12
            val internalGlintAlpha = if (fadeFactor <= 0f) 0f else {
                (0.12f * (1.0f + 0.25f * confirmationFactor) * fadeFactor).coerceIn(0f, 0.25f)
            }

            // 内部局部反光 (剪裁至轮廓内)
            if (internalGlintAlpha > 0.005f) {
                clipPath(realContour) {
                    drawRect(
                        color = Color(0xFFF0DCA5).copy(alpha = internalGlintAlpha),
                        topLeft = Offset(mappedRect.left, mappedRect.top),
                        size = Size(mappedRect.width, mappedRect.height),
                    )
                }
            }

            // 获取生长路径
            val pathToDraw = if (growthProgress >= 0.999f || reduceMotion) {
                realContour
            } else if (growthProgress <= 0.001f) {
                null
            } else {
                val androidPath = AndroidPath()
                val measure = AndroidPathMeasure(realContour.asAndroidPath(), false)
                measure.getSegment(0f, measure.length * growthProgress, androidPath, true)
                androidPath.asComposePath()
            }

            if (pathToDraw != null) {
                // 外发散光晕 Halo (10dp, #C9A66B)
                if (outerHaloAlpha > 0.005f) {
                    drawLocalContourBlur(
                        pathToDraw,
                        Color(0xFFC9A66B).copy(alpha = outerHaloAlpha),
                        widthPx = 10.dp.toPx(),
                        blurRadiusPx = 10.dp.toPx(),
                        blurPaints = contourBlurPaints,
                    )
                }

                // 外围柔光 Soft Glow (5dp, #E8C882)
                if (softGlowAlpha > 0.005f) {
                    drawLocalContourBlur(
                        pathToDraw,
                        Color(0xFFE8C882).copy(alpha = softGlowAlpha),
                        widthPx = 5.dp.toPx(),
                        blurRadiusPx = 5.dp.toPx(),
                        blurPaints = contourBlurPaints,
                    )
                }

                // 核心描边 Core (1.2dp, #F5DFB0)
                if (coreAlpha > 0.005f) {
                    drawPath(
                        pathToDraw,
                        Color(0xFFF5DFB0).copy(alpha = coreAlpha),
                        style = Stroke(
                            width = 1.2.dp.toPx(),
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
