package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.RecognitionPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RecognitionProcessingVisualTokensTest {
    @Test
    fun v31StatusCopyKeepsAllFourPipelineBeatsDistinct() {
        assertEquals(
            RecognitionStatusCopy("正在准备识别", "AI 已获取这张照片"),
            recognitionStatusCopy(RecognitionPhase.CAPTURED),
        )
        assertEquals(
            RecognitionStatusCopy("正在理解这张照片", "寻找这次鱼获的线索"),
            recognitionStatusCopy(RecognitionPhase.DETECTING),
        )
        assertEquals(
            RecognitionStatusCopy("已定位到鱼体", "正在分析这次鱼获"),
            recognitionStatusCopy(RecognitionPhase.OUTLINE),
        )
        assertEquals(
            RecognitionStatusCopy("正在认识这条鱼", "分析鱼体特征"),
            recognitionStatusCopy(RecognitionPhase.CLASSIFYING),
        )
        assertNotEquals(
            recognitionStatusCopy(RecognitionPhase.CAPTURED),
            recognitionStatusCopy(RecognitionPhase.DETECTING),
        )
    }

    @Test
    fun energyRingUsesSharedClockAndBecomesSteadyForReduceMotion() {
        assertEquals(-90f, recognitionEnergyRingStartAngle(0L, false), .001f)
        assertEquals(0f, recognitionEnergyRingStartAngle(400L, false), .001f)
        assertEquals(180f, recognitionEnergyRingStartAngle(1_200L, false), .001f)
        assertEquals(-90f, recognitionEnergyRingStartAngle(1_600L, false), .001f)
        assertEquals(-90f, recognitionEnergyRingStartAngle(1_200L, true), .001f)
    }
}
