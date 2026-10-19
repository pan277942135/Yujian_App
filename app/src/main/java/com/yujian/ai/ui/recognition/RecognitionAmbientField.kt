package com.yujian.ai.ui.recognition

import android.graphics.Bitmap
import android.graphics.BlendMode as AndroidBlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.PathMeasure as AndroidPathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ui.identify.DisplayRect
import com.yujian.ai.ui.identify.RecognitionImageTransform
import kotlin.math.PI
import kotlin.math.sin

private val AiBlueCore = Color(0xFFBFEAF2)
private val AiBlueHot = Color(0xFFD8F5FA)
private val AiGoldCore = Color(0xFFF6D99B)
private val AiGoldHot = Color(0xFFFFE7AE)
private val MotionEase = CubicBezierEasing(.22f, 1f, .36f, 1f)

internal val recognitionAmbientPrimaryIslandIds = setOf("B1", "G1", "B3", "G3")
internal val recognitionAmbientSecondaryFragmentIds = setOf("B2", "G2", "B4")
private val ENERGY_NODES = listOf("B1" to AiBlueHot, "G1" to AiGoldHot)

private data class Cubic(
    val x: Float,
    val y: Float,
)

private data class Filament(
    val id: String,
    val color: Color,
    val hot: Color,
    val width: Dp,
    val seed: Float,
    val visibleRatio: Float,
    val islandWeight: Float,
    val start: Cubic,
    val control1: Cubic,
    val control2: Cubic,
    val end: Cubic,
)

private data class EchoFilament(
    val id: String,
    val parentId: String,
    val color: Color,
    val hot: Color,
    val width: Dp,
    val phaseDelta: Float,
    val visibleRatio: Float,
    val start: Cubic,
    val control1: Cubic,
    val control2: Cubic,
    val end: Cubic,
)

private data class QualityProfile(
    val receiving: Float,
    val outer: Float,
    val mid: Float,
    val core: Float,
    val companionIds: Set<String>,
    val microEnabled: Boolean,
    val nodeCount: Int,
    val particleCount: Int,
    val alphaMultiplier: Float,
)

private data class ParticleAnchor(
    val islandId: String,
    val point: Cubic,
)

private enum class ProductStage {
    IMAGE_RECOGNIZING,
    FISH_LOCATED,
    SPECIES_RECOGNIZING,
    TERMINAL,
}

private data class MotionTarget(
    val strength: Float,
    val speed: Float,
)

internal data class RecognitionAmbientStateCalibration(
    val strength: Float,
    val speed: Float,
)

internal data class MotionFrame(
    val cycleOffset: Float,
    val segmentSpeed: Float,
    val stateStrength: Float,
    val detailMotionTimeMs: Long,
    val receivingEntry: Float,
    val primaryEntry: Float,
    val detailEntry: Float,
)

data class RecognitionMotionTraceSample(
    val uptimeMs: Long,
    val phase: RecognitionPhase,
    val segmentOffset: Float,
    val segmentSpeed: Float,
    val stateStrength: Float,
    val detailMotionTimeMs: Long,
    val reduceMotion: Boolean,
    val qualityLevel: RecognitionQualityLevel,
)

internal class EdgeFieldMotionAccumulator {
    private var initialized = false
    private var stage = ProductStage.IMAGE_RECOGNIZING
    private var lastNowMs = 0L
    private var entryStartedAtMs = 0L
    private var cycleOffset = 0f
    private var reduceMotion = false

    private var speedFrom = 1f
    private var speedTo = 1f
    private var speedTransitionAtMs = 0L
    private var speedTransitionMs = 0L

    private var strengthFrom = 0f
    private var strengthTo = 1f
    private var strengthTransitionAtMs = 0L
    private var strengthTransitionMs = 420L

    fun sample(
        nowMs: Long,
        phase: RecognitionPhase,
        reduceMotionNow: Boolean,
    ): MotionFrame {
        val nextStage = productStage(phase)

        if (!initialized) {
            initialized = true
            stage = nextStage
            lastNowMs = nowMs
            entryStartedAtMs = nowMs
            reduceMotion = reduceMotionNow

            val target = targetFor(stage)
            speedFrom = if (reduceMotionNow) 0f else target.speed
            speedTo = speedFrom
            speedTransitionAtMs = nowMs
            speedTransitionMs = 0L

            strengthFrom = if (stage == ProductStage.IMAGE_RECOGNIZING) 0f else target.strength
            strengthTo = target.strength
            strengthTransitionAtMs = nowMs
            strengthTransitionMs = if (stage == ProductStage.IMAGE_RECOGNIZING) {
                if (reduceMotionNow) 180L else 420L
            } else {
                0L
            }
        }

        advance(nowMs)

        if (nextStage != stage) {
            val currentSpeed = speedAt(nowMs)
            val currentStrength = strengthAt(nowMs)
            stage = nextStage
            val target = targetFor(stage)

            speedFrom = currentSpeed
            speedTo = if (reduceMotionNow) 0f else target.speed
            speedTransitionAtMs = nowMs
            speedTransitionMs = if (reduceMotionNow) 0L else transitionDurationFor(stage)

            strengthFrom = currentStrength
            strengthTo = target.strength
            strengthTransitionAtMs = nowMs
            strengthTransitionMs = if (reduceMotionNow) 180L else transitionDurationFor(stage)
        }

        if (reduceMotionNow != reduceMotion) {
            val currentSpeed = speedAt(nowMs)
            reduceMotion = reduceMotionNow
            speedFrom = currentSpeed
            speedTransitionAtMs = nowMs
            if (reduceMotionNow) {
                speedTo = 0f
                speedTransitionMs = 0L
            } else {
                speedTo = targetFor(stage).speed
                speedTransitionMs = 220L
            }
        }

        val elapsed = (nowMs - entryStartedAtMs).coerceAtLeast(0L)
        val receivingEntry = if (reduceMotionNow) {
            easedFraction(elapsed, 180L)
        } else {
            easedWindow(elapsed, 0L, 180L)
        }
        val primaryEntry = if (reduceMotionNow) {
            receivingEntry
        } else {
            easedWindow(elapsed, 80L, 320L)
        }
        val detailEntry = if (reduceMotionNow) {
            receivingEntry
        } else {
            easedWindow(elapsed, 180L, 420L)
        }

        return MotionFrame(
            cycleOffset = cycleOffset,
            segmentSpeed = speedAt(nowMs),
            stateStrength = strengthAt(nowMs),
            detailMotionTimeMs = nowMs,
            receivingEntry = receivingEntry,
            primaryEntry = primaryEntry,
            detailEntry = detailEntry,
        )
    }

