package com.yujian.ai.ui.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightPhase

class RecognitionPerimeterShaderMathTest {
    @Test
    fun spreadIsLinearAtRequiredCaptureTimesAndStaysComplete() {
        val times = listOf(0L, 300L, 600L, 900L, 1_200L, 1_800L, 2_600L)
        val progress = times.map(RecognitionPerimeterShaderMath::linearProgress)

        assertEquals(listOf(0f, .25f, .5f, .75f, 1f, 1f, 1f), progress)
        assertEquals(0f, RecognitionPerimeterShaderMath.sourceTimeSec(0L), 0f)
        assertEquals(2.6f, RecognitionPerimeterShaderMath.sourceTimeSec(2_600L), .0001f)
        assertEquals(5.2f, RecognitionPerimeterShaderMath.sourceTimeSec(2_600L, speed = 2f), .0001f)
    }

    @Test
    fun measuredCoordinateScaleMapsCappedSourceBackingUnitsUniformly() {
        val metrics = RecognitionPerimeterReferenceMetrics(
            devicePixelRatio = 3f,
            cssWidthPx = 400f,
            cssHeightPx = 800f,
            backingWidthPx = 800f,
            backingHeightPx = 1_600f,
        )

        val mapping = RecognitionPerimeterShaderMath.coordinateScale(
            metrics = metrics,
            targetWidthPx = 600f,
            targetHeightPx = 1_200f,
        )

        assertTrue(mapping.isMeasured)
        assertEquals(2f, mapping.sourceDprCap, 0f)
        assertEquals(1.5f, mapping.pathPxPerCssUnit, .0001f)
        assertEquals(.75f, mapping.shaderPxPerSourceUnit, .0001f)
    }

    @Test
    fun missingSourceMetricsAreExplicitlyUnmeasured() {
        val mapping = RecognitionPerimeterShaderMath.coordinateScale(
            metrics = null,
            targetWidthPx = 600f,
            targetHeightPx = 1_200f,
        )

        assertFalse(mapping.isMeasured)
        assertEquals(1f, mapping.shaderPxPerSourceUnit, 0f)
        assertEquals(1f, mapping.pathPxPerCssUnit, 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnAspectRatioMismatchInsteadOfDistortingThePerimeter() {
        RecognitionPerimeterShaderMath.coordinateScale(
            metrics = RecognitionPerimeterReferenceMetrics(
                devicePixelRatio = 2f,
                cssWidthPx = 400f,
                cssHeightPx = 800f,
                backingWidthPx = 800f,
                backingHeightPx = 1_600f,
            ),
            targetWidthPx = 600f,
            targetHeightPx = 900f,
        )
    }

    @Test
    fun sourcePerimeterHas280SamplesAndStartsAtBottomCenter() {
        val path = RecognitionPerimeterShaderMath.generatePerimeterPath(400f, 800f)

        assertEquals(280, path.size)
        assertEquals(200f, path.first().x, .0001f)
        assertEquals(794f, path.first().y, .0001f)
        assertTrue(path.any { it.x > 393f && it.y > 394f })
        assertTrue(path.any { it.x < 7f && it.y < 400f })
    }

    @Test
    fun sixteenDeterministicSparklesExpandFromBothBottomBranches() {
        val path = RecognitionPerimeterShaderMath.generatePerimeterPath(400f, 800f)
        val first = RecognitionPerimeterShaderMath.sparkles(
            path = path,
            timeSec = 0f,
            activeCoverage = .25f,
            pathScale = 1.5f,
            photoOffsetX = 10f,
            photoOffsetY = 20f,
        )
        val repeat = RecognitionPerimeterShaderMath.sparkles(
            path = path,
            timeSec = 0f,
            activeCoverage = .25f,
            pathScale = 1.5f,
            photoOffsetX = 10f,
            photoOffsetY = 20f,
        )

        assertEquals(16, first.size)
        assertEquals(first, repeat)
        assertTrue(first[0].active)
        assertTrue(first[1].active)
        assertTrue(first[2].active)
        assertFalse(first[7].active)
        assertTrue(first[15].active)
        assertEquals(310f, first[0].x, .001f)
        assertEquals(1_211f, first[0].y, .001f)
    }

    @Test
    fun perimeterLifetimeFollowsRecognitionPhaseInsteadOfAnAbsoluteElapsedCutoff() {
        assertTrue(recognitionPerimeterActive(true, RecognitionPhase.CLASSIFYING))
        assertEquals(0f, recognitionPerimeterOpacity(true, false, frame(RecognitionSceneStage.ORB_WAKE, 1f)), 0f)
        assertEquals(.12f, recognitionPerimeterOpacity(true, false, frame(RecognitionSceneStage.ORB_RELEASE, 0f)), .0001f)
        assertEquals(1f, recognitionPerimeterOpacity(true, false, frame(RecognitionSceneStage.ORB_RELEASE, 1f)), 0f)
        assertEquals(.10f, recognitionPerimeterOpacity(true, true, frame(RecognitionSceneStage.ORB_RELEASE, 0f)), .0001f)
        assertTrue(recognitionPerimeterActive(true, RecognitionPhase.CLASSIFYING))
        assertFalse(recognitionPerimeterActive(true, RecognitionPhase.RESULT))
    }

    private fun frame(stage: RecognitionSceneStage, progress: Float) = WaterLightFrame(
        sessionId = "session",
        photoSha256 = "a".repeat(64),
        elapsedMs = 0L,
        opticalReferenceElapsedMs = 0L,
        phase = WaterLightPhase.PROCESSING,
        completion = 0f,
        reducedMotion = false,
        geometryVersion = 0L,
        attemptId = "attempt",
        imageId = "image",
        sceneStageOverride = stage,
        sceneStageElapsedMsOverride = (stage.minimumVisibleMs * progress).toLong(),
        sceneStageProgressOverride = progress,
    )
}
