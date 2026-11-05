package com.yujian.ai.ui.waterlight.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.yujian.ai.ui.waterlight.core.EnergyEvent
import com.yujian.ai.ui.waterlight.core.EnergyFeedback
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
import com.yujian.ai.ui.waterlight.core.WaterLightHost
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometry
import kotlin.math.hypot
import kotlin.math.max

/** Draws only current, identity-matched measured paths. RETURN consumes the stored route in reverse. */
@Composable
fun WaterLightEnergyPathRenderer(
    frame: WaterLightFrame,
    event: EnergyEvent?,
    material: WaterLightMaterialProfile,
    modifier: Modifier = Modifier,
) {
    val current = event ?: return
    if (!current.matches(frame) || current.level <= 0f ||
        current.feedback !in setOf(EnergyFeedback.RELEASE, EnergyFeedback.ARRIVE, EnergyFeedback.ABSORB, EnergyFeedback.RETURN)
    ) return
    val route = current.pathSegments.takeIf { it.isNotEmpty() }
        ?: listOfNotNull(current.pathGeometry)
    if (route.isEmpty() || route.any { !it.isValid() }) return
    val lengths = route.map(::approximateLength)
    val totalLength = lengths.sum().coerceAtLeast(1f)
    val progress = current.level.coerceIn(0f, 1f)

    Canvas(modifier.fillMaxSize()) {
        val visible = Path()
        var remaining = totalLength * progress
        var head: Offset? = null
        route.forEachIndexed { index, geometry ->
            if (remaining <= 0f) return@forEachIndexed
            val length = lengths[index].coerceAtLeast(.001f)
            val localProgress = (remaining / length).coerceIn(0f, 1f)
            val samples = max(1, (48 * localProgress).toInt())
            visible.moveTo(geometry.start.x, geometry.start.y)
            for (sample in 1..samples) {
                val t = localProgress * sample / samples
                val point = cubicPoint(geometry, t)
                visible.lineTo(point.x, point.y)
                head = point
            }
            remaining -= length
        }

        val alpha = material.opacity.coerceIn(0f, 1f) * (.35f + .65f * progress)
        val seeking = material.host == WaterLightHost.PATH && current.feedback == EnergyFeedback.ABSORB
        val cool = Color((if (seeking) material.goldColor else material.secondaryColor).toInt())
        val bright = Color((if (seeking) material.goldColor else material.highlightColor).toInt())
        val dashes = if (seeking) PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 6.dp.toPx())) else null
        drawPath(visible, cool.copy(alpha = .18f * alpha), style = Stroke(if (seeking) 7.dp.toPx() else 14.dp.toPx(), cap = StrokeCap.Round, pathEffect = dashes), blendMode = BlendMode.Screen)
        drawPath(visible, cool.copy(alpha = .36f * alpha), style = Stroke(if (seeking) 2.5f.dp.toPx() else 6.dp.toPx(), cap = StrokeCap.Round, pathEffect = dashes), blendMode = BlendMode.Screen)
        drawPath(visible, bright.copy(alpha = .72f * alpha), style = Stroke(if (seeking) 1.dp.toPx() else 1.4f.dp.toPx(), cap = StrokeCap.Round, pathEffect = dashes), blendMode = BlendMode.Screen)
        head?.let { position ->
            drawCircle(bright.copy(alpha = .8f * alpha), 2.6.dp.toPx(), position, blendMode = BlendMode.Screen)
            drawCircle(cool.copy(alpha = .24f * alpha), 6.dp.toPx(), position, blendMode = BlendMode.Screen)
        }

        // Small lens-shaped droplets are pinned to deterministic samples of the same path.
        listOf(.28f, .62f).forEachIndexed { index, routeFraction ->
            if (progress >= routeFraction) {
                val center = pointOnRoute(route, lengths, totalLength, routeFraction)
                if (center != null) drawLensDroplet(
                    center = center,
                    radius = if (index == 0) 2.2.dp.toPx() else 1.7.dp.toPx(),
                    alpha = alpha * if (index == 0) .76f else .58f,
                )
            }
        }
    }
}

private fun approximateLength(path: WaterLightPathGeometry): Float {
    var previous = cubicPoint(path, 0f)
    var length = 0f
    for (step in 1..32) {
        val current = cubicPoint(path, step / 32f)
        length += hypot(current.x - previous.x, current.y - previous.y)
        previous = current
    }
    return length
}

private fun cubicPoint(path: WaterLightPathGeometry, t: Float): Offset {
    val u = 1f - t
    return Offset(
        u * u * u * path.start.x + 3f * u * u * t * path.control1.x +
            3f * u * t * t * path.control2.x + t * t * t * path.end.x,
        u * u * u * path.start.y + 3f * u * u * t * path.control1.y +
            3f * u * t * t * path.control2.y + t * t * t * path.end.y,
    )
}

private fun pointOnRoute(
    route: List<WaterLightPathGeometry>,
    lengths: List<Float>,
    totalLength: Float,
    progress: Float,
): Offset? {
    var remaining = totalLength * progress.coerceIn(0f, 1f)
    route.forEachIndexed { index, geometry ->
        val length = lengths[index].coerceAtLeast(.001f)
        if (remaining <= length || index == route.lastIndex) {
            return cubicPoint(geometry, (remaining / length).coerceIn(0f, 1f))
        }
        remaining -= length
    }
    return null
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLensDroplet(
    center: Offset,
    radius: Float,
    alpha: Float,
) {
    val shape = Path().apply {
        moveTo(center.x, center.y - radius)
        cubicTo(center.x + radius * .72f, center.y - radius * .34f,
            center.x + radius * .88f, center.y + radius * .52f,
            center.x, center.y + radius)
        cubicTo(center.x - radius * .88f, center.y + radius * .52f,
            center.x - radius * .72f, center.y - radius * .34f,
            center.x, center.y - radius)
        close()
    }
    drawPath(
        shape,
        Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = .48f * alpha),
                Color(0xFF9DE4DF).copy(alpha = .22f * alpha),
                Color(0xFF2E8B83).copy(alpha = .08f * alpha),
            ),
            center = Offset(center.x - radius * .18f, center.y - radius * .2f),
            radius = radius * 1.45f,
        ),
    )
    drawPath(shape, Color(0xFFDDFBFF).copy(alpha = .62f * alpha),
        style = Stroke(width = .55f.dp.toPx()))
    drawLine(Color.White.copy(alpha = .78f * alpha),
        Offset(center.x - radius * .28f, center.y - radius * .38f),
        Offset(center.x - radius * .03f, center.y - radius * .16f),
        strokeWidth = .55f.dp.toPx(), cap = StrokeCap.Round)
}
