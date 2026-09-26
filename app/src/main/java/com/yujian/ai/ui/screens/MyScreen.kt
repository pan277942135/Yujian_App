package com.yujian.ai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.designsystem.components.YuJianFishRecordRowCard
import com.yujian.ai.ui.home.HomeCameraButton
import com.yujian.ai.ui.mycatches.CatchTimeRange
import com.yujian.ai.ui.mycatches.GrowthMarkResolver
import com.yujian.ai.ui.mycatches.MyCatchesDayGroup
import com.yujian.ai.ui.mycatches.MyCatchesFilterState
import com.yujian.ai.ui.mycatches.MyCatchesMonthGroup
import com.yujian.ai.ui.mycatches.MyCatchesEmptyState
import com.yujian.ai.ui.mycatches.filterAndSortCatches
import com.yujian.ai.ui.mycatches.groupCatchesByMonthAndDay
import com.yujian.ai.ui.mycatches.resolveMyCatchesEmptyState
import com.yujian.ai.ui.mycatches.toFishRecordPresentation
import com.yujian.ai.presentation.presentationSpeciesName
import com.yujian.ai.presentation.sanitizeOptionalText
import com.yujian.ai.ui.theme.CardWhite
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.Hairline
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.SoftWater
import com.yujian.ai.ui.theme.WaterTeal

