package com.yujian.ai.ui.mycatches

import com.yujian.ai.catches.RemoteCatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MyCatchesPresentationTest {
    @Test
    fun daySummaryCountsSpeciesAndOnlyShowsASingleLocation() {
        val grass = catchRecord(id = "grass", speciesId = "grass", speciesName = "草鱼", location = "千岛湖")
        val carp = catchRecord(id = "carp", speciesId = "carp", speciesName = "鲤鱼", location = " 千岛湖 ")
        val summary = daySummary(listOf(grass, carp))

        assertEquals("千岛湖 · 2条鱼获 · 2种鱼", summary)
        assertEquals("2条鱼获 · 2种鱼", daySummary(listOf(grass.copy(location = null), carp.copy(location = "钱塘江"))))
        assertEquals("2条鱼获 · 2种鱼", daySummary(listOf(grass.copy(location = "千岛湖"), carp.copy(location = "钱塘江"))))
        assertEquals(2, daySpeciesCount(listOf(grass, carp, grass.copy(id = "blank", speciesId = "", speciesName = " "))))
        assertEquals(null, grass.copy(location = "null").toFishRecordPresentation(null).location)
    }

    @Test
    fun archiveRecordDaysCountDistinctValidCatchDatesOnly() {
        val sameDay = catchRecord("same-day-2", day = 23)
        val nextDay = catchRecord("next-day", day = 24)
        val invalidDate = catchRecord("invalid", day = 25).copy(capturedAt = "bad", createdAt = "also bad")

        assertEquals(2, archiveRecordDayCount(listOf(catchRecord("same-day-1", day = 23), sameDay, nextDay, invalidDate)))
        assertEquals(0, archiveRecordDayCount(listOf(invalidDate)))
    }

    @Test
    fun growthMarksFollowChronologyAndCompareSizesWithinSpecies() {
        val catches = buildList {
            add(catchRecord("grass-1", "grass", "草鱼", day = 1, length = 10f, weight = 2f, location = "千岛湖"))
            add(catchRecord("grass-2", "grass", "草鱼", day = 2, length = 20f, weight = 1f, location = "千岛湖"))
            add(catchRecord("carp-1", "carp", "鲤鱼", day = 3, length = 99f, weight = 9f))
            for (day in 4..10) add(catchRecord("other-$day", "other-$day", "其他鱼", day = day))
        }
        val marks = GrowthMarkResolver.resolve(catches)

        assertTrue(marks.getValue("grass-1").any { it.type == GrowthMarkType.FirstSpecies })
        assertTrue(marks.getValue("grass-1").any { it.type == GrowthMarkType.Heaviest })
        assertFalse(marks.getValue("grass-1").any { it.type == GrowthMarkType.Longest })
        assertTrue(marks.getValue("grass-2").any { it.type == GrowthMarkType.Longest })
        assertFalse(marks.getValue("grass-2").any { it.type == GrowthMarkType.Heaviest })
        assertTrue(marks.getValue("carp-1").any { it.type == GrowthMarkType.Longest })
        assertTrue(marks.getValue("carp-1").any { it.type == GrowthMarkType.Heaviest })
        assertTrue(marks.getValue("other-10").any { it.type == GrowthMarkType.CountMilestone })
        assertEquals("第10条", marks.getValue("other-10").rowGrowthMark()?.text)
    }

    @Test
    fun rowGrowthMarkUsesFrozenPriorityAndCombinesSizeRecords() {
        val marks = listOf(
            GrowthMark(GrowthMarkType.FirstSpecies, "首条草鱼"),
            GrowthMark(GrowthMarkType.Longest, "最长"),
            GrowthMark(GrowthMarkType.Heaviest, "最重"),
        )
        assertEquals("首条草鱼", marks.rowGrowthMark()?.text)
        assertEquals("最长 · 最重", marks.drop(1).rowGrowthMark()?.text)
        assertEquals(
            "第100条",
            (marks + GrowthMark(GrowthMarkType.CountMilestone, "第100条")).rowGrowthMark()?.text,
        )
    }

    @Test
    fun countMilestonesUseTenFiftyOneHundredFiveHundredAndOneThousandThresholds() {
        val catches = (1..1000).map { index ->
            catchRecord("record-%04d".format(index), "species-$index", "鱼$index", day = 25)
        }
        val marks = GrowthMarkResolver.resolve(catches)
        val milestoneLabels = marks.values.flatten().filter { it.type == GrowthMarkType.CountMilestone }.map { it.text }

        assertEquals(listOf("第10条", "第50条", "第100条", "第500条", "第1000条"), milestoneLabels)
    }

    @Test
    fun specialRecordFilterUsesAnySelectedMarkAcrossTheFullHistory() {
        val catches = (1..10).map { index ->
            catchRecord(
                "record-%02d".format(index),
                "species-$index",
                "鱼$index",
                day = 25,
                length = if (index == 8) 30f else null,
            )
        }

        val selected = filterAndSortCatches(
            catches,
            "",
            MyCatchesFilterState(specialMarks = setOf(SpecialCatchMark.Longest, SpecialCatchMark.Milestone)),
        ).map { it.id }.toSet()

        assertEquals(setOf("record-08", "record-10"), selected)
        assertTrue(isValidCatchDate("2026-09-30"))
        assertFalse(isValidCatchDate("2026-02-30"))
    }

    @Test
    fun searchMatchesAndKeywordsDateAndGrowthMarks() {
        val record = catchRecord("c1", "grass", "草鱼", day = 23, location = "千岛湖")
        val tenRecords = (1..10).map { day -> catchRecord("id-$day", "species-$day", "鱼$day", day = day) }

        assertTrue(matchesCatchSearch(record, " 草鱼  千岛湖 "))
        assertFalse(matchesCatchSearch(record, "草鱼 钱塘江"))
        assertTrue(matchesCatchSearch(record, "2026年9月"))
        assertTrue(matchesCatchSearch(record, "2026-09-23"))
        assertEquals(listOf("id-10"), filterAndSortCatches(tenRecords, "第10条", MyCatchesFilterState()).map { it.id })
    }

    @Test
    fun filterUsesOrWithinDimensionsAndAndAcrossDimensions() {
        val catches = listOf(
            catchRecord("grass", "grass", "草鱼", day = 23, length = 30f, weight = 0.8f),
            catchRecord("carp", "carp", "鲤鱼", day = 22, length = 50f, weight = 2f),
            catchRecord("crucian", "crucian", "鲫鱼", day = 20, length = 15f, weight = 0.2f),
        )
        val state = MyCatchesFilterState(
            speciesIds = setOf("grass", "carp"),
            timeRange = CatchTimeRange.ThisMonth,
            lengthRange = CatchLengthRange.From20To40,
        )
        val now = Instant.parse("2026-09-30T00:00:00Z").toEpochMilli()

        assertEquals(listOf("grass"), filterAndSortCatches(catches, "", state, now).map { it.id })
        assertEquals(
            listOf("grass"),
            filterAndSortCatches(
                catches,
                "",
                state.copy(lengthRange = CatchLengthRange.All, customWeightMinKg = 0.5f, customWeightMaxKg = 1f),
                now,
            ).map { it.id },
        )
        assertTrue(state.isActive)
        assertFalse(state.cleared().isActive)
    }

    @Test
    fun recentSearchesAreCommittedOnceAndCappedAtSix() {
        val initial = (1..6).map { "query-$it" }
        val firstCommit = commitRecentSearch(initial, " grass lake ")
        assertEquals(listOf("grass lake", "query-1", "query-2", "query-3", "query-4", "query-5"), firstCommit)

        val repeatedCommit = commitRecentSearch(firstCommit, "GRASS LAKE")
        assertEquals(listOf("GRASS LAKE", "query-1", "query-2", "query-3", "query-4", "query-5"), repeatedCommit)
        assertEquals(1, repeatedCommit.count { it.equals("grass lake", ignoreCase = true) })
        assertEquals(6, repeatedCommit.size)
        assertEquals(repeatedCommit, commitRecentSearch(repeatedCommit, "   "))
    }

    @Test
    fun emptyStatePriorityKeepsSearchAheadOfFilterAndLoadingAheadOfBoth() {
        val filter = MyCatchesFilterState(speciesIds = setOf("grass"))
        assertEquals(MyCatchesEmptyState.Archive, resolveMyCatchesEmptyState(0, "query", filter, 0))
        assertEquals(MyCatchesEmptyState.Search, resolveMyCatchesEmptyState(3, "query", filter, 0))
        assertEquals(MyCatchesEmptyState.Filter, resolveMyCatchesEmptyState(3, "", filter, 0))
        assertEquals(MyCatchesEmptyState.None, resolveMyCatchesEmptyState(3, "query", filter, 0, loading = true))
    }

    private fun catchRecord(
        id: String,
        speciesId: String = "grass",
        speciesName: String = "草鱼",
        day: Int = 25,
        length: Float? = null,
        weight: Float? = null,
        location: String? = null,
    ): RemoteCatch {
        val date = "2026-09-%02dT20:07:19+08:00".format(day)
        return RemoteCatch(
            id = id,
            imageUrl = "",
            speciesId = speciesId,
            speciesName = speciesName,
            confidence = .9f,
            modelVersion = "test",
            capturedAt = date,
            createdAt = date,
            lengthCm = length,
            weightKg = weight,
            location = location,
        )
    }
}
