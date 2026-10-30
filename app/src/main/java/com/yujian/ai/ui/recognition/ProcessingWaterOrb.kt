package com.yujian.ai.ui.recognition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yujian.ai.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The original capture art dissolves into a water core; ribbons have independent optical periods. */
@Composable
fun ProcessingWaterOrb(
    coreSize: Dp,
    entranceElapsedMs: Long,
    elapsedMs: Long,
    completion: Float,
    reduceMotion: Boolean,
    lowPerformance: Boolean,
    fromCamera: Boolean,
    modifier: Modifier = Modifier,
) {
    val frame = processingOrbMotionFrame(entranceElapsedMs, fromCamera, reduceMotion, completion)
    val morph = frame.waterAlpha
    val ribbons = frame.ribbonOpening
    val settle = completion.coerceIn(0f, 1f)
    val time = if (reduceMotion) 0f else elapsedMs / 1000f
    val breath = if (reduceMotion) 1f else 1f + .018f * sin(time * (2f * PI.toFloat() / 4.2f))
    Box(modifier.size(coreSize + 28.dp).graphicsLayer {
        scaleX = (1f - .18f * settle) * breath
        scaleY = scaleX
        alpha = frame.completionAlpha
    }, contentAlignment = Alignment.Center) {
        OrbRibbons(coreSize, time, ribbons, settle, false, lowPerformance)
        if (morph < 1f) Image(
            painterResource(R.drawable.capture_button_main_v1), null,
            Modifier.size(coreSize).graphicsLayer {
                alpha = 1f - morph
                val press = if (entranceElapsedMs < 150L) 1f - .04f * (entranceElapsedMs / 150f) else .96f
                scaleX = press; scaleY = press
            },
        )
        Image(painterResource(R.drawable.processing_water_core_v1), null,
            Modifier.size(coreSize / .84f).graphicsLayer {
                alpha = morph
                scaleX = .92f + .08f * morph; scaleY = scaleX
            })
        // Gentle moving internal refraction; the core texture itself never spins as a flat disk.
        Canvas(Modifier.fillMaxSize()) {
            if (morph <= 0f) return@Canvas
            val radius = coreSize.toPx() * .48f
            val mask = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center - Offset(radius, radius), center + Offset(radius, radius))) }
            clipPath(mask) {
                val glint = center + Offset(sin(time * .7f) * radius * .35f, -radius * .45f)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFECF9EE).copy(alpha = .20f * morph), Color.Transparent), glint, radius * .9f), radius, center)
                repeat(if (lowPerformance) 2 else 4) { i ->
                    val path = Path()
                    val y = center.y - radius * .7f + radius * .44f * i
                    path.moveTo(center.x - radius, y)
                    path.cubicTo(center.x - radius * .3f, y + sin(time + i) * radius * .28f,
                        center.x + radius * .4f, y - cos(time * .8f + i) * radius * .25f,
                        center.x + radius, y + radius * .18f)
                    drawPath(path, Color(0xFFB5E6DF).copy(alpha = .16f * morph), style = Stroke(.7.dp.toPx()))
                }
            }
        }
        OrbRibbons(coreSize, time, ribbons, settle, true, lowPerformance)
    }
}

@Composable
private fun OrbRibbons(coreSize: Dp, time: Float, opening: Float, settle: Float, front: Boolean, low: Boolean) {
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
            for (i in 0..steps) {
                val a = i * 2f * PI.toFloat() / steps + phase
                val z = sin(a)
                val isFront = z >= 0f
                val x = cos(a) * r
                val y = sin(a) * r * (.33f + .03f * sin(time * .9f + layer))
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