    private fun advance(nowMs: Long) {
        if (!initialized || nowMs <= lastNowMs) return
        if (!reduceMotion) {
            val before = speedAt(lastNowMs)
            val after = speedAt(nowMs)
            val average = (before + after) * .5f
            val delta = nowMs - lastNowMs
            cycleOffset = positiveMod(cycleOffset + average * delta / 10_000f)
        }
        lastNowMs = nowMs
    }

    private fun speedAt(nowMs: Long): Float =
        interpolate(
            speedFrom,
            speedTo,
            nowMs,
            speedTransitionAtMs,
            speedTransitionMs,
        )

    private fun strengthAt(nowMs: Long): Float =
        interpolate(
            strengthFrom,
            strengthTo,
            nowMs,
            strengthTransitionAtMs,
            strengthTransitionMs,
        )
}

@Composable
fun RecognitionAmbientField(
    phase: RecognitionPhase,
    modifier: Modifier = Modifier,
    visualClockMs: Long? = null,
    motionSessionKey: Any? = null,
    deterministicMotion: Boolean = visualClockMs != null,
    lowPerformance: Boolean = false,
    reduceMotion: Boolean = false,
    qualityLevel: RecognitionQualityLevel =
        if (lowPerformance) RecognitionQualityLevel.LITE else RecognitionQualityLevel.FULL,
    onMotionFrame: ((RecognitionMotionTraceSample) -> Unit)? = null,
    photoTransform: RecognitionImageTransform? = null,
    sourcePhoto: Bitmap? = null,
) {
    val nowMs = visualClockMs ?: 0L
    val accumulator = remember(motionSessionKey) { EdgeFieldMotionAccumulator() }
    val cachedGeometry = remember(photoTransform?.photoContentRect) {
        photoTransform?.photoContentRect?.let(::buildOpticalPathCache)
    }
    val localLuminance = remember(sourcePhoto) {
        sourcePhoto?.let(::samplePerimeterLuminance).orEmpty()
    }

    val frame = if (deterministicMotion) {
        deterministicFrame(
            nowMs,
            phase,
            reduceMotion,
        )
    } else {
        accumulator.sample(nowMs, phase, reduceMotion)
    }

    val quality = remember(qualityLevel) { qualityProfile(qualityLevel) }
    if (onMotionFrame != null) {
        SideEffect {
            onMotionFrame(
                RecognitionMotionTraceSample(
                    uptimeMs = nowMs,
                    phase = phase,
                    segmentOffset = frame.cycleOffset,
                    segmentSpeed = frame.segmentSpeed,
                    stateStrength = frame.stateStrength,
                    detailMotionTimeMs = frame.detailMotionTimeMs,
                    reduceMotion = reduceMotion,
                    qualityLevel = qualityLevel,
                ),
            )
        }
    }
    val evidenceTag =
        "recognition-ambient-" +
            (if (reduceMotion) "reduced-motion" else "motion") + "-" +
            (if (lowPerformance) "low-performance" else qualityLevel.name.lowercase())

    Canvas(
        modifier
            .fillMaxSize()
            .testTag(evidenceTag),
    ) {
        val fieldRect = photoTransform?.photoContentRect
            ?: DisplayRect(0f, 0f, size.width, size.height)
        val geometry = cachedGeometry?.takeIf { it.fieldRect == fieldRect }
            ?: buildOpticalPathCache(fieldRect)
        val primaryPaths = geometry.primaryPaths
        val companionPaths = geometry.companionPaths
        val microPaths = geometry.microPaths

        clipRect(fieldRect.left, fieldRect.top, fieldRect.right, fieldRect.bottom) {
            drawReceivingLight(
                frame = frame,
                quality = quality,
                qualityLevel = qualityLevel,
                fieldRect = fieldRect,
                primaryPaths = primaryPaths,
                localLuminance = localLuminance,
                blurPaints = geometry.blurPaints,
            )

            PRIMARY_ISLANDS.forEachIndexed { index, filament ->
                val path = primaryPaths[index]
                val offset = positiveMod(frame.cycleOffset + filament.seed)
                val fieldWeight = if (filament.id in recognitionAmbientPrimaryIslandIds) {
                    1f
                } else {
                    filament.islandWeight
                }
                val common =
                    frame.stateStrength *
                        frame.primaryEntry *
                        quality.alphaMultiplier *
                        fieldWeight

                if (filament.id in recognitionAmbientPrimaryIslandIds) {
                    drawCompositePrimary(
                        path = path,
                        filament = filament,
                        offset = offset,
                        common = common,
                        quality = quality,
                        qualityLevel = qualityLevel,
                        blurPaints = geometry.blurPaints,
                        fieldRect = fieldRect,
                        localLuminance = localLuminance,
                    )
                } else if (filament.id in recognitionAmbientSecondaryFragmentIds) {
                    drawSecondaryFragment(
                        path = path,
                        filament = filament,
                        offset = offset,
                        common = common,
                        quality = quality,
                        blurPaints = geometry.blurPaints,
                    )
                }
            }

            if (quality.companionIds.isNotEmpty()) {
                COMPANION_HAIRLINES.forEachIndexed { index, echo ->
                    if (echo.id !in quality.companionIds) return@forEachIndexed
                    val parent = PRIMARY_ISLANDS.first { it.id == echo.parentId }
                    val common =
                        frame.stateStrength *
                            frame.detailEntry *
                            quality.alphaMultiplier *
                            parent.islandWeight
                    drawHairlineEcho(
                        path = companionPaths[index],
                        echo = echo,
                        offset = positiveMod(frame.cycleOffset + parent.seed + echo.phaseDelta),
                        common = common,
                        micro = false,
                        blurPaints = geometry.blurPaints,
                    )
                }
            }

            if (quality.microEnabled) {
                MICRO_HAIRLINES.forEachIndexed { index, echo ->
                    val parent = PRIMARY_ISLANDS.first { it.id == echo.parentId }
                    val common =
                        frame.stateStrength *
                            frame.detailEntry *
                            quality.alphaMultiplier *
                            parent.islandWeight
                    drawHairlineEcho(
                        path = microPaths[index],
                        echo = echo,
                        offset = positiveMod(frame.cycleOffset + parent.seed + echo.phaseDelta),
                        common = common,
                        micro = true,
                        blurPaints = geometry.blurPaints,
                    )
                }
            }

            if (quality.nodeCount > 0) {
                drawEnergyNodes(
                    nowMs = frame.detailMotionTimeMs,
                    frame = frame,
                    quality = quality,
                    reduceMotion = reduceMotion,
                    fieldRect = fieldRect,
                )
            }

            if (quality.particleCount > 0 && !reduceMotion) {
                drawParticles(
                    nowMs = frame.detailMotionTimeMs,
                    count = quality.particleCount,
                    frame = frame,
                    quality = quality,
                    fieldRect = fieldRect,
                )
            }
        }
    }
}

