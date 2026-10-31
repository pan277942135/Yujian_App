package com.yujian.ai.ui.waterlight.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Extracted without changing Orb path samples, palette, stroke order or alpha. */
@Composable
fun OrbWaterLightRibbons(coreSize: Dp, frame: WaterLightFrame, opening: Float, settle: Float, front: Boolean, low: Boolean) {
    val time = frame.timeSeconds
    Canvas(Modifier.fillMaxSize()) {
        val radius = coreSize.toPx() * .5f
        val colors = listOf(Color(0xFFD8BC87), Color(0xFF348CA6), Color(0xFF5B8F78))
        colors.forEachIndexed { layer, color ->
            val period = listOf(5.5f, 4.5f, 6.5f)[layer]
            val phase = time * 2f * PI.toFloat() / period * (if (layer == 1) -1f else 1f)
            val tilt = listOf(-.45f, .60f, 1.25f)[layer]
            val r = radius + (10f + layer * 2f).dp.toPx() * opening * (1f - settle)
            val steps = if (low) 48 else 96
            val path = Path()
            var connected = false
            // Open, irregular light trails: no complete mechanical orbit rings.
            val sweep = listOf(1.62f, 1.48f, 1.16f)[layer] * PI.toFloat()
            for (i in 0..steps) {
                val a = i * sweep / steps + phase
                val z = sin(a)
                val isFront = z >= 0f
                val ripple = 1f + .035f * sin(a * 3f + time * .8f + layer)
                val x = cos(a) * r * ripple
                val y = sin(a) * r * ripple * (.33f + .03f * sin(time * .9f + layer))
                val point = center + Offset(x * cos(tilt) - y * sin(tilt), x * sin(tilt) + y * cos(tilt))
                if (isFront == front) {
                    if (!connected) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                    connected = true
                } else connected = false
            }
            val alpha = opening * (if (front) .72f else .25f) * (if (layer == 2) .45f else 1f)
            drawPath(path, color.copy(alpha = alpha * .18f), style = Stroke((if (layer == 2) 3.6f else 4f).dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, color.copy(alpha = alpha), style = Stroke((if (layer == 2) 1.6f else .9f).dp.toPx(), cap = StrokeCap.Round))
            if (layer != 2 && front) drawPath(path, Color(0xFFFFFAE8).copy(alpha = alpha * .5f), style = Stroke(.35.dp.toPx()))
        }
    }
}
