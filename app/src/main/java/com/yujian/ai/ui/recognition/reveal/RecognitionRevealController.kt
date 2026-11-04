package com.yujian.ai.ui.recognition.reveal

import com.yujian.ai.ui.waterlight.core.RecognitionSceneFrame
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage

enum class RecognitionRevealPhase {
    PHOTO_CONTINUITY,
    ORB_RETURN,
    ORB_TO_NAME,
    SPECIES_NAME_REVEAL,
    RESIDUE_SETTLE,
    COMPLETE,
}

enum class RecognitionRevealTitleMode { STATIC, HIDDEN, FORMING, REVEALING, VISIBLE }

data class RecognitionRevealMotionPolicy(
    val reduceMotion: Boolean = false,
    val lowPerformance: Boolean = false,
)

data class RecognitionRevealFrame(
    /** Local elapsed time for the semantic stage carried by [stage]. */
    val elapsedMs: Long,
    val stage: RecognitionSceneStage,
    val stageProgress: Float,
    val phase: RecognitionRevealPhase,
    val photoProgress: Float,
    val fishHaloAlpha: Float,
    val transferProgress: Float,
    val titleMode: RecognitionRevealTitleMode,
    val titleFormationProgress: Float,
    val titleRevealProgress: Float,
    val titleSweepProgress: Float,
    val afterglowAlpha: Float,
    val visibleParticleCount: Int,
    val drawOpticalLayer: Boolean,
    val fallback: Boolean,
    val ctaEnabled: Boolean = true,
) {
    val isComplete: Boolean get() = phase == RecognitionRevealPhase.COMPLETE
    val orbReturnProgress: Float get() = transferProgress
    val orbToNameProgress: Float get() = titleFormationProgress
}

/** Pure projection of the shared semantic scene frame. It owns no timer or completion deadline. */
object RecognitionRevealClock {
    const val FULL_PARTICLE_LIMIT = 18
    const val LOW_PARTICLE_LIMIT = 6

    fun evaluate(
        scene: RecognitionSceneFrame,
        handoff: RecognitionRevealHandoff?,
        policy: RecognitionRevealMotionPolicy,
        alreadyPlayed: Boolean = false,
    ): RecognitionRevealFrame {
        val stage = scene.stage
        val progress = scene.stageProgress.coerceIn(0f, 1f)
        val eligibility = handoff?.eligibility ?: RecognitionRevealEligibility.STATIC_RESULT
        val fullReveal = eligibility == RecognitionRevealEligibility.FULL_REVEAL
        val fallback = eligibility == RecognitionRevealEligibility.TITLE_FADE_ONLY
        val revealEligible = fullReveal || fallback
        val isFinalStage = stage == RecognitionSceneStage.RESIDUE_SETTLE
        val complete = alreadyPlayed || (isFinalStage && scene.stageEventComplete)
        val particleLimit = if (policy.lowPerformance) LOW_PARTICLE_LIMIT else FULL_PARTICLE_LIMIT

        if (!revealEligible || policy.reduceMotion || complete) {
            return frame(
                scene = scene,
                phase = if (revealEligible && !complete && !policy.reduceMotion) phaseFor(stage) else RecognitionRevealPhase.COMPLETE,
                photoProgress = if (complete || !revealEligible || policy.reduceMotion) 1f else progress,
                fishHaloAlpha = 0f,
                transferProgress = 0f,
                titleMode = when {
                    eligibility == RecognitionRevealEligibility.TITLE_FADE_ONLY -> RecognitionRevealTitleMode.VISIBLE
                    fullReveal -> RecognitionRevealTitleMode.VISIBLE
                    eligibility == RecognitionRevealEligibility.STATIC_RESULT ||
                        eligibility == RecognitionRevealEligibility.PHOTO_ONLY -> RecognitionRevealTitleMode.STATIC
                    else -> RecognitionRevealTitleMode.HIDDEN
                },
                titleFormationProgress = if (fullReveal) 1f else 0f,
                titleRevealProgress = 1f,
                titleSweepProgress = 0f,
                afterglowAlpha = 0f,
                visibleParticleCount = 0,
                drawOpticalLayer = false,
                fallback = fallback || (fullReveal && policy.reduceMotion),
            )
        }

        if (stage.ordinal < RecognitionSceneStage.ORB_RETURN.ordinal) {
            return frame(
                scene = scene,
                phase = RecognitionRevealPhase.PHOTO_CONTINUITY,
                photoProgress = 0f,
                fishHaloAlpha = if (fullReveal) 1f else 0f,
                transferProgress = 0f,
                titleMode = RecognitionRevealTitleMode.HIDDEN,
                titleFormationProgress = 0f,
                titleRevealProgress = 0f,
                titleSweepProgress = 0f,
                afterglowAlpha = 1f,
                visibleParticleCount = 0,
                drawOpticalLayer = false,
                fallback = fallback,
            )
        }

        val phase = phaseFor(stage)
        val returnProgress = if (stage == RecognitionSceneStage.ORB_RETURN) progress else 1f
        val formationProgress = if (stage == RecognitionSceneStage.ORB_TO_NAME) progress else if (
            stage.ordinal > RecognitionSceneStage.ORB_TO_NAME.ordinal
        ) 1f else 0f
        val titleRevealProgress = if (stage == RecognitionSceneStage.SPECIES_NAME_REVEAL) progress else if (
            stage.ordinal > RecognitionSceneStage.SPECIES_NAME_REVEAL.ordinal
        ) 1f else 0f
        val residueProgress = if (stage == RecognitionSceneStage.RESIDUE_SETTLE) progress else 0f
        val afterglow = if (stage.ordinal < RecognitionSceneStage.RESIDUE_SETTLE.ordinal) 1f else 1f - residueProgress
        val particleCount = when {
            stage != RecognitionSceneStage.ORB_RETURN || !fullReveal -> 0
            else -> (particleLimit * (1f - returnProgress)).toInt().coerceIn(0, particleLimit)
        }
        val titleMode = when (stage) {
            RecognitionSceneStage.ORB_RETURN -> RecognitionRevealTitleMode.HIDDEN
            RecognitionSceneStage.ORB_TO_NAME -> RecognitionRevealTitleMode.FORMING
            RecognitionSceneStage.SPECIES_NAME_REVEAL -> RecognitionRevealTitleMode.REVEALING
            RecognitionSceneStage.RESIDUE_SETTLE -> RecognitionRevealTitleMode.VISIBLE
            else -> RecognitionRevealTitleMode.HIDDEN
        }
        return frame(
            scene = scene,
            phase = phase,
            photoProgress = if (stage == RecognitionSceneStage.ORB_RETURN) smoothStep(progress) else 1f,
            fishHaloAlpha = if (fullReveal && stage == RecognitionSceneStage.ORB_RETURN) 1f - smoothStep(progress) else 0f,
            transferProgress = smoothStep(returnProgress),
            titleMode = titleMode,
            titleFormationProgress = smoothStep(formationProgress),
            titleRevealProgress = easeOutCubic(titleRevealProgress),
            titleSweepProgress = if (stage == RecognitionSceneStage.SPECIES_NAME_REVEAL) smoothStep(progress) else 0f,
            afterglowAlpha = afterglow.coerceIn(0f, 1f),
            visibleParticleCount = particleCount,
            drawOpticalLayer = stage.ordinal <= RecognitionSceneStage.RESIDUE_SETTLE.ordinal && !complete,
            fallback = fallback,
        )
    }

