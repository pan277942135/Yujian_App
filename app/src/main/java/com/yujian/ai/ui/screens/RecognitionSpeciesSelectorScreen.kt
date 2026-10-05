package com.yujian.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import com.yujian.ai.knowledge.FishGuideItem
import com.yujian.ai.ui.components.FishIllustration
import com.yujian.ai.ui.components.RemoteImage
import com.yujian.ai.ui.designsystem.components.YuJianBackTitleTopBar
import com.yujian.ai.ui.designsystem.components.YuJianTextAction
import com.yujian.ai.ui.designsystem.components.YuJianTextActionRole
import com.yujian.ai.ui.recognition.result.RecognitionSpeciesSearch
import com.yujian.ai.ui.recognition.result.SpeciesSelectorEntryContext
import com.yujian.ai.ui.recognition.result.SpeciesSelectorRecentStore
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.SoftWater
import com.yujian.ai.ui.theme.WaterTeal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecognitionSpeciesSelectorScreen(
    species: List<FishGuideItem>,
    resolveCoverUrl: (String?) -> String?,
    selectedSpeciesId: String,
    entryContext: SpeciesSelectorEntryContext,
    onBack: () -> Unit,
    onSelect: (FishGuideItem) -> Unit,
    onUnconfirmed: () -> Unit,
) {
    val context = LocalContext.current
    val recentStore = remember(context) { SpeciesSelectorRecentStore(context) }
    var recentIds by remember(context) { mutableStateOf(recentStore.read()) }
    var query by remember { mutableStateOf("") }
    var settledQuery by remember { mutableStateOf("") }
    var committing by remember { mutableStateOf(false) }
    var choosingId by remember(selectedSpeciesId) { mutableStateOf(selectedSpeciesId) }
    val scope = rememberCoroutineScope()
    val widthDp = LocalConfiguration.current.screenWidthDp
    val commonColumns = if (widthDp <= 320) 2 else 3
    val speciesById = remember(species) { species.associateBy { it.id } }
    val recentSpecies = remember(recentIds, speciesById) { recentIds.mapNotNull(speciesById::get).take(3) }
    val commonSpecies = remember(species) {
        val preferred = listOf("grass_carp", "crucian_carp", "common_carp", "bighead_carp", "silver_carp", "largemouth_bass")
        val byId = species.associateBy { it.id }
        val preferredItems = preferred.mapNotNull(byId::get)
        (preferredItems + species.filterNot { item -> preferredItems.any { it.id == item.id } }).take(6)
    }

    fun choose(item: FishGuideItem) {
        if (committing) return
        committing = true
        choosingId = item.id
        scope.launch {
            delay(120L)
            recentIds = recentStore.commit(item.id)
            onSelect(item)
        }
    }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            settledQuery = ""
        } else {
            delay(300L)
            settledQuery = query
        }
    }

    val searching = query.isNotBlank() && query != settledQuery
    val results = remember(species, settledQuery) {
        if (settledQuery.isBlank()) emptyList() else RecognitionSpeciesSearch.search(species, settledQuery)
    }

    Box(Modifier.fillMaxSize()) {
        BgContentSurface()
        Column(Modifier.fillMaxSize()) {
            YuJianBackTitleTopBar(title = "选择鱼种", onBack = onBack)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                    .testTag("recognition-species-selector-search"),
                singleLine = true,
                placeholder = { Text("搜索鱼种", color = MutedInk) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MutedInk) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }, enabled = !committing) {
                            Icon(Icons.Rounded.Close, contentDescription = "清除搜索", tint = MutedInk)
                        }
                    }
                } else null,
                enabled = !committing,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xDDF7FAFB),
                    unfocusedContainerColor = Color(0xCFF7FAFB),
                    focusedBorderColor = WaterTeal,
                    unfocusedBorderColor = Color(0x4DFFFFFF),
                ),
            )

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (query.isBlank()) {
                    Text("最近选择", color = DeepInk, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
                    if (recentSpecies.isEmpty()) {
                        Text("暂无最近选择", color = MutedInk, fontSize = 14.sp, lineHeight = 20.sp)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            recentSpecies.forEach { item ->
                                RecentSpeciesChip(
                                    item = item,
                                    selected = item.id == choosingId,
                                    imageUrl = resolveCoverUrl(item.coverImage),
                                    enabled = !committing,
                                ) { choose(item) }
                            }
                        }
                    }

                    Text("常见鱼种", color = DeepInk, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
                    commonSpecies.chunked(commonColumns).forEach { rowSpecies ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowSpecies.forEach { item ->
                                CommonSpeciesCard(
                                    item = item,
                                    selected = item.id == choosingId,
                                    imageUrl = resolveCoverUrl(item.coverImage),
                                    enabled = !committing,
                                    modifier = Modifier.weight(1f),
                                ) { choose(item) }
                            }
                            repeat(commonColumns - rowSpecies.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }

                    Text("全部鱼种", color = DeepInk, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
                    val grouped = remember(species) {
                        species.sortedWith(compareBy<FishGuideItem>({ RecognitionSpeciesSearch.initial(it) }, { RecognitionSpeciesSearch.pinyin(it) }, { it.nameCn }))
                            .groupBy(RecognitionSpeciesSearch::initial)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        grouped.forEach { (initial, items) ->
                            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                Text(initial, color = MutedInk, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                                items.forEach { item ->
                                    SpeciesListRow(
                                        item = item,
                                        selected = item.id == choosingId,
                                        imageUrl = resolveCoverUrl(item.coverImage),
                                        enabled = !committing,
                                    ) { choose(item) }
                                }
                            }
                        }
                    }
                } else if (searching) {
                    Box(Modifier.fillMaxWidth().padding(top = 28.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = WaterTeal, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else if (results.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("没有找到相关鱼种", color = MutedInk, fontSize = 15.sp, lineHeight = 22.sp)
                        YuJianTextAction(
                            text = "清除搜索",
                            onClick = { query = "" },
                            role = YuJianTextActionRole.NORMAL,
                            enabled = !committing,
                        )
                    }
                } else {
                    results.forEach { item ->
                        SpeciesListRow(
                            item = item,
                            selected = item.id == choosingId,
                            imageUrl = resolveCoverUrl(item.coverImage),
                            enabled = !committing,
                            testTag = "recognition-species-result-${item.id}",
                        ) { choose(item) }
                    }
                }
            }

            if (entryContext != SpeciesSelectorEntryContext.EDIT_CONFIRMED) {
                Box(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    YuJianTextAction(
                        text = "暂不确认鱼种",
                        onClick = onUnconfirmed,
                        role = YuJianTextActionRole.MUTED,
                        enabled = !committing,
                    )
                }
            }
        }
    }

}

@Composable
private fun RecentSpeciesChip(item: FishGuideItem, selected: Boolean, imageUrl: String?, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(18.dp))
            .background(if (selected) SoftWater else Color(0xBFF7FAFB))
            .border(if (selected) 2.dp else 1.dp, if (selected) WaterTeal else Color(0x47FFFFFF), RoundedCornerShape(18.dp))
            .semantics(mergeDescendants = true) {
                this.selected = selected
                if (selected) stateDescription = "已选择"
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpeciesMedia(item = item, imageUrl = imageUrl, modifier = Modifier.size(width = 32.dp, height = 24.dp))
        Spacer(Modifier.width(6.dp))
        Text(item.nameCn, color = DeepInk, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 2)
        if (selected) SelectedMark(Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun CommonSpeciesCard(item: FishGuideItem, selected: Boolean, imageUrl: String?, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 88.dp).clip(RoundedCornerShape(16.dp))
            .background(if (selected) SoftWater else Color(0xBFF7FAFB))
            .border(if (selected) 2.dp else 1.dp, if (selected) WaterTeal else Color(0x47FFFFFF), RoundedCornerShape(16.dp))
            .semantics(mergeDescendants = true) {
                this.selected = selected
                if (selected) stateDescription = "已选择"
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SpeciesMedia(item = item, imageUrl = imageUrl, modifier = Modifier.size(width = 56.dp, height = 40.dp))
            if (selected) SelectedMark(Modifier.align(Alignment.TopEnd))
        }
        Text(item.nameCn, color = DeepInk, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SpeciesListRow(
    item: FishGuideItem,
    selected: Boolean,
    imageUrl: String?,
    enabled: Boolean,
    testTag: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(14.dp))
            .background(if (selected) SoftWater else Color(0xCFF7FAFB))
            .border(if (selected) 2.dp else 1.dp, if (selected) WaterTeal else Color(0x33FFFFFF), RoundedCornerShape(14.dp))
            .semantics(mergeDescendants = true) {
                this.selected = selected
                if (selected) stateDescription = "已选择"
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (testTag == null) Modifier else Modifier.testTag(testTag))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpeciesMedia(item = item, imageUrl = imageUrl, modifier = Modifier.size(width = 56.dp, height = 40.dp))
        Spacer(Modifier.width(12.dp))
        Text(item.nameCn, modifier = Modifier.weight(1f), color = DeepInk, fontSize = 16.sp, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (selected) {
            SelectedMark(Modifier.padding(start = 8.dp))
        } else {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MutedInk, modifier = Modifier.padding(start = 8.dp).size(18.dp))
        }
    }
}

@Composable
private fun SpeciesMedia(item: FishGuideItem, imageUrl: String?, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (!imageUrl.isNullOrBlank()) {
            RemoteImage(
                url = imageUrl,
                modifier = Modifier.fillMaxSize(),
                contentDescription = "${item.nameCn}鱼种图片",
                contentScale = ContentScale.Fit,
                placeholder = { FishIllustration(Modifier.fillMaxSize(), size = 40.dp, bodyColor = Color(0xFF789795)) },
            )
        } else {
            FishIllustration(Modifier.fillMaxSize(), size = 40.dp, bodyColor = Color(0xFF789795))
        }
    }
}

@Composable
private fun SelectedMark(modifier: Modifier = Modifier) {
    Box(modifier.size(20.dp).clip(CircleShape).background(WaterTeal), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Check, contentDescription = "已选择", tint = Color.White, modifier = Modifier.size(14.dp))
    }
}
