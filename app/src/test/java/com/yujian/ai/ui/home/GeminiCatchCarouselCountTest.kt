package com.yujian.ai.ui.home

import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.catches.CatchStatistics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiCatchCarouselCountTest {
    @Test
    fun finitePageCountMatchesDistinctValidCatchIdsForRequiredCases() {
        listOf(0, 1, 2, 3, 10).forEach { count ->
            val input = (0 until count).map(::catchAt).reversed()
            val ordered = orderedHomeRecords(input)

            assertEquals("${count} valid input records must create $count real pages", count, ordered.size)
            assertEquals(count, ordered.map(RemoteCatch::id).distinct().size)
            if (count > 0) {
                assertEquals("catch-${(count - 1).toString().padStart(2, '0')}", ordered.first().id)
                assertEquals("catch-00", ordered.last().id)
            }
        }
    }

    @Test
    fun duplicateStableIdsKeepTheNewestRealCatchWithoutCloningPages() {
        val old = catchAt(0).copy(capturedAt = "2026-10-01T08:00:00Z", imageUrl = "/old.jpg")
        val newest = old.copy(capturedAt = "2026-10-02T08:00:00Z", imageUrl = "/new.jpg", speciesName = "鲫鱼")
        val other = catchAt(1)

        val ordered = orderedHomeRecords(listOf(old, other, newest))

        assertEquals(listOf("catch-01", "catch-00"), ordered.map(RemoteCatch::id))
        assertEquals("/new.jpg", ordered.last().imageUrl)
        assertEquals("鲫鱼", ordered.last().speciesName)
        assertTrue(ordered.map(RemoteCatch::id).distinct().size == ordered.size)
    }

    @Test
    fun localCatchTotalUsesUniqueStableIdsWhenServerTotalIsUnavailable() {
        val duplicate = catchAt(0)
        val values = resolveHomeStatValues(
            statistics = CatchStatistics(),
            catches = listOf(duplicate, duplicate.copy(imageUrl = "/duplicate.jpg"), catchAt(1)),
        )

        assertEquals(2, values.catchCount)
    }

    private fun catchAt(index: Int): RemoteCatch {
        val suffix = index.toString().padStart(2, '0')
        return RemoteCatch(
            id = "catch-$suffix",
            imageUrl = "/$suffix.jpg",
            speciesId = "species-$suffix",
            speciesName = "鱼-$suffix",
            confidence = 0.9f,
            modelVersion = "gemini-carousel-count-test",
            capturedAt = "2026-10-${(index + 1).toString().padStart(2, '0')}T08:00:00Z",
            createdAt = "2026-10-${(index + 1).toString().padStart(2, '0')}T08:00:00Z",
        )
    }
}
