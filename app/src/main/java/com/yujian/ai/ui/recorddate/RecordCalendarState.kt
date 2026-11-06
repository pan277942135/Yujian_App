package com.yujian.ai.ui.recorddate

import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.mycatches.resolveCatchTimestamp
import java.util.Calendar
import java.util.Locale

/** Route id for the record calendar page (month/year views of catch days). */
const val RecordCalendarRoute = "record_calendar"

enum class RecordCalendarViewMode {
    MONTH,
    YEAR,
}

/** One cell of the month grid; padding cells carry the neighbouring month date. */
data class RecordCalendarCell(
    val year: Int,
    val month: Int,
    val day: Int,
    val inMonth: Boolean,
) {
    val dayKey: String get() = "%04d-%02d-%02d".format(Locale.US, year, month, day)
}

data class RecordCalendarMonthStats(
    val streakDays: Int,
    val weekDays: Int,
    val monthDays: Int,
)

data class RecordCalendarYearMonthStats(
    val month: Int,
    val recordDays: Int,
    val catchCount: Int,
)

data class RecordCalendarYearStats(
    val recordDays: Int,
    val catchCount: Int,
    val averagePerMonth: Float,
)

/** Groups every record under its resolved natural day key (newest first). */
fun buildRecordDayMap(catches: List<RemoteCatch>): Map<String, List<RemoteCatch>> {
    return catches
        .groupBy { record -> resolveCatchTimestamp(record).takeIf { it.isKnown }?.dayKey }
        .filterKeys { key -> key != null && key != "unknown" }
        .mapKeys { (key, _) -> requireNotNull(key) }
        .mapValues { (_, dayCatches) ->
            dayCatches.sortedWith(
                compareByDescending<RemoteCatch> { resolveCatchTimestamp(it).millis ?: 0L }
                    .thenBy { it.id },
            )
        }
}

/**
 * Monday-first month grid padded to whole weeks (at least 5, at most 6 rows),
 * matching the frozen record-date V2 month view.
 */
fun buildMonthCells(year: Int, month: Int): List<RecordCalendarCell> {
    val calendar = Calendar.getInstance().apply {
        clear()
        set(year, month - 1, 1)
    }
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val leading = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday == 0

    val previous = Calendar.getInstance().apply {
        clear()
        set(year, month - 1, 1)
        add(Calendar.MONTH, -1)
    }
    val previousDays = previous.getActualMaximum(Calendar.DAY_OF_MONTH)
    val previousMonth = previous.get(Calendar.MONTH) + 1
    val previousYear = previous.get(Calendar.YEAR)

    val cells = mutableListOf<RecordCalendarCell>()
    for (offset in 1..leading) {
        val day = previousDays - leading + offset
        cells += RecordCalendarCell(previousYear, previousMonth, day, inMonth = false)
    }
    for (day in 1..daysInMonth) {
        cells += RecordCalendarCell(year, month, day, inMonth = true)
    }
    while (cells.size % 7 != 0 || cells.size < 35) {
        val next = cells.size - leading - daysInMonth + 1
        val nextCalendar = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, 1)
            add(Calendar.MONTH, 1)
        }
        cells += RecordCalendarCell(
            nextCalendar.get(Calendar.YEAR),
            nextCalendar.get(Calendar.MONTH) + 1,
            next,
            inMonth = false,
        )
    }
    return cells
}

/** Consecutive record days ending today, or yesterday when today has none. */
fun resolveStreakDays(dayMap: Map<String, List<RemoteCatch>>, today: Calendar): Int {
    var streak = 0
    val cursor = today.clone() as Calendar
    if (!dayMap.containsKey(calendarDayKey(cursor))) cursor.add(Calendar.DAY_OF_MONTH, -1)
    if (!dayMap.containsKey(calendarDayKey(cursor))) return 0
    while (dayMap.containsKey(calendarDayKey(cursor))) {
        streak++
        cursor.add(Calendar.DAY_OF_MONTH, -1)
    }
    return streak
}

fun resolveMonthStats(
    dayMap: Map<String, List<RemoteCatch>>,
    year: Int,
    month: Int,
    today: Calendar,
): RecordCalendarMonthStats {
    val todayKey = calendarDayKey(today)
    val weekStart = (today.clone() as Calendar).apply {
        val weekdayOffset = (get(Calendar.DAY_OF_WEEK) + 5) % 7 // back to Monday
        add(Calendar.DAY_OF_MONTH, -weekdayOffset)
    }
    var weekDays = 0
    for (dayOffset in 0 until 7) {
        val key = calendarDayKey(weekStart.apply { add(Calendar.DAY_OF_MONTH, if (dayOffset == 0) 0 else 1) })
        if (dayMap.containsKey(key)) weekDays++
    }
    val monthPrefix = "%04d-%02d".format(Locale.US, year, month)
    val monthDays = dayMap.keys.count { it.startsWith(monthPrefix) }
    return RecordCalendarMonthStats(
        streakDays = resolveStreakDays(dayMap, today),
        weekDays = weekDays,
        monthDays = monthDays,
    )
}

fun resolveYearMonthStats(
    dayMap: Map<String, List<RemoteCatch>>,
    year: Int,
): List<RecordCalendarYearMonthStats> {
    return (1..12).map { month ->
        val prefix = "%04d-%02d".format(Locale.US, year, month)
        val monthKeys = dayMap.keys.filter { it.startsWith(prefix) }
        RecordCalendarYearMonthStats(
            month = month,
            recordDays = monthKeys.size,
            catchCount = monthKeys.sumOf { dayMap.getValue(it).size },
        )
    }
}

fun resolveYearStats(
    dayMap: Map<String, List<RemoteCatch>>,
    year: Int,
    today: Calendar,
): RecordCalendarYearStats {
    val prefix = "%04d".format(Locale.US, year)
    val yearKeys = dayMap.keys.filter { it.startsWith(prefix) }
    val catchCount = yearKeys.sumOf { dayMap.getValue(it).size }
    val elapsedMonths = if (today.get(Calendar.YEAR) > year) {
        12
    } else if (today.get(Calendar.YEAR) == year) {
        today.get(Calendar.MONTH) + 1
    } else {
        1
    }
    return RecordCalendarYearStats(
        recordDays = yearKeys.size,
        catchCount = catchCount,
        averagePerMonth = if (elapsedMonths > 0) catchCount.toFloat() / elapsedMonths else 0f,
    )
}

/** Clamps a carried-over day selection when the cursor month changes. */
fun clampDayToMonth(year: Int, month: Int, day: Int): Int {
    val calendar = Calendar.getInstance().apply {
        clear()
        set(year, month - 1, 1)
    }
    return day.coerceIn(1, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
}

fun calendarDayKey(calendar: Calendar): String = "%04d-%02d-%02d".format(
    Locale.US,
    calendar.get(Calendar.YEAR),
    calendar.get(Calendar.MONTH) + 1,
    calendar.get(Calendar.DAY_OF_MONTH),
)

fun weekdayLabel(year: Int, month: Int, day: Int): String {
    val calendar = Calendar.getInstance().apply {
        clear()
        set(year, month - 1, day)
    }
    return listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")[calendar.get(Calendar.DAY_OF_WEEK) - 1]
}

fun timeLabel(record: RemoteCatch): String {
    val millis = resolveCatchTimestamp(record).millis ?: return "--:--"
    val calendar = Calendar.getInstance().apply { timeInMillis = millis }
    return "%02d:%02d".format(
        Locale.US,
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
    )
}
