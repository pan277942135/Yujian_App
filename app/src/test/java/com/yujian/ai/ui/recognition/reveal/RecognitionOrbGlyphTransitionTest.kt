package com.yujian.ai.ui.recognition.reveal

import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionOrbGlyphTransitionTest {
    @Test
    fun sharedSceneStagesProjectTheSixRequiredGlyphSubsteps() {
        assertNull(recognitionOrbGlyphTransition(RecognitionSceneStage.ORB_RETURN, .5f)?.substep)
        assertEquals(
            RecognitionOrbGlyphSubstep.ORB_CORE_HOLD,
            requireNotNull(recognitionOrbGlyphTransition(RecognitionSceneStage.ORB_TO_NAME, .05f)).substep,
        )
        assertEquals(
            RecognitionOrbGlyphSubstep.ORB_HORIZONTAL_STRETCH,
            requireNotNull(recognitionOrbGlyphTransition(RecognitionSceneStage.ORB_TO_NAME, .2f)).substep,
        )
        assertEquals(
            RecognitionOrbGlyphSubstep.LIQUID_BRUSH_TRANSITION,
            requireNotNull(recognitionOrbGlyphTransition(RecognitionSceneStage.ORB_TO_NAME, .7f)).substep,
        )
        assertEquals(
            RecognitionOrbGlyphSubstep.STROKE_AGGREGATION,
            requireNotNull(recognitionOrbGlyphTransition(RecognitionSceneStage.SPECIES_NAME_REVEAL, .15f)).substep,
        )
        assertEquals(
            RecognitionOrbGlyphSubstep.GLYPH_EMERGENCE,
            requireNotNull(recognitionOrbGlyphTransition(RecognitionSceneStage.SPECIES_NAME_REVEAL, .30f)).substep,
        )
        assertEquals(
            RecognitionOrbGlyphSubstep.SPECIES_NAME_FINAL,
            requireNotNull(recognitionOrbGlyphTransition(RecognitionSceneStage.SPECIES_NAME_REVEAL, .9f)).substep,
        )
    }

    @Test
    fun orbMaterialAndGlyphProgressRemainContinuousAtSubstepBoundaries() {
        val stretchBoundary = 450f / 850f
        val stretchEnd = requireNotNull(recognitionOrbGlyphTransition(RecognitionSceneStage.ORB_TO_NAME, stretchBoundary))
        val brushStart = requireNotNull(recognitionOrbGlyphTransition(RecognitionSceneStage.ORB_TO_NAME, stretchBoundary + .0001f))
        assertEquals(stretchEnd.orbScaleX, brushStart.orbScaleX, .002f)
        assertEquals(stretchEnd.orbScaleY, brushStart.orbScaleY, .002f)
        assertEquals(stretchEnd.orbAlpha, brushStart.orbAlpha, .002f)

        val aggregationBoundary = 150f / 800f
        val aggregationEnd = requireNotNull(recognitionOrbGlyphTransition(RecognitionSceneStage.SPECIES_NAME_REVEAL, aggregationBoundary))
        val emergenceStart = requireNotNull(recognitionOrbGlyphTransition(RecognitionSceneStage.SPECIES_NAME_REVEAL, aggregationBoundary + .0001f))
        assertEquals(.40f, aggregationEnd.glyphProgress, .002f)
        assertEquals(aggregationEnd.glyphProgress, emergenceStart.glyphProgress, .002f)
        assertEquals(aggregationEnd.orbAlpha, emergenceStart.orbAlpha, .002f)

        val finalBoundary = 350f / 800f
        val emergenceEnd = requireNotNull(recognitionOrbGlyphTransition(RecognitionSceneStage.SPECIES_NAME_REVEAL, finalBoundary))
        val nameFinalStart = requireNotNull(recognitionOrbGlyphTransition(RecognitionSceneStage.SPECIES_NAME_REVEAL, finalBoundary + .0001f))
        assertEquals(1f, emergenceEnd.glyphProgress, 0f)
        assertEquals(emergenceEnd.orbAlpha, nameFinalStart.orbAlpha, .002f)
        assertTrue(requireNotNull(recognitionOrbGlyphTransition(RecognitionSceneStage.SPECIES_NAME_REVEAL, 1f)).orbAlpha < .001f)
    }

    @Test
    fun glyphInkProgressConnectsLiquidBrushToStrokeAggregation() {
        val brushEnd = requireNotNull(recognitionOrbGlyphTransition(RecognitionSceneStage.ORB_TO_NAME, 1f))
        val aggregationStart = requireNotNull(recognitionOrbGlyphTransition(RecognitionSceneStage.SPECIES_NAME_REVEAL, 0f))
        assertEquals(.22f, brushEnd.glyphProgress, .001f)
        assertEquals(brushEnd.glyphProgress, aggregationStart.glyphProgress, .001f)
        assertEquals(brushEnd.orbAlpha, aggregationStart.orbAlpha, .001f)
    }

    @Test
    fun stageElevenAndTwelveBoundariesMatchFrozenSubphaseDurations() {
        val holdEnd = requireNotNull(recognitionOrbGlyphTransition(RecognitionSceneStage.ORB_TO_NAME, 100f / 850f))
        assertEquals(RecognitionOrbGlyphSubstep.ORB_HORIZONTAL_STRETCH, holdEnd.substep)
        assertEquals(RecognitionOrbGlyphSubstep.LIQUID_BRUSH_TRANSITION,
            requireNotNull(recognitionOrbGlyphTransition(RecognitionSceneStage.ORB_TO_NAME, 450f / 850f)).substep)

        assertEquals(RecognitionOrbGlyphSubstep.GLYPH_EMERGENCE,
            requireNotNull(recognitionOrbGlyphTransition(RecognitionSceneStage.SPECIES_NAME_REVEAL, 150f / 800f)).substep)
        assertEquals(RecognitionOrbGlyphSubstep.SPECIES_NAME_FINAL,
            requireNotNull(recognitionOrbGlyphTransition(RecognitionSceneStage.SPECIES_NAME_REVEAL, 350f / 800f)).substep)
    }

    @Test
    fun fullAndOrbOnlyUseTheSameDynamicGlyphStageProjection() {
        val full = recognitionOrbGlyphTransition(RecognitionSceneStage.SPECIES_NAME_REVEAL, .30f)
        val orbOnly = recognitionOrbGlyphTransition(RecognitionSceneStage.SPECIES_NAME_REVEAL, .30f)
        assertEquals(full, orbOnly)
        assertEquals(RecognitionOrbGlyphSubstep.GLYPH_EMERGENCE, requireNotNull(full).substep)
    }

    @Test
    fun glyphCacheIdentityIncludesCurrentAttemptPhotoAndRealSpeciesName() {
        val scene = RecognitionSceneIdentity("session-a", "attempt-a", "photo-a", "a".repeat(64), 1L)
        val glyph = requireNotNull(recognitionSpeciesGlyphIdentity(scene, "snakehead", "黑鱼"))
        assertEquals(glyph, recognitionSpeciesGlyphIdentity(scene, "snakehead", "黑鱼"))
        assertNotEquals(glyph, recognitionSpeciesGlyphIdentity(scene.copy(attemptId = "attempt-b"), "snakehead", "黑鱼"))
        assertNotEquals(glyph, recognitionSpeciesGlyphIdentity(scene.copy(imageId = "photo-b"), "snakehead", "黑鱼"))
        assertNotEquals(glyph, recognitionSpeciesGlyphIdentity(scene, "grass_carp", "草鱼"))
        assertNull(recognitionSpeciesGlyphIdentity(scene, "", "黑鱼"))
        assertNull(recognitionSpeciesGlyphIdentity(scene, "snakehead", " "))
        assertNull(recognitionSpeciesGlyphIdentity(scene.copy(photoSha256 = null), "snakehead", "黑鱼"))
    }
}
