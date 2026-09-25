package com.yujian.ai.ui.fishguide

import com.yujian.ai.knowledge.FishGuideItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FishGuidePresentationTest {
    private val species = listOf(
        FishGuideItem("grass", "草鱼", aliases = listOf("鲩鱼"), discovered = true, catches = 3),
        FishGuideItem("crucian", "鲫鱼", aliases = listOf("鲫") , discovered = false),
        FishGuideItem("carp", "鲤鱼", discovered = true, catches = 1),
    )

    @Test
    fun countsLitSpeciesAndProgressFromRuntimeData() {
        assertEquals(2, species.litCount())
        assertEquals(3, species.size)
        assertEquals(2f / 3f, species.progressFraction(), 0.0001f)
    }

    @Test
    fun searchMatchesNameAndAliasAndBlankReturnsAll() {
        assertEquals(listOf("grass"), species.filterFishGuide("草").map { it.id })
        assertEquals(listOf("grass"), species.filterFishGuide("鲩").map { it.id })
        assertEquals(species, species.filterFishGuide(" "))
    }

    @Test
    fun selectionIsPreservedWhenPresentAndResetsToFirstWhenFilteredOut() {
        assertEquals("carp", selectionIdAfterFilter(species, "carp"))
        assertEquals("grass", selectionIdAfterFilter(species.filterFishGuide("草"), "carp"))
        assertEquals(0, selectionIndex(species.filterFishGuide("草"), "carp"))
    }

    @Test
    fun datasetShapesCoverEmptyThroughCarousel() {
        assertEquals(FishGuideDatasetShape.EMPTY, emptyList<FishGuideItem>().datasetShape())
        assertEquals(FishGuideDatasetShape.SINGLE, species.take(1).datasetShape())
        assertEquals(FishGuideDatasetShape.TWO, species.take(2).datasetShape())
        assertEquals(FishGuideDatasetShape.CAROUSEL, species.datasetShape())
    }

    @Test
    fun recordCountIsQuietAndOmitsZero() {
        assertEquals("3 次记录", formatRecordCount(3))
        assertNull(formatRecordCount(0))
        assertNull(formatRecordCount(-1))
    }

    @Test
    fun presentationMapsRuntimeImageAndLitStateWithoutChangingDomainItem() {
        val item = species.toFishGuidePresentation { value -> "resolved:$value" }.first()
        assertEquals("grass", item.id)
        assertEquals("草鱼", item.name)
        assertEquals("resolved:null", item.imageUrl)
        assertTrue(item.discovered)
        assertEquals(3, item.catches)
    }
}
