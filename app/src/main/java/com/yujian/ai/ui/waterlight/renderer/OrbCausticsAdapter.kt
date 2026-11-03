package com.yujian.ai.ui.waterlight.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yujian.ai.R
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Orb-only bridge to the pinned field sampler; this does not reuse photo sizing or masks. */
internal class OrbCausticsAdapter(private val scope: CoroutineScope) : AutoCloseable {
    private data class CacheKey(
        val sessionId: String,
        val attemptId: String,
        val imageId: String,
        val photoSha256: String?,
        val photoContentVersion: Long,
        val geometryVersion: Long,
        val sizePx: Int,
        val profileVersion: String,
        val low: Boolean,
    )

    private val lock = Any()
    private var bitmaps: Array<Bitmap>? = null
    private var front = 0
    private var pixels: IntArray? = null
    private var key: CacheKey? = null
    private var lastRequestedSceneMs = Long.MIN_VALUE
    private var generation = 0L
    private var job: Job? = null
    private var closed = false
    private var framesRendered = 0L
    private var lastRenderNanos = 0L
    private var totalRenderNanos = 0L

    data class Metrics(val framesRendered: Long, val lastRenderMs: Double, val averageRenderMs: Double)

    fun request(frame: WaterLightFrame, coreSizeDp: Float, density: Float, low: Boolean, onReady: () -> Unit) {
        if (coreSizeDp <= 0f || density <= 0f) return
        val textureBase = if (low) OrbRuntimeProfileV1.CAUSTIC_LOW_TEXTURE_PX_80DP else OrbRuntimeProfileV1.CAUSTIC_NORMAL_TEXTURE_PX_80DP
        val size = (textureBase * coreSizeDp / 80f).roundToInt().coerceAtLeast(1)
        val minInterval = if (low) OrbRuntimeProfileV1.CAUSTIC_LOW_MIN_INTERVAL_MS else OrbRuntimeProfileV1.CAUSTIC_NORMAL_MIN_INTERVAL_MS
        val elapsedMs = if (frame.reducedMotion) 0L else frame.elapsedMs
        val cacheKey = CacheKey(
            frame.sessionId, frame.attemptId, frame.imageId, frame.photoSha256,
            frame.photoContentVersion, frame.geometryVersion, size,
            OrbRuntimeProfileV1.PROFILE_VERSION, low,
        )
        synchronized(lock) {
            if (closed) return
            if (key != cacheKey) {
                generation++
                job?.cancel()
                bitmaps?.forEach { if (!it.isRecycled) it.recycle() }
                bitmaps = Array(OrbRuntimeProfileV1.CAUSTIC_BUFFER_COUNT) {
                    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                }
                pixels = IntArray(size * size)
                front = 0
                key = cacheKey
                lastRequestedSceneMs = Long.MIN_VALUE
            }
            if (job?.isActive == true) return
            if (lastRequestedSceneMs != Long.MIN_VALUE && elapsedMs >= lastRequestedSceneMs &&
                elapsedMs - lastRequestedSceneMs < minInterval) return
            lastRequestedSceneMs = elapsedMs
            val targetGeneration = generation
            val targetPixels = pixels ?: return
            job = scope.launch(Dispatchers.Default) {
                val started = System.nanoTime()
                renderPixels(targetPixels, size, elapsedMs, coreSizeDp, low)
                val tookNanos = System.nanoTime() - started
                val publishedCount = synchronized(lock) {
                    if (closed || targetGeneration != generation || key != cacheKey ||
                        bitmaps?.getOrNull(1 - front)?.isRecycled != false) {
                        -1L
                    } else {
                        val back = 1 - front
                        bitmaps!![back].setPixels(targetPixels, 0, size, 0, 0, size, size)
                        front = back
                        framesRendered++
                        lastRenderNanos = tookNanos
                        totalRenderNanos += tookNanos
                        framesRendered
                    }
                }
                if (publishedCount > 0L && (publishedCount == 1L || publishedCount % 30L == 0L)) {
                    Log.i("OrbCausticsRuntime", "profile=${cacheKey.profileVersion} size=${cacheKey.sizePx} low=${cacheKey.low} render_ms=${"%.3f".format(tookNanos / 1_000_000.0)} frames=$publishedCount")
                }
                if (publishedCount > 0L) withContext(Dispatchers.Main.immediate) {
                    synchronized(lock) {
                        if (!closed && targetGeneration == generation && key == cacheKey) onReady()
                    }
                }
            }
        }
    }

