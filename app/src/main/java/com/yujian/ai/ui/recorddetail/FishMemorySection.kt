package com.yujian.ai.ui.recorddetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yujian.ai.catches.BsideStatus
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.components.YuJianActionButtonVariant
import com.yujian.ai.ui.designsystem.components.YuJianGlassCard
import com.yujian.ai.ui.designsystem.components.YuJianPrimaryButton
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
        level = YuJianGlassLevel.Medium,
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("鱼获记忆", style = YuJianTypography.sectionTitle)
            when (record.bsideStatus) {
                BsideStatus.NONE -> {
                    Text("为这次相遇生成一份鱼获记忆", style = YuJianTypography.body)
                    if (generationEnabled) {
                        YuJianPrimaryButton(
                            text = "生成鱼获记忆",
                            onClick = { onGenerateMemory?.invoke() },
                            modifier = Modifier.fillMaxWidth(),
                            variant = YuJianActionButtonVariant.PRIMARY,
                        )
                    } else {
                        Text(
                            "登录后可以生成鱼获记忆。",
                            style = YuJianTypography.caption,
                            color = YuJianColors.MistBlueGray,
                        )
                    }
                }
                BsideStatus.GENERATING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("正在生成鱼获记忆…", style = YuJianTypography.body)
                            Text(
                                "可以继续浏览或离开，完成后会保留在这条鱼获里。",
                                style = YuJianTypography.caption,
                                color = YuJianColors.MistBlueGray,
                            )
                        }
                        CircularProgressIndicator(
                            modifier = Modifier.padding(start = 8.dp).size(24.dp),
                            color = YuJianColors.MorningGold,
                            strokeWidth = 2.dp,
                        )
                    }
                }
                BsideStatus.FAILED -> {
                    Text("鱼获记忆生成失败", style = YuJianTypography.body)
                    Text(
                        "原鱼获记录不受影响。",
                        style = YuJianTypography.caption,
                        color = YuJianColors.MistBlueGray,
                    )
                    if (generationEnabled) {
                        OutlinedButton(
                            onClick = { onGenerateMemory?.invoke() },
                            modifier = Modifier.fillMaxWidth().padding(top = YuJianSpacing.xs),
                        ) {
                            Text("重新生成")
                        }
                    } else {
                        Text(
                            "登录后可以生成鱼获记忆。",
                            style = YuJianTypography.caption,
                            color = YuJianColors.MistBlueGray,
                        )
                    }
                }
                BsideStatus.READY -> if (bsideUnavailable) {
                    Text("鱼获记忆暂时无法显示", style = YuJianTypography.body)
                    OutlinedButton(
                        onClick = onRetryBside,
                        modifier = Modifier.fillMaxWidth().padding(top = YuJianSpacing.xs),
                    ) {
                        Text("重新加载")
                    }
                }
            }
        }
    }
}
