package com.yujian.ai.ui.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.radius.YuJianRadius
import com.yujian.ai.ui.designsystem.spacing.YuJianSpacing
import com.yujian.ai.ui.designsystem.typography.YuJianTypography
import com.yujian.ai.ui.designsystem.motion.rememberYuJianReduceMotion

enum class YuJianActionButtonVariant {
    PRIMARY,
    SECONDARY_STRONG,
    SECONDARY_MUTED,
    /** Result-page save action: light surface with a restrained Morning Gold edge. */
    RESULT_SAVE,
    /** Result-page enabled secondary action with enough opacity for photo-backed screens. */
    RESULT_CONTINUE,
    /** Compatibility tone used by the existing component gallery. */
    BRAND_GOLD,
}

/** Compatibility for the original shared button call sites. */
enum class YuJianPrimaryButtonTone {
    Lake,
    Gold,
}

/**
 * Extended existing YuJianPrimaryButton with the frozen Action Button V1.1
 * variants and runtime states. Auth and other page actions share this one
 * implementation.
 */
@Composable
fun YuJianPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    variant: YuJianActionButtonVariant = YuJianActionButtonVariant.PRIMARY,
    leadingIcon: (@Composable () -> Unit)? = null,
    tone: YuJianPrimaryButtonTone? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = YuJianSpacing.md),
    leadingIconSpacing: Dp = YuJianSpacing.xs,
) {
    val resolvedVariant = tone?.let {
        when (it) {
            YuJianPrimaryButtonTone.Lake -> YuJianActionButtonVariant.PRIMARY
            YuJianPrimaryButtonTone.Gold -> YuJianActionButtonVariant.BRAND_GOLD
        }
    } ?: variant
    val reduceMotion = rememberYuJianReduceMotion()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.985f else 1f,
        animationSpec = tween(durationMillis = if (pressed) 90 else 120),
        label = "YuJianPrimaryButtonScale",
    )

    val primary = YuJianColors.ActionPrimary
    val content = when (resolvedVariant) {
        YuJianActionButtonVariant.PRIMARY -> Color.White
        YuJianActionButtonVariant.SECONDARY_STRONG -> YuJianColors.DeepLakeBlue
        YuJianActionButtonVariant.SECONDARY_MUTED -> YuJianColors.MistBlueGray
        YuJianActionButtonVariant.RESULT_SAVE -> YuJianColors.DeepLakeBlue
        YuJianActionButtonVariant.RESULT_CONTINUE -> YuJianColors.DeepLakeBlue
        YuJianActionButtonVariant.BRAND_GOLD -> YuJianColors.DeepLakeBlue
    }
    val container = when (resolvedVariant) {
        YuJianActionButtonVariant.PRIMARY ->
            if (pressed) YuJianColors.ActionPrimaryPressed else primary
        YuJianActionButtonVariant.SECONDARY_STRONG ->
            if (pressed) Color(0xFFEAF3F1) else Color(0xE0F7FAFB)
        YuJianActionButtonVariant.SECONDARY_MUTED ->
            if (pressed) Color(0xE6F7FAFB) else Color(0x73F7FAFB)
        YuJianActionButtonVariant.RESULT_SAVE ->
            if (pressed) YuJianColors.LakeWhite else Color(0xF7FAFB)
        YuJianActionButtonVariant.RESULT_CONTINUE ->
            if (pressed) Color(0xFFEAF3F1) else Color(0xF2F7FAFB)
        YuJianActionButtonVariant.BRAND_GOLD ->
            if (pressed) Color(0xFFE5C77C) else YuJianColors.MorningGold
    }
    val disabledContainer = when (resolvedVariant) {
        YuJianActionButtonVariant.PRIMARY -> YuJianColors.PrimaryActionDisabledSurface
        YuJianActionButtonVariant.SECONDARY_STRONG,
        YuJianActionButtonVariant.SECONDARY_MUTED -> YuJianColors.ActionDisabledSurface
        YuJianActionButtonVariant.RESULT_SAVE -> YuJianColors.ActionDisabledSurface
        YuJianActionButtonVariant.RESULT_CONTINUE -> YuJianColors.ActionDisabledSurface
        YuJianActionButtonVariant.BRAND_GOLD -> YuJianColors.MorningGold.copy(alpha = 0.48f)
    }
    val disabledContent = when (resolvedVariant) {
        YuJianActionButtonVariant.PRIMARY -> YuJianColors.PrimaryActionDisabledContent
        YuJianActionButtonVariant.SECONDARY_STRONG,
        YuJianActionButtonVariant.SECONDARY_MUTED -> YuJianColors.ActionDisabledContent
        YuJianActionButtonVariant.RESULT_SAVE -> YuJianColors.ActionDisabledContent
        YuJianActionButtonVariant.RESULT_CONTINUE -> YuJianColors.ActionDisabledContent
        YuJianActionButtonVariant.BRAND_GOLD -> YuJianColors.DeepLakeBlue.copy(alpha = 0.56f)
    }
    val labelColor = if (!enabled && !loading) disabledContent else content
    val borderColor = when {
        !enabled && !loading -> YuJianColors.ActionDisabledBorder
        resolvedVariant == YuJianActionButtonVariant.PRIMARY -> Color.White.copy(alpha = 0.10f)
        resolvedVariant == YuJianActionButtonVariant.SECONDARY_STRONG -> primary.copy(alpha = if (pressed) 0.36f else 0.26f)
        resolvedVariant == YuJianActionButtonVariant.SECONDARY_MUTED -> YuJianColors.MistBlueGray.copy(alpha = 0.18f)
        resolvedVariant == YuJianActionButtonVariant.RESULT_SAVE -> YuJianColors.MorningGold
        resolvedVariant == YuJianActionButtonVariant.RESULT_CONTINUE -> primary.copy(alpha = if (pressed) 0.36f else 0.20f)
        else -> YuJianColors.DeepLakeBlue.copy(alpha = 0.10f)
    }
    val shape = YuJianRadius.button
    val shadow = when (resolvedVariant) {
        YuJianActionButtonVariant.PRIMARY -> if (pressed) 1.4.dp else 2.dp
        YuJianActionButtonVariant.SECONDARY_STRONG,
        YuJianActionButtonVariant.BRAND_GOLD -> if (pressed) 0.7.dp else 1.dp
        YuJianActionButtonVariant.SECONDARY_MUTED,
        YuJianActionButtonVariant.RESULT_SAVE,
        YuJianActionButtonVariant.RESULT_CONTINUE -> if (pressed) 0.7.dp else 1.dp
    }
    val interactive = enabled && !loading

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (focused) Modifier.border(2.dp, YuJianColors.ActionPrimary, shape).padding(4.dp) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(YuJianSpacing.actionButtonHeight)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(shadow, shape = shape, clip = false)
                .onFocusChanged { focused = it.isFocused }
                .semantics {
                    if (loading) stateDescription = "正在加载"
                },
            enabled = interactive,
            interactionSource = interactionSource,
            shape = shape,
            border = BorderStroke(1.dp, borderColor),
            contentPadding = contentPadding,
            colors = ButtonDefaults.buttonColors(
                containerColor = container,
                contentColor = content,
                disabledContainerColor = if (loading) container else disabledContainer,
                disabledContentColor = if (loading) content else disabledContent,
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Row(
                    modifier = Modifier.graphicsLayer { alpha = if (loading) 0f else 1f },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) {
                        leadingIcon()
                        Spacer(Modifier.width(leadingIconSpacing))
                    }
                    Text(
                        text = text,
                        style = YuJianTypography.buttonText.copy(
                            color = labelColor,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        maxLines = 1,
                    )
                }
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = if (resolvedVariant == YuJianActionButtonVariant.PRIMARY) Color.White else primary,
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}
