package com.yujian.ai.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/** Five staggered open trails. The broad transparent strokes are the soft glow layer. */
@Composable
fun CaptureTransitionLightField(progress: Float, modifier: Modifier = Modifier) {
    val teal = Color(0xFF2E8B83)
    val gold = Color(0xFFD8B36A)
    Canvas(modifier) {
        val p = progress.coerceIn(0f, 1f)
        val spread = if (p < .52f) p / .52f else (1f - p) / .48f
        val alpha = (p * 8f).coerceAtMost(1f) * ((1f - p) * 4f).coerceAtMost(1f)
        val diameter = size.minDimension
        val tracks = listOf(
            Triple(teal, 0f, 76f), Triple(teal, 83f, 48f),
            Triple(teal, 193f, 34f), Triple(gold, 148f, 42f), Triple(gold, 278f, 28f),
        )
        tracks.forEachIndexed { i, (color, phase, length) ->
            val radius = diameter * (.245f + spread * (.08f + i * .021f))
            val rect = Size(radius * 2f, radius * 2f)
            val origin = Offset(center.x - radius, center.y - radius)
            val angle = phase + p * (325f + i * 23f) + sin(p * 9f + i) * 8f
            drawArc(color.copy(alpha = alpha * .10f), angle, length, false, origin, rect,
                style = Stroke(width = 15.dp.toPx(), cap = StrokeCap.Round))
            drawArc(color.copy(alpha = alpha * if (i < 3) .85f else .72f), angle, length, false, origin, rect,
                style = Stroke(width = (if (i == 0) 2.4f else 1.2f).dp.toPx(), cap = StrokeCap.Round))
        }
        repeat(5) { i ->
            val angle = p * 7f + i * 1.26f
            val distance = diameter * (.28f + spread * .21f)
            drawCircle(gold.copy(alpha = alpha * .35f), radius = (1.2f + i % 2).dp.toPx(),
                center = center + Offset(kotlin.math.cos(angle) * distance, kotlin.math.sin(angle) * distance))
        }
    }
}