    private suspend fun renderPixels(pixels: IntArray, size: Int, elapsedMs: Long, @Suppress("UNUSED_PARAMETER") coreSizeDp: Float, @Suppress("UNUSED_PARAMETER") low: Boolean) {
        val layers = OrbRuntimeProfileV1.causticLayers
        val normalColor = WaterCausticsField.Rgb(.78, .94, .91)
        val waterTint = WaterCausticsField.Rgb(.10, .32, .36)
        val extentD = size.toDouble() * .5
        val frameSeconds = elapsedMs / 1000.0
        for (y in 0 until size) {
            coroutineContext.ensureActive()
            for (x in 0 until size) {
                val dxD = (x + .5 - size * .5) / extentD
                val dyD = (y + .5 - size * .5) / extentD
                val distanceD = sqrt(dxD * dxD + dyD * dyD)
                if (distanceD >= OrbRuntimeProfileV1.CAUSTIC_INNER_RADIUS_D) {
                    pixels[y * size + x] = 0
                    continue
                }
                val edge = if (distanceD <= OrbRuntimeProfileV1.CAUSTIC_INNER_RADIUS_D - OrbRuntimeProfileV1.CAUSTIC_EDGE_FADE_D) 1.0 else {
                    ((OrbRuntimeProfileV1.CAUSTIC_INNER_RADIUS_D - distanceD) / OrbRuntimeProfileV1.CAUSTIC_EDGE_FADE_D).coerceIn(0.0, 1.0)
                }
                val edgeAlpha = edge * edge * (3.0 - 2.0 * edge)
                var alpha = 0.0
                var red = 0.0
                var green = 0.0
                var blue = 0.0
                for (layer in layers) {
                    val layerSeconds = if (frameSeconds == 0.0) 0.0 else elapsedMs % layer.periodMs / 1000.0
                    val advectX = layer.advectionXDps * frameSeconds * size
                    val advectY = layer.advectionYDps * frameSeconds * size
                    val sample = WaterCausticsField.sampleCaustic(
                        x + .5 + advectX,
                        y + .5 + advectY,
                        size.toDouble(),
                        size.toDouble(),
                        layerSeconds,
                        layer.frequency.toDouble(),
                        1.0,
                        normalColor,
                        waterTint,
                        .06,
                    )
                    val layerAlpha = (sample.alpha.toDouble() * layer.alpha * edgeAlpha).coerceIn(0.0, layer.alpha.toDouble())
                    val remaining = 1.0 - layerAlpha
                    red = sample.red * layerAlpha + red * remaining
                    green = sample.green * layerAlpha + green * remaining
                    blue = sample.blue * layerAlpha + blue * remaining
                    alpha = layerAlpha + alpha * remaining
                }
                val aux = WaterCausticsField.sampleSoftDomainWarpedCaustic(
                    fragX = x + .5,
                    fragY = y + .5,
                    resX = size.toDouble(),
                    resY = size.toDouble(),
                    time = if (frameSeconds == 0.0) 0.0 else frameSeconds * OrbFluidOpticalProfileV1.CAUSTIC_TIME_RATE,
                    scale = OrbFluidOpticalProfileV1.CAUSTIC_FLOW_SCALE,
                    intensity = OrbFluidOpticalProfileV1.CAUSTIC_AUX_INTENSITY,
                    color = normalColor,
                    tint = waterTint,
                    tintStrength = 0.0,
                    domainWarp = OrbFluidOpticalProfileV1.CAUSTIC_DOMAIN_WARP,
                    softEdgeLow = OrbFluidOpticalProfileV1.CAUSTIC_SOFT_EDGE_LOW,
                    softEdgeHigh = OrbFluidOpticalProfileV1.CAUSTIC_SOFT_EDGE_HIGH,
                )
                val auxAlpha = (aux.alpha * OrbFluidOpticalProfileV1.CAUSTIC_AUX_ALPHA_MAX * edgeAlpha)
                    .coerceIn(0.0, OrbFluidOpticalProfileV1.CAUSTIC_AUX_ALPHA_MAX)
                val auxRemaining = 1.0 - auxAlpha
                red = aux.red * auxAlpha + red * auxRemaining
                green = aux.green * auxAlpha + green * auxRemaining
                blue = aux.blue * auxAlpha + blue * auxRemaining
                alpha = auxAlpha + alpha * auxRemaining
                val boundedAlpha = min(alpha, OrbRuntimeProfileV1.R03_CAUSTIC_RESPONSE_ALPHA_MAX.toDouble())
                val scaleToBound = if (alpha <= 0.0) 0.0 else boundedAlpha / alpha
                val unpremultiply = if (boundedAlpha <= 0.0) 0.0 else scaleToBound / boundedAlpha
                val redByte = (red * unpremultiply * 255.0).roundToInt().coerceIn(0, 255)
                val greenByte = (green * unpremultiply * 255.0).roundToInt().coerceIn(0, 255)
                val blueByte = (blue * unpremultiply * 255.0).roundToInt().coerceIn(0, 255)
                pixels[y * size + x] = ((boundedAlpha * 255.0).roundToInt().coerceIn(0, 255) shl 24) or
                    (redByte shl 16) or (greenByte shl 8) or blueByte
            }
        }
    }

