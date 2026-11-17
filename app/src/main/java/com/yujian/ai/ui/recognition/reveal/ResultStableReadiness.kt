package com.yujian.ai.ui.recognition.reveal

import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage

internal data class OpticalResidueStatus(
    val complete: Boolean,
    val waitingReason: String? = null,
)

internal data class ResultSceneStableStatus(
    val ready: Boolean,
    val waitingReason: String? = null,
)

internal object ResultStableReadiness {
    fun evaluateOpticalResidue(
        stage: RecognitionSceneStage,
        stageEventComplete: Boolean,
        stageProgress: Float,
        afterglowAlpha: Float,
    ): OpticalResidueStatus {
        val reason = when {
            stage != RecognitionSceneStage.RESIDUE_SETTLE -> "STAGE_NOT_RESIDUE_SETTLE"
            !stageEventComplete -> "SETTLE_EVENT_PENDING"
            stageProgress < .999f -> "STAGE_PROGRESS_INCOMPLETE"
            afterglowAlpha > .001f -> "AFTERGLOW_ACTIVE"
            else -> null
        }
        return OpticalResidueStatus(complete = reason == null, waitingReason = reason)
    }

    fun evaluateResultSceneStable(
        opticalResidue: OpticalResidueStatus,
        resultInfoLayoutReady: Boolean,
        resultGeometryReady: Boolean,
        photoTransformAtTarget: Boolean,
        heroRenderReady: Boolean,
        photoProgress: Float,
        resultInfoEntranceProgress: Float,
        eventSequenceSynced: Boolean,
        completedSubstages: Set<ResultHandoffSubstage>,
        alreadyStable: Boolean,
    ): ResultSceneStableStatus {
        val reason = when {
            alreadyStable -> "ALREADY_STABLE"
            !opticalResidue.complete -> opticalResidue.waitingReason
            !resultInfoLayoutReady -> "RESULT_INFO_LAYOUT_PENDING"
            !resultGeometryReady -> "RESULT_GEOMETRY_PENDING"
            !photoTransformAtTarget -> "PHOTO_TRANSFORM_PENDING"
            !heroRenderReady -> "HERO_RENDER_PENDING"
            photoProgress < .999f -> "PHOTO_PROGRESS_INCOMPLETE"
            resultInfoEntranceProgress < .999f -> "INFO_ENTRANCE_INCOMPLETE"
            !eventSequenceSynced -> "HANDOFF_SEQUENCE_UNSYNCED"
            ResultHandoffSubstage.NAME_STABLE !in completedSubstages -> "MISSING_NAME_STABLE"
            ResultHandoffSubstage.PHOTO_SETTLE !in completedSubstages -> "MISSING_PHOTO_SETTLE"
            ResultHandoffSubstage.RESULT_SCENE_HANDOFF !in completedSubstages -> "MISSING_RESULT_SCENE_HANDOFF"
            ResultHandoffSubstage.RESULT_INFO_ARRIVAL !in completedSubstages -> "MISSING_RESULT_INFO_ARRIVAL"
            ResultHandoffSubstage.RESULT_SCENE_STABLE in completedSubstages -> "ALREADY_STABLE"
            else -> null
        }
        return ResultSceneStableStatus(ready = reason == null, waitingReason = reason)
    }
}
