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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.unit.dp
import com.yujian.ai.ui.waterlight.core.EnergyEvent
import com.yujian.ai.ui.waterlight.core.EnergyFeedback
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometry
import kotlin.math.max

/** Draws only a current, identity-matched event whose real viewport anchors came from this photo. */
@Composable
fun WaterLightEnergyPathRenderer(
    frame: WaterLightFrame,
    event: EnergyEvent?,
    material: WaterLightMaterialProfile,
    modifier: Modifier = Modifier,
) {
    val current = event ?: return
    val geometry = current.pathGeometry?.takeIf(WaterLightPathGeometry::isValid) ?: return
    if (!current.matches(frame) || current.level <= 0f ||
        current.feedback !in setOf(EnergyFeedback.RELEASE, EnergyFeedback.ARRIVE, EnergyFeedback.ABSORB, EnergyFeedback.RETURN)
    ) return
    val progress = current.level.coerceIn(0f, 1f)
    Canvas(modifier.fillMaxSize()) {
        val samples = max(1, (64 * progress).toInt())
        val visible = Path()
        var previous = Offset(geometry.start.x, geometry.start.y)
        visible.moveTo(previous.x, previous.y)
        for (index in 1..samples) {
            val t = progress * index / samples
            val oneMinusT = 1f - t
            val point = Offset(
                oneMinusT * oneMinusT * oneMinusT * geometry.start.x +
                    3f * oneMinusT * oneMinusT * t * geometry.control1.x +
                    3f * oneMinusT * t * t * geometry.control2.x + t * t * t * geometry.end.x,
                oneMinusT * oneMinusT * oneMinusT * geometry.start.y +
                    3f * oneMinusT * oneMinusT * t * geometry.control1.y +
                    3f * oneMinusT * t * t * geometry.control2.y + t * t * t * geometry.end.y,
            )
            visible.lineTo(point.x, point.y)
            previous = point
        }
        val alpha = material.opacity.coerceIn(0f, 1f) * progress
        val cool = Color(material.secondaryColor.toInt())
        val bright = Color(material.highlightColor.toInt())
        drawPath(visible, cool.copy(alpha = .18f * alpha), style = Stroke(14.dp.toPx(), cap = StrokeCap.Round), blendMode = BlendMode.Screen)
        drawPath(visible, cool.copy(alpha = .36f * alpha), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round), blendMode = BlendMode.Screen)
        drawPath(visible, bright.copy(alpha = .72f * alpha), style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round), blendMode = BlendMode.Screen)
        // The leading bead is attached to the exact current Bézier sample, never randomized per frame.
        drawCircle(bright.copy(alpha = .8f * alpha), 2.6.dp.toPx(), previous, blendMode = BlendMode.Screen)
        drawCircle(cool.copy(alpha = .24f * alpha), 6.dp.toPx(), previous, blendMode = BlendMode.Screen)
    }
}
