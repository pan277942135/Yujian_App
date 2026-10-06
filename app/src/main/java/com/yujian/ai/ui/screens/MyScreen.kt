package com.yujian.ai.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.designsystem.components.YuJianAchievementAnnotation
import com.yujian.ai.ui.designsystem.background.YuJianMorningLakeBackground
import com.yujian.ai.ui.designsystem.background.YuJianMorningLakeVariant
import com.yujian.ai.ui.designsystem.components.YuJianBackAction
import com.yujian.ai.ui.designsystem.components.YuJianBackCenterTitleTopBar
import com.yujian.ai.ui.designsystem.components.YuJianFishRecordRowCard
import com.yujian.ai.ui.designsystem.components.YuJianIconAction
import com.yujian.ai.ui.designsystem.components.YuJianIconActionFamily
import com.yujian.ai.ui.designsystem.components.YuJianTextAction
import com.yujian.ai.ui.designsystem.components.YuJianTextActionRole
import com.yujian.ai.ui.home.HomeCameraButton
import com.yujian.ai.ui.mycatches.CatchLengthRange
import com.yujian.ai.ui.mycatches.CatchTimeRange
import com.yujian.ai.ui.mycatches.CatchWeightRange
import com.yujian.ai.ui.mycatches.GrowthMarkResolver
import com.yujian.ai.ui.mycatches.MyCatchesDayGroup
import com.yujian.ai.ui.mycatches.MyCatchesEmptyState
import com.yujian.ai.ui.mycatches.MyCatchesFilterState
import com.yujian.ai.ui.mycatches.MyCatchesMonthGroup
import com.yujian.ai.ui.mycatches.SpecialCatchMark
import com.yujian.ai.ui.mycatches.commitRecentSearch
import com.yujian.ai.ui.mycatches.filterAndSortCatches
import com.yujian.ai.ui.mycatches.groupCatchesByMonthAndDay
import com.yujian.ai.ui.mycatches.isValidCatchDate
import com.yujian.ai.ui.mycatches.resolveMyCatchesEmptyState
import com.yujian.ai.ui.mycatches.rowGrowthMark
import com.yujian.ai.ui.mycatches.speciesKey
import com.yujian.ai.ui.mycatches.toFishRecordPresentation
import com.yujian.ai.presentation.presentationSpeciesName
import com.yujian.ai.ui.theme.CardWhite
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.Hairline
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.SoftWater
import com.yujian.ai.ui.theme.WaterTeal
import kotlinx.coroutines.flow.collect
import java.util.Locale

