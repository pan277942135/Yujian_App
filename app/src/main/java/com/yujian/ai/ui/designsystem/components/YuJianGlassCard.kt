package com.yujian.ai.ui.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yujian.ai.ui.designsystem.glass.MistGlass
import com.yujian.ai.ui.designsystem.glass.YuJianGlassLevel
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.haptic.rememberYuJianHaptic
import com.yujian.ai.ui.designsystem.motion.YuJianMotion
import com.yujian.ai.ui.designsystem.motion.rememberYuJianReduceMotion
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
    var focused by remember { mutableStateOf(false) }
    val reduceMotion = rememberYuJianReduceMotion()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled && onClick != null && !reduceMotion) 0.985f else 1f,
        animationSpec = YuJianMotion.glassInteractionSpec(),
        label = "YuJianGlassCardPress",
    )
    val haptic = rememberYuJianHaptic()
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = Role.Button,
        ) {
            if (enabled) {
                haptic.performCardClick()
                onClick()
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(if (focused && onClick != null) Modifier.border(2.dp, YuJianColors.ActionPrimary, shape).padding(4.dp) else Modifier),
    ) {
        MistGlass(
            level = level,
            shape = shape,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .then(clickModifier)
                .onFocusChanged { focused = it.isFocused },
        ) {
            Box(Modifier.padding(contentPadding), content = content)
        }
    }
}
