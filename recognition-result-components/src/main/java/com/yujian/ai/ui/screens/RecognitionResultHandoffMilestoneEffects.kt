package com.yujian.ai.ui.screens

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import com.yujian.ai.ui.recognition.diagnostics.RecognitionSessionEvidenceRecorder
import com.yujian.ai.ui.recognition.result.resultSpeciesDisplayName
import com.yujian.ai.ui.recognition.reveal.RecognitionGlyphReadiness
import com.yujian.ai.ui.recognition.reveal.RecognitionGlyphReadinessContract
import com.yujian.ai.ui.recognition.reveal.RecognitionResultInfoRenderContract
import com.yujian.ai.ui.recognition.reveal.RecognitionResultInfoRenderReceipt
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealKey
import com.yujian.ai.ui.recognition.reveal.RecognitionResultSnapshot
import com.yujian.ai.ui.recognition.reveal.ResultHandoffSubstage
import com.yujian.ai.ui.waterlight.core.RecognitionSceneFrame
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.SceneMotionCoordinator

data class RecognitionResultHandoffMilestoneState(
    val handoffKey: RecognitionRevealKey?,
    val frame: RecognitionSceneFrame?,
    val resultSnapshot: RecognitionResultSnapshot?,
    val resultFirstPresentedAtMs: Long?,
    val resultGeometryReady: Boolean,
    val photoProgress: Float,
    val photoTransformAtTarget: Boolean,
    val heroRenderReady: Boolean?,
    val revealTitleReady: Boolean,
    val resultInfoLayoutReady: Boolean,
    val resultSceneHandoffAccepted: Boolean,
    val resultInfoEntranceStartedAtUptimeMs: Long?,
    val resultInfoRenderReceipt: RecognitionResultInfoRenderReceipt?,
    val eventSequence: Long,
    val sceneIdentity: RecognitionSceneIdentity,
    val geometryVersion: Long,
    val expectedSpeciesKey: String,
    val expectedSpeciesName: String,
    val selectedSpeciesKey: String,
    val selectedSpeciesName: String,
    val glyphReadiness: RecognitionGlyphReadiness,
) {
    val completedSubstages: Set<ResultHandoffSubstage>
        get() = resultSnapshot?.handoffState?.completedSubstages.orEmpty()
}

