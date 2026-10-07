package com.yujian.ai.ui.fishguide

import com.yujian.ai.ui.adaptive.resolveAdaptiveLayoutProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FishGuideResponsiveGeometryTest {
    @Test
    fun homeCardAndAdjacentPeekFollowFrozenCompositionAcrossSupportedWidths() {
        listOf(320f, 360f, 393f, 411f).forEach { width ->
            val geometry = FishGuideResponsiveGeometryResolver.resolveHome(width)
            assertEquals(673f / 941f, geometry.cardWidthDp / width, 0.0001f)
            assertEquals(923f / 673f, geometry.cardHeightDp / geometry.cardWidthDp, 0.0001f)
            assertEquals(33f / 941f, geometry.pageSpacingDp / width, 0.0001f)
            assertEquals(0.107f, geometry.adjacentVisibleDp / width, 0.001f)
            assertTrue(geometry.cardWidthDp + 2f * geometry.pageSpacingDp < width)
        }
    }

    @Test
    fun speciesDetailUsesFrozen82To86PercentActiveCardTarget() {
        listOf(320f, 360f, 393f, 411f).forEach { width ->
            val geometry = FishGuideResponsiveGeometryResolver.resolveSpeciesDetail(width)
            assertTrue(geometry.cardWidthDp / width in 0.82f..0.86f)
            assertTrue(geometry.adjacentVisibleDp / width in 0.05f..0.08f)
        }
    }

    @Test
    fun supportedViewportAndAccessibilityFontProfilesKeepTheResponsiveEnvelope() {
        val viewports = listOf(
            320f to 640f,
            360f to 780f,
            393f to 852f,
            411f to 891f,
        )

        viewports.forEach { (width, height) ->
            val standard = resolveAdaptiveLayoutProfile(width, height, fontScale = 1f)
            val accessible = resolveAdaptiveLayoutProfile(width, height, fontScale = 1.3f)
            val regularGeometry = FishGuideResponsiveGeometryResolver.resolveHome(standard.safeWidthDp)
            val accessibleGeometry = FishGuideResponsiveGeometryResolver.resolveHome(accessible.safeWidthDp)

            assertEquals(regularGeometry.cardWidthDp, accessibleGeometry.cardWidthDp, 0.001f)
            assertEquals(regularGeometry.cardHeightDp, accessibleGeometry.cardHeightDp, 0.001f)
            assertTrue(accessible.accessibilityFontScale)
            assertTrue("Fish Guide remains vertically scrollable at ${width}x$height", accessible.requiresScrollableContent)
        }
    }
}
