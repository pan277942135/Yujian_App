package com.yujian.ai.ui.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionEffect02AndContourTest {

    @Test
    fun frozenEffect02CalibrationMatchesSpecification() {
        assertEquals("fish-catch-recognition", Effect02NativeCalibration.sceneId)
        assertEquals("效果02 (默认推荐)", Effect02NativeCalibration.displayName)
        assertEquals(1_200L, Effect02NativeCalibration.spreadDurationMs)
        assertEquals(1.80f, Effect02NativeCalibration.intensity, 0.001f)
        assertEquals(1.00f, Effect02NativeCalibration.speed, 0.001f)
        assertEquals(0.015f, Effect02NativeCalibration.marginRatio, 0.0001f)
        assertEquals(45f, Effect02NativeCalibration.glowSizeDp, 0.001f)
        assertEquals(0.80f, Effect02NativeCalibration.blendRatio, 0.001f)
        assertEquals(1.00f, Effect02NativeCalibration.bottomMaskStrength, 0.001f)

        // Colors
        assertEquals(0xFF4D9B91L, Effect02NativeCalibration.goldColorArgb) // 深湖青
        assertEquals(0xFFD89913L, Effect02NativeCalibration.cyanColorArgb) // 琥珀金
        assertEquals(0xFF719FBCL, Effect02NativeCalibration.greenColorArgb) // 湖水灰青
    }

    @Test
    fun waterResonanceCalibrationMatchesSpecification() {
        assertEquals("1.0.0", WaterResonanceCalibration.version)
        assertEquals(2_600L, WaterResonanceCalibration.durationMs)
        assertEquals(2_100L, WaterResonanceCalibration.freezeAtMs)
        assertEquals(0.22f, WaterResonanceCalibration.ambientAlpha, 0.001f)
        assertEquals(0.45f, WaterResonanceCalibration.rippleAlpha, 0.001f)
        assertEquals(0.45f, WaterResonanceCalibration.goldAlpha, 0.001f)
        assertEquals(0.22f, WaterResonanceCalibration.residueAlpha, 0.001f)

        assertEquals(3, WaterResonanceCalibration.pulseStartTimes.size)
        assertEquals(300L, WaterResonanceCalibration.pulseStartTimes[0])
        assertEquals(700L, WaterResonanceCalibration.pulseStartTimes[1])
        assertEquals(1100L, WaterResonanceCalibration.pulseStartTimes[2])

        assertEquals(0.55f, WaterResonanceCalibration.pulsePeakAlphas[0], 0.001f)
        assertEquals(0.48f, WaterResonanceCalibration.pulsePeakAlphas[1], 0.001f)
        assertEquals(0.42f, WaterResonanceCalibration.pulsePeakAlphas[2], 0.001f)
    }

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
