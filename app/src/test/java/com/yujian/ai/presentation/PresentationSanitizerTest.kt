package com.yujian.ai.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class PresentationSanitizerTest {
    @Test
    fun optionalText_contractRemovesOnlyKnownSentinels() {
        listOf(null, "", " ", "null", " NULL ", "none", "undefined", "N/A", "-").forEach {
            assertNull(PresentationSanitizer.sanitizeOptionalText(it))
        }
        assertEquals("千岛湖", PresentationSanitizer.sanitizeOptionalText(" 千岛湖 "))
        assertEquals("none-ish", PresentationSanitizer.sanitizeOptionalText("none-ish"))
    }

    @Test
    fun timestamp_contractUsesCreatedAtWhenCapturedAtIsInvalid() {
        val resolved = PresentationSanitizer.resolveTimestamp(
            capturedAt = "not-a-timestamp",
            createdAt = "2026-09-25T20:07:19+08:00",
        )

        assertTrue(resolved.isValid)
        assertEquals(PresentationSanitizer.TimestampSource.CreatedAt, resolved.source)
        assertEquals("2026年9月", PresentationSanitizer.formatMonthLabel("not-a-timestamp", "2026-09-25T20:07:19+08:00"))
    }

    @Test
    fun invalidTimestampsHaveNoEpochFallbackOrRawIsoOutput() {
        val resolved = PresentationSanitizer.resolveTimestamp("also-invalid", "null")

        assertEquals(null, resolved.date)
        assertNull(PresentationSanitizer.formatDetailTimestamp("also-invalid", "null"))
        assertNull(PresentationSanitizer.dateKey("also-invalid", "null"))
        assertTrue("1970 must never be used as a missing timestamp", resolved.millis == null)
    }

    @Test
    fun homeFormattingIsHumanReadable() {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        val now = format.parse("2026-09-25T20:07:19+08:00")!!.time

        assertEquals(
            "今天 20:07",
            PresentationSanitizer.formatHomeTimestamp("2026-09-25T20:07:19+08:00", null, now),
        )
        assertEquals("09月25日", PresentationSanitizer.formatDayLabel("2026-09-25T20:07:19+08:00", null))
    }
}
