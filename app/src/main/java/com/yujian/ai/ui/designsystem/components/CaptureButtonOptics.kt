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
        val orbitRadius = visualSize.toPx() * 0.372f
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

        // Persistent restrained -45° pearl highlight on the morning light entry point
        val pearlAngle = -45f * (PI.toFloat() / 180f)
        val pearlCenter = Offset(
            centerPoint.x + orbitRadius * cos(pearlAngle),
            centerPoint.y + orbitRadius * sin(pearlAngle),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    specularGold.copy(alpha = .55f),
                    morningGold.copy(alpha = .18f),
                    Color.Transparent,
                ),
                center = pearlCenter,
                radius = 6.dp.toPx(),
            ),
            radius = 6.dp.toPx(),
            center = pearlCenter,
            blendMode = BlendMode.Screen,
        )
        drawCircle(Color.White.copy(alpha = .85f), radius = 1.4.dp.toPx(), center = pearlCenter)
        drawEllipseArc(orbitRadius, orbitRadius, -65f, 40f, specularGold.copy(alpha = .38f), 1.2.dp.toPx())

        if (click) {
            // Three click-only concentric water rings. Maximum outward travel is exactly 36dp.
            val p = progress.coerceIn(0f, 1f)
            val spread = CaptureVisualTokens.ClickRippleSpreadDp.dp.toPx()
            val clickRadiusX = visualSize.toPx() * .40f
            val clickRadiusY = clickRadiusX * .64f
            for (wave in 0..2) {
                val delay = wave * .15f
                if (p > delay) {
                    val waveP = ((p - delay) / (1f - delay)).coerceIn(0f, 1f)
                    val eased = 1f - (1f - waveP) * (1f - waveP)
                    val scale = .75f + wave * .125f
                    val rx = clickRadiusX + spread * eased * scale
                    val ry = clickRadiusY + spread * .58f * eased * scale
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
            // Idle: emerges from button at -45°, sweeps around perimeter, then gracefully dissolves back into the button.
            val cycleMs = CaptureVisualTokens.IdleCycleMs.toLong()
            val cycle = ((effectiveTime % cycleMs) + cycleMs) % cycleMs
            val glintMs = CaptureVisualTokens.IdleGlintMs.toFloat()
            val isGliding = cycle < CaptureVisualTokens.IdleGlintMs
            val sweepProgress = if (isGliding) (cycle / glintMs).coerceIn(0f, 1f) else 1f

            val flowEnvelope = if (!isGliding) 0f else when {
                sweepProgress < 0.15f -> {
                    val p = sweepProgress / 0.15f
                    p * p * (3f - 2f * p)
                }
                sweepProgress > 0.85f -> {
                    val p = (1f - sweepProgress) / 0.15f
                    p * p * (3f - 2f * p)
                }
                else -> 1f
            }

            if (flowEnvelope > 0.001f) {
                val angle = captureIdleFlareAngleDegrees(effectiveTime)
                val radians = angle * (PI.toFloat() / 180f)
                val flareCenter = Offset(
                    centerPoint.x + orbitRadius * cos(radians),
                    centerPoint.y + orbitRadius * sin(radians),
                )
                val sunDelta = (angle + 45f) * (PI.toFloat() / 180f)
                val sunFactor = .68f + .32f * cos(sunDelta)
                val intensity = flowEnvelope * sunFactor

                // 1. Comet fluid trail trailing behind the flare along the water groove
                drawEllipseArc(
                    orbitRadius,
                    orbitRadius,
                    angle - 42f,
                    42f,
                    lightTeal.copy(alpha = .22f * intensity),
                    (2.8.dp * intensity).toPx(),
                )
                drawEllipseArc(
                    orbitRadius,
                    orbitRadius,
                    angle - 24f,
                    24f,
                    morningGold.copy(alpha = .55f * intensity),
                    (1.8.dp * intensity).toPx(),
                )
                drawEllipseArc(
                    orbitRadius,
                    orbitRadius,
                    angle - 10f,
                    10f,
                    specularGold.copy(alpha = .80f * intensity),
                    (1.0.dp * intensity).toPx(),
                )

                // 2. Translucent, delicate water-light bloom (compact, staying within button)
                val bloomRadius = (7.dp * (.82f + .18f * intensity)).toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            morningGold.copy(alpha = .30f * intensity),
                            lightTeal.copy(alpha = .10f * intensity),
                            Color.Transparent,
                        ),
                        center = flareCenter,
                        radius = bloomRadius,
                    ),
                    radius = bloomRadius,
                    center = flareCenter,
                    blendMode = BlendMode.Screen,
                )
                val innerBloom = (3.8.dp * (.85f + .15f * intensity)).toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            specularGold.copy(alpha = .78f * intensity),
                            Color.Transparent,
                        ),
                        center = flareCenter,
                        radius = innerBloom,
                    ),
                    radius = innerBloom,
                    center = flareCenter,
                    blendMode = BlendMode.Screen,
                )

                // 3. Tangential micro-gleam strictly oriented along the circular groove
                val tangentRad = (angle + 90f) * (PI.toFloat() / 180f)
                val tangentDx = cos(tangentRad)
                val tangentDy = sin(tangentRad)
                val rayLength = (4.5.dp * intensity).toPx()
                val rayStart = Offset(flareCenter.x - tangentDx * rayLength, flareCenter.y - tangentDy * rayLength)
                val rayEnd = Offset(flareCenter.x + tangentDx * rayLength, flareCenter.y + tangentDy * rayLength)
                drawLine(
                    brush = Brush.linearGradient(
                        0f to Color.Transparent,
                        .5f to Color.White.copy(alpha = .75f * intensity),
                        1f to Color.Transparent,
                        start = rayStart,
                        end = rayEnd,
                    ),
                    start = rayStart,
                    end = rayEnd,
                    strokeWidth = (1.0.dp * intensity).toPx(),
                    cap = StrokeCap.Round,
                    blendMode = BlendMode.Screen,
                )

                // 4. Pinpoint diamond specular core
                drawCircle(
                    color = Color(0xFFFFFBEA).copy(alpha = .88f * intensity),
                    radius = 2.0.dp.toPx(),
                    center = flareCenter,
                    blendMode = BlendMode.Screen,
                )
                drawCircle(
                    color = Color.White.copy(alpha = intensity),
                    radius = 1.1.dp.toPx(),
                    center = flareCenter,
                    blendMode = BlendMode.Screen,
                )
            }
        }

        if (cameraReady) {
            drawEllipseArc(orbitRadius, orbitRadius, -125f, 68f, lightTeal.copy(alpha = .22f), 1.4.dp.toPx())
            drawEllipseArc(orbitRadius, orbitRadius, 52f, 52f, morningGold.copy(alpha = .22f), 1.4.dp.toPx())
        }
    }
}
