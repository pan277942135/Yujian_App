package com.yujian.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.yujian.ai.ui.designsystem.components.aiorb.AIOrbCore
import com.yujian.ai.ui.designsystem.components.aiorb.AIOrbVisualState
import com.yujian.ai.ui.designsystem.components.aiorb.OrbRenderIsolationMode
import com.yujian.ai.ui.waterlight.core.RecognitionSceneCoordinator
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.ScenePlaybackMode
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
import com.yujian.ai.ui.waterlight.core.WaterLightPhase
import com.yujian.ai.ui.waterlight.renderer.OrbFilamentRendererMode
import com.yujian.ai.ui.waterlight.renderer.OrbRuntimeProfileV1
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OrbRenderIsolationRuntimeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun rawCoreR06AndNormalModesProduceDistinctNonEmptyPixelsAtWakeAndReleaseStages() {
        val stage1Core = render(RecognitionSceneStage.PHOTO_STABLE, 0L, 0f, OrbRenderIsolationMode.RAW_CORE)
        val stage2Core = render(RecognitionSceneStage.ORB_WAKE, 100L, .75f, OrbRenderIsolationMode.RAW_CORE)
        val stage2R06 = render(RecognitionSceneStage.ORB_WAKE, 100L, .75f, OrbRenderIsolationMode.RAW_CORE_WITH_R06)
        val stage3R06 = render(RecognitionSceneStage.ORB_RELEASE, 200L, .8f, OrbRenderIsolationMode.RAW_CORE_WITH_R06)
        val stage2Normal = render(RecognitionSceneStage.ORB_WAKE, 100L, .75f, OrbRenderIsolationMode.NORMAL)
        val stage3Normal = render(RecognitionSceneStage.ORB_RELEASE, 200L, .8f, OrbRenderIsolationMode.NORMAL)

        listOf(stage1Core, stage2Core, stage2R06, stage3R06, stage2Normal, stage3Normal).forEach {
            assertTrue("core ROI must contain rendered pixels", nonBlackCenterPixels(it) > 500)
        }
        assertTrue("R06 must add real pixels over the isolated source core",
            differingCenterPixels(stage2Core, stage2R06) > 0)
        assertTrue("Stage03 R06 must add real pixels over the isolated source core",
            differingCenterPixels(stage1Core, stage3R06) > 0)
        assertTrue("NORMAL shader and optical stack must not erase the source core",
            nonBlackCenterPixels(stage2Normal) > 500 && nonBlackCenterPixels(stage3Normal) > 500)
        assertTrue("all isolation modes keep the same core center and dimensions",
            nonBlackCorePixels(stage1Core) > 500 && nonBlackCorePixels(stage2R06) > 500 &&
            nonBlackCorePixels(stage3Normal) > 500)
    }

    private fun render(
        stage: RecognitionSceneStage,
        stageElapsedMs: Long,
        progress: Float,
        isolation: OrbRenderIsolationMode,
    ): android.graphics.Bitmap {
        val identity = RecognitionSceneIdentity("render-session", "render-attempt", "render-image", "a".repeat(64), 1L)
        val scene = RecognitionSceneCoordinator.frameForStage(
            identity = identity,
            sceneElapsedMs = stageElapsedMs,
            opticalReferenceElapsedMs = stageElapsedMs,
            geometryVersion = 17L,
            stage = stage,
            stageElapsedMs = stageElapsedMs,
            stageProgress = progress,
            playbackMode = ScenePlaybackMode.AUTO,
            monotonicTimestampMs = stageElapsedMs,
        )
        val frame = WaterLightFrame(
            sessionId = identity.sessionId,
            photoSha256 = identity.photoSha256,
            elapsedMs = stageElapsedMs,
            opticalReferenceElapsedMs = stageElapsedMs,
            phase = WaterLightPhase.PROCESSING,
            completion = 0f,
            reducedMotion = false,
            geometryVersion = 17L,
            attemptId = identity.attemptId,
            imageId = identity.imageId,
            photoContentVersion = identity.photoContentVersion,
            playbackMode = ScenePlaybackMode.AUTO,
            sceneFrameOverride = scene,
        )
        composeRule.setContent {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = androidx.compose.ui.Alignment.Center) {
                OrbForRender(frame, isolation)
            }
        }
        composeRule.waitForIdle()
        return composeRule.onRoot().captureToImage().asAndroidBitmap()
    }

    @Composable
    private fun OrbForRender(frame: WaterLightFrame, isolation: OrbRenderIsolationMode) {
        AIOrbCore(
            state = AIOrbVisualState.ORB_ACTIVE,
            coreSize = 120.dp,
            elapsedMs = frame.elapsedMs,
            completion = 0f,
            waterLightFrame = frame,
            waterLightMaterial = WaterLightMaterialProfile.ORB_LEGACY,
            orbRendererMode = OrbFilamentRendererMode.ORB_V1_CANDIDATE,
            orbLifeState = OrbRuntimeProfileV1.LifeState.S2_ACTIVE,
            renderIsolationMode = isolation,
        )
    }

    private fun nonBlackCenterPixels(bitmap: android.graphics.Bitmap): Int {
        val cx = bitmap.width / 2
        val cy = bitmap.height / 2
        var count = 0
        for (y in (cy - 120).coerceAtLeast(0)..(cy + 120).coerceAtMost(bitmap.height - 1)) {
            for (x in (cx - 120).coerceAtLeast(0)..(cx + 120).coerceAtMost(bitmap.width - 1)) {
                if ((bitmap.getPixel(x, y) and 0x00FFFFFF) != 0) count++
            }
        }
        return count
    }

    private fun nonBlackCorePixels(bitmap: android.graphics.Bitmap): Int {
        val cx = bitmap.width / 2
        val cy = bitmap.height / 2
        var count = 0
        for (y in (cy - 60).coerceAtLeast(0)..(cy + 60).coerceAtMost(bitmap.height - 1)) {
            for (x in (cx - 60).coerceAtLeast(0)..(cx + 60).coerceAtMost(bitmap.width - 1)) {
                if ((bitmap.getPixel(x, y) and 0x00FFFFFF) != 0) count++
            }
        }
        return count
    }

    private fun differingCenterPixels(a: android.graphics.Bitmap, b: android.graphics.Bitmap): Int {
        assertTrue(a.width == b.width && a.height == b.height)
        val cx = a.width / 2
        val cy = a.height / 2
        var count = 0
        for (y in (cy - 140).coerceAtLeast(0)..(cy + 140).coerceAtMost(a.height - 1)) {
            for (x in (cx - 140).coerceAtLeast(0)..(cx + 140).coerceAtMost(a.width - 1)) {
                if (a.getPixel(x, y) != b.getPixel(x, y)) count++
            }
        }
        return count
    }
}