private const val RECENT_SEARCH_PREFERENCES = "my_catches_search_v1"
private const val RECENT_SEARCH_KEY = "queries"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MyScreen(
    catches: List<RemoteCatch>,
    loading: Boolean,
    error: String?,
    resolveImageUrl: (String?) -> String?,
    accessToken: String,
    initialSpeciesFilterId: String? = null,
    onCatch: (String) -> Unit,
    onRetry: () -> Unit,
    onCapture: () -> Unit,
    onBack: () -> Unit,
    onDayDetail: (MyCatchesDayGroup) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchMode by rememberSaveable { mutableStateOf(false) }
    var filterExpanded by rememberSaveable { mutableStateOf(false) }
    var filter by remember(initialSpeciesFilterId) {
        mutableStateOf(
            initialSpeciesFilterId?.takeIf(String::isNotBlank)
                ?.let { MyCatchesFilterState(speciesIds = setOf(it)) }
                ?: MyCatchesFilterState(),
        )
    }
    var expandedDays by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var visibleMonthCount by rememberSaveable { mutableStateOf(3) }
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences(RECENT_SEARCH_PREFERENCES, Context.MODE_PRIVATE) }
    var recentSearches by remember {
        mutableStateOf(preferences.getString(RECENT_SEARCH_KEY, "").orEmpty().lineSequence().filter(String::isNotBlank).take(6).toList())
    }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val listState = rememberLazyListState()
    var preSearchPosition by remember { mutableStateOf(0 to 0) }
    var pendingSearchRestore by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val growthMarks = remember(catches) { GrowthMarkResolver.resolve(catches) }
    val filtered = remember(catches, query, filter) { filterAndSortCatches(catches, query, filter) }
    val monthGroups = remember(filtered) { groupCatchesByMonthAndDay(filtered) }
    val visibleMonthGroups = monthGroups.take(visibleMonthCount)
    val emptyState = resolveMyCatchesEmptyState(
        rawCount = catches.size,
        query = query,
        filter = filter,
        resultCount = filtered.size,
        loading = loading,
        hasError = !error.isNullOrBlank(),
    )

    fun saveRecentSearch(value: String) {
        recentSearches = commitRecentSearch(recentSearches, value)
        preferences.edit().putString(RECENT_SEARCH_KEY, recentSearches.joinToString("\n")).apply()
    }

    fun exitSearch() {
        if (query.isNotBlank() && filtered.isNotEmpty()) saveRecentSearch(query)
        pendingSearchRestore = preSearchPosition
        query = ""
        searchMode = false
        focusManager.clearFocus()
        keyboard?.hide()
    }

    fun handleSearchBack() {
        if (imeVisible) keyboard?.hide() else exitSearch()
    }

    BackHandler(enabled = searchMode) { handleSearchBack() }
    LaunchedEffect(searchMode) {
        if (searchMode) {
            preSearchPosition = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            listState.scrollToItem(0)
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(query, filter) {
        expandedDays = emptyList()
        visibleMonthCount = 3
        listState.scrollToItem(0)
    }
    LaunchedEffect(monthGroups.size, visibleMonthCount) {
        if (visibleMonthCount < monthGroups.size) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.key == "load-older-months" } }
                .collect { reachedHistoryBoundary ->
                    if (reachedHistoryBoundary && visibleMonthCount < monthGroups.size) {
                        visibleMonthCount = (visibleMonthCount + 3).coerceAtMost(monthGroups.size)
                    }
                }
        }
    }
    LaunchedEffect(pendingSearchRestore) {
        pendingSearchRestore?.let { (index, offset) ->
            listState.scrollToItem(index.coerceAtLeast(0), offset.coerceAtLeast(0))
            pendingSearchRestore = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        YuJianMorningLakeBackground(YuJianMorningLakeVariant.DATA)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = safeInsets.calculateTopPadding() + 8.dp,
                bottom = safeInsets.calculateBottomPadding() + 116.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "my-catches-header") {
                if (searchMode) {
                    SearchHeader(
                        query = query,
                        focusRequester = focusRequester,
                        onQueryChange = { query = it },
                        onClearQuery = { query = "" },
                        onBack = ::handleSearchBack,
                        onCancel = ::exitSearch,
                    )
                } else {
                    MainHeader(
                        onBack = onBack,
                        filterActive = filter.isActive || filterExpanded,
                        onSearch = { searchMode = true },
                        onFilter = { filterExpanded = !filterExpanded },
                    )
                }
            }

            if (searchMode && query.isBlank()) {
                item(key = "recent-searches") {
                    RecentSearches(
                        queries = recentSearches,
                        onChoose = { query = it },
                        onClear = {
                            recentSearches = emptyList()
                            preferences.edit().remove(RECENT_SEARCH_KEY).apply()
                        },
                    )
                }
            }

            if (searchMode && filter.isActive) {
                item(key = "search-filter-summary") { FilterSummary(filter) }
            }

            if (!searchMode) {
                item(key = "archive-summary") {
                    Text(
                        text = "${filtered.size} 次鱼获 · ${filtered.map { it.speciesKey() }.distinct().size} 种鱼",
                        color = MutedInk,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 1.dp, bottom = 2.dp),
                    )
                }
                if (filterExpanded) {
                    item(key = "filter-panel") {
                        FilterPanel(catches = catches, filter = filter, onFilterChange = { filter = it })
                    }
                }
            }

            if (searchMode && query.isNotBlank()) {
                item(key = "search-count") {
                    Text("${filtered.size} 次鱼获", color = MutedInk, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
            }

            if (loading) {
                item(key = "loading") { ArchiveLoading() }
            } else if (!error.isNullOrBlank()) {
                item(key = "error") { ArchiveError(onRetry) }
            } else when (emptyState) {
                MyCatchesEmptyState.Archive -> item(key = "archive-empty") { ArchiveEmpty() }
                MyCatchesEmptyState.Search -> item(key = "search-empty") { SearchEmpty(onClear = { query = "" }) }
                MyCatchesEmptyState.Filter -> item(key = "filter-empty") {
                    FilterEmpty(
                        onClear = { filter = filter.cleared() },
                        onModify = { filterExpanded = true },
                    )
                }
                MyCatchesEmptyState.None -> {
                    visibleMonthGroups.forEach { month ->
                        stickyHeader(key = "month-${month.key}") { MonthHeader(month) }
                        month.days.forEach { day ->
                            item(key = "day-${day.key}") { DayHeader(day) }
                            val isExpanded = day.key in expandedDays
                            val collapsed = day.catches.size > 5
                            val visibleRecords = if (collapsed && !isExpanded) day.catches.take(5) else day.catches
                            items(visibleRecords, key = { "catch-${it.id}" }) { record ->
                                val allMarks = growthMarks[record.id].orEmpty()
                                YuJianFishRecordRowCard(
                                    record = record,
                                    presentation = record.toFishRecordPresentation(
                                        imageUrl = resolveImageUrl(record.imageUrl),
                                        annotations = listOfNotNull(allMarks.rowGrowthMark()),
                                    ),
                                    accessToken = accessToken,
                                    onClick = {
                                        if (query.isNotBlank()) saveRecentSearch(query)
                                        onCatch(record.id)
                                    },
                                )
                            }
                            if (collapsed) {
                                item(key = "day-fold-${day.key}") {
                                    DayFoldAction(
                                        count = day.catches.size,
                                        expanded = isExpanded,
                                        opensDayDetail = day.catches.size > 10,
                                        onClick = {
                                            when {
                                                day.catches.size > 10 -> onDayDetail(day)
                                                isExpanded -> expandedDays = expandedDays - day.key
                                                else -> expandedDays = expandedDays + day.key
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (visibleMonthCount < monthGroups.size) item(key = "load-older-months") { Spacer(Modifier.height(1.dp)) }
                }
            }
        }

        HomeCameraButton(
            onClick = onCapture,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = safeInsets.calculateBottomPadding() + 6.dp),
        )
    }
}

@Composable
fun MyCatchesDayDetailScreen(
    dayKey: String,
    catches: List<RemoteCatch>,
    resolveImageUrl: (String?) -> String?,
    accessToken: String,
    onCatch: (String) -> Unit,
    onBack: () -> Unit,
) {
    val day = remember(catches, dayKey) {
        groupCatchesByMonthAndDay(catches).flatMap { it.days }.firstOrNull { it.key == dayKey }
    }
    val marks = remember(catches) { GrowthMarkResolver.resolve(catches) }
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    Box(Modifier.fillMaxSize()) {
        YuJianMorningLakeBackground(YuJianMorningLakeVariant.DATA)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = safeInsets.calculateTopPadding() + 8.dp,
                bottom = safeInsets.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "day-detail-header") {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    YuJianBackAction(onClick = onBack)
                    Column(Modifier.weight(1f)) {
                        Text(day?.label ?: "鱼获详情", color = DeepInk, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                        day?.let { Text(it.summary, color = MutedInk, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
            if (day == null) {
                item(key = "day-detail-empty") { Text("该日期暂无鱼获", color = MutedInk, fontSize = 13.sp) }
            } else {
                items(day.catches, key = { "detail-${it.id}" }) { record ->
                    YuJianFishRecordRowCard(
                        record = record,
                        presentation = record.toFishRecordPresentation(
                            imageUrl = resolveImageUrl(record.imageUrl),
                            annotations = listOfNotNull(marks[record.id].orEmpty().rowGrowthMark()),
                        ),
                        accessToken = accessToken,
                        onClick = { onCatch(record.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MainHeader(onBack: () -> Unit, filterActive: Boolean, onSearch: () -> Unit, onFilter: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        YuJianBackCenterTitleTopBar(
            title = "我的鱼获",
            onBack = onBack,
            modifier = Modifier.fillMaxWidth(),
            statusBarInset = false,
        )
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            YuJianIconAction(icon = Icons.Rounded.Search, contentDescription = "搜索", onClick = onSearch)
            Box {
                YuJianIconAction(icon = Icons.Rounded.FilterList, contentDescription = "筛选", onClick = onFilter)
                if (filterActive) Box(Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 5.dp).size(6.dp).background(WaterTeal, CircleShape))
            }
        }
    }
}

@Composable
private fun SearchHeader(
    query: String,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        YuJianBackAction(onClick = onBack)
        SearchField(
            value = query,
            onValueChange = onQueryChange,
            onClear = onClearQuery,
            modifier = Modifier.weight(1f),
            focusRequester = focusRequester,
        )
        YuJianTextAction(text = "取消", onClick = onCancel)
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.80f))
            .border(1.dp, if (focused) WaterTeal.copy(alpha = 0.26f) else Hairline, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = DeepInk.copy(alpha = 0.72f), modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(7.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text("搜索鱼种、地点或日期", color = MutedInk, fontSize = 14.sp, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = DeepInk, fontSize = 15.sp),
                modifier = Modifier.fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused },
            )
        }
        if (value.isNotEmpty()) {
            YuJianIconAction(
                icon = Icons.Rounded.Close,
                contentDescription = "清除搜索",
                onClick = onClear,
                family = YuJianIconActionFamily.CONTEXT,
            )
        }
    }
}

@Composable
private fun RecentSearches(queries: List<String>, onChoose: (String) -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("最近搜索", color = DeepInk, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (queries.isNotEmpty()) {
                YuJianTextAction(
                    text = "清空",
                    onClick = onClear,
                    role = YuJianTextActionRole.MUTED,
                )
            }
        }
        if (queries.isEmpty()) {
            Text("搜索记录会显示在这里", color = MutedInk, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                queries.forEach { query ->
                    FilterChip(
                        selected = false,
                        onClick = { onChoose(query) },
                        label = { Text(query, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = Color.White.copy(alpha = 0.66f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterSummary(filter: MyCatchesFilterState) {
    Text(
        "筛选：${filterSummaryText(filter)}",
        color = MutedInk,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun FilterPanel(catches: List<RemoteCatch>, filter: MyCatchesFilterState, onFilterChange: (MyCatchesFilterState) -> Unit) {
    val species = catches.map { it.speciesId.ifBlank { it.speciesName.trim().lowercase(Locale.ROOT) } to presentationSpeciesName(it.speciesName) }
        .distinctBy { it.first }
        .sortedBy { it.second }
    val commonSpecies = listOf(
        "grass_carp" to "草鱼",
        "crucian_carp" to "鲫鱼",
        "common_carp" to "鲤鱼",
    ).map { (id, commonName) -> species.firstOrNull { it.second == commonName } ?: (id to commonName) }
    var showSpeciesPicker by remember { mutableStateOf(false) }
    var showCustomDate by remember { mutableStateOf(false) }
    var showCustomSize by remember { mutableStateOf(false) }
    var sizeTab by rememberSaveable { mutableStateOf("length") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.79f),
        border = BorderStroke(1.dp, Hairline.copy(alpha = 0.72f)),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            FilterDimension("鱼种") {
                FilterChipRow {
                    ChoiceChip("不限", filter.speciesIds.isEmpty()) { onFilterChange(filter.copy(speciesIds = emptySet())) }
                    commonSpecies.forEach { (key, label) ->
                        ChoiceChip(label, key in filter.speciesIds) { onFilterChange(filter.copy(speciesIds = filter.speciesIds.toggle(key))) }
                    }
                    ChoiceChip("更多 ›", false) { showSpeciesPicker = true }
                }
            }
            FilterDimension("时间") {
                FilterChipRow {
                    listOf(CatchTimeRange.All, CatchTimeRange.ThisMonth, CatchTimeRange.Last3Months, CatchTimeRange.ThisYear).forEach { range ->
                        ChoiceChip(range.label, filter.timeRange == range) {
                            onFilterChange(filter.copy(timeRange = range, customStartDate = null, customEndDate = null))
                        }
                    }
                    ChoiceChip("自定义 ›", filter.timeRange == CatchTimeRange.Custom) { showCustomDate = true }
                }
            }
            FilterDimension("尺寸") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoiceChip("长度", sizeTab == "length") { sizeTab = "length" }
                    ChoiceChip("重量", sizeTab == "weight") { sizeTab = "weight" }
                }
                Spacer(Modifier.height(5.dp))
                FilterChipRow {
                    if (sizeTab == "length") {
                        CatchLengthRange.entries.forEach { range ->
                            ChoiceChip(range.label, filter.lengthRange == range && filter.customLengthMinCm == null && filter.customLengthMaxCm == null) {
                                onFilterChange(filter.copy(lengthRange = range, customLengthMinCm = null, customLengthMaxCm = null))
                            }
                        }
                        ChoiceChip("自定义 ›", filter.customLengthMinCm != null || filter.customLengthMaxCm != null) { showCustomSize = true }
                    } else {
                        CatchWeightRange.entries.forEach { range ->
                            ChoiceChip(range.label, filter.weightRange == range && filter.customWeightMinKg == null && filter.customWeightMaxKg == null) {
                                onFilterChange(filter.copy(weightRange = range, customWeightMinKg = null, customWeightMaxKg = null))
                            }
                        }
                        ChoiceChip("自定义 ›", filter.customWeightMinKg != null || filter.customWeightMaxKg != null) { showCustomSize = true }
                    }
                }
            }
            FilterDimension("特殊记录") {
                FilterChipRow {
                    ChoiceChip("不限", filter.specialMarks.isEmpty()) { onFilterChange(filter.copy(specialMarks = emptySet())) }
                    SpecialCatchMark.entries.forEach { mark ->
                        ChoiceChip(mark.label, mark in filter.specialMarks) {
                            onFilterChange(filter.copy(specialMarks = filter.specialMarks.toggle(mark)))
                        }
                    }
                }
            }
            TextButton(
                onClick = { onFilterChange(filter.cleared()) },
                enabled = filter.isActive,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                modifier = Modifier.align(Alignment.End),
            ) { Text("重置", color = if (filter.isActive) WaterTeal else MutedInk, fontSize = 12.sp) }
        }
    }

    if (showSpeciesPicker) {
        AlertDialog(
            onDismissRequest = { showSpeciesPicker = false },
            title = { Text("选择鱼种", color = DeepInk) },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(species, key = { it.first }) { (key, label) ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 46.dp).clickable {
                                onFilterChange(filter.copy(speciesIds = filter.speciesIds.toggle(key)))
                            }.padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (key in filter.speciesIds) "✓" else "", color = WaterTeal, modifier = Modifier.width(28.dp))
                            Text(label, color = DeepInk)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSpeciesPicker = false }) { Text("完成", color = WaterTeal) } },
        )
    }
    if (showCustomDate) {
        CustomDateDialog(
            initialStart = filter.customStartDate.orEmpty(),
            initialEnd = filter.customEndDate.orEmpty(),
            onDismiss = { showCustomDate = false },
            onApply = { start, end ->
                onFilterChange(filter.copy(timeRange = CatchTimeRange.Custom, customStartDate = start, customEndDate = end))
                showCustomDate = false
            },
        )
    }
    if (showCustomSize) {
        CustomSizeDialog(
            isLength = sizeTab == "length",
            initialMin = if (sizeTab == "length") filter.customLengthMinCm?.toString().orEmpty() else filter.customWeightMinKg?.toString().orEmpty(),
            initialMax = if (sizeTab == "length") filter.customLengthMaxCm?.toString().orEmpty() else filter.customWeightMaxKg?.toString().orEmpty(),
            onDismiss = { showCustomSize = false },
            onApply = { min, max ->
                if (sizeTab == "length") onFilterChange(filter.copy(lengthRange = CatchLengthRange.All, customLengthMinCm = min, customLengthMaxCm = max))
                else onFilterChange(filter.copy(weightRange = CatchWeightRange.All, customWeightMinKg = min, customWeightMaxKg = max))
                showCustomSize = false
            },
        )
    }
}

@Composable
private fun FilterDimension(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(title, color = DeepInk, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        content()
    }
}

@Composable
private fun FilterChipRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) { content() }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp, maxLines = 1) },
        modifier = Modifier.heightIn(min = 44.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SoftWater.copy(alpha = 0.88f),
            selectedLabelColor = WaterTeal,
            containerColor = Color(0xFFEDEFEF).copy(alpha = 0.76f),
            labelColor = DeepInk,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Hairline,
            selectedBorderColor = WaterTeal.copy(alpha = 0.35f),
        ),
    )
}

@Composable
private fun CustomDateDialog(initialStart: String, initialEnd: String, onDismiss: () -> Unit, onApply: (String, String) -> Unit) {
    var start by rememberSaveable { mutableStateOf(initialStart) }
    var end by rememberSaveable { mutableStateOf(initialEnd) }
    val valid = isValidCatchDate(start) && isValidCatchDate(end) && start <= end
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义日期") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(start, { start = it.take(10) }, label = { Text("开始日期 YYYY-MM-DD") }, singleLine = true)
                OutlinedTextField(end, { end = it.take(10) }, label = { Text("结束日期 YYYY-MM-DD") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onApply(start, end) }) { Text("应用", color = WaterTeal) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun CustomSizeDialog(
    isLength: Boolean,
    initialMin: String,
    initialMax: String,
    onDismiss: () -> Unit,
    onApply: (Float?, Float?) -> Unit,
) {
    var minText by rememberSaveable { mutableStateOf(initialMin) }
    var maxText by rememberSaveable { mutableStateOf(initialMax) }
    val min = minText.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }
    val max = maxText.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }
    val valid = (minText.isBlank() || min != null) && (maxText.isBlank() || max != null) &&
        (min != null || max != null) && (min == null || max == null || min <= max)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isLength) "自定义长度" else "自定义重量") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(minText, { minText = it }, label = { Text("最小值 ${if (isLength) "cm" else "kg"}") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(maxText, { maxText = it }, label = { Text("最大值 ${if (isLength) "cm" else "kg"}") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onApply(min, max) }) { Text("应用", color = WaterTeal) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun MonthHeader(month: MyCatchesMonthGroup) {
    Surface(color = Color.White.copy(alpha = 0.66f), shape = RoundedCornerShape(12.dp)) {
        Text(month.label, color = DeepInk, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
private fun DayHeader(day: MyCatchesDayGroup) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 2.dp)) {
        Box(Modifier.size(8.dp).background(WaterTeal, CircleShape))
        Spacer(Modifier.size(8.dp))
        Text(day.label, color = DeepInk, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(day.summary, color = MutedInk, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 7.dp).weight(1f))
    }
}

@Composable
private fun DayFoldAction(count: Int, expanded: Boolean, opensDayDetail: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 42.dp).clickable(onClick = onClick).padding(start = 100.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when {
                opensDayDetail -> "查看全部 ${count} 条鱼获"
                expanded -> "收起"
                else -> "查看另外 ${count - 5} 条鱼获"
            },
            color = MutedInk,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        if (!opensDayDetail) Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = MutedInk, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ArchiveLoading() {
    Surface(color = CardWhite.copy(alpha = 0.76f), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = WaterTeal, strokeWidth = 2.dp)
            Text("正在整理你的时间档案…", color = MutedInk, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

@Composable
private fun ArchiveError(onRetry: () -> Unit) {
    Surface(color = CardWhite.copy(alpha = 0.78f), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text("鱼获档案暂时无法加载", color = DeepInk, fontWeight = FontWeight.SemiBold)
            Text("请检查网络后重试。", color = MutedInk, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            TextButton(onClick = onRetry, modifier = Modifier.padding(top = 4.dp)) { Text("重新加载", color = WaterTeal) }
        }
    }
}

@Composable
private fun ArchiveEmpty() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 45.dp)) {
        Surface(color = CardWhite.copy(alpha = 0.76f), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("还没有鱼获记录", color = DeepInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("拍下第一条鱼，开始你的鱼获时间线", color = MutedInk, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Text("记录第一条鱼", color = MutedInk, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun SearchEmpty(onClear: () -> Unit) = EmptyMessage(
    title = "没有找到相关鱼获",
    subtitle = "你可以搜索：鱼种、地点、日期",
    actions = listOf("清除搜索" to onClear),
)

@Composable
private fun FilterEmpty(onClear: () -> Unit, onModify: () -> Unit) = EmptyMessage(
    title = "没有找到符合条件的鱼获",
    subtitle = "试试调整筛选条件",
    actions = listOf("清除筛选" to onClear, "修改筛选" to onModify),
)

@Composable
private fun EmptyMessage(title: String, subtitle: String, actions: List<Pair<String, () -> Unit>>) {
    Surface(color = CardWhite.copy(alpha = 0.76f), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = DeepInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MutedInk, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                actions.forEach { (label, action) -> TextButton(onClick = action) { Text(label, color = WaterTeal) } }
            }
        }
    }
}

private fun filterSummaryText(filter: MyCatchesFilterState): String = buildList {
    if (filter.speciesIds.isNotEmpty()) add("${filter.speciesIds.size}种鱼")
    if (filter.timeRange == CatchTimeRange.Custom) add("${filter.customStartDate.orEmpty()}–${filter.customEndDate.orEmpty()}")
    else if (filter.timeRange != CatchTimeRange.All) add(filter.timeRange.label)
    if (filter.lengthRange != CatchLengthRange.All) add(filter.lengthRange.label)
    filter.customLengthMinCm?.let { add("长度≥${it}cm") }
    filter.customLengthMaxCm?.let { add("长度≤${it}cm") }
    if (filter.weightRange != CatchWeightRange.All) add(filter.weightRange.label)
    filter.customWeightMinKg?.let { add("重量≥${it}kg") }
    filter.customWeightMaxKg?.let { add("重量≤${it}kg") }
    if (filter.specialMarks.isNotEmpty()) addAll(filter.specialMarks.map { it.label })
}.joinToString(" · ").ifBlank { "不限" }

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
