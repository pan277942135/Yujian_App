package com.yujian.ai.ai.subject

import org.junit.Assert.assertEquals
import org.junit.Test

class SlimSamPromptMapperTest {
    @Test fun horizontalFishPromptsMatchReferenceOrderAndPerAxisResizeMapping() {
        val points = SlimSamPromptMapper.fromDetection(
            SlimSamMaskQualityGate.PixelRect(10, 20, 110, 60),
            imageWidth = 200,
            imageHeight = 100,
            resizedWidth = 1024,
            resizedHeight = 512,
        )

        assertEquals(7, points.size)
        assertEquals(SlimSamPromptMapper.PromptPoint(307.2f, 204.8f, 1L), points[0])
        assertEquals(SlimSamPromptMapper.PromptPoint(153.6f, 204.8f, 1L), points[1])
        assertEquals(SlimSamPromptMapper.PromptPoint(245.76f, 204.8f, 1L), points[2])
        assertEquals(SlimSamPromptMapper.PromptPoint(368.64f, 204.8f, 1L), points[3])
        assertEquals(SlimSamPromptMapper.PromptPoint(460.8f, 204.8f, 1L), points[4])
        assertEquals(SlimSamPromptMapper.PromptPoint(307.2f, 163.84f, 1L), points[5])
        assertEquals(SlimSamPromptMapper.PromptPoint(307.2f, 245.76f, 1L), points[6])
    }

    @Test fun verticalFishUsesNegativeGripPointAndClampsToPhotoBeforeMapping() {
        val points = SlimSamPromptMapper.fromDetection(
            SlimSamMaskQualityGate.PixelRect(0, 0, 40, 120),
            imageWidth = 100,
            imageHeight = 200,
            resizedWidth = 512,
            resizedHeight = 1024,
        )

        assertEquals(8, points.size)
        assertEquals(0L, points.last().label)
        assertEquals(102.4f, points.last().x, 0f)
        assertEquals(0f, points.last().y, 0f)
        assertEquals(SlimSamPromptMapper.PromptPoint(102.4f, 343.04f, 1L), points[0])
    }
}
