package com.yujian.ai.ui.mycatches

import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.presentation.PresentationSanitizer
import com.yujian.ai.presentation.sanitizeOptionalText
import java.util.Calendar
import java.util.Locale

data class CatchTimestamp(
    val value: String?,
    val millis: Long?,
    val year: Int,
    val month: Int,
    val day: Int,
    val isKnown: Boolean,
    val formattedMonthLabel: String,
    val formattedDayLabel: String,
) {
    val monthKey: String get() = formattedMonthLabel.takeIf { isKnown }?.let {
        "%04d-%02d".format(Locale.US, year, month)
    } ?: "unknown"
    val monthLabel: String get() = formattedMonthLabel
    val dayKey: String get() = formattedDayLabel.takeIf { isKnown }?.let {
        "%04d-%02d-%02d".format(Locale.US, year, month, day)
    } ?: "unknown"
    val dayLabel: String get() = formattedDayLabel
}

/** One canonical timestamp for every page concern: sorting, grouping and display. */
fun resolveCatchTimestamp(record: RemoteCatch): CatchTimestamp =
    resolveCatchTimestamp(record.capturedAt, record.createdAt)

fun resolveCatchTimestamp(capturedAt: String?, createdAt: String?): CatchTimestamp {
    val resolved = PresentationSanitizer.resolveTimestamp(capturedAt, createdAt)
    val calendar = Calendar.getInstance().apply { timeInMillis = resolved.millis ?: 0L }
    return CatchTimestamp(
        value = resolved.source.name.takeIf { resolved.isValid },
        millis = resolved.millis,
        year = calendar.get(Calendar.YEAR),
        month = calendar.get(Calendar.MONTH) + 1,
        day = calendar.get(Calendar.DAY_OF_MONTH),
        isKnown = resolved.isValid,
        formattedMonthLabel = PresentationSanitizer.formatMonthLabel(capturedAt, createdAt) ?: "时间未知",
        formattedDayLabel = PresentationSanitizer.formatDayLabel(capturedAt, createdAt) ?: "日期未知",
    )
}

data class MyCatchesDayGroup(
    val key: String,
    val label: String,
    val summary: String,
    val catches: List<RemoteCatch>,
)

data class MyCatchesMonthGroup(
    val key: String,
    val label: String,
    val days: List<MyCatchesDayGroup>,
)

fun sortCatchesNewestFirst(catches: List<RemoteCatch>): List<RemoteCatch> =
    catches.sortedWith(
        compareByDescending<RemoteCatch> { resolveCatchTimestamp(it).millis ?: Long.MIN_VALUE }
            .thenBy { it.id },
    )

fun groupCatchesByMonthAndDay(catches: List<RemoteCatch>): List<MyCatchesMonthGroup> {
    val sorted = sortCatchesNewestFirst(catches)
    return sorted.groupBy { resolveCatchTimestamp(it).monthKey }
        .map { (_, monthCatches) ->
            val monthTimestamp = resolveCatchTimestamp(monthCatches.first())
            MyCatchesMonthGroup(
                key = monthTimestamp.monthKey,
                label = monthTimestamp.monthLabel,
                days = monthCatches.groupBy { resolveCatchTimestamp(it).dayKey }
                    .map { (_, dayCatches) ->
                        val dayTimestamp = resolveCatchTimestamp(dayCatches.first())
                        MyCatchesDayGroup(
                            key = dayTimestamp.dayKey,
                            label = dayTimestamp.dayLabel,
                            summary = daySummary(dayCatches),
                            catches = sortCatchesNewestFirst(dayCatches),
                        )
                    }
                    .sortedByDescending { it.key },
            )
        }
        .sortedByDescending { it.key }
}

fun daySummary(catches: List<RemoteCatch>): String {
    val locations = catches.mapNotNull { sanitizeOptionalText(it.location) }.distinct()
    val countLabel = "${catches.size}条鱼获"
    return if (locations.size == 1) "${locations.first()} · $countLabel" else countLabel
}

fun filterAndSortCatches(
    catches: List<RemoteCatch>,
    query: String,
    filter: MyCatchesFilterState,
    nowMillis: Long = System.currentTimeMillis(),
): List<RemoteCatch> = sortCatchesNewestFirst(
    catches.filter { record ->
        matchesCatchSearch(record, query) && matchesCatchFilter(record, filter, nowMillis)
    },
)

private fun matchesCatchFilter(record: RemoteCatch, filter: MyCatchesFilterState, nowMillis: Long): Boolean {
    val timestamp = resolveCatchTimestamp(record)
    val speciesKey = record.speciesId.ifBlank { record.speciesName }
    if (filter.speciesIds.isNotEmpty() && speciesKey !in filter.speciesIds) return false
    if (filter.locations.isNotEmpty() && sanitizeOptionalText(record.location) !in filter.locations) return false
    return when (filter.timeRange) {
        CatchTimeRange.All -> true
        CatchTimeRange.Last7Days -> timestamp.millis?.let { it >= nowMillis - 7L * 24 * 60 * 60 * 1_000 } == true
        CatchTimeRange.Last30Days -> timestamp.millis?.let { it >= nowMillis - 30L * 24 * 60 * 60 * 1_000 } == true
        CatchTimeRange.ThisYear -> {
            timestamp.isKnown && Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.YEAR) == timestamp.year
        }
    }
}
