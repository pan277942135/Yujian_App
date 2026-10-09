package com.yujian.ai.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.unit.Density

class NormalHomeTypographyContractTest {
    @Test
    fun referenceSizesMapToSamePhysicalSizeAcrossDensityProfiles() {
        val referenceWidthPx = 1080f
        val referenceWidths = listOf(160, 320, 440)

        referenceWidths.forEach { densityDpi ->
            val density = densityDpi / 160f
            val contentWidthDp = referenceWidthPx / density
            val scale = contentWidthDp / 1080f
            val contract = normalHomeTypographyContract(scale, density)

            assertEquals(32f, contract.brand.fontSize.value * density, 0.01f)
            assertEquals(22f, contract.statValue.fontSize.value * density, 0.01f)
            assertEquals(12f, contract.statLabel.fontSize.value * density, 0.01f)
            assertEquals(24f, contract.sectionTitle.fontSize.value * density, 0.01f)
            assertEquals(18f, contract.action.fontSize.value * density, 0.01f)
            assertEquals(32f, contract.heroTitle.fontSize.value * density, 0.01f)
            assertEquals(22f, contract.measurement.fontSize.value * density, 0.01f)
            assertEquals(16f, contract.metadata.fontSize.value * density, 0.01f)
            assertEquals(20f, contract.captureCta.fontSize.value * density, 0.01f)
        }
    }

    @Test
    fun androidFontScaleRemainsAnIndependentAccessibilityMultiplier() {
        val density = 440f / 160f
        val scale = 1080f / density / 1080f
        val contract = normalHomeTypographyContract(scale, density)
        val standardPx = with(Density(density = density, fontScale = 1f)) { contract.metadata.fontSize.toPx() }

        listOf(1.0f, 1.15f, 1.3f).forEach { fontScale ->
            val effectivePx = with(Density(density = density, fontScale = fontScale)) {
                contract.metadata.fontSize.toPx()
            }
            assertEquals(standardPx * fontScale, effectivePx, 0.01f)
        }
        assertEquals(16f, standardPx, 0.01f)
    }

    @Test
    fun narrowViewportUsesPhysicalReadabilityFloorsWithoutCancellingFontScale() {
        val density = 3f
        val contentWidthPx = 360f
        val contentWidthDp = contentWidthPx / density
        val contract = normalHomeTypographyContract(contentWidthDp / 1080f, density)

        assertEquals(18f, contract.brand.fontSize.value * density, 0.01f)
        assertEquals(16f, contract.statValue.fontSize.value * density, 0.01f)
        assertEquals(12f, contract.statLabel.fontSize.value * density, 0.01f)
        assertTrue(contract.brand.lineHeight.value > contract.brand.fontSize.value)
        assertTrue(contract.metadata.lineHeight.value > contract.metadata.fontSize.value)
    }
}
