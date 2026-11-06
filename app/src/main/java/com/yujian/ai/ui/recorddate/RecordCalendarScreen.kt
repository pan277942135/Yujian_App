package com.yujian.ai.ui.recorddate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.components.FishIllustration
import com.yujian.ai.ui.components.RemoteImage
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.components.YuJianBackAction
import com.yujian.ai.ui.designsystem.components.YuJianGlassCard
import com.yujian.ai.ui.designsystem.components.YuJianPinnedCenterTitleTopBar
import com.yujian.ai.ui.designsystem.components.YuJianTextAction
import com.yujian.ai.ui.designsystem.typography.YuJianTypography
import com.yujian.ai.ui.mycatches.toFishRecordPresentation
import com.yujian.ai.ui.designsystem.background.YuJianMorningLakeBackground
import com.yujian.ai.ui.designsystem.background.YuJianMorningLakeVariant
import com.yujian.ai.ui.theme.MutedInk
import java.util.Calendar
import java.util.Locale

private val TealMarker = Color(0xFF4E838A)
private val TealDeep = Color(0xFF3E6E75)
private val SoftWater = Color(0xFFEAF3F1)
private val WeekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
fun RecordCalendarScreen(
    catches: List<RemoteCatch>,
    resolveImageUrl: (String?) -> String?,
    accessToken: String,
    onCatch: (String) -> Unit,
    onBack: () -> Unit,
) {
    val today = remember { Calendar.getInstance() }
    val dayMap = remember(catches) { buildRecordDayMap(catches) }
    var viewMode by remember { mutableStateOf(RecordCalendarViewMode.MONTH) }
    var cursorYear by remember { mutableStateOf(today.get(Calendar.YEAR)) }
    var cursorMonth by remember { mutableStateOf(today.get(Calendar.MONTH) + 1) }
    var selectedYear by remember { mutableStateOf(today.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableStateOf(today.get(Calendar.MONTH) + 1) }
    var selectedDay by remember { mutableStateOf(today.get(Calendar.DAY_OF_MONTH)) }

    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    Box(Modifier.fillMaxSize()) {
        YuJianMorningLakeBackground(YuJianMorningLakeVariant.DATA)
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxWidth()
                    .padding(top = safeInsets.calculateTopPadding() + 4.dp, start = 18.dp, end = 18.dp),
            ) {
                RecordCalendarHeader(onBack = onBack, onGoToday = {
                    cursorYear = today.get(Calendar.YEAR)
                    cursorMonth = today.get(Calendar.MONTH) + 1
                    selectedYear = cursorYear
                    selectedMonth = cursorMonth
                    selectedDay = today.get(Calendar.DAY_OF_MONTH)
                    viewMode = RecordCalendarViewMode.MONTH
                })
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 4.dp,
                    bottom = safeInsets.calculateBottomPadding() + 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
            item(key = "time-nav") {
                TimeNavRow(
                    title = if (viewMode == RecordCalendarViewMode.YEAR) {
                        "${cursorYear}年"
                    } else {
                        "${cursorYear}年${cursorMonth}月"
                    },
                    onPrevious = { shiftCursor(today, -1) { y, m -> cursorYear = y; cursorMonth = m } },
                    onNext = { shiftCursor(today, +1) { y, m -> cursorYear = y; cursorMonth = m } },
                )
            }
            item(key = "segment") {
                ViewModeSegment(
                    mode = viewMode,
                    onSelect = { mode ->
                        viewMode = mode
                        if (mode == RecordCalendarViewMode.YEAR) {
                            selectedYear = cursorYear
                            selectedMonth = cursorMonth
                        } else {
                            cursorYear = selectedYear
                            cursorMonth = selectedMonth
                        }
                    },
                )
            }
            when (viewMode) {
                RecordCalendarViewMode.MONTH -> {
                    item(key = "month-summary") {
                        MonthSummaryBar(resolveMonthStats(dayMap, cursorYear, cursorMonth, today))
                    }
                    item(key = "month-grid") {
                        MonthGridCard(
                            cells = remember(cursorYear, cursorMonth) { buildMonthCells(cursorYear, cursorMonth) },
                            dayMap = dayMap,
                            todayKey = calendarDayKey(today),
                            selectedKey = "%04d-%02d-%02d".format(Locale.US, selectedYear, selectedMonth, selectedDay),
                            onSelect = { cell ->
                                if (!cell.inMonth) {
                                    cursorYear = cell.year
                                    cursorMonth = cell.month
                                }
                                selectedYear = cell.year
                                selectedMonth = cell.month
                                selectedDay = cell.day
                            },
                        )
                    }
                    item(key = "day-timeline") {
                        DayTimelinePanel(
                            year = selectedYear,
                            month = selectedMonth,
                            day = selectedDay,
                            catches = dayMap[
                                "%04d-%02d-%02d".format(Locale.US, selectedYear, selectedMonth, selectedDay)
                            ].orEmpty(),
                            nearestRecordDay = nearestRecordDay(dayMap, today),
                            onJumpDay = { key ->
                                val parts = key.split('-').map { it.toInt() }
                                cursorYear = parts[0]
                                cursorMonth = parts[1]
                                selectedYear = parts[0]
                                selectedMonth = parts[1]
                                selectedDay = parts[2]
                            },
                            resolveImageUrl = resolveImageUrl,
                            accessToken = accessToken,
                            onCatch = onCatch,
                        )
                    }
                }
                RecordCalendarViewMode.YEAR -> {
                    item(key = "year-summary") {
                        YearSummaryBar(resolveYearStats(dayMap, cursorYear, today))
                    }
                    item(key = "year-grid") {
                        YearGridCard(
                            stats = remember(cursorYear) { resolveYearMonthStats(dayMap, cursorYear) },
                            displayYear = cursorYear,
                            currentYear = today.get(Calendar.YEAR),
                            currentMonth = today.get(Calendar.MONTH) + 1,
                            selectedMonth = selectedMonth,
                            onSelect = { month -> selectedMonth = month },
                        )
                    }
                    item(key = "month-archive") {
                        MonthArchivePanel(
                            year = cursorYear,
                            month = selectedMonth,
                            dayMap = dayMap,
                            onOpenDay = { key ->
                                val parts = key.split('-').map { it.toInt() }
                                viewMode = RecordCalendarViewMode.MONTH
                                cursorYear = parts[0]
                                cursorMonth = parts[1]
                                selectedYear = parts[0]
                                selectedMonth = parts[1]
                                selectedDay = parts[2]
                            },
                        )
                    }
                }
            }
        }
        }
    }
}

