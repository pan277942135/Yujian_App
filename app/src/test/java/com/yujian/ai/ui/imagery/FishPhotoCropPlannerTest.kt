package com.yujian.ai.ui.imagery

import com.yujian.ai.ai.NormalizedFishBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FishPhotoCropPlannerTest {
    private val sample = NormalizedFishBox(0.1f, 0.2f, 0.4f, 0.7f)

    @Test
    fun exifOrientationsTwoThroughEightMapBoundingBoxCornersCorrectly() {
        val expected = mapOf(
            2 to NormalizedFishBox(0.6f, 0.2f, 0.9f, 0.7f),
            3 to NormalizedFishBox(0.6f, 0.3f, 0.9f, 0.8f),
            4 to NormalizedFishBox(0.1f, 0.3f, 0.4f, 0.8f),
            5 to NormalizedFishBox(0.2f, 0.1f, 0.7f, 0.4f),
            6 to NormalizedFishBox(0.3f, 0.1f, 0.8f, 0.4f),
            7 to NormalizedFishBox(0.3f, 0.6f, 0.8f, 0.9f),
            8 to NormalizedFishBox(0.2f, 0.6f, 0.7f, 0.9f),
        )

        expected.forEach { (orientation, box) ->
            val actual = FishPhotoCropPlanner.mapBoxToDisplayedCoordinates(
                sample,
                PhotoExifOrientation.fromValue(orientation),
            )
            assertBoxEquals(box, actual)
        }
    }

    @Test
    fun displayOrientedBoxIsNeverTransformedTwice() {
        val plan = FishPhotoCropPlanner.plan(
            1000,
            1500,
            4f / 3f,
            FishPhotoBoundingBox(
                box = sample,
                confidence = 0.91f,
                coordinateSpace = FishBoxCoordinateSpace.DISPLAY_ORIENTED,
                exifOrientation = PhotoExifOrientation.ROTATE_90,
            ),
        )
        val actual = requireNotNull(plan.orientedBoundingBox)
        assertEquals(100f, actual.left, 0.01f)
        assertEquals(300f, actual.top, 0.01f)
        assertEquals(400f, actual.right, 0.01f)
        assertEquals(1050f, actual.bottom, 0.01f)
    }

    @Test
    fun safeCropKeepsTheWholeBoxInsideSourceAndPreservesRequestedRatio() {
        val plan = FishPhotoCropPlanner.plan(
            sourceWidthPx = 1200,
            sourceHeightPx = 900,
            targetAspectRatio = 1.4f,
            boundingBox = FishPhotoBoundingBox(
                NormalizedFishBox(0.31f, 0.29f, 0.72f, 0.61f),
                confidence = 0.88f,
            ),
        )
        val crop = requireNotNull(plan.cropRect)
        val fish = requireNotNull(plan.orientedBoundingBox)
        assertEquals(FishPhotoDisplayMode.SUBJECT_CROP, plan.mode)
        assertTrue(crop.contains(fish))
        assertTrue(crop.left >= 0f && crop.top >= 0f)
        assertTrue(crop.right <= 1200f && crop.bottom <= 900f)
        assertEquals(1.4f, crop.aspectRatio, 0.0001f)
    }

    @Test
    fun labelledLandscapeAndPortraitFixturesKeepTheWholeFishAtOneCardRatio() {
        data class Fixture(
            val name: String,
            val width: Int,
            val height: Int,
            val box: NormalizedFishBox,
        )

        val fixtures = listOf(
            Fixture("landscape-fish", 1600, 900, NormalizedFishBox(0.28f, 0.32f, 0.68f, 0.68f)),
            Fixture("portrait-fish", 900, 1600, NormalizedFishBox(0.35f, 0.20f, 0.65f, 0.80f)),
        )
        val targetRatio = 247f / 293f

        fixtures.forEach { fixture ->
            val plan = FishPhotoCropPlanner.plan(
                sourceWidthPx = fixture.width,
                sourceHeightPx = fixture.height,
                targetAspectRatio = targetRatio,
                boundingBox = FishPhotoBoundingBox(fixture.box, confidence = 0.93f, version = fixture.name),
            )
            val crop = requireNotNull(plan.cropRect) { "${fixture.name} unexpectedly used ${plan.fallbackReason}" }
            val fish = requireNotNull(plan.orientedBoundingBox)

            assertEquals(FishPhotoDisplayMode.SUBJECT_CROP, plan.mode)
            assertTrue("${fixture.name} BBox must be fully retained", crop.contains(fish))
            assertTrue(crop.left >= 0f && crop.top >= 0f)
            assertTrue(crop.right <= fixture.width && crop.bottom <= fixture.height)
            assertEquals(targetRatio, crop.aspectRatio, 0.0001f)
        }
    }

    @Test
    fun allFourImageEdgesRemainCropCandidatesAndUseOppositeSpace() {
        val boxes = listOf(
            NormalizedFishBox(0.001f, 0.40f, 0.091f, 0.52f),
            NormalizedFishBox(0.909f, 0.40f, 0.999f, 0.52f),
            NormalizedFishBox(0.40f, 0.001f, 0.52f, 0.091f),
            NormalizedFishBox(0.40f, 0.909f, 0.52f, 0.999f),
        )
        val edge = listOf("left", "right", "top", "bottom")
        boxes.forEachIndexed { index, box ->
            val plan = FishPhotoCropPlanner.plan(
                1000,
                1000,
                1f,
                FishPhotoBoundingBox(box, 0.9f),
            )
            val crop = requireNotNull(plan.cropRect)
            assertTrue("${edge[index]} edge must remain crop eligible", plan.retainsWholeBoundingBox)
            when (index) {
                0 -> { assertEquals(0f, crop.left, 0.01f); assertTrue(crop.right > 100f) }
                1 -> { assertEquals(1000f, crop.right, 0.01f); assertTrue(crop.left < 900f) }
                2 -> { assertEquals(0f, crop.top, 0.01f); assertTrue(crop.bottom > 100f) }
                3 -> { assertEquals(1000f, crop.bottom, 0.01f); assertTrue(crop.top < 900f) }
            }
        }
    }

    @Test
    fun exactBoundaryOverflowMovesToTheOppositeSideInBothAxes() {
        val left = FishPhotoCropPlanner.expandAxisWithOppositeSideTransfer(1f, 91f, 1000f, 9f)
        val right = FishPhotoCropPlanner.expandAxisWithOppositeSideTransfer(900f, 999f, 1000f, 9f)
        val top = FishPhotoCropPlanner.expandAxisWithOppositeSideTransfer(1f, 91f, 1000f, 9f)
        val bottom = FishPhotoCropPlanner.expandAxisWithOppositeSideTransfer(900f, 999f, 1000f, 9f)
        assertEquals(0f, left.first, 0.001f)
        assertEquals(108f, left.second, 0.001f)
        assertEquals(883f, right.first, 0.001f)
        assertEquals(1000f, right.second, 0.001f)
        assertEquals(0f, top.first, 0.001f)
        assertEquals(108f, top.second, 0.001f)
        assertEquals(883f, bottom.first, 0.001f)
        assertEquals(1000f, bottom.second, 0.001f)
    }

    @Test
    fun safetyMarginRetriesInFrozenOrderAndUsesLargestFeasibleLevel() {
        val plan = FishPhotoCropPlanner.plan(
            sourceWidthPx = 100,
            sourceHeightPx = 100,
            targetAspectRatio = 2f,
            boundingBox = FishPhotoBoundingBox(
                NormalizedFishBox(0.1f, 0f, 0.55f, 0.45f),
                confidence = 0.9f,
            ),
        )
        assertEquals(listOf(12, 8, 4), plan.attemptedMarginLevels)
        assertEquals(4, plan.marginLevelPercent)
        assertTrue(plan.retainsWholeBoundingBox)
    }

    @Test
    fun impossibleAspectAndMissingOrUntrustedBoxesUseFullSourceFallback() {
        val impossible = FishPhotoCropPlanner.plan(
            100,
            100,
            2f,
            FishPhotoBoundingBox(NormalizedFishBox(0.1f, 0f, 0.55f, 0.6f), 0.9f),
        )
        assertEquals(FishPhotoDisplayMode.FULL_FRAME_WITH_SOURCE_BACKDROP, impossible.mode)
        assertNull(impossible.cropRect)
        assertEquals(FishPhotoFallbackReason.ASPECT_CANNOT_CONTAIN_SUBJECT, impossible.fallbackReason)
        assertEquals(listOf(12, 8, 4, 0), impossible.attemptedMarginLevels)

        val missing = FishPhotoCropPlanner.plan(1000, 1500, 4f / 3f)
        assertEquals(FishPhotoFallbackReason.MISSING_BBOX, missing.fallbackReason)
        assertNull(missing.cropRect)

        val weak = FishPhotoCropPlanner.plan(
            1000,
            1500,
            4f / 3f,
            FishPhotoBoundingBox(sample, 0.64f),
        )
        assertEquals(FishPhotoFallbackReason.LOW_CONFIDENCE, weak.fallbackReason)
        assertNull(weak.cropRect)
    }

    @Test
    fun displaySafetyThresholdCanBeConfiguredWithoutTouchingInferenceGates() {
        val detection = FishPhotoBoundingBox(sample, confidence = 0.6f)
        val configured = FishPhotoCropPlanner.plan(
            sourceWidthPx = 1000,
            sourceHeightPx = 1000,
            targetAspectRatio = 4f / 3f,
            boundingBox = detection,
            displayConfidenceThreshold = 0.55f,
        )
        assertEquals(FishPhotoDisplayMode.SUBJECT_CROP, configured.mode)
        assertTrue(configured.retainsWholeBoundingBox)
    }

    @Test
    fun invalidAmbiguousAndUnreasonableDetectionsNeverBecomeAggressiveCrops() {
        val invalid = FishPhotoCropPlanner.plan(
            1000,
            1000,
            1f,
            FishPhotoBoundingBox(NormalizedFishBox(-0.1f, 0.1f, 0.5f, 0.5f), 0.9f),
        )
        val ambiguous = FishPhotoCropPlanner.plan(
            1000,
            1000,
            1f,
            FishPhotoBoundingBox(sample, 0.9f),
            primaryAmbiguous = true,
        )
        val tooSmall = FishPhotoCropPlanner.plan(
            100,
            100,
            1f,
            FishPhotoBoundingBox(NormalizedFishBox(0.01f, 0.01f, 0.015f, 0.015f), 0.9f),
        )
        assertEquals(FishPhotoFallbackReason.INVALID_BBOX, invalid.fallbackReason)
        assertEquals(FishPhotoFallbackReason.AMBIGUOUS_SUBJECT, ambiguous.fallbackReason)
        assertEquals(FishPhotoFallbackReason.UNREASONABLE_BBOX, tooSmall.fallbackReason)
        listOf(invalid, ambiguous, tooSmall).forEach {
            assertEquals(FishPhotoDisplayMode.FULL_FRAME_WITH_SOURCE_BACKDROP, it.mode)
            assertNull(it.cropRect)
        }
    }

    @Test
    fun sameContainerRatioDoesNotDependOnPhotoOrientation() {
        val targetAspect = 247f / 293f
        val portrait = FishPhotoCropPlanner.plan(900, 1600, targetAspect)
        val landscape = FishPhotoCropPlanner.plan(1600, 900, targetAspect)
        assertEquals(targetAspect, portrait.cropRect?.aspectRatio ?: targetAspect, 0.0001f)
        assertEquals(targetAspect, landscape.cropRect?.aspectRatio ?: targetAspect, 0.0001f)
        assertFalse(portrait.mode == FishPhotoDisplayMode.SUBJECT_CROP)
        assertFalse(landscape.mode == FishPhotoDisplayMode.SUBJECT_CROP)
        assertNotNull(portrait)
    }

    private fun assertBoxEquals(expected: NormalizedFishBox, actual: NormalizedFishBox) {
        assertEquals(expected.x1, actual.x1, 0.0001f)
        assertEquals(expected.y1, actual.y1, 0.0001f)
        assertEquals(expected.x2, actual.x2, 0.0001f)
        assertEquals(expected.y2, actual.y2, 0.0001f)
    }
}
