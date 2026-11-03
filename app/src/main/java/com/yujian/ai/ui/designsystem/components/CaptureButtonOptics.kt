package com.yujian.ai.ui.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yujian.ai.ui.home.CaptureVisualTokens
import com.yujian.ai.ui.home.captureIdleFlareAngleDegrees
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Transparent optics only: never paints over the central camera glyph or orb core. */
@Composable
fun CaptureButtonOptics(
    visualSize: Dp,
    progress: Float = 0f,
    click: Boolean = false,
    modifier: Modifier = Modifier,
    cameraReady: Boolean = false,
    sceneTimeMs: Long? = null,
) {
    val ticker = rememberInfiniteTransition(label = "CaptureOpticsClock")
    val idleCycle by ticker.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(CaptureVisualTokens.IdleCycleMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "CaptureOpticsCycle",
    )

    val lakeTeal = Color(0xFF2E8B83)
    val lightTeal = Color(0xFF45C4BE)
    val morningGold = Color(0xFFD8B36A)
    val specularGold = Color(0xFFFFF4D0)

    Canvas(modifier) {
        val effectiveTime = sceneTimeMs ?: (idleCycle * CaptureVisualTokens.IdleCycleMs).toLong()
        val radiusX = visualSize.toPx() * .48f
        val radiusY = radiusX * .64f
        val centerPoint = center

        fun drawEllipseArc(
            rx: Float,
            ry: Float,
            start: Float,
            sweep: Float,
            color: Color,
            width: Float,
        ) {
            drawArc(
                color = color,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(centerPoint.x - rx, centerPoint.y - ry),
                size = Size(rx * 2f, ry * 2f),
                style = Stroke(width = width, cap = StrokeCap.Round),
                blendMode = BlendMode.Screen,
            )
        }

        // Persistent -45° pearl highlight: a white specular core with a restrained gold halo.
        val pearlRadius = radiusX * .70f
        val pearlAngle = -45f * (PI.toFloat() / 180f)
        val pearlCenter = Offset(
            centerPoint.x + pearlRadius * cos(pearlAngle),
            centerPoint.y + pearlRadius * sin(pearlAngle),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    specularGold.copy(alpha = .82f),
                    morningGold.copy(alpha = .28f),
                    Color.Transparent,
                ),
                center = pearlCenter,
                radius = 11.dp.toPx(),
            ),
            radius = 11.dp.toPx(),
            center = pearlCenter,
            blendMode = BlendMode.Screen,
        )
        drawCircle(Color.White.copy(alpha = .98f), radius = 2.8.dp.toPx(), center = pearlCenter)
        drawEllipseArc(radiusX * .98f, radiusX * .98f, -75f, 60f, specularGold.copy(alpha = .55f), 1.8.dp.toPx())

        if (click) {
            // Three click-only concentric water rings. Maximum outward travel is exactly 36dp.
            val p = progress.coerceIn(0f, 1f)
            val spread = CaptureVisualTokens.ClickRippleSpreadDp.dp.toPx()
            for (wave in 0..2) {
                val delay = wave * .15f
                if (p > delay) {
                    val waveP = ((p - delay) / (1f - delay)).coerceIn(0f, 1f)
                    val eased = 1f - (1f - waveP) * (1f - waveP)
                    val scale = .75f + wave * .125f
                    val rx = radiusX + spread * eased * scale
                    val ry = radiusY + spread * .58f * eased * scale
                    val envelope = sin(waveP * PI.toFloat()).coerceAtLeast(0f) * (1f - waveP * .35f)
                    if (envelope > .008f) {
                        val glowWidth = (10.dp * (1f - waveP * .30f)).toPx()
                        val crestWidth = (2.1.dp * (1f - waveP * .35f)).toPx()
                        drawEllipseArc(rx, ry, -180f, 360f, lightTeal.copy(alpha = .16f * envelope), glowWidth)
                        drawEllipseArc(rx, ry, -180f, 360f, lightTeal.copy(alpha = .44f * envelope), crestWidth)
                        drawEllipseArc(rx, ry, -88f, 128f, morningGold.copy(alpha = .66f * envelope), crestWidth * 1.25f)
                        drawEllipseArc(rx, ry, -62f, 76f, specularGold.copy(alpha = .72f * envelope), crestWidth * .72f)
                        drawEllipseArc(rx, ry, 132f, 56f, morningGold.copy(alpha = .34f * envelope), crestWidth)
                    }
                }
            }
        } else {
            // Idle: one 2.5s eased orbit, followed by 8s of stillness. No outward idle ripple.
            val angle = captureIdleFlareAngleDegrees(effectiveTime)
            val radians = angle * (PI.toFloat() / 180f)
            val orbitRadius = radiusX * 1.04f
            val flareCenter = Offset(
                centerPoint.x + orbitRadius * cos(radians),
                centerPoint.y + orbitRadius * sin(radians),
            )
            val sunDelta = (angle + 45f) * (PI.toFloat() / 180f)
            val sunFactor = .5f + .5f * cos(sunDelta)
            val intensity = .72f + .28f * sunFactor

            drawEllipseArc(
                orbitRadius,
                orbitRadius,
                angle - 24f,
                48f,
                morningGold.copy(alpha = .46f * intensity),
                (3.6.dp * intensity).toPx(),
            )
            drawEllipseArc(
                orbitRadius,
                orbitRadius,
                angle - 11f,
                22f,
                specularGold.copy(alpha = .82f * intensity),
                (1.8.dp * intensity).toPx(),
            )
            val bloomRadius = (14.dp * (.85f + .20f * intensity)).toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        morningGold.copy(alpha = .34f * intensity),
                        morningGold.copy(alpha = .10f * intensity),
                        Color.Transparent,
                    ),
                    center = flareCenter,
                    radius = bloomRadius,
                ),
                radius = bloomRadius,
                center = flareCenter,
                blendMode = BlendMode.Screen,
            )
            val innerBloom = (7.dp * (.85f + .20f * intensity)).toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(specularGold.copy(alpha = .82f * intensity), Color.Transparent),
                    center = flareCenter,
                    radius = innerBloom,
                ),
                radius = innerBloom,
                center = flareCenter,
                blendMode = BlendMode.Screen,
            )

            fun drawFlareRay(angleDeg: Float, length: Float, width: Float, alpha: Float) {
                val rad = angleDeg * (PI.toFloat() / 180f)
                val dx = cos(rad)
                val dy = sin(rad)
                val start = Offset(flareCenter.x - dx * length, flareCenter.y - dy * length)
                val end = Offset(flareCenter.x + dx * length, flareCenter.y + dy * length)
                drawLine(
                    brush = Brush.linearGradient(
                        0f to Color.Transparent,
                        .5f to Color.White.copy(alpha = alpha * intensity),
                        1f to Color.Transparent,
                        start = start,
                        end = end,
                    ),
                    start = start,
                    end = end,
                    strokeWidth = width,
                    cap = StrokeCap.Round,
                    blendMode = BlendMode.Screen,
                )
            }
            drawFlareRay(angle + 90f, 14.dp.toPx(), 1.5.dp.toPx(), .92f)
            drawFlareRay(angle, 10.dp.toPx(), 1.2.dp.toPx(), .72f)
            drawFlareRay(angle + 45f, 7.dp.toPx(), .9.dp.toPx(), .52f)
            drawFlareRay(angle + 135f, 7.dp.toPx(), .9.dp.toPx(), .52f)
            drawCircle(Color(0xFFFFFBEA).copy(alpha = .92f * intensity), 3.dp.toPx(), flareCenter, blendMode = BlendMode.Screen)
            drawCircle(Color.White.copy(alpha = intensity), 1.5.dp.toPx(), flareCenter, blendMode = BlendMode.Screen)
        }

        if (cameraReady) {
            drawEllipseArc(radiusX * 1.08f, radiusX * 1.08f, -125f, 68f, lightTeal.copy(alpha = .22f), 1.6.dp.toPx())
            drawEllipseArc(radiusX * 1.08f, radiusX * 1.08f, 52f, 52f, morningGold.copy(alpha = .22f), 1.6.dp.toPx())
        }
    }
}
