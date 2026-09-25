package com.yujian.ai.ui.mycatches

import com.yujian.ai.catches.RemoteCatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyCatchesPresentationTest {
    @Test
    fun nullLocationDoesNotCreateGrowthMarkOrLocationSummary() {
        val record = catchRecord(id = "null-location", location = "null")

        val marks = GrowthMarkResolver.resolve(listOf(record)).getValue(record.id)

        assertFalse(marks.any { it.text == "首次null" })
        assertEquals("1条鱼获", daySummary(listOf(record)))
        assertEquals(null, record.toFishRecordPresentation(null).location)
    }

    @Test
    fun validLocationSurvivesPresentationAndFilterSource() {
        val record = catchRecord(id = "valid-location", location = " 千岛湖 ")
        val presentation = record.toFishRecordPresentation(null)

        assertEquals("千岛湖", presentation.location)
        assertTrue(GrowthMarkResolver.resolve(listOf(record)).getValue(record.id).any { it.text == "首次千岛湖" })
        assertEquals("千岛湖 · 1条鱼获", daySummary(listOf(record)))
    }

    private fun catchRecord(id: String, location: String?): RemoteCatch = RemoteCatch(
        id = id,
        imageUrl = "",
        speciesId = "grass_carp",
        speciesName = "草鱼",
        confidence = .9f,
        modelVersion = "test",
        capturedAt = "2026-09-25T20:07:19+08:00",
        createdAt = "2026-09-25T20:07:19+08:00",
        location = location,
    )
}
