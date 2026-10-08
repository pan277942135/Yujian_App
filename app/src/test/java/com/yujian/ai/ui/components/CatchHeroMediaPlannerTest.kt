package com.yujian.ai.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchHeroMediaPlannerTest {
    private val safeFish = NormalizedRect(0.42, 0.30, 0.58, 0.70)

    @Test
    fun sourceOrientationAloneNeverSelectsCropWithoutTrustedFishBox() {
        val sources = listOf(1600 to 1200, 1920 to 1080, 900 to 1200, 900 to 1600)
        val viewports = listOf(
            740 to 880 to CatchHeroVariant.HOME,
            841 to 540 to CatchHeroVariant.DETAIL_A,
        )

        sources.forEach { (sourceWidth, sourceHeight) ->
            viewports.forEach { (size, variant) ->
                val plan = plan(sourceWidth, sourceHeight, size.first, size.second, variant)
                assertEquals(CatchHeroMediaMode.EVIDENCE_FIT, plan.mode)
                assertNull(plan.sourceCropRectPx)
                assertTrue(plan.foregroundFit)
            }
        }
    }

    @Test
    fun homeAndDetailComputeDifferentSafeCropRectanglesForTheSameSource() {
        val home = plan(1600, 1200, 740, 880, CatchHeroVariant.HOME, trustedFish = safeFish)
        val detail = plan(1600, 1200, 841, 540, CatchHeroVariant.DETAIL_A, trustedFish = safeFish)

        assertEquals(CatchHeroMediaMode.SUBJECT_CROP, home.mode)
        assertEquals(CatchHeroMediaMode.SUBJECT_CROP, detail.mode)
        assertNotNull(home.sourceCropRectPx)
        assertNotNull(detail.sourceCropRectPx)
        assertTrue(home.sourceCropRectPx!!.width < detail.sourceCropRectPx!!.width)
        assertPaddedFishAndViewportInsetRemainInside(home, 1600, 1200, 740, 880, density = 1f)
        assertPaddedFishAndViewportInsetRemainInside(detail, 1600, 1200, 841, 540, density = 1f)
    }

    @Test
    fun edgeContactAlwaysSelectsFitAndNeverProducesSecondaryCrop() {
        val edge = plan(
            sourceWidth = 1600,
            sourceHeight = 1200,
            viewportWidth = 841,
            viewportHeight = 540,
            variant = CatchHeroVariant.DETAIL_A,
            trustedFish = NormalizedRect(0.01, 0.35, 0.40, 0.72),
        )

        assertEquals(CatchHeroMediaMode.EDGE_PROTECTED_FIT, edge.mode)
        assertNull(edge.sourceCropRectPx)
        assertEquals(CatchHeroBackgroundPolicy.SAME_SOURCE_AMBIENT, edge.backgroundPolicy)
    }

    @Test
    fun missingUntrustedAndInvalidBoxesAlwaysFallBackToEvidenceFit() {
        val missing = plan(1600, 1200, 740, 880, CatchHeroVariant.HOME)
        val untrusted = plan(
            1600, 1200, 740, 880, CatchHeroVariant.HOME,
            trustedFish = safeFish,
            trusted = false,
        )
        val invalid = plan(
            1600, 1200, 740, 880, CatchHeroVariant.HOME,
            trustedFish = NormalizedRect(0.8, 0.2, 0.3, 0.9),
        )

        listOf(missing, untrusted, invalid).forEach { result ->
            assertEquals(CatchHeroMediaMode.EVIDENCE_FIT, result.mode)
            assertNull(result.sourceCropRectPx)
            assertTrue(result.foregroundFit)
        }
    }

    @Test
    fun trustedBoxThatCannotKeepTheTwelveDpInsetFallsBackToAmbientFit() {
        val tight = plan(
            sourceWidth = 1600,
            sourceHeight = 1200,
            viewportWidth = 740,
            viewportHeight = 880,
            variant = CatchHeroVariant.HOME,
            trustedFish = NormalizedRect(0.20, 0.20, 0.82, 0.80),
        )

        assertEquals(CatchHeroMediaMode.SAFE_FIT_AMBIENT, tight.mode)
        assertNull(tight.sourceCropRectPx)
    }

    @Test
    fun extremeAspectAndUncertainBarsUseEvidenceFit() {
        val extreme = plan(300, 1000, 841, 540, CatchHeroVariant.DETAIL_A, trustedFish = safeFish)
        val uncertainBars = CatchHeroMediaPlanner.plan(
            CatchHeroMediaRequest(
                sourceWidthPx = 1600,
                sourceHeightPx = 1200,
                viewportWidthPx = 841,
                viewportHeightPx = 540,
                variant = CatchHeroVariant.DETAIL_A,
                trustedFishRect = safeFish,
                trustedFishBox = true,
                letterboxConfidence = LetterboxConfidence.UNCERTAIN,
            ),
        )

        assertEquals(CatchHeroMediaMode.EVIDENCE_FIT, extreme.mode)
        assertEquals(CatchHeroMediaMode.EVIDENCE_FIT, uncertainBars.mode)
    }

    @Test
    fun exifOrientationMapsNormalizedRectAndSwapsDimensions() {
        val sourceRect = NormalizedRect(0.1, 0.2, 0.4, 0.6)
        val clockwise = ExifOrientation.ROTATE_90_CW
        val mapped = clockwise.mapRect(sourceRect)

        assertEquals(200 to 100, clockwise.orientedSize(100, 200))
        assertEquals(0.4, mapped.left, 1e-9)
        assertEquals(0.1, mapped.top, 1e-9)
        assertEquals(0.8, mapped.right, 1e-9)
        assertEquals(0.4, mapped.bottom, 1e-9)
    }

    @Test
    fun transposeExifOrientationMapsAllRectangleCorners() {
        val mapped = ExifOrientation.TRANSPOSE.mapRect(NormalizedRect(0.1, 0.2, 0.4, 0.6))
        assertEquals(NormalizedRect(0.2, 0.1, 0.6, 0.4), mapped)
        assertEquals(200 to 100, ExifOrientation.TRANSPOSE.orientedSize(100, 200))
    }

    @Test
    fun allExifOrientationsMapSourceCornersIntoTheirDisplayCoordinates() {
        val source = NormalizedRect(0.1, 0.2, 0.4, 0.6)
        val expected = mapOf(
            ExifOrientation.NORMAL to NormalizedRect(0.1, 0.2, 0.4, 0.6),
            ExifOrientation.FLIP_HORIZONTAL to NormalizedRect(0.6, 0.2, 0.9, 0.6),
            ExifOrientation.ROTATE_180 to NormalizedRect(0.6, 0.4, 0.9, 0.8),
            ExifOrientation.FLIP_VERTICAL to NormalizedRect(0.1, 0.4, 0.4, 0.8),
            ExifOrientation.TRANSPOSE to NormalizedRect(0.2, 0.1, 0.6, 0.4),
            ExifOrientation.ROTATE_90_CW to NormalizedRect(0.4, 0.1, 0.8, 0.4),
            ExifOrientation.TRANSVERSE to NormalizedRect(0.4, 0.6, 0.8, 0.9),
            ExifOrientation.ROTATE_270_CW to NormalizedRect(0.2, 0.6, 0.6, 0.9),
        )
        expected.forEach { (orientation, mapped) -> assertEquals(orientation.name, mapped, orientation.mapRect(source)) }
        assertEquals(ExifOrientation.ROTATE_90_CW, ExifOrientation.fromExif(6))
        assertEquals(ExifOrientation.UNKNOWN, ExifOrientation.fromExif(99))
    }

    private fun plan(
        sourceWidth: Int,
        sourceHeight: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        variant: CatchHeroVariant,
        trustedFish: NormalizedRect? = null,
        trusted: Boolean = trustedFish != null,
    ) = CatchHeroMediaPlanner.plan(
        CatchHeroMediaRequest(
            sourceWidthPx = sourceWidth,
            sourceHeightPx = sourceHeight,
            viewportWidthPx = viewportWidth,
            viewportHeightPx = viewportHeight,
            variant = variant,
            trustedFishRect = trustedFish,
            trustedFishBox = trusted,
        ),
    )

    private fun assertPaddedFishAndViewportInsetRemainInside(
        plan: CatchHeroMediaPlan,
        sourceWidth: Int,
        sourceHeight: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        density: Float,
    ) {
        val crop = requireNotNull(plan.sourceCropRectPx)
        val safe = requireNotNull(plan.safeSubjectRect)
        val scale = minOf(viewportWidth.toDouble() / crop.width, viewportHeight.toDouble() / crop.height)
        val insetPx = 12.0 * density
        assertTrue(safe.left * sourceWidth * scale >= crop.left * scale + insetPx)
        assertTrue(safe.top * sourceHeight * scale >= crop.top * scale + insetPx)
        assertTrue(safe.right * sourceWidth * scale <= crop.right * scale - insetPx)
        assertTrue(safe.bottom * sourceHeight * scale <= crop.bottom * scale - insetPx)
    }
}
