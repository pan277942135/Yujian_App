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
            // 0-100 wake; 100-400 outward travel; 400-700 meeting; 700-1000 settle.
            val wake = (p / .10f).coerceIn(0f, 1f)
            val travel = ((p - .10f) / .60f).coerceIn(0f, 1f)
            val fade = ((1f - p) / .30f).coerceIn(0f, 1f)
            val a = wake * fade
            val outerRadius = baseRadius + 12.dp.toPx() * travel
            arc(outerRadius, -125f + p * 55f, 78f, teal.copy(alpha = .22f * a), 8.dp.toPx())
            arc(outerRadius, -125f + p * 55f, 78f, teal.copy(alpha = .72f * a), 1.7.dp.toPx())
            arc(outerRadius - 2.dp.toPx(), 46f + p * 80f, 38f,
                gold.copy(alpha = .38f * a), 1.2.dp.toPx())
            val inward = ((p - .12f) / .58f).coerceIn(0f, 1f)
            val innerRadius = baseRadius * (.83f - .48f * inward)
            arc(innerRadius, 210f + p * 70f, 54f,
                teal.copy(alpha = .22f * a), 1.dp.toPx())
            arc(innerRadius, 290f + p * 58f, 28f,
                gold.copy(alpha = .16f * a), .8.dp.toPx())
        } else {
            // A 600 ms glint in each 3000 ms idle cycle; no full-ring rotation.
            val local = (p * 5f).coerceIn(0f, 1f)
            val a = sin(local * Math.PI).toFloat().coerceAtLeast(0f)
            arc(baseRadius, -120f + local * 84f, 64f,
                teal.copy(alpha = .14f * a), 2.dp.toPx())
            arc(baseRadius, -145f + local * 82f, 26f,
                gold.copy(alpha = .18f * a), 1.dp.toPx())
        }
    }
}