private fun deterministicFrame(
    clockMs: Long,
    phase: RecognitionPhase,
    reduceMotion: Boolean,
): MotionFrame {
    val stage = productStage(phase)
    val target = targetFor(stage)
    val offset = if (reduceMotion) {
        when (stage) {
            ProductStage.IMAGE_RECOGNIZING -> 0f
            ProductStage.FISH_LOCATED -> .23f
            ProductStage.SPECIES_RECOGNIZING -> .47f
            ProductStage.TERMINAL -> 0f
        }
    } else {
        positiveMod(clockMs / 10_000f * target.speed)
    }

    return MotionFrame(
        cycleOffset = offset,
        segmentSpeed = if (reduceMotion) 0f else target.speed,
        stateStrength = target.strength,
        detailMotionTimeMs = clockMs,
        receivingEntry = 1f,
        primaryEntry = 1f,
        detailEntry = if (reduceMotion) 1f else 1f,
    )
}

private fun qualityProfile(level: RecognitionQualityLevel): QualityProfile =
    when (level) {
        RecognitionQualityLevel.FULL -> QualityProfile(
            receiving = 1f,
            outer = 1f,
            mid = 1f,
            core = 1f,
            companionIds = setOf("B1C", "G1C", "G3C", "B3C"),
            microEnabled = true,
            nodeCount = 2,
            particleCount = 8,
            alphaMultiplier = 1f,
        )
        RecognitionQualityLevel.BALANCED -> QualityProfile(
            receiving = .85f,
            outer = .75f,
            mid = .90f,
            core = 1f,
            companionIds = setOf("B1C", "G1C"),
            microEnabled = false,
            nodeCount = 1,
            particleCount = 4,
            alphaMultiplier = .92f,
        )
        RecognitionQualityLevel.LITE -> QualityProfile(
            receiving = .70f,
            outer = .50f,
            mid = .80f,
            core = 1f,
            companionIds = emptySet(),
            microEnabled = false,
            nodeCount = 0,
            particleCount = 0,
            alphaMultiplier = .82f,
        )
    }

