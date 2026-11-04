package com.yujian.ai.ui.recognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ui.identify.DisplayRect
import com.yujian.ai.ui.identify.RecognitionImageTransform
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
import com.yujian.ai.ui.waterlight.core.EnergyEvent
import com.yujian.ai.ui.waterlight.core.EnergyFeedback
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

private const val PERIMETER_LOG_TAG = "RecognitionPerimeter"
private const val NODE_POSITION_FLOATS = RecognitionPerimeterShaderMath.SPARKLE_COUNT * 2

internal fun recognitionPerimeterOpacity(
    processingClockStarted: Boolean,
    reduceMotion: Boolean,
    frame: WaterLightFrame,
): Float {
    if (!processingClockStarted) return 0f
    val scene = frame.sceneFrame
    if (reduceMotion) return when (scene.stage) {
        RecognitionSceneStage.PHOTO_STABLE,
        RecognitionSceneStage.ORB_WAKE -> 0f
        RecognitionSceneStage.ORB_RETURN,
        RecognitionSceneStage.ORB_TO_NAME,
        RecognitionSceneStage.SPECIES_NAME_REVEAL,
        RecognitionSceneStage.RESIDUE_SETTLE -> .04f
        else -> .10f
    }
    return when (scene.stage) {
        RecognitionSceneStage.PHOTO_STABLE,
        RecognitionSceneStage.ORB_WAKE -> 0f
        RecognitionSceneStage.ORB_RELEASE -> scene.stageProgress.coerceIn(0f, 1f)
        RecognitionSceneStage.ORB_RETURN -> 1f - .82f * scene.stageProgress.coerceIn(0f, 1f)
        RecognitionSceneStage.ORB_TO_NAME -> .18f * (1f - scene.stageProgress.coerceIn(0f, 1f))
        RecognitionSceneStage.SPECIES_NAME_REVEAL -> .08f
        RecognitionSceneStage.RESIDUE_SETTLE -> .08f * (1f - scene.stageProgress.coerceIn(0f, 1f))
        else -> 1f
    }
}

internal fun recognitionPerimeterProgress(
    frame: WaterLightFrame,
    profile: RecognitionPerimeterOpticalProfileV1 = RecognitionPerimeterOpticalProfileV1.EFFECT_02,
): Float = RecognitionPerimeterShaderMath.expansionProgress(frame.sceneFrame, profile)

internal fun recognitionPerimeterActive(processingClockStarted: Boolean, phase: RecognitionPhase): Boolean =
    processingClockStarted && phase != RecognitionPhase.RESULT && phase != RecognitionPhase.FAILURE

/**
 * The TextureView renders the actual photo plus perimeter in one surface. Compose keeps the
 * unchanged photo underneath until the matching GPU frame has been presented.
 */
