package com.yujian.ai.ui.waterlight.renderer

import android.graphics.Bitmap
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.UnifiedWaterLightEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Background 220px Canvas2D port. Bitmap access is serialized with UI consumers. */
class WaterCausticsRenderer(private val scope: CoroutineScope, private val pixelBudget: Int = 220 * 220) : AutoCloseable {
    init { require(pixelBudget > 0) }
    private val lock = Any()
    private val gate = UnifiedWaterLightEngine()
    private var buffers: Array<Bitmap>? = null
    private var front = 0
    private var job: Job? = null
    private var lastDrawMs = Long.MIN_VALUE
    private var requestKey: CausticsRenderKey? = null
    private var renderGeneration = 0L
    private var closed = false
    private var staleTaskCount = 0L

    internal data class CausticsRenderKey(
        val sessionId: String, val attemptId: String, val imageId: String,
        val photoSha256: String?, val photoContentVersion: Long, val geometryVersion: Long,
        val width: Int, val height: Int, val scale: Double, val intensity: Double,
        val color: WaterCausticsField.Rgb, val tint: WaterCausticsField.Rgb,
        val tintStrength: Double,
    )

    fun request(
        frame: WaterLightFrame, outputWidth: Int, outputHeight: Int,
        scale: Double, intensity: Double, color: WaterCausticsField.Rgb,
        tint: WaterCausticsField.Rgb, tintStrength: Double, onReady: () -> Unit,
    ) {
        if (outputWidth <= 0 || outputHeight <= 0 || frame.sessionId.isBlank() ||
            frame.photoSha256.isNullOrBlank()) return
        val budgetedWidth = sqrt(pixelBudget.toDouble() * outputWidth / outputHeight).toInt().coerceAtLeast(1)
        val width = min(220, min(outputWidth, budgetedWidth)).coerceAtLeast(1)
        val height = max(1, (width.toDouble() * outputHeight / outputWidth).roundToInt())
        if (width.toLong() * height > pixelBudget) return
        val key = CausticsRenderKey(frame.sessionId, frame.attemptId, frame.imageId,
            frame.photoSha256, frame.photoContentVersion, frame.geometryVersion,
            width, height, scale, intensity, color, tint, tintStrength)
        synchronized(lock) {
            if (closed) return
            if (requestKey != key) {
                renderGeneration++
                job?.cancel()
                buffers?.forEach(Bitmap::recycle)
                buffers = Array(2) { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }
                requestKey = key
                gate.activate(frame)
                lastDrawMs = Long.MIN_VALUE
                front = 0
            }
            if (job?.isActive == true || (lastDrawMs != Long.MIN_VALUE && frame.elapsedMs - lastDrawMs < 33)) return
            lastDrawMs = frame.elapsedMs
            val generation = renderGeneration
            job = scope.launch(Dispatchers.Default) {
                val pixels = IntArray(width * height)
                val time = frame.opticalReferenceElapsedMs / 1000.0
                for (y in 0 until height) {
                    coroutineContext.ensureActive()
                    for (x in 0 until width) {
                        val c = WaterCausticsField.sampleCaustic(
                            x + 0.5, height - y - 0.5, width.toDouble(), height.toDouble(),
                            time, scale, intensity, color, tint, tintStrength,
                        )
                        fun channel(v: Double) = (v * 255.0).roundToInt().coerceIn(0, 255)
                        pixels[y * width + x] = (channel(c.alpha) shl 24) or
                            (channel(c.red) shl 16) or (channel(c.green) shl 8) or channel(c.blue)
                    }
                }
                val published = synchronized(lock) {
                    if (closed || generation != renderGeneration || requestKey != key ||
                        !gate.accepts(frame) || buffers?.firstOrNull()?.width != width ||
                        buffers?.firstOrNull()?.height != height) {
                        staleTaskCount++
                        false
                    } else {
                        val target = 1 - front
                        val bitmap = buffers?.get(target)
                        if (bitmap == null || bitmap.isRecycled) false
                        else {
                            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
                            front = target
                            true
                        }
                    }
                }
                if (published) withContext(Dispatchers.Main.immediate) {
                    synchronized(lock) {
                        if (!closed && generation == renderGeneration && requestKey == key &&
                            gate.accepts(frame)) onReady()
                    }
                }
            }
        }
    }

    /** Draw under the same lock so the worker never overwrites a bitmap in use. */
    fun withFrontBitmap(draw: (Bitmap) -> Unit) = synchronized(lock) {
        buffers?.get(front)?.takeUnless(Bitmap::isRecycled)?.let(draw)
    }

    internal fun staleTasks(): Long = synchronized(lock) { staleTaskCount }

    override fun close() = synchronized(lock) {
        if (closed) return@synchronized
        closed = true
        renderGeneration++
        gate.release()
        job?.cancel()
        job = null
        buffers?.forEach(Bitmap::recycle)
        buffers = null
        requestKey = null
        lastDrawMs = Long.MIN_VALUE
    }
}