    private fun frame(
        scene: RecognitionSceneFrame,
        phase: RecognitionRevealPhase,
        photoProgress: Float,
        fishHaloAlpha: Float,
        transferProgress: Float,
        titleMode: RecognitionRevealTitleMode,
        titleFormationProgress: Float,
        titleRevealProgress: Float,
        titleSweepProgress: Float,
        afterglowAlpha: Float,
        visibleParticleCount: Int,
        drawOpticalLayer: Boolean,
        fallback: Boolean,
    ) = RecognitionRevealFrame(
        elapsedMs = scene.stageElapsedMs,
        stage = scene.stage,
        stageProgress = scene.stageProgress,
        phase = phase,
        photoProgress = photoProgress.coerceIn(0f, 1f),
        fishHaloAlpha = fishHaloAlpha.coerceIn(0f, 1f),
        transferProgress = transferProgress.coerceIn(0f, 1f),
        titleMode = titleMode,
        titleFormationProgress = titleFormationProgress.coerceIn(0f, 1f),
        titleRevealProgress = titleRevealProgress.coerceIn(0f, 1f),
        titleSweepProgress = titleSweepProgress.coerceIn(0f, 1f),
        afterglowAlpha = afterglowAlpha.coerceIn(0f, 1f),
        visibleParticleCount = visibleParticleCount,
        drawOpticalLayer = drawOpticalLayer,
        fallback = fallback,
    )

    private fun phaseFor(stage: RecognitionSceneStage): RecognitionRevealPhase = when (stage) {
        RecognitionSceneStage.ORB_RETURN -> RecognitionRevealPhase.ORB_RETURN
        RecognitionSceneStage.ORB_TO_NAME -> RecognitionRevealPhase.ORB_TO_NAME
        RecognitionSceneStage.SPECIES_NAME_REVEAL -> RecognitionRevealPhase.SPECIES_NAME_REVEAL
        RecognitionSceneStage.RESIDUE_SETTLE -> RecognitionRevealPhase.RESIDUE_SETTLE
        else -> RecognitionRevealPhase.PHOTO_CONTINUITY
    }

    private fun smoothStep(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun easeOutCubic(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return 1f - (1f - t) * (1f - t) * (1f - t)
    }
}
