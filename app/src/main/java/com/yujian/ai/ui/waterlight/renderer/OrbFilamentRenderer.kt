package com.yujian.ai.ui.waterlight.renderer

import android.graphics.BlurMaskFilter
import android.graphics.Paint as NativePaint
import android.graphics.Path as NativePath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
import com.yujian.ai.ui.waterlight.core.EnergyEvent
import com.yujian.ai.ui.waterlight.core.EnergyFeedback
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

enum class OrbFilamentRendererMode { LEGACY, ORB_V1_CANDIDATE }

/**
 * One Orb ribbon entry point. LEGACY remains byte-for-byte visually equivalent to the old
 * three-ellipse implementation. Candidate mode draws the five generated R06 Bezier paths only.
 */
@Composable
fun OrbWaterLightRibbons(
    coreSize: Dp,
    frame: WaterLightFrame,
    opening: Float,
    settle: Float,
    front: Boolean,
    low: Boolean,
    material: WaterLightMaterialProfile = WaterLightMaterialProfile.ORB_LEGACY,
    mode: OrbFilamentRendererMode = OrbFilamentRendererMode.LEGACY,
    lifeAlpha: Float = opening,
    motionEvent: OrbRuntimeProfileV1.MotionEvent = OrbRuntimeProfileV1.MotionEvent.NONE,
    motionEventStartOpticalMs: Long = 0L,
    energyEvent: EnergyEvent? = null,
    releaseEnergy: Float = 0f,
    returnEnergy: Float = 0f,
) {
    if (mode == OrbFilamentRendererMode.LEGACY) {
        LegacyOrbWaterLightRibbons(coreSize, frame, opening, settle, front, low, material, energyEvent, releaseEnergy, returnEnergy)
        return
    }
    val densityScale = LocalDensity.current.density
    val renderer = remember(front, densityScale) { OrbFilamentPathRenderer(front, densityScale) }
    Canvas(Modifier.fillMaxSize()) {
        drawIntoCanvas { canvas ->
            renderer.draw(
                canvas.nativeCanvas,
                coreSize.toPx(),
                center.x,
                center.y,
                frame,
                opening,
                settle,
                low,
                lifeAlpha,
                motionEvent,
                motionEventStartOpticalMs,
                density,
            )
        }
    }
}

/** Cached arc-length LUTs, paths and paints for one depth plane. */
internal class OrbFilamentPathRenderer(private val front: Boolean, private val density: Float) {
    private data class PathCache(
        val spec: OrbFilamentSpecV1,
        val material: OrbFilamentMaterialV1,
        val lut: OrbFilamentPathMathV1.ArcLengthLut,
        val samplesX: FloatArray,
        val samplesY: FloatArray,
        val samplesU: FloatArray,
        val samplesNormalX: FloatArray,
        val samplesNormalY: FloatArray,
        val scratch: FloatArray,
        val paths: Array<Array<NativePath>>,
        val paints: Array<NativePaint>,
        val blurFilters: Array<BlurMaskFilter?>,
        val headPaint: NativePaint,
        val bandPath: NativePath,
        val bandPaint: NativePaint,
    )

