package com.yujian.ai.ui.designsystem.components.aiorb

import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbRenderVisibilityContractTest {
    @Test
    fun stage01KeepsShaderOffAndWakeUsesNormalizedEightyMillisecondBoundary() {
        assertFalse(aiOrbOpticalEffectEnabled(RecognitionSceneStage.PHOTO_STABLE, 0f))
        assertFalse(aiOrbOpticalEffectEnabled(RecognitionSceneStage.PHOTO_STABLE, 1f))

        val wakeThreshold = 80f / RecognitionSceneStage.ORB_WAKE.minimumVisibleMs
        assertFalse(aiOrbOpticalEffectEnabled(RecognitionSceneStage.ORB_WAKE, 79f / 350f))
        assertTrue(aiOrbOpticalEffectEnabled(RecognitionSceneStage.ORB_WAKE, wakeThreshold))
    }

    @Test
    fun formalAndDiagnosticWakeDurationsShareOneProgressBoundary() {
        val threshold = 80f / RecognitionSceneStage.ORB_WAKE.minimumVisibleMs
        assertFalse(aiOrbOpticalEffectEnabled(RecognitionSceneStage.ORB_WAKE, 79f / 350f))
        assertTrue(aiOrbOpticalEffectEnabled(RecognitionSceneStage.ORB_WAKE, 80f / 350f))
        assertFalse(aiOrbOpticalEffectEnabled(RecognitionSceneStage.ORB_WAKE, 80f / 3000f))
        assertFalse(aiOrbOpticalEffectEnabled(RecognitionSceneStage.ORB_WAKE, 685f / 3000f))
        assertTrue(aiOrbOpticalEffectEnabled(RecognitionSceneStage.ORB_WAKE, 686f / 3000f))
        assertEquals(threshold, 80f / 350f, .000001f)
    }

    @Test
    fun releaseShaderIsAllowedButRawCoreDoesNotDependOnShaderStatus() {
        assertTrue(aiOrbOpticalEffectEnabled(RecognitionSceneStage.ORB_RELEASE, 0f))
        assertTrue(aiOrbRawCoreVisible(OrbRenderIsolationMode.RAW_CORE))
        assertTrue(aiOrbRawCoreVisible(OrbRenderIsolationMode.NORMAL))
        assertFalse(aiOrbRawCoreVisible(OrbRenderIsolationMode.SUPPRESSED))

        assertFalse(aiOrbShaderOverlayVisible(
            renderIsolationMode = OrbRenderIsolationMode.NORMAL,
            opticalEffectEnabled = true,
            shaderAvailable = true,
            renderEffectAvailable = true,
            updateStatus = OrbShaderUpdateStatus.FAILED,
            overlayAlpha = .25f,
        ))
        assertTrue("valid Stage03 source core remains visible after shader failure",
            aiOrbRawCoreVisible(OrbRenderIsolationMode.RAW_CORE))
    }

    @Test
    fun overlayRequiresNormalModeEligibleStageAvailableEffectReadyStatusAndPositiveAlpha() {
        assertTrue(aiOrbShaderOverlayVisible(OrbRenderIsolationMode.NORMAL, true, true, true,
            OrbShaderUpdateStatus.READY, .25f))
        assertFalse(aiOrbShaderOverlayVisible(OrbRenderIsolationMode.RAW_CORE, true, true, true,
            OrbShaderUpdateStatus.READY, .25f))
        assertFalse(aiOrbShaderOverlayVisible(OrbRenderIsolationMode.NORMAL, false, true, true,
            OrbShaderUpdateStatus.READY, .25f))
        assertFalse(aiOrbShaderOverlayVisible(OrbRenderIsolationMode.NORMAL, true, false, true,
            OrbShaderUpdateStatus.READY, .25f))
        assertFalse(aiOrbShaderOverlayVisible(OrbRenderIsolationMode.NORMAL, true, true, false,
            OrbShaderUpdateStatus.READY, .25f))
        assertFalse(aiOrbShaderOverlayVisible(OrbRenderIsolationMode.NORMAL, true, true, true,
            OrbShaderUpdateStatus.UNAVAILABLE, .25f))
        assertFalse(aiOrbShaderOverlayVisible(OrbRenderIsolationMode.NORMAL, true, true, true,
            OrbShaderUpdateStatus.READY, 0f))
    }

    @Test
    fun failedShaderInstanceIsLatchedAndNewAttemptMayCreateANewInstance() {
        val failedInstance = Any()
        val failedGate = OrbShaderUpdateGate(failedInstance)
        var attempts = 0
        val failure = failedGate.update { instance ->
            assertTrue(instance === failedInstance)
            attempts++
            throw IllegalStateException("uniform rejected")
        }
        assertTrue(failure is IllegalStateException)
        assertEquals(OrbShaderUpdateStatus.FAILED, failedGate.status)
        assertEquals(1, attempts)

        assertNull(failedGate.update { attempts++ })
        assertEquals("failed instance must not be retried", 1, attempts)

        val retryInstance = Any()
        val retryGate = OrbShaderUpdateGate(retryInstance)
        assertNull(retryGate.update { instance ->
            assertTrue(instance === retryInstance)
            attempts++
        })
        assertEquals(OrbShaderUpdateStatus.READY, retryGate.status)
        assertEquals(2, attempts)
        assertEquals(OrbShaderUpdateStatus.UNAVAILABLE, OrbShaderUpdateGate<Any>(null).status)
    }

    @Test
    fun completionZeroKeepsParentCompletionAlphaAtOne() {
        assertEquals(1f, aiOrbMotionFrame(0L, false, false, completion = 0f).completionAlpha, 0f)
    }

    @Test
    fun completionCannotHideOrbBeforeGoldPeakAndClampsAfterward() {
        RecognitionSceneStage.entries.takeWhile { it.ordinal <= RecognitionSceneStage.FISH_GOLD_PEAK.ordinal }
            .forEach { stage -> assertEquals(stage.name, 0f, aiOrbPresentationCompletion(stage, 1f), 0f) }
        assertEquals(0.4f, aiOrbPresentationCompletion(RecognitionSceneStage.ORB_RETURN, .4f), 0f)
        assertEquals(1f, aiOrbPresentationCompletion(RecognitionSceneStage.RESIDUE_SETTLE, 2f), 0f)
    }
}
