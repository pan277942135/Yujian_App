package com.yujian.ai.ui.waterlight.renderer

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal const val GL_FLOAT_BYTES = 4

internal interface GlSceneRenderer<FrameT> {
    fun onSurfaceCreated()
    fun onSurfaceChanged(width: Int, height: Int)
    fun onDrawFrame(frame: FrameT): Boolean
    fun releaseGl(contextCurrent: Boolean)
}

/**
 * Transparent TextureView + EGL14 + GLES20 host modeled after RecognitionPerimeterTextureView,
 * adding coalesced latest-frame scheduling so Compose animation ticks never pile up on the GL thread.
 */
internal class TransparentGlTextureView<FrameT : Any>(
    context: Context,
    threadName: String,
    private val tag: String,
    private val renderer: GlSceneRenderer<FrameT>,
    private val onFirstFramePresented: () -> Unit = {},
) : TextureView(context), TextureView.SurfaceTextureListener {

    private val worker = HandlerThread(threadName).apply { start() }
    private val handler = Handler(worker.looper)
    private val latestFrame = AtomicReference<FrameT?>(null)
    private val renderScheduled = AtomicBoolean(false)
    private val released = AtomicBoolean(false)

    private var surfaceTexture: SurfaceTexture? = null
    private var surfaceEpoch = 0L
    private var hostResumed = true
    private var initialized = false
    private var firstFrameNotified = false

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var contextGl: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surfaceGl: EGLSurface = EGL14.EGL_NO_SURFACE
    private var windowSurface: Surface? = null

    init {
        isOpaque = false
        surfaceTextureListener = this
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isFocusable = false
        isClickable = false
    }

    fun submit(frame: FrameT) {
        if (released.get()) return
        latestFrame.set(frame)
        scheduleRender()
    }

    fun setHostResumed(resumed: Boolean) {
        hostResumed = resumed
        if (resumed && latestFrame.get() != null) {
            scheduleRender()
        }
    }

    private fun scheduleRender() {
        if (!hostResumed || released.get() || !isAvailable) return
        val currentTexture = surfaceTexture ?: return
        if (!renderScheduled.compareAndSet(false, true)) return
        val epoch = surfaceEpoch
        handler.post {
            renderScheduled.set(false)
            if (released.get() || !hostResumed || currentTexture !== surfaceTexture || epoch != surfaceEpoch) {
                return@post
            }
            val frame = latestFrame.get() ?: return@post
            if (!initialized && !initGl(currentTexture)) return@post
            val w = width.coerceAtLeast(1)
            val h = height.coerceAtLeast(1)
            renderer.onSurfaceChanged(w, h)
            val drawn = renderer.onDrawFrame(frame)
            val swapped = drawn && EGL14.eglSwapBuffers(display, surfaceGl)
            if (swapped && !firstFrameNotified) {
                firstFrameNotified = true
                post {
                    if (!released.get()) {
                        onFirstFramePresented()
                    }
                }
            }
        }
    }

    private fun initGl(st: SurfaceTexture): Boolean {
        return try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY || !EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0)) {
                return failInit("DISPLAY")
            }
            val configAttrs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            if (!EGL14.eglChooseConfig(display, configAttrs, 0, configs, 0, 1, IntArray(1), 0)) {
                return failInit("CONFIG")
            }
            val config = configs[0] ?: return failInit("CONFIG_EMPTY")
            contextGl = EGL14.eglCreateContext(
                display,
                config,
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                0,
            )
            if (contextGl == EGL14.EGL_NO_CONTEXT) return failInit("CONTEXT")
            windowSurface = Surface(st)
            surfaceGl = EGL14.eglCreateWindowSurface(display, config, windowSurface, intArrayOf(EGL14.EGL_NONE), 0)
            if (surfaceGl == EGL14.EGL_NO_SURFACE || !EGL14.eglMakeCurrent(display, surfaceGl, surfaceGl, contextGl)) {
                return failInit("SURFACE")
            }
            renderer.onSurfaceCreated()
            initialized = true
            true
        } catch (t: Throwable) {
            Log.e(tag, "GL init failed", t)
            failInit(t.javaClass.simpleName)
        }
    }

    private fun failInit(reason: String): Boolean {
        Log.e(tag, "GL_INIT_FAIL reason=$reason eglError=0x${EGL14.eglGetError().toString(16)}")
        releaseGl()
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
        windowSurface?.release()
        windowSurface = null
        display = EGL14.EGL_NO_DISPLAY
        surfaceGl = EGL14.EGL_NO_SURFACE
        contextGl = EGL14.EGL_NO_CONTEXT
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        surfaceEpoch++
        surfaceTexture = surface
        scheduleRender()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        scheduleRender()
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        surfaceEpoch++
        surfaceTexture = null
        handler.post { releaseGl() }
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    fun release() {
        if (!released.compareAndSet(false, true)) return
        surfaceEpoch++
        latestFrame.set(null)
        surfaceTexture = null
        handler.post {
            releaseGl()
            worker.quitSafely()
        }
    }
}

