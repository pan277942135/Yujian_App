package com.yujian.ai.ui.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.radius.YuJianRadius
import com.yujian.ai.ui.designsystem.spacing.YuJianSpacing
import com.yujian.ai.ui.designsystem.typography.YuJianTypography

enum class YuJianPrimaryButtonTone {
    Lake,
    Gold,
}

@Composable
fun YuJianPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: YuJianPrimaryButtonTone = YuJianPrimaryButtonTone.Lake,
) {
    val container = when (tone) {
        YuJianPrimaryButtonTone.Lake -> YuJianColors.LakeBlue
        YuJianPrimaryButtonTone.Gold -> YuJianColors.MorningGold
    }
    val content = when (tone) {
        YuJianPrimaryButtonTone.Lake -> YuJianColors.OnDark
        YuJianPrimaryButtonTone.Gold -> YuJianColors.DeepInk
    }
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(YuJianSpacing.xxl),
        enabled = enabled,
        shape = YuJianRadius.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.48f),
            disabledContentColor = content.copy(alpha = 0.56f),
        ),
    ) {
        Text(text = text, style = YuJianTypography.buttonText.copy(color = content))
    }
}
