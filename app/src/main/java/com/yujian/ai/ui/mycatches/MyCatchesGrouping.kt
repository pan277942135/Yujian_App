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
                    .sortedWith(compareByDescending<MyCatchesDayGroup> { it.key != "unknown" }.thenByDescending { it.key }),
            )
        }
        .sortedWith(compareByDescending<MyCatchesMonthGroup> { it.key != "unknown" }.thenByDescending { it.key })
}

fun daySummary(catches: List<RemoteCatch>): String {
    val countLabel = "${catches.size}条鱼获 · ${daySpeciesCount(catches)}种鱼"
    return sharedDayLocation(catches)?.let { "$it · $countLabel" } ?: countLabel
}

/** A shared location is shown only when every record has the same usable value. */
fun sharedDayLocation(catches: List<RemoteCatch>): String? {
    if (catches.isEmpty()) return null
    val locations = catches.map { sanitizeOptionalText(it.location) }
    if (locations.any { it == null }) return null
    val usable = locations.filterNotNull()
    if (usable.map { it.lowercase(Locale.ROOT) }.distinct().size != 1) return null
    return usable.first()
}

fun daySpeciesCount(catches: List<RemoteCatch>): Int = catches
    .map { it.speciesKey().trim() }
    .filter(String::isNotEmpty)
    .distinct()
    .size

/** Counts only distinct days backed by a parseable catch timestamp. */
fun archiveRecordDayCount(catches: List<RemoteCatch>): Int = catches
    .mapNotNull { record -> resolveCatchTimestamp(record).takeIf { it.isKnown }?.dayKey }
    .distinct()
    .size

fun filterAndSortCatches(
    catches: List<RemoteCatch>,
    query: String,
    filter: MyCatchesFilterState,
    nowMillis: Long = System.currentTimeMillis(),
): List<RemoteCatch> = sortCatchesNewestFirst(
    catches.let { allCatches ->
        val marks = GrowthMarkResolver.resolve(allCatches)
        allCatches.filter { record ->
            matchesCatchSearch(record, query, marks[record.id].orEmpty()) &&
                matchesCatchFilter(record, filter, marks[record.id].orEmpty(), nowMillis)
        }
    },
)

private fun matchesCatchFilter(
    record: RemoteCatch,
    filter: MyCatchesFilterState,
    marks: List<GrowthMark>,
    nowMillis: Long,
): Boolean {
    val timestamp = resolveCatchTimestamp(record)
    if (filter.speciesIds.isNotEmpty() && record.speciesKey() !in filter.speciesIds) return false
    if (filter.specialMarks.isNotEmpty() && filter.specialMarks.none { special ->
            marks.any { mark ->
                when (special) {
                    SpecialCatchMark.FirstSpecies -> mark.type == GrowthMarkType.FirstSpecies
                    SpecialCatchMark.Longest -> mark.type == GrowthMarkType.Longest
                    SpecialCatchMark.Heaviest -> mark.type == GrowthMarkType.Heaviest
                    SpecialCatchMark.Milestone -> mark.type == GrowthMarkType.CountMilestone
                }
            }
        }
    ) return false

    val length = record.lengthCm?.takeIf { it.isFinite() && it > 0f }
    if (filter.lengthRange != CatchLengthRange.All && !matchesRange(length, filter.lengthRange.min, filter.lengthRange.max)) return false
    if (filter.customLengthMinCm != null || filter.customLengthMaxCm != null) {
        if (!matchesCustomRange(length, filter.customLengthMinCm, filter.customLengthMaxCm)) return false
    }
    val weight = record.weightKg?.takeIf { it.isFinite() && it > 0f }
    if (filter.weightRange != CatchWeightRange.All && !matchesRange(weight, filter.weightRange.min, filter.weightRange.max)) return false
    if (filter.customWeightMinKg != null || filter.customWeightMaxKg != null) {
        if (!matchesCustomRange(weight, filter.customWeightMinKg, filter.customWeightMaxKg)) return false
    }

    val date = timestamp.takeIf { it.isKnown }?.let { "%04d-%02d-%02d".format(Locale.US, it.year, it.month, it.day) }
    return when (filter.timeRange) {
        CatchTimeRange.All -> true
        CatchTimeRange.ThisMonth -> {
            val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
            timestamp.millis?.let { it <= nowMillis } == true &&
                now.get(Calendar.YEAR) == timestamp.year && now.get(Calendar.MONTH) + 1 == timestamp.month
        }
        CatchTimeRange.Last3Months -> {
            val cutoff = Calendar.getInstance().apply { timeInMillis = nowMillis; add(Calendar.MONTH, -3) }.timeInMillis
            timestamp.millis?.let { it >= cutoff && it <= nowMillis } == true
        }
        CatchTimeRange.ThisYear -> {
            timestamp.isKnown && Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.YEAR) == timestamp.year
        }
        CatchTimeRange.Custom -> {
            val start = filter.customStartDate?.takeIf(::isValidCatchDate)
            val end = filter.customEndDate?.takeIf(::isValidCatchDate)
            date != null && (start == null || date >= start) && (end == null || date <= end) && (start != null || end != null)
        }
    }
}

private fun matchesRange(value: Float?, min: Float?, max: Float?): Boolean =
    value != null && (min == null || value >= min) && (max == null || value < max)

private fun matchesCustomRange(value: Float?, min: Float?, max: Float?): Boolean =
    value != null && (min == null || value >= min) && (max == null || value <= max)

fun isValidCatchDate(value: String): Boolean {
    if (!value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return false
    val parts = value.split('-').map { it.toInt() }
    return runCatching {
        Calendar.getInstance().apply {
            isLenient = false
            clear()
            set(parts[0], parts[1] - 1, parts[2], 0, 0, 0)
        }.time
    }.isSuccess
}
