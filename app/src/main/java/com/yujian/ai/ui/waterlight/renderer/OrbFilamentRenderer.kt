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
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
import com.yujian.ai.ui.waterlight.core.EnergyEvent
import com.yujian.ai.ui.waterlight.core.EnergyFeedback
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Extracted without changing Orb path samples, palette, stroke order or alpha. */
@Composable
fun OrbWaterLightRibbons(coreSize: Dp, frame: WaterLightFrame, opening: Float, settle: Float, front: Boolean, low: Boolean,
    material: WaterLightMaterialProfile = WaterLightMaterialProfile.ORB_LEGACY,
    energyEvent: EnergyEvent? = null,
    releaseEnergy: Float = 0f,
    returnEnergy: Float = 0f,
) {
    val time = frame.timeSeconds
    Canvas(Modifier.fillMaxSize()) {
        val radius = coreSize.toPx() * .5f
        val referenceMs = frame.opticalReferenceElapsedMs
        val retention = when {
            referenceMs < 350L -> 1f
            referenceMs < 950L -> 1f - .42f * ((referenceMs - 350L) / 600f).coerceIn(0f, 1f)
            referenceMs < 2_800L -> .58f
            energyEvent?.feedback == EnergyFeedback.RETURN -> .58f + .42f * returnEnergy.coerceIn(0f, 1f)
            else -> .58f
        }
        val colors = listOf(Color(material.goldColor.toInt()), Color(material.blueColor.toInt()), Color(material.greenColor.toInt()))
        colors.forEachIndexed { layer, color ->
            val period = listOf(material.goldPeriodSeconds, material.bluePeriodSeconds, material.greenPeriodSeconds)[layer]
            val phase = time * 2f * PI.toFloat() / period * (if (layer == 1) -1f else 1f)
            val tilt = listOf(-.45f, .60f, 1.25f)[layer]
            val released = releaseEnergy.coerceIn(0f, 1f)
            val returned = returnEnergy.coerceIn(0f, 1f)
            val expansion = if (energyEvent?.feedback == EnergyFeedback.RELEASE) released else 0f
            val contraction = if (energyEvent?.feedback == EnergyFeedback.RETURN) returned else 0f
            val r = radius + (10f + layer * 2f).dp.toPx() * opening * (1f - settle) +
                radius * .42f * expansion * (1f - contraction)
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
            val alpha = opening * (if (front) .72f else .25f) * (if (layer == 2) .45f else 1f) *
                (1f - .42f * expansion + .38f * contraction).coerceIn(.35f, 1f) * retention
            val glowWidth = listOf(material.goldGlowWidthDp, material.blueGlowWidthDp, material.greenGlowWidthDp)[layer]
            val coreWidth = listOf(material.goldCoreWidthDp, material.blueCoreWidthDp, material.greenCoreWidthDp)[layer]
            drawPath(path, color.copy(alpha = alpha * .18f), style = Stroke(glowWidth.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, color.copy(alpha = alpha), style = Stroke(coreWidth.dp.toPx(), cap = StrokeCap.Round))
            if (layer != 2 && front) drawPath(path, Color(0xFFFFFAE8).copy(alpha = alpha * .5f), style = Stroke(.35.dp.toPx()))
        }
    }
}
