package com.yujian.ai.ui.recorddetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.catches.CatchMemoryMedia
import com.yujian.ai.ui.components.RemoteImage
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.components.YuJianGlassCard
import com.yujian.ai.ui.designsystem.glass.YuJianGlassLevel
import com.yujian.ai.ui.designsystem.radius.YuJianRadius
import com.yujian.ai.ui.designsystem.spacing.YuJianSpacing
import com.yujian.ai.ui.designsystem.typography.YuJianTypography

@Composable
fun FishMediaPicker(
    media: List<CatchMemoryMedia> = emptyList(),
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
            if (media.isEmpty()) {
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
            } else {
                val photos = media.count { !it.isVideo }
                val videos = media.size - photos
                Text(
                    buildList {
                        if (photos > 0) add("照片 $photos")
                        if (videos > 0) add("视频 $videos")
                    }.joinToString(" · "),
                    style = YuJianTypography.caption,
                    color = YuJianColors.MistBlueGray,
                    modifier = Modifier.padding(top = YuJianSpacing.xs, bottom = YuJianSpacing.sm),
                )
                media.chunked(2).forEachIndexed { _, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = YuJianSpacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(YuJianSpacing.xs),
                    ) {
                        row.forEach { item ->
                            Box(Modifier.weight(1f).aspectRatio(1.52f).clip(RoundedCornerShape(14.dp))) {
                                MemoryMediaTile(item)
                                if (item.isVideo) {
                                    Icon(
                                        Icons.Rounded.Videocam,
                                        contentDescription = "视频",
                                        tint = Color.White,
                                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                                    )
                                }
                            }
                        }
                        if (row.size == 1 && media.size > 1) Box(Modifier.weight(1f).aspectRatio(1.52f))
                    }
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
private fun MemoryMediaTile(media: CatchMemoryMedia) {
    var videoFrame by remember(media.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(media.id, media.isVideo) {
        if (media.isVideo) {
            videoFrame = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(media.filePath)
                        retriever.getFrameAtTime(0L)
                    } finally {
                        retriever.release()
                    }
                }.getOrNull()
            }
        }
    }
    when {
        media.isVideo && videoFrame != null -> Image(
            bitmap = videoFrame!!.asImageBitmap(),
            contentDescription = "鱼获视频缩略图",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        media.isVideo -> Box(Modifier.fillMaxSize().background(YuJianColors.DeepLakeBlue.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Videocam, contentDescription = "视频", tint = YuJianColors.DeepLakeBlue, modifier = Modifier.size(32.dp))
        }
        else -> RemoteImage(
            url = "file://${media.filePath}",
            modifier = Modifier.fillMaxSize(),
            contentDescription = "鱼获照片",
            contentScale = ContentScale.Crop,
        )
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
