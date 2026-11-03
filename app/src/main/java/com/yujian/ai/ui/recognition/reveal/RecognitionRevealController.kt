package com.yujian.ai.ui.recognition.reveal

enum class RecognitionRevealPhase {
    PHOTO_CONTINUITY,
    ORB_RETURN,
    ORB_TO_NAME,
    SPECIES_NAME_REVEAL,
    RESIDUE_SETTLE,
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
    /** RR09 V4 Orb receive/stabilize progress after the return path launches during photo continuity. */
    val orbReturnProgress: Float get() = transferProgress
    val orbToNameProgress: Float get() = titleFormationProgress
}

object RecognitionRevealClock {
    const val TOTAL_DURATION_MS = 850L
    const val PHOTO_END_MS = 200L
    const val ORB_RETURN_END_MS = 380L
    const val ORB_TO_NAME_END_MS = 550L
    const val SPECIES_NAME_REVEAL_END_MS = 680L
    const val GOLD_TRANSFER_END_MS = ORB_RETURN_END_MS
    const val NAME_FORMATION_END_MS = ORB_TO_NAME_END_MS
    const val NAME_REVEAL_END_MS = SPECIES_NAME_REVEAL_END_MS
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
            val fallbackAfterglow = if (t < NAME_REVEAL_END_MS) 1f else
                1f - ((t - NAME_REVEAL_END_MS).toFloat() /
                    (TOTAL_DURATION_MS - NAME_REVEAL_END_MS)).coerceIn(0f, 1f)
            val particleLimit = if (policy.lowPerformance) LOW_PARTICLE_LIMIT else FULL_PARTICLE_LIMIT
            val fallbackParticles = when {
                !fallback || t < PHOTO_END_MS || t >= TOTAL_DURATION_MS -> 0
                t < NAME_REVEAL_END_MS -> (particleLimit * (1f - fallbackFormation * .65f)).toInt().coerceAtLeast(2)
                else -> (particleLimit * fallbackAfterglow).toInt()
            }.coerceIn(0, particleLimit)
            return RecognitionRevealFrame(
                elapsedMs = t,
                phase = when {
                    t < PHOTO_END_MS -> RecognitionRevealPhase.PHOTO_CONTINUITY
                    t < GOLD_TRANSFER_END_MS -> RecognitionRevealPhase.ORB_RETURN
                    t < NAME_FORMATION_END_MS -> RecognitionRevealPhase.ORB_TO_NAME
                    t < NAME_REVEAL_END_MS -> RecognitionRevealPhase.SPECIES_NAME_REVEAL
                    t < TOTAL_DURATION_MS -> RecognitionRevealPhase.RESIDUE_SETTLE
                    else -> RecognitionRevealPhase.COMPLETE
                },
                photoProgress = photoProgress,
                fishHaloAlpha = 0f,
                transferProgress = if (fallback && t >= PHOTO_END_MS) {
                    easeInOut(((t - PHOTO_END_MS).toFloat() / (GOLD_TRANSFER_END_MS - PHOTO_END_MS)).coerceIn(0f, 1f))
                } else 0f,
                titleMode = when {
                    !fallback -> RecognitionRevealTitleMode.STATIC
                    t < GOLD_TRANSFER_END_MS -> RecognitionRevealTitleMode.HIDDEN
                    t < NAME_FORMATION_END_MS -> RecognitionRevealTitleMode.FORMING
                    t < NAME_REVEAL_END_MS -> RecognitionRevealTitleMode.REVEALING
                    else -> RecognitionRevealTitleMode.VISIBLE
                },
                titleFormationProgress = fallbackFormation,
                titleRevealProgress = fallbackProgress,
                titleSweepProgress = fallbackProgress,
                afterglowAlpha = fallbackAfterglow,
                visibleParticleCount = fallbackParticles,
                // TITLE_FADE_ONLY is the production Orb-only branch: it has no fish contour,
                // but the measured perimeter-to-Orb return, Orb-to-glyph translation and
                // residue remain visible for the complete RR09 window. PHOTO_ONLY never
                // reaches this branch because it is excluded from canPhotoTransition.
                drawOpticalLayer = fallback && canPhotoTransition && t < TOTAL_DURATION_MS,
                fallback = fallback,
            )
        }

        val phase = when {
            t < PHOTO_END_MS -> RecognitionRevealPhase.PHOTO_CONTINUITY
            t < GOLD_TRANSFER_END_MS -> RecognitionRevealPhase.ORB_RETURN
            t < NAME_FORMATION_END_MS -> RecognitionRevealPhase.ORB_TO_NAME
            t < NAME_REVEAL_END_MS -> RecognitionRevealPhase.SPECIES_NAME_REVEAL
            else -> RecognitionRevealPhase.RESIDUE_SETTLE
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