/** Samples only the four photo-edge environments; it never adjusts the source image. */
private fun samplePerimeterLuminance(bitmap: Bitmap): Map<String, Float> {
    val normalizedPoints = mapOf(
        "G1" to (.10f to .10f),
        "B1" to (.92f to .12f),
        "G3" to (.12f to .90f),
        "B3" to (.88f to .88f),
    )
    return normalizedPoints.mapValues { (_, point) ->
        val centerX = (point.first * (bitmap.width - 1)).toInt().coerceIn(0, bitmap.width - 1)
        val centerY = (point.second * (bitmap.height - 1)).toInt().coerceIn(0, bitmap.height - 1)
        var total = 0f
        var samples = 0
        for (dy in -2..2) for (dx in -2..2) {
            val x = (centerX + dx).coerceIn(0, bitmap.width - 1)
            val y = (centerY + dy).coerceIn(0, bitmap.height - 1)
            val pixel = bitmap.getPixel(x, y)
            val red = android.graphics.Color.red(pixel) / 255f
            val green = android.graphics.Color.green(pixel) / 255f
            val blue = android.graphics.Color.blue(pixel) / 255f
            total += .2126f * red + .7152f * green + .0722f * blue
            samples++
        }
        total / samples
    }
}

private fun DrawScope.drawReceivingLight(
    frame: MotionFrame,
    quality: QualityProfile,
    qualityLevel: RecognitionQualityLevel,
    fieldRect: DisplayRect,
    primaryPaths: List<RecognitionMeasuredPath>,
    localLuminance: Map<String, Float>,
    blurPaints: RecognitionBlurPaintCache,
) {
    if (frame.stateStrength <= .001f) return
    val dimensions = RecognitionOpticalConfigV2.layerDimensions(
        displayedPhotoWidthPx = fieldRect.width,
        quality = qualityLevel,
    )
    PRIMARY_ISLANDS.forEachIndexed { index, island ->
        if (island.id !in recognitionAmbientPrimaryIslandIds) return@forEachIndexed
        val measuredPath = primaryPaths[index]
        val compensation = RecognitionOpticalConfigV2.localBackgroundGain(localLuminance[island.id] ?: .5f)
        val common = frame.stateStrength * frame.receivingEntry * quality.receiving *
            quality.alphaMultiplier * RecognitionOpticalConfigV2.islandStrength(island.id) * compensation
        if (common <= .001f) return@forEachIndexed
        drawLocalBlur(
            path = measuredPath.path,
            color = island.color.copy(alpha = RecognitionOpticalConfigV2.ATMOSPHERIC_SPILL_INTENSITY * common),
            strokeWidthPx = dimensions.spillRadiusPx * 1.7f,
            blurRadiusPx = dimensions.spillRadiusPx * .56f,
            blurPaints = blurPaints,
            blend = RecognitionOpticalBlend.SOFT_LIGHT,
        )
    }
}

