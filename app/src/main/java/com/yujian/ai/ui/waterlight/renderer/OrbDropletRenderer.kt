package com.yujian.ai.ui.waterlight.renderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import kotlin.math.PI
import kotlin.math.sin

/** R02's fixed three-lens Orb layer; photo droplet generation remains independent and random. */
internal class OrbDropletRenderer {
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = LinearGradient(
            0f, 0f, 1f, 1f,
            intArrayOf(Color.argb(18, 240, 255, 252), Color.argb(74, 39, 117, 122), Color.argb(24, 232, 255, 249)),
            null,
            Shader.TileMode.CLAMP,
        )
    }
    private val lens = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            .36f, .34f, .60f,
            intArrayOf(Color.argb(46, 255, 255, 255), Color.TRANSPARENT, Color.argb(42, 10, 65, 76)),
            null,
            Shader.TileMode.CLAMP,
        )
    }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val glint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val glintRect = RectF()

    fun draw(canvas: Canvas, frame: WaterLightFrame, coreSizeDp: Float, density: Float, low: Boolean, lifeAlpha: Float) {
        if (coreSizeDp <= 0f || density <= 0f || lifeAlpha <= 0f) return
        val opticalMs = if (frame.reducedMotion) 0L else frame.opticalReferenceElapsedMs
        val count = if (low) OrbRuntimeProfileV1.R02_LOW_PERFORMANCE_COUNT else OrbRuntimeProfileV1.droplets.size
        val centerX = canvas.width * .5f
        val centerY = canvas.height * .5f
        val coreSizePx = coreSizeDp * density
        val radius = coreSizePx * .5f
        val stateAlpha = .35f + .65f * lifeAlpha.coerceIn(0f, 1f)
        OrbRuntimeProfileV1.droplets.take(count).forEach { drop ->
            val phase = drop.phase + opticalMs.toDouble() / drop.periodMs
            val wave = (2.0 * PI * phase).toFloat()
            val dx = sin(wave * .83f) * .007f
            val dy = sin(wave) * .012f
            val x = centerX + ((drop.u - .5f) * 2f + dx) * radius
            val y = centerY + ((drop.v - .5f) * 2f + dy) * radius
            val scale = coreSizeDp / 80f
            val dropletDiameter = drop.diameterDpAt80 * scale * density
            val opacity = stateAlpha
            body.alpha = (255f * drop.bodyAlpha * opacity).toInt().coerceIn(0, 255)
            lens.alpha = (255f * drop.bodyAlpha * opacity).toInt().coerceIn(0, 255)
            rim.alpha = (255f * drop.rimAlpha * opacity).toInt().coerceIn(0, 255)
            rim.strokeWidth = maxOf(.28f, dropletDiameter * .19f)
            glint.alpha = (255f * OrbRuntimeProfileV1.R03_DROPLET_SPECULAR_ALPHA_MAX * opacity).toInt().coerceIn(0, 255)

            val save = canvas.save()
            try {
                canvas.translate(x, y)
                canvas.scale(dropletDiameter, dropletDiameter * 1.22f)
                val path = WaterDropletUnitLensGeometry.path
                canvas.drawPath(path, body)
                canvas.drawPath(path, lens)
                canvas.drawPath(path, rim)
                // The small highlight remains on the profile's fixed upper-left azimuth.
                glintRect.set(.28f, .16f, .43f, .37f)
                canvas.drawOval(glintRect, glint)
            } finally {
                canvas.restoreToCount(save)
            }
        }
    }

}
