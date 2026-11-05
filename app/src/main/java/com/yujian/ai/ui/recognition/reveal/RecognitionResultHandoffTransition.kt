package com.yujian.ai.ui.recognition.reveal

import com.yujian.ai.ui.waterlight.core.RecognitionSceneFrame
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage

enum class RecognitionResultHandoffSubstep(val id: String) {
    RESULT_NAME_HOLD("13.1_RESULT_NAME_HOLD"),
    PHOTO_REPOSITION("13.2_PHOTO_REPOSITION"),
    RESULT_HANDOFF("13.3_RESULT_HANDOFF"),
    RESULT_INFO_ARRIVAL("13.4_RESULT_INFO_ARRIVAL"),
    RESULT_SCENE_STABLE("13.5_RESULT_SCENE_STABLE"),
}

data class RecognitionResultHandoffProjection(
    val substep: RecognitionResultHandoffSubstep?,
    val photoProgress: Float,
    val resultInformationAlpha: Float,
    val interactionEnabled: Boolean,
    /** True only when the result details are fully presented and the settle event may commit once. */
    val resultCommitReady: Boolean,
)

/** Stage 13 is a projection of the shared SceneClock frame; it owns no timer. */
object RecognitionResultHandoffTransition {
    // These are normalized regions of the existing 450ms parent-stage progress,
    // not child-stage durations or a second clock.
    private const val NAME_HOLD_END = .08f
    private const val PHOTO_REPOSITION_END = .68f
    private const val RESULT_HANDOFF_END = .78f

    fun project(
        scene: RecognitionSceneFrame,
        dynamicRevealActive: Boolean,
        photoIdentityMatches: Boolean,
        geometryReady: Boolean,
        resultInformationReady: Boolean,
    ): RecognitionResultHandoffProjection {
        if (!dynamicRevealActive) {
            return RecognitionResultHandoffProjection(
                substep = null,
                photoProgress = 1f,
                resultInformationAlpha = 1f,
                interactionEnabled = true,
                resultCommitReady = false,
            )
        }

        if (scene.stage.ordinal < RecognitionSceneStage.RESIDUE_SETTLE.ordinal) {
            return RecognitionResultHandoffProjection(
                substep = null,
                photoProgress = 0f,
                resultInformationAlpha = 0f,
                interactionEnabled = false,
                resultCommitReady = false,
            )
        }

        if (!photoIdentityMatches || !geometryReady || !resultInformationReady) {
            return RecognitionResultHandoffProjection(
                substep = RecognitionResultHandoffSubstep.RESULT_NAME_HOLD,
                photoProgress = 0f,
                resultInformationAlpha = 0f,
                interactionEnabled = false,
                resultCommitReady = false,
            )
        }

        val progress = scene.stageProgress.coerceIn(0f, 1f)
        if (scene.stageEventComplete && progress >= 1f) {
            return RecognitionResultHandoffProjection(
                substep = RecognitionResultHandoffSubstep.RESULT_SCENE_STABLE,
                photoProgress = 1f,
                resultInformationAlpha = 1f,
                interactionEnabled = true,
                resultCommitReady = false,
            )
        }

        return when {
            progress < NAME_HOLD_END -> RecognitionResultHandoffProjection(
                substep = RecognitionResultHandoffSubstep.RESULT_NAME_HOLD,
                photoProgress = 0f,
                resultInformationAlpha = 0f,
                interactionEnabled = false,
                resultCommitReady = false,
            )
            progress < PHOTO_REPOSITION_END -> {
                val movement = ((progress - NAME_HOLD_END) /
                    (PHOTO_REPOSITION_END - NAME_HOLD_END)).coerceIn(0f, 1f)
                RecognitionResultHandoffProjection(
                    substep = RecognitionResultHandoffSubstep.PHOTO_REPOSITION,
                    photoProgress = smoothStep(movement),
                    resultInformationAlpha = 0f,
                    interactionEnabled = false,
                    resultCommitReady = false,
                )
            }
            progress < RESULT_HANDOFF_END -> RecognitionResultHandoffProjection(
                substep = RecognitionResultHandoffSubstep.RESULT_HANDOFF,
                photoProgress = 1f,
                resultInformationAlpha = 0f,
                interactionEnabled = false,
                resultCommitReady = false,
            )
            else -> {
                val arrival = ((progress - RESULT_HANDOFF_END) /
                    (1f - RESULT_HANDOFF_END)).coerceIn(0f, 1f)
                val alpha = smoothStep(arrival)
                RecognitionResultHandoffProjection(
                    substep = RecognitionResultHandoffSubstep.RESULT_INFO_ARRIVAL,
                    photoProgress = 1f,
                    resultInformationAlpha = alpha,
                    interactionEnabled = false,
                    resultCommitReady = alpha >= 1f,
                )
            }
        }
    }

    private fun smoothStep(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

data class RecognitionResultCommitIdentity(
    val sessionId: String,
    val attemptId: String,
    val imageId: String,
    val photoSha256: String,
) {
    fun isValid(): Boolean = sessionId.isNotBlank() && attemptId.isNotBlank() &&
        imageId.isNotBlank() && photoSha256.isNotBlank()
}

/** Idempotent visual result-completion guard, scoped by the current photo attempt identity. */
class RecognitionResultHandoffCommitGuard {
    private val committed = mutableSetOf<RecognitionResultCommitIdentity>()

    fun claim(identity: RecognitionResultCommitIdentity, ready: Boolean): Boolean =
        ready && identity.isValid() && committed.add(identity)
}
