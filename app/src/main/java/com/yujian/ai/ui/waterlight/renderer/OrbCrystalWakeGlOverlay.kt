package com.yujian.ai.ui.waterlight.renderer

import android.opengl.GLES20
import android.os.SystemClock
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.RecognitionSceneFrame
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

internal const val ORB_CRYSTAL_CONTAINER_RATIO = 2.5f

internal data class OrbCrystalGlFrame(
    val sceneFrame: RecognitionSceneFrame,
    val elapsedMs: Long,
    val stage: RecognitionSceneStage,
    val stageProgress: Float,
    val wakeOptics: Float,
    val wakeWater: Float,
    val ribbonOpening: Float,
    val settle: Float,
    val releasePlumeStrength: Float,
    val reducedMotion: Boolean,
    val lowPerformance: Boolean,
    val showDroplets: Boolean,
    val showSunlight: Boolean,
    val showCaustics: Boolean,
)

/**
 * High-fidelity 3D Crystal Water Orb rendered via TextureView + EGL14 + GLES20 + 1/4-res Bloom:
 * - Back FBO: bottom water ripples & reflection shimmer, upward release plume, bokeh, back grass & back 3D glass ribbons
 * - Scene FBO: 3D refracting glass sphere (samples Back FBO with chromatic dispersion), S-curve meniscus wave,
 *   GPU ridge caustics, upper-left solar flare, front grass & front 3D ribbons, 12 orbiting micro-droplets, 4-point starbursts
 * - Bloom Composite: 1/4-res Gaussian bloom + valid premultiplied alpha output
 */
