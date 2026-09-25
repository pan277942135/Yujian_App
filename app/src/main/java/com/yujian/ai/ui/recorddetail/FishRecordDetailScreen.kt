package com.yujian.ai.ui.recorddetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.components.YuJianGlassCard
import com.yujian.ai.ui.designsystem.components.YuJianTopBar
import com.yujian.ai.ui.designsystem.spacing.YuJianSpacing
import com.yujian.ai.ui.designsystem.typography.YuJianTypography

@Composable
fun FishRecordDetailScreen(
    uiState: FishRecordDetailUiState,
    imageUrlFor: (RemoteCatch) -> String?,
    bsideUrlFor: (RemoteCatch) -> String?,
    accessToken: String,
    onBack: () -> Unit,
    onOpenFishGuide: (RemoteCatch) -> Unit,
    onShare: (RemoteCatch) -> Unit,
    onEditRecord: (RemoteCatch) -> Unit,
    onAddMedia: (RemoteCatch) -> Unit,
    onGenerateMemory: ((RemoteCatch) -> Unit)?,
) {
    when (uiState) {
        FishRecordDetailUiState.Loading -> DetailMessage("正在打开这条鱼获…", onBack = onBack)
        FishRecordDetailUiState.Empty -> DetailMessage("没有找到这条鱼获记录", onBack = onBack)
        is FishRecordDetailUiState.Error -> DetailMessage(uiState.message, onBack = onBack, retryLabel = null)
        is FishRecordDetailUiState.Success -> {
            val record = uiState.record
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = YuJianSpacing.sm,
                    end = YuJianSpacing.sm,
                    top = YuJianSpacing.xs,
                    bottom = YuJianSpacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(YuJianSpacing.sm),
            ) {
                item {
                    YuJianTopBar(
                        title = "鱼获详情",
                        onBack = onBack,
                        actions = {
                            IconButton(onClick = { onOpenFishGuide(record) }) {
                                Icon(Icons.Rounded.MenuBook, contentDescription = "鱼鉴")
                            }
                            IconButton(onClick = { onShare(record) }) {
                                Icon(Icons.Rounded.Share, contentDescription = "分享")
                            }
                        },
                    )
                }
                item {
                    AnimatedVisibility(visible = true, enter = fadeIn(animationSpec = tween(260))) {
                        FishRecordHeroCard(
                            record = record,
                            imageUrl = imageUrlFor(record),
                            accessToken = accessToken,
                            onEdit = { onEditRecord(record) },
                        )
                    }
                }
                item { AboutCatchSection(record) }
                item {
                    Text(
                        "鱼获记忆",
                        style = YuJianTypography.sectionTitle,
                        modifier = Modifier.padding(top = YuJianSpacing.xs),
                    )
                }
                item {
                    FishMemorySection(
                        record = record,
                        bsideUrl = bsideUrlFor(record),
                        accessToken = accessToken,
                        onGenerateMemory = onGenerateMemory?.let { callback -> { callback(record) } },
                    )
                }
                item {
                    FishMediaPicker(
                        mediaUrls = FishRecordDetailPresentation.mediaUrls(record)
                            .mapNotNull { imageUrlFor(record) },
                        accessToken = accessToken,
                        onAddMedia = { onAddMedia(record) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutCatchSection(record: RemoteCatch) {
    YuJianGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(YuJianSpacing.xs)) {
            Text("关于这次鱼获", style = YuJianTypography.sectionTitle)
            DetailField("鱼种", record.speciesName)
            FishRecordDetailPresentation.measurement(record)?.let { DetailField("尺寸", it) }
            FishRecordDetailPresentation.location(record)?.let { DetailField("地点", it) }
            FishRecordDetailPresentation.capturedAt(record)?.let {
                Text("记录于 $it", style = YuJianTypography.caption, modifier = Modifier.padding(top = YuJianSpacing.xs))
            }
        }
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = YuJianTypography.caption)
        Text(value, style = YuJianTypography.body, color = YuJianColors.DeepInk)
    }
}

@Composable
private fun DetailMessage(message: String, onBack: () -> Unit, retryLabel: String? = "返回") {
    Column(
        modifier = Modifier.fillMaxSize().padding(YuJianSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = YuJianTypography.body)
        if (retryLabel != null) {
            Button(onClick = onBack, modifier = Modifier.padding(top = YuJianSpacing.sm)) {
                Text(retryLabel)
            }
        }
    }
}
