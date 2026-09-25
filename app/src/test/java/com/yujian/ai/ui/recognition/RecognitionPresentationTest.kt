package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.FishDetectionQualityGate
import com.yujian.ai.ai.FishDetectorEngine
import com.yujian.ai.ai.FishInputStatus
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.RecognitionFailureCode
import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ai.RecognitionTerminal
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionPresentationTest {
    @Test
    fun processingPhasesMapToTheNineStateVocabulary() {
        assertEquals(RecognitionProcessingState.CAPTURE_TRANSITION, RecognitionPresentation.processing(RecognitionPhase.CAPTURED).state)
        assertEquals(RecognitionProcessingState.AI_UNDERSTANDING, RecognitionPresentation.processing(RecognitionPhase.DETECTING).state)
        assertEquals(RecognitionProcessingState.FISH_HIGHLIGHT, RecognitionPresentation.processing(RecognitionPhase.OUTLINE).state)
        assertEquals(RecognitionProcessingState.FISH_IDENTIFYING, RecognitionPresentation.processing(RecognitionPhase.CLASSIFYING).state)
    }

    @Test
    fun terminalRoutesMapToHighMediumLowWithoutRecheckingUiThresholds() {
        val assessment = FishDetectionQualityGate.assess(emptyList())
        fun result(terminal: RecognitionTerminal) = ProductionRecognitionResult(
            status = FishInputStatus.READY,
            detectorRun = FishDetectorEngine.DetectorRun("test", "test", 416, 1f, 1, 1, 1L, emptyList()),
            assessment = assessment,
            prediction = null,
            cropPixels = null,
            terminal = terminal,
        )
        assertEquals(RecognitionResultLevel.HIGH, RecognitionPresentation.resultLevel(result(RecognitionTerminal.SUCCESS)))
        assertEquals(RecognitionResultLevel.MEDIUM, RecognitionPresentation.resultLevel(result(RecognitionTerminal.CONFIRM)))
        assertEquals(RecognitionResultLevel.LOW, RecognitionPresentation.resultLevel(result(RecognitionTerminal.UNKNOWN)))
    }

    @Test
    fun qualityAndNoFishErrorsRemainDistinct() {
        val noFishAssessment = FishDetectionQualityGate.assess(emptyList())
        val noFish = ProductionRecognitionResult(
            FishInputStatus.NO_FISH,
            FishDetectorEngine.DetectorRun("test", "test", 416, 1f, 1, 1, 1L, emptyList()),
            noFishAssessment,
            null,
            null,
            terminal = RecognitionTerminal.NO_FISH,
        )
        val quality = noFish.copy(
            status = FishInputStatus.FISH_TOO_SMALL,
            terminal = RecognitionTerminal.TOO_FAR,
        )
        val technical = quality.copy(failureCode = RecognitionFailureCode.CLASSIFIER_FAILED, terminal = RecognitionTerminal.ERROR)
        assertEquals(RecognitionErrorState.NO_FISH, RecognitionPresentation.errorState(noFish))
        assertEquals(RecognitionErrorState.IMAGE_QUALITY, RecognitionPresentation.errorState(quality))
        assertEquals(RecognitionErrorState.TECHNICAL, RecognitionPresentation.errorState(technical))
    }
}
