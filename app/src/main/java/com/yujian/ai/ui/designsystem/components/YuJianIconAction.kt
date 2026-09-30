package com.yujian.ai.ui.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.spacing.YuJianSpacing

enum class YuJianIconActionFamily {
    NAVIGATION,
    UTILITY,
    CONTEXT,
}

enum class YuJianIconActionTone {
    ON_LIGHT,
    ON_MEDIA,
}

/** Frozen Icon Action V1 runtime mapping. */
@Composable
fun YuJianIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    family: YuJianIconActionFamily = YuJianIconActionFamily.UTILITY,
    tone: YuJianIconActionTone = YuJianIconActionTone.ON_LIGHT,
    enabled: Boolean = true,
    supportDisc: Boolean = false,
) {
    require(contentDescription.isNotBlank()) {
        "Icon actions need a non-empty content description."
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }

    val normal = when (tone) {
        YuJianIconActionTone.ON_LIGHT -> YuJianColors.DeepLakeBlue.copy(alpha = 0.88f)
        YuJianIconActionTone.ON_MEDIA -> YuJianColors.LakeWhite.copy(alpha = 0.94f)
    }
    val pressedColor = when (tone) {
        YuJianIconActionTone.ON_LIGHT -> YuJianColors.ActionPrimary
        YuJianIconActionTone.ON_MEDIA -> YuJianColors.LakeWhite.copy(alpha = 0.78f)
    }
    val disabledColor = when (tone) {
        YuJianIconActionTone.ON_LIGHT -> YuJianColors.MistBlueGray.copy(alpha = 0.38f)
        YuJianIconActionTone.ON_MEDIA -> YuJianColors.LakeWhite.copy(alpha = 0.38f)
    }
    val target = when {
        !enabled -> disabledColor
        pressed -> pressedColor
        else -> normal
    }
    val tint by animateColorAsState(
        targetValue = target,
        animationSpec = tween(90),
        label = "YuJianIconActionTone",
    )
    val glyphSize = when (family) {
        YuJianIconActionFamily.NAVIGATION -> 22.dp
        YuJianIconActionFamily.UTILITY -> 21.dp
        YuJianIconActionFamily.CONTEXT -> 19.dp
    }
    val focusShape = RoundedCornerShape(12.dp)
    val focusModifier = if (focused) {
        Modifier.border(2.dp, YuJianColors.ActiveAccent, focusShape)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(YuJianSpacing.xxl)
            .then(focusModifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .focusable(enabled = enabled),
        contentAlignment = Alignment.Center,
    ) {
        if (supportDisc && tone == YuJianIconActionTone.ON_MEDIA) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(Color(0x29081926), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(glyphSize),
        )
    }
}

@Composable
fun YuJianBackAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: YuJianIconActionTone = YuJianIconActionTone.ON_LIGHT,
) {
    YuJianIconAction(
        icon = Icons.Rounded.ArrowBack,
        contentDescription = "返回",
        onClick = onClick,
        modifier = modifier,
        family = YuJianIconActionFamily.NAVIGATION,
        tone = tone,
        enabled = enabled,
    )
}