@Composable
internal fun OrbCrystalWakeGlOverlay(
    frame: WaterLightFrame,
    ribbonOpening: Float,
    settle: Float,
    wake: OrbWakeBreathReleaseV11.Wake,
    lowPerformance: Boolean,
    showDroplets: Boolean,
    showSunlight: Boolean,
    showCaustics: Boolean,
    onFirstFramePresented: () -> Unit,
    glOwnerAccepted: Boolean = false,
    onOwnerStateChanged: (OrbCrystalRenderResult, Boolean) -> Unit = { _, _ -> },
    onRenderResult: (RecognitionSceneIdentity, Map<String, Any?>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestFirstFramePresented = rememberUpdatedState(onFirstFramePresented)
    val latestOwnerStateChanged = rememberUpdatedState(onOwnerStateChanged)
    val latestRenderResult = rememberUpdatedState(onRenderResult)
    val renderer = remember { OrbCrystalGlRenderer() }
    val textureView = remember(context) {
        TransparentGlTextureView(
            context = context,
            threadName = "OrbCrystalGlThread",
            tag = "OrbCrystalGl",
            renderer = renderer,
            onFirstFramePresented = { latestFirstFramePresented.value() },
            onRenderResult = { result, eventType ->
                latestRenderResult.value(
                    result.identity,
                    result.diagnosticFields() + ("eventType" to eventType),
                )
            },
            onOwnerStateChanged = { result, accepted ->
                result?.let { latestOwnerStateChanged.value(it, accepted) }
            },
        )
    }

    val scene = frame.sceneFrame
    val releasePlume = when (scene.stage) {
        RecognitionSceneStage.ORB_RELEASE -> (0.25f + 0.75f * scene.stageProgress.coerceIn(0f, 1f))
        RecognitionSceneStage.PERIMETER_ARRIVAL,
        RecognitionSceneStage.PATH_TO_FISH,
        RecognitionSceneStage.CONTOUR_A,
        RecognitionSceneStage.CONTOUR_B,
        RecognitionSceneStage.CONTOUR_C,
        RecognitionSceneStage.FISH_GOLD_PEAK -> 0.85f
        RecognitionSceneStage.ORB_RETURN -> (1f - scene.stageProgress.coerceIn(0f, 1f)) * 0.75f
        else -> 0f
    }

    val glFrame = OrbCrystalGlFrame(
        sceneFrame = scene,
        elapsedMs = if (frame.reducedMotion) 0L else frame.elapsedMs.coerceAtLeast(0L),
        stage = scene.stage,
        stageProgress = scene.stageProgress.coerceIn(0f, 1f),
        wakeOptics = if (scene.stage == RecognitionSceneStage.PHOTO_STABLE) 0.15f else max(0.35f, wake.optics),
        wakeWater = if (scene.stage == RecognitionSceneStage.PHOTO_STABLE) 0.20f else max(0.45f, wake.waterCore),
        ribbonOpening = ribbonOpening.coerceIn(0f, 1f),
        settle = settle.coerceIn(0f, 1f),
        releasePlumeStrength = releasePlume,
        reducedMotion = frame.reducedMotion,
        lowPerformance = lowPerformance,
        showDroplets = showDroplets,
        showSunlight = showSunlight,
        showCaustics = showCaustics,
    )

    AndroidView(
        factory = { textureView },
        modifier = modifier,
        update = { view ->
            view.visibility = View.VISIBLE
            view.alpha = if (glOwnerAccepted) 1f else 0f
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

private class OrbCrystalGlRenderer : GlSceneRenderer<OrbCrystalGlFrame> {
    override val requiresRenderResult: Boolean = true
    private val bloom = GlBloomPipeline()
    private var backFbo: GlFramebuffer? = null
    private var quadVbo = 0
    private var ribbonVbo = 0
    private var spriteVbo = 0
    private var ribbonBuffer: FloatBuffer? = null
    private var spriteBuffer: FloatBuffer? = null

    private var surfaceW = 1
    private var surfaceH = 1
    private var fboW = 1
    private var fboH = 1

    private var envProg = 0
    private var ribbonProg = 0
    private var grassProg = 0
    private var sphereProg = 0
    private var dropletProg = 0
    private var starProg = 0
    private var lastBufferStatus = RenderPassStatus.NOT_RUN
    private var lastBloomExecution: GlBloomExecution? = null
    private var lastDrawFailureReason: String? = null

    override fun sceneFrameForResult(frame: OrbCrystalGlFrame): RecognitionSceneFrame = frame.sceneFrame

    // Uniform locations
    private var envTimeLoc = -1
    private var envWakeLoc = -1
    private var envPlumeLoc = -1
    private var envRadiusLoc = -1

    private var ribTimeLoc = -1
    private var ribWakeLoc = -1
    private var ribFrontLoc = -1

    private var grsTimeLoc = -1
    private var grsWakeLoc = -1
    private var grsFrontLoc = -1

    private var sphBackTexLoc = -1
    private var sphTimeLoc = -1
    private var sphWakeLoc = -1
    private var sphRadiusLoc = -1
    private var sphSunLoc = -1
    private var sphCausticLoc = -1

    private var drpTimeLoc = -1
    private var drpWakeLoc = -1
    private var strTimeLoc = -1
    private var strWakeLoc = -1

    override fun onSurfaceCreated() {
        quadVbo = GlShaderUtil.createQuadVbo()
        val ids = IntArray(2)
        GLES20.glGenBuffers(2, ids, 0)
        ribbonVbo = ids[0]
        spriteVbo = ids[1]

        bloom.init()
        envProg = GlShaderUtil.createProgram(QUAD_VS, ENV_FS, "orb-env")
        ribbonProg = GlShaderUtil.createProgram(RIBBON_VS, RIBBON_FS, "orb-ribbon")
        grassProg = GlShaderUtil.createProgram(RIBBON_VS, GRASS_FS, "orb-grass")
        sphereProg = GlShaderUtil.createProgram(QUAD_VS, SPHERE_FS, "orb-sphere")
        dropletProg = GlShaderUtil.createProgram(SPRITE_VS, DROPLET_FS, "orb-droplet")
        starProg = GlShaderUtil.createProgram(SPRITE_VS, STAR_FS, "orb-star")

        if (envProg != 0) {
            envTimeLoc = GLES20.glGetUniformLocation(envProg, "uTime")
            envWakeLoc = GLES20.glGetUniformLocation(envProg, "uWake")
            envPlumeLoc = GLES20.glGetUniformLocation(envProg, "uPlume")
            envRadiusLoc = GLES20.glGetUniformLocation(envProg, "uSphereRadius")
        }
        if (ribbonProg != 0) {
            ribTimeLoc = GLES20.glGetUniformLocation(ribbonProg, "uTime")
            ribWakeLoc = GLES20.glGetUniformLocation(ribbonProg, "uWake")
            ribFrontLoc = GLES20.glGetUniformLocation(ribbonProg, "uIsFront")
        }
        if (grassProg != 0) {
            grsTimeLoc = GLES20.glGetUniformLocation(grassProg, "uTime")
            grsWakeLoc = GLES20.glGetUniformLocation(grassProg, "uWake")
            grsFrontLoc = GLES20.glGetUniformLocation(grassProg, "uIsFront")
        }
        if (sphereProg != 0) {
            sphBackTexLoc = GLES20.glGetUniformLocation(sphereProg, "uBackTex")
            sphTimeLoc = GLES20.glGetUniformLocation(sphereProg, "uTime")
            sphWakeLoc = GLES20.glGetUniformLocation(sphereProg, "uWake")
            sphRadiusLoc = GLES20.glGetUniformLocation(sphereProg, "uSphereRadius")
            sphSunLoc = GLES20.glGetUniformLocation(sphereProg, "uShowSun")
            sphCausticLoc = GLES20.glGetUniformLocation(sphereProg, "uShowCaustics")
        }
        if (dropletProg != 0) {
            drpTimeLoc = GLES20.glGetUniformLocation(dropletProg, "uTime")
            drpWakeLoc = GLES20.glGetUniformLocation(dropletProg, "uWake")
        }
        if (starProg != 0) {
            strTimeLoc = GLES20.glGetUniformLocation(starProg, "uTime")
            strWakeLoc = GLES20.glGetUniformLocation(starProg, "uWake")
        }
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        surfaceW = width.coerceAtLeast(1)
        surfaceH = height.coerceAtLeast(1)
    }

    override fun onDrawFrame(frame: OrbCrystalGlFrame): Boolean {
        lastBufferStatus = RenderPassStatus.NOT_RUN
        lastBloomExecution = null
        lastDrawFailureReason = null
        clearGlErrors()
        if (sphereProg == 0 || ribbonProg == 0 || grassProg == 0 || envProg == 0) {
            lastDrawFailureReason = "REQUIRED_PROCEDURAL_SHADER_MISSING"
            return false
        }

        // Cap FBO internal size cleanly so both high-DPI phones & emulators stay blazing fast
        val maxDim = if (frame.lowPerformance) 300 else 440
        val scale = minOf(1f, maxDim.toFloat() / maxOf(surfaceW, surfaceH).toFloat())
        val targetW = (surfaceW * scale).toInt().coerceAtLeast(64)
        val targetH = (surfaceH * scale).toInt().coerceAtLeast(64)
        if (!ensureBuffers(targetW, targetH)) {
            lastBufferStatus = RenderPassStatus.FAIL
            lastDrawFailureReason = "BACK_OR_SCENE_FBO_INCOMPLETE"
            return false
        }
        lastBufferStatus = RenderPassStatus.PASS

        val timeSec = frame.elapsedMs / 1000f
        val sphereRadiusNdc = 1f / ORB_CONTAINER_RATIO // 1 / 2.5 = 0.40 in [-1, 1] NDC space
        val wakeOptics = frame.wakeOptics.coerceIn(0f, 1f)
        val wakeWater = frame.wakeWater.coerceIn(0f, 1f)
        val openScale = 0.72f + 0.28f * frame.ribbonOpening

        // Build 3D orbital ribbons (back vs front segments) + curved aquatic grass blades
        val backGrassVerts = buildGrassStripVertices(timeSec, sphereRadiusNdc, frontPass = false)
        val frontGrassVerts = buildGrassStripVertices(timeSec, sphereRadiusNdc, frontPass = true)
        val backRibbonVerts = buildOrbitalRibbonVertices(timeSec, sphereRadiusNdc * openScale, frontPass = false)
        val frontRibbonVerts = buildOrbitalRibbonVertices(timeSec, sphereRadiusNdc * openScale, frontPass = true)

        val backBuf = backFbo ?: return false

        // =========================================================================
        // PASS 1: Render Back Environment + Water Ripples + Back Grass + Back Ribbons -> backFbo
        // =========================================================================
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, backBuf.fboId)
        GLES20.glViewport(0, 0, fboW, fboH)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFuncSeparate(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        if (envProg != 0) {
            GLES20.glUseProgram(envProg)
            GLES20.glUniform1f(envTimeLoc, timeSec)
            GLES20.glUniform1f(envWakeLoc, wakeOptics)
            GLES20.glUniform1f(envPlumeLoc, frame.releasePlumeStrength)
            GLES20.glUniform1f(envRadiusLoc, sphereRadiusNdc)
            drawQuad(envProg)
        }

        drawStripBatch(grassProg, grsTimeLoc, grsWakeLoc, grsFrontLoc, backGrassVerts, timeSec, wakeWater, 0f)
        drawStripBatch(ribbonProg, ribTimeLoc, ribWakeLoc, ribFrontLoc, backRibbonVerts, timeSec, wakeOptics, 0f)
        lastDrawFailureReason = checkGlError("PROCEDURAL_BACK_PASS") ?: lastDrawFailureReason

        // =========================================================================
        // PASS 2: Render Refracting 3D Crystal Sphere + Front Grass + Front Ribbons + Droplets + Starbursts -> sceneFbo
        // =========================================================================
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, bloom.sceneFboId)
        GLES20.glViewport(0, 0, fboW, fboH)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // 2a. Sphere + BackFbo copy + real optical refraction + caustics + meniscus + solar flare
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(sphereProg)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backBuf.textureId)
        GLES20.glUniform1i(sphBackTexLoc, 0)
        GLES20.glUniform1f(sphTimeLoc, timeSec)
        GLES20.glUniform1f(sphWakeLoc, wakeWater)
        GLES20.glUniform1f(sphRadiusLoc, sphereRadiusNdc)
        GLES20.glUniform1f(sphSunLoc, if (frame.showSunlight) 1f else 0f)
        GLES20.glUniform1f(sphCausticLoc, if (frame.showCaustics) 1f else 0f)
        drawQuad(sphereProg)

        // 2b. Front Grass & Front Ribbons (normal premultiplied blend over sphere)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFuncSeparate(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        drawStripBatch(grassProg, grsTimeLoc, grsWakeLoc, grsFrontLoc, frontGrassVerts, timeSec, wakeWater, 1f)
        drawStripBatch(ribbonProg, ribTimeLoc, ribWakeLoc, ribFrontLoc, frontRibbonVerts, timeSec, wakeOptics, 1f)

        // 2c. 12 Floating Refractive Micro-Droplets
        if (frame.showDroplets && dropletProg != 0) {
            val droplets = buildDropletSprites(timeSec, sphereRadiusNdc * openScale)
            drawSpriteBatch(dropletProg, drpTimeLoc, drpWakeLoc, droplets, timeSec, wakeOptics)
        }

        // 2d. Additive 4-Point Starburst Flares (Solar Peak + Orbiting Ribbon Crests)
        if (starProg != 0) {
            GLES20.glBlendFuncSeparate(GLES20.GL_ONE, GLES20.GL_ONE, GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            val stars = buildStarburstSprites(timeSec, sphereRadiusNdc * openScale, frame.showSunlight)
            drawSpriteBatch(starProg, strTimeLoc, strWakeLoc, stars, timeSec, wakeOptics)
        }
        lastDrawFailureReason = checkGlError("PROCEDURAL_SCENE_PASS") ?: lastDrawFailureReason

        // =========================================================================
        // PASS 3 & 4: 1/4-Res Bloom Blur + Final Premultiplied Composite to TextureView
        // =========================================================================
        lastBloomExecution = bloom.renderBloomAndComposite(
            quadVbo = quadVbo,
            threshold = 0.56f,
            bloomIntensity = 0.62f * (0.65f + 0.35f * wakeOptics),
            globalOpacity = 1f,
            skipBlur = frame.lowPerformance,
        )
        lastDrawFailureReason = lastBloomExecution?.failureReason ?: lastDrawFailureReason

        // Restore full viewport cleanly
        GLES20.glViewport(0, 0, surfaceW, surfaceH)
        return true
    }

    override fun onDrawFrameWithResult(frame: OrbCrystalGlFrame, frameSequence: Long): GlFrameExecution {
        val drawSucceeded = try {
            onDrawFrame(frame)
        } catch (error: Throwable) {
            lastDrawFailureReason = "DRAW_EXCEPTION:${error.javaClass.simpleName}"
            false
        }
        val scene = frame.sceneFrame
        val bloomResult = lastBloomExecution
        val programsReady = quadVbo != 0 && envProg != 0 && ribbonProg != 0 && grassProg != 0 &&
            sphereProg != 0 && dropletProg != 0 && starProg != 0 && bloom.shaderProgramsReady
        val result = OrbCrystalRenderResult(
            identity = scene.identity,
            sceneClockMs = scene.sceneElapsedMs,
            frameSequence = frameSequence,
            stage = scene.stage,
            stageProgress = scene.stageProgress,
            eventSequence = scene.eventSequence,
            geometryVersion = scene.geometryVersion,
            timestampMs = SystemClock.uptimeMillis(),
            // Stage 3-B binds the frozen, alpha-preserving Core texture. The procedural sphere is not a substitute.
            coreTextureStatus = RenderPassStatus.NOT_READY,
            coreAlphaStatus = RenderPassStatus.NOT_READY,
            shaderCompileLinkStatus = if (programsReady) RenderPassStatus.PASS else RenderPassStatus.FAIL,
            backFboStatus = lastBufferStatus,
            // Stage 3-D supplies the frozen five Resident paths and their real BACK/FRONT passes.
            residentBackStatus = RenderPassStatus.NOT_READY,
            // Stage 3-B/3-C/3-E/3-F/3-G own these passes; current procedural output is not counted as their PASS.
            coreFluidStatus = RenderPassStatus.NOT_READY,
            refractionStatus = RenderPassStatus.NOT_READY,
            r01bMaskStatus = RenderPassStatus.NOT_READY,
            r02DropletStatus = RenderPassStatus.NOT_READY,
            r03LightStatus = RenderPassStatus.NOT_READY,
            r05CausticsStatus = RenderPassStatus.NOT_READY,
            residentFrontStatus = RenderPassStatus.NOT_READY,
            bloomExtractStatus = bloomResult?.extract ?: RenderPassStatus.NOT_RUN,
            bloomBlurHStatus = bloomResult?.blurHorizontal ?: RenderPassStatus.NOT_RUN,
            bloomBlurVStatus = bloomResult?.blurVertical ?: RenderPassStatus.NOT_RUN,
            compositeStatus = bloomResult?.composite ?: RenderPassStatus.NOT_RUN,
            failureReason = lastDrawFailureReason,
        )
        return GlFrameExecution(
            drawStatus = if (drawSucceeded) RenderPassStatus.PASS else RenderPassStatus.FAIL,
            renderResult = result,
            failureReason = lastDrawFailureReason,
        )
    }

    override fun onHostFailure(
        frame: OrbCrystalGlFrame,
        frameSequence: Long,
        reason: String,
    ): OrbCrystalRenderResult = OrbCrystalRenderResult(
        identity = frame.sceneFrame.identity,
        sceneClockMs = frame.sceneFrame.sceneElapsedMs,
        frameSequence = frameSequence,
        stage = frame.sceneFrame.stage,
        stageProgress = frame.sceneFrame.stageProgress,
        eventSequence = frame.sceneFrame.eventSequence,
        geometryVersion = frame.sceneFrame.geometryVersion,
        timestampMs = SystemClock.uptimeMillis(),
        coreTextureStatus = RenderPassStatus.NOT_READY,
        coreAlphaStatus = RenderPassStatus.NOT_READY,
        shaderCompileLinkStatus = RenderPassStatus.NOT_RUN,
        backFboStatus = RenderPassStatus.NOT_RUN,
        residentBackStatus = RenderPassStatus.NOT_READY,
        coreFluidStatus = RenderPassStatus.NOT_READY,
        refractionStatus = RenderPassStatus.NOT_READY,
        r01bMaskStatus = RenderPassStatus.NOT_READY,
        r02DropletStatus = RenderPassStatus.NOT_READY,
        r03LightStatus = RenderPassStatus.NOT_READY,
        r05CausticsStatus = RenderPassStatus.NOT_READY,
        residentFrontStatus = RenderPassStatus.NOT_READY,
        bloomExtractStatus = RenderPassStatus.NOT_RUN,
        bloomBlurHStatus = RenderPassStatus.NOT_RUN,
        bloomBlurVStatus = RenderPassStatus.NOT_RUN,
        compositeStatus = RenderPassStatus.NOT_RUN,
        failureReason = reason,
    )

    private fun clearGlErrors() {
        while (GLES20.glGetError() != GLES20.GL_NO_ERROR) Unit
    }

    private fun checkGlError(pass: String): String? {
        val error = GLES20.glGetError()
        if (error == GLES20.GL_NO_ERROR) return null
        while (GLES20.glGetError() != GLES20.GL_NO_ERROR) Unit
        return "${pass}_GL_ERROR_0x${error.toString(16)}"
    }

    private fun ensureBuffers(w: Int, h: Int): Boolean {
        if (w == fboW && h == fboH && backFbo != null && bloom.sceneFboId != 0) return true
        backFbo?.destroy()
        backFbo = GlShaderUtil.createFramebuffer(w, h)
        if (backFbo == null) return false
        if (!bloom.ensureSize(w, h)) {
            backFbo?.destroy()
            backFbo = null
            return false
        }
        fboW = w
        fboH = h
        return true
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
        wakeLoc: Int,
        frontLoc: Int,
        verts: FloatArray,
        timeSec: Float,
        wake: Float,
        isFront: Float,
    ) {
        if (program == 0 || verts.isEmpty()) return
        val strideFloats = 8 // x, y, u, v, colorMix, depth, alphaWeight, unused
        val vertexCount = verts.size / strideFloats
        if (vertexCount < 3) return

        val buf = GlShaderUtil.ensureFloatBuffer(ribbonBuffer, verts.size).also { ribbonBuffer = it }
        buf.put(verts)
        buf.position(0)

        GLES20.glUseProgram(program)
        GLES20.glUniform1f(timeLoc, timeSec)
        GLES20.glUniform1f(wakeLoc, wake)
        GLES20.glUniform1f(frontLoc, isFront)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ribbonVbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts.size * GL_FLOAT_BYTES, buf, GLES20.GL_DYNAMIC_DRAW)

        val strideBytes = strideFloats * GL_FLOAT_BYTES
        val aPosUv = GLES20.glGetAttribLocation(program, "aPosUv")
        val aAttr = GLES20.glGetAttribLocation(program, "aAttr")
        GLES20.glEnableVertexAttribArray(aPosUv)
        GLES20.glVertexAttribPointer(aPosUv, 4, GLES20.GL_FLOAT, false, strideBytes, 0)
        GLES20.glEnableVertexAttribArray(aAttr)
        GLES20.glVertexAttribPointer(aAttr, 4, GLES20.GL_FLOAT, false, strideBytes, 4 * GL_FLOAT_BYTES)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, vertexCount)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun drawSpriteBatch(
        program: Int,
        timeLoc: Int,
        wakeLoc: Int,
        verts: FloatArray,
        timeSec: Float,
        wake: Float,
    ) {
        if (program == 0 || verts.isEmpty()) return
        val strideFloats = 8 // x, y, localU, localV, colorMix, intensity, seed, sizeScale
        val vertexCount = verts.size / strideFloats
        if (vertexCount < 6) return

        val buf = GlShaderUtil.ensureFloatBuffer(spriteBuffer, verts.size).also { spriteBuffer = it }
        buf.put(verts)
        buf.position(0)

        GLES20.glUseProgram(program)
        GLES20.glUniform1f(timeLoc, timeSec)
        GLES20.glUniform1f(wakeLoc, wake)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, spriteVbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts.size * GL_FLOAT_BYTES, buf, GLES20.GL_DYNAMIC_DRAW)

        val strideBytes = strideFloats * GL_FLOAT_BYTES
        val aPosUv = GLES20.glGetAttribLocation(program, "aPosUv")
        val aAttr = GLES20.glGetAttribLocation(program, "aAttr")
        GLES20.glEnableVertexAttribArray(aPosUv)
        GLES20.glVertexAttribPointer(aPosUv, 4, GLES20.GL_FLOAT, false, strideBytes, 0)
        GLES20.glEnableVertexAttribArray(aAttr)
        GLES20.glVertexAttribPointer(aAttr, 4, GLES20.GL_FLOAT, false, strideBytes, 4 * GL_FLOAT_BYTES)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    /**
     * Builds 5 intertwined 3D tilted orbital ellipses split seamlessly by depth (z < 0 back vs z >= 0 front),
     * connected cleanly via degenerate triangles into one Triangle Strip draw call.
     */
    private fun buildOrbitalRibbonVertices(
        timeSec: Float,
        radius: Float,
        frontPass: Boolean,
    ): FloatArray {
        data class OrbitSpec(
            val rx: Float,
            val ry: Float,
            val tiltZ: Float,
            val tiltX: Float,
            val speed: Float,
            val phase0: Float,
            val halfWidth: Float,
            val colorMix: Float, // 0 = Cyan, 1 = Amber Gold, 0.45 = Emerald Cyan
        )

        val orbits = listOf(
            // Primary outer Cyan loop sweeping lower-left -> upper-right
            OrbitSpec(1.42f * radius, 0.56f * radius, 0.48f, 0.85f, 0.52f, 0.2f, 0.044f * radius, 0.05f),
            // Primary Champagne Gold loop crossing upper-left -> lower-right
            OrbitSpec(1.38f * radius, 0.52f * radius, -0.54f, 0.92f, -0.46f, 1.4f, 0.040f * radius, 0.95f),
            // Tight inner Cyan glass ring hugging sphere
            OrbitSpec(1.18f * radius, 0.44f * radius, -0.22f, 1.05f, 0.68f, 2.8f, 0.028f * radius, 0.12f),
            // Wide outer Gold halo ring
            OrbitSpec(1.52f * radius, 0.64f * radius, 0.20f, 0.78f, -0.38f, 4.1f, 0.024f * radius, 0.88f),
            // Mid Emerald-Cyan energy stream
            OrbitSpec(1.28f * radius, 0.72f * radius, -0.82f, 0.96f, 0.41f, 5.2f, 0.032f * radius, 0.35f),
        )

        val steps = 72
        val out = ArrayList<Float>(orbits.size * (steps + 4) * 16)
        val pxBuf = FloatArray(steps + 1)
        val pyBuf = FloatArray(steps + 1)
        val pzBuf = FloatArray(steps + 1)

        for (oIdx in orbits.indices) {
            val o = orbits[oIdx]
            val cosTz = cos(o.tiltZ + sin(timeSec * 0.35f + oIdx) * 0.05f)
            val sinTz = sin(o.tiltZ + sin(timeSec * 0.35f + oIdx) * 0.05f)
            val cosTx = cos(o.tiltX)
            val sinTx = sin(o.tiltX)
            val flowShift = timeSec * o.speed + o.phase0

            for (i in 0..steps) {
                val theta = (i.toFloat() / steps) * (2f * PI.toFloat())
                val lx = o.rx * cos(theta)
                val ly = o.ry * sin(theta)
                val x1 = lx * cosTz - ly * sinTz
                val y1 = lx * sinTz + ly * cosTz
                val y2 = y1 * cosTx
                val z2 = ly * sinTx
                pxBuf[i] = x1
                pyBuf[i] = y2 + 0.03f * radius * sin(theta * 2f + flowShift)
                pzBuf[i] = z2
            }

            // Emit continuous runs whose depth matches frontPass
            var inRun = false
            for (i in 0..steps) {
                val depth = pzBuf[i]
                val matchesPass = if (frontPass) depth >= -0.02f * radius else depth <= 0.02f * radius
                if (!matchesPass) {
                    inRun = false
                    continue
                }
                val prev = (i - 1).coerceAtLeast(0)
                val next = (i + 1).coerceAtMost(steps)
                val tx = pxBuf[next] - pxBuf[prev]
                val ty = pyBuf[next] - pyBuf[prev]
                val len = hypot(tx.toDouble(), ty.toDouble()).toFloat().coerceAtLeast(1e-5f)
                val nx = -ty / len
                val ny = tx / len

                val u = i.toFloat() / steps
                val swell = 0.65f + 0.55f * (0.5f + 0.5f * sin(u * 2f * PI.toFloat() * 2f - flowShift * 2f))
                val hw = o.halfWidth * swell
                val depthAlpha = if (frontPass) 1.0f else (0.55f + 0.35f * (1f + depth / o.rx).coerceIn(0f, 1f))

                val lx = pxBuf[i] - nx * hw
                val ly = pyBuf[i] - ny * hw
                val rx = pxBuf[i] + nx * hw
                val ry = pyBuf[i] + ny * hw

                if (!inRun && out.isNotEmpty()) {
                    // Degenerate bridge to start new sub-strip
                    val lastOffset = out.size - 8
                    for (k in 0 until 8) out.add(out[lastOffset + k])
                    emitVertex(out, lx, ly, u, -1f, o.colorMix, depth / radius, depthAlpha, oIdx.toFloat())
                }
                inRun = true
                emitVertex(out, lx, ly, u, -1f, o.colorMix, depth / radius, depthAlpha, oIdx.toFloat())
                emitVertex(out, rx, ry, u, 1f, o.colorMix, depth / radius, depthAlpha, oIdx.toFloat())
            }
        }
        return out.toFloatArray()
    }

    /**
     * Sweeping curved aquatic grass blades wrapping both behind and in front of the orb,
     * extending gracefully out to ~1.48R like the reference illustration.
     */
    private fun buildGrassStripVertices(
        timeSec: Float,
        radius: Float,
        frontPass: Boolean,
    ): FloatArray {
        data class BladeSpec(
            val p0x: Float, val p0y: Float,
            val p1x: Float, val p1y: Float,
            val p2x: Float, val p2y: Float,
            val p3x: Float, val p3y: Float,
            val maxHalfWidth: Float,
            val seed: Float,
            val tint: Float, // 0 = deep emerald, 1 = vibrant sunlit lime-emerald
            val front: Boolean,
        )

        val r = radius
        val blades = listOf(
            // Back blades sweeping behind top-right and left
            BladeSpec(0.35f * r, -1.08f * r, 1.32f * r, -0.58f * r, 1.48f * r, 0.35f * r, 0.62f * r, 1.28f * r, 0.135f * r, 0.3f, 0.65f, false),
            BladeSpec(-0.62f * r, 1.18f * r, -1.35f * r, 0.55f * r, -1.25f * r, -0.45f * r, -0.15f * r, -1.24f * r, 0.125f * r, 1.7f, 0.45f, false),
            BladeSpec(-0.95f * r, -0.62f * r, -0.35f * r, -0.10f * r, 0.25f * r, 0.32f * r, 1.18f * r, 0.85f * r, 0.115f * r, 2.9f, 0.85f, false),
            // Front blades wrapping cleanly over lower-left and right orb edges
            BladeSpec(-0.98f * r, 0.15f * r, -1.28f * r, -0.55f * r, -0.62f * r, -1.25f * r, 0.32f * r, -1.32f * r, 0.110f * r, 4.1f, 0.90f, true),
            BladeSpec(1.18f * r, -0.48f * r, 1.24f * r, 0.28f * r, 0.68f * r, 0.98f * r, -0.25f * r, 1.25f * r, 0.095f * r, 5.4f, 0.72f, true),
        )

        val steps = 36
        val out = ArrayList<Float>(blades.size * (steps + 2) * 16)
        val pxBuf = FloatArray(steps + 1)
        val pyBuf = FloatArray(steps + 1)

        for (blade in blades) {
            if (blade.front != frontPass) continue
            val swayFreq = 1.15f
            for (i in 0..steps) {
                val u = i.toFloat() / steps
                val inv = 1f - u
                val bx = inv * inv * inv * blade.p0x + 3f * inv * inv * u * blade.p1x +
                    3f * inv * u * u * blade.p2x + u * u * u * blade.p3x
                val by = inv * inv * inv * blade.p0y + 3f * inv * inv * u * blade.p1y +
                    3f * inv * u * u * blade.p2y + u * u * u * blade.p3y
                val swayAmp = (0.045f * r) * sin(u * PI.toFloat())
                val sway = sin(timeSec * swayFreq + u * 3.4f + blade.seed) * swayAmp
                pxBuf[i] = bx + sway
                pyBuf[i] = by - sway * 0.55f
            }

            for (i in 0..steps) {
                val u = i.toFloat() / steps
                val prev = (i - 1).coerceAtLeast(0)
                val next = (i + 1).coerceAtMost(steps)
                val tx = pxBuf[next] - pxBuf[prev]
                val ty = pyBuf[next] - pyBuf[prev]
                val len = hypot(tx.toDouble(), ty.toDouble()).toFloat().coerceAtLeast(1e-5f)
                val nx = -ty / len
                val ny = tx / len

                // Graceful leaf taper: rounded base to sharp tip
                val profile = sin(u * PI.toFloat()).coerceAtLeast(0f)
                val hw = blade.maxHalfWidth * sqrt(profile) * (1f - 0.35f * u)

                val lx = pxBuf[i] - nx * hw
                val ly = pyBuf[i] - ny * hw
                val rx = pxBuf[i] + nx * hw
                val ry = pyBuf[i] + ny * hw

                if (i == 0 && out.isNotEmpty()) {
                    val lastOffset = out.size - 8
                    for (k in 0 until 8) out.add(out[lastOffset + k])
                    emitVertex(out, lx, ly, u, -1f, blade.tint, 0f, 1f, blade.seed)
                }
                emitVertex(out, lx, ly, u, -1f, blade.tint, 0f, 1f, blade.seed)
                emitVertex(out, rx, ry, u, 1f, blade.tint, 0f, 1f, blade.seed)
            }
        }
        return out.toFloatArray()
    }

    private fun buildDropletSprites(timeSec: Float, radius: Float): FloatArray {
        val count = 12
        val out = ArrayList<Float>(count * 6 * 8)
        for (i in 0 until count) {
            val fi = i.toFloat()
            val orbitR = radius * (1.06f + 0.44f * ((i * 37 % 10) / 10f))
            val baseAngle = (fi / count) * 2f * PI.toFloat() + 0.35f
            val speed = 0.14f + (i % 3) * 0.05f
            val angle = baseAngle + timeSec * speed * (if (i % 2 == 0) 1f else -0.8f)
            val bob = sin(timeSec * 1.4f + fi * 1.9f) * 0.045f * radius
            val cx = cos(angle) * orbitR
            val cy = sin(angle) * orbitR * 0.88f + bob
            val size = radius * (0.045f + (i % 4) * 0.016f)
            emitSpriteQuad(out, cx, cy, size, colorMix = if (i % 3 == 0) 0.85f else 0.15f, intensity = 0.92f, seed = fi)
        }
        return out.toFloatArray()
    }

    private fun buildStarburstSprites(
        timeSec: Float,
        radius: Float,
        showSunlight: Boolean,
    ): FloatArray {
        val out = ArrayList<Float>(8 * 6 * 8)
        if (showSunlight) {
            // Hero upper-left solar flare peak
            val pulse = 0.88f + 0.12f * sin(timeSec * 2.1f)
            emitSpriteQuad(out, -0.44f * radius, 0.46f * radius, 0.56f * radius * pulse, colorMix = 0.95f, intensity = 1.25f, seed = 0f)
        }
        // 5 traveling starburst crests riding the cyan & gold orbital paths
        val starDefs = listOf(
            Triple(1.42f * radius, 0.56f * radius, 0.48f),
            Triple(1.38f * radius, 0.52f * radius, -0.54f),
            Triple(1.18f * radius, 0.44f * radius, -0.22f),
            Triple(1.52f * radius, 0.64f * radius, 0.20f),
            Triple(1.28f * radius, 0.72f * radius, -0.82f),
        )
        starDefs.forEachIndexed { idx, (rx, ry, tz) ->
            val fi = idx.toFloat()
            val theta = timeSec * (0.55f + idx * 0.11f) * (if (idx % 2 == 0) 1f else -1f) + fi * 1.37f
            val lx = rx * cos(theta)
            val ly = ry * sin(theta)
            val cosTz = cos(tz)
            val sinTz = sin(tz)
            val cosTx = cos(0.86f)
            val sx = lx * cosTz - ly * sinTz
            val sy = (lx * sinTz + ly * cosTz) * cosTx
            // Brightest when curving across front or edge horizon
            val zApprox = ly * sin(0.86f)
            val vis = (0.45f + 0.55f * ((zApprox / ry) * 0.5f + 0.5f)) * (0.72f + 0.28f * sin(timeSec * 3.1f + fi * 2.3f))
            val colorMix = if (idx % 2 == 1) 0.92f else 0.08f
            emitSpriteQuad(out, sx, sy, (0.34f + (idx % 2) * 0.09f) * radius, colorMix, vis, fi + 1f)
        }
        return out.toFloatArray()
    }

    private fun emitVertex(
        out: MutableList<Float>,
        x: Float,
        y: Float,
        u: Float,
        v: Float,
        colorMix: Float,
        depth: Float,
        alphaWeight: Float,
        seed: Float,
    ) {
        out.add(x); out.add(y); out.add(u); out.add(v)
        out.add(colorMix); out.add(depth); out.add(alphaWeight); out.add(seed)
    }

    private fun emitSpriteQuad(
        out: MutableList<Float>,
        cx: Float,
        cy: Float,
        halfSize: Float,
        colorMix: Float,
        intensity: Float,
        seed: Float,
    ) {
        fun pt(u: Float, v: Float) {
            out.add(cx + u * halfSize)
            out.add(cy + v * halfSize)
            out.add(u); out.add(v)
            out.add(colorMix); out.add(intensity); out.add(seed); out.add(halfSize)
        }
        pt(-1f, -1f); pt(1f, -1f); pt(-1f, 1f)
        pt(-1f, 1f); pt(1f, -1f); pt(1f, 1f)
    }

    override fun releaseGl(contextCurrent: Boolean) {
        if (contextCurrent) {
            backFbo?.destroy()
            if (quadVbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(quadVbo), 0)
            if (ribbonVbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(ribbonVbo), 0)
            if (spriteVbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(spriteVbo), 0)
            if (envProg != 0) GLES20.glDeleteProgram(envProg)
            if (ribbonProg != 0) GLES20.glDeleteProgram(ribbonProg)
            if (grassProg != 0) GLES20.glDeleteProgram(grassProg)
            if (sphereProg != 0) GLES20.glDeleteProgram(sphereProg)
            if (dropletProg != 0) GLES20.glDeleteProgram(dropletProg)
            if (starProg != 0) GLES20.glDeleteProgram(starProg)
        }
        bloom.release(contextCurrent)
        backFbo = null
        quadVbo = 0; ribbonVbo = 0; spriteVbo = 0
        envProg = 0; ribbonProg = 0; grassProg = 0; sphereProg = 0; dropletProg = 0; starProg = 0
        fboW = 0; fboH = 0
    }

    companion object {
        const val ORB_CONTAINER_RATIO = ORB_CRYSTAL_CONTAINER_RATIO

        private const val QUAD_VS = """
            attribute vec2 aPosition;
            varying vec2 vNdc;
            void main() {
                vNdc = aPosition;
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

        // Pass 1a: Bottom concentric water ripples, shimmering golden-cyan reflection & upward mist plume
        private const val ENV_FS = """
            precision highp float;
            varying vec2 vNdc;
            uniform float uTime;
            uniform float uWake;
            uniform float uPlume;
            uniform float uSphereRadius;

            void main() {
                vec2 p = vNdc / max(uSphereRadius, 0.001);
                vec3 col = vec3(0.0);
                float alpha = 0.0;

                // 1. Water puddle ripples & vertical shimmer reflection under orb (p.y in [-1.95, -0.78])
                vec2 ripCenter = vec2(0.0, -1.26);
                vec2 rDelta = (p - ripCenter) * vec2(0.62, 2.55);
                float rDist = length(rDelta);
                float rippleMask = smoothstep(1.35, 0.20, rDist) * smoothstep(-0.72, -1.10, p.y);
                float wave1 = pow(0.5 + 0.5 * sin(rDist * 22.0 - uTime * 3.8), 4.0);
                float wave2 = pow(0.5 + 0.5 * sin(rDist * 13.0 - uTime * 2.5 + 1.1), 3.0);
                float shimmerCol = exp(-p.x * p.x * 2.6) * smoothstep(-0.72, -1.12, p.y) * smoothstep(-2.1, -1.25, p.y);
                float rippleGlow = (wave1 * 0.42 + wave2 * 0.25) * rippleMask;
                vec3 rippleTint = mix(vec3(0.24, 0.88, 0.96), vec3(1.0, 0.84, 0.46), shimmerCol * 0.72);
                col += rippleTint * (rippleGlow + shimmerCol * 0.35) * uWake;
                alpha += clamp((rippleGlow * 0.75 + shimmerCol * 0.38) * uWake, 0.0, 0.72);

                // 2. Soft upward water-mist plume during energy release / stage 04-05
                if (uPlume > 0.001) {
                    float plumeY = smoothstep(0.15, 1.35, p.y) * smoothstep(2.48, 0.85, p.y);
                    float swayX = sin(p.y * 2.4 - uTime * 3.2) * 0.14;
                    float plumeWidth = 0.55 + 0.45 * smoothstep(0.3, 2.3, p.y);
                    float beam = exp(-pow((p.x - swayX) / plumeWidth, 2.0) * 2.2) * plumeY;
                    float wisp = 0.65 + 0.35 * sin(p.y * 7.5 - uTime * 5.2 + p.x * 3.0);
                    vec3 plumeCol = mix(vec3(0.30, 0.92, 0.96), vec3(0.96, 0.88, 0.62), smoothstep(-0.4, 0.6, p.x));
                    col += plumeCol * beam * wisp * uPlume * 0.52;
                    alpha = max(alpha, clamp(beam * wisp * uPlume * 0.48, 0.0, 0.65));
                }

                // 3. Soft outer bokeh glow behind orb
                float halo = exp(-dot(p, p) * 0.68) * 0.24 * uWake;
                col += vec3(0.16, 0.68, 0.66) * halo;
                alpha = clamp(max(alpha, halo * 0.9), 0.0, 0.85);

                gl_FragColor = vec4(col, alpha);
            }
        """

        // Glass Light Ribbon shader: hollow translucent body + crisp glowing double rims + traveling wave crests
        private const val RIBBON_FS = """
            precision highp float;
            varying vec4 vPosUv; // x, y, u, v (-1..1)
            varying vec4 vAttr;  // colorMix, depth, depthAlpha, seed
            uniform float uTime;
            uniform float uWake;
            uniform float uIsFront;

            void main() {
                float u = vPosUv.z;
                float v = abs(vPosUv.w);
                float colorMix = vAttr.x;
                float depthAlpha = vAttr.z;
                float seed = vAttr.w;

                // Smooth outer AA edge + sharp dual glass rim highlights + hollow interior
                float outerAA = 1.0 - smoothstep(0.84, 1.0, v);
                float dualRim = smoothstep(0.46, 0.86, v) * outerAA;
                float innerBody = 0.14 * (1.0 - v * v) * outerAA;
                float spine = exp(-v * v * 26.0) * 0.32;

                float travel = 0.58 + 0.42 * sin((u * 12.566 - uTime * (2.2 + seed * 0.35) + seed * 1.7));
                float alpha = (dualRim * 0.88 + innerBody + spine * travel) * depthAlpha * uWake;
                alpha = clamp(alpha, 0.0, 0.95);

                vec3 cyanCol = vec3(0.32, 0.93, 1.0);
                vec3 goldCol = vec3(1.0, 0.84, 0.45);
                vec3 baseCol = mix(cyanCol, goldCol, colorMix);
                vec3 hotCol = mix(baseCol, vec3(1.0, 0.99, 0.95), dualRim * travel * 0.72 + spine * 0.45);

                float frontBoost = mix(0.68, 1.12, uIsFront);
                gl_FragColor = vec4(hotCol * alpha * frontBoost, alpha);
            }
        """

        // Translucent Aquatic Grass Blade shader: vibrant emerald-lime gradient + luminous leaf edge veins
        private const val GRASS_FS = """
            precision highp float;
            varying vec4 vPosUv;
            varying vec4 vAttr; // tint, 0, 1, seed
            uniform float uTime;
            uniform float uWake;
            uniform float uIsFront;

            void main() {
                float u = vPosUv.z;
                float v = abs(vPosUv.w);
                float tint = vAttr.x;

                float outerAA = 1.0 - smoothstep(0.82, 1.0, v);
                float tipFade = smoothstep(0.0, 0.10, u) * smoothstep(1.0, 0.84, u);
                float edgeVein = smoothstep(0.55, 0.86, v) * outerAA;
                float centerVein = exp(-v * v * 18.0) * 0.42;

                vec3 deepEmerald = vec3(0.08, 0.44, 0.28);
                vec3 brightLime = vec3(0.45, 0.86, 0.36);
                vec3 leafCol = mix(deepEmerald, brightLime, 0.35 + 0.50 * u * tint);
                leafCol = mix(leafCol, vec3(0.82, 1.0, 0.72), edgeVein * 0.55 + centerVein * 0.45);

                float baseOpacity = mix(0.48, 0.72, uIsFront) * (0.72 + 0.28 * edgeVein);
                float a = clamp(baseOpacity * outerAA * tipFade * (0.45 + 0.55 * uWake), 0.0, 0.88);
                gl_FragColor = vec4(leafCol * a, a);
            }
        """

        // Center 3D Crystal Water Sphere: Real Back-Buffer Refraction + Dispersion + Meniscus + GPU Caustics + Solar Flare
        private const val SPHERE_FS = """
            precision highp float;
            varying vec2 vNdc;
            uniform sampler2D uBackTex;
            uniform float uTime;
            uniform float uWake;
            uniform float uSphereRadius;
            uniform float uShowSun;
            uniform float uShowCaustics;

            vec2 rot2(vec2 p, float a) {
                float c = cos(a); float s = sin(a);
                return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
            }

            float causticOctave(vec2 uv, float t, float fi) {
                vec2 p = rot2(uv, 0.42 + fi * 2.39996);
                for (int k = 0; k < 2; k++) {
                    float fk = float(k);
                    vec2 w = vec2(
                        sin(p.y * (1.22 + fi * 0.19 + fk * 0.13) + p.x * 0.25 + t * (0.42 + fi * 0.06) + fi * 1.6),
                        cos(p.x * (0.94 + fi * 0.16 + fk * 0.11) - p.y * 0.30 - t * (0.37 + fi * 0.05) + fi * 1.1)
                    );
                    p += w * (0.44 / (1.0 + fi * 0.16 + fk * 0.11));
                }
                vec2 d1 = vec2(1.0 + fi * 0.20, 0.34 + fi * 0.12);
                vec2 d2 = vec2(-0.50 - fi * 0.10, 1.10 + fi * 0.08);
                float ridge = sin(dot(p, d1) + t * 0.14) * sin(dot(p, d2) - t * 0.11);
                return 0.18 / (abs(ridge) * 5.2 + 0.20);
            }

            float causticField(vec2 uv, float t) {
                vec2 p = uv + vec2(sin(uv.y * 0.4 + t * 0.22) * 0.35, cos(uv.x * 0.35 - t * 0.18) * 0.35);
                float a = causticOctave(p, t, 0.0);
                float b = causticOctave(rot2(p * 1.618 + vec2(1.7, -1.2), 1.18), t * 0.80, 1.0);
                float c = causticOctave(rot2(p * 2.54 + vec2(2.3, -0.7), 1.54), t * 0.64, 2.0);
                return pow(clamp(max(a * 1.15, b * 0.82) + c * 0.38, 0.0, 2.1), 1.45);
            }

            void main() {
                vec2 baseUv = vNdc * 0.5 + 0.5;
                vec4 backSample = texture2D(uBackTex, baseUv);
                vec2 p = vNdc / max(uSphereRadius, 0.001);
                float r2 = dot(p, p);
                if (r2 >= 1.02) {
                    gl_FragColor = backSample;
                    return;
                }

                float r = sqrt(r2);
                float sphereAA = 1.0 - smoothstep(0.975, 1.01, r);
                float nz = sqrt(max(0.0, 1.0 - min(r2, 1.0)));
                vec3 N = normalize(vec3(p.x, p.y, nz));

                // 1. Chromatic glass refraction sampling backFbo (bends back grass & back ribbons!)
                vec2 refractShift = N.xy * (0.28 * uSphereRadius * (0.55 + 0.45 * (1.0 - nz)));
                vec2 uvR = clamp(baseUv - refractShift * 1.08, 0.0, 1.0);
                vec2 uvG = clamp(baseUv - refractShift * 1.00, 0.0, 1.0);
                vec2 uvB = clamp(baseUv - refractShift * 0.91, 0.0, 1.0);
                vec4 backR = texture2D(uBackTex, uvR);
                vec4 backG = texture2D(uBackTex, uvG);
                vec4 backB = texture2D(uBackTex, uvB);
                vec3 refractedBg = vec3(backR.r, backG.g, backB.b);
                float refractedBgA = max(backG.a, max(backR.a, backB.a));

                // 2. Deep oceanic emerald-teal water volume + curved S-meniscus wave surface
                float meniscusY = 0.09 * sin(p.x * 3.1 + uTime * 1.75) +
                    0.045 * cos(p.x * 5.4 - uTime * 1.25) - 0.10 * p.x + 0.06;
                float inLowerLiquid = 1.0 - smoothstep(meniscusY - 0.12, meniscusY + 0.10, p.y);
                float meniscusCrest = exp(-pow((p.y - meniscusY) * 18.0, 2.0)) * (1.0 - smoothstep(0.75, 0.98, r));

                vec3 deepTeal = vec3(0.02, 0.18, 0.24);
                vec3 midAqua = vec3(0.04, 0.44, 0.50);
                vec3 upperGlassTint = vec3(0.10, 0.40, 0.48);
                vec3 waterBody = mix(upperGlassTint, mix(deepTeal, midAqua, smoothstep(-0.85, 0.35, p.x - p.y * 0.4)), inLowerLiquid);

                // Sphere optical body density (rich translucent glass center, deeper color at liquid bottom)
                float bodyDensity = mix(0.62, 0.88, inLowerLiquid * 0.72 + (1.0 - nz) * 0.35);
                vec3 interior = waterBody * bodyDensity + refractedBg * (0.92 - bodyDensity * 0.42);
                interior += vec3(0.45, 0.96, 1.0) * meniscusCrest * 0.58 * uWake;

                // 3. GPU multi-octave caustics brightly lighting the lower-right basin & upper-left entry
                if (uShowCaustics > 0.5) {
                    vec2 cUv = p * 2.15 + N.xy * 0.35;
                    float cVal = causticField(cUv, uTime * 0.85);
                    float veins = smoothstep(0.22, 1.28, cVal);
                    float lowerBasin = smoothstep(0.45, -0.75, p.y) * smoothstep(-0.85, 0.75, p.x * 0.6 - p.y * 0.5);
                    vec3 cyanCaustic = vec3(0.26, 0.96, 0.94);
                    interior += cyanCaustic * veins * (0.28 + 0.72 * lowerBasin) * uWake * 0.88;
                }

                // 4. Upper-left golden solar entry flare & diagonal refractive rays
                if (uShowSun > 0.5) {
                    vec2 sunPos = vec2(-0.42, 0.44);
                    float dSun = length(p - sunPos);
                    float sunCore = exp(-dSun * dSun * 14.0);
                    float sunHalo = exp(-dSun * 3.1) * 0.62;
                    // Diagonal rays streaming toward lower-right interior
                    vec2 rayDir = normalize(vec2(0.76, -0.65));
                    vec2 rel = p - sunPos;
                    float proj = dot(rel, rayDir);
                    float perp = abs(rel.x * rayDir.y - rel.y * rayDir.x);
                    float rayMask = smoothstep(0.0, 0.3, proj) * smoothstep(1.35, 0.15, proj) * exp(-perp * 7.5);
                    vec3 goldSun = vec3(1.0, 0.88, 0.52);
                    vec3 whiteHot = vec3(1.0, 0.99, 0.92);
                    interior += goldSun * (sunHalo + rayMask * 0.38) * uWake;
                    interior += whiteHot * sunCore * 0.95 * uWake;
                }

                // 5. Double Spherical Fresnel Rim Shell (gold top-left/right -> electric cyan bottom-left/right)
                float fresnel = pow(1.0 - nz, 3.6);
                float innerThicknessRim = smoothstep(0.78, 0.90, r) * (1.0 - smoothstep(0.92, 0.985, r));
                float sunBias = clamp(dot(normalize(p + vec2(0.0001)), normalize(vec2(-0.6, 0.7))) * 0.5 + 0.5, 0.0, 1.0);
                vec3 rimCol = mix(vec3(0.38, 0.95, 1.0), vec3(1.0, 0.88, 0.54), sunBias);
                vec3 specularTop = vec3(0.92, 0.99, 1.0) * exp(-length(p - vec2(0.02, 0.62)) * 4.5) * 0.45;

                vec3 sphereRgb = interior + rimCol * (fresnel * 0.95 + innerThicknessRim * 0.34) * (0.55 + 0.45 * uWake) + specularTop;
                float sphereAlpha = clamp(max(bodyDensity, max(fresnel * 0.95, refractedBgA)), 0.0, 0.98);
                vec4 spherePremul = vec4(sphereRgb * sphereAlpha, sphereAlpha);

                // Anti-aliased blend at sphere outer edge against backFbo
                gl_FragColor = mix(backSample, spherePremul, sphereAA);
            }
        """

        // Floating Mini-Droplets shader: analytical 3D mini-sphere refraction + pinpoint highlight
        private const val DROPLET_FS = """
            precision highp float;
            varying vec2 vLocal;
            varying vec4 vAttr; // colorMix, intensity, seed, size
            uniform float uTime;
            uniform float uWake;

            void main() {
                float r2 = dot(vLocal, vLocal);
                if (r2 > 1.0) discard;
                float r = sqrt(r2);
                float edgeAA = 1.0 - smoothstep(0.84, 1.0, r);
                float nz = sqrt(max(0.0, 1.0 - r2));

                float fresnel = pow(1.0 - nz, 2.8);
                float specTopLeft = exp(-length(vLocal - vec2(-0.34, 0.36)) * 5.2);
                float crescentBottomRight = exp(-length(vLocal - vec2(0.32, -0.34)) * 3.8) * fresnel;

                vec3 tint = mix(vec3(0.45, 0.94, 1.0), vec3(1.0, 0.88, 0.55), vAttr.x);
                vec3 col = tint * (0.28 + 0.72 * fresnel + crescentBottomRight * 0.9) + vec3(1.0) * specTopLeft;
                float a = clamp((0.28 + 0.65 * fresnel + specTopLeft * 0.9) * edgeAA * vAttr.y * uWake, 0.0, 0.95);
                gl_FragColor = vec4(col * a, a);
            }
        """

        // Crisp Additive 4-Point Starburst Flare shader
        private const val STAR_FS = """
            precision highp float;
            varying vec2 vLocal;
            varying vec4 vAttr; // colorMix, intensity, seed, size
            uniform float uTime;
            uniform float uWake;

            void main() {
                float d = length(vLocal);
                if (d > 1.0) discard;
                float falloff = smoothstep(1.0, 0.0, d);
                // Crisp horizontal & vertical 4-point diffraction spikes
                float spikeH = exp(-abs(vLocal.y) * 28.0) * exp(-abs(vLocal.x) * 3.4);
                float spikeV = exp(-abs(vLocal.x) * 28.0) * exp(-abs(vLocal.y) * 3.4);
                float diag = exp(-(abs(vLocal.x - vLocal.y) * 14.0)) * 0.25 * exp(-d * 4.0);
                float core = exp(-d * d * 36.0);
                float halo = exp(-d * 4.5) * 0.35;

                float shape = (spikeH + spikeV + diag + core * 1.15 + halo) * falloff * vAttr.y * uWake;
                vec3 tint = mix(vec3(0.38, 0.94, 1.0), vec3(1.0, 0.86, 0.48), vAttr.x);
                vec3 col = mix(tint, vec3(1.0, 0.99, 0.96), clamp(core + (spikeH + spikeV) * 0.6, 0.0, 1.0));
                vec3 premul = clamp(col * shape, 0.0, 1.0);
                float a = clamp(max(premul.r, max(premul.g, premul.b)), 0.0, 1.0);
                gl_FragColor = vec4(premul, a);
            }
        """
    }
}
