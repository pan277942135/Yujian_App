package com.yujian.ai.ui.recognition.reveal

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.sin

/** Glyph capture for a real classifier display name, sampled from the existing RR09 clock. */
internal class SpeciesNameReveal {
    private val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.1f
        color = android.graphics.Color.rgb(235, 202, 143)
    }

    fun draw(canvas: Canvas, glyph: Path, width: Float, height: Float, progress: Float, elapsedMs: Long) {
        if (glyph.isEmpty || width <= 0f || height <= 0f) return
        val p = progress.coerceIn(0f, 1f)
        if (p <= 0f) return
        val save = canvas.save()
        try {
            // A moving, soft front captures actual glyph pixels; no fixed name bitmap or second clock.
            val lead = width * p
            canvas.clipRect(0f, 0f, lead, height)
            canvas.clipPath(glyph)
            val drift = if (p >= 1f) 0f else sin(elapsedMs.coerceAtLeast(0L) / 180f) * width * .035f
            waterPaint.shader = LinearGradient(
                -width * .15f + drift, height, width * 1.1f + drift, 0f,
                intArrayOf(0xFF0B4A5B.toInt(), 0xFF2D9BA5.toInt(), 0xFF9BCFD2.toInt(), 0xFF153C55.toInt()),
                null, Shader.TileMode.CLAMP,
            )
            waterPaint.alpha = (235 * p).toInt().coerceIn(0, 235)
            canvas.drawPath(glyph, waterPaint)
            waterPaint.shader = null
            // The glint is a small local edge fragment at the active capture front.
            if (p < 1f) {
                val local = canvas.save()
                canvas.clipRect((lead - width * .09f).coerceAtLeast(0f), 0f, lead, height)
                glintPaint.alpha = (160 * (1f - p * .45f)).toInt()
                canvas.drawPath(glyph, glintPaint)
                canvas.restoreToCount(local)
            }
        } finally {
            canvas.restoreToCount(save)
        }
    }
}
