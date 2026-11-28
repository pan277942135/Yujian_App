package com.yujian.ai.ui.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.yujian.ai.ui.waterlight.core.RecognitionSceneCoordinator
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage

class RecognitionEffect01MotionTest {
    @Test
    fun frozenEffect01AndSurgeValuesRemainExact() {
        assertEquals("fish-catch-recognition", Effect01NativeCalibration.sceneId)
        assertEquals("鱼获识别", Effect01NativeCalibration.displayName)
        assertEquals(1_200L, Effect01NativeCalibration.spreadDurationMs)
        assertEquals(1.50f, Effect01NativeCalibration.intensity, 0f)
        assertEquals(1f, Effect01NativeCalibration.speed, 0f)
        assertEquals(.060f, Effect01NativeCalibration.marginRatio, 0f)
        assertEquals(36f, Effect01NativeCalibration.glowSizeDp, 0f)
        assertEquals(.75f, Effect01NativeCalibration.blendRatio, 0f)
        assertEquals(0xFF0B6E69L, Effect01NativeCalibration.deepPeacockColorArgb)
        assertEquals(0xFF4DD0C7L, Effect01NativeCalibration.waterGreenColorArgb)
        assertEquals(0xFFDDF7F5L, Effect01NativeCalibration.iceWhiteColorArgb)
        assertEquals(1_100L, UpwardCausticSurgeCalibration.durationMs)
        assertEquals(.025f, UpwardCausticSurgeCalibration.maxArcHeightRatio, 0f)
        assertEquals(.10f, UpwardCausticSurgeCalibration.bodyWidthHeightRatio, 0f)
        assertEquals(.015f, UpwardCausticSurgeCalibration.crestWidthHeightRatio, 0f)
        assertEquals(.08f, UpwardCausticSurgeCalibration.tailWidthHeightRatio, 0f)
        assertEquals(.22f, UpwardCausticSurgeCalibration.maxCompositeOpacity, 0f)
    }

    @Test
    fun upwardWaveIsDeterministicMonotoneAndBounded() {
        val progress = listOf(0f, .25f, .5f, .9f, 1f)
        val frames = progress.map(::upwardCausticFrame)

        assertEquals(0f, frames.first().progress, 0f)
        assertEquals(1f, frames.last().progress, 0f)
        assertEquals(1f, frames.first().centerYFraction, 0f)
        assertEquals(0f, frames.last().centerYFraction, 0f)
        assertEquals(frames[3], upwardCausticFrame(.9f))
        assertTrue(frames.zipWithNext().all { (a, b) -> b.progress >= a.progress })
        assertTrue(frames.all {
            kotlin.math.abs(it.arcOffsetFraction) <=
                UpwardCausticSurgeCalibration.maxArcHeightRatio
        })
        assertTrue(frames.all {
            it.opacity in 0f..UpwardCausticSurgeCalibration.maxCompositeOpacity
        })
        assertEquals(0f, frames.first().opacity, 0f)
        assertEquals(0f, frames.last().opacity, 0f)
        assertEquals(0f, effect01SpreadProgress(scene(RecognitionSceneStage.PHOTO_STABLE)), 0f)
        assertEquals(.5f, effect01SpreadProgress(scene(RecognitionSceneStage.ORB_RELEASE, 1f)), 0f)
        assertEquals(1f, effect01SpreadProgress(scene(RecognitionSceneStage.PATH_TO_FISH, 1f)), 0f)
    }

    @Test
    fun processingEnvironmentEnvelopeUsesSemanticStageBoundaries() {
        val stages = listOf(
            RecognitionSceneStage.PHOTO_STABLE,
            RecognitionSceneStage.ORB_WAKE,
            RecognitionSceneStage.ORB_RELEASE,
            RecognitionSceneStage.PERIMETER_ARRIVAL,
            RecognitionSceneStage.PATH_TO_FISH,
            RecognitionSceneStage.CONTOUR_A,
            RecognitionSceneStage.CONTOUR_B,
            RecognitionSceneStage.CONTOUR_C,
        )
        val expected = listOf(0f, 10f, 45f, 75f, 60f, 35f, 10f, 0f)
        stages.zip(expected).forEach { (stage, value) ->
            assertEquals(value, processingEnvironmentStrength(scene(stage, 1f)), 0f)
        }
        assertEquals(0f, processingEnvironmentStrength(scene(RecognitionSceneStage.RESIDUE_SETTLE, 1f)), 0f)
    }

    @Test
    fun shockwaveIsBoundToOrbReleaseStageLocalTime() {
        assertEquals(PhotoShockwaveTransform(), photoShockwaveTransform(scene(RecognitionSceneStage.ORB_WAKE, 1f, 350L)))
        assertEquals(PhotoShockwaveTransform(), photoShockwaveTransform(scene(RecognitionSceneStage.ORB_RELEASE, 0f, 0L)))

        val upstroke = photoShockwaveTransform(scene(RecognitionSceneStage.ORB_RELEASE, .1f, 60L))
        assertEquals(-3f, upstroke.translationYDp, .001f)
        assertEquals(.15f, upstroke.rotationDegrees, .001f)
        assertEquals(1.004f, upstroke.scale, .0001f)

        val rebound = photoShockwaveTransform(scene(RecognitionSceneStage.ORB_RELEASE, .2f, 120L))
        assertEquals(2f, rebound.translationYDp, .001f)
        assertEquals(-.15f, rebound.rotationDegrees, .001f)
        assertEquals(1f, rebound.scale, .0001f)

        assertEquals(PhotoShockwaveTransform(), photoShockwaveTransform(scene(RecognitionSceneStage.ORB_RELEASE, .5f, 260L)))
        assertEquals(
            PhotoShockwaveTransform(),
            photoShockwaveTransform(scene(RecognitionSceneStage.ORB_RELEASE, .1f, 60L), reduceMotion = true),
        )
    }

    private fun scene(
        stage: RecognitionSceneStage,
        progress: Float = 0f,
        localElapsedMs: Long = (stage.minimumVisibleMs * progress).toLong(),
    ) = RecognitionSceneCoordinator.frameForStage(
        identity = RecognitionSceneIdentity("session", "attempt", "image", "a".repeat(64), 0L),
        sceneElapsedMs = localElapsedMs,
        opticalReferenceElapsedMs = localElapsedMs,
        geometryVersion = 0L,
        stage = stage,
        stageElapsedMs = localElapsedMs,
        stageProgress = progress,
    )
}
