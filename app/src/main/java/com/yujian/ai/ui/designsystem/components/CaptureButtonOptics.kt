package com.yujian.ai.ui.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/** Transparent light only. The frozen button art, icon and touch geometry stay untouched. */
@Composable
fun CaptureButtonOptics(
    visualSize: Dp,
    progress: Float,
    click: Boolean,
    modifier: Modifier = Modifier,
    cameraReady: Boolean = false,
) {
    val teal = Color(0xFF55BFC0)
    val gold = Color(0xFFD8B36A)
    Canvas(modifier) {
        val p = progress.coerceIn(0f, 1f)
        val baseRadius = visualSize.toPx() * .47f
        val box = center
        fun arc(radius: Float, start: Float, sweep: Float, color: Color, width: Float) {
            drawArc(color, start, sweep, false,
                Offset(box.x - radius, box.y - radius), Size(radius * 2f, radius * 2f),
                style = Stroke(width = width, cap = StrokeCap.Round))
        }
        if (click) {
            // One 750ms pulse: wake -> outward/inward transmission -> meet -> settle.
            val wake = (p / .1333f).coerceIn(0f, 1f)
            val travel = ((p - .1333f) / .5333f).coerceIn(0f, 1f)
            val fade = ((1f - p) / .3333f).coerceIn(0f, 1f)
            val a = wake * fade
            val outerRadius = baseRadius + 12.dp.toPx() * travel
            // One translucent main water ring, feathered rather than neon.
            arc(outerRadius, -90f, 360f, teal.copy(alpha = .025f * a), 10.dp.toPx())
            arc(outerRadius, -90f, 360f, teal.copy(alpha = .07f * a), 6.dp.toPx())
            arc(outerRadius, -90f, 360f, teal.copy(alpha = .23f * a), 1.4.dp.toPx())
            for (index in 0..2) {
                val start = -135f + p * 65f + index * 120f
                arc(outerRadius, start, 65f, teal.copy(alpha = .55f * a), 1.5.dp.toPx())
                arc(outerRadius - 1.dp.toPx(), start + 58f, 64f, gold.copy(alpha = .12f * a), 7.dp.toPx())
                arc(outerRadius - 1.dp.toPx(), start + 58f, 64f, gold.copy(alpha = .57f * a), 1.5.dp.toPx())
                arc(baseRadius - 2.dp.toPx(), start + 100f, 24f, Color(0xFF89B690).copy(alpha = .24f * a), 2.dp.toPx())
            }
            val innerRadius = baseRadius * (.86f - .56f * travel)
            // Refraction stays clear of the central camera glyph.
            arc(innerRadius, 190f + p * 90f, 70f, teal.copy(alpha = .14f * a), 1.dp.toPx())
            arc(innerRadius, 15f + p * 90f, 70f, gold.copy(alpha = .14f * a), 1.dp.toPx())
        } else {
            // A 600 ms glint in each 3000 ms idle cycle; no full-ring rotation.
            val local = (p * 5f).coerceIn(0f, 1f)
            val a = sin(local * Math.PI).toFloat().coerceAtLeast(0f)
            arc(baseRadius, -120f + local * 84f, 64f,
                teal.copy(alpha = .14f * a), 2.dp.toPx())
            if (cameraReady) {
                val r = baseRadius + 6.dp.toPx()
                arc(r, -130f, 74f, teal.copy(alpha = .10f + .08f * a), 1.dp.toPx())
                arc(r, 55f, 58f, gold.copy(alpha = .10f + .08f * a), 1.dp.toPx())
            }
            arc(baseRadius, -145f + local * 82f, 26f,
                gold.copy(alpha = .18f * a), 1.dp.toPx())
        }
    }
}
