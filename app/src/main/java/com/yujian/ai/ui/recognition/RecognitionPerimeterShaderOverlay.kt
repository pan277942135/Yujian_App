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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

private const val SOURCE_SPEED = 1f
private const val PERIMETER_LOG_TAG = "RecognitionPerimeter"
private const val NODE_POSITION_FLOATS = RecognitionPerimeterShaderMath.SPARKLE_COUNT * 2

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
    opticalReferenceElapsedMs: Long = elapsedMs,
    processingClockStarted: Boolean,
    sessionKey: Any?,
    recognitionSessionId: String,
    recognitionAttemptId: String,
    imageId: String,
    photoSha256: String,
    phase: RecognitionPhase,
    reduceMotion: Boolean = false,
    referenceMetrics: RecognitionPerimeterReferenceMetrics? = null,
    modifier: Modifier = Modifier,
) {
    if (waterLightFrame.sessionId != recognitionSessionId || waterLightFrame.photoSha256 != photoSha256 ||
        waterLightFrame.phase != phase) return
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
        mapping.isMeasured,
    ) {
        RecognitionPerimeterShaderMath.generatePerimeterPath(sourceCssWidth, sourceCssHeight)
    }
    val shaderElapsedMs = if (waterLightFrame.reducedMotion) 0L else waterLightFrame.elapsedMs
    val timeSec = RecognitionPerimeterShaderMath.sourceTimeSec(shaderElapsedMs, speed = SOURCE_SPEED)
    val progress = if (waterLightFrame.reducedMotion) 1f else RecognitionPerimeterShaderMath.linearProgress(waterLightFrame.opticalReferenceElapsedMs)
    val perimeterOpacity = when {
        !processingClockStarted -> 0f
        opticalReferenceElapsedMs >= 2_800L -> 0f
        reduceMotion -> .10f
        else -> processingEnvironmentStrength(opticalReferenceElapsedMs) / 100f
    }
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
    val active = processingClockStarted && phase != RecognitionPhase.RESULT &&
        phase != RecognitionPhase.FAILURE && opticalReferenceElapsedMs < 2_800L
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
        opticalReferenceElapsedMs = opticalReferenceElapsedMs.coerceAtLeast(0L),
        timeSec = timeSec,
        progress = progress,
        perimeterOpacity = perimeterOpacity,
        shaderCoordinateScale = mapping.shaderPxPerSourceUnit,
        pathCoordinateScale = mapping.pathPxPerCssUnit,
        nodePositions = positions,
        coordinateMappingMeasured = mapping.isMeasured,
        glowSizeSource = 36f * context.resources.displayMetrics.density / mapping.shaderPxPerSourceUnit.coerceAtLeast(.0001f),
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
    val glowSizeSource: Float,
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
    private val releaseRequested = AtomicBoolean(false)
    private var program = 0
    private var surfaceWidth = 1
    private var surfaceHeight = 1
    private var lastMappingLogKey: String? = null
    private var firstOpticalFrameLogged = false
    private var positionBuffer = 0
    private var photoTexture = 0
    private var uploadedBitmap: Bitmap? = null
    private val vertices = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)

    fun submit(frame: PerimeterFrame) {
        frameRef.set(frame)
    }

    fun markReleased() {
        releaseRequested.set(true)
        frameRef.set(null)
    }

    fun onSurfaceCreated() {
        firstOpticalFrameLogged = false
        uploadedBitmap = null
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
        val shaderFinishedAtMs = android.os.SystemClock.uptimeMillis()
        if (program != 0) {
            Log.i(TAG, "PERIMETER_SHADER_COMPILE=PASS source=$SOURCE_SHA started_uptime_ms=$shaderStartedAtMs completed_uptime_ms=$shaderFinishedAtMs $frameIdentity")
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
        if (releaseRequested.get()) {
            deleteGlObjects()
            return false
        }
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
        GLES20.glUniform1i(GLES20.glGetUniformLocation(p, "uPhoto"), 0)
        bindQuad(p)
        setCommonUniforms(p, frame)
        GLES20.glUniform4fv(GLES20.glGetUniformLocation(p, "uNodePositions"), 8, frame.nodePositions, 0)
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
        if (frame.bitmap === uploadedBitmap) return true
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
        GLES20.glUniform2f(
            GLES20.glGetUniformLocation(programId, "uViewport"),
            surfaceWidth.toFloat(),
            surfaceHeight.toFloat(),
        )
        GLES20.glUniform4f(
            GLES20.glGetUniformLocation(programId, "uPhotoBounds"),
            frame.photoBounds.left,
            frame.photoBounds.top,
            frame.photoBounds.width,
            frame.photoBounds.height,
        )
        GLES20.glUniform4f(GLES20.glGetUniformLocation(programId, "uImageRect"),
            frame.imageBounds.left, frame.imageBounds.top,
            frame.imageBounds.width, frame.imageBounds.height)
        GLES20.glUniform1f(
            GLES20.glGetUniformLocation(programId, "uPerimeterOpacity"),
            frame.perimeterOpacity.coerceIn(0f, 1f),
        )
        GLES20.glUniform4f(
            GLES20.glGetUniformLocation(programId, "uMotion"),
            frame.timeSec,
            frame.progress,
            frame.shaderCoordinateScale,
            frame.pathCoordinateScale,
        )
        GLES20.glUniform1f(
            GLES20.glGetUniformLocation(programId, "uGlowSizeSource"),
            frame.glowSizeSource,
        )
    }

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
    }

    private fun checkGl(stage: String, identity: String = "") {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) Log.e(TAG, "GLES_ERROR stage=$stage code=0x${error.toString(16)} $identity")
    }

    companion object {
        private const val FLOAT_SIZE_BYTES = 4
        private const val TAG = "RecognitionPerimeter"
        private const val SOURCE_SHA = "794e7f2698525761bbb2f3e5d775b5dae8d7a25a"

        private const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        /** Source FISH_CATCH_FRAG_SRC perimeter branch, emitted as a transparent optical overlay. */
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
            // x=timeSec, y=linear spread progress, z=source shader-unit scale, w=source CSS-unit scale.
            uniform vec4 uMotion;
            // SWOS timeline envelope is applied after the source perimeter math; P7 is exact zero.
            uniform float uPerimeterOpacity;
            uniform float uGlowSizeSource;
            // Two node positions per vector; constant-index branches keep GLES2 fragment indexing portable.
            uniform vec4 uNodePositions[8];

            const float PI = 3.14159265358979323846;
            const float INTENSITY = 1.50;
            const float MARGIN_RATIO = 0.060;
            const float BLEND_RATIO = 0.75;
            const vec3 DEEP_PEACOCK = vec3(11.0, 110.0, 105.0) / 255.0;
            const vec3 WATER_GREEN = vec3(77.0, 208.0, 199.0) / 255.0;
            const vec3 ICE_WHITE = vec3(221.0, 247.0, 245.0) / 255.0;

            float sdRoundedBox(vec2 p, vec2 b, vec4 r) {
                r.xy = (p.x > 0.0) ? r.xy : r.zw;
                r.x  = (p.y > 0.0) ? r.x  : r.y;
                vec2 q = abs(p) - b + r.x;
                return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
            }

            vec3 overlayBlend(vec3 backdrop, vec3 source) {
                vec3 low = 2.0 * backdrop * source;
                vec3 high = 1.0 - 2.0 * (1.0 - backdrop) * (1.0 - source);
                return mix(low, high, step(vec3(0.5), backdrop));
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

            void screenOver(inout vec3 dstPremul, inout float dstAlpha, vec3 sourceColor, float sourceAlpha) {
                float a = clamp(sourceAlpha, 0.0, 1.0);
                vec3 sourcePremul = sourceColor * a;
                dstPremul = sourcePremul + dstPremul * (1.0 - a);
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

                // The source shader uses gl_FragCoord with a bottom-left origin and its
                // canvas backing-store resolution. Convert the Android photo surface to
                // those source units through the measured uniform scale.
                float sourceScale = max(uMotion.z, 0.0001);
                vec2 frag = vec2(pTop.x - uPhotoBounds.x, uPhotoBounds.y + uPhotoBounds.w - pTop.y) / sourceScale;
                vec2 res = uPhotoBounds.zw / sourceScale;
                vec2 uv = (frag * 2.0 - res) / max(res.y, 1.0);
                float normY = frag.y / max(res.y, 1.0);
                float t = uMotion.x * 0.42;

                float revealedMask = (uMotion.y >= 1.0)
                    ? 1.0
                    : smoothstep(normY - 0.14, normY + 0.02, uMotion.y * 1.08 - 0.04);
                revealedMask = clamp(revealedMask, 0.0, 1.0);

                vec2 halfRes = res * 0.5;
                vec2 centeredPos = frag - halfRes;
                float margin = min(res.x, res.y) * MARGIN_RATIO;
                vec2 boxHalf = halfRes - vec2(margin);
                float cornerR = min(res.x, res.y) * 0.12;
                float perimAngle = atan(centeredPos.y, centeredPos.x);
                float perimWave = sin(perimAngle * 5.0 + t * 2.2) * 4.5 +
                    cos(perimAngle * 9.0 - t * 1.5) * 2.6;
                float dBox = abs(sdRoundedBox(centeredPos, boxHalf, vec4(cornerR)) + perimWave);

                float effectiveGlow = max(8.0, uGlowSizeSource);
                float wideHalo = exp(-dBox * (0.07 / (effectiveGlow * 0.12)));
                float mediumBloom = exp(-dBox * (0.16 / (effectiveGlow * 0.10)));
                // Source uses reversed smoothstep edges. This descending form is defined by GLSL.
                float sharpCore = 1.0 - smoothstep(0.3, 2.8, dBox);
                float perimeterAura = (wideHalo * 0.35 + mediumBloom * 0.65 + sharpCore * 0.95) * revealedMask;

                float filamentAngleT = (perimAngle + 3.14159) / 6.28318;
                float filamentGoldBias = smoothstep(0.18, 0.82, sin(filamentAngleT * 3.14159 + 0.7));
                vec3 perimeterColor = mix(WATER_GREEN, DEEP_PEACOCK, filamentGoldBias);
                perimeterColor = mix(perimeterColor, WATER_GREEN, sin(filamentAngleT * 6.28 + t * 0.5) * 0.2 + 0.2);

                float perimContrib = perimeterAura * BLEND_RATIO;
                float totalLight = perimContrib * INTENSITY;
                float finalAlpha = clamp(totalLight, 0.0, 0.96);
                float totalWeight = max(0.001, perimContrib);
                vec3 finalRgb = (perimeterColor * perimContrib) / totalWeight;
                float coreSpark = smoothstep(0.85, 1.75, totalLight);
                finalRgb = mix(finalRgb, ICE_WHITE, coreSpark * 0.48);
                finalRgb *= (1.0 + coreSpark * 0.3);

                // First source stage: WebGL SRC_ALPHA / ONE_MINUS_SRC_ALPHA over transparent.
                // The same factors apply to alpha in the source, producing alpha^2.
                vec3 shaderLayerPremul = clamp(finalRgb * finalAlpha, 0.0, 1.0);
                float shaderLayerAlpha = clamp(finalAlpha * finalAlpha, 0.0, 1.0);
                vec3 shaderLayerColor = clamp(shaderLayerPremul / max(shaderLayerAlpha, 0.0001), 0.0, 1.0);
                // Second source stage: the 2D accent canvas screens its 16 halo/core disks
                // into transparency, then CSS Overlay blends that completed canvas over the
                // already perimeter-composited photo. No bottom flourish is ported.
                vec3 accentPremul = vec3(0.0);
                float accentAlpha = 0.0;
                float canvasGlobalAlpha = 1.0;
                // Canvas antialiasing is defined in source backing pixels; map that
                // half-pixel footprint through the same backing-unit transform.
                float edgeAa = max(0.0001, uMotion.z * 0.5);
                if (uMotion.y > 0.05) {
                    for (int i = 0; i < 16; i++) {
                        vec2 node = nodePosition(i);
                        if (node.x > -1000.0 && node.y > -1000.0) {
                            float fi = float(i);
                            float nodeSpeed = 0.012 + mod(fi, 3.0) * 0.007;
                            float pPos = mod(fi / 16.0 + uMotion.x * nodeSpeed, 1.0);
                            float pulse = 0.65 + 0.35 * sin(uMotion.x * 3.4 + seededPhase(fi));
                            bool isBright = pPos > 0.45;
                            vec3 glowColor = isBright ? WATER_GREEN : DEEP_PEACOCK;
                            vec3 coreColor = isBright ? ICE_WHITE : WATER_GREEN;
                            float nodeSize = (2.2 + mod(fi, 4.0) * 0.7) * uMotion.w;

                            float haloRadius = nodeSize * 3.2 * pulse;
                            float haloCoverage = circleCoverage(length(pTop - node), haloRadius, edgeAa);
                            float haloAlpha = 0.42 * pulse * INTENSITY * canvasGlobalAlpha * haloCoverage;
                            screenOver(accentPremul, accentAlpha, glowColor, haloAlpha);

                            float coreAlphaCandidate = 0.92 * pulse * INTENSITY;
                            // CanvasRenderingContext2D ignores globalAlpha assignments outside [0, 1].
                            if (coreAlphaCandidate <= 1.0) canvasGlobalAlpha = coreAlphaCandidate;
                            float coreRadius = nodeSize * 1.15 * pulse;
                            float coreCoverage = circleCoverage(length(pTop - node), coreRadius, edgeAa);
                            screenOver(accentPremul, accentAlpha, coreColor, canvasGlobalAlpha * coreCoverage);
                        }
                    }
                }
                vec3 accentColor = accentPremul / max(accentAlpha, 0.0001);
                float effectiveAccentAlpha = accentAlpha * uPerimeterOpacity;
                float perimeterAlpha = shaderLayerAlpha * uPerimeterOpacity;
                float outputAlpha = effectiveAccentAlpha + perimeterAlpha * (1.0 - effectiveAccentAlpha);
                vec3 perimeterComposite = mix(
                    base,
                    overlayBlend(base, shaderLayerColor),
                    perimeterAlpha
                );
                vec3 outputColor = mix(
                    perimeterComposite,
                    overlayBlend(perimeterComposite, accentColor),
                    effectiveAccentAlpha
                );
                // The same opaque output owns the photo and its positive optical delta.
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
