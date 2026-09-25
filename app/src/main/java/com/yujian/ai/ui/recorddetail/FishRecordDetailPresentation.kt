package com.yujian.ai.ui.recorddetail

import com.yujian.ai.catches.RemoteCatch
import java.util.Locale

/** Pure presentation decisions kept separate from Compose for deterministic tests. */
object FishRecordDetailPresentation {
    fun resolve(
        catchId: String,
        records: List<RemoteCatch>,
        loading: Boolean,
        error: String?,
    ): FishRecordDetailUiState {
        records.firstOrNull { it.id == catchId }?.let { return FishRecordDetailUiState.Success(it) }
        if (loading) return FishRecordDetailUiState.Loading
        error?.takeIf(String::isNotBlank)?.let { return FishRecordDetailUiState.Error(it) }
        return FishRecordDetailUiState.Empty
    }

    fun measurement(record: RemoteCatch): String? = listOfNotNull(
        record.lengthCm?.takeIf { it > 0f }?.let { "${formatNumber(it)} cm" },
        record.weightKg?.takeIf { it > 0f }?.let { "${formatNumber(it)} kg" },
    ).joinToString(" · ").takeIf(String::isNotBlank)

    fun location(record: RemoteCatch): String? = record.location?.trim()?.takeIf(String::isNotBlank)

    fun capturedAt(record: RemoteCatch): String? = record.capturedAt.trim()
        .takeIf(String::isNotBlank)
        ?: record.createdAt.trim().takeIf(String::isNotBlank)

    fun mediaUrls(record: RemoteCatch): List<String> = listOfNotNull(
        record.imageUrl.trim().takeIf(String::isNotBlank),
    )

    private fun formatNumber(value: Float): String = "%.2f".format(Locale.US, value)
        .trimEnd('0')
        .trimEnd('.')
}
