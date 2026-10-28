package com.yujian.ai.ui.identify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionFirstCaptureGuidePolicyTest {
    @Test
    fun guideActionsStackForCompactWidthOrLargeAccessibilityText() {
        assertTrue(shouldStackRecognitionGuideActions(379.9f, 1.0f))
        assertFalse(shouldStackRecognitionGuideActions(380f, 1.0f))
        assertFalse(shouldStackRecognitionGuideActions(393f, 1.3f))
        assertTrue(shouldStackRecognitionGuideActions(393f, 1.5f))
        assertTrue(shouldStackRecognitionGuideActions(640f, 2.0f))
    }

    @Test
    fun guideAppearsOnlyAfterCameraPreviewIsReadyForTheFirstCameraEntry() {
        fun decision(permission: Boolean, preview: Boolean, galleryPending: Boolean, completed: Boolean) =
            shouldShowRecognitionFirstCaptureGuide(permission, preview, galleryPending, completed)

        assertFalse(decision(permission = false, preview = true, galleryPending = false, completed = false))
        assertFalse(decision(permission = true, preview = false, galleryPending = false, completed = false))
        assertFalse(decision(permission = true, preview = true, galleryPending = true, completed = false))
        assertFalse(decision(permission = true, preview = true, galleryPending = false, completed = true))
        assertTrue(decision(permission = true, preview = true, galleryPending = false, completed = false))
        assertFalse(
            shouldShowRecognitionFirstCaptureGuide(
                cameraPermissionGranted = true,
                previewReady = true,
                galleryPickerPending = false,
                guideCompleted = false,
                galleryVisitedThisSession = true,
            ),
        )
    }

    @Test
    fun galleryBypassAndCameraReadinessAreIndependentOfRouteEntry() {
        assertFalse(
            shouldShowRecognitionFirstCaptureGuide(
                cameraPermissionGranted = true,
                previewReady = true,
                galleryPickerPending = true,
                guideCompleted = false,
            ),
        )
        assertFalse(
            shouldShowRecognitionFirstCaptureGuide(
                cameraPermissionGranted = false,
                previewReady = false,
                galleryPickerPending = false,
                guideCompleted = false,
            ),
        )
    }

    @Test
    fun pageSequenceAndActionsMatchTheFrozenThreePageContract() {
        var state = RecognitionFirstCaptureGuideState()
        state = reduceRecognitionFirstCaptureGuideState(state, RecognitionFirstCaptureGuideIntent.NEXT)
        assertEquals(RecognitionFirstCaptureGuidePage.P02, state.currentPage)
        assertFalse(state.completed)

        state = reduceRecognitionFirstCaptureGuideState(state, RecognitionFirstCaptureGuideIntent.PREVIOUS)
        assertEquals(RecognitionFirstCaptureGuidePage.P01, state.currentPage)
        state = reduceRecognitionFirstCaptureGuideState(state, RecognitionFirstCaptureGuideIntent.NEXT)
        state = reduceRecognitionFirstCaptureGuideState(state, RecognitionFirstCaptureGuideIntent.NEXT)
        assertEquals(RecognitionFirstCaptureGuidePage.P03, state.currentPage)
        state = reduceRecognitionFirstCaptureGuideState(state, RecognitionFirstCaptureGuideIntent.PREVIOUS)
        assertEquals(RecognitionFirstCaptureGuidePage.P02, state.currentPage)
    }

    @Test
    fun onlySkipAndFinalStartCompleteTheGuide() {
        listOf(
            RecognitionFirstCaptureGuideIntent.BACK,
            RecognitionFirstCaptureGuideIntent.CLOSE,
            RecognitionFirstCaptureGuideIntent.OUTSIDE_TAP,
            RecognitionFirstCaptureGuideIntent.NEXT,
            RecognitionFirstCaptureGuideIntent.PREVIOUS,
        ).forEach { intent ->
            val result = reduceRecognitionFirstCaptureGuideState(RecognitionFirstCaptureGuideState(), intent)
            assertFalse("$intent must not complete the guide", result.completed)
        }

        val skipped = reduceRecognitionFirstCaptureGuideState(
            RecognitionFirstCaptureGuideState(),
            RecognitionFirstCaptureGuideIntent.SKIP,
        )
        assertFalse(skipped.visible)
        assertTrue(skipped.completed)

        val started = reduceRecognitionFirstCaptureGuideState(
            RecognitionFirstCaptureGuideState(currentPage = RecognitionFirstCaptureGuidePage.P03),
            RecognitionFirstCaptureGuideIntent.START,
        )
        assertFalse(started.visible)
        assertTrue(started.completed)

        listOf(RecognitionFirstCaptureGuidePage.P01, RecognitionFirstCaptureGuidePage.P02).forEach { page ->
            val prematureStart = reduceRecognitionFirstCaptureGuideState(
                RecognitionFirstCaptureGuideState(currentPage = page),
                RecognitionFirstCaptureGuideIntent.START,
            )
            assertTrue("START is only valid on P03 ($page)", prematureStart.visible)
            assertFalse("START is only valid on P03 ($page)", prematureStart.completed)
        }
    }

    @Test
    fun fishOrientationAndErrorExamplesStayConsistentOnEachPage() {
        val horizontalPages = listOf(RecognitionFirstCaptureGuidePage.P01, RecognitionFirstCaptureGuidePage.P03)
        horizontalPages.forEach { page ->
            val spec = recognitionFirstCaptureGuidePageSpec(page)
            assertEquals(RecognitionGuideFishOrientation.HORIZONTAL, spec.fishOrientation)
            assertEquals(listOf("鱼头不在画面", "鱼体过小", "遮挡过多"), spec.errors.map { it.label })
            assertTrue(spec.errors.all { it.orientation == RecognitionGuideFishOrientation.HORIZONTAL })
        }

        val verticalSpec = recognitionFirstCaptureGuidePageSpec(RecognitionFirstCaptureGuidePage.P02)
        assertEquals(RecognitionGuideFishOrientation.VERTICAL, verticalSpec.fishOrientation)
        assertTrue(verticalSpec.errors.all { it.orientation == RecognitionGuideFishOrientation.VERTICAL })

        listOf(RecognitionFirstCaptureGuidePage.P01, RecognitionFirstCaptureGuidePage.P02, RecognitionFirstCaptureGuidePage.P03)
            .forEach { page ->
                val spec = recognitionFirstCaptureGuidePageSpec(page)
                assertEquals(RecognitionGuideRuntimeAssetStatus.ASSET_SOURCE_BLOCKED, spec.runtimeAssetStatus)
                assertEquals(null, spec.heroAsset)
                assertEquals(listOf(null, null, null), spec.errorAssets)
                assertEquals(3, spec.errors.size)
            }
    }

    @Test
    fun frozenReferenceMapsToRuntimeWithOneUniformScale() {
        val mapping = mapRecognitionGuideReferenceSpace(
            page = RecognitionFirstCaptureGuidePage.P01,
            runtimeWidthPx = 1080,
            runtimeHeightPx = 1920,
        )

        assertEquals(941, mapping.referenceWidthPx)
        assertEquals(1671, mapping.referenceHeightPx)
        assertEquals(1080f / 941f, mapping.uniformScale, 0.0001f)
        assertEquals(0f, mapping.offsetXPx, 0f)
        val left = mapping.map(100f, 200f)
        val right = mapping.map(200f, 200f)
        val lower = mapping.map(100f, 300f)
        assertEquals(100f * mapping.uniformScale, right.x - left.x, 0.001f)
        assertEquals(100f * mapping.uniformScale, lower.y - left.y, 0.001f)

        assertEquals(
            1672,
            mapRecognitionGuideReferenceSpace(
                page = RecognitionFirstCaptureGuidePage.P03,
                runtimeWidthPx = 1080,
                runtimeHeightPx = 1920,
            ).referenceHeightPx,
        )
    }
}
