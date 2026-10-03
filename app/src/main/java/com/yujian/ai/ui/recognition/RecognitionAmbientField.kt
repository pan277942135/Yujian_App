package com.yujian.ai.ui.recognition

import android.os.SystemClock
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yujian.ai.ai.RecognitionPhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

private val AiBlueCore = Color(0xFFBFEAF2)
private val AiBlueHot = Color(0xFFD8F5FA)
private val AiGoldCore = Color(0xFFF6D99B)
private val AiGoldHot = Color(0xFFFFE7AE)
private val MotionEase = CubicBezierEasing(.22f, 1f, .36f, 1f)

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

private data class MotionFrame(
    val cycleOffset: Float,
    val segmentSpeed: Float,
    val stateStrength: Float,
    val resolveStrength: Float,
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
    val resolveStrength: Float,
    val detailMotionTimeMs: Long,
    val reduceMotion: Boolean,
    val qualityLevel: RecognitionQualityLevel,
)

private class EdgeFieldMotionAccumulator {
    private var initialized = false
    private var stage = ProductStage.IMAGE_RECOGNIZING
    private var lastNowMs = 0L
    private var entryStartedAtMs = 0L
    private var cycleOffset = 0f
    private var resolving = false
    private var resolveStartedAtMs = 0L
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
        resolveActive: Boolean,
        resolveProgress: Float,
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

