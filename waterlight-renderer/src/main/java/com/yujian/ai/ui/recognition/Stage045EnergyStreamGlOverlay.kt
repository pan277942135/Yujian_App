package com.yujian.ai.ui.recognition

import android.opengl.GLES20
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yujian.ai.ui.identify.DisplayRect
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometry
import com.yujian.ai.ui.waterlight.core.WaterLightPathPoint
import com.yujian.ai.ui.waterlight.renderer.GL_FLOAT_BYTES
import com.yujian.ai.ui.waterlight.renderer.GlBloomPipeline
import com.yujian.ai.ui.waterlight.renderer.GlSceneRenderer
import com.yujian.ai.ui.waterlight.renderer.GlShaderUtil
import com.yujian.ai.ui.waterlight.renderer.TransparentGlTextureView
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal data class Stage045GlFrame(
    val viewportWidthPx: Float,
    val viewportHeightPx: Float,
    val density: Float,
    val elapsedMs: Long,
    val stage: RecognitionSceneStage,
    val stageProgress: Float,
    val orbCenter: Offset,
    val orbRadiusPx: Float,
    val photoRect: DisplayRect,
    val cornerRadiusPx: Float,
    val releasePath: WaterLightPathGeometry?,
    val arrivalPath: WaterLightPathGeometry?,
    val fishPath: WaterLightPathGeometry?,
    val releaseProgress: Float,
    val arrivalProgress: Float,
    val perimeterCoverage: Float,
    val fishSeekProgress: Float,
    val fishArrivalPulse: Float,
    val globalOpacity: Float,
    val lowPerformance: Boolean,
    val reducedMotion: Boolean,
)

/**
 * Unified GLES20 + 1/4-Res Bloom overlay for Stage 04-05 (KF01 -> KF02 -> KF03 -> KF04):
 * - KF01: Continuous upward mist plume + twin cyan/gold ribbons from Orb to Photo bottom contact + contact flare
 * - KF02: Unclipped overflowing liquid-glass perimeter spreading from contact point around photo + droplets + starbursts
 * - KF03: Continuous Orb output + continuous Perimeter circulation + natural twin-strand bifurcation seeking P*
 * - KF04: Arrival flare & contact ripple at fish body P* while Orb & Perimeter remain continuously active
 */
