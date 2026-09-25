package com.yujian.ai.presentation

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Shared boundary between backend values and user-visible presentation text. */
object PresentationSanitizer {
    private val optionalTextSentinels = setOf("null", "none", "undefined", "n/a", "-")
    private val timestampPatterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mmXXX",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
    )

    fun sanitizeOptionalText(value: String?): String? {
        val trimmed = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return trimmed.takeUnless { it.lowercase(Locale.ROOT) in optionalTextSentinels }
    }

    enum class TimestampSource { CapturedAt, CreatedAt, Missing }

    data class ResolvedTimestamp(val date: Date?, val source: TimestampSource) {
        val millis: Long? get() = date?.time
        val isValid: Boolean get() = date != null
    }

    /** capturedAt wins only when it is actually parseable; createdAt is the fallback. */
    fun resolveTimestamp(capturedAt: String?, createdAt: String?): ResolvedTimestamp {
        parseTimestamp(capturedAt)?.let { return ResolvedTimestamp(it, TimestampSource.CapturedAt) }
        parseTimestamp(createdAt)?.let { return ResolvedTimestamp(it, TimestampSource.CreatedAt) }
        return ResolvedTimestamp(null, TimestampSource.Missing)
    }

    fun formatHomeTimestamp(
        capturedAt: String?,
        createdAt: String?,
        nowMillis: Long = System.currentTimeMillis(),
    ): String? = resolveTimestamp(capturedAt, createdAt).date?.let { date ->
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val target = Calendar.getInstance().apply { time = date }
        val time = SimpleDateFormat("HH:mm", Locale.CHINA).format(date)
        when {
            sameDay(now, target) -> "今天 $time"
            isYesterday(now, target) -> "昨天 $time"
            else -> SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(date)
        }
    }

    fun formatDayLabel(capturedAt: String?, createdAt: String?): String? =
        resolveTimestamp(capturedAt, createdAt).date?.let { SimpleDateFormat("MM月dd日", Locale.CHINA).format(it) }

    fun formatMonthLabel(capturedAt: String?, createdAt: String?): String? =
        resolveTimestamp(capturedAt, createdAt).date?.let { SimpleDateFormat("yyyy年M月", Locale.CHINA).format(it) }

    fun formatDetailTimestamp(capturedAt: String?, createdAt: String?): String? =
        formatHomeTimestamp(capturedAt, createdAt)

    fun dateKey(capturedAt: String?, createdAt: String?): String? =
        resolveTimestamp(capturedAt, createdAt).date?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(it) }

    fun monthKey(capturedAt: String?, createdAt: String?): String? =
        resolveTimestamp(capturedAt, createdAt).date?.let { SimpleDateFormat("yyyy-MM", Locale.US).format(it) }

    private fun parseTimestamp(value: String?): Date? {
        val normalized = sanitizeOptionalText(value) ?: return null
        return timestampPatterns.firstNotNullOfOrNull { pattern ->
            val formatter = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
            val position = ParsePosition(0)
            formatter.parse(normalized, position)?.takeIf { position.index == normalized.length }
        }
    }

    private fun sameDay(first: Calendar, second: Calendar): Boolean =
        first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)

    private fun isYesterday(now: Calendar, target: Calendar): Boolean {
        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        return sameDay(yesterday, target)
    }
}

fun sanitizeOptionalText(value: String?): String? =
    PresentationSanitizer.sanitizeOptionalText(value)

fun presentationSpeciesName(value: String): String =
    sanitizeOptionalText(value) ?: "鱼获"
