package com.yujian.ai.ui.waterlight.renderer

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.RadialGradient
import android.graphics.Paint as NativePaint
import android.graphics.Path as NativePath
import android.graphics.Shader
import android.util.Log
import com.yujian.ai.BuildConfig
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yujian.ai.R
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
import com.yujian.ai.ui.waterlight.core.EnergyEvent
import com.yujian.ai.ui.waterlight.core.EnergyFeedback
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

enum class OrbFilamentRendererMode { LEGACY, ORB_V1_CANDIDATE }
enum class OrbR06RenderPass { BASELINE, GLASS_TRANSMISSION }
data class OrbR06PathDiagnostic(
    val pathId: String,
    val front: Boolean,
    val renderPass: OrbR06RenderPass,
    val stage: RecognitionSceneStage,
    val stageProgress: Float,
    val geometryVersion: Long,
    val validSampleCount: Int,
    val bodyValidSegmentCount: Int,
    val bodyDrawnRunCount: Int,
    val bodyPaintAlpha: Int,
    val bodyStrokeWidthPx: Float,
    val bodyOriginalAlpha: Float,
    val bodyFinalAlpha: Float,
    val bodyAddedAlpha: Float,
)

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
    visualProfile: OrbStage02VisualProfile = OrbStage02VisualProfile.BASELINE,
    renderPass: OrbR06RenderPass = OrbR06RenderPass.BASELINE,
    onPathMetrics: ((Boolean, OrbR06RenderPass, Set<String>) -> Unit)? = null,
    onPathDiagnostics: ((Boolean, OrbR06RenderPass, List<OrbR06PathDiagnostic>) -> Unit)? = null,
) {
    if (mode == OrbFilamentRendererMode.LEGACY) {
        if (renderPass == OrbR06RenderPass.BASELINE) {
            LegacyOrbWaterLightRibbons(coreSize, frame, opening, settle, front, low, material, energyEvent, releaseEnergy, returnEnergy)
        }
        return
    }
    val densityScale = LocalDensity.current.density
    val renderer = remember(front, densityScale, renderPass) {
        OrbFilamentPathRenderer(
            front = front,
            density = densityScale,
            renderPass = renderPass,
            pathIds = if (renderPass == OrbR06RenderPass.GLASS_TRANSMISSION) setOf("G01", "A01") else null,
        )
    }
    val coreMaskBitmap = if (renderPass == OrbR06RenderPass.GLASS_TRANSMISSION) {
        imageResource(R.drawable.processing_water_core_v1).asAndroidBitmap()
    } else null
    val lastDiagnosticCheckpoint = remember(front, renderPass, visualProfile) { arrayOfNulls<String>(1) }
    Canvas(Modifier.fillMaxSize()) {
        drawIntoCanvas { canvas ->
            val metrics = renderer.draw(
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
                visualProfile,
                coreMaskBitmap,
            )
            onPathMetrics?.invoke(front, renderPass, metrics.drawnPathIds)
            val progress = frame.sceneFrame.stageProgress.coerceIn(0f, 1f)
            val checkpoint = when {
                frame.sceneFrame.stage != RecognitionSceneStage.ORB_WAKE -> null
                abs(progress - .25f) <= .01f -> "25"
                abs(progress - .50f) <= .01f -> "50"
                abs(progress - .75f) <= .01f -> "75"
                abs(progress - 1f) <= .01f -> "100"
                else -> null
            }
            val checkpointKey = checkpoint?.let { "${frame.sceneFrame.stage.name}:$it" }
            if (onPathDiagnostics != null && checkpointKey != null && lastDiagnosticCheckpoint[0] != checkpointKey) {
                lastDiagnosticCheckpoint[0] = checkpointKey
                onPathDiagnostics.invoke(front, renderPass, metrics.paths.values.map { metric ->
                    OrbR06PathDiagnostic(
                        pathId = metric.pathId,
                        front = metric.front,
                        renderPass = renderPass,
                        stage = frame.sceneFrame.stage,
                        stageProgress = frame.sceneFrame.stageProgress,
                        geometryVersion = frame.geometryVersion,
                        validSampleCount = metric.validSampleCount,
                        bodyValidSegmentCount = metric.bodyValidSegmentCount,
                        bodyDrawnRunCount = metric.bodyDrawnRunCount,
                        bodyPaintAlpha = metric.bodyPaintAlpha,
                        bodyStrokeWidthPx = metric.bodyStrokeWidthPx,
                        bodyOriginalAlpha = metric.bodyMaximumOriginalAlpha,
                        bodyFinalAlpha = metric.bodyMaximumFinalAlpha,
                        bodyAddedAlpha = metric.bodyMaximumAddedAlpha,
                    )
                })
            }
        }
    }
}

