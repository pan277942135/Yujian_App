package com.yujian.ai.ui.recognition.reveal

import com.yujian.ai.ui.waterlight.core.RecognitionSceneCoordinator
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionResultHandoffTransitionTest {
    private val sceneIdentity = RecognitionSceneIdentity(
        sessionId = "session-a",
        attemptId = "attempt-a",
        imageId = "photo-a",
        photoSha256 = "sha-a",
        photoContentVersion = 4L,
    )

    @Test
    fun stageThirteenProjectsFiveOrderedFramesOnItsSingleParentProgress() {
        val held = project(.01f)
        assertEquals(RecognitionResultHandoffSubstep.RESULT_NAME_HOLD, held.substep)
        assertEquals(0f, held.photoProgress, 0f)
        assertEquals(0f, held.resultInformationAlpha, 0f)

        val moving = project(.38f)
        assertEquals(RecognitionResultHandoffSubstep.PHOTO_REPOSITION, moving.substep)
        assertEquals(.5f, moving.photoProgress, .0001f)
        assertEquals(0f, moving.resultInformationAlpha, 0f)

        val photoProgressSamples = listOf(.08f, .2f, .38f, .5f, .68f).map { progress ->
            project(progress).photoProgress
        }
        assertTrue(photoProgressSamples.zipWithNext().all { (previous, next) -> next >= previous })
        assertEquals(0f, photoProgressSamples.first(), 0f)
        assertEquals(1f, photoProgressSamples.last(), 0f)

        val handedOff = project(.73f)
        assertEquals(RecognitionResultHandoffSubstep.RESULT_HANDOFF, handedOff.substep)
        assertEquals(1f, handedOff.photoProgress, 0f)
        assertEquals(0f, handedOff.resultInformationAlpha, 0f)

        val arriving = project(.89f)
        assertEquals(RecognitionResultHandoffSubstep.RESULT_INFO_ARRIVAL, arriving.substep)
        assertEquals(.5f, arriving.resultInformationAlpha, .0001f)
        assertFalse(arriving.interactionEnabled)
        assertFalse(arriving.resultCommitReady)

        val readyToCommit = project(1f)
        assertEquals(RecognitionResultHandoffSubstep.RESULT_INFO_ARRIVAL, readyToCommit.substep)
        assertEquals(1f, readyToCommit.resultInformationAlpha, 0f)
        assertTrue(readyToCommit.resultCommitReady)
        assertFalse(readyToCommit.interactionEnabled)

        val stable = project(1f, stageEventComplete = true)
        assertEquals(RecognitionResultHandoffSubstep.RESULT_SCENE_STABLE, stable.substep)
        assertEquals(1f, stable.photoProgress, 0f)
        assertEquals(1f, stable.resultInformationAlpha, 0f)
        assertTrue(stable.interactionEnabled)
        assertFalse(stable.resultCommitReady)
    }

    @Test
    fun failedPhotoIdentityGeometryOrResultDataNeverMovesOrCommitsTheHandoff() {
        listOf(
            project(.95f, photoIdentityMatches = false),
            project(.95f, geometryReady = false),
            project(.95f, resultInformationReady = false),
        ).forEach { blocked ->
            assertEquals(RecognitionResultHandoffSubstep.RESULT_NAME_HOLD, blocked.substep)
            assertEquals(0f, blocked.photoProgress, 0f)
            assertEquals(0f, blocked.resultInformationAlpha, 0f)
            assertFalse(blocked.interactionEnabled)
            assertFalse(blocked.resultCommitReady)
        }
    }

    @Test
    fun photoRemainsInProcessingGeometryUntilStageThirteenAndStaticResultsStayUsable() {
        val beforeHandoff = RecognitionResultHandoffTransition.project(
            scene = scene(RecognitionSceneStage.ORB_RETURN, .8f),
            dynamicRevealActive = true,
            photoIdentityMatches = true,
            geometryReady = true,
            resultInformationReady = true,
        )
        assertEquals(0f, beforeHandoff.photoProgress, 0f)
        assertEquals(0f, beforeHandoff.resultInformationAlpha, 0f)
        assertFalse(beforeHandoff.interactionEnabled)

        val static = RecognitionResultHandoffTransition.project(
            scene = scene(RecognitionSceneStage.ORB_RETURN, .8f),
            dynamicRevealActive = false,
            photoIdentityMatches = false,
            geometryReady = false,
            resultInformationReady = false,
        )
        assertNull(static.substep)
        assertEquals(1f, static.photoProgress, 0f)
        assertEquals(1f, static.resultInformationAlpha, 0f)
        assertTrue(static.interactionEnabled)
        assertFalse(static.resultCommitReady)
    }

    @Test
    fun settleCompletionGuardCommitsOncePerPhotoAttemptIdentity() {
        val guard = RecognitionResultHandoffCommitGuard()
        val first = RecognitionResultCommitIdentity("session-a", "attempt-a", "photo-a", "sha-a")
        assertFalse(guard.claim(first, ready = false))
        assertTrue(guard.claim(first, ready = true))
        assertFalse(guard.claim(first, ready = true))
        assertTrue(guard.claim(first.copy(attemptId = "attempt-b"), ready = true))
        assertTrue(guard.claim(first.copy(imageId = "photo-b", photoSha256 = "sha-b"), ready = true))
        assertFalse(guard.claim(first.copy(photoSha256 = ""), ready = true))
    }

    private fun project(
        progress: Float,
        stageEventComplete: Boolean = false,
        photoIdentityMatches: Boolean = true,
        geometryReady: Boolean = true,
        resultInformationReady: Boolean = true,
    ) = RecognitionResultHandoffTransition.project(
        scene = scene(RecognitionSceneStage.RESIDUE_SETTLE, progress, stageEventComplete),
        dynamicRevealActive = true,
        photoIdentityMatches = photoIdentityMatches,
        geometryReady = geometryReady,
        resultInformationReady = resultInformationReady,
    )

    private fun scene(
        stage: RecognitionSceneStage,
        progress: Float,
        stageEventComplete: Boolean = false,
    ) = RecognitionSceneCoordinator.frameForStage(
        identity = sceneIdentity,
        sceneElapsedMs = 6_000L,
        opticalReferenceElapsedMs = 6_000L,
        geometryVersion = 44L,
        stage = stage,
        stageElapsedMs = (progress * stage.minimumVisibleMs).toLong(),
        stageProgress = progress,
        stageEventComplete = stageEventComplete,
    )
}