    private val chunkCount = 12
    private val layerCount = 4
    private val caches = OrbRuntimeProfileV1.paths.filter { it.front == front }.map { spec ->
        val material = requireNotNull(OrbRuntimeProfileV1.materials[spec.material])
        val staticLut = OrbFilamentPathMathV1.newLut().also {
            OrbFilamentPathMathV1.fillLut(
                spec,
                (spec.deformationPeriodMs * OrbRuntimeProfileV1.R06_REDUCED_MOTION_PHASE).toLong(),
                it,
            )
        }
        PathCache(
            spec,
            material,
            staticLut,
            FloatArray(97), FloatArray(97), FloatArray(97), FloatArray(97), FloatArray(97),
            FloatArray(6),
            Array(chunkCount) { Array(layerCount) { NativePath() } },
            Array(layerCount) { NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
                style = NativePaint.Style.STROKE
                strokeCap = NativePaint.Cap.ROUND
                strokeJoin = NativePaint.Join.ROUND
            } },
            Array(layerCount) { layerIndex ->
                material.layers.getOrNull(layerIndex)?.blurSigmaDp?.let {
                    BlurMaskFilter(it * density, BlurMaskFilter.Blur.NORMAL)
                }
            },
            NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply { style = NativePaint.Style.FILL },
            NativePath(),
            NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
                style = NativePaint.Style.STROKE
                strokeCap = NativePaint.Cap.ROUND
                strokeJoin = NativePaint.Join.ROUND
            },
        )
    }

    fun draw(
        canvas: android.graphics.Canvas,
        diameterPx: Float,
        centerX: Float,
        centerY: Float,
        frame: WaterLightFrame,
        opening: Float,
        settle: Float,
        low: Boolean,
        lifeAlpha: Float,
        event: OrbRuntimeProfileV1.MotionEvent,
        eventStartMs: Long,
        density: Float,
    ) {
        if (diameterPx <= 0f) return
        val sceneMs = if (frame.reducedMotion) 0L else frame.elapsedMs
        val opticalMs = frame.opticalReferenceElapsedMs
        val steps = if (low) OrbRuntimeProfileV1.R06_LOW_STEPS_PER_PATH else OrbRuntimeProfileV1.R06_NORMAL_STEPS_PER_PATH
        val active = (opening.coerceIn(0f, 1f) * lifeAlpha.coerceIn(0f, 1f) * (1f - settle.coerceIn(0f, 1f))).coerceIn(0f, 1f)
        val diameterDp = diameterPx / density
        if (active <= 0f) return
        val canvasSave = canvas.save()
        try {
            for (cache in caches) {
                val spec = cache.spec
                val material = cache.material
                val lut = cache.lut
                if (!frame.reducedMotion) OrbFilamentPathMathV1.fillLut(spec, sceneMs, lut)
                val sceneFrame = frame.sceneFrame
                val localRelease = OrbWakeBreathReleaseV11.localRelease(
                    sceneFrame.stage, sceneFrame.stageProgress,
                ) != OrbWakeBreathReleaseV11.LocalRelease.AMBIENT
                val move = when {
                    localRelease -> OrbFilamentPathMathV1.localReleaseMovementForStage(
                        spec, sceneMs, sceneFrame.stage, sceneFrame.stageProgress,
                        frame.reducedMotion, lut.cumulativeD.last(), diameterDp,
                    )
                    event != OrbRuntimeProfileV1.MotionEvent.NONE ->
                        OrbFilamentPathMathV1.movement(spec, opticalMs, frame.reducedMotion, event, eventStartMs)
                    else -> OrbFilamentPathMathV1.movement(spec, sceneMs, frame.reducedMotion,
                        OrbRuntimeProfileV1.MotionEvent.NONE, 0L)
                }
                val trail = material.trailLengthFraction
                val strokeMarginDp = spec.widthMaxDp * material.widthMultiplier * material.layers.maxOf { it.widthMultiplier } * .5f
                val maxCenterRadiusDp = minOf(
                    spec.maxOuterRadiusD * diameterDp - strokeMarginDp,
                    diameterDp * .5f + OrbWakeBreathReleaseV11.R06_MAX_EXTENSION_DP - strokeMarginDp,
                ).coerceAtLeast(0f)
                val startU = move.u - move.direction * trail
                val widthCycle = if (frame.reducedMotion) OrbRuntimeProfileV1.R06_REDUCED_MOTION_PHASE else sceneMs.toFloat() / spec.periodMs
                val widthPulse = .5f + .5f * sin((2.0 * PI * (widthCycle + spec.phase)).toFloat())
                val alphaPulse = spec.alphaMin + (spec.alphaMax - spec.alphaMin) * widthPulse
                val eventBrightness = if (localRelease) OrbWakeBreathReleaseV11.R06_RELEASE_HEAD_BRIGHTNESS_GAIN else when (event) {
                    OrbRuntimeProfileV1.MotionEvent.RELEASE -> OrbWakeBreathReleaseV11.R06_RELEASE_HEAD_BRIGHTNESS_GAIN
                    OrbRuntimeProfileV1.MotionEvent.RETURN -> 1.07f
                    OrbRuntimeProfileV1.MotionEvent.NONE -> 1f
                }
                val endFade = when {
                    move.unclampedU < 0f -> (1f - abs(move.unclampedU) / trail).coerceIn(0f, 1f)
                    move.unclampedU > 1f -> (1f - (move.unclampedU - 1f) / trail).coerceIn(0f, 1f)
                    else -> 1f
                }
                if (endFade <= 0f) continue
                val samplesX = cache.samplesX
                val samplesY = cache.samplesY
                val samplesU = cache.samplesU
                val samplesNormalX = cache.samplesNormalX
                val samplesNormalY = cache.samplesNormalY
                var anyPoint = false
                for (i in 0..steps) {
                    val tailProgress = i.toFloat() / steps
                    val u = startU + move.direction * trail * tailProgress
                    if (u !in 0f..1f) continue
                    OrbFilamentPathMathV1.sampleAt(lut, u, cache.scratch, 0)
                    OrbFilamentPathMathV1.sampleAt(lut, (u - .003f).coerceIn(0f, 1f), cache.scratch, 2)
                    OrbFilamentPathMathV1.sampleAt(lut, (u + .003f).coerceIn(0f, 1f), cache.scratch, 4)
                    val dx = cache.scratch[4] - cache.scratch[2]
                    val dy = cache.scratch[5] - cache.scratch[3]
                    val length = sqrt(dx * dx + dy * dy).coerceAtLeast(.0001f)
                    val nx = -dy / length
                    val ny = dx / length
                    val waveD = if (frame.reducedMotion) 0f else sin(
                        (2.0 * PI * (sceneMs.toDouble() / spec.deformationPeriodMs + spec.phase + u * 1.7)).toFloat(),
                    ) * spec.normalAmplitudeD
                    var px = cache.scratch[0] + nx * waveD
                    var py = cache.scratch[1] + ny * waveD
                    val radiusDp = sqrt(px * px + py * py) * diameterDp
                    if (radiusDp > maxCenterRadiusDp && radiusDp > 0f) {
                        val scale = maxCenterRadiusDp / radiusDp
                        px *= scale
                        py *= scale
                    }
                    samplesX[i] = centerX + px * diameterPx
                    samplesY[i] = centerY + py * diameterPx
                    samplesU[i] = u
                    samplesNormalX[i] = nx
                    samplesNormalY[i] = ny
                    anyPoint = true
                }
                if (!anyPoint) continue
                val layerCountForMaterial = material.layers.size.coerceAtMost(layerCount)
                for (layerIndex in 0 until layerCountForMaterial) {
                    val layer = material.layers[layerIndex]
                    val paint = cache.paints[layerIndex]
                    val colorInt = layer.color.toInt() or (0xFF shl 24)
                    paint.color = colorInt
                    paint.maskFilter = if (layer.blurSigmaDp != null && !low) cache.blurFilters[layerIndex] else null
                    val chunkSize = steps.toFloat() / chunkCount
                    for (chunk in 0 until chunkCount) {
                        val first = (chunk * chunkSize).toInt().coerceAtMost(steps)
                        val last = ((chunk + 1) * chunkSize).toInt().coerceAtMost(steps)
                        if (first >= last) continue
                        val mid = (first + last) / 2
                        val pathU = samplesU[mid]
                        val tailAlpha = tailTaper((mid.toFloat() / steps), material.tailLuminance)
                        val profileWidth = OrbFilamentPathMathV1.profileAt(material.widthProfile, pathU)
                        val profileOpacity = OrbFilamentPathMathV1.profileAt(material.opacityProfile, pathU)
                        val crossOpacity = OrbFilamentPathMathV1.profileAt(material.crossSection, layer.offsetNormalFraction ?: 0f)
                        val longitudinal = material.tailLuminance + (material.bodyLuminance - material.tailLuminance) * (mid.toFloat() / steps)
                        val headBoost = if (mid >= steps - max(1, (steps * material.specularBandLengthFraction).toInt())) {
                            material.headLuminance
                        } else 1f
                        val layerAlpha = (active * alphaPulse * profileOpacity * crossOpacity * tailAlpha * longitudinal *
                            headBoost * eventBrightness * layer.alphaMax * endFade * OrbWakeBreathReleaseV11.R06_ALPHA_GAIN)
                            .coerceIn(0f, OrbWakeBreathReleaseV11.R06_ALPHA_CAP)
                        if (layerAlpha <= .002f) continue
                        val path = cache.paths[chunk][layerIndex]
                        path.rewind()
                        var began = false
                        for (i in first..last) {
                            if (samplesU[i] == 0f && samplesX[i] == 0f && samplesY[i] == 0f) {
                                began = false
                                continue
                            }
                            val offsetFraction = layer.offsetNormalFraction ?: 0f
                            val x = samplesX[i] + samplesNormalX[i] * offsetFraction * diameterPx * .003f
                            val y = samplesY[i] + samplesNormalY[i] * offsetFraction * diameterPx * .003f
                            if (!began) { path.moveTo(x, y); began = true } else path.lineTo(x, y)
                        }
                        if (!began) continue
                        paint.alpha = (255f * layerAlpha).toInt().coerceIn(0, 255)
                        paint.strokeWidth = spec.widthMinDp + (spec.widthMaxDp - spec.widthMinDp) * widthPulse
                        paint.strokeWidth *= material.widthMultiplier * profileWidth * layer.widthMultiplier * density
                        canvas.drawPath(path, paint)
                    }
                }
                // The moving local highlight is separate from the low-energy body and follows u.
                if (material.layers.any { it.travelsWithHead }) {
                    OrbFilamentPathMathV1.sampleAt(lut, move.u, cache.scratch, 0)
                    OrbFilamentPathMathV1.sampleAt(lut, (move.u - move.direction * .006f).coerceIn(0f, 1f), cache.scratch, 2)
                    OrbFilamentPathMathV1.sampleAt(lut, (move.u + move.direction * .006f).coerceIn(0f, 1f), cache.scratch, 4)
                    val dx = cache.scratch[4] - cache.scratch[2]
                    val dy = cache.scratch[5] - cache.scratch[3]
                    val length = sqrt(dx * dx + dy * dy).coerceAtLeast(.0001f)
                    val nx = -dy / length
                    val ny = dx / length
                    cache.headPaint.color = material.highlightColor.toInt() or (0xFF shl 24)
                    val headAlpha = (active * alphaPulse * material.specularHeadAlpha * eventBrightness *
                        OrbWakeBreathReleaseV11.R06_ALPHA_GAIN * OrbWakeBreathReleaseV11.R06_MOVING_HIGHLIGHT_GAIN * endFade)
                        .coerceIn(0f, OrbWakeBreathReleaseV11.R06_ALPHA_CAP)
                    cache.headPaint.alpha = (255f * headAlpha).toInt().coerceIn(0, 255)
                    val radiusDp = material.specularHeadRadiusDpMin +
                        (material.specularHeadRadiusDpMax - material.specularHeadRadiusDpMin) * widthPulse
                    canvas.drawCircle(centerX + (cache.scratch[0] + nx * spec.normalAmplitudeD) * diameterPx,
                        centerY + (cache.scratch[1] + ny * spec.normalAmplitudeD) * diameterPx,
                        radiusDp * density, cache.headPaint)
                    cache.bandPath.rewind()
                    val fromU = (move.u - move.direction * material.specularBandLengthFraction * trail).coerceIn(0f, 1f)
                    val toU = move.u.coerceIn(0f, 1f)
                    OrbFilamentPathMathV1.sampleAt(lut, fromU, cache.scratch, 0)
                    cache.bandPath.moveTo(centerX + cache.scratch[0] * diameterPx, centerY + cache.scratch[1] * diameterPx)
                    for (i in 1..8) {
                        val u = fromU + (toU - fromU) * i / 8f
                        OrbFilamentPathMathV1.sampleAt(lut, u, cache.scratch, 0)
                        cache.bandPath.lineTo(centerX + cache.scratch[0] * diameterPx, centerY + cache.scratch[1] * diameterPx)
                    }
                    cache.bandPaint.color = material.highlightColor.toInt() or (0xFF shl 24)
                    val bandAlpha = (active * alphaPulse * material.specularHeadAlpha * eventBrightness *
                        OrbWakeBreathReleaseV11.R06_ALPHA_GAIN * OrbWakeBreathReleaseV11.R06_MOVING_HIGHLIGHT_GAIN * .58f * endFade)
                        .coerceIn(0f, OrbWakeBreathReleaseV11.R06_ALPHA_CAP)
                    cache.bandPaint.alpha = (255f * bandAlpha).toInt().coerceIn(0, 255)
                    cache.bandPaint.strokeWidth = max(.28f, spec.widthMinDp * .34f) * density
                    canvas.drawPath(cache.bandPath, cache.bandPaint)
                }
            }
        } finally {
            canvas.restoreToCount(canvasSave)
        }
    }

    private fun tailTaper(progress: Float, tailLuminance: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        val fade = (t / .18f).coerceIn(0f, 1f)
        val smooth = fade * fade * (3f - 2f * fade)
        return tailLuminance + (1f - tailLuminance) * smooth
    }
}