@Composable
internal fun RecognitionPerimeterShaderOverlay(
    waterLightFrame: WaterLightFrame,
    bitmap: Bitmap,
    photoTransform: RecognitionImageTransform,
    elapsedMs: Long,
    processingClockStarted: Boolean,
    sessionKey: Any?,
    recognitionSessionId: String,
    recognitionAttemptId: String,
    imageId: String,
    photoSha256: String,
    phase: RecognitionPhase,
    reduceMotion: Boolean = false,
    referenceMetrics: RecognitionPerimeterReferenceMetrics? = null,
    material: WaterLightMaterialProfile = WaterLightMaterialProfile.PERIMETER,
    opticalProfile: RecognitionPerimeterOpticalProfileV1 = RecognitionPerimeterOpticalProfileV1.EFFECT_02,
    energyEvent: EnergyEvent? = null,
    modifier: Modifier = Modifier,
) {
    if (waterLightFrame.sessionId != recognitionSessionId || waterLightFrame.photoSha256 != photoSha256 ||
        !waterLightFrame.sceneFrame.isIdentityValid) return
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val identity = sessionKey ?: photoTransform
    val view = remember(context, identity) { RecognitionPerimeterTextureView(context) }
    val photoRect = photoTransform.photoContentRect
    val mapping = RecognitionPerimeterShaderMath.coordinateScale(
        metrics = referenceMetrics,
        targetWidthPx = photoRect.width,
        targetHeightPx = photoRect.height,
    )
    val sourceCssWidth = referenceMetrics?.cssWidthPx ?: photoRect.width
    val sourceCssHeight = referenceMetrics?.cssHeightPx ?: photoRect.height
    val path = remember(
        sourceCssWidth,
        sourceCssHeight,
        opticalProfile.marginRatio,
        opticalProfile.cornerRadiusRatio,
        mapping.isMeasured,
    ) {
        RecognitionPerimeterShaderMath.generatePerimeterPath(
            width = sourceCssWidth,
            height = sourceCssHeight,
            margin = minOf(sourceCssWidth, sourceCssHeight) * opticalProfile.marginRatio,
            cornerRadius = minOf(sourceCssWidth, sourceCssHeight) * opticalProfile.cornerRadiusRatio,
        )
    }
    val sceneFrame = waterLightFrame.sceneFrame
    val effectiveReduceMotion = reduceMotion || waterLightFrame.reducedMotion
    val timeSec = if (effectiveReduceMotion) 0f else
        RecognitionPerimeterShaderMath.sourceTimeSec(sceneFrame, opticalProfile.speed)
    val progress = if (effectiveReduceMotion) 1f else
        recognitionPerimeterProgress(waterLightFrame, opticalProfile)
    val perimeterOpacity = recognitionPerimeterOpacity(
        processingClockStarted = processingClockStarted,
        reduceMotion = effectiveReduceMotion,
        frame = waterLightFrame,
    )
    val nodes = RecognitionPerimeterShaderMath.sparkles(
        path = path,
        timeSec = timeSec,
        activeCoverage = progress,
        pathScale = mapping.pathPxPerCssUnit,
        photoOffsetX = 0f,
        photoOffsetY = 0f,
    )
    val positions = FloatArray(NODE_POSITION_FLOATS).also { values ->
        nodes.forEachIndexed { index, node ->
            values[index * 2] = node.x
            values[index * 2 + 1] = node.y
        }
    }
    // Stage progress and model gates own its lifetime. P7 can drive opacity to zero,
    // but an absolute elapsed-time cutoff must not terminate a slow real recognition.
    val active = recognitionPerimeterActive(processingClockStarted, phase)
    val frame = PerimeterFrame(
        bitmap = bitmap,
        photoBounds = DisplayRect(0f, 0f, photoRect.width, photoRect.height),
        imageBounds = DisplayRect(photoTransform.translationX - photoRect.left,
            photoTransform.translationY - photoRect.top,
            photoTransform.translationX + photoTransform.drawnWidth - photoRect.left,
            photoTransform.translationY + photoTransform.drawnHeight - photoRect.top),
        geometryVersion = waterLightFrame.geometryVersion,
        recognitionSessionId = recognitionSessionId,
        recognitionAttemptId = recognitionAttemptId,
        imageId = imageId,
        photoSha256 = photoSha256,
        elapsedMs = elapsedMs.coerceAtLeast(0L),
        opticalReferenceElapsedMs = sceneFrame.opticalReferenceElapsedMs,
        timeSec = timeSec,
        progress = progress,
        perimeterOpacity = perimeterOpacity * material.opacity,
        shaderCoordinateScale = mapping.shaderPxPerSourceUnit,
        pathCoordinateScale = mapping.pathPxPerCssUnit,
        nodePositions = positions,
        coordinateMappingMeasured = mapping.isMeasured,
        material = material,
        opticalProfile = opticalProfile,
        energyLevel = energyEvent?.takeIf {
            it.matches(waterLightFrame) && it.imageId == imageId && it.photoSha256 == photoSha256 &&
                it.feedback in setOf(EnergyFeedback.RELEASE, EnergyFeedback.ARRIVE)
        }?.level?.coerceIn(0f, 1f) ?: 0f,
        active = active,
    )

    val density = LocalDensity.current
    AndroidView(
        factory = { view },
        modifier = Modifier.offset { IntOffset(photoRect.left.roundToInt(), photoRect.top.roundToInt()) }
            .size(with(density) { photoRect.width.toDp() }, with(density) { photoRect.height.toDp() })
            .then(modifier),
        update = { it.submit(frame) },
    )

    DisposableEffect(view, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view.setHostResumed(true)
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> view.setHostResumed(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        view.setHostResumed(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view.release()
        }
    }
}

private data class PerimeterFrame(
    val bitmap: Bitmap,
    val photoBounds: DisplayRect,
    val imageBounds: DisplayRect,
    val geometryVersion: Long,
    val recognitionSessionId: String,
    val recognitionAttemptId: String,
    val imageId: String,
    val photoSha256: String,
    val elapsedMs: Long,
    val opticalReferenceElapsedMs: Long,
    val timeSec: Float,
    val progress: Float,
    val perimeterOpacity: Float,
    val shaderCoordinateScale: Float,
    val pathCoordinateScale: Float,
    val nodePositions: FloatArray,
    val coordinateMappingMeasured: Boolean,
    val material: WaterLightMaterialProfile,
    val opticalProfile: RecognitionPerimeterOpticalProfileV1,
    val energyLevel: Float,
    val active: Boolean,
)

private class RecognitionPerimeterTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private val worker = HandlerThread("RecognitionPhotoGles").also { it.start() }
    private val handler = Handler(worker.looper)
    private val renderer = RecognitionPerimeterGlRenderer()
    private val released = AtomicBoolean(false)
    private var texture: SurfaceTexture? = null
    @Volatile private var hostResumed = false
    @Volatile private var latest: PerimeterFrame? = null
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var contextGl: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surfaceGl: EGLSurface = EGL14.EGL_NO_SURFACE
    private var surface: Surface? = null
    private var initialized = false
    private var shownIdentity: String? = null
    @Volatile private var surfaceEpoch = 0L

    init {
        alpha = 0f
        isOpaque = true
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 16f * resources.displayMetrics.density)
            }
        }
        surfaceTextureListener = this
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        isFocusable = false
        isClickable = false
        Log.i(PERIMETER_LOG_TAG, "PHOTO_COMPOSITOR=TEXTURE_VIEW_OPAQUE_PHOTO_PLUS_LIGHT")
    }

    fun submit(frame: PerimeterFrame) {
        if (released.get()) return
        val key = frameKey(frame)
        if (shownIdentity != key) {
            alpha = 0f
            shownIdentity = null
        }
        latest = frame
        render(frame)
    }

    fun setHostResumed(resumed: Boolean) {
        hostResumed = resumed
        if (resumed) latest?.let(::render) else {
            alpha = 0f
            shownIdentity = null
            Log.i(PERIMETER_LOG_TAG, "PHOTO_COMPOSE_FALLBACK=LIFECYCLE_PAUSED")
        }
    }

    private fun render(frame: PerimeterFrame) {
        if (!hostResumed || released.get() || !isAvailable) return
        val currentTexture = texture ?: return
        val epoch = surfaceEpoch
        val frameIdentity = frameKey(frame)
        handler.post {
            if (released.get() || !hostResumed || currentTexture !== texture || epoch != surfaceEpoch ||
                latest?.let(::frameKey) != frameIdentity) return@post
            if (!initialized && !initGl(currentTexture)) return@post
            renderer.submit(frame)
            renderer.onSurfaceChanged(width.coerceAtLeast(1), height.coerceAtLeast(1))
            val drawn = renderer.onDrawFrame()
            val swapped = drawn && EGL14.eglSwapBuffers(display, surfaceGl)
            Log.i(PERIMETER_LOG_TAG, "PHOTO_GPU_SWAP=${if (swapped) "PASS" else "FAIL"} " +
                "drawn=$drawn eglError=0x${EGL14.eglGetError().toString(16)} identity=$frameIdentity")
            if (swapped) {
                post {
                    if (!released.get() && hostResumed && isAvailable && epoch == surfaceEpoch &&
                        currentTexture === texture && latest?.let(::frameKey) == frameIdentity) {
                        val first = shownIdentity != frameIdentity
                        shownIdentity = frameIdentity
                        alpha = 1f
                        if (first) Log.i(PERIMETER_LOG_TAG, "PHOTO_GPU_FIRST_FRAME=PASS identity=$frameIdentity")
                        Log.i(PERIMETER_LOG_TAG, "PHOTO_OWNER=GPU identity=$frameIdentity")
                    }
                }
            } else {
                post { if (epoch == surfaceEpoch && latest?.let(::frameKey) == frameIdentity) alpha = 0f }
                Log.e(PERIMETER_LOG_TAG, "PHOTO_COMPOSE_FALLBACK=GPU_DRAW_OR_SWAP_FAILED identity=$frameIdentity")
            }
        }
    }

    private fun initGl(surfaceTexture: SurfaceTexture): Boolean {
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY || !EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0)) return failInit("DISPLAY")
            val configAttrs = intArrayOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE)
            val configs = arrayOfNulls<EGLConfig>(1)
            if (!EGL14.eglChooseConfig(display, configAttrs, 0, configs, 0, 1, IntArray(1), 0)) return failInit("CONFIG")
            val config = configs[0] ?: return failInit("CONFIG_EMPTY")
            contextGl = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            if (contextGl == EGL14.EGL_NO_CONTEXT) return failInit("CONTEXT")
            surface = Surface(surfaceTexture)
            surfaceGl = EGL14.eglCreateWindowSurface(display, config, surface,
                intArrayOf(EGL14.EGL_NONE), 0)
            if (surfaceGl == EGL14.EGL_NO_SURFACE || !EGL14.eglMakeCurrent(display, surfaceGl, surfaceGl, contextGl)) return failInit("SURFACE")
            renderer.onSurfaceCreated()
            initialized = true
            Log.i(PERIMETER_LOG_TAG, "PHOTO_GPU_INIT=PASS")
            return true
        } catch (error: Throwable) {
            Log.e(PERIMETER_LOG_TAG, "PHOTO_GPU_INIT=FAIL", error)
            return failInit(error.javaClass.simpleName)
        }
    }

    private fun failInit(reason: String): Boolean {
        Log.e(PERIMETER_LOG_TAG, "PHOTO_GPU_INIT=FAIL reason=$reason eglError=0x${EGL14.eglGetError().toString(16)}")
        releaseGl()
        post { alpha = 0f }
        return false
    }

    private fun releaseGl() {
        renderer.releaseGl(initialized)
        initialized = false
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surfaceGl != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surfaceGl)
            if (contextGl != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, contextGl)
            EGL14.eglTerminate(display)
        }
        surface?.release(); surface = null
        display = EGL14.EGL_NO_DISPLAY
        surfaceGl = EGL14.EGL_NO_SURFACE
        contextGl = EGL14.EGL_NO_CONTEXT
        Log.i(PERIMETER_LOG_TAG, "PHOTO_GPU_RELEASE=PASS")
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        surfaceEpoch++
        texture = surface
        latest?.let(::render)
    }
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        latest?.let(::render)
    }
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        alpha = 0f
        surfaceEpoch++
        shownIdentity = null
        texture = null
        handler.post { releaseGl() }
        return true
    }
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    fun release() {
        if (!released.compareAndSet(false, true)) return
        alpha = 0f
        surfaceEpoch++
        shownIdentity = null
        latest = null
        texture = null
        handler.post { releaseGl(); worker.quitSafely() }
    }
    private fun frameKey(frame: PerimeterFrame): String =
        "${frame.recognitionSessionId}:${frame.recognitionAttemptId}:${frame.imageId}:" +
            "${frame.photoSha256}:${frame.geometryVersion}"
}

