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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yujian.ai.R
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.renderer.OrbWaterLightRibbons
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
    waterLightFrame: WaterLightFrame,
) {
    val frame = processingOrbMotionFrame(entranceElapsedMs, fromCamera, reduceMotion, completion)
    val morph = frame.waterAlpha
    val ribbons = frame.ribbonOpening
    val settle = completion.coerceIn(0f, 1f)
    val time = waterLightFrame.timeSeconds
    val breath = if (reduceMotion) 1f else 1f + .018f * sin(time * (2f * PI.toFloat() / 4.2f))
    Box(modifier.size(coreSize + 28.dp).graphicsLayer {
        scaleX = (1f - .18f * settle) * breath
        scaleY = scaleX
        alpha = frame.completionAlpha
    }, contentAlignment = Alignment.Center) {
        OrbWaterLightRibbons(coreSize, waterLightFrame, ribbons, settle, false, lowPerformance)
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
        OrbWaterLightRibbons(coreSize, waterLightFrame, ribbons, settle, true, lowPerformance)
    }
}
