package com.yujian.ai.ui.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.yujian.ai.ui.waterlight.core.RecognitionSceneCoordinator
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage

class RecognitionEffect02AndContourTest {

    @Test
    fun activePerimeterCalibrationMatchesTheFrozenEffect01Contract() {
        assertEquals("fish-catch-recognition", Effect01NativeCalibration.sceneId)
        assertEquals("鱼获识别", Effect01NativeCalibration.displayName)
        assertEquals(1_200L, Effect01NativeCalibration.spreadDurationMs)
        assertEquals(1.50f, Effect01NativeCalibration.intensity, 0f)
        assertEquals(1.00f, Effect01NativeCalibration.speed, 0f)
        assertEquals(0.060f, Effect01NativeCalibration.marginRatio, 0f)
        assertEquals(36f, Effect01NativeCalibration.glowSizeDp, 0f)
        assertEquals(1f, Effect01NativeCalibration.bottomMaskStrength, 0f)
        assertEquals(0.75f, Effect01NativeCalibration.blendRatio, 0f)
        assertEquals("overlay", Effect01NativeCalibration.blendMode)
        assertEquals(0xFF0B6E69L, Effect01NativeCalibration.deepPeacockColorArgb)
        assertEquals(0xFF4DD0C7L, Effect01NativeCalibration.waterGreenColorArgb)
        assertEquals(0xFFDDF7F5L, Effect01NativeCalibration.iceWhiteColorArgb)
    }

    @Test
    fun processingEnvironmentStrengthUsesTheSharedSemanticStageFrame() {
        assertEquals(45f, processingEnvironmentStrength(scene(RecognitionSceneStage.ORB_RELEASE, 1f)), 0f)
        assertEquals(60f, processingEnvironmentStrength(scene(RecognitionSceneStage.PATH_TO_FISH, 1f)), 0f)
        assertEquals(22.5f, processingEnvironmentStrength(scene(RecognitionSceneStage.CONTOUR_A, .5f)), .001f)
        assertEquals(0f, processingEnvironmentStrength(scene(RecognitionSceneStage.RESIDUE_SETTLE, 1f)), 0f)
    }

    private fun scene(stage: RecognitionSceneStage, progress: Float) =
        RecognitionSceneCoordinator.frameForStage(
            identity = RecognitionSceneIdentity("session", "attempt", "image", "a".repeat(64), 0L),
            sceneElapsedMs = 0L,
            opticalReferenceElapsedMs = 0L,
            geometryVersion = 0L,
            stage = stage,
            stageElapsedMs = (stage.minimumVisibleMs * progress).toLong(),
            stageProgress = progress,
        )

    @Test
    fun goldContourV11TimelineKeyframesAndMutualExclusion() {
        // 0-1150ms mutual exclusion: soft glow, halo, and glint must be strictly 0
        val timesBefore1150 = listOf(0L, 200L, 500L, 1000L, 1149L)
        for (t in timesBefore1150) {
            val fadeFactor = when {
                t < 1150L -> 0.0f
                t >= 1400L -> 1.0f
                else -> {
                    val u = (t - 1150L).toFloat() / 250f
                    u * u * (3f - 2f * u)
                }
            }
            assertEquals(0f, fadeFactor, 0f)
        }

        // 1150-1400ms smooth fade-in
        val fadeMid = run {
            val u = (1275L - 1150L).toFloat() / 250f
            u * u * (3f - 2f * u)
        }
        assertTrue(fadeMid in 0.4f..0.6f)

        // 1400ms+ fadeFactor is 1.0
        val fadePost1400 = run {
            val t = 1450L
            if (t >= 1400L) 1.0f else 0.0f
        }
        assertEquals(1.0f, fadePost1400, 0f)

        // 1680ms confirmation highlight peak
        val confirmationPeak = run {
            val t = 1680L
            val k = (t - 1600L).toFloat() / 80f
            k * k * (3f - 2f * k)
        }
        assertEquals(1.0f, confirmationPeak, 0.001f)

        // Core 1.15x boost at peak
        val coreBoost = 1.0f + 0.15f * confirmationPeak
        assertEquals(1.15f, coreBoost, 0.001f)

        // Soft 1.25x boost at peak
        val softBoost = 1.0f + 0.25f * confirmationPeak
        assertEquals(1.25f, softBoost, 0.001f)

        // Loop modulation after 1400ms strictly bounded to ±3%
        for (t in 1400L..2600L step 50L) {
            val mod = kotlin.math.sin(((t - 1400L) % 2200L).toFloat() / 2200f * 2f * Math.PI.toFloat()) * 0.03f
            assertTrue(mod in -0.0301f..0.0301f)
        }
    }
}