        if (nextStage != stage && !resolving) {
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

        if (reduceMotionNow != reduceMotion && !resolving) {
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

        if (resolveActive && !resolving) {
            advance(nowMs)
            resolving = true
            resolveStartedAtMs = nowMs
            speedFrom = 0f
            speedTo = 0f
            speedTransitionAtMs = nowMs
            speedTransitionMs = 0L
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
            resolveStrength = (1f - resolveProgress.coerceIn(0f, 1f)).let { it * it },
            detailMotionTimeMs = if (resolving) resolveStartedAtMs else nowMs,
            receivingEntry = receivingEntry,
            primaryEntry = primaryEntry,
            detailEntry = detailEntry,
        )
    }

    private fun advance(nowMs: Long) {
        if (!initialized || nowMs <= lastNowMs) return
        if (!resolving && !reduceMotion) {
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
    lowPerformance: Boolean = false,
    reduceMotion: Boolean = false,
    resolveProgress: Float = 0f,
    resolveActive: Boolean = resolveProgress > 0f,
    qualityLevel: RecognitionQualityLevel =
        if (lowPerformance) RecognitionQualityLevel.LITE else RecognitionQualityLevel.FULL,
    onMotionFrame: ((RecognitionMotionTraceSample) -> Unit)? = null,
) {
    val nowMs = ambientNowMs(visualClockMs)
    val accumulator = remember { EdgeFieldMotionAccumulator() }

    val frame = if (visualClockMs != null) {
        deterministicFrame(
            visualClockMs,
            phase,
            reduceMotion,
            resolveActive,
            resolveProgress,
        )
    } else {
        accumulator.sample(
            nowMs,
            phase,
            reduceMotion,
            resolveActive,
            resolveProgress,
        )
    }

    val quality = qualityProfile(qualityLevel)
    if (onMotionFrame != null) {
        LaunchedEffect(nowMs, phase, frame, reduceMotion, qualityLevel, onMotionFrame) {
            onMotionFrame(
                RecognitionMotionTraceSample(
                    uptimeMs = nowMs,
                    phase = phase,
                    segmentOffset = frame.cycleOffset,
                    segmentSpeed = frame.segmentSpeed,
                    stateStrength = frame.stateStrength,
                    resolveStrength = frame.resolveStrength,
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
        val primaryPaths = buildPaths(size, PRIMARY_ISLANDS)
        val companionPaths = buildEchoPaths(size, COMPANION_HAIRLINES)
        val microPaths = buildEchoPaths(size, MICRO_HAIRLINES)

        drawReceivingLight(
            frame = frame,
            quality = quality,
        )

        PRIMARY_ISLANDS.forEachIndexed { index, filament ->
            val path = primaryPaths[index]
            val offset = positiveMod(frame.cycleOffset + filament.seed)
            val common =
                frame.stateStrength *
                    frame.resolveStrength *
                    frame.primaryEntry *
                    quality.alphaMultiplier *
                    filament.islandWeight

            drawCompositePrimary(
                path = path,
                filament = filament,
                offset = offset,
                common = common,
                quality = quality,
            )
        }

        if (quality.companionIds.isNotEmpty()) {
            COMPANION_HAIRLINES.forEachIndexed { index, echo ->
                if (echo.id !in quality.companionIds) return@forEachIndexed
                val parent = PRIMARY_ISLANDS.first { it.id == echo.parentId }
                val common =
                    frame.stateStrength *
                        frame.resolveStrength *
                        frame.detailEntry *
                        quality.alphaMultiplier *
                        parent.islandWeight
                drawHairlineEcho(
                    path = companionPaths[index],
                    echo = echo,
                    offset = positiveMod(frame.cycleOffset + parent.seed + echo.phaseDelta),
                    common = common,
                    micro = false,
                )
            }
        }

        if (quality.microEnabled) {
            MICRO_HAIRLINES.forEachIndexed { index, echo ->
                val parent = PRIMARY_ISLANDS.first { it.id == echo.parentId }
                val common =
                    frame.stateStrength *
                        frame.resolveStrength *
                        frame.detailEntry *
                        quality.alphaMultiplier *
                        parent.islandWeight
                drawHairlineEcho(
                    path = microPaths[index],
                    echo = echo,
                    offset = positiveMod(frame.cycleOffset + parent.seed + echo.phaseDelta),
                    common = common,
                    micro = true,
                )
            }
        }

        if (quality.nodeCount > 0) {
            drawEnergyNodes(
                nowMs = frame.detailMotionTimeMs,
                frame = frame,
                quality = quality,
                reduceMotion = reduceMotion,
            )
        }

        if (quality.particleCount > 0 && !reduceMotion) {
            drawParticles(
                nowMs = frame.detailMotionTimeMs,
                count = quality.particleCount,
                frame = frame,
                quality = quality,
            )
        }
    }
}

@Composable
private fun ambientNowMs(override: Long?): Long {
    if (override != null) return override

    var nowMs by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            nowMs = SystemClock.uptimeMillis()
            delay(33L)
        }
    }
    return nowMs
}

private fun deterministicFrame(
    clockMs: Long,
    phase: RecognitionPhase,
    reduceMotion: Boolean,
    resolveActive: Boolean,
    resolveProgress: Float,
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

    val resolve =
        (1f - resolveProgress.coerceIn(0f, 1f)).let { it * it }

    return MotionFrame(
        cycleOffset = offset,
        segmentSpeed = if (reduceMotion || resolveActive) 0f else target.speed,
        stateStrength = target.strength,
        resolveStrength = resolve,
        detailMotionTimeMs = if (resolveActive) {
            (clockMs - (resolveProgress.coerceIn(0f, 1f) * 200f).toLong()).coerceAtLeast(0L)
        } else {
            clockMs
        },
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

private fun DrawScope.drawReceivingLight(
    frame: MotionFrame,
    quality: QualityProfile,
) {
    val common =
        frame.stateStrength *
            frame.resolveStrength *
            frame.receivingEntry *
            quality.receiving *
            quality.alphaMultiplier

    if (common <= .001f) return

    fun islandLight(
        color: Color,
        weight: Float,
        center: Offset,
        radius: Float,
        ovalTopLeft: Offset,
        ovalSize: Size,
    ) {
        val alpha = (.13f * common * weight).coerceAtMost(.14f)
        val brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to color.copy(alpha = alpha),
                .34f to color.copy(alpha = alpha * .92f),
                .72f to color.copy(alpha = alpha * .40f),
                1f to Color.Transparent,
            ),
            center = center,
            radius = radius,
        )
        drawOval(
            brush = brush,
            topLeft = ovalTopLeft,
            size = ovalSize,
        )
    }

    islandLight(
        color = AiBlueCore,
        weight = 1f,
        center = Offset(size.width * 1.00f, size.height * .12f),
        radius = size.minDimension * .28f,
        ovalTopLeft = Offset(size.width * .62f, -size.height * .04f),
        ovalSize = Size(size.width * .46f, size.height * .40f),
    )
    islandLight(
        color = AiGoldCore,
        weight = .78f,
        center = Offset(0f, size.height * .10f),
        radius = size.minDimension * .25f,
        ovalTopLeft = Offset(-size.width * .08f, -size.height * .04f),
        ovalSize = Size(size.width * .46f, size.height * .36f),
    )
    islandLight(
        color = AiGoldCore,
        weight = .63f,
        center = Offset(0f, size.height * .88f),
        radius = size.minDimension * .26f,
        ovalTopLeft = Offset(-size.width * .08f, size.height * .64f),
        ovalSize = Size(size.width * .48f, size.height * .38f),
    )
    islandLight(
        color = AiBlueCore,
        weight = .54f,
        center = Offset(size.width, size.height * .86f),
        radius = size.minDimension * .25f,
        ovalTopLeft = Offset(size.width * .60f, size.height * .64f),
        ovalSize = Size(size.width * .48f, size.height * .38f),
    )
}

private fun DrawScope.drawCompositePrimary(
    path: Path,
    filament: Filament,
    offset: Float,
    common: Float,
    quality: QualityProfile,
) {
    if (common <= .001f) return

    val effect = segmentEffect(
        filament = filament,
        size = size,
        offset = offset,
        visibleRatio = filament.visibleRatio,
    )

    fun stroke(width: Dp) = Stroke(
        width = width.toPx(),
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
        pathEffect = effect,
    )

    drawPath(
        path,
        filament.color.copy(
            alpha = (.04f * common * quality.outer).coerceAtMost(.055f),
        ),
        style = stroke(10.dp),
    )
    drawPath(
        path,
        filament.color.copy(
        alpha = (.075f * common * quality.mid).coerceAtMost(.10f),
        ),
        style = stroke(4.dp),
    )
    drawPath(
        path,
        filament.hot.copy(
            alpha = (.46f * common * quality.core).coerceAtMost(.50f),
        ),
        style = stroke(filament.width),
    )
}

private fun DrawScope.drawHairlineEcho(
    path: Path,
    echo: EchoFilament,
    offset: Float,
    common: Float,
    micro: Boolean,
) {
    if (common <= .001f) return

    val pseudo = Filament(
        id = echo.id,
        color = echo.color,
        hot = echo.hot,
        width = echo.width,
        seed = 0f,
        visibleRatio = echo.visibleRatio,
        islandWeight = 1f,
        start = echo.start,
        control1 = echo.control1,
        control2 = echo.control2,
        end = echo.end,
    )
    val effect = segmentEffect(
        filament = pseudo,
        size = size,
        offset = offset,
        visibleRatio = echo.visibleRatio,
    )

    val localWeight = if (micro) .18f else .30f
    val glowWidth = if (micro) 1.5.dp else 2.1.dp

    drawPath(
        path,
        echo.color.copy(
            alpha = (.18f * common * localWeight).coerceAtMost(.08f),
        ),
        style = Stroke(
            width = glowWidth.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = effect,
        ),
    )
    drawPath(
        path,
        echo.hot.copy(
            alpha = (.52f * common * localWeight).coerceAtMost(.20f),
        ),
        style = Stroke(
            width = echo.width.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = effect,
        ),
    )
}

private fun DrawScope.drawEnergyNodes(
    nowMs: Long,
    frame: MotionFrame,
    quality: QualityProfile,
    reduceMotion: Boolean,
) {
    val common =
        frame.stateStrength *
            frame.resolveStrength *
            frame.detailEntry *
            quality.alphaMultiplier

    val nodes = listOf("B1" to AiBlueHot, "G1" to AiGoldHot)

    repeat(quality.nodeCount.coerceAtMost(nodes.size)) { index ->
        val (islandId, color) = nodes[index]
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
        val center = bezierPoint(island, trajectory, size)

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
) {
    val common =
        frame.stateStrength *
            frame.resolveStrength *
            frame.detailEntry *
            quality.alphaMultiplier

    repeat(count.coerceAtMost(PARTICLE_ANCHORS.size)) { index ->
        val anchor = PARTICLE_ANCHORS[index]
        val t = ((nowMs % 5_000L) / 5_000f + index * .137f) % 1f
        val travel = 14f + (index % 4) * 7f
        val x =
            anchor.point.x * size.width +
                sin((t + index) * 2f * PI).toFloat() * travel
        val y =
            anchor.point.y * size.height +
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

private fun bezierPoint(filament: Filament, t: Float, size: Size): Offset {
    val oneMinus = 1f - t
    val x = oneMinus * oneMinus * oneMinus * filament.start.x +
        3f * oneMinus * oneMinus * t * filament.control1.x +
        3f * oneMinus * t * t * filament.control2.x +
        t * t * t * filament.end.x
    val y = oneMinus * oneMinus * oneMinus * filament.start.y +
        3f * oneMinus * oneMinus * t * filament.control1.y +
        3f * oneMinus * t * t * filament.control2.y +
        t * t * t * filament.end.y
    return Offset(x * size.width, y * size.height)
}

private fun segmentEffect(
    filament: Filament,
    size: Size,
    offset: Float,
    visibleRatio: Float,
): PathEffect {
    val length = estimatedLength(filament, size).coerceAtLeast(1f)
    val visible = (length * visibleRatio.coerceIn(.05f, .60f)).coerceAtLeast(1f)
    val gap = (length - visible).coerceAtLeast(1f)
    return PathEffect.dashPathEffect(
        floatArrayOf(visible, gap),
        -positiveMod(offset) * length,
    )
}

private fun estimatedLength(
    filament: Filament,
    size: Size,
): Float {
    fun mapped(p: Cubic) =
        Offset(
            p.x * size.width,
            p.y * size.height,
        )

    fun distance(a: Offset, b: Offset): Float =
        hypot(
            (a.x - b.x).toDouble(),
            (a.y - b.y).toDouble(),
        ).toFloat()

    val p0 = mapped(filament.start)
    val p1 = mapped(filament.control1)
    val p2 = mapped(filament.control2)
    val p3 = mapped(filament.end)

    return (distance(p0, p1) + distance(p1, p2) + distance(p2, p3)) * .72f
}

private fun buildPaths(
    size: Size,
    filaments: List<Filament>,
): List<Path> =
    filaments.map { filament ->
        Path().apply {
            moveTo(
                filament.start.x * size.width,
                filament.start.y * size.height,
            )
            cubicTo(
                filament.control1.x * size.width,
                filament.control1.y * size.height,
                filament.control2.x * size.width,
                filament.control2.y * size.height,
                filament.end.x * size.width,
                filament.end.y * size.height,
            )
        }
    }

private fun buildEchoPaths(
    size: Size,
    filaments: List<EchoFilament>,
): List<Path> =
    filaments.map { filament ->
        Path().apply {
            moveTo(
                filament.start.x * size.width,
                filament.start.y * size.height,
            )
            cubicTo(
                filament.control1.x * size.width,
                filament.control1.y * size.height,
                filament.control2.x * size.width,
                filament.control2.y * size.height,
                filament.end.x * size.width,
                filament.end.y * size.height,
            )
        }
    }

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
        RecognitionPhase.OUTLINE -> RecognitionAmbientStateCalibration(.48f, .62f)
        RecognitionPhase.CLASSIFYING -> RecognitionAmbientStateCalibration(.30f, .42f)
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