/** Keeps month/year navigation within a sane range around the current date. */
private fun shiftCursor(today: Calendar, delta: Int, apply: (Int, Int) -> Unit) {
    val calendar = Calendar.getInstance().apply {
        clear()
        set(today.get(Calendar.YEAR), today.get(Calendar.MONTH), 1)
        add(Calendar.MONTH, delta)
    }
    apply(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
}

private fun nearestRecordDay(dayMap: Map<String, List<RemoteCatch>>, today: Calendar): String? {
    val todayKey = calendarDayKey(today)
    return dayMap.keys.filter { it < todayKey }.maxOrNull()
}

@Composable
private fun RecordCalendarHeader(onBack: () -> Unit, onGoToday: () -> Unit) {
    YuJianPinnedCenterTitleTopBar(
        title = "记录",
        onBack = onBack,
        statusBarInset = false,
        titleStyle = YuJianTypography.sectionTitle,
        actions = {
            YuJianTextAction(
                text = "今天",
                onClick = onGoToday,
            )
        },
    )
}

@Composable
private fun TimeNavRow(title: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(18.dp))
                .clickable(role = Role.Button, onClick = onPrevious),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.ChevronLeft,
                contentDescription = "上一周期",
                tint = YuJianColors.TextPrimary.copy(alpha = 0.82f),
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = title,
            color = YuJianColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(18.dp))
                .clickable(role = Role.Button, onClick = onNext),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = "下一周期",
                tint = YuJianColors.TextPrimary.copy(alpha = 0.82f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ViewModeSegment(mode: RecordCalendarViewMode, onSelect: (RecordCalendarViewMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 68.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, YuJianColors.MistBlueGray.copy(alpha = 0.34f), RoundedCornerShape(16.dp))
            .padding(2.dp),
    ) {
        SegmentLabel("月", mode == RecordCalendarViewMode.MONTH, Modifier.weight(1f)) {
            onSelect(RecordCalendarViewMode.MONTH)
        }
        SegmentLabel("年", mode == RecordCalendarViewMode.YEAR, Modifier.weight(1f)) {
            onSelect(RecordCalendarViewMode.YEAR)
        }
    }
}