private fun DrawScope.drawCompositePrimary(
    path: RecognitionMeasuredPath,
    filament: Filament,
    offset: Float,
    common: Float,
    quality: QualityProfile,
    qualityLevel: RecognitionQualityLevel,
    blurPaints: RecognitionBlurPaintCache,
    fieldRect: DisplayRect,
    localLuminance: Map<String, Float>,
) {
    if (common <= .001f) return

    val segments = extractVisiblePathSegments(path, offset, filament.visibleRatio)
    val compensation = RecognitionOpticalConfigV2.localBackgroundGain(localLuminance[filament.id] ?: .5f)
    val islandCommon = common * RecognitionOpticalConfigV2.islandStrength(filament.id) * compensation
    val dimensions = RecognitionOpticalConfigV2.layerDimensions(
        displayedPhotoWidthPx = fieldRect.width,
        quality = qualityLevel,
    )
    val outerRadius = dimensions.outerRadiusPx
    val innerRadius = dimensions.innerRadiusPx * quality.mid
    val outer = filament.color.copy(
        alpha = RecognitionOpticalConfigV2.OUTER_BLOOM_INTENSITY *
            RecognitionOpticalConfigV2.OUTER_BLOOM_ALPHA_MODIFIER * islandCommon * quality.outer,
    )
    val inner = filament.color.copy(
        alpha = RecognitionOpticalConfigV2.INNER_BLOOM_INTENSITY *
            RecognitionOpticalConfigV2.INNER_BLOOM_ALPHA_MODIFIER * islandCommon * quality.mid,
    )
    val coreAlpha = RecognitionOpticalConfigV2.CORE_INTENSITY * islandCommon * quality.core
    val coreWidth = dimensions.coreWidthPx

    segments.forEach { segment ->
        drawLocalBlur(
            segment.path,
            outer,
            strokeWidthPx = outerRadius * 1.8f + coreWidth,
            blurRadiusPx = outerRadius * .46f,
            blurPaints = blurPaints,
            blend = RecognitionOpticalBlend.SCREEN,
        )
        drawLocalBlur(
            segment.path,
            inner,
            strokeWidthPx = innerRadius * 1.65f + coreWidth,
            blurRadiusPx = innerRadius * .42f,
            blurPaints = blurPaints,
            blend = RecognitionOpticalBlend.SCREEN,
        )
        drawPath(
            segment.path,
            brush = gradientTail(segment, filament.color, filament.hot, coreAlpha),
            style = Stroke(
                width = coreWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
            blendMode = BlendMode.SrcOver,
        )
    }
}

private fun DrawScope.drawSecondaryFragment(
    path: RecognitionMeasuredPath,
    filament: Filament,
    offset: Float,
    common: Float,
    quality: QualityProfile,
    blurPaints: RecognitionBlurPaintCache,
) {
    if (common <= .001f) return

    // B2/G2/B4 are fragment accents only. They do not receive the Primary
    // 10dp outer bloom, so they cannot reconstruct a second perimeter.
    val segments = extractVisiblePathSegments(path, offset, filament.visibleRatio)
    val mid = filament.color.copy(
        alpha = (.025f * common * quality.mid).coerceAtMost(.035f),
    )
    val coreAlpha = (.16f * common * quality.core).coerceAtMost(.12f)
    segments.forEach { segment ->
        drawLocalBlur(segment.path, mid, 2.dp.toPx(), 1.dp.toPx(), blurPaints)
        drawPath(
            segment.path,
            brush = gradientTail(segment, filament.color, filament.hot, coreAlpha),
            style = Stroke(
                width = filament.width.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

private fun DrawScope.drawHairlineEcho(
    path: RecognitionMeasuredPath,
    echo: EchoFilament,
    offset: Float,
    common: Float,
    micro: Boolean,
    blurPaints: RecognitionBlurPaintCache,
) {
    if (common <= .001f) return

    val localWeight = if (micro) .18f else .30f
    val glowWidth = if (micro) 1.5.dp else 2.1.dp
    val segments = extractVisiblePathSegments(path, offset, echo.visibleRatio)
    val glow = echo.color.copy(
        alpha = (.18f * common * localWeight).coerceAtMost(.08f),
    )
    val coreAlpha = (.52f * common * localWeight).coerceAtMost(.20f)
    segments.forEach { segment ->
        drawLocalBlur(segment.path, glow, glowWidth.toPx(), (glowWidth / 2f).toPx(), blurPaints)
        drawPath(
            segment.path,
            brush = gradientTail(segment, echo.color, echo.hot, coreAlpha),
            style = Stroke(
                width = echo.width.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

private fun DrawScope.drawEnergyNodes(
    nowMs: Long,
    frame: MotionFrame,
    quality: QualityProfile,
    reduceMotion: Boolean,
    fieldRect: DisplayRect,
) {
    val common =
        frame.stateStrength *
            frame.detailEntry *
            quality.alphaMultiplier

    repeat(quality.nodeCount.coerceAtMost(ENERGY_NODES.size)) { index ->
        val (islandId, color) = ENERGY_NODES[index]
        val island = PRIMARY_ISLANDS.first { it.id == islandId }
        val pulse = if (reduceMotion) {
            1f
        } else {
            val t = ((nowMs % 4_800L) / 4_800f + island.seed) % 1f
            .78f + .22f * sin(t * 2f * PI).toFloat()
        }
        val weight = if (index == 0) 1f else .42f
        val alpha = common * pulse * weight
        val trajectory = if (reduceMotion) {
            .5f
        } else {
            positiveMod(frame.cycleOffset + island.seed + island.visibleRatio * .5f)
        }
        val center = bezierPoint(island, trajectory, fieldRect)

        drawCircle(
            color.copy(alpha = (.07f * alpha).coerceAtMost(.07f)),
            radius = 4.dp.toPx(),
            center = center,
        )
        drawCircle(
            color.copy(alpha = (.36f * alpha).coerceAtMost(.30f)),
            radius = 1.35.dp.toPx(),
            center = center,
        )
    }
}

private fun DrawScope.drawParticles(
    nowMs: Long,
    count: Int,
    frame: MotionFrame,
    quality: QualityProfile,
    fieldRect: DisplayRect,
) {
    val common =
        frame.stateStrength *
            frame.detailEntry *
            quality.alphaMultiplier

    repeat(count.coerceAtMost(PARTICLE_ANCHORS.size)) { index ->
        val anchor = PARTICLE_ANCHORS[index]
        val t = ((nowMs % 5_000L) / 5_000f + index * .137f) % 1f
        val travel = 14f + (index % 4) * 7f
        val x =
            fieldRect.left + anchor.point.x * fieldRect.width +
                sin((t + index) * 2f * PI).toFloat() * travel
        val y =
            fieldRect.top + anchor.point.y * fieldRect.height +
                (t - .5f) * travel

        val goldOwner = anchor.islandId == "G1" || anchor.islandId == "G3"
        val maxAlpha = .18f
        val pulse = sin(t * PI).toFloat().coerceAtLeast(0f)
        if (pulse < .42f) return@repeat

        drawCircle(
            color = (if (goldOwner) AiGoldHot else AiBlueHot).copy(
                alpha = (common * maxAlpha * pulse).coerceAtMost(maxAlpha),
            ),
            radius = 1.5.dp.toPx(),
            center = Offset(x, y),
        )
    }
}

private fun bezierPoint(filament: Filament, t: Float, fieldRect: DisplayRect): Offset {
    val oneMinus = 1f - t
    val x = oneMinus * oneMinus * oneMinus * filament.start.x +
        3f * oneMinus * oneMinus * t * filament.control1.x +
        3f * oneMinus * t * t * filament.control2.x +
        t * t * t * filament.end.x
    val y = oneMinus * oneMinus * oneMinus * filament.start.y +
        3f * oneMinus * oneMinus * t * filament.control1.y +
        3f * oneMinus * t * t * filament.control2.y +
        t * t * t * filament.end.y
    return Offset(fieldRect.left + x * fieldRect.width, fieldRect.top + y * fieldRect.height)
}

internal data class RecognitionVisiblePathSegment(
    val path: Path,
    val start: Offset,
    val end: Offset,
)

/** Static screen-space Beziers and PathMeasures cached for one photo viewport. */
internal class RecognitionMeasuredPath internal constructor(
    val path: Path,
) {
    internal val measure = AndroidPathMeasure(path.asAndroidPath(), false)
}

private data class RecognitionOpticalPathCache(
    val fieldRect: DisplayRect,
    val primaryPaths: List<RecognitionMeasuredPath>,
    val companionPaths: List<RecognitionMeasuredPath>,
    val microPaths: List<RecognitionMeasuredPath>,
    val blurPaints: RecognitionBlurPaintCache,
)

/** Reuses native glow paints while updating only the animated color each frame. */
internal class RecognitionBlurPaintCache {
    private data class Style(val strokeWidthBits: Int, val blurRadiusBits: Int, val blend: RecognitionOpticalBlend)

    private val paints = HashMap<Style, AndroidPaint>()

    fun obtain(
        color: Color,
        strokeWidthPx: Float,
        blurRadiusPx: Float,
        blend: RecognitionOpticalBlend,
    ): AndroidPaint? {
        if (color.alpha <= 0f || strokeWidthPx <= 0f || blurRadiusPx <= 0f) return null
        val style = Style(strokeWidthPx.toBits(), blurRadiusPx.toBits(), blend)
        return paints.getOrPut(style) {
            AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                this.style = AndroidPaint.Style.STROKE
                strokeCap = AndroidPaint.Cap.ROUND
                strokeJoin = AndroidPaint.Join.ROUND
                strokeWidth = strokeWidthPx
                maskFilter = BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL)
                applyOpticalBlend(blend)
            }
        }.apply {
            this.color = color.toArgb()
        }
    }

    @Suppress("NewApi")
    private fun AndroidPaint.applyOpticalBlend(blend: RecognitionOpticalBlend) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            xfermode = null
            blendMode = when (blend) {
                RecognitionOpticalBlend.SOURCE_OVER -> AndroidBlendMode.SRC_OVER
                RecognitionOpticalBlend.SCREEN -> AndroidBlendMode.SCREEN
                RecognitionOpticalBlend.SOFT_LIGHT -> AndroidBlendMode.SOFT_LIGHT
            }
        } else {
            xfermode = when (blend) {
                RecognitionOpticalBlend.SOURCE_OVER -> null
                RecognitionOpticalBlend.SCREEN -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                RecognitionOpticalBlend.SOFT_LIGHT -> PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
            }
        }
    }
}

/** Extract the moving luminous dash from the real measured curve, including wraparound. */
internal fun extractVisiblePathSegments(
    path: Path,
    offset: Float,
    visibleRatio: Float,
): List<RecognitionVisiblePathSegment> =
    extractVisiblePathSegments(RecognitionMeasuredPath(path), offset, visibleRatio)

internal fun extractVisiblePathSegments(
    path: RecognitionMeasuredPath,
    offset: Float,
    visibleRatio: Float,
): List<RecognitionVisiblePathSegment> {
    val measure = path.measure
    val length = measure.length
    if (length <= 1f) return emptyList()

    val visibleLength = (length * visibleRatio.coerceIn(.05f, .60f)).coerceAtLeast(1f)
    val startDistance = positiveMod(offset) * length
    val endDistance = startDistance + visibleLength
    val segments = ArrayList<RecognitionVisiblePathSegment>(2)

    fun append(from: Float, to: Float) {
        if (to - from <= .5f) return
        val androidSegment = AndroidPath()
        if (!measure.getSegment(from, to, androidSegment, true)) return
        val startPosition = FloatArray(2)
        val endPosition = FloatArray(2)
        measure.getPosTan(from, startPosition, null)
        measure.getPosTan(to, endPosition, null)
        val composeSegment = Path().apply { asAndroidPath().set(androidSegment) }
        segments += RecognitionVisiblePathSegment(
            path = composeSegment,
            start = Offset(startPosition[0], startPosition[1]),
            end = Offset(endPosition[0], endPosition[1]),
        )
    }

    if (endDistance <= length) {
        append(startDistance, endDistance)
    } else {
        append(startDistance, length)
        append(0f, endDistance - length)
    }
    return segments
}

private fun DrawScope.drawLocalBlur(
    path: Path,
    color: Color,
    strokeWidthPx: Float,
    blurRadiusPx: Float,
    blurPaints: RecognitionBlurPaintCache,
    blend: RecognitionOpticalBlend = RecognitionOpticalBlend.SCREEN,
) {
    val paint = blurPaints.obtain(color, strokeWidthPx, blurRadiusPx, blend) ?: return
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
    }
}

private fun gradientTail(
    segment: RecognitionVisiblePathSegment,
    color: Color,
    hot: Color,
    alpha: Float,
): Brush = Brush.linearGradient(
    colorStops = arrayOf(
        0f to color.copy(alpha = alpha * .08f),
        .58f to color.copy(alpha = alpha * .58f),
        .88f to hot.copy(alpha = alpha),
        1f to hot.copy(alpha = alpha * .78f),
    ),
    start = segment.start,
    end = segment.end,
)

private fun buildPaths(
    fieldRect: DisplayRect,
    filaments: List<Filament>,
): List<RecognitionMeasuredPath> =
    filaments.map { filament ->
        val path = Path().apply {
            moveTo(
                fieldRect.left + filament.start.x * fieldRect.width,
                fieldRect.top + filament.start.y * fieldRect.height,
            )
            cubicTo(
                fieldRect.left + filament.control1.x * fieldRect.width,
                fieldRect.top + filament.control1.y * fieldRect.height,
                fieldRect.left + filament.control2.x * fieldRect.width,
                fieldRect.top + filament.control2.y * fieldRect.height,
                fieldRect.left + filament.end.x * fieldRect.width,
                fieldRect.top + filament.end.y * fieldRect.height,
            )
        }
        RecognitionMeasuredPath(path)
    }

private fun buildEchoPaths(
    fieldRect: DisplayRect,
    filaments: List<EchoFilament>,
): List<RecognitionMeasuredPath> =
    filaments.map { filament ->
        val path = Path().apply {
            moveTo(
                fieldRect.left + filament.start.x * fieldRect.width,
                fieldRect.top + filament.start.y * fieldRect.height,
            )
            cubicTo(
                fieldRect.left + filament.control1.x * fieldRect.width,
                fieldRect.top + filament.control1.y * fieldRect.height,
                fieldRect.left + filament.control2.x * fieldRect.width,
                fieldRect.top + filament.control2.y * fieldRect.height,
                fieldRect.left + filament.end.x * fieldRect.width,
                fieldRect.top + filament.end.y * fieldRect.height,
            )
        }
        RecognitionMeasuredPath(path)
    }

private fun buildOpticalPathCache(fieldRect: DisplayRect) = RecognitionOpticalPathCache(
    fieldRect = fieldRect,
    primaryPaths = buildPaths(fieldRect, PRIMARY_ISLANDS),
    companionPaths = buildEchoPaths(fieldRect, COMPANION_HAIRLINES),
    microPaths = buildEchoPaths(fieldRect, MICRO_HAIRLINES),
    blurPaints = RecognitionBlurPaintCache(),
)

private fun productStage(phase: RecognitionPhase): ProductStage =
    when (phase) {
        RecognitionPhase.CAPTURED,
        RecognitionPhase.DETECTING -> ProductStage.IMAGE_RECOGNIZING
        RecognitionPhase.OUTLINE -> ProductStage.FISH_LOCATED
        RecognitionPhase.CLASSIFYING -> ProductStage.SPECIES_RECOGNIZING
        RecognitionPhase.RESULT,
        RecognitionPhase.FAILURE -> ProductStage.TERMINAL
    }

internal fun recognitionAmbientStateCalibration(phase: RecognitionPhase): RecognitionAmbientStateCalibration =
    when (phase) {
        RecognitionPhase.CAPTURED,
        RecognitionPhase.DETECTING -> RecognitionAmbientStateCalibration(1f, 1f)
        RecognitionPhase.OUTLINE -> RecognitionAmbientStateCalibration(.58f, .62f)
        RecognitionPhase.CLASSIFYING -> RecognitionAmbientStateCalibration(.36f, .42f)
        RecognitionPhase.RESULT,
        RecognitionPhase.FAILURE -> RecognitionAmbientStateCalibration(0f, 0f)
    }

private fun targetFor(stage: ProductStage): MotionTarget =
    when (stage) {
        ProductStage.IMAGE_RECOGNIZING -> recognitionAmbientStateCalibration(RecognitionPhase.CAPTURED)
        ProductStage.FISH_LOCATED -> recognitionAmbientStateCalibration(RecognitionPhase.OUTLINE)
        ProductStage.SPECIES_RECOGNIZING -> recognitionAmbientStateCalibration(RecognitionPhase.CLASSIFYING)
        ProductStage.TERMINAL -> recognitionAmbientStateCalibration(RecognitionPhase.RESULT)
    }.let { calibration ->
        MotionTarget(calibration.strength, calibration.speed)
    }

private fun transitionDurationFor(stage: ProductStage): Long =
    when (stage) {
        ProductStage.IMAGE_RECOGNIZING -> 420L
        ProductStage.FISH_LOCATED -> 260L
        ProductStage.SPECIES_RECOGNIZING -> 280L
        ProductStage.TERMINAL -> 200L
    }

private fun interpolate(
    from: Float,
    to: Float,
    nowMs: Long,
    startMs: Long,
    durationMs: Long,
): Float {
    if (durationMs <= 0L) return to
    val raw =
        ((nowMs - startMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    val eased = MotionEase.transform(raw)
    return from + (to - from) * eased
}

private fun easedWindow(
    elapsedMs: Long,
    startMs: Long,
    endMs: Long,
): Float {
    if (elapsedMs <= startMs) return 0f
    if (elapsedMs >= endMs) return 1f
    val raw =
        (elapsedMs - startMs).toFloat() /
            (endMs - startMs).toFloat()
    return MotionEase.transform(raw.coerceIn(0f, 1f))
}

private fun easedFraction(
    elapsedMs: Long,
    durationMs: Long,
): Float =
    MotionEase.transform(
        (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
    )

private fun positiveMod(value: Float): Float =
    ((value % 1f) + 1f) % 1f

/** Frozen seven-path primary AI topology: B1/B2/B3/B4 and G1/G2/G3. */
private val PRIMARY_ISLANDS = listOf(
    Filament(
        "B1",
        AiBlueCore,
        AiBlueHot,
        1.2.dp,
        .00f,
        .30f,
        1.00f,
        Cubic(.78f, -.02f),
        Cubic(.94f, .06f),
        Cubic(1.02f, .22f),
        Cubic(.96f, .42f),
    ),
    Filament(
        "B2",
        AiBlueCore,
        AiBlueHot,
        1.0.dp,
        .07f,
        .18f,
        .30f,
        Cubic(1.01f, .18f),
        Cubic(.92f, .31f),
        Cubic(.95f, .52f),
        Cubic(1.02f, .68f),
    ),
    Filament(
        "B3",
        AiBlueCore,
        AiBlueHot,
        1.2.dp,
        .47f,
        .20f,
        .54f,
        Cubic(1.02f, .61f),
        Cubic(.92f, .75f),
        Cubic(.84f, .89f),
        Cubic(.66f, 1.02f),
    ),
    Filament(
        "B4",
        AiBlueCore,
        AiBlueHot,
        .8.dp,
        .11f,
        .14f,
        .22f,
        Cubic(.22f, 1.02f),
        Cubic(.38f, .95f),
        Cubic(.52f, .95f),
        Cubic(.66f, 1.01f),
    ),
    Filament(
        "G1",
        AiGoldCore,
        AiGoldHot,
        1.1.dp,
        .12f,
        .26f,
        .78f,
        Cubic(-.02f, .18f),
        Cubic(.03f, .08f),
        Cubic(.12f, .02f),
        Cubic(.26f, -.02f),
    ),
    Filament(
        "G2",
        AiGoldCore,
        AiGoldHot,
        .9.dp,
        .09f,
        .16f,
        .26f,
        Cubic(-.02f, .33f),
        Cubic(.04f, .47f),
        Cubic(.02f, .62f),
        Cubic(-.01f, .78f),
    ),
    Filament(
        "G3",
        AiGoldCore,
        AiGoldHot,
        1.2.dp,
        .64f,
        .22f,
        .63f,
        Cubic(-.01f, .74f),
        Cubic(.08f, .86f),
        Cubic(.20f, .95f),
        Cubic(.38f, 1.02f),
    ),
)

private val COMPANION_HAIRLINES = listOf(
    EchoFilament(
        "B1C",
        "B1",
        AiBlueCore,
        AiBlueHot,
        .60.dp,
        -.055f,
        .44f,
        Cubic(.75f, -.01f),
        Cubic(.91f, .07f),
        Cubic(.99f, .20f),
        Cubic(.94f, .37f),
    ),
    EchoFilament(
        "G1C",
        "G1",
        AiGoldCore,
        AiGoldHot,
        .58.dp,
        .065f,
        .42f,
        Cubic(-.01f, .20f),
        Cubic(.04f, .11f),
        Cubic(.13f, .04f),
        Cubic(.24f, .00f),
    ),
    EchoFilament(
        "G3C",
        "G3",
        AiGoldCore,
        AiGoldHot,
        .60.dp,
        .050f,
        .38f,
        Cubic(.01f, .77f),
        Cubic(.09f, .87f),
        Cubic(.20f, .94f),
        Cubic(.34f, .99f),
    ),
    EchoFilament(
        "B3C",
        "B3",
        AiBlueCore,
        AiBlueHot,
        .58.dp,
        -.060f,
        .34f,
        Cubic(.99f, .65f),
        Cubic(.91f, .77f),
        Cubic(.82f, .90f),
        Cubic(.69f, .99f),
    ),
)

private val MICRO_HAIRLINES = listOf(
    EchoFilament(
        "B1M",
        "B1",
        AiBlueCore,
        AiBlueHot,
        .42.dp,
        .085f,
        .12f,
        Cubic(.84f, -.01f),
        Cubic(.94f, .05f),
        Cubic(.99f, .13f),
        Cubic(.985f, .24f),
    ),
)

private val PARTICLE_ANCHORS = listOf(
    ParticleAnchor("B1", Cubic(.84f, .10f)),
    ParticleAnchor("B1", Cubic(.96f, .33f)),
    ParticleAnchor("B3", Cubic(.92f, .64f)),
    ParticleAnchor("B3", Cubic(.73f, .88f)),
    ParticleAnchor("G1", Cubic(.08f, .18f)),
    ParticleAnchor("G1", Cubic(.04f, .51f)),
    ParticleAnchor("G3", Cubic(.18f, .90f)),
    ParticleAnchor("G3", Cubic(.48f, .97f)),
)
