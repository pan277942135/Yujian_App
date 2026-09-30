package com.yujian.ai.ui.recorddetail

import com.yujian.ai.catches.BsideStatus
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.presentation.PresentationSanitizer
import com.yujian.ai.presentation.sanitizeOptionalText
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
        if (!error.isNullOrBlank()) {
            // Repository/server details are intentionally kept out of user-facing copy.
            return FishRecordDetailUiState.Error("暂时无法打开这条鱼获")
        }
        return FishRecordDetailUiState.Empty
    }

    fun measurement(record: RemoteCatch): String? = listOfNotNull(
        record.lengthCm?.takeIf { it.isFinite() && it > 0f }?.let { "${formatNumber(it)} cm" },
        record.weightKg?.takeIf { it.isFinite() && it > 0f }?.let { "${formatNumber(it)} kg" },
    ).joinToString(" · ").takeIf(String::isNotBlank)

    fun location(record: RemoteCatch): String? = sanitizeOptionalText(record.location)
        ?.replace(Regex("\\s*·\\s*"), " · ")

    fun capturedAt(record: RemoteCatch): String? =
        PresentationSanitizer.formatDetailTimestamp(record.capturedAt, record.createdAt)

    fun shouldAutoRevealBside(
        status: BsideStatus,
        hasAsset: Boolean,
        firstRevealDone: Boolean,
    ): Boolean = status == BsideStatus.READY && hasAsset && !firstRevealDone

    private fun formatNumber(value: Float): String = "%.2f".format(Locale.US, value)
        .trimEnd('0')
        .trimEnd('.')
}
