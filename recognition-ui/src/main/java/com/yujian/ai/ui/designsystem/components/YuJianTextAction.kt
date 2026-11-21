package com.yujian.ai.ui.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.spacing.YuJianSpacing

enum class YuJianTextActionRole {
    STRONG,
    NORMAL,
    MUTED,
}

enum class YuJianTextActionTone {
    LIGHT,
    ON_MEDIA,
}

/** Frozen Text Action V1 runtime mapping. */
@Composable
fun YuJianTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: YuJianTextActionRole = YuJianTextActionRole.NORMAL,
    tone: YuJianTextActionTone = YuJianTextActionTone.LIGHT,
    enabled: Boolean = true,
    showChevron: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }

    val normal = when (tone) {
        YuJianTextActionTone.ON_MEDIA -> Color(0xEFF7FAFB)
        YuJianTextActionTone.LIGHT -> when (role) {
            YuJianTextActionRole.STRONG -> YuJianColors.ActiveAccent
            YuJianTextActionRole.NORMAL -> YuJianColors.ActionPrimary
            YuJianTextActionRole.MUTED -> YuJianColors.MistBlueGray
        }
    }
    val pressedColor = when (tone) {
        YuJianTextActionTone.ON_MEDIA -> Color(0xB8F7FAFB)
        YuJianTextActionTone.LIGHT -> when (role) {
            YuJianTextActionRole.STRONG -> YuJianColors.ActionPrimary
            YuJianTextActionRole.NORMAL -> YuJianColors.ActionPrimaryPressed
            YuJianTextActionRole.MUTED -> YuJianColors.MistBlueGray
        }
    }
    val disabledColor = when (tone) {
        YuJianTextActionTone.ON_MEDIA -> Color(0x61F7FAFB)
        YuJianTextActionTone.LIGHT -> Color(0xFFA0ADAF)
    }
    val target = when {
        !enabled -> disabledColor
        pressed -> pressedColor
        else -> normal
    }
    val color by animateColorAsState(
        targetValue = target,
        animationSpec = tween(90),
        label = "YuJianTextActionTone",
    )

    val fontSize = if (role == YuJianTextActionRole.NORMAL) 14.sp else 13.sp
    val fontWeight = when (role) {
        YuJianTextActionRole.STRONG -> FontWeight.SemiBold
        YuJianTextActionRole.NORMAL,
        YuJianTextActionRole.MUTED -> FontWeight.Medium
    }
    val focusShape = RoundedCornerShape(10.dp)
    val focusModifier = if (focused) {
        Modifier.border(2.dp, YuJianColors.ActiveAccent, focusShape)
    } else {
        Modifier
    }

    Box(
        modifier = modifier.then(focusModifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .then(if (focused) Modifier.padding(4.dp) else Modifier)
                .defaultMinSize(
                    minWidth = YuJianSpacing.minimumTouchTarget,
                    minHeight = YuJianSpacing.minimumTouchTarget,
                )
                .onFocusChanged { focused = it.isFocused }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .focusable(enabled = enabled)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = text,
                    maxLines = 1,
                    style = TextStyle(
                        color = color,
                        fontSize = fontSize,
                        lineHeight = 18.sp,
                        fontWeight = fontWeight,
                        shadow = if (tone == YuJianTextActionTone.ON_MEDIA) {
                            Shadow(
                                color = Color(0x3D0B2D4B),
                                offset = Offset(0f, 1f),
                                blurRadius = 3f,
                            )
                        } else {
                            null
                        },
                    ),
                )
                if (showChevron) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = if (tone == YuJianTextActionTone.ON_MEDIA) {
                            color.copy(alpha = 0.87f)
                        } else {
                            color
                        },
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}
