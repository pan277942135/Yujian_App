package com.yujian.ai.ui.recorddetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yujian.ai.catches.BsideStatus
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.designsystem.components.YuJianGlassCard
import com.yujian.ai.ui.designsystem.glass.YuJianGlassLevel
import com.yujian.ai.ui.designsystem.spacing.YuJianSpacing
import com.yujian.ai.ui.designsystem.typography.YuJianTypography

@Composable
fun FishMemorySection(
    record: RemoteCatch,
    generationEnabled: Boolean,
    bsideUnavailable: Boolean,
    onGenerateMemory: (() -> Unit)?,
    onRetryBside: () -> Unit,
) {
    if (record.bsideStatus == BsideStatus.READY && !bsideUnavailable) return

    YuJianGlassCard(
        modifier = Modifier.fillMaxWidth(),
        level = YuJianGlassLevel.Light,
        contentPadding = PaddingValues(YuJianSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(YuJianSpacing.xs)) {
            when (record.bsideStatus) {
                BsideStatus.NONE -> {
                    Text("为这次相遇生成一份鱼获记忆", style = YuJianTypography.body)
                    MemoryAction(
                        label = "生成鱼获记忆",
                        enabled = generationEnabled,
                        onClick = { onGenerateMemory?.invoke() },
                    )
                    if (!generationEnabled) {
                        Text("登录后可以生成鱼获记忆。", style = YuJianTypography.caption)
                    }
                }
                BsideStatus.GENERATING -> {
                    Text("正在生成鱼获记忆…", style = YuJianTypography.body)
                    Text(
                        "可以继续浏览或离开，完成后会保留在这条鱼获里。",
                        style = YuJianTypography.caption,
                    )
                }
                BsideStatus.FAILED -> {
                    Text("鱼获记忆生成失败", style = YuJianTypography.body)
                    Text("原鱼获记录不受影响。", style = YuJianTypography.caption)
                    MemoryAction(
                        label = "重新生成",
                        enabled = generationEnabled,
                        onClick = { onGenerateMemory?.invoke() },
                    )
                    if (!generationEnabled) {
                        Text("登录后可以生成鱼获记忆。", style = YuJianTypography.caption)
                    }
                }
                BsideStatus.READY -> if (bsideUnavailable) {
                    Text("鱼获记忆暂时无法显示", style = YuJianTypography.body)
                    OutlinedButton(
                        onClick = onRetryBside,
                        modifier = Modifier.padding(top = YuJianSpacing.xs),
                    ) {
                        Text("重新加载")
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.padding(top = YuJianSpacing.xs),
    ) {
        Text(label)
    }
}