    fun withFrontBitmap(block: (Bitmap) -> Unit) = synchronized(lock) {
        bitmaps?.getOrNull(front)?.takeUnless(Bitmap::isRecycled)?.let(block)
    }

    fun metrics(): Metrics = synchronized(lock) {
        Metrics(framesRendered, lastRenderNanos / 1_000_000.0,
            if (framesRendered == 0L) 0.0 else totalRenderNanos / framesRendered / 1_000_000.0)
    }

    override fun close() = synchronized(lock) {
        if (closed) return@synchronized
        closed = true
        generation++
        job?.cancel()
        job = null
        bitmaps?.forEach { if (!it.isRecycled) it.recycle() }
        bitmaps = null
        pixels = null
        key = null
        lastRequestedSceneMs = Long.MIN_VALUE
    }
}

/** Internal sunlight, droplets and caustics are all intersected with the real WebP alpha. */
@Composable
internal fun OrbCoreOpticalEffects(
    coreSize: Dp,
    frame: WaterLightFrame,
    coreAlpha: Float,
    coreScale: Float,
    low: Boolean,
    lifeState: OrbRuntimeProfileV1.LifeState,
    candidate: Boolean,
    wake: OrbWakeBreathReleaseV11.Wake,
    showDroplets: Boolean = true,
    showSunlight: Boolean = true,
    showCaustics: Boolean = true,
) {
    if (!candidate) return
    val scope = rememberCoroutineScope()
    val caustics = remember { OrbCausticsAdapter(scope) }
    var revision by remember { mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val densityValue = density.density
    val sizeDp = coreSize.value
    val causticPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true } }
    val additiveBudgetPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (255f * OrbRuntimeProfileV1.R03_MAX_EXTRA_ADDITIVE_ALPHA).toInt().coerceIn(0, 255)
        }
    }
    val maskPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
    } }
    val causticRect = remember { RectF() }
    val effectBounds = remember { RectF() }
    val droplets = remember { OrbDropletRenderer() }
    val sunlight = remember { OrbSunlightRenderer() }
    val mask = ImageBitmap.imageResource(R.drawable.processing_water_core_v1)
    LaunchedEffect(caustics, frame, sizeDp, densityValue, low, showCaustics) {
        if (showCaustics) caustics.request(frame, sizeDp, densityValue, low) { revision++ }
    }
    DisposableEffect(caustics) { onDispose { caustics.close() } }
    @Suppress("UNUSED_VARIABLE") val renderRevision = revision
    ComposeCanvas(
        Modifier.fillMaxSize().graphicsLayer {
            alpha = coreAlpha
            scaleX = coreScale
            scaleY = coreScale
            compositingStrategy = CompositingStrategy.Offscreen
        },
    ) {
        drawIntoCanvas { drawCanvas ->
            val native = drawCanvas.nativeCanvas
            val boxSize = coreSize.value * density.density / .84f
            effectBounds.set(0f, 0f, native.width.toFloat(), native.height.toFloat())
            causticRect.set((native.width - coreSize.value * density.density * .77f) / 2f,
                (native.height - coreSize.value * density.density * .77f) / 2f,
                (native.width + coreSize.value * density.density * .77f) / 2f,
                (native.height + coreSize.value * density.density * .77f) / 2f)
            val effectSave = native.saveLayer(effectBounds, additiveBudgetPaint)
            try {
                if (showCaustics && wake.waterCore > 0f) {
                    causticPaint.alpha = (255f * wake.waterCore).toInt().coerceIn(0, 255)
                    caustics.withFrontBitmap { bitmap -> native.drawBitmap(bitmap, null, causticRect, causticPaint) }
                }
                if (showSunlight) sunlight.draw(native, coreSize.value * density.density, frame, wake.optics)
                if (showDroplets) droplets.draw(native, frame, coreSize.value, density.density, low,
                    if (lifeState == OrbRuntimeProfileV1.LifeState.S1_IDLE) .35f else 1f, wake.waterCore)
                // Alpha from the very same transparent core resource clips every internal layer.
                val maskRect = Rect((native.width / 2f - boxSize / 2f).toInt(),
                    (native.height / 2f - boxSize / 2f).toInt(),
                    (native.width / 2f + boxSize / 2f).toInt(),
                    (native.height / 2f + boxSize / 2f).toInt())
                native.drawBitmap(mask.asAndroidBitmap(), null, maskRect, maskPaint)
            } finally {
                native.restoreToCount(effectSave)
            }
        }
    }
}

