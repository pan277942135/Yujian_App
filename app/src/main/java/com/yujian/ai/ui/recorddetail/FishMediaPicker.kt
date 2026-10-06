package com.yujian.ai.ui.recorddetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.components.YuJianGlassCard
import com.yujian.ai.ui.designsystem.glass.YuJianGlassLevel
import com.yujian.ai.ui.designsystem.radius.YuJianRadius
import com.yujian.ai.ui.designsystem.spacing.YuJianSpacing
import com.yujian.ai.ui.designsystem.typography.YuJianTypography

@Composable
fun FishMediaPicker(
    onAddPhotosOrVideos: () -> Unit,
    onContinuePhoto: () -> Unit,
    onRecordVideo: () -> Unit,
    fontScale: Float,
) {
    YuJianGlassCard(
        modifier = Modifier.fillMaxWidth(),
        level = YuJianGlassLevel.Light,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("鱼获记忆", style = YuJianTypography.sectionTitle)
            Text(
                "还没有留下影像",
                style = YuJianTypography.caption,
                color = YuJianColors.MistBlueGray,
                modifier = Modifier.padding(top = YuJianSpacing.xs),
            )
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(
                    min = if (fontScale >= 1.3f) 120.dp else 104.dp,
                    max = if (fontScale >= 1.3f) 168.dp else 136.dp,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Rounded.Image,
                        contentDescription = null,
                        tint = YuJianColors.MistBlueGray,
                        modifier = Modifier.size(48.dp),
                    )
                    Text("留下这次鱼获的画面", style = YuJianTypography.sectionTitle)
                    Text(
                        "照片和视频，会让这一刻更完整。",
                        style = YuJianTypography.caption.copy(fontSize = 14.sp, lineHeight = 20.sp),
                        color = YuJianColors.MistBlueGray,
                    )
                }
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth < 330.dp || fontScale >= 1.3f) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MemoryMediaAction(
                            label = "添加照片/视频",
                            icon = Icons.Rounded.Image,
                            onClick = onAddPhotosOrVideos,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        MemoryMediaAction(
                            label = "继续拍照",
                            icon = Icons.Rounded.CameraAlt,
                            onClick = onContinuePhoto,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        MemoryMediaAction(
                            label = "录制视频",
                            icon = Icons.Rounded.Videocam,
                            onClick = onRecordVideo,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MemoryMediaAction(
                            label = "添加照片/视频",
                            icon = Icons.Rounded.Image,
                            onClick = onAddPhotosOrVideos,
                            modifier = Modifier.weight(1.3f),
                        )
                        MemoryMediaAction(
                            label = "继续拍照",
                            icon = Icons.Rounded.CameraAlt,
                            onClick = onContinuePhoto,
                            modifier = Modifier.weight(1f),
                        )
                        MemoryMediaAction(
                            label = "录制视频",
                            icon = Icons.Rounded.Videocam,
                            onClick = onRecordVideo,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryMediaAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = YuJianRadius.button,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = YuJianColors.DeepLakeBlue, modifier = Modifier.size(18.dp))
        Text(
            label,
            modifier = Modifier.padding(start = 4.dp),
            style = YuJianTypography.caption,
            color = YuJianColors.DeepInk,
            maxLines = 1,
        )
    }
}
