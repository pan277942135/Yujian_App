package com.yujian.ai.ui.recognition.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionResultContractsTest {
    @Test
    fun frozenWidthGeometryMatchesCanonicalTable() {
        val expected = mapOf(320 to (288 to 224), 360 to (320 to 248), 393 to (353 to 274), 411 to (363 to 282))
        expected.forEach { (width, size) ->
            val geometry = RecognitionResultGeometryResolver.resolve(width, 640)
            assertEquals(size.first, geometry.heroWidthDp)
            assertEquals(size.second, geometry.heroHeightDp)
        }
        assertEquals(20, RecognitionResultGeometryResolver.resolve(360, 640).horizontalMarginDp)
        assertEquals(24, RecognitionResultGeometryResolver.resolve(411, 640).horizontalMarginDp)
    }

    @Test
    fun shortHeightShrinksHeroWithinContractAndKeepsActionsUnaffected() {
        val normal = RecognitionResultGeometryResolver.resolve(360, 640)
        val compact = RecognitionResultGeometryResolver.resolve(360, 599)
        assertTrue(compact.compactHeightPolicy)
        assertTrue(compact.heroHeightDp >= 208)
        assertTrue(compact.heroHeightDp >= (normal.heroHeightDp * 0.88f).toInt())
    }

    @Test
    fun subjectPlannerUsesSafeFitForMissingOrSourceClippedBox() {
        val missing = RecognitionHeroMediaPlanner.plan(1600, 900, 320f, 248f, null, false)
        assertEquals(RecognitionHeroMediaMode.SUBJECT_SAFE_FIT, missing.mode)
        val clipped = RecognitionHeroMediaPlanner.plan(1600, 900, 320f, 248f, NormalizedSourceRect(0f, .25f, .45f, .75f), false)
        assertEquals(RecognitionHeroMediaMode.SUBJECT_SAFE_FIT, clipped.mode)
        assertTrue(SourceEdge.LEFT in clipped.sourceClippedEdges)
        assertEquals(NormalizedSourceRect(0f, 0f, 1f, 1f), clipped.sourceRect)
    }

    @Test
    fun evidenceAlwaysFitsWholeImageAndCenteredSubjectCanCropSafely() {
        val evidence = RecognitionHeroMediaPlanner.plan(1600, 900, 320f, 248f, NormalizedSourceRect(.2f, .2f, .8f, .8f), true)
        assertEquals(RecognitionHeroMediaMode.EVIDENCE_FIT, evidence.mode)
        assertEquals(NormalizedSourceRect(0f, 0f, 1f, 1f), evidence.sourceRect)
        val subject = RecognitionHeroMediaPlanner.plan(1600, 900, 320f, 248f, NormalizedSourceRect(.35f, .3f, .65f, .7f), false)
        assertEquals(RecognitionHeroMediaMode.SUBJECT_CROP_FILL, subject.mode)
        assertTrue(subject.sourceRect.left <= .35f)
        assertTrue(subject.sourceRect.right >= .65f)
        assertTrue(subject.sourceRect.top <= .3f)
        assertTrue(subject.sourceRect.bottom >= .7f)
    }

    @Test
    fun metadataValidationHonorsOptionalRangeAndPrecision() {
        assertEquals(null, RecognitionResultInputValidation.length(""))
        assertEquals(null, RecognitionResultInputValidation.length(" 42.6 "))
        assertEquals("请输入 0.1–999.9 cm 的长度", RecognitionResultInputValidation.length("0.09"))
        assertEquals("请输入 0.1–999.9 cm 的长度", RecognitionResultInputValidation.length("1.25"))
        assertEquals(null, RecognitionResultInputValidation.weight("0.01"))
        assertEquals(null, RecognitionResultInputValidation.weight("999.99"))
        assertEquals("请输入 0.01–999.99 kg 的重量", RecognitionResultInputValidation.weight("1000"))
    }

    @Test
    fun metadataLimitsCountUnicodeCodePointsRatherThanUtf16Units() {
        val input = "a😀bc"
        assertEquals("a😀b", RecognitionResultInputValidation.takeUnicodeCodePoints(input, 3))
    }
}
