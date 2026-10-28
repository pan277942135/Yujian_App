package com.yujian.ai.ui.home

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalHomeTypographyContractTest {
    private val densities = listOf(1.0f, 2.0f, 2.75f, 3.0f)
    private val fontScales = listOf(1.0f, 1.15f, 1.3f)

    @Test
    fun referencePixelSizesAreAppliedOnceAcrossViewportAndDensityProfiles() {
        val widthsDp = listOf(360f, 393f, 480f)
        widthsDp.forEach { widthDp ->
            densities.forEach { density ->
                val contract = normalHomeTypographyContract(widthDp, density)
                val expectedScale = widthDp * density / 1080f

                assertRoleSize(contract.brand, 72f, expectedScale, 18f, density)
                assertRoleSize(contract.statValue, 36f, expectedScale, 16f, density)
                assertRoleSize(contract.statLabel, 28f, expectedScale, 16f, density)
                assertRoleSize(contract.sectionTitle, 48f, expectedScale, 18f, density)
                assertRoleSize(contract.action, 40f, expectedScale, 16f, density)
                assertRoleSize(contract.heroTitle, 56f, expectedScale, 18f, density)
                assertRoleSize(contract.measurement, 46f, expectedScale, 16f, density)
                assertRoleSize(contract.metadata, 36f, expectedScale, 16f, density)
                assertRoleSize(contract.captureCta, 40f, expectedScale, 16f, density)
            }
        }
    }

    @Test
    fun a1080PhysicalPixelViewportMatchesFrozenReferenceAtEveryDensity() {
        densities.forEach { density ->
            val widthDp = 1080f / density
            val contract = normalHomeTypographyContract(widthDp, density)
            assertEquals(72f, pxAt(contract.brand, density), 0.01f)
            assertEquals(36f, pxAt(contract.statValue, density), 0.01f)
            assertEquals(28f, pxAt(contract.statLabel, density), 0.01f)
            assertEquals(48f, pxAt(contract.sectionTitle, density), 0.01f)
            assertEquals(40f, pxAt(contract.action, density), 0.01f)
            assertEquals(56f, pxAt(contract.heroTitle, density), 0.01f)
            assertEquals(46f, pxAt(contract.measurement, density), 0.01f)
            assertEquals(36f, pxAt(contract.metadata, density), 0.01f)
            assertEquals(40f, pxAt(contract.captureCta, density), 0.01f)
        }
    }

    @Test
    fun systemFontScaleRemainsAnIndependentAccessibilityMultiplier() {
        val widthDp = 1080f / 2.75f
        val contract = normalHomeTypographyContract(widthDp, density = 2.75f)
        val standardDensity = Density(density = 2.75f, fontScale = 1f)
        val standardPx = with(standardDensity) {
            contract.metadata.fontSize.toPx()
        }
        val standardLineHeightPx = with(standardDensity) {
            contract.metadata.lineHeight.toPx()
        }
        assertEquals("Frozen metadata font size remains exact at standard scale", 36f, standardPx, 0.01f)
        assertEquals("Frozen metadata line height remains exact at standard scale", 42f, standardLineHeightPx, 0.01f)

        val standardSpacing = normalHomeSpacingContract(widthDp)
        val spacingValues = listOf(
            standardSpacing.recentHeaderHorizontalInset,
            standardSpacing.statVerticalPadding,
            standardSpacing.statLabelSpacing,
            standardSpacing.heroMetadataSpacing,
            standardSpacing.pagerSpacing,
            standardSpacing.recentActionSpacing,
            standardSpacing.recentChevronSize,
        )
        var previousFontPx = standardPx
        var previousLineHeightPx = standardLineHeightPx

        fontScales.filter { it > 1f }.forEach { fontScale ->
            val scaledDensity = Density(density = 2.75f, fontScale = fontScale)
            // Compose Density owns the platform font-scaling conversion. Do
            // not predict the result with a linear fontScale multiplier.
            val effectivePx = with(scaledDensity) {
                contract.metadata.fontSize.toPx()
            }
            val effectiveLineHeightPx = with(scaledDensity) {
                contract.metadata.lineHeight.toPx()
            }

            assertTrue("Compose-scaled font size must be finite and positive", effectivePx.isFinite() && effectivePx > 0f)
            assertTrue("Compose-scaled line height must be finite and positive", effectiveLineHeightPx.isFinite() && effectiveLineHeightPx > 0f)
            assertTrue("Font size must grow at fontScale=$fontScale", effectivePx > previousFontPx)
            assertTrue("Line height must grow at fontScale=$fontScale", effectiveLineHeightPx > previousLineHeightPx)
            previousFontPx = effectivePx
            previousLineHeightPx = effectiveLineHeightPx

            val scaledSpacing = normalHomeSpacingContract(widthDp)
            assertEquals("fontScale must not alter the dp spacing contract", standardSpacing, scaledSpacing)
            val scaledSpacingPx = with(scaledDensity) {
                listOf(
                    scaledSpacing.recentHeaderHorizontalInset,
                    scaledSpacing.statVerticalPadding,
                    scaledSpacing.statLabelSpacing,
                    scaledSpacing.heroMetadataSpacing,
                    scaledSpacing.pagerSpacing,
                    scaledSpacing.recentActionSpacing,
                    scaledSpacing.recentChevronSize,
                ).map { it.toPx() }
            }
            val standardSpacingPx = with(standardDensity) { spacingValues.map { it.toPx() } }
            standardSpacingPx.zip(scaledSpacingPx).forEachIndexed { index, (standard, scaled) ->
                assertEquals("fontScale must not alter dp spacing px at index $index", standard, scaled, 0.01f)
            }
        }
    }

    @Test
    fun minimumReadingSizesAndLineHeightsAreMaintainedOnNarrowViewports() {
        densities.forEach { density ->
            val contract = normalHomeTypographyContract(360f, density)
            listOf(
                contract.brand to 18f,
                contract.statValue to 16f,
                contract.statLabel to 16f,
                contract.sectionTitle to 18f,
                contract.action to 16f,
                contract.heroTitle to 18f,
                contract.measurement to 16f,
                contract.metadata to 16f,
                contract.captureCta to 16f,
            ).forEach { (role, minimumPx) ->
                assertTrue("$role should respect its physical-pixel readability floor", pxAt(role, density) >= minimumPx)
                assertTrue("$role line height should exceed font size", role.lineHeight.value > role.fontSize.value)
            }
        }
    }

    @Test
    fun measuredReferenceSpacingUsesViewportWidthAndIsNotDensityMultipliedTwice() {
        densities.forEach { density ->
            listOf(360f, 393f, 480f).forEach { usableWidthDp ->
                val spacing = normalHomeSpacingContract(usableWidthDp)
                val referenceScale = usableWidthDp * density / 1080f
                assertEquals(108f * referenceScale, spacing.recentHeaderHorizontalInset.value * density, 0.01f)
                assertEquals(17f * referenceScale, spacing.statVerticalPadding.value * density, 0.01f)
                assertEquals(8f * referenceScale, spacing.statLabelSpacing.value * density, 0.01f)
                assertEquals(20f * referenceScale, spacing.heroMetadataSpacing.value * density, 0.01f)
                assertEquals(16f * referenceScale, spacing.pagerSpacing.value * density, 0.01f)
                assertEquals(24f * referenceScale, spacing.recentChevronSize.value * density, 0.01f)
            }
        }

        val narrowDp = normalHomeReferenceDp(referencePx = 20f, usableWidthDp = 360f)
        assertEquals(6.6667f, narrowDp, 0.001f)
        assertEquals(20f, narrowDp * 3f, 0.001f)
    }

    private fun assertRoleSize(
        role: NormalHomeTypeSize,
        referenceFontPx: Float,
        widthScale: Float,
        minimumFontPx: Float,
        density: Float,
    ) {
        val expectedPx = maxOf(referenceFontPx * widthScale, minimumFontPx)
        assertEquals(expectedPx, pxAt(role, density), 0.01f)
        assertTrue(role.lineHeight.value >= role.fontSize.value * 1.08f - 0.01f)
    }

    private fun pxAt(role: NormalHomeTypeSize, density: Float): Float =
        with(Density(density = density, fontScale = 1f)) { role.fontSize.toPx() }
}
