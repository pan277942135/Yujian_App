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
    fun defaultProfileMatchesFrozenEffect02AndKeepsEffect01ForRegression() {
        val effect02 = RecognitionPerimeterOpticalProfileV1.EFFECT_02
        assertEquals(RecognitionPerimeterOpticalProfileV1.Version.EFFECT_02, effect02.version)
        assertEquals(1.8f, effect02.intensity, 0f)
        assertEquals(1f, effect02.speed, 0f)
        assertEquals(.015f, effect02.marginRatio, 0f)
        assertEquals(.12f, effect02.cornerRadiusRatio, 0f)
        assertEquals(45f, effect02.glowSizeSourcePx, 0f)
        assertEquals(.80f, effect02.blendRatio, 0f)
        assertEquals(1f, effect02.bottomMaskStrength, 0f)
        assertEquals(0xFF4D9B91L, effect02.goldColor)
        assertEquals(0xFFD89913L, effect02.cyanColor)
        assertEquals(0xFF719FBCL, effect02.greenColor)
        assertEquals(.85f, effect02.causticsDensityReferenceOnly, 0f)

        val effect01 = RecognitionPerimeterOpticalProfileV1.EFFECT_01
        assertEquals(RecognitionPerimeterOpticalProfileV1.Version.EFFECT_01, effect01.version)
        assertEquals(1.5f, effect01.intensity, 0f)
        assertEquals(48f, effect01.glowSizeSourcePx, 0f)
        assertEquals(.75f, effect01.blendRatio, 0f)
        assertEquals(0xFFFEF08AL, effect01.goldColor)
        assertEquals(0xFF38BDF8L, effect01.cyanColor)
        assertEquals(0xFF2DD4BFL, effect01.greenColor)
    }

    @Test
    fun perimeterExpansionStaysContinuousAcrossExistingReleaseAndArrivalStages() {
        val profile = RecognitionPerimeterOpticalProfileV1.EFFECT_02
        assertEquals(0f, RecognitionPerimeterShaderMath.expansionProgress(
            frame(RecognitionSceneStage.ORB_RELEASE, 0f).sceneFrame, profile,
        ), 0f)
        assertEquals(.36f, RecognitionPerimeterShaderMath.expansionProgress(
            frame(RecognitionSceneStage.ORB_RELEASE, .5f).sceneFrame, profile,
        ), .0001f)
        val releaseEnd = RecognitionPerimeterShaderMath.expansionProgress(
            frame(RecognitionSceneStage.ORB_RELEASE, 1f).sceneFrame, profile,
        )
        val arrivalStart = RecognitionPerimeterShaderMath.expansionProgress(
            frame(RecognitionSceneStage.PERIMETER_ARRIVAL, 0f).sceneFrame, profile,
        )
        assertEquals(profile.releaseExpansionShare, releaseEnd, 0f)
        assertEquals(releaseEnd, arrivalStart, 0f)
        assertEquals(.86f, RecognitionPerimeterShaderMath.expansionProgress(
            frame(RecognitionSceneStage.PERIMETER_ARRIVAL, .5f).sceneFrame, profile,
        ), .0001f)
        assertEquals(1f, RecognitionPerimeterShaderMath.expansionProgress(
            frame(RecognitionSceneStage.PERIMETER_ARRIVAL, 1f).sceneFrame, profile,
        ), 0f)
        assertEquals(1f, RecognitionPerimeterShaderMath.expansionProgress(
            frame(RecognitionSceneStage.PATH_TO_FISH, 0f).sceneFrame, profile,
        ), 0f)
    }

    @Test
    fun sourceTimeUsesOnlyTheSharedSceneOpticalReference() {
        val scene = frame(RecognitionSceneStage.CONTOUR_B, .5f, opticalReferenceMs = 2_600L).sceneFrame
        assertEquals(2.6f, RecognitionPerimeterShaderMath.sourceTimeSec(scene), .0001f)
        assertEquals(5.2f, RecognitionPerimeterShaderMath.sourceTimeSec(scene, speed = 2f), .0001f)
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
        assertEquals(0f, recognitionPerimeterOpacity(true, false, frame(RecognitionSceneStage.ORB_RELEASE, 0f)), 0f)
        assertEquals(0f, recognitionPerimeterOpacity(true, false, frame(RecognitionSceneStage.ORB_RELEASE, 1f)), 0f)
        assertEquals(0f, recognitionPerimeterOpacity(true, true, frame(RecognitionSceneStage.ORB_RELEASE, 1f)), 0f)
        assertTrue(recognitionPerimeterOpacity(true, false, frame(RecognitionSceneStage.PERIMETER_ARRIVAL, .5f)) > 0f)
        assertEquals(0f, recognitionPerimeterOpacity(true, true, frame(RecognitionSceneStage.PHOTO_STABLE, 0f)), 0f)
        assertEquals(.18f, recognitionPerimeterOpacity(true, false, frame(RecognitionSceneStage.ORB_RETURN, 1f)), .0001f)
        assertEquals(0f, recognitionPerimeterOpacity(true, false, frame(RecognitionSceneStage.ORB_TO_NAME, 1f)), 0f)
        assertTrue(recognitionPerimeterActive(true, RecognitionPhase.CLASSIFYING))
        assertFalse(recognitionPerimeterActive(true, RecognitionPhase.RESULT))
    }

    private fun frame(stage: RecognitionSceneStage, progress: Float, opticalReferenceMs: Long = 0L) = WaterLightFrame(
        sessionId = "session",
        photoSha256 = "a".repeat(64),
        elapsedMs = 0L,
        opticalReferenceElapsedMs = opticalReferenceMs,
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