@Composable
private fun SegmentLabel(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) TealDeep else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else YuJianColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun MonthSummaryBar(stats: RecordCalendarMonthStats) {
    YuJianGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 13.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SummaryEntry("连续记录", "${stats.streakDays}天", Modifier.weight(1f))
            SummaryDivider()
            SummaryEntry("本周", "${stats.weekDays}天", Modifier.weight(1f))
            SummaryDivider()
            SummaryEntry("本月", "${stats.monthDays}天", Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryEntry(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = YuJianColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = YuJianColors.TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun SummaryDivider() {
    Box(
        Modifier.width(1.dp).height(22.dp)
            .background(YuJianColors.MistBlueGray.copy(alpha = 0.32f)),
    )
}

@Composable
private fun MonthGridCard(
    cells: List<RecordCalendarCell>,
    dayMap: Map<String, List<RemoteCatch>>,
    todayKey: String,
    selectedKey: String,
    onSelect: (RecordCalendarCell) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.72f))
            .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            WeekdayLabels.forEach { label ->
                Text(
                    text = label,
                    color = YuJianColors.TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    DayCell(
                        cell = cell,
                        catchCount = dayMap[cell.dayKey]?.size ?: 0,
                        isToday = cell.dayKey == todayKey,
                        isSelected = cell.dayKey == selectedKey,
                        modifier = Modifier.weight(1f).padding(2.dp),
                        onClick = { onSelect(cell) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    cell: RecordCalendarCell,
    catchCount: Int,
    isToday: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .then(
                when {
                    isSelected -> Modifier.border(
                        1.dp,
                        YuJianColors.MorningGold.copy(alpha = 0.55f),
                        shape,
                    ).background(YuJianColors.MorningGold.copy(alpha = 0.14f))
                    isToday -> Modifier.border(1.dp, TealMarker.copy(alpha = 0.6f), shape)
                    else -> Modifier
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = cell.day.toString(),
            color = if (!cell.inMonth) {
                YuJianColors.TextPrimary.copy(alpha = 0.35f)
            } else {
                YuJianColors.TextPrimary
            },
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
        Box(Modifier.height(10.dp), contentAlignment = Alignment.Center) {
            when {
                catchCount == 0 -> Unit
                catchCount > 5 -> Text(
                    "×$catchCount",
                    color = TealMarker,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
                else -> Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    repeat(catchCount) {
                        FishIllustration(size = 8.dp, bodyColor = TealMarker)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayTimelinePanel(
    year: Int,
    month: Int,
    day: Int,
    catches: List<RemoteCatch>,
    nearestRecordDay: String?,
    onJumpDay: (String) -> Unit,
    resolveImageUrl: (String?) -> String?,
    accessToken: String,
    onCatch: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.72f))
            .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column {
            Text(
                text = "${month}月${day}日 ${weekdayLabel(year, month, day)}",
                color = YuJianColors.TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${catches.size}次鱼获 · ${catches.map { it.speciesId.ifBlank { it.speciesName } }.filter(String::isNotBlank).distinct().size}种鱼",
                color = YuJianColors.TextSecondary,
                fontSize = 13.sp,
            )
        }
        if (catches.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("这一天暂无鱼获", color = YuJianColors.TextPrimary, fontSize = 14.sp)
                nearestRecordDay?.let { key ->
                    val label = remember(key) {
                        val parts = key.split('-')
                        "${parts[1].toInt()}月${parts[2].toInt()}日"
                    }
                    Text(
                        "最近的鱼获在$label",
                        color = TealMarker,
                        fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button) { onJumpDay(key) }
                            .padding(4.dp),
                    )
                }
            }
        } else {
            catches.forEach { record ->
                TimelineRow(
                    record = record,
                    resolveImageUrl = resolveImageUrl,
                    accessToken = accessToken,
                    onClick = { onCatch(record.id) },
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(
    record: RemoteCatch,
    resolveImageUrl: (String?) -> String?,
    accessToken: String,
    onClick: () -> Unit,
) {
    val presentation = remember(record.id) {
        record.toFishRecordPresentation(imageUrl = resolveImageUrl(record.imageUrl))
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = timeLabel(record),
            color = YuJianColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.width(34.dp),
        )
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(SoftWater),
            contentAlignment = Alignment.Center,
        ) {
            RemoteImage(
                url = presentation.imageUrl,
                authToken = accessToken,
                modifier = Modifier.fillMaxSize(),
                contentDescription = "${presentation.speciesName} 鱼获照片",
                contentScale = ContentScale.Crop,
                placeholder = {
                    FishIllustration(size = 16.dp, bodyColor = TealMarker.copy(alpha = 0.72f))
                },
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                presentation.speciesName,
                color = YuJianColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            presentation.measurementLabel?.let {
                Text(it, color = YuJianColors.TextPrimary.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 1)
            }
            presentation.location?.let { location ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = MutedInk,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(location, color = YuJianColors.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = YuJianColors.TextPrimary.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun YearSummaryBar(stats: RecordCalendarYearStats) {
    YuJianGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 13.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SummaryEntry("全年记录", "${stats.recordDays}天", Modifier.weight(1f))
            SummaryDivider()
            SummaryEntry("鱼获总次数", "${stats.catchCount}次", Modifier.weight(1f))
            SummaryDivider()
            SummaryEntry(
                "平均每月",
                if (stats.averagePerMonth >= 10f) {
                    "${stats.averagePerMonth.toInt()}次"
                } else {
                    String.format(Locale.US, "%.1f次", stats.averagePerMonth)
                },
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun YearGridCard(
    stats: List<RecordCalendarYearMonthStats>,
    displayYear: Int,
    currentYear: Int,
    currentMonth: Int,
    selectedMonth: Int,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        stats.chunked(4).forEach { rowStats ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowStats.forEach { monthStats ->
                    YearMonthCell(
                        stats = monthStats,
                        isCurrent = displayYear == currentYear && monthStats.month == currentMonth,
                        isSelected = monthStats.month == selectedMonth,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(monthStats.month) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun YearMonthCell(
    stats: RecordCalendarYearMonthStats,
    isCurrent: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .heightIn(min = 70.dp)
            .aspectRatio(1f)
            .clip(shape)
            .then(
                when {
                    isSelected -> Modifier.border(
                        1.5.dp,
                        YuJianColors.MorningGold,
                        shape,
                    ).background(YuJianColors.MorningGold.copy(alpha = 0.14f))
                    // 卡片本体已是 White 0.72 半透明底，单元格再叠白会与边框 padding 处
                    // 呈现双重透明度，因此只保留描边、不再单独填充。
                    else -> Modifier
                        .border(1.dp, Color.White.copy(alpha = 0.55f), shape)
                },
            )
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        // "本月"角标固定在单元格右上角，与居中的月份标题分离，避免重叠。
        if (isCurrent) {
            Text(
                text = "本月",
                color = YuJianColors.MorningGold,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .background(
                        YuJianColors.MorningGold.copy(alpha = 0.16f),
                        RoundedCornerShape(7.dp),
                    )
                    .border(1.dp, YuJianColors.MorningGold.copy(alpha = 0.45f), RoundedCornerShape(7.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 7.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "${stats.month}月",
                color = if (isSelected) YuJianColors.MorningGold else YuJianColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${stats.recordDays}天 / ${stats.catchCount}次",
                color = YuJianColors.TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun MonthArchivePanel(
    year: Int,
    month: Int,
    dayMap: Map<String, List<RemoteCatch>>,
    onOpenDay: (String) -> Unit,
) {
    val monthKeyPrefix = "%04d-%02d".format(Locale.US, year, month)
    val dayKeys = remember(monthKeyPrefix, dayMap) {
        dayMap.keys.filter { it.startsWith(monthKeyPrefix) }.sortedDescending()
    }
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.72f))
            .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.width(4.dp).height(18.dp)
                    .background(YuJianColors.MorningGold, RoundedCornerShape(2.dp)),
            )
            Text(
                text = "${month}月记录",
                color = YuJianColors.TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (dayKeys.isEmpty()) {
            Text(
                "这个月还没有鱼获记录",
                color = YuJianColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        } else {
            dayKeys.forEach { key ->
                MonthArchiveRow(dayKey = key, catches = dayMap.getValue(key), onOpenDay = { onOpenDay(key) })
            }
        }
    }
}

@Composable
private fun MonthArchiveRow(dayKey: String, catches: List<RemoteCatch>, onOpenDay: () -> Unit) {
    val day = remember(dayKey) { dayKey.split('-')[2].toInt() }
    val speciesNames = remember(catches) {
        catches.map { it.speciesName.ifBlank { "确认中的鱼" } }.distinct().take(3).joinToString("、")
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onOpenDay)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(58.dp)) {
            Text(
                text = "%d.%d".format(Locale.US, dayKey.split('-')[1].toInt(), day),
                color = YuJianColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${weekdayLabel(dayKey.split('-')[0].toInt(), dayKey.split('-')[1].toInt(), day)} · ${catches.size}次",
                color = YuJianColors.TextSecondary,
                fontSize = 10.sp,
            )
        }
        Text(
            text = speciesNames,
            color = YuJianColors.TextPrimary.copy(alpha = 0.86f),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = YuJianColors.TextPrimary.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp),
        )
    }
}
