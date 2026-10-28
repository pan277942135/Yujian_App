package com.yujian.ai.ui.recorddetail

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class FishRecordEditTimePresentation(
    val dateTime: String,
    val timeZoneLabel: String,
)

/** Converts stored ISO timestamps to a readable value while retaining their original UTC offset. */
object FishRecordEditTime {
    private val storedTimePattern = Regex(
        "^(\\d{4}-\\d{2}-\\d{2})T(\\d{2}):(\\d{2})(?::(\\d{2})(\\.\\d+)?)?(Z|[+-]\\d{2}:\\d{2})$",
    )
    private const val MaxFutureSkewMillis = 5 * 60 * 1000L

    private data class ParsedTime(
        val instant: Date,
        val timeZone: TimeZone,
        val offsetSuffix: String,
        val fractionalSeconds: String,
        val second: Int,
        val millisecond: Int,
    )

    fun presentation(value: String): FishRecordEditTimePresentation? {
        val parsed = parse(value) ?: return null
        val formatter = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).apply {
            timeZone = parsed.timeZone
            isLenient = false
        }
        return FishRecordEditTimePresentation(
            dateTime = formatter.format(parsed.instant),
            timeZoneLabel = if (parsed.offsetSuffix == "Z") "UTC" else "UTC${parsed.offsetSuffix}",
        )
    }

    fun calendar(value: String, now: Date = Date()): Calendar {
        val parsed = parse(value)
        return Calendar.getInstance(parsed?.timeZone ?: TimeZone.getDefault()).apply {
            time = parsed?.instant ?: now
        }
    }

    fun withDateAndTime(
        originalValue: String,
        year: Int,
        month: Int,
        dayOfMonth: Int,
        hourOfDay: Int,
        minute: Int,
    ): String? {
        val existing = parse(originalValue)
        val timeZone = existing?.timeZone ?: TimeZone.getDefault()
        val calendar = Calendar.getInstance(timeZone).apply {
            isLenient = false
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, existing?.second ?: 0)
            set(Calendar.MILLISECOND, existing?.millisecond ?: 0)
        }
        val selected = runCatching { calendar.time }.getOrNull() ?: return null
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            this.timeZone = timeZone
            isLenient = false
        }
        val offset = existing?.offsetSuffix ?: offsetFor(timeZone, selected)
        return formatter.format(selected) + existing?.fractionalSeconds.orEmpty() + offset
    }

    fun isValid(value: String, now: Date = Date()): Boolean =
        parse(value)?.instant?.time?.let { it <= now.time + MaxFutureSkewMillis } == true

    private fun parse(value: String): ParsedTime? {
        val match = storedTimePattern.matchEntire(value) ?: return null
        val date = match.groupValues[1]
        val hour = match.groupValues[2].toIntOrNull() ?: return null
        val minute = match.groupValues[3].toIntOrNull() ?: return null
        val second = match.groupValues[4].takeIf(String::isNotEmpty)?.toIntOrNull() ?: 0
        val fraction = match.groupValues[5]
        val offset = match.groupValues[6]
        if (hour !in 0..23 || minute !in 0..59 || second !in 0..59 || !validOffset(offset)) return null

        val timeZone = TimeZone.getTimeZone(if (offset == "Z") "UTC" else "GMT$offset")
        val millis = fraction.removePrefix(".").take(3).padEnd(3, '0').toIntOrNull() ?: 0
        val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            this.timeZone = timeZone
            isLenient = false
        }
        val localDateTime = "$date ${match.groupValues[2]}:${match.groupValues[3]}:${second.toString().padStart(2, '0')}"
        val position = ParsePosition(0)
        val instant = parser.parse(localDateTime, position) ?: return null
        if (position.errorIndex >= 0 || position.index != localDateTime.length) {
            return null
        }
        instant.time += millis
        return ParsedTime(instant, timeZone, offset, fraction, second, millis)
    }

    private fun validOffset(value: String): Boolean {
        if (value == "Z") return true
        val hours = value.substring(1, 3).toIntOrNull() ?: return false
        val minutes = value.substring(4, 6).toIntOrNull() ?: return false
        return hours <= 18 && minutes <= 59 && (hours < 18 || minutes == 0)
    }

    private fun offsetFor(timeZone: TimeZone, date: Date): String {
        val offsetMinutes = timeZone.getOffset(date.time) / 60_000
        if (offsetMinutes == 0) return "Z"
        val sign = if (offsetMinutes < 0) "-" else "+"
        val absoluteMinutes = kotlin.math.abs(offsetMinutes)
        val hours = absoluteMinutes / 60
        val minutes = absoluteMinutes % 60
        return "$sign${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
    }
}