/** Three-material elliptical implementation retained solely as an explicit fallback. */
@Composable
private fun LegacyOrbWaterLightRibbons(
    coreSize: Dp,
    frame: WaterLightFrame,
    opening: Float,
    settle: Float,
    front: Boolean,
    low: Boolean,
    material: WaterLightMaterialProfile,
    energyEvent: EnergyEvent?,
    releaseEnergy: Float,
    returnEnergy: Float,
) {
    val time = frame.timeSeconds
    Canvas(Modifier.fillMaxSize()) {
        val radius = coreSize.toPx() * .5f
        val retention = when (energyEvent?.feedback) {
            EnergyFeedback.RELEASE -> 1f - .42f * releaseEnergy.coerceIn(0f, 1f)
            EnergyFeedback.RETURN -> .58f + .42f * returnEnergy.coerceIn(0f, 1f)
            else -> 1f
        }
        val colors = listOf(Color(material.goldColor.toInt()), Color(material.blueColor.toInt()), Color(material.greenColor.toInt()))
        colors.forEachIndexed { layer, color ->
            val period = listOf(material.goldPeriodSeconds, material.bluePeriodSeconds, material.greenPeriodSeconds)[layer]
            val phase = time * 2f * PI.toFloat() / period * (if (layer == 1) -1f else 1f)
            val tilt = listOf(-.45f, .60f, 1.25f)[layer]
            val expansion = if (energyEvent?.feedback == EnergyFeedback.RELEASE) releaseEnergy.coerceIn(0f, 1f) else 0f
            val contraction = if (energyEvent?.feedback == EnergyFeedback.RETURN) returnEnergy.coerceIn(0f, 1f) else 0f
            val r = radius + (10f + layer * 2f).dp.toPx() * opening * (1f - settle) + radius * .42f * expansion * (1f - contraction)
            val steps = if (low) OrbRuntimeProfileV1.R06_LOW_STEPS_PER_PATH else OrbRuntimeProfileV1.R06_NORMAL_STEPS_PER_PATH
            val path = androidx.compose.ui.graphics.Path()
            var connected = false
            val sweep = listOf(1.62f, 1.48f, 1.16f)[layer] * PI.toFloat()
            for (i in 0..steps) {
                val a = i * sweep / steps + phase
                val z = sin(a)
                val isFront = z >= 0f
                val ripple = 1f + .035f * sin(a * 3f + time * .8f + layer)
                val x = cos(a) * r * ripple
                val y = sin(a) * r * ripple * (.33f + .03f * sin(time * .9f + layer))
                val point = center + androidx.compose.ui.geometry.Offset(x * cos(tilt) - y * sin(tilt), x * sin(tilt) + y * cos(tilt))
                if (isFront == front) {
                    if (!connected) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                    connected = true
                } else connected = false
            }
            val alpha = opening * (if (front) .72f else .25f) * (if (layer == 2) .45f else 1f) *
                (1f - .42f * expansion + .38f * contraction).coerceIn(.35f, 1f) * retention
            val glowWidth = listOf(material.goldGlowWidthDp, material.blueGlowWidthDp, material.greenGlowWidthDp)[layer]
            val coreWidth = listOf(material.goldCoreWidthDp, material.blueCoreWidthDp, material.greenCoreWidthDp)[layer]
            drawPath(path, color.copy(alpha = alpha * .18f), style = androidx.compose.ui.graphics.drawscope.Stroke(glowWidth.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
            drawPath(path, color.copy(alpha = alpha), style = androidx.compose.ui.graphics.drawscope.Stroke(coreWidth.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
            if (layer != 2 && front) drawPath(path, Color(0xFFFFFAE8).copy(alpha = alpha * .5f), style = androidx.compose.ui.graphics.drawscope.Stroke(.35.dp.toPx()))
        }
    }
}