@Composable
fun RecognitionResultHandoffMilestoneEffects(
    state: RecognitionResultHandoffMilestoneState,
    diagnosticPlaybackController: SceneMotionCoordinator?,
    evidenceRecorder: RecognitionSessionEvidenceRecorder?,
    onResultInfoEntranceStarted: (Long) -> Unit,
    onEmitMilestone: (
        ResultHandoffSubstage,
        RecognitionSceneFrame,
        Float,
        RecognitionResultInfoRenderReceipt?,
    ) -> Unit,
) {
    val emitMilestone = rememberUpdatedState(onEmitMilestone)
    val markInfoEntranceStarted = rememberUpdatedState(onResultInfoEntranceStarted)
    val frame = state.frame
    val stage = frame?.stage
    val stageEventComplete = frame?.stageEventComplete
    val completedSubstages = state.completedSubstages
    val photoSettled = state.photoProgress >= .999f
    val result = state.resultSnapshot

    LaunchedEffect(state.handoffKey, stage, stageEventComplete) {
        val currentFrame = frame ?: return@LaunchedEffect
        if (currentFrame.stageEventComplete) {
            diagnosticPlaybackController?.onSceneTransitionComplete(currentFrame.stage, state.sceneIdentity)
        }
    }

    LaunchedEffect(
        state.handoffKey,
        state.resultFirstPresentedAtMs,
        state.resultGeometryReady,
        stage,
        photoSettled,
        result?.resultDataReady,
        completedSubstages,
    ) {
        val key = state.handoffKey ?: return@LaunchedEffect
        val currentFrame = frame ?: return@LaunchedEffect
        val currentResult = result ?: return@LaunchedEffect
        if (state.resultFirstPresentedAtMs != null && state.resultGeometryReady &&
            currentResult.key == key && currentResult.resultDataReady &&
            ResultHandoffSubstage.PHOTO_SETTLE in completedSubstages &&
            ResultHandoffSubstage.RESULT_SCENE_HANDOFF !in completedSubstages &&
            currentFrame.stage == RecognitionSceneStage.RESIDUE_SETTLE && photoSettled
        ) {
            emitMilestone.value(
                ResultHandoffSubstage.RESULT_SCENE_HANDOFF,
                currentFrame,
                1f,
                null,
            )
        }
    }

    LaunchedEffect(
        state.handoffKey,
        stage,
        state.resultGeometryReady,
        state.revealTitleReady,
        state.expectedSpeciesKey,
        state.expectedSpeciesName,
        result?.resultDataReady,
        result?.glyphSpeciesKey,
        result?.glyphSpeciesName,
        state.selectedSpeciesKey,
        state.selectedSpeciesName,
        state.glyphReadiness,
        completedSubstages,
    ) {
        val currentFrame = frame ?: return@LaunchedEffect
        val currentResult = result ?: return@LaunchedEffect
        val expectedName = resultSpeciesDisplayName(state.expectedSpeciesName)
        val actuallyRendered = RecognitionGlyphReadinessContract.isRendered(
            state.sceneIdentity,
            state.expectedSpeciesKey,
            expectedName,
            state.glyphReadiness,
        )
        if (currentFrame.stage == RecognitionSceneStage.SPECIES_NAME_REVEAL && actuallyRendered &&
            state.resultGeometryReady && state.revealTitleReady &&
            currentResult.key == state.handoffKey && currentResult.resultDataReady &&
            ResultHandoffSubstage.NAME_STABLE !in completedSubstages &&
            currentResult.glyphSpeciesKey == state.expectedSpeciesKey &&
            currentResult.glyphSpeciesName == state.expectedSpeciesName &&
            state.selectedSpeciesKey == state.expectedSpeciesKey &&
            resultSpeciesDisplayName(state.selectedSpeciesName) == expectedName
        ) {
            emitMilestone.value(
                ResultHandoffSubstage.NAME_STABLE,
                currentFrame,
                state.glyphReadiness.renderReceipt?.renderedProgress ?: 0f,
                null,
            )
        }
    }

    LaunchedEffect(
        state.handoffKey,
        stage,
        stageEventComplete,
        state.resultGeometryReady,
        photoSettled,
        state.photoTransformAtTarget,
        state.heroRenderReady,
        completedSubstages,
    ) {
        val currentFrame = frame ?: return@LaunchedEffect
        if (currentFrame.stage == RecognitionSceneStage.RESIDUE_SETTLE && state.resultGeometryReady &&
            photoSettled && state.photoTransformAtTarget && state.heroRenderReady == true &&
            ResultHandoffSubstage.NAME_STABLE in completedSubstages &&
            ResultHandoffSubstage.PHOTO_SETTLE !in completedSubstages
        ) {
            emitMilestone.value(
                ResultHandoffSubstage.PHOTO_SETTLE,
                currentFrame,
                state.photoProgress,
                null,
            )
        }
    }

    LaunchedEffect(
        state.handoffKey,
        stage,
        frame?.stageElapsedMs,
        state.resultInfoLayoutReady,
        state.resultSceneHandoffAccepted,
        completedSubstages,
    ) {
        val currentFrame = frame ?: return@LaunchedEffect
        val currentResult = result ?: return@LaunchedEffect
        val key = state.handoffKey ?: return@LaunchedEffect
        if (currentFrame.stage == RecognitionSceneStage.RESIDUE_SETTLE && state.resultInfoLayoutReady &&
            state.resultSceneHandoffAccepted && currentResult.key == key && currentResult.resultDataReady &&
            currentResult.glyphSpeciesName == state.expectedSpeciesName &&
            state.resultInfoEntranceStartedAtUptimeMs == null
        ) {
            val startedAtMs = SystemClock.uptimeMillis()
            markInfoEntranceStarted.value(startedAtMs)
            evidenceRecorder?.recordResultInfoEntranceCheckpoint(
                checkpoint = "INFO_ENTRANCE_STARTED",
                identity = state.sceneIdentity,
                resultIdentity = state.expectedSpeciesKey,
                stage = currentFrame.stage,
                stageProgress = currentFrame.stageProgress,
                entranceProgress = 0f,
                geometryVersion = state.geometryVersion,
                renderStatus = "STARTED_AFTER_RESULT_SCENE_HANDOFF_ACCEPTED",
            )
        }
    }

    LaunchedEffect(
        state.handoffKey,
        stage,
        state.resultInfoRenderReceipt,
        state.eventSequence,
        completedSubstages,
    ) {
        val currentFrame = frame ?: return@LaunchedEffect
        val receipt = state.resultInfoRenderReceipt ?: return@LaunchedEffect
        if (currentFrame.stage == RecognitionSceneStage.RESIDUE_SETTLE &&
            ResultHandoffSubstage.RESULT_SCENE_HANDOFF in completedSubstages &&
            ResultHandoffSubstage.RESULT_INFO_ARRIVAL !in completedSubstages &&
            RecognitionResultInfoRenderContract.isComplete(
                expectedIdentity = state.sceneIdentity,
                expectedResultIdentity = state.expectedSpeciesKey,
                expectedGeometryVersion = state.geometryVersion,
                receipt = receipt,
            )
        ) {
            emitMilestone.value(
                ResultHandoffSubstage.RESULT_INFO_ARRIVAL,
                currentFrame,
                receipt.renderedProgress,
                receipt,
            )
        }
    }
}
