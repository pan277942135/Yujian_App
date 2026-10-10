package com.yujian.ai.ui.designsystem.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.haptic.YuJianHaptic
import com.yujian.ai.ui.designsystem.haptic.rememberYuJianHaptic
import com.yujian.ai.ui.designsystem.motion.YuJianMotion
import com.yujian.ai.ui.designsystem.motion.rememberYuJianInfiniteTransition
import com.yujian.ai.ui.designsystem.motion.rememberYuJianReduceMotion
import com.yujian.ai.ui.designsystem.spacing.YuJianSpacing
import kotlin.math.PI
import kotlin.math.sin

private val CaptureVisualSize = 56.dp
private val CaptureIconSize = 24.dp
private val RasterCaptureVisualSize = 64.dp

/** Frozen Empty Home V2 raster masters, passed by the home renderer. */
data class YuJianCaptureButtonRasterAssets(
    val base: ImageBitmap,
    val goldRimSweep: ImageBitmap,
    val breathGlow: ImageBitmap,
)

/** Scene-clock values keep Empty Home idle effects phase-independent. */
data class YuJianCaptureButtonRasterMotion(
    val breathScale: Float,
    val breathGlowAlpha: Float,
    val sweepRotationDegrees: Float,
    val sweepAlpha: Float,
)

/**
 * Native Core UI V1 capture control.
 *
 * Defaults remain density-based for shared callers. Empty Home may supply a
 * reference-space visual/touch size so its raster master lands on the exact
 * Frozen V2 bbox without changing other capture-button call sites.
 */
@Composable
fun YuJianCaptureButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = "开始识鱼",
    motionEnabled: Boolean = true,
    rasterAssets: YuJianCaptureButtonRasterAssets? = null,
    rasterMotion: YuJianCaptureButtonRasterMotion? = null,
    visualSize: Dp? = null,
    touchTargetSize: Dp? = null,
    rasterArtworkScaleX: Float = 1f,
    rasterArtworkScaleY: Float = 1f,
    rasterArtworkOffsetX: Dp = 0.dp,
    rasterArtworkOffsetY: Dp = 0.dp,
) {
    val haptic = rememberYuJianHaptic()
    val effectiveMotionEnabled = motionEnabled && !rememberYuJianReduceMotion()
    val (rimProgress, breathingScale) = if (effectiveMotionEnabled && rasterMotion == null) {
        val transition = rememberYuJianInfiniteTransition(label = "YuJianCaptureButton")
        val localRimProgress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = YuJianMotion.captureRimSweepSpec(),
            label = "GoldRimSweep",
        )
        val localBreathingScale by transition.animateFloat(
            initialValue = 1f,
            targetValue = YuJianMotion.CaptureBreathingMaxScale,
            animationSpec = YuJianMotion.captureBreathingSpec(),
            label = "CaptureBreathing",
        )
        localRimProgress to localBreathingScale
    } else {
        0f to 1f
    }
    val sweepAlpha = sin(rimProgress * PI.toFloat()).coerceAtLeast(0f)
    val visualScale = if (effectiveMotionEnabled) rasterMotion?.breathScale ?: breathingScale else 1f
    val visibleSweepAlpha = if (effectiveMotionEnabled) rasterMotion?.sweepAlpha ?: sweepAlpha else 0f
    val resolvedVisualSize = visualSize
        ?: if (rasterAssets != null) RasterCaptureVisualSize else CaptureVisualSize
    val resolvedTouchTarget = touchTargetSize ?: YuJianSpacing.captureTouchTarget

    Box(
        modifier = modifier
            .size(resolvedTouchTarget)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .clickable(enabled = enabled) {
                haptic.perform(YuJianHaptic.Feedback.LightImpact)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(resolvedVisualSize)
                .graphicsLayer {
                    scaleX = visualScale
                    scaleY = visualScale
                    alpha = if (enabled) 1f else 0.48f
                }
                .shadow(1.dp, CircleShape, clip = false),
            contentAlignment = Alignment.Center,
        ) {
            if (rasterAssets != null) {
                val glowAlpha = if (effectiveMotionEnabled) rasterMotion?.breathGlowAlpha ?: 0f else 0f
                Image(
                    bitmap = rasterAssets.breathGlow,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = rasterArtworkScaleX
                        scaleY = rasterArtworkScaleY
                        translationX = rasterArtworkOffsetX.toPx()
                        translationY = rasterArtworkOffsetY.toPx()
                        alpha = glowAlpha
                    },
                )
                Image(
                    bitmap = rasterAssets.base,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = rasterArtworkScaleX
                        scaleY = rasterArtworkScaleY
                        translationX = rasterArtworkOffsetX.toPx()
                        translationY = rasterArtworkOffsetY.toPx()
                    },
                )
                if (visibleSweepAlpha > 0f) {
                    Image(
                        bitmap = rasterAssets.goldRimSweep,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = rasterArtworkScaleX
                            scaleY = rasterArtworkScaleY
                            translationX = rasterArtworkOffsetX.toPx()
                            translationY = rasterArtworkOffsetY.toPx()
                            alpha = visibleSweepAlpha
                            rotationZ = rasterMotion?.sweepRotationDegrees ?: (-110f + rimProgress * 360f)
                        },
                    )
                }
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 1.dp.toPx()
                    drawCircle(color = YuJianColors.LakeWhite)
                    drawCircle(
                        color = YuJianColors.MorningGold.copy(alpha = 0.76f),
                        style = Stroke(width = stroke),
                    )
                    if (visibleSweepAlpha > 0f) {
                        drawArc(
                            color = YuJianColors.MorningGold.copy(alpha = 0.82f * visibleSweepAlpha),
                            startAngle = -110f + rimProgress * 360f,
                            sweepAngle = 56f,
                            useCenter = false,
                            style = Stroke(width = stroke * 1.35f, cap = StrokeCap.Round),
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = null,
                    tint = YuJianColors.DeepInk,
                    modifier = Modifier.size(CaptureIconSize),
                )
            }
        }
    }
}
