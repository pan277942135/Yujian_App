package com.yujian.ai.ui.recorddetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.yujian.ai.catches.BsideStatus
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.components.RemoteImage
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.components.YuJianGlassCard
import com.yujian.ai.ui.designsystem.glass.YuJianGlassLevel
import com.yujian.ai.ui.designsystem.spacing.YuJianSpacing
import com.yujian.ai.ui.designsystem.typography.YuJianTypography

@Composable
fun FishMemorySection(
    record: RemoteCatch,
    bsideUrl: String?,
    accessToken: String,
    onGenerateMemory: (() -> Unit)?,
) {
    YuJianGlassCard(
        modifier = Modifier.fillMaxWidth(),
        level = YuJianGlassLevel.Light,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(YuJianSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(YuJianSpacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = YuJianColors.MorningGold)
                Text(
                    text = when (record.bsideStatus) {
                        BsideStatus.READY -> "这条鱼的记忆"
                        else -> "留下这条鱼的记忆"
                    },
                    style = YuJianTypography.sectionTitle,
                    modifier = Modifier.padding(start = YuJianSpacing.xs),
                )
            }
            when (record.bsideStatus) {
                BsideStatus.NONE -> MemoryAction(
                    label = "生成鱼获记忆",
                    enabled = onGenerateMemory != null,
                    onClick = { onGenerateMemory?.invoke() },
                )
                BsideStatus.GENERATING -> {
                    Text("正在生成鱼获记忆…", style = YuJianTypography.body)
                    Text("完成后会出现在这条记录里。", style = YuJianTypography.caption)
                }
                BsideStatus.FAILED -> {
                    Text("记忆生成失败", style = YuJianTypography.body)
                    MemoryAction(
                        label = "重新生成",
                        enabled = onGenerateMemory != null,
                        onClick = { onGenerateMemory?.invoke() },
                    )
                }
                BsideStatus.READY -> {
                    Text("一段关于这次相遇的视觉记忆。", style = YuJianTypography.body)
                    if (!bsideUrl.isNullOrBlank()) {
                        RemoteImage(
                            url = bsideUrl,
                            authToken = accessToken,
                            modifier = Modifier.fillMaxWidth().padding(top = YuJianSpacing.xs),
                            contentDescription = "${record.speciesName} 鱼获记忆",
                            contentScale = ContentScale.Crop,
                        )
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
