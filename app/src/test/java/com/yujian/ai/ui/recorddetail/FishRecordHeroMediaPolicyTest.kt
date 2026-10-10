package com.yujian.ai.ui.recorddetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FishRecordHeroMediaPolicyTest {
    @Test
    fun aSideUsesEvidenceFitAndBsideKeepsCoverBehavior() {
        assertEquals(FishRecordHeroImageMode.EVIDENCE_FIT, FishRecordHeroMediaPolicy.mode(showBside = false))
        assertEquals(FishRecordHeroImageMode.COVER, FishRecordHeroMediaPolicy.mode(showBside = true))
    }

    @Test
    fun fitAndCoverScalesHandleLandscapePortraitAndExtremeRatios() {
        val viewportWidth = 841f
        val viewportHeight = 540f
        listOf(
            4f to 3f,
            16f to 9f,
            3f to 4f,
            9f to 16f,
            1f to 3f,
            3f to 1f,
        ).forEach { (sourceWidth, sourceHeight) ->
            val fit = FishRecordHeroMediaPolicy.fitScale(sourceWidth, sourceHeight, viewportWidth, viewportHeight)
            val cover = FishRecordHeroMediaPolicy.coverScale(sourceWidth, sourceHeight, viewportWidth, viewportHeight)
            assertTrue("fit must keep the full source visible for $sourceWidth:$sourceHeight", fit <= cover)
            assertTrue(sourceWidth * fit <= viewportWidth + 0.001f)
            assertTrue(sourceHeight * fit <= viewportHeight + 0.001f)
            assertTrue(sourceWidth * cover + 0.001f >= viewportWidth)
            assertTrue(sourceHeight * cover + 0.001f >= viewportHeight)
        }
    }

    @Test
    fun fitRetainsBothSourceEdgesWithoutRequiringSubjectCoordinates() {
        val scale = FishRecordHeroMediaPolicy.fitScale(1080f, 3240f, 841f, 540f)
        val fittedHeight = 3240f * scale
        val top = (540f - fittedHeight) / 2f
        val bottom = top + fittedHeight
        assertTrue(top >= 0f)
        assertEquals(540f, bottom, 0.001f)
    }
}
