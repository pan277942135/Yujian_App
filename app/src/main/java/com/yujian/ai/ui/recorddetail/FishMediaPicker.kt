package com.yujian.ai.ui.recorddetail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.yujian.ai.ui.components.RemoteImage
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.components.YuJianGlassCard
import com.yujian.ai.ui.designsystem.glass.YuJianGlassLevel
import com.yujian.ai.ui.designsystem.radius.YuJianRadius
import com.yujian.ai.ui.designsystem.spacing.YuJianSpacing
import com.yujian.ai.ui.designsystem.typography.YuJianTypography

@Composable
fun FishMediaPicker(
    mediaUrls: List<String>,
    accessToken: String,
    onAddMedia: () -> Unit,
) {
    YuJianGlassCard(
        modifier = Modifier.fillMaxWidth(),
        level = YuJianGlassLevel.Light,
        contentPadding = PaddingValues(YuJianSpacing.md),
    ) {
        Column {
            Text("媒体补充", style = YuJianTypography.sectionTitle)
            if (mediaUrls.isEmpty()) {
                Text(
                    "还没有留下照片和视频",
                    style = YuJianTypography.body,
                    modifier = Modifier.padding(top = YuJianSpacing.xs),
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = YuJianSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(YuJianSpacing.xs),
                ) {
                    mediaUrls.forEachIndexed { index, url ->
                        RemoteImage(
                            url = url,
                            authToken = accessToken,
                            modifier = Modifier.size(112.dp).clip(YuJianRadius.button),
                            contentDescription = "鱼获媒体 ${index + 1}",
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = onAddMedia,
                modifier = Modifier.padding(top = YuJianSpacing.sm),
                shape = YuJianRadius.pill,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = YuJianColors.MorningGold)
                Text("添加照片或视频", modifier = Modifier.padding(start = YuJianSpacing.xs))
            }
        }
    }
}
