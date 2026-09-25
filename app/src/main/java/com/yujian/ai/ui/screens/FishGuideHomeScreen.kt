package com.yujian.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.knowledge.FishGuideItem
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.components.YuJianGlassCard
import com.yujian.ai.ui.designsystem.glass.YuJianGlassLevel
import com.yujian.ai.ui.designsystem.spacing.YuJianSpacing
import com.yujian.ai.ui.designsystem.typography.YuJianTypography
import com.yujian.ai.ui.fishguide.FishGuideBackground
import com.yujian.ai.ui.fishguide.FishGuideCarousel
import com.yujian.ai.ui.fishguide.FishGuideProgress
import com.yujian.ai.ui.fishguide.filterFishGuide
import com.yujian.ai.ui.fishguide.litCount
import com.yujian.ai.ui.fishguide.progressFraction
import com.yujian.ai.ui.fishguide.selectionIdAfterFilter
import com.yujian.ai.ui.fishguide.toFishGuidePresentation

@Composable
fun FishGuideHomeScreen(
    species: List<FishGuideItem>,
    loading: Boolean,
    offlinePreview: Boolean,
    error: String?,
    resolveAssetUrl: (String?) -> String?,
    onRetry: () -> Unit,
    onSpeciesClick: (FishGuideItem) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val visibleSpecies = remember(query, species) { species.filterFishGuide(query) }
    val selectedAfterFilter = selectionIdAfterFilter(visibleSpecies, selectedId)
    LaunchedEffect(visibleSpecies.map { it.id }) {
        selectedId = selectionIdAfterFilter(visibleSpecies, selectedId)
    }

    FishGuideBackground {
        if (loading && species.isEmpty()) {
            FishGuideLoadingState()
            return@FishGuideBackground
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = YuJianSpacing.sm, vertical = YuJianSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(YuJianSpacing.sm),
        ) {
            FishGuideHeader(query = query, onQueryChanged = { query = it })

            if (offlinePreview) {
                Text(
                    text = "离线预览中，仍显示当前鱼种档案",
                    style = YuJianTypography.caption.copy(color = YuJianColors.TextSecondary.copy(alpha = 0.82f)),
                    modifier = Modifier.padding(horizontal = YuJianSpacing.xs),
                )
            } else if (error != null && species.isNotEmpty()) {
                Text(
                    text = "鱼种档案暂时未更新",
                    style = YuJianTypography.caption.copy(color = YuJianColors.TextSecondary.copy(alpha = 0.82f)),
                    modifier = Modifier.padding(horizontal = YuJianSpacing.xs),
                )
            }

            FishGuideProgressHeader(species)

            if (visibleSpecies.isEmpty()) {
                FishGuideEmptyState(error = error, hasSpecies = species.isNotEmpty(), onRetry = onRetry)
            } else {
                FishGuideCarousel(
                    items = visibleSpecies.toFishGuidePresentation(resolveAssetUrl),
                    selectedId = selectedAfterFilter,
                    onSelectionChanged = { selectedId = it },
                    onSpeciesClick = { item -> onSpeciesClick(item.source) },
                    modifier = Modifier.height(472.dp),
                )
                Text(
                    text = "${visibleSpecies.indexOfFirst { it.id == selectedAfterFilter }.coerceAtLeast(0) + 1} / ${visibleSpecies.size}",
                    style = YuJianTypography.dataNumber.copy(color = YuJianColors.TextPrimary.copy(alpha = 0.84f)),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FishGuideHeader(query: String, onQueryChanged: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(YuJianSpacing.sm)) {
        Text(text = "鱼鉴", style = YuJianTypography.pageTitle)
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "搜索鱼种", tint = YuJianColors.DeepLakeBlue) },
            placeholder = { Text("搜索鱼种", style = YuJianTypography.body.copy(color = YuJianColors.TextSecondary)) },
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = YuJianColors.GlassWhite.copy(alpha = 0.72f),
                unfocusedContainerColor = YuJianColors.GlassWhite.copy(alpha = 0.58f),
                focusedBorderColor = YuJianColors.DeepLakeBlue.copy(alpha = 0.48f),
                unfocusedBorderColor = YuJianColors.GlassBorder.copy(alpha = 0.42f),
                cursorColor = YuJianColors.DeepLakeBlue,
            ),
        )
    }
}

@Composable
private fun FishGuideProgressHeader(species: List<FishGuideItem>) {
    YuJianGlassCard(
        modifier = Modifier.fillMaxWidth(),
        level = YuJianGlassLevel.Light,
        contentPadding = PaddingValues(horizontal = YuJianSpacing.md, vertical = YuJianSpacing.sm),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(YuJianSpacing.xs)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("已点亮 ", style = YuJianTypography.body)
                Text(species.litCount().toString(), style = YuJianTypography.dataNumber)
                Text(" / ${species.size} 种", style = YuJianTypography.caption.copy(fontSize = 14.sp))
            }
            FishGuideProgress(species.progressFraction())
        }
    }
}

@Composable
private fun FishGuideLoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("正在加载鱼鉴…", style = YuJianTypography.caption)
    }
}

@Composable
private fun FishGuideEmptyState(error: String?, hasSpecies: Boolean, onRetry: () -> Unit) {
    YuJianGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = YuJianSpacing.md),
        level = YuJianGlassLevel.Light,
        contentPadding = PaddingValues(YuJianSpacing.lg),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(YuJianSpacing.xs),
        ) {
            Text(
                text = if (hasSpecies) "没有匹配的鱼种" else "暂时还没有鱼种档案",
                style = YuJianTypography.sectionTitle,
                textAlign = TextAlign.Center,
            )
            Text(
                text = error ?: "连接后会继续读取鱼种资料。",
                style = YuJianTypography.caption,
                textAlign = TextAlign.Center,
            )
            if (!hasSpecies && error != null) {
                Button(onClick = onRetry) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Text("重试", modifier = Modifier.padding(start = YuJianSpacing.xs))
                }
            }
        }
    }
}