/** Cached arc-length LUTs, paths and paints for one depth plane. */
internal class OrbFilamentPathRenderer(
    private val front: Boolean,
    private val density: Float,
    private val pathIdFilter: String? = null,
    private val renderPass: OrbR06RenderPass = OrbR06RenderPass.BASELINE,
    private val pathIds: Set<String>? = null,
) {
    data class PathFrameMetrics(
        val pathId: String,
        val front: Boolean,
        val validSampleCount: Int,
        val validBodySegmentCount: Int,
        val drawnRunCount: Int,
        val separatedInvalidGapCount: Int,
        val bridgedInvalidGapCount: Int,
        val drawnChunkCount: Int,
        val maximumPaintAlpha: Int,
        val maximumStrokeWidthPx: Float,
        val maximumEffectiveAlpha: Float,
        val bodyLayerId: String?,
        val bodyValidSegmentCount: Int,
        val bodyDrawnRunCount: Int,
        val bodyPaintAlpha: Int,
        val bodyStrokeWidthPx: Float,
        val bodyMaximumOriginalAlpha: Float,
        val bodyMaximumFinalAlpha: Float,
        val bodyMaximumAddedAlpha: Float,
        val bodyMaximumEndFade: Float,
    )

    data class FrameMetrics(
        val validSampleCount: Int,
        val drawnChunkCount: Int,
        val drawnPathIds: Set<String>,
        val maximumEffectiveAlpha: Float,
        val paths: Map<String, PathFrameMetrics>,
    )

    private class MutablePathFrameMetrics(
        val spec: OrbFilamentSpecV1,
        val bodyLayerId: String?,
    ) {
        var validSampleCount = 0
        var validBodySegmentCount = 0
        var drawnRunCount = 0
        var separatedInvalidGapCount = 0
        var bridgedInvalidGapCount = 0
        var drawnChunkCount = 0
        var maximumPaintAlpha = 0
        var maximumStrokeWidthPx = 0f
        var maximumEffectiveAlpha = 0f
        var bodyValidSegmentCount = 0
        var bodyDrawnRunCount = 0
        var bodyPaintAlpha = 0
        var bodyStrokeWidthPx = 0f
        var bodyMaximumOriginalAlpha = 0f
        var bodyMaximumFinalAlpha = 0f
        var bodyMaximumAddedAlpha = 0f
        var bodyMaximumEndFade = 0f

        fun snapshot() = PathFrameMetrics(
            pathId = spec.id,
            front = spec.front,
            validSampleCount = validSampleCount,
            validBodySegmentCount = validBodySegmentCount,
            drawnRunCount = drawnRunCount,
            separatedInvalidGapCount = separatedInvalidGapCount,
            bridgedInvalidGapCount = bridgedInvalidGapCount,
            drawnChunkCount = drawnChunkCount,
            maximumPaintAlpha = maximumPaintAlpha,
            maximumStrokeWidthPx = maximumStrokeWidthPx,
            maximumEffectiveAlpha = maximumEffectiveAlpha,
            bodyLayerId = bodyLayerId,
            bodyValidSegmentCount = bodyValidSegmentCount,
            bodyDrawnRunCount = bodyDrawnRunCount,
            bodyPaintAlpha = bodyPaintAlpha,
            bodyStrokeWidthPx = bodyStrokeWidthPx,
            bodyMaximumOriginalAlpha = bodyMaximumOriginalAlpha,
            bodyMaximumFinalAlpha = bodyMaximumFinalAlpha,
            bodyMaximumAddedAlpha = bodyMaximumAddedAlpha,
            bodyMaximumEndFade = bodyMaximumEndFade,
        )
    }

    private data class PathCache(
        val spec: OrbFilamentSpecV1,
        val material: OrbFilamentMaterialV1,
        val bodyLayerId: String?,
        val lut: OrbFilamentPathMathV1.ArcLengthLut,
        val samplesX: FloatArray,
        val samplesY: FloatArray,
        val samplesU: FloatArray,
        val samplesValid: BooleanArray,
        val samplesNormalX: FloatArray,
        val samplesNormalY: FloatArray,
        val scratch: FloatArray,
        val paths: Array<Array<NativePath>>,
        val paints: Array<NativePaint>,
        val blurFilters: Array<BlurMaskFilter?>,
        val glassBlurFilters: Array<BlurMaskFilter?>,
        val headPaint: NativePaint,
        val bandPath: NativePath,
        val bandPaint: NativePaint,
    )

    private val chunkCount = 12
    private val layerCount = 4
    private val loggedMetricSamples = mutableSetOf<String>()
    private var glassShaderDiameterBits = Int.MIN_VALUE
    private var glassShaderCenterXBits = Int.MIN_VALUE
    private var glassShaderCenterYBits = Int.MIN_VALUE
    private val glassMaskShaders = HashMap<Int, RadialGradient>()
    private var glassCoreMaskBitmap: Bitmap? = null
    private var glassCoreMaskPixels: IntArray? = null
    private val caches = OrbRuntimeProfileV1.paths.filter {
        it.front == front && (pathIdFilter == null || it.id == pathIdFilter) && (pathIds == null || it.id in pathIds)
    }.map { spec ->
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
            material.layers.firstOrNull { it.id == "translucent_body" || it.id == "fine_filament" }?.id,
            staticLut,
            FloatArray(97), FloatArray(97), FloatArray(97), BooleanArray(97), FloatArray(97), FloatArray(97),
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
            Array(layerCount) {
                if (renderPass == OrbR06RenderPass.GLASS_TRANSMISSION) {
                    BlurMaskFilter(OrbStage02MaxVisualProfile.GLASS_BACK_SOFTEN_MAX_DP * density, BlurMaskFilter.Blur.NORMAL)
                } else null
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
        visualProfile: OrbStage02VisualProfile = OrbStage02VisualProfile.BASELINE,
        coreMaskBitmap: Bitmap? = null,
    ): FrameMetrics {
        if (diameterPx <= 0f) return FrameMetrics(0, 0, emptySet(), 0f, emptyMap())
        if (renderPass == OrbR06RenderPass.GLASS_TRANSMISSION &&
            (front || visualProfile != OrbStage02VisualProfile.MAX || pathIds.isNullOrEmpty() ||
                pathIds.any { it != "G01" && it != "A01" })
        ) return FrameMetrics(0, 0, emptySet(), 0f, emptyMap())
        val sceneMs = if (frame.reducedMotion) 0L else frame.elapsedMs
        val opticalMs = frame.opticalReferenceElapsedMs
        val steps = if (low) OrbRuntimeProfileV1.R06_LOW_STEPS_PER_PATH else OrbRuntimeProfileV1.R06_NORMAL_STEPS_PER_PATH
        val active = (opening.coerceIn(0f, 1f) * lifeAlpha.coerceIn(0f, 1f) * (1f - settle.coerceIn(0f, 1f))).coerceIn(0f, 1f)
        val diameterDp = diameterPx / density
        val sceneFrame = frame.sceneFrame
        val interiorField = OrbInteriorOpticalField.frame(
            sceneFrame.stage, sceneFrame.stageProgress, frame.reducedMotion, visualProfile,
        )
        if (renderPass == OrbR06RenderPass.GLASS_TRANSMISSION && interiorField.wakeStrength <= 0f) {
            return FrameMetrics(0, 0, emptySet(), 0f, emptyMap())
        }
        if (renderPass == OrbR06RenderPass.GLASS_TRANSMISSION &&
            (diameterPx.toBits() != glassShaderDiameterBits || centerX.toBits() != glassShaderCenterXBits || centerY.toBits() != glassShaderCenterYBits)
        ) {
            glassShaderDiameterBits = diameterPx.toBits()
            glassShaderCenterXBits = centerX.toBits()
            glassShaderCenterYBits = centerY.toBits()
            glassMaskShaders.clear()
        }
        if (renderPass == OrbR06RenderPass.GLASS_TRANSMISSION && coreMaskBitmap != null &&
            (coreMaskBitmap !== glassCoreMaskBitmap || glassCoreMaskPixels == null)
        ) {
            glassCoreMaskBitmap = coreMaskBitmap
            glassCoreMaskPixels = IntArray(coreMaskBitmap.width * coreMaskBitmap.height).also {
                coreMaskBitmap.getPixels(it, 0, coreMaskBitmap.width, 0, 0, coreMaskBitmap.width, coreMaskBitmap.height)
            }
        }
        var validSampleCount = 0
        var drawnChunkCount = 0
        var maximumEffectiveAlpha = 0f
        val drawnPathIds = linkedSetOf<String>()
        val pathMetrics = caches.associate { cache ->
            cache.spec.id to MutablePathFrameMetrics(cache.spec, cache.bodyLayerId)
        }
        val metricsKey = "${frame.sessionId}:${frame.attemptId}:${frame.imageId}:${frame.geometryVersion}:" +
            "${frame.sceneFrame.stage.name}:${if (front) "front" else "back"}:" +
            "${visualProfile.name}:${renderPass.name}"
        fun emitMetricsIfDue() {
            if (!BuildConfig.DEBUG) return
            val progress = frame.sceneFrame.stageProgress.coerceIn(0f, 1f)
            if (frame.sceneFrame.stage != RecognitionSceneStage.ORB_WAKE &&
                frame.sceneFrame.stage != RecognitionSceneStage.ORB_RELEASE
            ) return
            val marker = when {
                abs(progress - .25f) <= .01f -> "25"
                abs(progress - .50f) <= .01f -> "50"
                abs(progress - .75f) <= .01f -> "75"
                abs(progress - 1f) <= .01f -> "100"
                else -> null
            } ?: return
            if (!loggedMetricSamples.add("$metricsKey:$marker")) return
            pathMetrics.values.forEach { metric ->
                Log.i("R06_WAKE_BODY", "stage=${frame.sceneFrame.stage.name} stageProgress=$progress " +
                    "pathId=${metric.spec.id} layerId=${metric.bodyLayerId ?: "NONE"} front=$front " +
                    "originalAlpha=${metric.bodyMaximumOriginalAlpha} finalAlpha=${metric.bodyMaximumFinalAlpha} " +
                    "addedAlpha=${metric.bodyMaximumAddedAlpha} readabilityEnvelope=" +
                    "${OrbWakeBreathReleaseV11.r06WakeReadabilityEnvelope(frame.sceneFrame.stage, progress)} " +
                    "active=$active endFade=${metric.bodyMaximumEndFade} bodySegments=${metric.bodyValidSegmentCount} " +
                    "drawnRuns=${metric.bodyDrawnRunCount} " +
                    "paintAlpha=${metric.bodyPaintAlpha} strokeWidthPx=${metric.bodyStrokeWidthPx} " +
                    "validSamples=${metric.validSampleCount} drawnChunks=${metric.drawnChunkCount}")
            }
            Log.i("R06_RENDER_METRICS", "sessionId=${frame.sessionId} attemptId=${frame.attemptId} " +
                "imageId=${frame.imageId} geometryVersion=${frame.geometryVersion} " +
                "stage=${frame.sceneFrame.stage.name} stageProgress=$progress front=$front " +
                "orbDiameterPx=$diameterPx lifeAlpha=$lifeAlpha opening=$opening settle=$settle active=$active " +
                "pathCount=${caches.size} validSampleCount=$validSampleCount drawnChunkCount=$drawnChunkCount " +
                "drawnPathIds=${drawnPathIds.joinToString(",")} maximumEffectiveAlpha=$maximumEffectiveAlpha")
        }
        if (active <= 0f) {
            emitMetricsIfDue()
            return FrameMetrics(0, 0, emptySet(), 0f, pathMetrics.mapValues { it.value.snapshot() })
        }
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
                val maxStrokeEnvelope = if (visualProfile == OrbStage02VisualProfile.MAX) {
                    OrbWakeBreathReleaseV11.r06WakeReadabilityEnvelope(
                        sceneFrame.stage, sceneFrame.stageProgress,
                    )
                } else 0f
                val strokeGain = 1f +
                    (OrbStage02MaxVisualProfile.R06_BODY_STROKE_GAIN_MAX - 1f) * maxStrokeEnvelope
                val refractionMarginDp = if (renderPass == OrbR06RenderPass.GLASS_TRANSMISSION) {
                    OrbStage02MaxVisualProfile.GLASS_BACK_REFRACTION_MAX_D * diameterDp
                } else 0f
                val strokeMarginDp = spec.widthMaxDp * material.widthMultiplier * material.layers.maxOf { it.widthMultiplier } * strokeGain * .5f
                val glassBlurMarginDp = if (renderPass == OrbR06RenderPass.GLASS_TRANSMISSION && !low) {
                    OrbStage02MaxVisualProfile.GLASS_BACK_SOFTEN_MAX_DP * 3f
                } else 0f
                val maxCenterRadiusDp = minOf(
                    spec.maxOuterRadiusD * diameterDp - strokeMarginDp - refractionMarginDp - glassBlurMarginDp,
                    diameterDp * .5f + OrbWakeBreathReleaseV11.R06_MAX_EXTENSION_DP - strokeMarginDp - refractionMarginDp - glassBlurMarginDp,
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
                val samplesValid = cache.samplesValid
                val samplesNormalX = cache.samplesNormalX
                val samplesNormalY = cache.samplesNormalY
                clearR06SampleValidity(samplesValid, steps)
                var anyPoint = false
                var currentPathValidSampleCount = 0
                for (i in 0..steps) {
                    val tailProgress = i.toFloat() / steps
                    val u = startU + move.direction * trail * tailProgress
                    if (!u.isFinite() || u !in 0f..1f) continue
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
                    if (samplesX[i].isFinite() && samplesY[i].isFinite() && nx.isFinite() && ny.isFinite()) {
                        samplesValid[i] = true
                        validSampleCount++
                        currentPathValidSampleCount++
                    }
                    anyPoint = true
                }
                if (!anyPoint) continue
                pathMetrics.getValue(spec.id).validSampleCount = currentPathValidSampleCount
                val layerCountForMaterial = material.layers.size.coerceAtMost(layerCount)
                for (layerIndex in 0 until layerCountForMaterial) {
                    val layer = material.layers[layerIndex]
                    val isBodyLayer = layer.id == "translucent_body" || layer.id == "fine_filament"
                    if (renderPass == OrbR06RenderPass.GLASS_TRANSMISSION && !isBodyLayer) continue
                    val paint = cache.paints[layerIndex]
                    val colorInt = layer.color.toInt() or (0xFF shl 24)
                    paint.color = colorInt
                    paint.maskFilter = when {
                        renderPass == OrbR06RenderPass.GLASS_TRANSMISSION && !low -> cache.glassBlurFilters[layerIndex]
                        layer.blurSigmaDp != null && !low -> cache.blurFilters[layerIndex]
                        else -> null
                    }
                    val chunkSize = steps.toFloat() / chunkCount
                    for (chunk in 0 until chunkCount) {
                        val first = (chunk * chunkSize).toInt().coerceAtMost(steps)
                        val last = ((chunk + 1) * chunkSize).toInt().coerceAtMost(steps)
                        if (first >= last) continue
                        // A cache chunk can straddle the path boundary or a clipped interval.
                        // Derive every value from this frame's valid runs so no stale midpoint
                        // coordinate or u value can suppress/bridge the visible part of a path.
                        val drawableRuns = r06ValidSampleRuns(samplesValid, first, last)
                            .filter { it.last - it.first + 1 >= 2 }
                        if (drawableRuns.isEmpty()) continue
                        val representativeRun = drawableRuns.maxBy { it.last - it.first }
                        val mid = (representativeRun.first + representativeRun.last) / 2
                        val pathU = samplesU[mid]
                        val tailAlpha = tailTaper((mid.toFloat() / steps), material.tailLuminance)
                        val profileWidth = OrbFilamentPathMathV1.profileAt(material.widthProfile, pathU)
                        val profileOpacity = OrbFilamentPathMathV1.profileAt(material.opacityProfile, pathU)
                        val crossOpacity = OrbFilamentPathMathV1.profileAt(material.crossSection, layer.offsetNormalFraction ?: 0f)
                        val longitudinal = material.tailLuminance + (material.bodyLuminance - material.tailLuminance) * (mid.toFloat() / steps)
                        val headBoost = if (mid >= steps - max(1, (steps * material.specularBandLengthFraction).toInt())) {
                            material.headLuminance
                        } else 1f
                        val originalLayerAlpha = (active * alphaPulse * profileOpacity * crossOpacity * tailAlpha * longitudinal *
                            headBoost * eventBrightness * layer.alphaMax * endFade * OrbWakeBreathReleaseV11.R06_ALPHA_GAIN)
                            .coerceIn(0f, OrbWakeBreathReleaseV11.R06_ALPHA_CAP)
                        var layerAlpha = OrbWakeBreathReleaseV11.r06WakeBodyAlpha(
                            stage = sceneFrame.stage,
                            stageProgress = sceneFrame.stageProgress,
                            originalLayerAlpha = originalLayerAlpha,
                            layerId = layer.id,
                            front = front,
                            active = active,
                            endFade = endFade,
                            tailAlpha = tailAlpha,
                            profileOpacity = profileOpacity,
                            crossOpacity = crossOpacity,
                        )
                        if (visualProfile == OrbStage02VisualProfile.MAX && isBodyLayer) {
                            layerAlpha = OrbWakeBreathReleaseV11.r06MaxBodyAlpha(
                                stage = sceneFrame.stage,
                                stageProgress = sceneFrame.stageProgress,
                                originalAlpha = layerAlpha,
                                active = active,
                                endFade = endFade,
                                tailAlpha = tailAlpha,
                                profileOpacity = profileOpacity,
                                crossOpacity = crossOpacity,
                                front = front,
                            )
                        }
                        val representativeRadiusD = kotlin.math.hypot(samplesX[mid] - centerX, samplesY[mid] - centerY) / diameterPx
                        if (renderPass == OrbR06RenderPass.GLASS_TRANSMISSION) {
                            val radialMask = OrbInteriorOpticalField.interiorWeight(representativeRadiusD)
                            val rawCoreAlpha = rawCoreAlphaAt(
                                samplesX[mid], samplesY[mid], centerX, centerY, diameterPx,
                            )
                            // The original WebP alpha, radial interior and edge fade all gate transmission.
                            layerAlpha *= OrbStage02MaxVisualProfile.GLASS_BACK_TRANSMISSION_MAX *
                                rawCoreAlpha * radialMask * radialMask
                        }
                        if (isBodyLayer) {
                            pathMetrics.getValue(spec.id).apply {
                                bodyMaximumOriginalAlpha = max(bodyMaximumOriginalAlpha, originalLayerAlpha)
                                bodyMaximumFinalAlpha = max(bodyMaximumFinalAlpha, layerAlpha)
                                bodyMaximumAddedAlpha = max(
                                    bodyMaximumAddedAlpha,
                                    (layerAlpha - originalLayerAlpha).coerceAtLeast(0f),
                                )
                                bodyMaximumEndFade = max(bodyMaximumEndFade, endFade)
                            }
                        }
                        if (layerAlpha <= .002f) continue
                        val path = cache.paths[chunk][layerIndex]
                        path.rewind()
                        var drawableRunCount = 0
                        for (run in drawableRuns) {
                            var began = false
                            for (i in run) {
                                val offsetFraction = layer.offsetNormalFraction ?: 0f
                                var x = samplesX[i] + samplesNormalX[i] * offsetFraction * diameterPx * .003f
                                var y = samplesY[i] + samplesNormalY[i] * offsetFraction * diameterPx * .003f
                                if (renderPass == OrbR06RenderPass.GLASS_TRANSMISSION) {
                                    val u = (x - centerX) / diameterPx + .5f
                                    val v = (y - centerY) / diameterPx + .5f
                                    val localWeight = OrbInteriorOpticalField.interiorWeight(
                                        kotlin.math.hypot(x - centerX, y - centerY) / diameterPx,
                                    ) * interiorField.wakeStrength
                                    OrbInteriorOpticalField.writeSharedFlowDirectionAt(
                                        u, v, interiorField.semanticPhase, sceneMs, cache.scratch, 4,
                                    )
                                    x += cache.scratch[4] * diameterPx *
                                        OrbStage02MaxVisualProfile.GLASS_BACK_REFRACTION_MAX_D * localWeight
                                    y += cache.scratch[5] * diameterPx *
                                        OrbStage02MaxVisualProfile.GLASS_BACK_REFRACTION_MAX_D * localWeight
                                }
                                if (!x.isFinite() || !y.isFinite()) { began = false; continue }
                                if (!began) { path.moveTo(x, y); began = true } else path.lineTo(x, y)
                            }
                            if (began) drawableRunCount++
                        }
                        if (drawableRunCount == 0 || path.isEmpty) continue
                        paint.alpha = (255f * layerAlpha).toInt().coerceIn(0, 255)
                        paint.strokeWidth = spec.widthMinDp + (spec.widthMaxDp - spec.widthMinDp) * widthPulse
                        paint.strokeWidth *= material.widthMultiplier * profileWidth * layer.widthMultiplier * density
                        if (isBodyLayer) paint.strokeWidth *= strokeGain
                        if (renderPass == OrbR06RenderPass.GLASS_TRANSMISSION) {
                            val alphaStop = OrbStage02MaxVisualProfile.FLUID_EDGE_PROTECTION_START_D /
                                OrbStage02MaxVisualProfile.FLUID_EDGE_PROTECTION_END_D
                            val clearColor = colorInt and 0x00FFFFFF
                            paint.shader = glassMaskShaders.getOrPut(colorInt) {
                                RadialGradient(
                                    centerX,
                                    centerY,
                                    diameterPx * OrbStage02MaxVisualProfile.FLUID_EDGE_PROTECTION_END_D,
                                    intArrayOf(colorInt, colorInt, clearColor),
                                    floatArrayOf(0f, alphaStop, 1f),
                                    Shader.TileMode.CLAMP,
                                )
                            }
                        } else paint.shader = null
                        if (paint.alpha <= 0 || paint.strokeWidth <= 0f) continue
                        canvas.drawPath(path, paint)
                        maximumEffectiveAlpha = max(maximumEffectiveAlpha, layerAlpha)
                        drawnChunkCount++
                        drawnPathIds += spec.id
                        pathMetrics.getValue(spec.id).apply {
                            validBodySegmentCount += drawableRuns.sumOf { (it.last - it.first).coerceAtLeast(0) }
                            drawnRunCount += drawableRuns.size
                            separatedInvalidGapCount += (drawableRuns.size - 1).coerceAtLeast(0)
                            if (drawableRunCount != drawableRuns.size) bridgedInvalidGapCount++
                            drawnChunkCount++
                            maximumPaintAlpha = max(maximumPaintAlpha, paint.alpha)
                            maximumStrokeWidthPx = max(maximumStrokeWidthPx, paint.strokeWidth)
                            maximumEffectiveAlpha = max(this.maximumEffectiveAlpha, layerAlpha)
                            if (isBodyLayer) {
                                bodyDrawnRunCount += drawableRunCount
                                bodyValidSegmentCount += drawableRuns.sumOf {
                                    (it.last - it.first).coerceAtLeast(0)
                                }
                                bodyPaintAlpha = max(bodyPaintAlpha, paint.alpha)
                                bodyStrokeWidthPx = max(bodyStrokeWidthPx, paint.strokeWidth)
                            }
                        }
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
            emitMetricsIfDue()
        }
        return FrameMetrics(
            validSampleCount = validSampleCount,
            drawnChunkCount = drawnChunkCount,
            drawnPathIds = drawnPathIds,
            maximumEffectiveAlpha = maximumEffectiveAlpha,
            paths = pathMetrics.mapValues { it.value.snapshot() },
        )
    }

    private fun tailTaper(progress: Float, tailLuminance: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        val fade = (t / .18f).coerceIn(0f, 1f)
        val smooth = fade * fade * (3f - 2f * fade)
        return tailLuminance + (1f - tailLuminance) * smooth
    }

    private fun rawCoreAlphaAt(x: Float, y: Float, centerX: Float, centerY: Float, visibleDiameterPx: Float): Float {
        val bitmap = glassCoreMaskBitmap ?: return 0f
        val pixels = glassCoreMaskPixels ?: return 0f
        val imageSizePx = visibleDiameterPx / OrbGrassMotionProfileV11.VISIBLE_DIAMETER_TO_SOURCE_UV
        val left = centerX - imageSizePx * .5f
        val top = centerY - imageSizePx * .5f
        val bx = (((x - left) / imageSizePx) * bitmap.width).toInt()
        val by = (((y - top) / imageSizePx) * bitmap.height).toInt()
        if (bx !in 0 until bitmap.width || by !in 0 until bitmap.height) return 0f
        return (pixels[by * bitmap.width + bx] ushr 24 and 0xFF) / 255f
    }
}

/** Current-frame contiguous runs; gaps are never bridged by samples left in a reused cache. */
internal fun r06ValidSampleRuns(valid: BooleanArray, first: Int, last: Int): List<IntRange> {
    if (valid.isEmpty() || first > last) return emptyList()
    val start = first.coerceAtLeast(0)
    val end = last.coerceAtMost(valid.lastIndex)
    if (start > end) return emptyList()
    val runs = ArrayList<IntRange>()
    var runStart = -1
    for (i in start..end) {
        if (valid[i]) {
            if (runStart < 0) runStart = i
        } else if (runStart >= 0) {
            runs += runStart until i
            runStart = -1
        }
    }
    if (runStart >= 0) runs += runStart..end
    return runs
}

internal fun clearR06SampleValidity(valid: BooleanArray, steps: Int) {
    java.util.Arrays.fill(valid, 0, (steps + 1).coerceIn(0, valid.size), false)
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
