package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.RecognitionPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionProcessingVisualTokensTest {
    @Test
    fun capturedAndDetectingUseOneStableProductCopy() {
        assertEquals(
            RecognitionStatusCopy("图片识别中", "正在理解照片并寻找鱼获线索"),
            recognitionStatusCopy(RecognitionPhase.CAPTURED),
        )
        assertEquals(
            RecognitionStatusCopy("图片识别中", "正在理解照片并寻找鱼获线索"),
            recognitionStatusCopy(RecognitionPhase.DETECTING),
        )
    }

    @Test
    fun locatedAndClassifyingCopyStayBoundToThreeStatePresentation() {
        assertEquals(
            RecognitionStatusCopy("已定位到鱼体", "正在分析这次鱼获"),
            recognitionStatusCopy(RecognitionPhase.OUTLINE),
        )
        assertEquals(
            RecognitionStatusCopy("正在认识这条鱼", "分析鱼体特征"),
            recognitionStatusCopy(RecognitionPhase.CLASSIFYING),
        )
    }
}

