package com.yujian.ai.ui.home

import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeStatsTest {
    @Test
    fun serverTotalsRemainAuthoritativeWhileRecordDaysComeFromRealRecords() {
        val values = resolveHomeStatValues(
            statistics = CatchStatistics(totalCatches = 38, speciesCount = 12),
            catches = listOf(
                catchRecord("a", "草鱼", "2026-09-22T18:20:00+08:00"),
                catchRecord("b", "鲫鱼", "2026-09-23T08:10:00+08:00"),
                catchRecord("c", "草鱼", "2026-09-23T19:10:00+08:00"),
            ),
        )

        assertEquals(HomeStatValues(speciesCount = 12, catchCount = 38, recordDays = 2), values)
    }

    @Test
    fun guestAndOfflineArchivesFallBackToTheirLocalRecords() {
        val values = resolveHomeStatValues(
            statistics = CatchStatistics(),
            catches = listOf(
                catchRecord("a", "草鱼", "2026-09-22T18:20:00+08:00"),
                catchRecord("b", "鲫鱼", "2026-09-23T08:10:00+08:00"),
                catchRecord("c", "草鱼", "2026-09-23T19:10:00+08:00"),
            ),
        )

        assertEquals(HomeStatValues(speciesCount = 2, catchCount = 3, recordDays = 2), values)
    }

    @Test
    fun datePrefixStillCountsWhenTheServerUsesAnUnknownTimestampSuffix() {
        val values = resolveHomeStatValues(
            statistics = CatchStatistics(),
            catches = listOf(
                catchRecord("a", "草鱼", "2026-09-24 legacy"),
                catchRecord("b", "草鱼", "2026-09-24T18:20:00+08:00"),
            ),
        )

        assertEquals(1, values.recordDays)
    }

    private fun catchRecord(id: String, speciesName: String, capturedAt: String): RemoteCatch = RemoteCatch(
        id = id,
        imageUrl = "/$id.jpg",
        speciesId = speciesName,
        speciesName = speciesName,
        confidence = 0.9f,
        modelVersion = "test",
        capturedAt = capturedAt,
        createdAt = capturedAt,
    )
}
