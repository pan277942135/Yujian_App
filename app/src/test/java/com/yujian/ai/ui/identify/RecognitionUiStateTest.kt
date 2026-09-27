package com.yujian.ai.ui.identify

import com.yujian.ai.model.RecognitionCandidate
import com.yujian.ai.model.RecognitionPrediction
import com.yujian.ai.ai.FishDetectionQualityGate
import com.yujian.ai.ai.FishDetectorEngine
import com.yujian.ai.ai.FishDetection
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.RecognitionFailureCode
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionUiStateTest {
    @Test
    fun classifierSemanticsMapToFrozenResultStates() {
        assertEquals(RecognitionUiState.RESULT_HIGH, resolveRecognitionResultState(prediction(0.86f, 0.42f)))
        assertEquals(RecognitionUiState.RESULT_MEDIUM, resolveRecognitionResultState(prediction(0.58f, 0.52f)))
        assertEquals(RecognitionUiState.RESULT_LOW, resolveRecognitionResultState(prediction(0.31f, 0.21f)))
    }

    @Test
    fun lowStateDoesNotRequireLegacyMetadataForm() {
        val state = resolveRecognitionResultState(prediction(0.31f, 0.21f))
        assertEquals(RecognitionUiState.RESULT_LOW, state)
        // The Frozen Low screen starts with a species recovery action only;
        // metadata becomes available after selector confirmation in Compose.
    }

    @Test
    fun qualityAndTechnicalFailuresStayOutsideResultStates() {
        val detectorRun = FishDetectorEngine.DetectorRun("test", "sha", 416, 1f, 32, 32, 1L, emptyList())
        val noFishAssessment = FishDetectionQualityGate.assess(emptyList())
        val noFish = ProductionRecognitionResult(
            status = noFishAssessment.status,
            detectorRun = detectorRun,
            assessment = noFishAssessment,
            prediction = null,
            cropPixels = null,
        )
        assertEquals(RecognitionUiState.ERROR_NO_FISH, resolveRecognitionUiState(noFish))

        val imageQualityAssessment = FishDetectionQualityGate.assess(
            listOf(
                FishDetection(0.9f, NormalizedFishBox(0.1f, 0.1f, 0.8f, 0.8f)),
                FishDetection(0.8f, NormalizedFishBox(0.2f, 0.2f, 0.7f, 0.7f)),
            ),
        )
        val imageQuality = noFish.copy(
            status = imageQualityAssessment.status,
            assessment = imageQualityAssessment,
        )
        assertEquals(RecognitionUiState.ERROR_IMAGE_QUALITY, resolveRecognitionUiState(imageQuality))

        val technical = noFish.copy(failureCode = RecognitionFailureCode.INVALID_CROP)
        assertEquals(RecognitionUiState.TECHNICAL_FAILURE, resolveRecognitionUiState(technical))
    }

    private fun prediction(topConfidence: Float, secondConfidence: Float): RecognitionPrediction {
        val top = RecognitionCandidate(0, "grass_carp", "草鱼", topConfidence)
        val second = RecognitionCandidate(1, "crucian_carp", "鲫鱼", secondConfidence)
        return RecognitionPrediction("MODEL_M1_v0.6", "sha", top, listOf(top, second), 1L)
    }
}
