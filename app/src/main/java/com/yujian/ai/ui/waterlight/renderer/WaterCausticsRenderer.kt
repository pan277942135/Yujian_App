package com.yujian.ai.ui.waterlight.renderer

import android.graphics.Bitmap
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.UnifiedWaterLightEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Background 220px Canvas2D port. Bitmap access is serialized with UI consumers. */
class WaterCausticsRenderer(private val scope: CoroutineScope, private val pixelBudget: Int = 220 * 220) : AutoCloseable {
    private val lock = Any()
    private val gate = UnifiedWaterLightEngine()
    private var buffers: Array<Bitmap>? = null
    private var front = 0
    private var job: Job? = null
    private var lastDrawMs = Long.MIN_VALUE
    private var activeSession: String? = null
    private var activeVersion = Long.MIN_VALUE

    fun request(
        frame: WaterLightFrame, outputWidth: Int, outputHeight: Int,
        scale: Double, intensity: Double, color: WaterCausticsField.Rgb,
        tint: WaterCausticsField.Rgb, tintStrength: Double, onReady: () -> Unit,
    ) {
        if (outputWidth <= 0 || outputHeight <= 0) return
        val width = min(220, outputWidth).coerceAtLeast(1)
        val height = max(1, (width.toDouble() * outputHeight / outputWidth).roundToInt())
        if (width.toLong() * height > pixelBudget) return
        synchronized(lock) {
            if (activeSession != frame.sessionId || activeVersion != frame.geometryVersion ||
                buffers?.firstOrNull()?.width != width || buffers?.firstOrNull()?.height != height) {
                job?.cancel()
                buffers?.forEach(Bitmap::recycle)
                buffers = Array(2) { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }
                activeSession = frame.sessionId
                activeVersion = frame.geometryVersion
                gate.activate(frame.sessionId, frame.geometryVersion)
                lastDrawMs = Long.MIN_VALUE
                front = 0
            }
            if (job?.isActive == true || (lastDrawMs != Long.MIN_VALUE && frame.elapsedMs - lastDrawMs < 33)) return
            lastDrawMs = frame.elapsedMs
        }
        job = scope.launch(Dispatchers.Default) {
            val pixels = IntArray(width * height)
            val time = frame.opticalReferenceElapsedMs / 1000.0
            for (y in 0 until height) for (x in 0 until width) {
                // Canvas2D reference samples pixel centres with Y flipped.
                val c = WaterCausticsField.sampleCaustic(
                    x + 0.5, height - y - 0.5, width.toDouble(), height.toDouble(),
                    time, scale, intensity, color, tint, tintStrength,
                )
                fun channel(v: Double) = (v * 255.0).roundToInt().coerceIn(0, 255)
                pixels[y * width + x] = (channel(c.alpha) shl 24) or
                    (channel(c.red) shl 16) or (channel(c.green) shl 8) or channel(c.blue)
            }
            synchronized(lock) {
                if (!gate.accepts(frame)) return@launch
                val target = 1 - front
                buffers?.get(target)?.setPixels(pixels, 0, width, 0, 0, width, height)
                front = target
            }
            onReady()
        }
    }

    /** Draw under the same lock so the worker never overwrites a bitmap in use. */
    fun withFrontBitmap(draw: (Bitmap) -> Unit) = synchronized(lock) {
        buffers?.get(front)?.takeUnless(Bitmap::isRecycled)?.let(draw)
    }

    override fun close() = synchronized(lock) {
        gate.release()
        job?.cancel()
        job = null
        buffers?.forEach(Bitmap::recycle)
        buffers = null
        activeSession = null
        activeVersion = Long.MIN_VALUE
        lastDrawMs = Long.MIN_VALUE
    }
}
