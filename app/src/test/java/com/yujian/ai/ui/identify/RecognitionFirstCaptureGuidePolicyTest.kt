package com.yujian.ai.ui.identify

import com.yujian.ai.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionFirstCaptureGuidePolicyTest {
    @Test fun firstCaptureGuideRoutesAndGalleryBypassesGuide() {
        assertEquals(RecognitionCaptureEntryDestination.FIRST_CAPTURE_GUIDE, resolveFirstCaptureEntryDestination(false, false))
        assertEquals(RecognitionCaptureEntryDestination.CAMERA, resolveFirstCaptureEntryDestination(false, true))
        assertEquals(RecognitionCaptureEntryDestination.GALLERY, resolveFirstCaptureEntryDestination(true, false))
        assertEquals(RecognitionCaptureEntryDestination.GALLERY, resolveFirstCaptureEntryDestination(true, true))
    }

    @Test fun guideActionsStackForCompactWidthOrLargeAccessibilityText() {
        assertTrue(shouldStackRecognitionGuideActions(379.9f, 1.0f))
        assertFalse(shouldStackRecognitionGuideActions(380f, 1.0f))
        assertFalse(shouldStackRecognitionGuideActions(393f, 1.3f))
        assertTrue(shouldStackRecognitionGuideActions(393f, 1.5f))
        assertTrue(shouldStackRecognitionGuideActions(640f, 2.0f))
    }

    @Test fun guideAppearsOnlyAfterCameraPreviewIsReady() {
        assertFalse(shouldShowRecognitionFirstCaptureGuide(false, true, false, false))
        assertFalse(shouldShowRecognitionFirstCaptureGuide(true, false, false, false))
        assertFalse(shouldShowRecognitionFirstCaptureGuide(true, true, true, false))
        assertFalse(shouldShowRecognitionFirstCaptureGuide(true, true, false, true))
        assertTrue(shouldShowRecognitionFirstCaptureGuide(true, true, false, false))
        assertFalse(shouldShowRecognitionFirstCaptureGuide(true, true, false, false, galleryVisitedThisSession = true))
    }

    @Test fun activeSequenceContainsOnlyTwoPortraitPhoneSteps() {
        assertEquals(RecognitionFirstCaptureGuidePage.P01, RecognitionFirstCaptureGuidePage.fromIndex(0))
        assertEquals(RecognitionFirstCaptureGuidePage.P02, RecognitionFirstCaptureGuidePage.fromIndex(1))
        assertEquals(RecognitionFirstCaptureGuidePage.P02, RecognitionFirstCaptureGuidePage.fromIndex(2))
        var state = RecognitionFirstCaptureGuideState()
        state = reduceRecognitionFirstCaptureGuideState(state, RecognitionFirstCaptureGuideIntent.NEXT)
        assertEquals(RecognitionFirstCaptureGuidePage.P02, state.currentPage)
        state = reduceRecognitionFirstCaptureGuideState(state, RecognitionFirstCaptureGuideIntent.NEXT)
        assertEquals(RecognitionFirstCaptureGuidePage.P02, state.currentPage)
        state = reduceRecognitionFirstCaptureGuideState(state, RecognitionFirstCaptureGuideIntent.PREVIOUS)
        assertEquals(RecognitionFirstCaptureGuidePage.P01, state.currentPage)
    }

    @Test fun onlySkipAndP02StartCompleteTheGuide() {
        listOf(
            RecognitionFirstCaptureGuideIntent.BACK, RecognitionFirstCaptureGuideIntent.CLOSE,
            RecognitionFirstCaptureGuideIntent.OUTSIDE_TAP, RecognitionFirstCaptureGuideIntent.NEXT,
            RecognitionFirstCaptureGuideIntent.PREVIOUS,
        ).forEach { intent ->
            assertFalse(reduceRecognitionFirstCaptureGuideState(RecognitionFirstCaptureGuideState(), intent).completed)
        }
        val skipped = reduceRecognitionFirstCaptureGuideState(RecognitionFirstCaptureGuideState(), RecognitionFirstCaptureGuideIntent.SKIP)
        assertFalse(skipped.visible); assertTrue(skipped.completed)
        val started = reduceRecognitionFirstCaptureGuideState(
            RecognitionFirstCaptureGuideState(currentPage = RecognitionFirstCaptureGuidePage.P02),
            RecognitionFirstCaptureGuideIntent.START,
        )
        assertFalse(started.visible); assertTrue(started.completed)
        val premature = reduceRecognitionFirstCaptureGuideState(
            RecognitionFirstCaptureGuideState(currentPage = RecognitionFirstCaptureGuidePage.P01),
            RecognitionFirstCaptureGuideIntent.START,
        )
        assertTrue(premature.visible); assertFalse(premature.completed)
    }

    @Test fun bothPagesKeepPortraitPhoneAndDistinctFishOrientationAssets() {
        val p01 = recognitionFirstCaptureGuidePageSpec(RecognitionFirstCaptureGuidePage.P01)
        assertEquals("竖屏", p01.phoneOrientationLabel)
        assertEquals(RecognitionGuideFishOrientation.HORIZONTAL, p01.fishOrientation)
        assertEquals("手机保持竖屏，整条鱼横向完整放入取景范围", p01.subtitle)
        assertEquals("下一张", p01.primaryActionLabel)
        assertEquals(R.drawable.first_use_guide_bg_p01, p01.backgroundAsset)
        assertEquals(R.drawable.first_use_guide_horizontal_correct_hero, p01.heroAsset)
        assertEquals(listOf(
            R.drawable.first_use_guide_horizontal_fish_head_out,
            R.drawable.first_use_guide_horizontal_fish_too_small,
            R.drawable.first_use_guide_horizontal_excessive_occlusion,
        ), p01.errorAssets)

        val p02 = recognitionFirstCaptureGuidePageSpec(RecognitionFirstCaptureGuidePage.P02)
        assertEquals("竖屏", p02.phoneOrientationLabel)
        assertEquals(RecognitionGuideFishOrientation.VERTICAL, p02.fishOrientation)
        assertEquals("手机保持竖屏，整条鱼竖直完整放入取景范围", p02.subtitle)
        assertEquals("开始拍摄", p02.primaryActionLabel)
        assertEquals(R.drawable.first_use_guide_bg_p02, p02.backgroundAsset)
        assertEquals(R.drawable.first_use_guide_vertical_correct_hero, p02.heroAsset)
        assertEquals(listOf(
            R.drawable.first_use_guide_vertical_fish_head_out,
            R.drawable.first_use_guide_vertical_fish_too_small,
            R.drawable.first_use_guide_vertical_excessive_occlusion,
        ), p02.errorAssets)
        assertEquals(listOf("鱼头不在画面", "鱼体过小", "遮挡过多"), p02.errors.map { it.label })
    }

    @Test fun bothActivePagesMapToPortraitReferenceArtboards() {
        listOf(RecognitionFirstCaptureGuidePage.P01, RecognitionFirstCaptureGuidePage.P02).forEach { page ->
            val mapping = mapRecognitionGuideReferenceSpace(page, 1080, 1920)
            assertEquals(941, mapping.referenceWidthPx)
            assertEquals(1671, mapping.referenceHeightPx)
            assertEquals(1080f / 941f, mapping.uniformScale, 0.0001f)
        }
    }
}