internal data class GlFramebuffer(
    val fboId: Int,
    val textureId: Int,
    val width: Int,
    val height: Int,
) {
    fun destroy() {
        if (fboId != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
        if (textureId != 0) GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
    }
}

internal object GlShaderUtil {
    fun createProgram(vertexSource: String, fragmentSource: String, label: String): Int {
        val v = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource, "$label-v")
        if (v == 0) return 0
        val f = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource, "$label-f")
        if (f == 0) {
            GLES20.glDeleteShader(v)
            return 0
        }
        val prog = GLES20.glCreateProgram()
        if (prog == 0) {
            GLES20.glDeleteShader(v)
            GLES20.glDeleteShader(f)
            return 0
        }
        GLES20.glAttachShader(prog, v)
        GLES20.glAttachShader(prog, f)
        GLES20.glLinkProgram(prog)
        val status = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, status, 0)
        val info = GLES20.glGetProgramInfoLog(prog).orEmpty()
        GLES20.glDeleteShader(v)
        GLES20.glDeleteShader(f)
        if (status[0] != GLES20.GL_TRUE) {
            Log.e("WaterLightGl", "$label link failed: $info")
            GLES20.glDeleteProgram(prog)
            return 0
        }
        return prog
    }

    private fun compileShader(type: Int, source: String, label: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) return 0
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            Log.e("WaterLightGl", "$label compile failed: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    fun createQuadVbo(): Int {
        val vertices = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        val buf = ByteBuffer.allocateDirect(vertices.size * GL_FLOAT_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        buf.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.size * GL_FLOAT_BYTES, buf, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        return ids[0]
    }

    fun createFramebuffer(width: Int, height: Int): GlFramebuffer? {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        val tex = texIds[0]
        if (tex == 0) return null
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        val fboIds = IntArray(1)
        GLES20.glGenFramebuffers(1, fboIds, 0)
        val fbo = fboIds[0]
        if (fbo == 0) {
            GLES20.glDeleteTextures(1, intArrayOf(tex), 0)
            return null
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, tex, 0)
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
            GLES20.glDeleteTextures(1, intArrayOf(tex), 0)
            return null
        }
        return GlFramebuffer(fbo, tex, w, h)
    }

    fun ensureFloatBuffer(existing: FloatBuffer?, requiredFloats: Int): FloatBuffer {
        if (existing != null && existing.capacity() >= requiredFloats) {
            existing.clear()
            return existing
        }
        return ByteBuffer.allocateDirect(requiredFloats * GL_FLOAT_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
    }
}

/**
 * Reusable 1/4-res HDR-style Bloom post-processor:
 * Brightness extract -> Separable 9-tap H/V Gaussian blur -> Premultiplied Alpha composite.
 */
internal class GlBloomPipeline {
    private var extractProg = 0
    private var blurProg = 0
    private var compositeProg = 0

    private var extractTexLoc = -1
    private var extractThreshLoc = -1
    private var blurTexLoc = -1
    private var blurDirLoc = -1
    private var compSceneLoc = -1
    private var compBloomLoc = -1
    private var compIntensityLoc = -1
    private var compOpacityLoc = -1

