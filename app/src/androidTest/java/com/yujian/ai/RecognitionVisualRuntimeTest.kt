package com.yujian.ai

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yujian.ai.ai.FishDetectionQualityGate
import com.yujian.ai.ai.FishDetectorEngine
import com.yujian.ai.ai.FishDetection
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.screens.RecognizingScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecognitionVisualRuntimeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recognitionScreenRendersContractResultLabel() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val assessment = FishDetectionQualityGate.assess(
            listOf(FishDetection(0.92f, NormalizedFishBox(0.1f, 0.1f, 0.9f, 0.9f))),
        )
        val result = ProductionRecognitionResult(
            status = assessment.status,
            detectorRun = FishDetectorEngine.DetectorRun("test", "test", 416, 1f, 32, 32, 1L, emptyList()),
            assessment = assessment,
            prediction = null,
            cropPixels = null,
        )

        composeRule.setContent {
            RecognizingScreen(
                image = SelectedImage("runtime-test", bitmap, "instrumentation"),
                onBack = {},
                recognize = { onProgress ->
                    onProgress(com.yujian.ai.ai.RecognitionProgress(RecognitionPhase.OUTLINE, assessment))
                    result
                },
                onFinished = {},
                phaseOverride = RecognitionPhase.RESULT,
            )
        }

        composeRule.onNodeWithText("认识完成").assertExists()
        bitmap.recycle()
    }
}
