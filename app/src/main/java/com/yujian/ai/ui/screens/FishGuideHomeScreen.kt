package com.yujian.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.knowledge.FishGuideItem
import com.yujian.ai.ui.components.FishIllustration
import com.yujian.ai.ui.components.RemoteImage
import com.yujian.ai.ui.components.TagChip
import com.yujian.ai.ui.theme.CardWhite
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.SoftWater
import com.yujian.ai.ui.theme.WarmBackground
import com.yujian.ai.ui.theme.WaterTeal

private enum class GuideFilter(val label: String) { ALL("全部"), DISCOVERED("已发现"), UNDISCOVERED("未发现") }

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
    var filter by remember { mutableStateOf(GuideFilter.ALL) }
    val visible = remember(query, filter, species) {
        species.filter { fish ->
            val normalizedQuery = query.trim()
            val matchesQuery = normalizedQuery.isBlank() ||
                fish.nameCn.contains(normalizedQuery, ignoreCase = true) ||
                fish.aliases.any { it.contains(normalizedQuery, ignoreCase = true) }
            val matchesFilter = when (filter) {
                GuideFilter.ALL -> true
                GuideFilter.DISCOVERED -> fish.discovered
                GuideFilter.UNDISCOVERED -> !fish.discovered
            }
            matchesQuery && matchesFilter
        }
    }

    if (loading && species.isEmpty()) {
        Box(Modifier.fillMaxSize().background(WarmBackground), contentAlignment = Alignment.Center) {
            Text("正在加载鱼鉴…", color = MutedInk, fontSize = 14.sp)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().background(WarmBackground),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 110.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Column {
                Text("鱼类图鉴", color = DeepInk, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text("把见过的鱼，一条条收藏起来", color = MutedInk, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }

        item(span = { GridItemSpan(2) }) {
            if (error != null) {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFFFFF3E8), RoundedCornerShape(16.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (offlinePreview) "离线预览：$error" else error, color = Color(0xFF9A5B16), fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Button(onClick = onRetry, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "重试", modifier = Modifier.size(16.dp))
                        Text("重试", modifier = Modifier.padding(start = 4.dp), fontSize = 12.sp)
                    }
                }
            } else if (offlinePreview) {
                Text("当前为离线预览数据，连接后将自动读取 Fish Knowledge Database。", color = MutedInk, fontSize = 11.sp)
            }
        }

        item(span = { GridItemSpan(2) }) {
            val discovered = species.count { it.discovered }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(28.dp), ambientColor = WaterTeal.copy(alpha = .08f), spotColor = WaterTeal.copy(alpha = .08f))
                    .background(SoftWater, RoundedCornerShape(28.dp))
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("我的图鉴", color = WaterTeal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
                            Text("$discovered", color = DeepInk, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                            Text(" / ${species.size}", color = MutedInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Text("已发现鱼种", color = MutedInk, fontSize = 12.sp)
                        Spacer(Modifier.height(14.dp))
                        Box(Modifier.fillMaxWidth().height(7.dp).background(Color.White.copy(alpha = .7f), RoundedCornerShape(50))) {
                            val progress = if (species.isEmpty()) 0f else discovered.toFloat() / species.size.toFloat()
                            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(7.dp).background(WaterTeal, RoundedCornerShape(50)))
                        }
                    }
                    FishIllustration(size = 104.dp, bodyColor = WaterTeal.copy(alpha = .78f))
                }
            }
        }

        item(span = { GridItemSpan(2) }) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MutedInk) },
                placeholder = { Text("搜索鱼名或别名", color = MutedInk) },
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CardWhite,
                    unfocusedContainerColor = CardWhite,
                    focusedBorderColor = WaterTeal,
                    unfocusedBorderColor = Color.Transparent,
                ),
            )
        }

        item(span = { GridItemSpan(2) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GuideFilter.entries.forEach { item ->
                    Box(modifier = Modifier.clickable { filter = item }) { TagChip(item.label, emphasized = filter == item) }
                }
            }
        }

        item(span = { GridItemSpan(2) }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("鱼种", color = DeepInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("${visible.size} 种", color = MutedInk, fontSize = 12.sp)
            }
        }

        items(visible, key = { it.id }) { fish ->
            SpeciesCard(fish = fish, imageUrl = resolveAssetUrl(fish.coverImage), onClick = { onSpeciesClick(fish) })
        }

        if (!loading && visible.isEmpty()) {
            item(span = { GridItemSpan(2) }) { Text("没有匹配的鱼种。", color = MutedInk, fontSize = 13.sp, modifier = Modifier.padding(vertical = 24.dp)) }
        }
    }
}

@Composable
private fun SpeciesCard(fish: FishGuideItem, imageUrl: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = .04f), spotColor = Color.Black.copy(alpha = .04f))
            .background(CardWhite, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(164.dp).clip(RoundedCornerShape(16.dp)).background(if (fish.discovered) SoftWater else Color(0xFFEEEFEA)),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null) {
                RemoteImage(imageUrl, Modifier.fillMaxSize(), contentDescription = fish.nameCn, contentScale = ContentScale.Crop)
            } else {
                FishIllustration(size = 112.dp, bodyColor = if (fish.discovered) WaterTeal.copy(alpha = .72f) else MutedInk.copy(alpha = .45f))
            }
            if (fish.discovered) {
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.White.copy(alpha = .88f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("已发现", color = WaterTeal, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(fish.nameCn, color = DeepInk, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 11.dp))
        TagChip(fish.category.ifBlank { "鱼鉴内容" }, emphasized = true)
        if (fish.catches > 0) Text("我的鱼获 ${fish.catches} 次", color = WaterTeal, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
        else Text("查看完整鱼鉴", color = MutedInk, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
    }
}
