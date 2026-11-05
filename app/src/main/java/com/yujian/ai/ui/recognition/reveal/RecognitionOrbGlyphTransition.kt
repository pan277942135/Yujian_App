package com.yujian.ai.ui.recognition.reveal

import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage

/** Six named beats are projections of the existing shared 13-stage SceneClock. */
internal enum class RecognitionOrbGlyphSubstep(val id: String) {
    ORB_CORE_HOLD("11.1"),
    ORB_HORIZONTAL_STRETCH("11.2"),
    LIQUID_BRUSH_TRANSITION("11.3"),
    STROKE_AGGREGATION("12.1"),
    GLYPH_EMERGENCE("12.2"),
    SPECIES_NAME_FINAL("12.3"),
}

internal data class RecognitionOrbGlyphTransition(
    val substep: RecognitionOrbGlyphSubstep?,
    val substepProgress: Float,
    val orbScaleX: Float,
    val orbScaleY: Float,
    val orbAlpha: Float,
    val liquidBrushProgress: Float,
    val liquidBrushAlpha: Float,
    val glyphProgress: Float,
)

/** Maps semantic stage progress only; it allocates no animator and never advances a clock. */
internal fun recognitionOrbGlyphTransition(
    stage: RecognitionSceneStage,
    stageProgress: Float,
): RecognitionOrbGlyphTransition? {
    val p = stageProgress.coerceIn(0f, 1f)
    fun smooth(value: Float): Float {
        val bounded = value.coerceIn(0f, 1f)
        return bounded * bounded * (3f - 2f * bounded)
    }
    return when (stage) {
        RecognitionSceneStage.ORB_RETURN -> RecognitionOrbGlyphTransition(
            substep = null,
            substepProgress = p,
            orbScaleX = 1f,
            orbScaleY = 1f,
            orbAlpha = 1f,
            liquidBrushProgress = 0f,
            liquidBrushAlpha = 0f,
            glyphProgress = 0f,
        )
        RecognitionSceneStage.ORB_TO_NAME -> {
            val holdBoundary = 100f / 850f
            val stretchBoundary = 450f / 850f
            when {
                p < holdBoundary -> RecognitionOrbGlyphTransition(
                    substep = RecognitionOrbGlyphSubstep.ORB_CORE_HOLD,
                    substepProgress = (p / holdBoundary).coerceIn(0f, 1f),
                    orbScaleX = 1f,
                    orbScaleY = 1f,
                    orbAlpha = 1f,
                    liquidBrushProgress = 0f,
                    liquidBrushAlpha = 0f,
                    glyphProgress = 0f,
                )
                p < stretchBoundary -> {
                    val stretch = smooth((p - holdBoundary) / (stretchBoundary - holdBoundary))
                    RecognitionOrbGlyphTransition(
                        substep = RecognitionOrbGlyphSubstep.ORB_HORIZONTAL_STRETCH,
                        substepProgress = ((p - holdBoundary) / (stretchBoundary - holdBoundary)).coerceIn(0f, 1f),
                        orbScaleX = 1f + .62f * stretch,
                        orbScaleY = 1f - .32f * stretch,
                        orbAlpha = 1f,
                        liquidBrushProgress = 0f,
                        liquidBrushAlpha = 0f,
                        glyphProgress = 0f,
                    )
                }
                else -> {
                    val brush = smooth((p - stretchBoundary) / (1f - stretchBoundary))
                    RecognitionOrbGlyphTransition(
                        substep = RecognitionOrbGlyphSubstep.LIQUID_BRUSH_TRANSITION,
                        substepProgress = ((p - stretchBoundary) / (1f - stretchBoundary)).coerceIn(0f, 1f),
                        orbScaleX = 1.62f,
                        orbScaleY = .68f,
                        orbAlpha = 1f - .74f * brush,
                        liquidBrushProgress = brush,
                        liquidBrushAlpha = .45f + .55f * brush,
                        glyphProgress = .22f * brush,
                    )
                }
            }
        }
        RecognitionSceneStage.SPECIES_NAME_REVEAL -> when {
            p < 150f / 800f -> {
                val local = (p / (150f / 800f)).coerceIn(0f, 1f)
                RecognitionOrbGlyphTransition(
                    substep = RecognitionOrbGlyphSubstep.STROKE_AGGREGATION,
                    substepProgress = local,
                    orbScaleX = 1.62f,
                    orbScaleY = .68f,
                    orbAlpha = .26f - .08f * smooth(local),
                    liquidBrushProgress = 1f,
                    liquidBrushAlpha = 1f - .70f * smooth(local),
                    glyphProgress = .22f + .18f * local,
                )
            }
            p < 350f / 800f -> {
                val local = ((p - 150f / 800f) / (200f / 800f)).coerceIn(0f, 1f)
                RecognitionOrbGlyphTransition(
                    substep = RecognitionOrbGlyphSubstep.GLYPH_EMERGENCE,
                    substepProgress = local,
                    orbScaleX = 1.62f,
                    orbScaleY = .68f,
                    orbAlpha = .18f * (1f - .72f * smooth(local)),
                    liquidBrushProgress = 1f,
                    liquidBrushAlpha = .30f * (1f - smooth(local)),
                    glyphProgress = .40f + .60f * smooth(local),
                )
            }
            else -> {
                val local = ((p - 350f / 800f) / (450f / 800f)).coerceIn(0f, 1f)
                RecognitionOrbGlyphTransition(
                    substep = RecognitionOrbGlyphSubstep.SPECIES_NAME_FINAL,
                    substepProgress = local,
                    orbScaleX = 1.62f,
                    orbScaleY = .68f,
                    orbAlpha = .0504f * (1f - smooth(local)),
                    liquidBrushProgress = 1f,
                    liquidBrushAlpha = 0f,
                    glyphProgress = 1f,
                )
            }
        }
        else -> null
    }
}

/** Cache identity for the real classifier glyph; attempt and photo changes invalidate it. */
internal data class RecognitionSpeciesGlyphIdentity(
    val sessionId: String,
    val attemptId: String,
    val imageId: String,
    val speciesKey: String,
    val displayName: String,
)

internal fun recognitionSpeciesGlyphIdentity(
    scene: RecognitionSceneIdentity,
    speciesKey: String,
    displayName: String,
): RecognitionSpeciesGlyphIdentity? {
    if (scene.sessionId.isBlank() || scene.attemptId.isBlank() || scene.imageId.isBlank() ||
        scene.photoSha256.isNullOrBlank() || scene.photoContentVersion < 0L ||
        speciesKey.isBlank() || displayName.isBlank()
    ) return null
    return RecognitionSpeciesGlyphIdentity(
        sessionId = scene.sessionId,
        attemptId = scene.attemptId,
        imageId = scene.imageId,
        speciesKey = speciesKey.trim(),
        displayName = displayName.trim(),
    )
}