/** Fixed upper-left source and azimuth; no global brightness pulse or rotating light. */
internal class OrbSunlightRenderer {
    private val sunRgb = OrbRuntimeProfileV1.sunColor.toInt()
    private val sunRed = Color.red(sunRgb)
    private val sunGreen = Color.green(sunRgb)
    private val sunBlue = Color.blue(sunRgb)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = android.graphics.RadialGradient(
            0f, 0f, 1f,
            intArrayOf(Color.argb(40, min(255, sunRed + 39), min(255, sunGreen + 57), min(255, sunBlue + 88)),
                Color.argb(12, sunRed, sunGreen, sunBlue), Color.TRANSPARENT),
            floatArrayOf(0f, .42f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
    }
    private val refraction = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.rgb(sunRed, sunGreen, sunBlue)
    }
    private val sunlightRayA = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.rgb(sunRed, sunGreen, sunBlue)
    }
    private val sunlightRayB = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.rgb(sunRed, sunGreen, sunBlue)
    }
    private val path = android.graphics.Path()
    private val rayPathA = android.graphics.Path()
    private val rayPathB = android.graphics.Path()
    private val rayShaderKey = FloatArray(8) { Float.NaN }

    fun draw(canvas: Canvas, coreSizePx: Float, frame: WaterLightFrame, wakeAlpha: Float) {
        val sceneMs = if (frame.reducedMotion) 0L else frame.elapsedMs
        val radius = coreSizePx * .5f
        val sourceX = canvas.width * .5f + (OrbRuntimeProfileV1.R03_SUN_SOURCE_U - .5f) * coreSizePx
        val sourceY = canvas.height * .5f + (OrbRuntimeProfileV1.R03_SUN_SOURCE_V - .5f) * coreSizePx
        val glintPhase = (2f * Math.PI.toFloat() * sceneMs / OrbRuntimeProfileV1.R03_GLASS_SPECULAR_PERIOD_MS)
        val translation = 0f // Keep the R03 source fixed in the upper-left; only local brightness breathes.
        val glintAlpha = (.5f + .5f * sin(glintPhase)) * OrbRuntimeProfileV1.R03_GLASS_SPECULAR_ALPHA_MAX * wakeAlpha
        val refractionPhase = (2f * Math.PI.toFloat() * sceneMs / OrbRuntimeProfileV1.R03_CORE_REFRACTION_PERIOD_MS)
        val refractionAlpha = (.45f + .55f * (.5f + .5f * sin(refractionPhase))) * OrbRuntimeProfileV1.R03_CORE_REFRACTION_ALPHA_MAX * wakeAlpha
        val save = canvas.save()
        try {
            canvas.translate(sourceX + translation * .707f, sourceY + translation * .707f)
            canvas.scale(radius * .24f, radius * .24f)
            glow.alpha = (255f * glintAlpha).toInt().coerceIn(0, 255)
            canvas.drawCircle(0f, 0f, 1f, glow)
        } finally {
            canvas.restoreToCount(save)
        }
        // A narrow, fixed diagonal refraction stroke carries light from source toward lower-right.
        path.rewind()
        path.moveTo(sourceX, sourceY)
        path.cubicTo(sourceX + radius * .14f, sourceY + radius * .10f,
            canvas.width * .5f + radius * .52f, canvas.height * .5f + radius * .19f,
            canvas.width * .5f + radius * .66f, canvas.height * .5f + radius * .31f)
        refraction.strokeWidth = maxOf(.3f, coreSizePx * .004f)
        val rayShare = OrbFluidOpticalProfileV1.SUNLIGHT_RAY_ALPHA_SHARE
        val rayAlphaNorm = minOf(
            refractionAlpha * rayShare,
            OrbFluidOpticalProfileV1.SUNLIGHT_MIX_MAX * wakeAlpha,
        )
        refraction.alpha = (255f * (refractionAlpha - rayAlphaNorm)).toInt().coerceIn(0, 255)
        canvas.drawPath(path, refraction)

        // Two local, fixed-azimuth rays share the existing refraction opacity budget.
        val rayAlpha = (255f * rayAlphaNorm * .5f).toInt().coerceIn(0, 255)
        sunlightRayA.alpha = rayAlpha
        sunlightRayB.alpha = rayAlpha
        sunlightRayA.strokeWidth = maxOf(.25f, coreSizePx * OrbFluidOpticalProfileV1.SUNLIGHT_RAY_WIDTH_D)
        sunlightRayB.strokeWidth = sunlightRayA.strokeWidth * .72f
        rayPathA.rewind()
        rayPathA.moveTo(sourceX + radius * .045f, sourceY + radius * .04f)
        rayPathA.cubicTo(sourceX + radius * .20f, sourceY + radius * .16f,
            canvas.width * .5f + radius * .44f, canvas.height * .5f + radius * .06f,
            canvas.width * .5f + radius * .58f, canvas.height * .5f + radius * .18f)
        rayPathB.rewind()
        rayPathB.moveTo(sourceX + radius * .02f, sourceY + radius * .075f)
        rayPathB.cubicTo(sourceX + radius * .16f, sourceY + radius * .22f,
            canvas.width * .5f + radius * .32f, canvas.height * .5f + radius * .12f,
            canvas.width * .5f + radius * .48f, canvas.height * .5f + radius * .27f)
        updateRayShaders(
            sourceX + radius * .045f, sourceY + radius * .04f,
            canvas.width * .5f + radius * .58f, canvas.height * .5f + radius * .18f,
            sourceX + radius * .02f, sourceY + radius * .075f,
            canvas.width * .5f + radius * .48f, canvas.height * .5f + radius * .27f,
        )
        canvas.drawPath(rayPathA, sunlightRayA)
        canvas.drawPath(rayPathB, sunlightRayB)
        // R03 leaves per-leaf water-grass driving disabled; only mask-projected warping is enabled.
    }

    private fun updateRayShaders(
        startAX: Float, startAY: Float, endAX: Float, endAY: Float,
        startBX: Float, startBY: Float, endBX: Float, endBY: Float,
    ) {
        val changed = kotlin.math.abs(startAX - rayShaderKey[0]) >= 0.01f ||
            kotlin.math.abs(startAY - rayShaderKey[1]) >= 0.01f ||
            kotlin.math.abs(endAX - rayShaderKey[2]) >= 0.01f ||
            kotlin.math.abs(endAY - rayShaderKey[3]) >= 0.01f ||
            kotlin.math.abs(startBX - rayShaderKey[4]) >= 0.01f ||
            kotlin.math.abs(startBY - rayShaderKey[5]) >= 0.01f ||
            kotlin.math.abs(endBX - rayShaderKey[6]) >= 0.01f ||
            kotlin.math.abs(endBY - rayShaderKey[7]) >= 0.01f
        if (!changed) return
        rayShaderKey[0] = startAX
        rayShaderKey[1] = startAY
        rayShaderKey[2] = endAX
        rayShaderKey[3] = endAY
        rayShaderKey[4] = startBX
        rayShaderKey[5] = startBY
        rayShaderKey[6] = endBX
        rayShaderKey[7] = endBY
        val colors = intArrayOf(
            Color.rgb(sunRed, sunGreen, sunBlue),
            Color.argb((255f * OrbFluidOpticalProfileV1.SUNLIGHT_RAY_FADE_MID_ALPHA).toInt(), sunRed, sunGreen, sunBlue),
            Color.argb((255f * OrbFluidOpticalProfileV1.SUNLIGHT_RAY_FADE_END_ALPHA).toInt(), sunRed, sunGreen, sunBlue),
        )
        val positions = floatArrayOf(0f, OrbFluidOpticalProfileV1.SUNLIGHT_RAY_FADE_MIDPOINT, 1f)
        sunlightRayA.shader = LinearGradient(startAX, startAY, endAX, endAY, colors, positions, android.graphics.Shader.TileMode.CLAMP)
        sunlightRayB.shader = LinearGradient(startBX, startBY, endBX, endBY, colors, positions, android.graphics.Shader.TileMode.CLAMP)
    }
}
