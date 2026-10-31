package com.yujian.ai.ui.designsystem.components.aiorb

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
import com.yujian.ai.ui.designsystem.components.YuJianCaptureButton
import com.yujian.ai.ui.designsystem.components.YuJianCaptureButtonRasterAssets
import com.yujian.ai.ui.designsystem.components.YuJianCaptureButtonRasterMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * One public, state-driven visual entry for the YuJian capture button and AI Orb.
 *
 * Hosts own navigation, capture, PhotoSession, inference and result routing.
 * This component owns only the visual state selected by the host.
 */
@Composable
fun AIOrbCore(
    state: AIOrbVisualState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    enabled: Boolean = true,
    contentDescription: String = "开始识鱼",
    motionEnabled: Boolean = true,
    rasterAssets: YuJianCaptureButtonRasterAssets? = null,
    rasterMotion: YuJianCaptureButtonRasterMotion? = null,
    visualSize: Dp? = null,
    touchTargetSize: Dp? = null,
    visualModifier: Modifier = Modifier,
    frozenVisual: Boolean = true,
    onCaptureHaptic: (() -> Unit)? = null,
    clickElapsedMs: Long? = null,
    cameraReady: Boolean = false,
    coreSize: Dp = 80.dp,
    entranceElapsedMs: Long = 0L,
    elapsedMs: Long = 0L,
    completion: Float = 0f,
    reduceMotion: Boolean = false,
    lowPerformance: Boolean = false,
    fromCamera: Boolean = false,
) {
    when (state) {
        AIOrbVisualState.CAPTURE_BUTTON_IDLE -> YuJianCaptureButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            contentDescription = contentDescription,
            motionEnabled = motionEnabled,
            rasterAssets = rasterAssets,
            rasterMotion = rasterMotion,
            visualSize = visualSize,
            touchTargetSize = touchTargetSize,
            visualModifier = visualModifier,
            frozenVisual = frozenVisual,
            onCaptureHaptic = onCaptureHaptic,
            clickElapsedMs = clickElapsedMs,
            cameraReady = cameraReady,
        )
        AIOrbVisualState.CAPTURE_BUTTON_ACTIVATED -> ActivatedCameraWaterState(
            coreSize = coreSize,
            entranceElapsedMs = entranceElapsedMs,
            modifier = modifier,
        )
        AIOrbVisualState.ORB_MORPH_IN,
        AIOrbVisualState.ORB_ACTIVE,
        AIOrbVisualState.ORB_CONTRACTING -> OrbVisual(
            coreSize = coreSize,
            entranceElapsedMs = entranceElapsedMs,
            elapsedMs = elapsedMs,
            completion = completion,
            reduceMotion = reduceMotion,
            lowPerformance = lowPerformance,
            fromCamera = fromCamera,
            modifier = modifier,
        )
        AIOrbVisualState.HIDDEN -> Unit
    }
}

@Composable
private fun ActivatedCameraWaterState(
    coreSize: Dp,
    entranceElapsedMs: Long,
    modifier: Modifier,
) {
    val progress = if (entranceElapsedMs < 0L) 0f else (entranceElapsedMs / 150f).coerceIn(0f, 1f)
    Box(modifier.size(coreSize + 28.dp), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.ai_orb_camera_activated_v1),
            contentDescription = null,
            modifier = Modifier.size(coreSize).graphicsLayer {
                scaleX = 1f - .04f * progress
                scaleY = scaleX
            },
        )
    }
}

