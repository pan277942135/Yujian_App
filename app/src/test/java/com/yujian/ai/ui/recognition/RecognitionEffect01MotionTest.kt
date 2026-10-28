package com.yujian.ai.ui.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertEquals(0xFFFEF08AL, Effect01NativeCalibration.goldColorArgb)
        assertEquals(0xFF38BDF8L, Effect01NativeCalibration.cyanColorArgb)
        assertEquals(0xFF2DD4BFL, Effect01NativeCalibration.greenColorArgb)
        assertEquals(1_100L, UpwardCausticSurgeCalibration.durationMs)
        assertEquals(.025f, UpwardCausticSurgeCalibration.maxArcHeightRatio, 0f)
        assertEquals(.10f, UpwardCausticSurgeCalibration.bodyWidthHeightRatio, 0f)
        assertEquals(.015f, UpwardCausticSurgeCalibration.crestWidthHeightRatio, 0f)
        assertEquals(.08f, UpwardCausticSurgeCalibration.tailWidthHeightRatio, 0f)
        assertEquals(.22f, UpwardCausticSurgeCalibration.maxCompositeOpacity, 0f)
    }

    @Test
    fun upwardWaveIsDeterministicMonotoneAndBounded() {
        val times = listOf(0L, 350L, 600L, 950L, 1_100L)
        val frames = times.map(::upwardCausticFrame)

        assertEquals(0f, frames.first().progress, 0f)
        assertEquals(1f, frames.last().progress, 0f)
        assertEquals(1f, frames.first().centerYFraction, 0f)
        assertEquals(0f, frames.last().centerYFraction, 0f)
        assertEquals(frames[3], upwardCausticFrame(950L))
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
        assertEquals(0f, effect01SpreadProgress(0L), 0f)
        assertEquals(1f, effect01SpreadProgress(1_200L), 0f)
    }

    @Test
    fun shockwaveHonorsSingleImpactBoundariesAndRestoresExactly() {
        assertEquals(PhotoShockwaveTransform(), photoShockwaveTransform(849L))
        assertEquals(PhotoShockwaveTransform(), photoShockwaveTransform(850L))

        val upstroke = photoShockwaveTransform(910L)
        assertEquals(-3f, upstroke.translationYDp, .001f)
        assertEquals(.15f, upstroke.rotationDegrees, .001f)
        assertEquals(1.004f, upstroke.scale, .0001f)

        val rebound = photoShockwaveTransform(970L)
        assertEquals(2f, rebound.translationYDp, .001f)
        assertEquals(-.15f, rebound.rotationDegrees, .001f)
        assertEquals(1f, rebound.scale, .0001f)

        assertEquals(PhotoShockwaveTransform(), photoShockwaveTransform(1_110L))
        assertEquals(
            PhotoShockwaveTransform(),
            photoShockwaveTransform(910L, reduceMotion = true),
        )
    }
}
