package com.yujian.ai.ui.recorddetail

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class FishRecordEditTimeTest {
    @Test
    fun timestampUsesItsStoredOffsetForHumanReadableDisplay() {
        val presentation = FishRecordEditTime.presentation("2026-10-07T12:34:56.789+08:00")

        assertNotNull(presentation)
        assertEquals("2026年10月7日 12:34", presentation?.dateTime)
        assertEquals("UTC+08:00", presentation?.timeZoneLabel)
    }

    @Test
    fun editingRetainsStoredOffsetAndFractionalSeconds() {
        val changed = FishRecordEditTime.withDateAndTime(
            originalValue = "2026-10-07T12:34:56.789+08:00",
            year = 2026,
            month = 9,
            dayOfMonth = 8,
            hourOfDay = 15,
            minute = 6,
        )

        assertEquals("2026-10-08T15:06:56.789+08:00", changed)
        assertEquals("2026年10月8日 15:06", FishRecordEditTime.presentation(changed.orEmpty())?.dateTime)
    }

    @Test
    fun editingRetainsZuluTimezone() {
        val changed = FishRecordEditTime.withDateAndTime(
            originalValue = "2026-10-07T12:34:56Z",
            year = 2026,
            month = 9,
            dayOfMonth = 7,
            hourOfDay = 10,
            minute = 5,
        )

        assertEquals("2026-10-07T10:05:56Z", changed)
        assertEquals("UTC", FishRecordEditTime.presentation(changed.orEmpty())?.timeZoneLabel)
    }

    @Test
    fun invalidAndFarFutureTimestampsAreRejected() {
        val now = Date(1_790_000_000_000L)

        assertFalse(FishRecordEditTime.isValid("2026-02-30T12:00:00+08:00", now))
        assertFalse(FishRecordEditTime.isValid("2035-01-01T12:00:00Z", now))
    }
}
