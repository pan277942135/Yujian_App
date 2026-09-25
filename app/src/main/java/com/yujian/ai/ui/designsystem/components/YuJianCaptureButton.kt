package com.yujian.ai.ui.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.haptic.YuJianHaptic
import com.yujian.ai.ui.designsystem.haptic.rememberYuJianHaptic
import com.yujian.ai.ui.designsystem.motion.YuJianMotion
import com.yujian.ai.ui.designsystem.motion.rememberYuJianInfiniteTransition
import com.yujian.ai.ui.designsystem.spacing.YuJianSpacing
import kotlin.math.PI
import kotlin.math.sin

private val CaptureVisualSize = 56.dp
private val CaptureIconSize = 24.dp

/**
 * Native Core UI V1 capture control. The touch target is 64 dp while the
 * visible white core remains 56 dp, matching the frozen component contract.
 */
@Composable
fun YuJianCaptureButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = "开始识鱼",
    motionEnabled: Boolean = true,
) {
    val haptic = rememberYuJianHaptic()
    val transition = rememberYuJianInfiniteTransition(label = "YuJianCaptureButton")
    val rimProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = YuJianMotion.captureRimSweepSpec(),
        label = "GoldRimSweep",
    )
    val breathingScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.015f,
        animationSpec = YuJianMotion.captureBreathingSpec(),
        label = "CaptureBreathing",
    )
    val sweepAlpha = sin(rimProgress * PI.toFloat()).coerceAtLeast(0f)
    val visualScale = if (motionEnabled) breathingScale else 1f
    val visibleSweepAlpha = if (motionEnabled) sweepAlpha else 0f

    Box(
        modifier = modifier
            .size(YuJianSpacing.minimumTouchTarget)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .clickable(enabled = enabled) {
                haptic.perform(YuJianHaptic.Feedback.Light)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(CaptureVisualSize)
                .graphicsLayer {
                    scaleX = visualScale
                    scaleY = visualScale
                    alpha = if (enabled) 1f else 0.48f
                }
                .shadow(5.dp, CircleShape, clip = false),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 1.5.dp.toPx()
                drawCircle(color = YuJianColors.LakeWhite)
                drawCircle(
                    color = YuJianColors.MorningGold.copy(alpha = 0.76f),
                    style = Stroke(width = stroke),
                )
                if (visibleSweepAlpha > 0f) {
                    drawArc(
                        color = YuJianColors.MorningGold.copy(alpha = visibleSweepAlpha),
                        startAngle = -110f + rimProgress * 360f,
                        sweepAngle = 56f,
                        useCenter = false,
                        style = Stroke(width = stroke * 2f, cap = StrokeCap.Round),
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
