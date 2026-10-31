package com.yujian.ai.ui.waterlight.renderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Transparent lens-shaped water droplets; no raster sprite or glow-dot substitute. */
class WaterDropletRenderer {
    private data class Drop(val x: Float, val y: Float, val radius: Float, val phase: Float)
    private var currentSeed = Long.MIN_VALUE
    private var currentCount = -1
    private var drops = emptyList<Drop>()
    private val bodyPath = Path().apply {
        moveTo(.5f, 0f)
        cubicTo(.79f, .20f, .98f, .54f, .84f, .80f)
        cubicTo(.69f, 1.07f, .31f, 1.07f, .16f, .80f)
        cubicTo(.02f, .54f, .21f, .20f, .5f, 0f)
        close()
    }
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = LinearGradient(0f, 0f, 1f, 1f,
            intArrayOf(Color.argb(15, 240, 255, 252), Color.argb(76, 39, 117, 122), Color.argb(26, 232, 255, 249)),
            null, Shader.TileMode.CLAMP)
    }
    private val lens = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(.36f, .34f, .60f,
            intArrayOf(Color.argb(38, 255, 255, 255), Color.TRANSPARENT, Color.argb(38, 10, 65, 76)),
            null, Shader.TileMode.CLAMP)
    }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(126, 213, 250, 242)
    }
    private val glint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(152, 255, 255, 250) }

    fun draw(canvas: Canvas, frame: WaterLightFrame, bounds: RectF, seed: Long, count: Int, opacity: Float = 1f) {
        if (bounds.width() <= 0 || bounds.height() <= 0 || count <= 0) return
        val boundedCount = count.coerceIn(0, 24)
        if (seed != currentSeed || boundedCount != currentCount) {
            val random = Random(seed)
            drops = List(boundedCount) {
                Drop(.08f + random.nextFloat() * .84f, .08f + random.nextFloat() * .84f,
                    .012f + random.nextFloat() * .018f, random.nextFloat() * (2 * PI).toFloat())
            }
            currentSeed = seed
            currentCount = boundedCount
        }
        val t = if (frame.reducedMotion) 0f else frame.opticalReferenceElapsedMs / 1000f
        val alpha = (opacity.coerceIn(0f, 1f) * 255).toInt()
        body.alpha = alpha
        lens.alpha = alpha
        edge.alpha = (126 * opacity.coerceIn(0f, 1f)).toInt()
        glint.alpha = (152 * opacity.coerceIn(0f, 1f)).toInt()
        val unit = minOf(bounds.width(), bounds.height())
        for (drop in drops) {
            val drift = if (frame.reducedMotion) 0f else sin(t * .65f + drop.phase) * .012f
            val diameter = drop.radius * unit * 2f
            canvas.save()
            canvas.translate(bounds.left + drop.x * bounds.width(), bounds.top + (drop.y + drift) * bounds.height())
            canvas.scale(diameter, diameter * 1.22f)
            // Shaders live in unit droplet space and are reused across all frames.
            canvas.drawPath(bodyPath, body)
            canvas.drawPath(bodyPath, lens)
            edge.strokeWidth = .025f
            canvas.drawPath(bodyPath, edge)
            canvas.drawOval(.29f, .19f, .43f, .41f, glint)
            canvas.restore()
        }
    }

    fun release() {
        drops = emptyList()
        currentSeed = Long.MIN_VALUE
        currentCount = -1
    }
}
