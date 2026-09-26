package com.yujian.ai.presentation

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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

    data class ResolvedTimestamp(
        val date: Date?,
        val source: TimestampSource,
        val timeZone: TimeZone = TimeZone.getDefault(),
    ) {
        val millis: Long? get() = date?.time
        val isValid: Boolean get() = date != null
    }

    private data class ParsedTimestamp(val date: Date, val timeZone: TimeZone)

    /** capturedAt wins only when it is actually parseable; createdAt is the fallback. */
    fun resolveTimestamp(capturedAt: String?, createdAt: String?): ResolvedTimestamp {
        parseTimestamp(capturedAt)?.let {
            return ResolvedTimestamp(it.date, TimestampSource.CapturedAt, it.timeZone)
        }
        parseTimestamp(createdAt)?.let {
            return ResolvedTimestamp(it.date, TimestampSource.CreatedAt, it.timeZone)
        }
        return ResolvedTimestamp(null, TimestampSource.Missing)
    }

    fun formatHomeTimestamp(
        capturedAt: String?,
        createdAt: String?,
        nowMillis: Long = System.currentTimeMillis(),
    ): String? {
        val resolved = resolveTimestamp(capturedAt, createdAt)
        val date = resolved.date ?: return null
        val zone = resolved.timeZone
        val now = Calendar.getInstance(zone).apply { timeInMillis = nowMillis }
        val target = Calendar.getInstance(zone).apply { time = date }
        val time = formatter("HH:mm", Locale.CHINA, zone).format(date)
        return when {
            sameDay(now, target) -> "今天 $time"
            isYesterday(now, target) -> "昨天 $time"
            else -> formatter("MM月dd日 HH:mm", Locale.CHINA, zone).format(date)
        }
    }

    fun formatDayLabel(capturedAt: String?, createdAt: String?): String? {
        val resolved = resolveTimestamp(capturedAt, createdAt)
        val date = resolved.date ?: return null
        return formatter("MM月dd日", Locale.CHINA, resolved.timeZone).format(date)
    }

    fun formatMonthLabel(capturedAt: String?, createdAt: String?): String? {
        val resolved = resolveTimestamp(capturedAt, createdAt)
        val date = resolved.date ?: return null
        return formatter("yyyy年M月", Locale.CHINA, resolved.timeZone).format(date)
    }

    fun formatDetailTimestamp(capturedAt: String?, createdAt: String?): String? =
        formatHomeTimestamp(capturedAt, createdAt)

    fun dateKey(capturedAt: String?, createdAt: String?): String? {
        val resolved = resolveTimestamp(capturedAt, createdAt)
        val date = resolved.date ?: return null
        return formatter("yyyy-MM-dd", Locale.US, resolved.timeZone).format(date)
    }

    fun monthKey(capturedAt: String?, createdAt: String?): String? {
        val resolved = resolveTimestamp(capturedAt, createdAt)
        val date = resolved.date ?: return null
        return formatter("yyyy-MM", Locale.US, resolved.timeZone).format(date)
    }

    private fun parseTimestamp(value: String?): ParsedTimestamp? {
        val normalized = sanitizeOptionalText(value) ?: return null
        val zone = timestampTimeZone(normalized)
        return timestampPatterns.firstNotNullOfOrNull { pattern ->
            val formatter = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = zone
            }
            val position = ParsePosition(0)
            formatter.parse(normalized, position)
                ?.takeIf { position.index == normalized.length }
                ?.let { ParsedTimestamp(it, zone) }
        }
    }

    private fun timestampTimeZone(value: String): TimeZone {
        if (value.endsWith("Z", ignoreCase = true)) return TimeZone.getTimeZone("UTC")
        val offset = Regex("""([+-])(\d{2}):?(\d{2})$""").find(value)
        return if (offset != null) {
            val (_, sign, hour, minute) = offset.groupValues
            TimeZone.getTimeZone("GMT$sign$hour:$minute")
        } else {
            TimeZone.getDefault()
        }
    }

    private fun formatter(pattern: String, locale: Locale, zone: TimeZone) =
        SimpleDateFormat(pattern, locale).apply { timeZone = zone }

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
