package com.yujian.ai.ui.recognition.result

import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ui.identify.DisplayRect
import com.yujian.ai.ui.identify.RecognitionContentScaleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionProcessingMediaPlanTest {
    private data class SubjectCase(
        val sourceWidth: Int,
        val sourceHeight: Int,
        val box: NormalizedFishBox,
    )

    private val viewports = listOf(
        1080f to 1920f,
        1080f to 2160f,
        1080f to 2340f,
        1080f to 2400f,
        720f to 1600f,
    )

    private val physicalSubjects = listOf(
        // Fish strongly left, person/context on the right.
        SubjectCase(2000, 1500, NormalizedFishBox(.03f, .28f, .30f, .68f)),
        // Large horizontal fish across a portrait source.
        SubjectCase(1536, 2304, NormalizedFishBox(.04f, .42f, .96f, .64f)),
        // Fish close to the source edge.
        SubjectCase(2048, 1365, NormalizedFishBox(.01f, .08f, .35f, .46f)),
        // Centered fish.
        SubjectCase(1152, 1536, NormalizedFishBox(.32f, .30f, .68f, .72f)),
        // Landscape source with a visible subject.
        SubjectCase(1600, 900, NormalizedFishBox(.58f, .24f, .96f, .78f)),
    )

    @Test
    fun allPhysicalViewportsKeepTheWholeSubjectInsideThePrimaryPhoto() {
        physicalSubjects.forEach { subject ->
            viewports.forEach { (width, height) ->
                val plan = RecognitionMediaPlanner.planProcessing(
                    sourceWidth = subject.sourceWidth,
                    sourceHeight = subject.sourceHeight,
                    viewportWidthPx = width,
                    viewportHeightPx = height,
                    bbox = subject.box,
                )
                val transform = plan.primaryTransform
                val mapped = transform.mapBoxRect(subject.box)
                val photo = DisplayRect(
                    transform.translationX,
                    transform.translationY,
                    transform.translationX + transform.drawnWidth,
                    transform.translationY + transform.drawnHeight,
                )

                assertEquals(RecognitionContentScaleMode.FIT, plan.primaryContentScaleMode)
                assertEquals(NormalizedSourceRect(0f, 0f, 1f, 1f), plan.primarySourceRect)
                assertEquals(RecognitionSubjectVisibilityPolicy.SUBJECT_SAFE_FIT, plan.subjectVisibilityPolicy)
                assertTrue(mapped.left >= photo.left - 1f)
                assertTrue(mapped.top >= photo.top - 1f)
                assertTrue(mapped.right <= photo.right + 1f)
                assertTrue(mapped.bottom <= photo.bottom + 1f)
                assertTrue(mapped.left >= -1f)
                assertTrue(mapped.top >= -1f)
                assertTrue(mapped.right <= width + 1f)
                assertTrue(mapped.bottom <= height + 1f)
            }
        }
    }

    @Test
    fun processingTransformIsFrozenWhenDetectorBoxArrivesLater() {
        val beforeDetection = RecognitionMediaPlanner.planProcessing(1152, 1536, 1080f, 2340f)
        val afterDetection = RecognitionMediaPlanner.planProcessing(
            sourceWidth = 1152,
            sourceHeight = 1536,
            viewportWidthPx = 1080f,
            viewportHeightPx = 2340f,
            bbox = NormalizedFishBox(.02f, .24f, .34f, .68f),
        )

        assertEquals(beforeDetection.primaryTransform, afterDetection.primaryTransform)
        assertEquals(beforeDetection.primarySourceRect, afterDetection.primarySourceRect)
        assertEquals(RecognitionContentScaleMode.FIT, beforeDetection.primaryTransform.contentScaleMode)
        assertNotNull(beforeDetection.decorativeBackgroundTransform)
        assertNotNull(afterDetection.decorativeBackgroundTransform)
    }

    @Test
    fun missingDetectorBoxUsesFullSourceSafeFallbackAndDecorativeFillOnly() {
        val plan = RecognitionMediaPlanner.planProcessing(1600, 900, 1080f, 2340f)

        assertEquals(RecognitionSubjectVisibilityPolicy.FULL_SOURCE_SAFE, plan.subjectVisibilityPolicy)
        assertEquals(NormalizedSourceRect(0f, 0f, 1f, 1f), plan.primarySourceRect)
        assertEquals(RecognitionContentScaleMode.FIT, plan.primaryTransform.contentScaleMode)
        assertEquals(RecognitionContentScaleMode.CROP, plan.decorativeBackgroundTransform?.contentScaleMode)
    }

    @Test
    fun compactViewportKeepsStructuralSafeFitPolicy() {
        val plan = RecognitionMediaPlanner.planProcessing(
            sourceWidth = 1600,
            sourceHeight = 900,
            viewportWidthPx = 320f,
            viewportHeightPx = 640f,
            bbox = NormalizedFishBox(.08f, .32f, .72f, .70f),
        )

        assertEquals(RecognitionContentScaleMode.FIT, plan.primaryTransform.contentScaleMode)
        assertEquals(RecognitionSubjectVisibilityPolicy.SUBJECT_SAFE_FIT, plan.subjectVisibilityPolicy)
        assertTrue(plan.primaryTransform.scale.isFinite())
        assertTrue(plan.primaryTransform.translationX.isFinite())
        assertTrue(plan.primaryTransform.translationY.isFinite())
    }
}
