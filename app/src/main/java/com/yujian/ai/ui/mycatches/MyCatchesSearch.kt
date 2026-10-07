package com.yujian.ai.ui.mycatches

import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.presentation.sanitizeOptionalText
import java.util.Locale

fun normalizedCatchQuery(query: String): String = query.replace('\u3000', ' ').trim().lowercase(Locale.ROOT)

fun matchesCatchSearch(record: RemoteCatch, query: String, marks: List<GrowthMark> = emptyList()): Boolean {
    val terms = normalizedCatchQuery(query).split(Regex("\\s+")).filter(String::isNotBlank)
    if (terms.isEmpty()) return true

    val timestamp = resolveCatchTimestamp(record)
    val dateFields = if (timestamp.isKnown) {
        listOf(
            "%04d-%02d-%02d".format(Locale.US, timestamp.year, timestamp.month, timestamp.day),
            "%04d年%d月".format(Locale.CHINA, timestamp.year, timestamp.month),
            "%d月%d日".format(Locale.CHINA, timestamp.month, timestamp.day),
            timestamp.formattedDayLabel,
            timestamp.formattedMonthLabel,
        )
    } else emptyList()
    val searchable = buildList {
        add(record.speciesName)
        sanitizeOptionalText(record.location)?.let(::add)
        addAll(dateFields)
        addAll(marks.map { it.text })
    }.map { it.lowercase(Locale.ROOT) }

    return terms.all { term -> searchable.any { field -> field.contains(term) } }
}

fun commitRecentSearch(recent: List<String>, query: String, limit: Int = 6): List<String> {
    val cap = limit.coerceAtLeast(0)
    val clean = query.trim().takeIf(String::isNotBlank) ?: return recent.take(cap)
    return (listOf(clean) + recent.map(String::trim).filter(String::isNotBlank).filterNot { it.equals(clean, ignoreCase = true) })
        .take(cap)
}