    private var sceneFbo: GlFramebuffer? = null
    private var bloomFboA: GlFramebuffer? = null
    private var bloomFboB: GlFramebuffer? = null
    private var width = 0
    private var height = 0

    val sceneFboId: Int get() = sceneFbo?.fboId ?: 0
    val sceneWidth: Int get() = width
    val sceneHeight: Int get() = height

    fun init() {
        extractProg = GlShaderUtil.createProgram(QUAD_VS, EXTRACT_FS, "bloom-extract")
        blurProg = GlShaderUtil.createProgram(QUAD_VS, BLUR_FS, "bloom-blur")
        compositeProg = GlShaderUtil.createProgram(QUAD_VS, COMPOSITE_FS, "bloom-composite")
        if (extractProg != 0) {
            extractTexLoc = GLES20.glGetUniformLocation(extractProg, "uTex")
            extractThreshLoc = GLES20.glGetUniformLocation(extractProg, "uThreshold")
        }
        if (blurProg != 0) {
            blurTexLoc = GLES20.glGetUniformLocation(blurProg, "uTex")
            blurDirLoc = GLES20.glGetUniformLocation(blurProg, "uTexelDir")
        }
        if (compositeProg != 0) {
            compSceneLoc = GLES20.glGetUniformLocation(compositeProg, "uSceneTex")
            compBloomLoc = GLES20.glGetUniformLocation(compositeProg, "uBloomTex")
            compIntensityLoc = GLES20.glGetUniformLocation(compositeProg, "uBloomIntensity")
            compOpacityLoc = GLES20.glGetUniformLocation(compositeProg, "uGlobalOpacity")
        }
    }

    fun ensureSize(w: Int, h: Int): Boolean {
        val targetW = w.coerceAtLeast(1)
        val targetH = h.coerceAtLeast(1)
        if (targetW == width && targetH == height && sceneFbo != null && bloomFboA != null && bloomFboB != null) {
            return true
        }
        destroyBuffers()
        val bw = (targetW / 4).coerceAtLeast(16)
        val bh = (targetH / 4).coerceAtLeast(16)
        val s = GlShaderUtil.createFramebuffer(targetW, targetH)
        val a = GlShaderUtil.createFramebuffer(bw, bh)
        val b = GlShaderUtil.createFramebuffer(bw, bh)
        if (s == null || a == null || b == null) {
            s?.destroy()
            a?.destroy()
            b?.destroy()
            return false
        }
        sceneFbo = s
        bloomFboA = a
        bloomFboB = b
        width = targetW
        height = targetH
        return true
    }

    fun renderBloomAndComposite(
        quadVbo: Int,
        threshold: Float,
        bloomIntensity: Float,
        globalOpacity: Float,
        skipBlur: Boolean,
    ) {
        val s = sceneFbo ?: return
        val a = bloomFboA ?: return
        val b = bloomFboB ?: return
        if (extractProg == 0 || blurProg == 0 || compositeProg == 0) return

        GLES20.glDisable(GLES20.GL_BLEND)

        if (!skipBlur && bloomIntensity > 0.001f) {
            // 1. Bright-pass extract into bloomFboA
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, a.fboId)
            GLES20.glViewport(0, 0, a.width, a.height)
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(extractProg)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, s.textureId)
            GLES20.glUniform1i(extractTexLoc, 0)
            GLES20.glUniform1f(extractThreshLoc, threshold)
            drawQuad(extractProg, quadVbo)