private class RecognitionPerimeterGlRenderer {
    private val frameRef = AtomicReference<PerimeterFrame?>(null)
    private var program = 0
    private var surfaceWidth = 1
    private var surfaceHeight = 1
    private var lastMappingLogKey: String? = null
    private var firstOpticalFrameLogged = false
    private var uniformLocations: Map<String, Int> = emptyMap()
    private var positionBuffer = 0
    private var photoTexture = 0
    private var uploadedBitmap: Bitmap? = null
    private var uploadedIdentity: String? = null
    private val vertices = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)

    fun submit(frame: PerimeterFrame) {
        frameRef.set(frame)
    }

    fun onSurfaceCreated() {
        firstOpticalFrameLogged = false
        uploadedBitmap = null
        uploadedIdentity = null
        val frameIdentity = frameRef.get()?.let(::identityLabel).orEmpty()
        Log.i(TAG, "GLES_VERSION=${GLES20.glGetString(GLES20.GL_VERSION)} $frameIdentity")
        Log.i(TAG, "GL_RENDERER=${GLES20.glGetString(GLES20.GL_RENDERER)} $frameIdentity")
        Log.i(TAG, "GLSL_VERSION=${GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION)} $frameIdentity")
        val bufferIds = IntArray(1)
        GLES20.glGenBuffers(1, bufferIds, 0)
        positionBuffer = bufferIds[0]
        val data = ByteBuffer.allocateDirect(vertices.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        data.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, positionBuffer)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            vertices.size * FLOAT_SIZE_BYTES,
            data,
            GLES20.GL_STATIC_DRAW,
        )
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        photoTexture = textureIds[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, photoTexture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        val shaderStartedAtMs = android.os.SystemClock.uptimeMillis()
        program = createProgram(VERTEX_SHADER, PERIMETER_FRAGMENT_SHADER, "perimeter")
        if (program != 0) {
            uniformLocations = buildList {
                addAll(listOf(
                    "uPhoto", "uViewport", "uPhotoBounds", "uImageRect", "uMotion",
                    "uPerimeterOpacity", "uEnergyLevel", "uNodePositions",
                    "uGoldColor", "uCyanColor", "uGreenColor",
                    "uSparkleGoldCoreColor", "uSparkleCyanCoreColor",
                ))
                repeat(10) { add("uProfile$it") }
            }.associateWith { GLES20.glGetUniformLocation(program, it) }
        }
        val shaderFinishedAtMs = android.os.SystemClock.uptimeMillis()
        if (program != 0) {
            Log.i(TAG, "PERIMETER_SHADER_COMPILE=PASS demo_source=$DEMO_SOURCE_SHA started_uptime_ms=$shaderStartedAtMs completed_uptime_ms=$shaderFinishedAtMs $frameIdentity")
            Log.i(TAG, "PERIMETER_SHADER_LINK=PASS uptime_ms=$shaderFinishedAtMs $frameIdentity")
        } else {
            Log.e(TAG, "PERIMETER_SHADER_COMPILE=FAIL; transparent photo-safe fallback started_uptime_ms=$shaderStartedAtMs completed_uptime_ms=$shaderFinishedAtMs $frameIdentity")
        }
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        Log.i(TAG, "PHOTO_TEXTURE_ROLE=OPAQUE_PHOTO_PLUS_PERIMETER; Compose remains fallback $frameIdentity")
        checkGl("surface-created", frameIdentity)
        Log.i(TAG, "PERIMETER_GL_SURFACE_CREATED uptime_ms=${android.os.SystemClock.uptimeMillis()} $frameIdentity")
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
        Log.i(TAG, "RENDER_SIZE=${surfaceWidth}x$surfaceHeight backend=GLES2 ${frameRef.get()?.let(::identityLabel).orEmpty()}")
        frameRef.get()?.let(::logMappingIfChanged)
    }

    fun onDrawFrame(): Boolean {
        val frame = frameRef.get()
        if (frame == null) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            return false
        }
        if (!uploadBitmapIfNeeded(frame)) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            return false
        }
        logMappingIfChanged(frame)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program != 0) {
            if (!firstOpticalFrameLogged) {
                firstOpticalFrameLogged = true
                Log.i(TAG, "PERIMETER_FIRST_OPTICAL_FRAME processing_elapsed_ms=${frame.elapsedMs} optical_reference_ms=${frame.opticalReferenceElapsedMs} uptime_ms=${android.os.SystemClock.uptimeMillis()} ${identityLabel(frame)}")
            }
            return drawPerimeter(frame)
        }
        Log.e(TAG, "PHOTO_GPU_DRAW=FAIL reason=PROGRAM_UNAVAILABLE ${identityLabel(frame)}")
        return false
    }

    fun releaseGl(contextCurrent: Boolean) {
        frameRef.set(null)
        uploadedBitmap = null
        uploadedIdentity = null
        if (contextCurrent) deleteGlObjects() else {
            positionBuffer = 0
            photoTexture = 0
            program = 0
        }
    }

    private fun drawPerimeter(frame: PerimeterFrame): Boolean {
        val started = android.os.SystemClock.uptimeMillis()
        val p = program
        GLES20.glUseProgram(p)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, photoTexture)
        GLES20.glUniform1i(uniform("uPhoto"), 0)
        bindQuad(p)
        setCommonUniforms(p, frame)
        GLES20.glUniform4fv(uniform("uNodePositions"), 8, frame.nodePositions, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        val error = GLES20.glGetError()
        val success = error == GLES20.GL_NO_ERROR
        Log.i(TAG, "PHOTO_GPU_DRAW=${if (success) "PASS" else "FAIL"} error=0x${error.toString(16)} " +
            "duration_ms=${android.os.SystemClock.uptimeMillis() - started} ${identityLabel(frame)}")
        return success
    }

    private fun uploadBitmapIfNeeded(frame: PerimeterFrame): Boolean {
        if (frame.bitmap.isRecycled || photoTexture == 0) {
            Log.e(TAG, "PHOTO_TEXTURE_UPLOAD=FAIL reason=${if (frame.bitmap.isRecycled) "BITMAP_RECYCLED" else "TEXTURE_UNAVAILABLE"} ${identityLabel(frame)}")
            return false
        }
        val identity = identityLabel(frame) + ":bitmapGeneration=${frame.bitmap.generationId}"
        if (frame.bitmap === uploadedBitmap && uploadedIdentity == identity) return true
        val uploadStartedAtMs = android.os.SystemClock.uptimeMillis()
        return try {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, photoTexture)
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, frame.bitmap, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            val error = GLES20.glGetError()
            if (error != GLES20.GL_NO_ERROR) {
                Log.e(TAG, "PHOTO_TEXTURE_UPLOAD=FAIL error=0x${error.toString(16)} started_uptime_ms=$uploadStartedAtMs completed_uptime_ms=${android.os.SystemClock.uptimeMillis()} ${identityLabel(frame)}")
                false
            } else {
                uploadedBitmap = frame.bitmap
                uploadedIdentity = identity
                Log.i(TAG, "PHOTO_TEXTURE_UPLOAD=PASS size=${frame.bitmap.width}x${frame.bitmap.height} duration_ms=${android.os.SystemClock.uptimeMillis() - uploadStartedAtMs} ${identityLabel(frame)}")
                true
            }
        } catch (error: Throwable) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            Log.e(TAG, "PHOTO_TEXTURE_UPLOAD=FAIL reason=${error.javaClass.simpleName} started_uptime_ms=$uploadStartedAtMs completed_uptime_ms=${android.os.SystemClock.uptimeMillis()} ${identityLabel(frame)}", error)
            false
        }
    }

    private fun bindQuad(programId: Int) {
        val location = GLES20.glGetAttribLocation(programId, "aPosition")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, positionBuffer)
        GLES20.glEnableVertexAttribArray(location)
        GLES20.glVertexAttribPointer(location, 2, GLES20.GL_FLOAT, false, 0, 0)
    }

    private fun setCommonUniforms(programId: Int, frame: PerimeterFrame) {
        fun rgb(value: Long): FloatArray = floatArrayOf(
            ((value shr 16) and 255).toFloat() / 255f,
            ((value shr 8) and 255).toFloat() / 255f,
            (value and 255).toFloat() / 255f,
        )
        val optical = frame.opticalProfile
        GLES20.glUniform3fv(uniform("uGoldColor"), 1, rgb(optical.goldColor), 0)
        GLES20.glUniform3fv(uniform("uCyanColor"), 1, rgb(optical.cyanColor), 0)
        GLES20.glUniform3fv(uniform("uGreenColor"), 1, rgb(optical.greenColor), 0)
        GLES20.glUniform3fv(uniform("uSparkleGoldCoreColor"), 1, rgb(optical.sparkleGoldCoreColor), 0)
        GLES20.glUniform3fv(uniform("uSparkleCyanCoreColor"), 1, rgb(optical.sparkleCyanCoreColor), 0)
        GLES20.glUniform2f(
            uniform("uViewport"),
            surfaceWidth.toFloat(),
            surfaceHeight.toFloat(),
        )
        GLES20.glUniform4f(
            uniform("uPhotoBounds"),
            frame.photoBounds.left,
            frame.photoBounds.top,
            frame.photoBounds.width,
            frame.photoBounds.height,
        )
        GLES20.glUniform4f(uniform("uImageRect"),
            frame.imageBounds.left, frame.imageBounds.top,
            frame.imageBounds.width, frame.imageBounds.height)
        GLES20.glUniform1f(
            uniform("uPerimeterOpacity"),
            frame.perimeterOpacity.coerceIn(0f, 1f),
        )
        GLES20.glUniform4f(
            uniform("uMotion"),
            frame.timeSec,
            frame.progress,
            frame.shaderCoordinateScale,
            frame.pathCoordinateScale,
        )
        GLES20.glUniform1f(
            uniform("uEnergyLevel"),
            frame.energyLevel.coerceIn(0f, 1f),
        )
        val p = optical
        GLES20.glUniform4f(uniform("uProfile0"), p.intensity, p.speed, p.marginRatio, p.cornerRadiusRatio)
        GLES20.glUniform4f(uniform("uProfile1"), p.wave1Frequency, p.wave1AmplitudeSourcePx, p.wave1Speed, p.wave2Frequency)
        GLES20.glUniform4f(uniform("uProfile2"), p.wave2AmplitudeSourcePx, p.wave2Speed, p.glowSizeSourcePx, p.wideHaloDecay)
        GLES20.glUniform4f(uniform("uProfile3"), p.wideHaloGlowFactor, p.wideHaloWeight, p.mediumBloomDecay, p.mediumBloomGlowFactor)
        GLES20.glUniform4f(uniform("uProfile4"), p.mediumBloomWeight, p.sharpCoreNear, p.sharpCoreFar, p.sharpCoreWeight)
        GLES20.glUniform4f(uniform("uProfile5"), p.bottomMaskStrength, p.blendRatio, p.bottomMaskFloor, p.bottomGradientLift)
        GLES20.glUniform4f(uniform("uProfile6"), p.bottomGradientExponent, p.causticsUvScale, p.bottomContributionScale, p.perimeterContributionScale)
        GLES20.glUniform4f(uniform("uProfile7"), p.specularStart, p.specularEnd, p.specularMix, p.specularBoost)
        GLES20.glUniform4f(uniform("uProfile8"), p.sparkleBaseSizeCssPx, p.sparkleSizeStepCssPx, p.sparkleHaloScale, p.sparkleCoreScale)
        GLES20.glUniform4f(uniform("uProfile9"), p.sparkleHaloOpacity, p.sparkleCoreOpacity, p.flourishHalfWidthCssPx, p.flourishPeakHeightCssPx)
    }

    private fun uniform(name: String): Int = uniformLocations[name] ?: -1

    private fun logMappingIfChanged(frame: PerimeterFrame) {
        val key = "${frame.photoBounds.width}x${frame.photoBounds.height}:" +
            "${frame.shaderCoordinateScale}:${frame.pathCoordinateScale}:${frame.coordinateMappingMeasured}"
        if (key == lastMappingLogKey) return
        lastMappingLogKey = key
        Log.i(
            TAG,
            "${identityLabel(frame)} PHOTO_CONTENT_RECT=${frame.photoBounds.left},${frame.photoBounds.top}," +
                "${frame.photoBounds.width},${frame.photoBounds.height} viewport=${surfaceWidth}x$surfaceHeight " +
                "processing_elapsed_ms=${frame.elapsedMs} optical_reference_ms=${frame.opticalReferenceElapsedMs} " +
                "source_coordinate_scale=${frame.shaderCoordinateScale} " +
                "path_css_scale=${frame.pathCoordinateScale} measured=${frame.coordinateMappingMeasured}",
        )
        Log.i(TAG, "OPTICAL_PROFILE=${frame.opticalProfile.version} blend=${frame.opticalProfile.blendMode} margin=${frame.opticalProfile.marginRatio} corner=${frame.opticalProfile.cornerRadiusRatio} glow_source_px=${frame.opticalProfile.glowSizeSourcePx}")
        if (!frame.coordinateMappingMeasured) {
            Log.w(TAG, "${identityLabel(frame)} COORDINATE_MAPPING=NOT_VERIFIED; identity candidate only; source DPR/CSS/backing dimensions not supplied")
        }
    }

    private fun identityLabel(frame: PerimeterFrame): String =
        "session=${frame.recognitionSessionId} attempt=${frame.recognitionAttemptId} " +
            "image=${frame.imageId} photoSha256=${frame.photoSha256}"

    private fun deleteGlObjects() {
        if (positionBuffer != 0) GLES20.glDeleteBuffers(1, intArrayOf(positionBuffer), 0)
        if (program != 0) GLES20.glDeleteProgram(program)
        if (photoTexture != 0) GLES20.glDeleteTextures(1, intArrayOf(photoTexture), 0)
        positionBuffer = 0
        program = 0
        photoTexture = 0
        uploadedBitmap = null
        uploadedIdentity = null
        uniformLocations = emptyMap()
    }

    private fun checkGl(stage: String, identity: String = "") {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) Log.e(TAG, "GLES_ERROR stage=$stage code=0x${error.toString(16)} $identity")
    }

    companion object {
        private const val FLOAT_SIZE_BYTES = 4
        private const val TAG = "RecognitionPerimeter"
        private const val DEMO_SOURCE_SHA = "a790f145620ab32f96fe525bc38dd902653bd960"

        private const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        /** Port of the Demo's final WebGL shader plus its 2D accents in the same GLES2 pass. */
        private const val PERIMETER_FRAGMENT_SHADER = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif

            uniform sampler2D uPhoto;
            uniform vec2 uViewport;
            uniform vec4 uPhotoBounds;
            uniform vec4 uImageRect;
            // x=timeSec, y=bottom-up expansion, z=target px/source backing px, w=target px/source CSS px.
            uniform vec4 uMotion;
            uniform float uPerimeterOpacity;
            uniform float uEnergyLevel;
            uniform vec4 uNodePositions[8];
            uniform vec3 uGoldColor;
            uniform vec3 uCyanColor;
            uniform vec3 uGreenColor;
            uniform vec3 uSparkleGoldCoreColor;
            uniform vec3 uSparkleCyanCoreColor;
            // Profile uniforms, packed to keep GLES2 uniform binding compact.
            uniform vec4 uProfile0; // intensity, speed, margin ratio, corner radius ratio
            uniform vec4 uProfile1; // wave 1 frequency/amplitude/speed, wave 2 frequency
            uniform vec4 uProfile2; // wave 2 amplitude/speed, glow size, wide halo decay
            uniform vec4 uProfile3; // wide halo glow factor/weight, bloom decay/glow factor
            uniform vec4 uProfile4; // bloom weight, sharp near/far/weight
            uniform vec4 uProfile5; // bottom mask strength, blend ratio, mask floor, gradient lift
            uniform vec4 uProfile6; // gradient exponent, caustic UV scale, bottom/perimeter contribution
            uniform vec4 uProfile7; // specular start/end/mix/boost
            uniform vec4 uProfile8; // sparkle size/step/halo/core
            uniform vec4 uProfile9; // halo/core opacity, flourish half width/height

            const float PI = 3.14159265358979323846;

            vec2 rotate(vec2 p, float a) {
                float c = cos(a);
                float s = sin(a);
                return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
            }

            float sdRoundedBox(vec2 p, vec2 b, vec4 r) {
                r.xy = (p.x > 0.0) ? r.xy : r.zw;
                r.x = (p.y > 0.0) ? r.x : r.y;
                vec2 q = abs(p) - b + r.x;
                return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
            }

            vec3 overlayBlend(vec3 backdrop, vec3 source) {
                vec3 low = 2.0 * backdrop * source;
                vec3 high = 1.0 - 2.0 * (1.0 - backdrop) * (1.0 - source);
                return mix(low, high, step(vec3(0.5), backdrop));
            }

            float hash(vec2 p) {
                p = fract(p * vec2(123.34, 456.21));
                p += dot(p, p + 45.32);
                return fract(p.x * p.y);
            }

            float causticOctave(vec2 uv, float t, float fi) {
                vec2 p = rotate(uv, 0.45 + fi * 2.39996);
                for (int k = 0; k < 2; k++) {
                    float fk = float(k);
                    vec2 w = vec2(
                        sin(p.y * (1.18 + fi * 0.17 + fk * 0.12) + p.x * (0.24 + fi * 0.08) + t * (0.42 + fi * 0.06) + fi * 1.57 + fk),
                        cos(p.x * (0.92 + fi * 0.15 + fk * 0.10) - p.y * (0.31 + fi * 0.08) - t * (0.38 + fi * 0.05) + fi * 1.18)
                    );
                    p += w * (0.45 / (1.0 + fi * 0.18 + fk * 0.12));
                }
                vec2 d1 = vec2(1.02 + fi * 0.19, 0.35 + fi * 0.11);
                vec2 d2 = vec2(-0.48 - fi * 0.09, 1.12 + fi * 0.07);
                float ridge = sin(dot(p, d1) + t * 0.14) * sin(dot(p, d2) - t * 0.11);
                return 0.17 / (abs(ridge) * 5.4 + 0.22);
            }

            float causticField(vec2 uv, float t) {
                vec2 p = uv + vec2(
                    sin(uv.y * 0.42 + t * 0.24) * 0.38,
                    cos(uv.x * 0.36 - t * 0.18) * 0.38
                );
                float a = causticOctave(p, t, 0.0);
                vec2 p2 = rotate(p * 1.618 + vec2(1.72, -1.18), 1.15);
                float b = causticOctave(p2, t * 0.82, 1.0);
                vec2 p3 = rotate(p * 2.54 + vec2(2.31, -0.65), 1.52);
                float c = causticOctave(p3, t * 0.65, 2.0);
                float acc = max(a * 1.15, b * 0.82) + c * 0.38;
                return pow(clamp(acc, 0.0, 2.0), 1.35);
            }

            float lightSpotsField(vec2 uv, float t) {
                vec2 p = uv * 3.6;
                vec2 ip = floor(p);
                vec2 fp = fract(p) - 0.5;
                float minDist = 1.0;
                for (int y = -1; y <= 1; y++) {
                    for (int x = -1; x <= 1; x++) {
                        vec2 neighbor = vec2(float(x), float(y));
                        vec2 cellId = ip + neighbor;
                        float h = hash(cellId);
                        vec2 offset = vec2(
                            sin(t * 1.25 + h * 6.28) * 0.32,
                            cos(t * 1.55 + h * 6.28) * 0.32
                        );
                        minDist = min(minDist, length(neighbor + offset - fp));
                    }
                }
                float spot = 1.0 - smoothstep(0.08, 0.65, minDist);
                return pow(spot, 1.8);
            }

            vec2 nodePosition(int index) {
                if (index == 0) return uNodePositions[0].xy;
                if (index == 1) return uNodePositions[0].zw;
                if (index == 2) return uNodePositions[1].xy;
                if (index == 3) return uNodePositions[1].zw;
                if (index == 4) return uNodePositions[2].xy;
                if (index == 5) return uNodePositions[2].zw;
                if (index == 6) return uNodePositions[3].xy;
                if (index == 7) return uNodePositions[3].zw;
                if (index == 8) return uNodePositions[4].xy;
                if (index == 9) return uNodePositions[4].zw;
                if (index == 10) return uNodePositions[5].xy;
                if (index == 11) return uNodePositions[5].zw;
                if (index == 12) return uNodePositions[6].xy;
                if (index == 13) return uNodePositions[6].zw;
                if (index == 14) return uNodePositions[7].xy;
                return uNodePositions[7].zw;
            }

            float seededPhase(float index) {
                vec2 p = fract(vec2(index, index + 19.19) * vec2(123.34, 456.21));
                p += dot(p, p + 45.32);
                return fract(p.x * p.y) * (2.0 * PI);
            }

            float circleCoverage(float distanceToCenter, float radius, float aaWidth) {
                return 1.0 - smoothstep(max(0.0, radius - aaWidth), radius + aaWidth, distanceToCenter);
            }

            float segmentDistance(vec2 p, vec2 a, vec2 b) {
                vec2 ab = b - a;
                float h = clamp(dot(p - a, ab) / max(dot(ab, ab), 0.0001), 0.0, 1.0);
                return length(p - a - h * ab);
            }

            void screenOver(inout vec3 dstPremul, inout float dstAlpha, vec3 sourceColor, float sourceAlpha) {
                float a = clamp(sourceAlpha, 0.0, 1.0);
                dstPremul = sourceColor * a + dstPremul * (1.0 - a);
                dstAlpha = a + dstAlpha * (1.0 - a);
            }

            void main() {
                vec2 pTop = vec2(gl_FragCoord.x, uViewport.y - gl_FragCoord.y);
                bool insidePhoto = pTop.x >= uPhotoBounds.x && pTop.y >= uPhotoBounds.y &&
                    pTop.x <= uPhotoBounds.x + uPhotoBounds.z &&
                    pTop.y <= uPhotoBounds.y + uPhotoBounds.w;
                if (!insidePhoto) {
                    gl_FragColor = vec4(0.0);
                    return;
                }
                vec2 photoUv = (pTop - uImageRect.xy) / max(uImageRect.zw, vec2(1.0));
                vec3 base = texture2D(uPhoto, photoUv).rgb;

                // Source shader coordinates are bottom-left-origin WebGL backing pixels.
                float sourceScale = max(uMotion.z, 0.0001);
                vec2 frag = vec2(pTop.x - uPhotoBounds.x, uPhotoBounds.y + uPhotoBounds.w - pTop.y) / sourceScale;
                vec2 res = uPhotoBounds.zw / sourceScale;
                vec2 uv = (frag * 2.0 - res) / max(res.y, 1.0);
                float normY = frag.y / max(res.y, 1.0);
                float t = uMotion.x * 0.42;

                float revealedMask = uMotion.y >= 1.0
                    ? 1.0
                    : smoothstep(normY - 0.14, normY + 0.02, uMotion.y * 1.08 - 0.04);
                revealedMask = clamp(revealedMask, 0.0, 1.0);

                vec2 halfRes = res * 0.5;
                vec2 centeredPos = frag - halfRes;
                float shortSide = min(res.x, res.y);
                float margin = shortSide * uProfile0.z;
                vec2 boxHalf = halfRes - vec2(margin);
                float cornerR = shortSide * uProfile0.w;
                float perimAngle = atan(centeredPos.y, centeredPos.x);
                float perimWave = sin(perimAngle * uProfile1.x + t * uProfile1.z) * uProfile1.y +
                    cos(perimAngle * uProfile1.w - t * uProfile2.y) * uProfile2.x;
                float dBox = abs(sdRoundedBox(centeredPos, boxHalf, vec4(cornerR)) + perimWave);

                float effectiveGlow = max(8.0, uProfile2.z);
                float wideHalo = exp(-dBox * (uProfile2.w / (effectiveGlow * uProfile3.x)));
                float mediumBloom = exp(-dBox * (uProfile3.z / (effectiveGlow * uProfile3.w)));
                float sharpCore = 1.0 - smoothstep(uProfile4.y, uProfile4.z, dBox);
                float perimeterAura = (wideHalo * uProfile3.y + mediumBloom * uProfile4.x + sharpCore * uProfile4.w) * revealedMask;

                float filamentAngleT = (perimAngle + PI) / (2.0 * PI);
                float filamentGoldBias = smoothstep(0.18, 0.82, sin(filamentAngleT * PI + 0.7));
                vec3 perimeterColor = mix(uCyanColor, uGoldColor, filamentGoldBias);
                perimeterColor = mix(perimeterColor, uGreenColor, sin(filamentAngleT * 6.28 + t * 0.5) * 0.2 + 0.2);

                // Demo's three-octave domain-warped caustics, bottom-up mask and sunlight spots.
                float bottomGrad = pow(clamp(1.0 - normY * uProfile5.w, 0.0, 1.0), uProfile6.x);
                float causticsMask = mix(uProfile5.z, 1.0, bottomGrad * uProfile5.x) * revealedMask;
                float cVal = causticField(uv * uProfile6.y, t);
                float veins = smoothstep(0.16, 1.10, cVal);
                float haze = smoothstep(0.03, 0.78, cVal) * 0.35;
                float causticGlow = veins * 0.92 + haze;
                float spots = lightSpotsField(uv * 1.25, t * 0.85);
                float waterLight = causticGlow * 0.88 + spots * 0.95;
                float colorFlow = sin(uv.x * 2.2 + uv.y * 1.5 + t * 0.75) * 0.5 + 0.5;
                vec3 waterSpectrum = mix(uCyanColor, uGreenColor, clamp(colorFlow * 0.85 + 0.1, 0.0, 1.0));
                float sunCatch = smoothstep(0.28, 0.88, waterLight * 0.55 + colorFlow * 0.45);
                vec3 bottomCausticColor = mix(waterSpectrum, uGoldColor, clamp(sunCatch * uProfile5.y, 0.0, 1.0));

                float bottomContrib = waterLight * causticsMask * uProfile6.z;
                float perimContrib = perimeterAura * uProfile6.w;
                float totalLight = (bottomContrib + perimContrib) * uProfile0.x;
                float finalAlpha = clamp(totalLight, 0.0, 0.96);
                float totalWeight = max(0.001, bottomContrib + perimContrib);
                vec3 finalRgb = (bottomCausticColor * bottomContrib + perimeterColor * perimContrib) / totalWeight;
                float coreSpark = smoothstep(uProfile7.x, uProfile7.y, totalLight);
                finalRgb = mix(finalRgb, vec3(0.99, 0.99, 0.96), coreSpark * uProfile7.z);
                finalRgb = mix(finalRgb, vec3(0.99, 0.99, 0.96), clamp(uEnergyLevel * 0.08, 0.0, 0.08));
                finalRgb *= (1.0 + coreSpark * uProfile7.w);

                // Demo WebGL canvas is SRC_ALPHA composited, then CSS Overlay blended over the photo.
                float shaderAlpha = clamp(finalAlpha * finalAlpha, 0.0, 1.0) * uPerimeterOpacity;
                vec3 perimeterComposite = mix(base, overlayBlend(base, clamp(finalRgb, 0.0, 1.0)), shaderAlpha);

                // The existing 16 nodes keep the Demo's two expanding bottom branches and screen-style cores.
                vec3 accentPremul = vec3(0.0);
                float accentAlpha = 0.0;
                float edgeAa = max(0.0001, sourceScale * 0.5);
                if (uMotion.y > 0.05) {
                    for (int i = 0; i < 16; i++) {
                        vec2 node = nodePosition(i);
                        if (node.x > -1000.0 && node.y > -1000.0) {
                            float fi = float(i);
                            float nodeSpeed = 0.012 + mod(fi, 3.0) * 0.007;
                            float pPos = mod(fi / 16.0 + uMotion.x * nodeSpeed, 1.0);
                            float pulse = 0.65 + 0.35 * sin(uMotion.x * 3.4 + seededPhase(fi));
                            bool isGold = pPos > 0.45;
                            vec3 glowColor = isGold ? uGoldColor : uCyanColor;
                            vec3 coreColor = isGold ? uSparkleGoldCoreColor : uSparkleCyanCoreColor;
                            float nodeSize = (uProfile8.x + mod(fi, 4.0) * uProfile8.y) * uMotion.w;
                            float haloCoverage = circleCoverage(length(pTop - node), nodeSize * uProfile8.z * pulse, edgeAa);
                            screenOver(accentPremul, accentAlpha, glowColor,
                                uProfile9.x * pulse * uProfile0.x * haloCoverage * uPerimeterOpacity);
                            float coreAlpha = min(1.0, uProfile9.y * pulse * uProfile0.x);
                            float coreCoverage = circleCoverage(length(pTop - node), nodeSize * uProfile8.w * pulse, edgeAa);
                            screenOver(accentPremul, accentAlpha, coreColor, coreAlpha * coreCoverage * uPerimeterOpacity);
                        }
                    }
                }

                // Demo bottom-center white core, symmetric gold water lines and pearl tips.
                float flourishReveal = smoothstep(0.15, 0.30, uMotion.y) * uPerimeterOpacity;
                if (flourishReveal > 0.001) {
                    float cssScale = max(uMotion.w, 0.0001);
                    vec2 localCss = (pTop - uPhotoBounds.xy) / cssScale;
                    float cssShortSide = min(uPhotoBounds.z, uPhotoBounds.w) / cssScale;
                    vec2 center = vec2(uPhotoBounds.z / cssScale * 0.5,
                        uPhotoBounds.w / cssScale - cssShortSide * uProfile0.z);
                    float bPulse = 0.75 + 0.25 * sin(uMotion.x * 2.4);
                    float centerDist = length(localCss - center);
                    float centerAa = edgeAa / cssScale;
                    float centerHalo = circleCoverage(centerDist, 8.0, centerAa);
                    screenOver(accentPremul, accentAlpha, uGoldColor, centerHalo * 0.22 * flourishReveal);
                    float centerCore = circleCoverage(centerDist, 3.0 * bPulse, centerAa);
                    screenOver(accentPremul, accentAlpha, vec3(1.0), centerCore * min(1.0, 0.95 * uProfile0.x) * flourishReveal);

                    float leftDistance = 100000.0;
                    float rightDistance = 100000.0;
                    vec2 leftPrev = center + vec2(-uProfile9.z, 0.0);
                    vec2 rightPrev = center + vec2(6.0, 0.0);
                    for (int j = 1; j <= 12; j++) {
                        float q = float(j) / 12.0;
                        vec2 leftNext = mix(mix(center + vec2(-uProfile9.z, 0.0), center + vec2(-uProfile9.z * 0.5, -2.0 * uProfile9.w), q),
                            mix(center + vec2(-uProfile9.z * 0.5, -2.0 * uProfile9.w), center + vec2(-6.0, 0.0), q), q);
                        vec2 rightNext = mix(mix(center + vec2(6.0, 0.0), center + vec2(uProfile9.z * 0.5, 2.0 * uProfile9.w), q),
                            mix(center + vec2(uProfile9.z * 0.5, 2.0 * uProfile9.w), center + vec2(uProfile9.z, 0.0), q), q);
                        leftDistance = min(leftDistance, segmentDistance(localCss, leftPrev, leftNext));
                        rightDistance = min(rightDistance, segmentDistance(localCss, rightPrev, rightNext));
                        leftPrev = leftNext;
                        rightPrev = rightNext;
                    }
                    float lineAA = max(0.06, centerAa);
                    float wingCoverage = max(1.0 - smoothstep(0.65, 0.65 + lineAA, leftDistance),
                        1.0 - smoothstep(0.65, 0.65 + lineAA, rightDistance));
                    screenOver(accentPremul, accentAlpha, uGoldColor,
                        wingCoverage * min(1.0, 0.65 * uProfile0.x) * flourishReveal);
                    float pearlRadius = 1.4;
                    float leftPearl = circleCoverage(length(localCss - (center + vec2(-uProfile9.z, 0.0))), pearlRadius, centerAa);
                    float rightPearl = circleCoverage(length(localCss - (center + vec2(uProfile9.z, 0.0))), pearlRadius, centerAa);
                    float pearlGlow = max(circleCoverage(length(localCss - (center + vec2(-uProfile9.z, 0.0))), 4.0, centerAa),
                        circleCoverage(length(localCss - (center + vec2(uProfile9.z, 0.0))), 4.0, centerAa));
                    screenOver(accentPremul, accentAlpha, uGoldColor, pearlGlow * 0.18 * flourishReveal);
                    screenOver(accentPremul, accentAlpha, vec3(1.0), max(leftPearl, rightPearl) * flourishReveal);
                }

                vec3 accentColor = accentPremul / max(accentAlpha, 0.0001);
                vec3 outputColor = mix(perimeterComposite, overlayBlend(perimeterComposite, accentColor), accentAlpha);
                // Preserve the APP photo texture contract and prevent CSS Overlay from darkening it.
                outputColor = max(outputColor, base);
                gl_FragColor = vec4(clamp(outputColor, 0.0, 1.0), 1.0);
            }
        """

        private fun createProgram(vertexSource: String, fragmentSource: String, label: String): Int {
            val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource, "$label-vertex")
            if (vertex == 0) return 0
            val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource, "$label-fragment")
            if (fragment == 0) {
                GLES20.glDeleteShader(vertex)
                return 0
            }
            val result = GLES20.glCreateProgram()
            if (result == 0) {
                GLES20.glDeleteShader(vertex)
                GLES20.glDeleteShader(fragment)
                Log.e(TAG, "$label program allocation failed")
                return 0
            }
            GLES20.glAttachShader(result, vertex)
            GLES20.glAttachShader(result, fragment)
            GLES20.glLinkProgram(result)
            val status = IntArray(1)
            GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, status, 0)
            val info = GLES20.glGetProgramInfoLog(result).orEmpty()
            if (info.isNotBlank()) Log.i(TAG, "$label link log: $info")
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)
            if (status[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "$label link failed: $info")
                GLES20.glDeleteProgram(result)
                return 0
            }
            return result
        }

        private fun compileShader(type: Int, source: String, label: String): Int {
            val shader = GLES20.glCreateShader(type)
            if (shader == 0) {
                Log.e(TAG, "$label allocation failed")
                return 0
            }
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            val info = GLES20.glGetShaderInfoLog(shader).orEmpty()
            if (info.isNotBlank()) Log.i(TAG, "$label compile log: $info")
            if (status[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "$label compile failed: $info")
                GLES20.glDeleteShader(shader)
                return 0
            }
            return shader
        }

    }
}