@Composable
fun Stage045EnergyStreamGlOverlay(
    waterLightFrame: WaterLightFrame,
    orbCenter: Offset,
    orbRadiusPx: Float,
    photoRect: DisplayRect,
    cornerRadiusPx: Float,
    releasePath: WaterLightPathGeometry?,
    arrivalPath: WaterLightPathGeometry?,
    fishPath: WaterLightPathGeometry?,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    globalOpacity: Float,
    lowPerformance: Boolean,
    onFirstFramePresented: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scene = waterLightFrame.sceneFrame
    if (!scene.isIdentityValid || globalOpacity <= 0.001f || viewportWidthPx <= 1f || viewportHeightPx <= 1f) return
    val stage = scene.stage
    if (stage == RecognitionSceneStage.PHOTO_STABLE || stage == RecognitionSceneStage.ORB_WAKE) return
    // A missing measured Orb-to-photo path is a real gate failure, not a cue to
    // synthesize an unbound contact point in this renderer.
    if (releasePath == null || !releasePath.isValid()) return

    val context = LocalContext.current
    val density = LocalDensity.current.density
    val lifecycleOwner = LocalLifecycleOwner.current
    val renderer = remember { Stage045GlRenderer() }
    val textureView = remember(context) {
        TransparentGlTextureView(
            context = context,
            threadName = "Stage045EnergyGlThread",
            tag = "Stage045EnergyGl",
            renderer = renderer,
            onFirstFramePresented = onFirstFramePresented,
        )
    }

    val p = scene.stageProgress.coerceIn(0f, 1f)
    val reduce = waterLightFrame.reducedMotion

    // Continuous non-disappearing progress curves across KF01 -> KF02 -> KF03 -> KF04
    val releaseProgress = when {
        reduce -> 1f
        stage == RecognitionSceneStage.ORB_RELEASE -> smoothstep(p / 0.58f)
        stage.ordinal in RecognitionSceneStage.PERIMETER_ARRIVAL.ordinal..RecognitionSceneStage.FISH_GOLD_PEAK.ordinal -> 1f
        stage == RecognitionSceneStage.ORB_RETURN -> 1f - smoothstep(p)
        else -> 0f
    }

    val perimeterCoverage = when {
        reduce -> 1f
        stage == RecognitionSceneStage.ORB_RELEASE -> smoothstep((p - 0.42f) / 0.58f) * 0.62f
        stage == RecognitionSceneStage.PERIMETER_ARRIVAL -> 0.62f + 0.38f * smoothstep(p)
        stage.ordinal in RecognitionSceneStage.PATH_TO_FISH.ordinal..RecognitionSceneStage.FISH_GOLD_PEAK.ordinal -> 1f
        stage == RecognitionSceneStage.ORB_RETURN -> 1f - 0.72f * smoothstep(p)
        else -> 0f
    }

    // The Stage-04 route continues from RELEASE's endpoint to the measured
    // perimeter split. Keep it on the same PathLedger geometry; this is not a
    // synthesized bridge and it becomes the exact start of the fish search.
    val arrivalProgress = when {
        reduce -> if (arrivalPath != null) 1f else 0f
        arrivalPath == null -> 0f
        stage == RecognitionSceneStage.PERIMETER_ARRIVAL -> smoothstep(p)
        stage.ordinal in RecognitionSceneStage.PATH_TO_FISH.ordinal..RecognitionSceneStage.FISH_GOLD_PEAK.ordinal -> 1f
        stage == RecognitionSceneStage.ORB_RETURN -> 1f - smoothstep(p)
        else -> 0f
    }

    val fishSeekProgress = when {
        fishPath == null -> 0f
        reduce -> 1f
        stage == RecognitionSceneStage.PERIMETER_ARRIVAL -> 0.12f * smoothstep((p - 0.45f) / 0.55f)
        stage == RecognitionSceneStage.PATH_TO_FISH -> 0.12f + 0.58f * smoothstep(p)
        stage == RecognitionSceneStage.CONTOUR_A -> 0.70f + 0.30f * smoothstep(p / 0.45f)
        stage.ordinal in RecognitionSceneStage.CONTOUR_B.ordinal..RecognitionSceneStage.FISH_GOLD_PEAK.ordinal -> 1f
        stage == RecognitionSceneStage.ORB_RETURN -> 1f - smoothstep(p)
        else -> 0f
    }

    val fishArrivalPulse = when {
        fishPath == null -> 0f
        stage == RecognitionSceneStage.CONTOUR_A -> smoothstep((p - 0.35f) / 0.65f)
        stage.ordinal in RecognitionSceneStage.CONTOUR_B.ordinal..RecognitionSceneStage.FISH_GOLD_PEAK.ordinal -> 1f
        stage == RecognitionSceneStage.ORB_RETURN -> 1f - smoothstep(p)
        else -> 0f
    }

    val stageFade = when (stage) {
        RecognitionSceneStage.ORB_TO_NAME -> 1f - smoothstep(p)
        RecognitionSceneStage.SPECIES_NAME_REVEAL,
        RecognitionSceneStage.RESIDUE_SETTLE -> 0f
        else -> 1f
    }

    val effectiveOpacity = (globalOpacity * stageFade).coerceIn(0f, 1f)
    if (effectiveOpacity <= 0.001f) return

    val glFrame = Stage045GlFrame(
        viewportWidthPx = viewportWidthPx,
        viewportHeightPx = viewportHeightPx,
        density = density,
        elapsedMs = if (reduce) 0L else waterLightFrame.elapsedMs.coerceAtLeast(0L),
        stage = stage,
        stageProgress = p,
        orbCenter = orbCenter,
        orbRadiusPx = orbRadiusPx,
        photoRect = photoRect,
        cornerRadiusPx = cornerRadiusPx,
        releasePath = releasePath,
        arrivalPath = arrivalPath,
        fishPath = fishPath,
        releaseProgress = releaseProgress,
        arrivalProgress = arrivalProgress,
        perimeterCoverage = perimeterCoverage,
        fishSeekProgress = fishSeekProgress,
        fishArrivalPulse = fishArrivalPulse,
        globalOpacity = effectiveOpacity,
        lowPerformance = lowPerformance,
        reducedMotion = reduce,
    )

    AndroidView(
        factory = { textureView },
        modifier = modifier,
        update = { view ->
            view.visibility = View.VISIBLE
            view.submit(glFrame)
        },
    )

    DisposableEffect(textureView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> textureView.setHostResumed(true)
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> textureView.setHostResumed(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        textureView.setHostResumed(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            textureView.release()
        }
    }
}

private fun smoothstep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private class Stage045GlRenderer : GlSceneRenderer<Stage045GlFrame> {
    private val bloom = GlBloomPipeline()
    private var quadVbo = 0
    private var ribbonVbo = 0
    private var spriteVbo = 0
    private var ribbonBuf: FloatBuffer? = null
    private var spriteBuf: FloatBuffer? = null

    private var surfaceW = 1
    private var surfaceH = 1
    private var fboW = 1
    private var fboH = 1

    private var perimProg = 0
    private var streamProg = 0
    private var starProg = 0
    private var dropletProg = 0

    // Perimeter & backdrop plume uniform locations
    private var uPerimFboSize = -1
    private var uPerimViewport = -1
    private var uPerimPhoto = -1
    private var uPerimOrb = -1
    private var uPerimContact = -1
    private var uPerimParams = -1 // x=time, y=cornerR, z=perimCoverage, w=releaseProg
    private var uPerimStage = -1 // x=fishSeek, y=fishArrivePulse, z=density, w=opacity
    private var uPerimFishTarget = -1

    // Twin-strand ribbon uniform locations
    private var uStreamTime = -1
    private var uStreamAlpha = -1

    // Starburst & droplet uniform locations
    private var uStarTime = -1
    private var uStarAlpha = -1
    private var uDropTime = -1
    private var uDropAlpha = -1

    override fun onSurfaceCreated() {
        quadVbo = GlShaderUtil.createQuadVbo()
        val ids = IntArray(2)
        GLES20.glGenBuffers(2, ids, 0)
        ribbonVbo = ids[0]
        spriteVbo = ids[1]

        bloom.init()
        perimProg = GlShaderUtil.createProgram(QUAD_VS, PERIMETER_AND_PLUME_FS, "s045-perim")
        streamProg = GlShaderUtil.createProgram(RIBBON_VS, STREAM_RIBBON_FS, "s045-stream")
        starProg = GlShaderUtil.createProgram(SPRITE_VS, STAR_FS, "s045-star")
        dropletProg = GlShaderUtil.createProgram(SPRITE_VS, DROPLET_FS, "s045-drop")

        if (perimProg != 0) {
            uPerimFboSize = GLES20.glGetUniformLocation(perimProg, "uFboSize")
            uPerimViewport = GLES20.glGetUniformLocation(perimProg, "uViewport")
            uPerimPhoto = GLES20.glGetUniformLocation(perimProg, "uPhotoRect")
            uPerimOrb = GLES20.glGetUniformLocation(perimProg, "uOrb")
            uPerimContact = GLES20.glGetUniformLocation(perimProg, "uContact")
            uPerimParams = GLES20.glGetUniformLocation(perimProg, "uParams")
            uPerimStage = GLES20.glGetUniformLocation(perimProg, "uStage")
            uPerimFishTarget = GLES20.glGetUniformLocation(perimProg, "uFishTarget")
        }
        if (streamProg != 0) {
            uStreamTime = GLES20.glGetUniformLocation(streamProg, "uTime")
            uStreamAlpha = GLES20.glGetUniformLocation(streamProg, "uAlpha")
        }
        if (starProg != 0) {
            uStarTime = GLES20.glGetUniformLocation(starProg, "uTime")
            uStarAlpha = GLES20.glGetUniformLocation(starProg, "uAlpha")
        }
        if (dropletProg != 0) {
            uDropTime = GLES20.glGetUniformLocation(dropletProg, "uTime")
            uDropAlpha = GLES20.glGetUniformLocation(dropletProg, "uAlpha")
        }
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        surfaceW = width.coerceAtLeast(1)
        surfaceH = height.coerceAtLeast(1)
    }

    override fun onDrawFrame(frame: Stage045GlFrame): Boolean {
        if (perimProg == 0 || streamProg == 0) return false

        // 0.70x max 840px internal FBO keeps full-screen Bloom ultra fast & smooth across emulators & phones
        val maxDim = if (frame.lowPerformance) 560 else 860
        val scale = minOf(0.85f, maxDim.toFloat() / maxOf(surfaceW, surfaceH).toFloat())
        val targetW = (surfaceW * scale).toInt().coerceAtLeast(64)
        val targetH = (surfaceH * scale).toInt().coerceAtLeast(64)
        if (!bloom.ensureSize(targetW, targetH)) return false
        fboW = targetW
        fboH = targetH

        val timeSec = frame.elapsedMs / 1000f
        val vpW = frame.viewportWidthPx.coerceAtLeast(1f)
        val vpH = frame.viewportHeightPx.coerceAtLeast(1f)
        val contactPt = frame.releasePath?.end ?: WaterLightPathPoint(frame.orbCenter.x, frame.photoRect.bottom)
        val fishTargetPt = frame.fishPath?.end ?: contactPt

        // =========================================================================
        // PASS 1: Render into sceneFbo (Upward Plume + Overflowing Liquid Perimeter + Twin Ribbons + Starbursts + Droplets)
        // =========================================================================
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, bloom.sceneFboId)
        GLES20.glViewport(0, 0, fboW, fboH)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)

        // 1a. Full-screen SDF Perimeter Waves + Upward Orb Plume + Peripheral Ambient Bokeh + Contact Glow
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(perimProg)
        GLES20.glUniform2f(uPerimFboSize, fboW.toFloat(), fboH.toFloat())
        GLES20.glUniform2f(uPerimViewport, vpW, vpH)
        GLES20.glUniform4f(
            uPerimPhoto,
            frame.photoRect.left,
            frame.photoRect.top,
            frame.photoRect.width,
            frame.photoRect.height,
        )
        GLES20.glUniform3f(uPerimOrb, frame.orbCenter.x, frame.orbCenter.y, frame.orbRadiusPx)
        GLES20.glUniform2f(uPerimContact, contactPt.x, contactPt.y)
        GLES20.glUniform4f(
            uPerimParams,
            timeSec,
            frame.cornerRadiusPx,
            frame.perimeterCoverage,
            frame.releaseProgress,
        )
        GLES20.glUniform4f(
            uPerimStage,
            frame.fishSeekProgress,
            frame.fishArrivalPulse,
            frame.density,
            1f,
        )
        GLES20.glUniform2f(uPerimFishTarget, fishTargetPt.x, fishTargetPt.y)
        drawQuad(perimProg)

        // 1b. Twin-Strand Intertwined Cyan/Gold Ribbons (Orb->Photo persistent stream + Perimeter->Fish bifurcation stream)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFuncSeparate(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        val streamVerts = buildTwinStreamVertices(frame, timeSec, vpW, vpH)
        if (streamVerts.isNotEmpty()) {
            drawStripBatch(streamProg, uStreamTime, uStreamAlpha, streamVerts, timeSec, 1f)
        }

        // 1c. Floating Micro-Droplets around the Photo Perimeter & along the Seeking Stream
        if (dropletProg != 0 && frame.perimeterCoverage > 0.05f) {
            val dropVerts = buildPerimeterDroplets(frame, timeSec, vpW, vpH)
            if (dropVerts.isNotEmpty()) {
                drawSpriteBatch(dropletProg, uDropTime, uDropAlpha, dropVerts, timeSec, 1f)
            }
        }

        // 1d. Additive 4-Point Starburst Flares (Contact Point + Orbiting Perimeter Crests + Fish Arrival Head)
        if (starProg != 0) {
            GLES20.glBlendFuncSeparate(GLES20.GL_ONE, GLES20.GL_ONE, GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            val starVerts = buildStarbursts(frame, timeSec, contactPt, vpW, vpH)
            if (starVerts.isNotEmpty()) {
                drawSpriteBatch(starProg, uStarTime, uStarAlpha, starVerts, timeSec, 1f)
            }
        }

        // =========================================================================
        // PASS 2 & 3: 1/4-Res Bloom Blur + Premultiplied Composite to TextureView
        // =========================================================================
        bloom.renderBloomAndComposite(
            quadVbo = quadVbo,
            threshold = 0.48f,
            bloomIntensity = 0.68f,
            globalOpacity = frame.globalOpacity,
            skipBlur = frame.lowPerformance,
        )

        GLES20.glViewport(0, 0, surfaceW, surfaceH)
        return true
    }

    private fun buildTwinStreamVertices(
        frame: Stage045GlFrame,
        timeSec: Float,
        vpW: Float,
        vpH: Float,
    ): FloatArray {
        val out = ArrayList<Float>(1024)
        val d = frame.density

        // 1) Persistent Orb-to-Photo Twin-Strand Stream (KF01 -> active continuously through KF04)
        frame.releasePath?.let { rel ->
            if (frame.releaseProgress > 0.01f) {
                emitBraidedRibbons(
                    out = out,
                    geometry = rel,
                    progress = frame.releaseProgress,
                    fadeAlpha = if (frame.stage == RecognitionSceneStage.ORB_RELEASE) 0.95f else 0.78f,
                    baseHalfWidthPx = 9.5f * d,
                    braidAmpPx = 11.0f * d,
                    timeSec = timeSec,
                    vpW = vpW,
                    vpH = vpH,
                    phaseSeed = 0.4f,
                )
            }
        }

        // The first and last points are the RELEASE endpoint and measured
        // Task split, respectively. Drawing the actual segment preserves
        // release.end == arrival.start == fishPath.start across stage changes.
        frame.arrivalPath?.let { arrival ->
            if (frame.arrivalProgress > 0.01f) {
                emitBraidedRibbons(
                    out = out,
                    geometry = arrival,
                    progress = frame.arrivalProgress,
                    fadeAlpha = if (frame.stage == RecognitionSceneStage.PERIMETER_ARRIVAL) 0.98f else 0.82f,
                    baseHalfWidthPx = 8.5f * d,
                    braidAmpPx = 9.0f * d,
                    timeSec = timeSec,
                    vpW = vpW,
                    vpH = vpH,
                    phaseSeed = 1.15f,
                )
            }
        }

        // 2) Seeking Bifurcation Stream: Perimeter -> Fish Contact P* (KF03 -> KF04)
        val seekGeo = frame.fishPath
        if (seekGeo != null && frame.fishSeekProgress > 0.01f) {
            emitBraidedRibbons(
                out = out,
                geometry = seekGeo,
                progress = frame.fishSeekProgress,
                fadeAlpha = 1.0f,
                baseHalfWidthPx = 11.5f * d,
                braidAmpPx = 14.5f * d,
                timeSec = timeSec,
                vpW = vpW,
                vpH = vpH,
                phaseSeed = 2.1f,
            )
        }
        return out.toFloatArray()
    }

    /**
     * Generates 3 intertwined fluid ribbons (1 luminous cyan glass ribbon, 1 warm champagne-gold ribbon,
     * 1 slender bright inner wisp) with smooth variable width tapering to wispy ends.
     */
    private fun emitBraidedRibbons(
        out: MutableList<Float>,
        geometry: WaterLightPathGeometry,
        progress: Float,
        fadeAlpha: Float,
        baseHalfWidthPx: Float,
        braidAmpPx: Float,
        timeSec: Float,
        vpW: Float,
        vpH: Float,
        phaseSeed: Float,
    ) {
        data class Strand(val colorMix: Float, val phase: Float, val freq: Float, val widthScale: Float, val ampScale: Float)
        val strands = listOf(
            Strand(0.05f, 0.0f + phaseSeed, 1.5f, 1.05f, 1.0f),  // Electric Cyan
            Strand(0.95f, 3.14f + phaseSeed, 1.5f, 0.92f, 1.0f), // Champagne Gold
            Strand(0.28f, 1.57f + phaseSeed, 2.3f, 0.58f, 0.65f), // Slender Aqua Filament
        )
        val steps = 44
        val activeMaxT = progress.coerceIn(0f, 1f)
        val px = FloatArray(steps + 1)
        val py = FloatArray(steps + 1)

        for (strand in strands) {
            for (i in 0..steps) {
                val u = i.toFloat() / steps
                val t = u * activeMaxT
                val inv = 1f - t
                val bx = inv * inv * inv * geometry.start.x +
                    3f * inv * inv * t * geometry.control1.x +
                    3f * inv * t * t * geometry.control2.x +
                    t * t * t * geometry.end.x
                val by = inv * inv * inv * geometry.start.y +
                    3f * inv * inv * t * geometry.control1.y +
                    3f * inv * t * t * geometry.control2.y +
                    t * t * t * geometry.end.y

                // Analytic tangent for braid normal offset
                val tx = 3f * inv * inv * (geometry.control1.x - geometry.start.x) +
                    6f * inv * t * (geometry.control2.x - geometry.control1.x) +
                    3f * t * t * (geometry.end.x - geometry.control2.x)
                val ty = 3f * inv * inv * (geometry.control1.y - geometry.start.y) +
                    6f * inv * t * (geometry.control2.y - geometry.control1.y) +
                    3f * t * t * (geometry.end.y - geometry.control2.y)
                val tLen = hypot(tx.toDouble(), ty.toDouble()).toFloat().coerceAtLeast(1e-4f)
                val nx = -ty / tLen
                val ny = tx / tLen

                val env = sin(u * PI.toFloat()).coerceAtLeast(0f)
                val offset = sin(t * 2f * PI.toFloat() * strand.freq - timeSec * 4.2f + strand.phase) *
                    braidAmpPx * strand.ampScale * sqrt(env)
                px[i] = bx + nx * offset
                py[i] = by + ny * offset
            }

            for (i in 0..steps) {
                val u = i.toFloat() / steps
                val prev = (i - 1).coerceAtLeast(0)
                val next = (i + 1).coerceAtMost(steps)
                val tx = px[next] - px[prev]
                val ty = py[next] - py[prev]
                val len = hypot(tx.toDouble(), ty.toDouble()).toFloat().coerceAtLeast(1e-4f)
                val nx = -ty / len
                val ny = tx / len

                // Tapered ribbon width: broader fluid belly toward active head, wispy tail
                val taper = (0.28f + 0.72f * sin(u * PI.toFloat()).coerceAtLeast(0f)) *
                    (0.55f + 0.65f * u)
                val hw = baseHalfWidthPx * strand.widthScale * taper

                val lNdcX = ((px[i] - nx * hw) / vpW) * 2f - 1f
                val lNdcY = 1f - ((py[i] - ny * hw) / vpH) * 2f
                val rNdcX = ((px[i] + nx * hw) / vpW) * 2f - 1f
                val rNdcY = 1f - ((py[i] + ny * hw) / vpH) * 2f

                if (i == 0 && out.isNotEmpty()) {
                    val last = out.size - 8
                    for (k in 0 until 8) out.add(out[last + k])
                    emitVertex(out, lNdcX, lNdcY, u, -1f, strand.colorMix, fadeAlpha, strand.phase, activeMaxT)
                }
                emitVertex(out, lNdcX, lNdcY, u, -1f, strand.colorMix, fadeAlpha, strand.phase, activeMaxT)
                emitVertex(out, rNdcX, rNdcY, u, 1f, strand.colorMix, fadeAlpha, strand.phase, activeMaxT)
            }
        }
    }

    private fun buildPerimeterDroplets(
        frame: Stage045GlFrame,
        timeSec: Float,
        vpW: Float,
        vpH: Float,
    ): FloatArray {
        val count = 12
        val rect = frame.photoRect
        val d = frame.density
        val perim = 2f * (rect.width + rect.height)
        val out = ArrayList<Float>(count * 6 * 8)

        for (i in 0 until count) {
            val fi = i.toFloat()
            val s = ((i / count.toFloat()) + timeSec * (0.015f + (i % 3) * 0.005f)) % 1f
            // Reveal symmetrically from bottom center (s ~ 0.5)
            val distFromBottomCenter = kotlin.math.abs(s - 0.5f) * 2f
            if (distFromBottomCenter > frame.perimeterCoverage) continue
            val pt = pointOnPerimeter(rect, s * perim)
            // Float gently outside the photo perimeter!
            val cxPhoto = rect.center.x
            val cyPhoto = rect.center.y
            val ox = pt.x - cxPhoto
            val oy = pt.y - cyPhoto
            val oLen = hypot(ox.toDouble(), oy.toDouble()).toFloat().coerceAtLeast(1f)
            val outDist = (6f + (i % 4) * 5.5f + 3f * sin(timeSec * 1.9f + fi)) * d
            val px = pt.x + (ox / oLen) * outDist
            val py = pt.y + (oy / oLen) * outDist
            val sizePx = (3.4f + (i % 3) * 1.6f) * d
            emitSpriteQuadNdc(
                out = out,
                cx = px,
                cy = py,
                halfSizePx = sizePx,
                vpW = vpW,
                vpH = vpH,
                colorMix = if (i % 2 == 0) 0.1f else 0.85f,
                intensity = 0.88f,
                seed = fi,
            )
        }
        return out.toFloatArray()
    }

    private fun buildStarbursts(
        frame: Stage045GlFrame,
        timeSec: Float,
        contactPt: WaterLightPathPoint,
        vpW: Float,
        vpH: Float,
    ): FloatArray {
        val out = ArrayList<Float>(16 * 6 * 8)
        val d = frame.density
        val rect = frame.photoRect

        // 1. KF01 Bottom Contact Starburst Flare
        if (frame.releaseProgress > 0.45f) {
            val strength = smoothstep((frame.releaseProgress - 0.45f) / 0.45f)
            val pulse = 0.82f + 0.18f * sin(timeSec * 3.8f)
            emitSpriteQuadNdc(
                out = out,
                cx = contactPt.x,
                cy = contactPt.y,
                halfSizePx = 28f * d * pulse * strength,
                vpW = vpW,
                vpH = vpH,
                colorMix = 0.90f,
                intensity = 1.25f * strength,
                seed = 0.1f,
            )
        }

        // 2. Orbiting 4-Point Starbursts around the Photo Perimeter (outside & on corners!)
        if (frame.perimeterCoverage > 0.08f) {
            val perim = 2f * (rect.width + rect.height)
            val starCount = 10
            for (i in 0 until starCount) {
                val fi = i.toFloat()
                val speed = 0.018f + (i % 3) * 0.008f
                val s = ((i / starCount.toFloat()) + timeSec * speed) % 1f
                val distFromBottom = kotlin.math.abs(s - 0.5f) * 2f
                if (distFromBottom > frame.perimeterCoverage) continue
                val pt = pointOnPerimeter(rect, s * perim)
                val twinkle = 0.55f + 0.45f * sin(timeSec * 3.4f + fi * 1.9f)
                val sizePx = (14f + (i % 3) * 5.5f) * d * (0.75f + 0.35f * twinkle)
                emitSpriteQuadNdc(
                    out = out,
                    cx = pt.x,
                    cy = pt.y,
                    halfSizePx = sizePx,
                    vpW = vpW,
                    vpH = vpH,
                    colorMix = if (i % 2 == 0) 0.12f else 0.92f,
                    intensity = 0.98f * twinkle,
                    seed = fi + 1f,
                )
            }
        }

        // 3. KF03 Leading Head & KF04 Fish Arrival Starburst at P*
        val seekGeo = frame.fishPath
        if (seekGeo != null && frame.fishSeekProgress > 0.05f) {
            val headPt = cubicAt(seekGeo, frame.fishSeekProgress.coerceIn(0f, 1f))
            val headSize = (18f + 12f * frame.fishArrivalPulse) * d
            emitSpriteQuadNdc(
                out = out,
                cx = headPt.x,
                cy = headPt.y,
                halfSizePx = headSize,
                vpW = vpW,
                vpH = vpH,
                colorMix = if (frame.fishArrivalPulse > 0.4f) 0.85f else 0.15f,
                intensity = 1.15f,
                seed = 7.7f,
            )
        }
        return out.toFloatArray()
    }

    private fun pointOnPerimeter(rect: DisplayRect, distance: Float): Offset {
        // Parametrized from top-left clockwise, so s = 0.5 lands cleanly near bottom-center!
        val w = rect.width
        val h = rect.height
        val d = ((distance % (2f * (w + h))) + 2f * (w + h)) % (2f * (w + h))
        return when {
            d < w -> Offset(rect.left + d, rect.top)
            d < w + h -> Offset(rect.right, rect.top + (d - w))
            d < 2f * w + h -> Offset(rect.right - (d - (w + h)), rect.bottom)
            else -> Offset(rect.left, rect.bottom - (d - (2f * w + h)))
        }
    }

    private fun cubicAt(g: WaterLightPathGeometry, t: Float): Offset {
        val u = 1f - t
        return Offset(
            u * u * u * g.start.x + 3f * u * u * t * g.control1.x + 3f * u * t * t * g.control2.x + t * t * t * g.end.x,
            u * u * u * g.start.y + 3f * u * u * t * g.control1.y + 3f * u * t * t * g.control2.y + t * t * t * g.end.y,
        )
    }

    private fun emitVertex(
        out: MutableList<Float>,
        x: Float, y: Float, u: Float, v: Float,
        colorMix: Float, alpha: Float, seed: Float, headLimit: Float,
    ) {
        out.add(x); out.add(y); out.add(u); out.add(v)
        out.add(colorMix); out.add(alpha); out.add(seed); out.add(headLimit)
    }

    private fun emitSpriteQuadNdc(
        out: MutableList<Float>,
        cx: Float, cy: Float, halfSizePx: Float,
        vpW: Float, vpH: Float,
        colorMix: Float, intensity: Float, seed: Float,
    ) {
        fun pt(u: Float, v: Float) {
            val sx = cx + u * halfSizePx
            val sy = cy - v * halfSizePx // +v is up in screen Y negative
            out.add((sx / vpW) * 2f - 1f)
            out.add(1f - (sy / vpH) * 2f)
            out.add(u); out.add(v)
            out.add(colorMix); out.add(intensity); out.add(seed); out.add(halfSizePx)
        }
        pt(-1f, -1f); pt(1f, -1f); pt(-1f, 1f)
        pt(-1f, 1f); pt(1f, -1f); pt(1f, 1f)
    }

    private fun drawQuad(program: Int) {
        val loc = GLES20.glGetAttribLocation(program, "aPosition")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glEnableVertexAttribArray(loc)
        GLES20.glVertexAttribPointer(loc, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun drawStripBatch(
        program: Int,
        timeLoc: Int,
        alphaLoc: Int,
        verts: FloatArray,
        timeSec: Float,
        alpha: Float,
    ) {
        if (program == 0 || verts.isEmpty()) return
        val strideFloats = 8
        val count = verts.size / strideFloats
        if (count < 3) return
        val buf = GlShaderUtil.ensureFloatBuffer(ribbonBuf, verts.size).also { ribbonBuf = it }
        buf.put(verts); buf.position(0)
        GLES20.glUseProgram(program)
        GLES20.glUniform1f(timeLoc, timeSec)
        GLES20.glUniform1f(alphaLoc, alpha)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ribbonVbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts.size * GL_FLOAT_BYTES, buf, GLES20.GL_DYNAMIC_DRAW)
        val strideBytes = strideFloats * GL_FLOAT_BYTES
        val aPosUv = GLES20.glGetAttribLocation(program, "aPosUv")
        val aAttr = GLES20.glGetAttribLocation(program, "aAttr")
        GLES20.glEnableVertexAttribArray(aPosUv)
        GLES20.glVertexAttribPointer(aPosUv, 4, GLES20.GL_FLOAT, false, strideBytes, 0)
        GLES20.glEnableVertexAttribArray(aAttr)
        GLES20.glVertexAttribPointer(aAttr, 4, GLES20.GL_FLOAT, false, strideBytes, 4 * GL_FLOAT_BYTES)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, count)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun drawSpriteBatch(
        program: Int,
        timeLoc: Int,
        alphaLoc: Int,
        verts: FloatArray,
        timeSec: Float,
        alpha: Float,
    ) {
        if (program == 0 || verts.isEmpty()) return
        val strideFloats = 8
        val count = verts.size / strideFloats
        if (count < 6) return
        val buf = GlShaderUtil.ensureFloatBuffer(spriteBuf, verts.size).also { spriteBuf = it }
        buf.put(verts); buf.position(0)
        GLES20.glUseProgram(program)
        GLES20.glUniform1f(timeLoc, timeSec)
        GLES20.glUniform1f(alphaLoc, alpha)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, spriteVbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts.size * GL_FLOAT_BYTES, buf, GLES20.GL_DYNAMIC_DRAW)
        val strideBytes = strideFloats * GL_FLOAT_BYTES
        val aPosUv = GLES20.glGetAttribLocation(program, "aPosUv")
        val aAttr = GLES20.glGetAttribLocation(program, "aAttr")
        GLES20.glEnableVertexAttribArray(aPosUv)
        GLES20.glVertexAttribPointer(aPosUv, 4, GLES20.GL_FLOAT, false, strideBytes, 0)
        GLES20.glEnableVertexAttribArray(aAttr)
        GLES20.glVertexAttribPointer(aAttr, 4, GLES20.GL_FLOAT, false, strideBytes, 4 * GL_FLOAT_BYTES)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    override fun releaseGl(contextCurrent: Boolean) {
        if (contextCurrent) {
            if (quadVbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(quadVbo), 0)
            if (ribbonVbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(ribbonVbo), 0)
            if (spriteVbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(spriteVbo), 0)
            if (perimProg != 0) GLES20.glDeleteProgram(perimProg)
            if (streamProg != 0) GLES20.glDeleteProgram(streamProg)
            if (starProg != 0) GLES20.glDeleteProgram(starProg)
            if (dropletProg != 0) GLES20.glDeleteProgram(dropletProg)
        }
        bloom.release(contextCurrent)
        quadVbo = 0; ribbonVbo = 0; spriteVbo = 0
        perimProg = 0; streamProg = 0; starProg = 0; dropletProg = 0
        fboW = 0; fboH = 0
    }

    companion object {
        private const val QUAD_VS = """
            attribute vec2 aPosition;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        private const val RIBBON_VS = """
            attribute vec4 aPosUv;
            attribute vec4 aAttr;
            varying vec4 vPosUv;
            varying vec4 vAttr;
            void main() {
                vPosUv = aPosUv;
                vAttr = aAttr;
                gl_Position = vec4(aPosUv.xy, 0.0, 1.0);
            }
        """

        private const val SPRITE_VS = """
            attribute vec4 aPosUv;
            attribute vec4 aAttr;
            varying vec2 vLocal;
            varying vec4 vAttr;
            void main() {
                vLocal = aPosUv.zw;
                vAttr = aAttr;
                gl_Position = vec4(aPosUv.xy, 0.0, 1.0);
            }
        """

        // Full-viewport SDF shader:
        // 1. Upward Orb water-mist plume feeding the photo bottom
        // 2. Unclipped overflowing wavy liquid-glass photo perimeter spreading left & right from contact point
        // 3. Contact shock flare at photo bottom & arrival pulse at fish body P*
        private const val PERIMETER_AND_PLUME_FS = """
            precision highp float;
            uniform vec2 uFboSize;
            uniform vec2 uViewport;
            uniform vec4 uPhotoRect;   // left, top, width, height (in viewport px)
            uniform vec3 uOrb;         // cx, cy, radiusPx
            uniform vec2 uContact;     // contact point on photo bottom edge
            uniform vec4 uParams;      // timeSec, cornerR, perimCoverage, releaseProg
            uniform vec4 uStage;       // fishSeek, fishArrivePulse, density, opacity
            uniform vec2 uFishTarget;  // P* contact point inside photo

            float sdRoundedBox(vec2 p, vec2 b, float r) {
                vec2 q = abs(p) - b + vec2(r);
                return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
            }

            void main() {
                vec2 uvNorm = gl_FragCoord.xy / max(uFboSize, vec2(1.0));
                vec2 pTop = vec2(uvNorm.x * uViewport.x, (1.0 - uvNorm.y) * uViewport.y);

                float t = uParams.x;
                float cornerR = uParams.y;
                float perimCov = uParams.z;
                float relProg = uParams.w;
                float arrivePulse = uStage.y;
                float dScale = max(uStage.z, 1.0);

                vec3 accum = vec3(0.0);
                float alpha = 0.0;

                // -----------------------------------------------------------------
                // 1. Wide Luminous Mist Plume from Orb rising cleanly to Photo bottom
                // -----------------------------------------------------------------
                if (relProg > 0.01) {
                    vec2 beamVec = uContact - uOrb.xy;
                    float beamLen = max(length(beamVec), 1.0);
                    vec2 dir = beamVec / beamLen;
                    vec2 rel = pTop - uOrb.xy;
                    float along = dot(rel, dir);
                    float perp = rel.x * dir.y - rel.y * dir.x;
                    float sway = sin(along * 0.025 - t * 3.4) * (10.0 * dScale);
                    float alongNorm = clamp(along / beamLen, 0.0, 1.15);
                    float widthPx = (uOrb.z * 0.68) * (0.65 + 0.65 * alongNorm);
                    float latMask = exp(-pow((perp - sway) / max(widthPx, 1.0), 2.0) * 1.9);
                    float lenMask = smoothstep(0.05, 0.35, alongNorm) * smoothstep(1.12, 0.85, alongNorm);
                    float headReveal = smoothstep(alongNorm - 0.12, alongNorm + 0.02, relProg * 1.08);
                    float plume = latMask * lenMask * headReveal * relProg;
                    float wisp = 0.72 + 0.28 * sin(along * 0.045 - t * 5.2 + perp * 0.02);
                    vec3 plumeCol = mix(vec3(0.28, 0.94, 0.98), vec3(1.0, 0.88, 0.56), smoothstep(-widthPx, widthPx, perp) * 0.45);
                    accum += plumeCol * plume * wisp * 0.34;
                    alpha = max(alpha, clamp(plume * wisp * 0.32, 0.0, 0.50));
                }

                // -----------------------------------------------------------------
                // 2. Unclipped Liquid-Glass Photo Perimeter (overflows inside & outside!)
                // -----------------------------------------------------------------
                if (perimCov > 0.01) {
                    vec2 photoCenter = uPhotoRect.xy + uPhotoRect.zw * 0.5;
                    vec2 halfBox = uPhotoRect.zw * 0.5;
                    vec2 q = pTop - photoCenter;

                    // Angle measured from bottom center (0 at bottom, +-1 at top) -> symmetric spread!
                    float ang = atan(q.x, q.y); // 0 = bottom (+Y), +-PI = top (-Y)
                    float symDist = abs(ang) / 3.14159265;
                    float reveal = smoothstep(symDist - 0.14, symDist + 0.02, perimCov * 1.12);

                    if (reveal > 0.002) {
                        float waveA = sin(ang * 7.0 + t * 2.8) * (3.2 * dScale);
                        float waveB = cos(ang * 13.0 - t * 2.1) * (1.8 * dScale);
                        float waveSecondary = sin(ang * 5.0 - t * 3.2) * (4.4 * dScale);

                        float sdfBase = sdRoundedBox(q, halfBox, cornerR);
                        float dMain = abs(sdfBase + waveA + waveB);
                        float dSec = abs(sdfBase + waveSecondary);

                        // Wide overflowing outer halo + crisp liquid core + secondary intertwined filament
                        float outerHalo = exp(-dMain / (16.0 * dScale)) * 0.38;
                        float innerBody = exp(-dMain / (4.8 * dScale)) * 0.72;
                        float sharpCore = (1.0 - smoothstep(0.6 * dScale, 2.3 * dScale, dMain)) * 0.95;
                        float secBand = exp(-dSec / (3.2 * dScale)) * 0.58;

                        float colorPhase = 0.5 + 0.5 * sin(ang * 2.0 + t * 1.3);
                        float goldSpot = smoothstep(0.25, 0.85, sin(ang * 3.0 - t * 1.6));
                        vec3 cyan = vec3(0.32, 0.95, 1.0);
                        vec3 emerald = vec3(0.28, 0.90, 0.74);
                        vec3 gold = vec3(1.0, 0.85, 0.44);

                        vec3 rimCol = mix(mix(cyan, emerald, colorPhase * 0.55), gold, goldSpot * 0.65);
                        vec3 coreCol = mix(rimCol, vec3(1.0, 0.99, 0.96), 0.78);

                        vec3 perimRgb = rimCol * (outerHalo + innerBody) +
                            gold * secBand * 0.85 +
                            coreCol * sharpCore;
                        float perimA = clamp((outerHalo * 0.85 + innerBody + sharpCore + secBand * 0.6) * reveal, 0.0, 0.96);
                        accum += perimRgb * reveal;
                        alpha = max(alpha, perimA);
                    }
                }

                // -----------------------------------------------------------------
                // 3. KF01 Bottom Contact Flare + KF04 Fish Contact Ripple at P*
                // -----------------------------------------------------------------
                if (relProg > 0.45) {
                    float cStrength = smoothstep(0.45, 0.95, relProg);
                    float dContact = length(pTop - uContact);
                    float flare = exp(-dContact / (22.0 * dScale)) * 0.78 * cStrength;
                    accum += vec3(1.0, 0.88, 0.52) * flare;
                    alpha = max(alpha, clamp(flare, 0.0, 0.88));
                }

                if (arrivePulse > 0.01) {
                    float dFish = length(pTop - uFishTarget);
                    float ringRadius = (10.0 + 26.0 * fract(t * 0.65)) * dScale;
                    float ring = exp(-abs(dFish - ringRadius) / (3.8 * dScale)) * (1.0 - fract(t * 0.65)) * 0.55;
                    float fishGlow = exp(-dFish / (20.0 * dScale)) * 0.72;
                    vec3 fCol = mix(vec3(0.35, 0.96, 0.98), vec3(1.0, 0.86, 0.48), 0.55);
                    accum += fCol * (fishGlow + ring) * arrivePulse;
                    alpha = max(alpha, clamp((fishGlow + ring) * arrivePulse, 0.0, 0.90));
                }

                // -----------------------------------------------------------------
                // 4. Subtle Ambient Corner Mist Wisps (Framing the Stage)
                // -----------------------------------------------------------------
                float cornerFade = smoothstep(0.62, 0.98, uvNorm.x) + smoothstep(0.38, 0.02, uvNorm.x);
                cornerFade *= smoothstep(0.45, 0.04, uvNorm.y);
                float mist = cornerFade * (0.5 + 0.5 * sin(uvNorm.x * 16.0 + uvNorm.y * 12.0 - t * 1.4)) * 0.14 * perimCov;
                accum += vec3(0.24, 0.82, 0.74) * mist;
                alpha = clamp(max(alpha, mist), 0.0, 0.98);

                gl_FragColor = vec4(clamp(accum, 0.0, alpha), alpha);
            }
        """

        // Tapered double-rimmed liquid ribbon shader for Orb->Photo and Perimeter->Fish twin strands
        private const val STREAM_RIBBON_FS = """
            precision highp float;
            varying vec4 vPosUv; // ndcX, ndcY, u (0..1), v (-1..1)
            varying vec4 vAttr;  // colorMix, alpha, seed, headLimit
            uniform float uTime;
            uniform float uAlpha;

            void main() {
                float u = vPosUv.z;
                float v = abs(vPosUv.w);
                float colorMix = vAttr.x;
                float fadeAlpha = vAttr.y;
                float seed = vAttr.z;

                float outerAA = 1.0 - smoothstep(0.80, 1.0, v);
                float tipFade = smoothstep(0.0, 0.10, u) * smoothstep(1.0, 0.88, u);
                float dualRim = smoothstep(0.42, 0.85, v) * outerAA;
                float coreSpine = exp(-v * v * 12.0) * 0.65;
                float body = 0.20 * (1.0 - v * v) * outerAA;

                float pulse = 0.62 + 0.38 * sin(u * 14.0 - uTime * 5.2 + seed);
                float a = clamp((dualRim * 0.85 + coreSpine * 0.72 + body) * tipFade * fadeAlpha * uAlpha, 0.0, 0.96);

                vec3 cyan = vec3(0.30, 0.94, 1.0);
                vec3 gold = vec3(1.0, 0.85, 0.46);
                vec3 baseCol = mix(cyan, gold, colorMix);
                vec3 col = mix(baseCol, vec3(1.0, 0.99, 0.95), (coreSpine * 0.75 + dualRim * 0.55) * pulse);

                gl_FragColor = vec4(col * a * 1.10, a);
            }
        """

        private const val DROPLET_FS = """
            precision highp float;
            varying vec2 vLocal;
            varying vec4 vAttr;
            uniform float uTime;
            uniform float uAlpha;

            void main() {
                float r2 = dot(vLocal, vLocal);
                if (r2 > 1.0) discard;
                float r = sqrt(r2);
                float edgeAA = 1.0 - smoothstep(0.82, 1.0, r);
                float nz = sqrt(max(0.0, 1.0 - r2));
                float fresnel = pow(1.0 - nz, 2.7);
                float spec = exp(-length(vLocal - vec2(-0.34, 0.36)) * 5.0);
                vec3 tint = mix(vec3(0.42, 0.95, 1.0), vec3(1.0, 0.88, 0.52), vAttr.x);
                vec3 col = tint * (0.28 + 0.72 * fresnel) + vec3(1.0) * spec;
                float a = clamp((0.26 + 0.68 * fresnel + spec * 0.85) * edgeAA * vAttr.y * uAlpha, 0.0, 0.95);
                gl_FragColor = vec4(col * a, a);
            }
        """

        private const val STAR_FS = """
            precision highp float;
            varying vec2 vLocal;
            varying vec4 vAttr;
            uniform float uTime;
            uniform float uAlpha;

            void main() {
                float d = length(vLocal);
                if (d > 1.0) discard;
                float falloff = smoothstep(1.0, 0.0, d);
                float spikeH = exp(-abs(vLocal.y) * 26.0) * exp(-abs(vLocal.x) * 3.2);
                float spikeV = exp(-abs(vLocal.x) * 26.0) * exp(-abs(vLocal.y) * 3.2);
                float core = exp(-d * d * 32.0);
                float halo = exp(-d * 4.2) * 0.36;
                float shape = (spikeH + spikeV + core * 1.1 + halo) * falloff * vAttr.y * uAlpha;
                vec3 tint = mix(vec3(0.36, 0.95, 1.0), vec3(1.0, 0.86, 0.48), vAttr.x);
                vec3 col = mix(tint, vec3(1.0, 0.99, 0.96), clamp(core + (spikeH + spikeV) * 0.65, 0.0, 1.0));
                vec3 premul = clamp(col * shape, 0.0, 1.0);
                float a = clamp(max(premul.r, max(premul.g, premul.b)), 0.0, 1.0);
                gl_FragColor = vec4(premul, a);
            }
        """
    }
}