@Composable
private fun OrbVisual(
    coreSize: Dp,
    entranceElapsedMs: Long,
    elapsedMs: Long,
    completion: Float,
    reduceMotion: Boolean,
    lowPerformance: Boolean,
    fromCamera: Boolean,
    modifier: Modifier,
) {
    val frame = aiOrbMotionFrame(entranceElapsedMs, fromCamera, reduceMotion, completion)
    val morph = frame.waterAlpha
    val ribbons = frame.ribbonOpening
    val settle = completion.coerceIn(0f, 1f)
    val time = if (reduceMotion) 0f else elapsedMs / 1000f
    val breath = if (reduceMotion) 1f else 1f + .018f * sin(time * (2f * PI.toFloat() / 4.2f))
    Box(
        modifier.size(coreSize + 28.dp).graphicsLayer {
            scaleX = (1f - .18f * settle) * breath
            scaleY = scaleX
            alpha = frame.completionAlpha
        },
        contentAlignment = Alignment.Center,
    ) {
        OrbRibbons(coreSize, time, ribbons, settle, false, lowPerformance)
        if (fromCamera && morph < 1f) {
            Image(
                painter = painterResource(R.drawable.ai_orb_camera_activated_v1),
                contentDescription = null,
                modifier = Modifier.size(coreSize).graphicsLayer {
                    alpha = 1f - morph
                    val press = if (entranceElapsedMs < 150L) 1f - .04f * (entranceElapsedMs / 150f) else .96f
                    scaleX = press
                    scaleY = press
                },
            )
        }
        Image(
            painter = painterResource(R.drawable.processing_water_core_v1),
            contentDescription = null,
            modifier = Modifier.size(coreSize / .84f).graphicsLayer {
                alpha = morph
                scaleX = .92f + .08f * morph
                scaleY = scaleX
            },
        )
        Canvas(Modifier.fillMaxSize()) {
            if (morph <= 0f) return@Canvas
            val radius = coreSize.toPx() * .48f
            val mask = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(center - Offset(radius, radius), center + Offset(radius, radius)))
            }
            clipPath(mask) {
                val glint = center + Offset(sin(time * .7f) * radius * .35f, -radius * .45f)
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0xFFECF9EE).copy(alpha = .20f * morph), Color.Transparent),
                        glint,
                        radius * .9f,
                    ),
                    radius,
                    center,
                )
                repeat(if (lowPerformance) 2 else 4) { i ->
                    val path = Path()
                    val y = center.y - radius * .7f + radius * .44f * i
                    path.moveTo(center.x - radius, y)
                    path.cubicTo(
                        center.x - radius * .3f, y + sin(time + i) * radius * .28f,
                        center.x + radius * .4f, y - cos(time * .8f + i) * radius * .25f,
                        center.x + radius, y + radius * .18f,
                    )
                    drawPath(path, Color(0xFFB5E6DF).copy(alpha = .16f * morph), style = Stroke(.7.dp.toPx()))
                }
            }
        }
        OrbRibbons(coreSize, time, ribbons, settle, true, lowPerformance)
    }
}

@Composable
private fun OrbRibbons(
    coreSize: Dp,
    time: Float,
    opening: Float,
    settle: Float,
    front: Boolean,
    low: Boolean,
) {
    Canvas(Modifier.fillMaxSize()) {
        val radius = coreSize.toPx() * .5f
        val colors = listOf(Color(0xFFD8BC87), Color(0xFF348CA6), Color(0xFF5B8F78))
        colors.forEachIndexed { layer, color ->
            val period = listOf(5.5f, 4.5f, 6.5f)[layer]
            val phase = time * 2f * PI.toFloat() / period * (if (layer == 1) -1f else 1f)
            val tilt = listOf(-.45f, .60f, 1.25f)[layer]
            val radiusWithOpening = radius + (10f + layer * 2f).dp.toPx() * opening * (1f - settle)
            val steps = if (low) 48 else 96
            val path = Path()
            var connected = false
            val sweep = listOf(1.62f, 1.48f, 1.16f)[layer] * PI.toFloat()
            for (i in 0..steps) {
                val angle = i * sweep / steps + phase
                val z = sin(angle)
                val isFront = z >= 0f
                val ripple = 1f + .035f * sin(angle * 3f + time * .8f + layer)
                val x = cos(angle) * radiusWithOpening * ripple
                val y = sin(angle) * radiusWithOpening * ripple * (.33f + .03f * sin(time * .9f + layer))
                val point = center + Offset(
                    x * cos(tilt) - y * sin(tilt),
                    x * sin(tilt) + y * cos(tilt),
                )
                if (isFront == front) {
                    if (!connected) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                    connected = true
                } else connected = false
            }
            val alpha = opening * (if (front) .72f else .25f) * (if (layer == 2) .45f else 1f)
            drawPath(path, color.copy(alpha = alpha * .18f), style = Stroke((if (layer == 2) 3.6f else 4f).dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, color.copy(alpha = alpha), style = Stroke((if (layer == 2) 1.6f else .9f).dp.toPx(), cap = StrokeCap.Round))
            if (layer != 2 && front) drawPath(path, Color(0xFFFFFAE8).copy(alpha = alpha * .5f), style = Stroke(.35f.dp.toPx()))
        }
    }
}