            // 2. Separable Gaussian blur passes (2 iterations for rich soft halo)
            val texelX = 1.35f / a.width.toFloat()
            val texelY = 1.35f / a.height.toFloat()
            repeat(2) { pass ->
                val spread = if (pass == 0) 1.0f else 1.85f
                // Horizontal: A -> B
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, b.fboId)
                GLES20.glViewport(0, 0, b.width, b.height)
                GLES20.glUseProgram(blurProg)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, a.textureId)
                GLES20.glUniform1i(blurTexLoc, 0)
                GLES20.glUniform2f(blurDirLoc, texelX * spread, 0f)
                drawQuad(blurProg, quadVbo)

                // Vertical: B -> A
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, a.fboId)
                GLES20.glViewport(0, 0, a.width, a.height)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, b.textureId)
                GLES20.glUniform2f(blurDirLoc, 0f, texelY * spread)
                drawQuad(blurProg, quadVbo)
            }
        }

        // 3. Final composite to default framebuffer (TextureView surface)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(compositeProg)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, s.textureId)
        GLES20.glUniform1i(compSceneLoc, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, a.textureId)
        GLES20.glUniform1i(compBloomLoc, 1)
        GLES20.glUniform1f(compIntensityLoc, if (skipBlur) 0f else bloomIntensity)
        GLES20.glUniform1f(compOpacityLoc, globalOpacity)
        drawQuad(compositeProg, quadVbo)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    }

    private fun drawQuad(program: Int, quadVbo: Int) {
        val loc = GLES20.glGetAttribLocation(program, "aPosition")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glEnableVertexAttribArray(loc)
        GLES20.glVertexAttribPointer(loc, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun destroyBuffers() {
        sceneFbo?.destroy(); sceneFbo = null
        bloomFboA?.destroy(); bloomFboA = null
        bloomFboB?.destroy(); bloomFboB = null
        width = 0
        height = 0
    }

    fun release(contextCurrent: Boolean) {
        if (contextCurrent) {
            destroyBuffers()
            if (extractProg != 0) GLES20.glDeleteProgram(extractProg)
            if (blurProg != 0) GLES20.glDeleteProgram(blurProg)
            if (compositeProg != 0) GLES20.glDeleteProgram(compositeProg)
        } else {
            sceneFbo = null
            bloomFboA = null
            bloomFboB = null
        }
        extractProg = 0
        blurProg = 0
        compositeProg = 0
    }

    companion object {
        private const val QUAD_VS = """
            attribute vec2 aPosition;
            varying vec2 vUv;
            void main() {
                vUv = aPosition * 0.5 + 0.5;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        private const val EXTRACT_FS = """
            precision mediump float;
            varying vec2 vUv;
            uniform sampler2D uTex;
            uniform float uThreshold;
            void main() {
                vec4 c = texture2D(uTex, vUv);
                float peak = max(c.r, max(c.g, c.b));
                float w = smoothstep(uThreshold, uThreshold + 0.35, peak);
                gl_FragColor = vec4(c.rgb * w, c.a * w);
            }
        """

        private const val BLUR_FS = """
            precision mediump float;
            varying vec2 vUv;
            uniform sampler2D uTex;
            uniform vec2 uTexelDir;
            void main() {
                vec4 sum = texture2D(uTex, vUv) * 0.227027;
                sum += texture2D(uTex, vUv + uTexelDir * 1.384615) * 0.316216;
                sum += texture2D(uTex, vUv - uTexelDir * 1.384615) * 0.316216;
                sum += texture2D(uTex, vUv + uTexelDir * 3.230769) * 0.070270;
                sum += texture2D(uTex, vUv - uTexelDir * 3.230769) * 0.070270;
                gl_FragColor = sum;
            }
        """

        private const val COMPOSITE_FS = """
            precision mediump float;
            varying vec2 vUv;
            uniform sampler2D uSceneTex;
            uniform sampler2D uBloomTex;
            uniform float uBloomIntensity;
            uniform float uGlobalOpacity;
            void main() {
                vec4 scene = texture2D(uSceneTex, vUv);
                vec3 bloom = texture2D(uBloomTex, vUv).rgb * uBloomIntensity;
                vec3 combined = (scene.rgb + bloom) * uGlobalOpacity;
                // Soft highlight compression + valid premultiplied alpha clamp (rgb <= outA)
                vec3 mapped = combined / (1.0 + max(combined - vec3(0.72), vec3(0.0)) * 0.45);
                float maxCh = max(mapped.r, max(mapped.g, mapped.b));
                float outA = clamp(max(scene.a * uGlobalOpacity, maxCh), 0.0, 1.0);
                gl_FragColor = vec4(clamp(mapped, 0.0, outA), outA);
            }
        """
    }
}
