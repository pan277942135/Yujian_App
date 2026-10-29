package com.yujian.ai.ui.recognition.reveal

enum class RecognitionRevealPhase {
    PHOTO_CONTINUITY,
    GOLD_TRANSFER,
    NAME_FORMATION,
    NAME_REVEAL,
    AFTERGLOW,
    COMPLETE,
}

enum class RecognitionRevealTitleMode {
    STATIC,
    HIDDEN,
    FORMING,
    REVEALING,
    VISIBLE,
}

data class RecognitionRevealMotionPolicy(
    val reduceMotion: Boolean = false,
    val lowPerformance: Boolean = false,
)

data class RecognitionRevealFrame(
    val elapsedMs: Long,
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
}

object RecognitionRevealClock {
    const val TOTAL_DURATION_MS = 850L
    const val PHOTO_END_MS = 200L
    const val GOLD_TRANSFER_END_MS = 380L
    const val NAME_FORMATION_END_MS = 550L
    const val NAME_REVEAL_END_MS = 680L
    const val FULL_PARTICLE_LIMIT = 18
    const val LOW_PARTICLE_LIMIT = 6

    fun evaluate(
        elapsedMs: Long,
        handoff: RecognitionRevealHandoff?,
        policy: RecognitionRevealMotionPolicy,
    ): RecognitionRevealFrame {
        val t = elapsedMs.coerceAtLeast(0L)
        val eligibility = handoff?.eligibility ?: RecognitionRevealEligibility.STATIC_RESULT
        val canPhotoTransition = handoff?.processingPhotoViewportBounds != null &&
            handoff.processingPhotoTransform != null &&
            (eligibility == RecognitionRevealEligibility.FULL_REVEAL ||
                eligibility == RecognitionRevealEligibility.TITLE_FADE_ONLY)
        val fullReveal = eligibility == RecognitionRevealEligibility.FULL_REVEAL
        val fallback = eligibility == RecognitionRevealEligibility.TITLE_FADE_ONLY
        val photoProgress = when {
            !canPhotoTransition || policy.reduceMotion || t >= PHOTO_END_MS -> 1f
            else -> smoothStep(t.toFloat() / PHOTO_END_MS)
        }

        if (policy.reduceMotion || t >= TOTAL_DURATION_MS) {
            return RecognitionRevealFrame(
                elapsedMs = t,
                phase = if (t >= TOTAL_DURATION_MS) RecognitionRevealPhase.COMPLETE else staticPhase(eligibility),
                photoProgress = 1f,
                fishHaloAlpha = 0f,
                transferProgress = 0f,
                titleMode = when (eligibility) {
                    RecognitionRevealEligibility.TITLE_FADE_ONLY -> RecognitionRevealTitleMode.VISIBLE
                    RecognitionRevealEligibility.STATIC_RESULT -> RecognitionRevealTitleMode.STATIC
                    else -> if (fullReveal) RecognitionRevealTitleMode.VISIBLE else RecognitionRevealTitleMode.STATIC
                },
                titleFormationProgress = 0f,
                titleRevealProgress = 1f,
                titleSweepProgress = 0f,
                afterglowAlpha = 0f,
                visibleParticleCount = 0,
                drawOpticalLayer = false,
                fallback = fallback || (fullReveal && policy.reduceMotion),
            )
        }

        if (!fullReveal) {
            val fallbackProgress = when {
                !fallback || t < NAME_FORMATION_END_MS -> 0f
                t < NAME_REVEAL_END_MS ->
                    ((t - NAME_FORMATION_END_MS).toFloat() /
                        (NAME_REVEAL_END_MS - NAME_FORMATION_END_MS)).coerceIn(0f, 1f)
                else -> 1f
            }
            val fallbackFormation = if (!fallback || t < GOLD_TRANSFER_END_MS) 0f else
                ((t - GOLD_TRANSFER_END_MS).toFloat() /
                    (NAME_FORMATION_END_MS - GOLD_TRANSFER_END_MS)).coerceIn(0f, 1f)
            return RecognitionRevealFrame(
                elapsedMs = t,
                phase = when {
                    t < PHOTO_END_MS -> RecognitionRevealPhase.PHOTO_CONTINUITY
                    t < GOLD_TRANSFER_END_MS -> RecognitionRevealPhase.GOLD_TRANSFER
                    t < NAME_FORMATION_END_MS -> RecognitionRevealPhase.NAME_FORMATION
                    t < NAME_REVEAL_END_MS -> RecognitionRevealPhase.NAME_REVEAL
                    t < TOTAL_DURATION_MS -> RecognitionRevealPhase.AFTERGLOW
                    else -> RecognitionRevealPhase.COMPLETE
                },
                photoProgress = photoProgress,
                fishHaloAlpha = 0f,
                transferProgress = 0f,
                titleMode = when {
                    !fallback -> RecognitionRevealTitleMode.STATIC
                    t < GOLD_TRANSFER_END_MS -> RecognitionRevealTitleMode.HIDDEN
                    t < NAME_FORMATION_END_MS -> RecognitionRevealTitleMode.FORMING
                    t < NAME_REVEAL_END_MS -> RecognitionRevealTitleMode.REVEALING
                    else -> RecognitionRevealTitleMode.VISIBLE
                },
                titleFormationProgress = fallbackFormation,
                titleRevealProgress = fallbackProgress,
                titleSweepProgress = 0f,
                afterglowAlpha = 0f,
                visibleParticleCount = 0,
                drawOpticalLayer = canPhotoTransition && t < PHOTO_END_MS,
                fallback = fallback,
            )
        }

        val phase = when {
            t < PHOTO_END_MS -> RecognitionRevealPhase.PHOTO_CONTINUITY
            t < GOLD_TRANSFER_END_MS -> RecognitionRevealPhase.GOLD_TRANSFER
            t < NAME_FORMATION_END_MS -> RecognitionRevealPhase.NAME_FORMATION
            t < NAME_REVEAL_END_MS -> RecognitionRevealPhase.NAME_REVEAL
            else -> RecognitionRevealPhase.AFTERGLOW
        }
        val transfer = ((t - PHOTO_END_MS).toFloat() /
            (GOLD_TRANSFER_END_MS - PHOTO_END_MS)).coerceIn(0f, 1f)
        val formation = ((t - GOLD_TRANSFER_END_MS).toFloat() /
            (NAME_FORMATION_END_MS - GOLD_TRANSFER_END_MS)).coerceIn(0f, 1f)
        val titleReveal = ((t - NAME_FORMATION_END_MS).toFloat() /
            (NAME_REVEAL_END_MS - NAME_FORMATION_END_MS)).coerceIn(0f, 1f)
        val afterglow = if (t < NAME_REVEAL_END_MS) 1f else
            1f - ((t - NAME_REVEAL_END_MS).toFloat() /
                (TOTAL_DURATION_MS - NAME_REVEAL_END_MS)).coerceIn(0f, 1f)
        val particleLimit = if (policy.lowPerformance) LOW_PARTICLE_LIMIT else FULL_PARTICLE_LIMIT
        val particleCount = when {
            t < PHOTO_END_MS || t >= GOLD_TRANSFER_END_MS -> 0
            else -> (particleLimit * (1f - transfer)).toInt().coerceAtLeast(1)
        }.coerceIn(0, particleLimit)

        return RecognitionRevealFrame(
            elapsedMs = t,
            phase = phase,
            photoProgress = photoProgress,
            fishHaloAlpha = when {
                t < PHOTO_END_MS -> 1f
                t < GOLD_TRANSFER_END_MS -> 1f - easeInOut(transfer)
                else -> 0f
            },
            transferProgress = easeInOut(transfer),
            titleMode = when {
                t < GOLD_TRANSFER_END_MS -> RecognitionRevealTitleMode.HIDDEN
                t < NAME_FORMATION_END_MS -> RecognitionRevealTitleMode.FORMING
                t < NAME_REVEAL_END_MS -> RecognitionRevealTitleMode.REVEALING
                else -> RecognitionRevealTitleMode.VISIBLE
            },
            titleFormationProgress = formation,
            titleRevealProgress = easeOutCubic(titleReveal),
            titleSweepProgress = titleReveal.takeIf { t in NAME_FORMATION_END_MS until NAME_REVEAL_END_MS }
                ?.let(::easeInOut) ?: 0f,
            afterglowAlpha = afterglow,
            visibleParticleCount = particleCount,
            drawOpticalLayer = t < TOTAL_DURATION_MS,
            fallback = false,
        )
    }

    private fun staticPhase(eligibility: RecognitionRevealEligibility) = when (eligibility) {
        RecognitionRevealEligibility.PHOTO_ONLY,
        RecognitionRevealEligibility.TITLE_FADE_ONLY -> RecognitionRevealPhase.PHOTO_CONTINUITY
        RecognitionRevealEligibility.FULL_REVEAL,
        RecognitionRevealEligibility.STATIC_RESULT -> RecognitionRevealPhase.COMPLETE
    }

    private fun easeOutCubic(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return 1f - (1f - t) * (1f - t) * (1f - t)
    }

    private fun easeInOut(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun smoothStep(value: Float): Float = easeInOut(value)
}