private enum class FilterSheetKind { Species, Location, Time }
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyScreen(
    catches: List<RemoteCatch>,
    loading: Boolean,
    error: String?,
    resolveImageUrl: (String?) -> String?,
    accessToken: String,
    onCatch: (String) -> Unit,
    onRetry: () -> Unit,
    onCapture: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedSpecies by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selectedLocations by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selectedTimeRange by rememberSaveable { mutableStateOf(CatchTimeRange.All.name) }
    var openFilter by remember { mutableStateOf<FilterSheetKind?>(null) }
    val listState = rememberLazyListState()
    val filter = MyCatchesFilterState(
        speciesIds = selectedSpecies.toSet(),
        locations = selectedLocations.toSet(),
        timeRange = CatchTimeRange.entries.firstOrNull { it.name == selectedTimeRange } ?: CatchTimeRange.All,
    )
    val filtered = filterAndSortCatches(catches, query, filter)
    val monthGroups = groupCatchesByMonthAndDay(filtered)
    val growthMarks = remember(catches) { GrowthMarkResolver.resolve(catches) }
    val emptyState = resolveMyCatchesEmptyState(catches.size, query, filter, filtered.size, loading, !error.isNullOrBlank())
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFE7F0F1), Color(0xFFF5F2EC)))),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = safeInsets.calculateTopPadding() + 10.dp,
                bottom = safeInsets.calculateBottomPadding() + 112.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "my-catches-header") {
                Column(Modifier.padding(bottom = 2.dp)) {
                    Text("我的鱼获", color = DeepInk, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                    Text("按时间留存每一次真实鱼获", color = MutedInk, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "搜索") },
                    placeholder = { Text("搜索鱼种或地点", color = MutedInk) },
                    shape = RoundedCornerShape(16.dp),
                )
            }
            item(key = "filters") {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterEntry("鱼种", selectedSpecies.size, selectedSpecies.isNotEmpty()) { openFilter = FilterSheetKind.Species }
                    FilterEntry("地点", selectedLocations.size, selectedLocations.isNotEmpty()) { openFilter = FilterSheetKind.Location }
                    FilterEntry("时间", if (filter.timeRange == CatchTimeRange.All) 0 else 1, filter.timeRange != CatchTimeRange.All) { openFilter = FilterSheetKind.Time }
                    if (filter.isActive || query.isNotBlank()) {
                        TextButton(onClick = {
                            query = ""
                            selectedSpecies = emptyList()
                            selectedLocations = emptyList()
                            selectedTimeRange = CatchTimeRange.All.name
                        }) { Text("清除", color = WaterTeal) }
                    }
                }
            }
            item(key = "archive-summary") {
                Text(
                    text = if (catches.isEmpty()) "时间档案" else "${filtered.size} 次鱼获 · ${filtered.map { it.speciesId.ifBlank { it.speciesName } }.distinct().size} 种鱼",
                    color = MutedInk,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (loading) {
                item(key = "loading") { ArchiveLoading() }
            } else if (!error.isNullOrBlank()) {
                item(key = "error") { ArchiveError(error, onRetry) }
            } else if (emptyState != MyCatchesEmptyState.None) {
                item(key = "empty-${emptyState.name}") {
                    when (emptyState) {
                        MyCatchesEmptyState.Archive -> ArchiveEmpty(onCapture)
                        MyCatchesEmptyState.Search -> SearchEmpty { query = "" }
                        MyCatchesEmptyState.Filter -> FilterEmpty {
                            selectedSpecies = emptyList()
                            selectedLocations = emptyList()
                            selectedTimeRange = CatchTimeRange.All.name
                        }
                        MyCatchesEmptyState.None -> Unit
                    }
                }
            } else {
                monthGroups.forEach { month ->
                    item(key = "month-${month.key}") { MonthHeader(month) }
                    month.days.forEach { day ->
                        item(key = "day-${day.key}") { DayHeader(day) }
                        items(day.catches, key = { it.id }) { record ->
                            YuJianFishRecordRowCard(
                                record = record,
                                presentation = record.toFishRecordPresentation(
                                    imageUrl = resolveImageUrl(record.imageUrl),
                                    annotations = growthMarks[record.id].orEmpty(),
                                ),
                                accessToken = accessToken,
                                onClick = { onCatch(record.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    openFilter?.let { kind ->
        FilterSheet(
            kind = kind,
            catches = catches,
            filter = filter,
            onDismiss = { openFilter = null },
            onSpeciesChanged = { selectedSpecies = it.toList() },
            onLocationsChanged = { selectedLocations = it.toList() },
            onTimeChanged = { selectedTimeRange = it.name },
        )
    }
}

@Composable
private fun FilterEntry(label: String, count: Int, active: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (active) SoftWater.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.45f),
            contentColor = if (active) WaterTeal else DeepInk,
        ),
        border = BorderStroke(1.dp, if (active) WaterTeal.copy(alpha = 0.45f) else Hairline),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
    ) {
        Icon(Icons.Rounded.FilterList, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.size(4.dp))
        Text(if (count == 0) label else "$label $count", fontSize = 12.sp)
    }
}

@Composable
private fun MonthHeader(month: MyCatchesMonthGroup) {
    Text(month.label, color = DeepInk, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
}

@Composable
private fun DayHeader(day: MyCatchesDayGroup) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
        Box(Modifier.size(10.dp).background(WaterTeal, CircleShape))
        Spacer(Modifier.size(10.dp))
        Text(day.label, color = DeepInk, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(day.summary, color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ArchiveLoading() {
    Surface(color = CardWhite.copy(alpha = 0.55f), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = WaterTeal, strokeWidth = 2.dp)
            Text("正在整理你的时间档案…", color = MutedInk, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

@Composable
private fun ArchiveError(error: String, onRetry: () -> Unit) {
    Surface(color = CardWhite.copy(alpha = 0.64f), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text("鱼获档案暂时无法加载", color = DeepInk, fontWeight = FontWeight.SemiBold)
            Text(error, color = MutedInk, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            TextButton(onClick = onRetry, modifier = Modifier.padding(top = 4.dp)) { Text("重新加载", color = WaterTeal) }
        }
    }
}

@Composable
private fun ArchiveEmpty(onCapture: () -> Unit) {
    Surface(color = CardWhite.copy(alpha = 0.64f), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("还没有鱼获记录", color = DeepInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("记录第一条鱼，让时间留下来。", color = MutedInk, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(12.dp))
            HomeCameraButton(onClick = onCapture)
        }
    }
}

@Composable
private fun SearchEmpty(onClear: () -> Unit) = EmptyMessage("没有找到匹配的鱼获", "换一个鱼种或地点试试。", "清除搜索", onClear)

@Composable
private fun FilterEmpty(onClear: () -> Unit) = EmptyMessage("筛选条件下暂无鱼获", "可以调整或清除筛选条件。", "清除筛选", onClear)

@Composable
private fun EmptyMessage(title: String, subtitle: String, action: String, onClick: () -> Unit) {
    Surface(color = CardWhite.copy(alpha = 0.64f), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = DeepInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MutedInk, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            TextButton(onClick = onClick, modifier = Modifier.padding(top = 4.dp)) { Text(action, color = WaterTeal) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    kind: FilterSheetKind,
    catches: List<RemoteCatch>,
    filter: MyCatchesFilterState,
    onDismiss: () -> Unit,
    onSpeciesChanged: (Set<String>) -> Unit,
    onLocationsChanged: (Set<String>) -> Unit,
    onTimeChanged: (CatchTimeRange) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val species = catches
        .map { it.speciesId.ifBlank { it.speciesName } to presentationSpeciesName(it.speciesName) }
        .distinctBy { it.first }
        .sortedBy { it.second }
    val locations = catches.mapNotNull { sanitizeOptionalText(it.location) }.distinct().sorted()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text(
                when (kind) {
                    FilterSheetKind.Species -> "按鱼种筛选"
                    FilterSheetKind.Location -> "按地点筛选"
                    FilterSheetKind.Time -> "按时间筛选"
                },
                color = DeepInk,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            when (kind) {
                FilterSheetKind.Species -> {
                    OptionList(Modifier.heightIn(max = 360.dp)) {
                        species.forEach { (key, label) ->
                            item(key = key) {
                                CheckOption(label, key in filter.speciesIds) { onSpeciesChanged(filter.speciesIds.toggle(key)) }
                            }
                        }
                    }
                }
                FilterSheetKind.Location -> {
                    OptionList(Modifier.heightIn(max = 360.dp)) {
                        locations.forEach { location ->
                            item(key = location) {
                                CheckOption(location, location in filter.locations) { onLocationsChanged(filter.locations.toggle(location)) }
                            }
                        }
                    }
                }
                FilterSheetKind.Time -> {
                    CatchTimeRange.entries.forEach { range ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = filter.timeRange == range, onClick = { onTimeChanged(range) })
                            Text(range.label, color = DeepInk, fontSize = 14.sp)
                        }
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("完成", color = WaterTeal) }
        }
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

@Composable
private fun OptionList(modifier: Modifier = Modifier, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(modifier = modifier, content = content)
}

@Composable
private fun CheckOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = selected, onCheckedChange = { onClick() })
        Text(label, color = DeepInk, fontSize = 14.sp)
    }
}
