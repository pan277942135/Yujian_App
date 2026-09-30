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

    @Test
    fun invalidRecordsAreIgnoredByLocalStatistics() {
        val values = resolveHomeStatValues(
            statistics = CatchStatistics(),
            catches = listOf(
                catchRecord("valid", "草鱼", "2026-09-24T18:20:00+08:00"),
                catchRecord("   ", "鲫鱼", "2026-09-24T18:20:00+08:00"),
                catchRecord("missing-species", " ", "2026-09-25T18:20:00+08:00").copy(speciesId = " "),
            ),
        )

        assertEquals(HomeStatValues(speciesCount = 1, catchCount = 1, recordDays = 1), values)
    }

    @Test
    fun archiveResolutionDistinguishesUnresolvedFromResolvedEmptyAndNormal() {
        assertEquals(null, resolveHomeState(emptyList(), resolved = false))
        assertEquals(HomeState.EMPTY, resolveHomeState(emptyList(), resolved = true))
        assertEquals(HomeState.NORMAL, resolveHomeState(listOf(catchRecord("valid", "草鱼", "")), resolved = true))
        assertEquals(HomeState.EMPTY, resolveHomeState(listOf(catchRecord(" ", "草鱼", "")), resolved = true))
    }

    @Test
    fun refreshLoadingAndFailureCanRetainLastResolvedNormalState() {
        val resolvedRecords = listOf(catchRecord("valid", "草鱼", ""))
        // Loading and error update archive metadata only; the resolved snapshot still decides Home.
        assertEquals(HomeState.NORMAL, resolveHomeState(resolvedRecords, resolved = true))
        assertEquals(HomeState.NORMAL, resolveHomeState(resolvedRecords, resolved = true))
    }

    @Test
    fun recentOrderingKeepsValidTimestampedRecordsFirstAndInvalidDatesLast() {
        val newest = catchRecord("new", "鲫鱼", "2026-09-25T18:20:00+08:00")
        val oldest = catchRecord("old", "草鱼", "2026-09-20T18:20:00+08:00")
        val undated = catchRecord("undated", "鲤鱼", "")
        val invalid = catchRecord(" ", "鲤鱼", "2026-09-26T18:20:00+08:00")

        assertEquals(listOf("new", "old", "undated"), orderedHomeRecords(listOf(oldest, invalid, undated, newest)).map { it.id })
    }

    @Test
    fun reduceMotionDisablesNormalHomeIdleCardMotion() {
        assertEquals(true, normalHomeCatchMotionActive(running = true, reduceMotion = false))
        assertEquals(false, normalHomeCatchMotionActive(running = true, reduceMotion = true))
        assertEquals(false, normalHomeCatchMotionActive(running = false, reduceMotion = false))
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
