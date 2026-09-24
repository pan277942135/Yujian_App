package com.yujian.ai.ui.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import com.yujian.ai.ui.designsystem.glass.MistGlass
import com.yujian.ai.ui.designsystem.glass.YuJianGlassLevel
import com.yujian.ai.ui.designsystem.haptic.rememberYuJianHaptic
import com.yujian.ai.ui.designsystem.motion.YuJianMotion
import com.yujian.ai.ui.designsystem.radius.YuJianRadius
import com.yujian.ai.ui.designsystem.spacing.YuJianSpacing

/**
 * Shared interactive glass card for story, memory, statistics, and fish rows.
 * Clickable cards receive only a restrained press scale and system haptic.
 */
@Composable
fun YuJianGlassCard(
    modifier: Modifier = Modifier,
    level: YuJianGlassLevel = YuJianGlassLevel.Medium,
    shape: Shape = YuJianRadius.glassCard,
    contentPadding: PaddingValues = PaddingValues(YuJianSpacing.md),
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled && onClick != null) 0.985f else 1f,
        animationSpec = YuJianMotion.glassInteractionSpec(),
        label = "YuJianGlassCardPress",
    )
    val haptic = rememberYuJianHaptic()
    val clickModifier = if (onClick != null && enabled) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
        ) {
            haptic.performCardClick()
            onClick()
        }
    } else {
        Modifier
    }

    MistGlass(
        level = level,
        shape = shape,
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .then(clickModifier),
    ) {
        Box(Modifier.padding(contentPadding), content = content)
    }
}
