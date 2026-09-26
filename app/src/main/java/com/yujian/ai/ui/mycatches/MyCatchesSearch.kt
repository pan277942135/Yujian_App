package com.yujian.ai.ui.mycatches

import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.presentation.sanitizeOptionalText

fun normalizedCatchQuery(query: String): String = query.trim().lowercase()

fun matchesCatchSearch(record: RemoteCatch, query: String): Boolean {
    val normalized = normalizedCatchQuery(query)
    if (normalized.isBlank()) return true
    return record.speciesName.lowercase().contains(normalized) ||
        sanitizeOptionalText(record.location).orEmpty().lowercase().contains(normalized)
}
