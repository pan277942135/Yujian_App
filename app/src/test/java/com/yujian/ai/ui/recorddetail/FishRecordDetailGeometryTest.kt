package com.yujian.ai.ui.recorddetail

import com.yujian.ai.ui.adaptive.resolveAdaptiveLayoutProfile
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FishRecordDetailGeometryTest {
    @Test
    fun heroKeepsFrozenReferenceAspectAcrossSupportedWidths() {
        val profiles = listOf(
            320f to 640f,
            360f to 780f,
            393f to 852f,
            411f to 891f,
        )

        profiles.forEach { (width, height) ->
            val profile = resolveAdaptiveLayoutProfile(width, height, fontScale = 1f)
            val geometry = FishRecordDetailGeometryResolver.resolve(profile)
            val renderedAspect = geometry.heroWidthDp.toFloat() / geometry.heroHeightDp
            val relativeAspectError = kotlin.math.abs(renderedAspect - (841f / 540f)) / (841f / 540f)

            assertTrue("Hero aspect drift at ${width}x$height: $relativeAspectError", relativeAspectError <= 0.01f)
            assertEquals(width.toInt(), geometry.heroWidthDp + 2 * geometry.horizontalMarginDp)
            assertTrue(geometry.heroHeightDp > 0)
        }
    }

    @Test
    fun frozenReferenceGeometryIsExplicitAndResponsiveMappingKeepsItsProportions() {
        assertEquals(941, FishRecordDetailFrozenGeometry.canvasWidthPx)
        assertEquals(1672, FishRecordDetailFrozenGeometry.canvasHeightPx)
        assertEquals(50, FishRecordDetailFrozenGeometry.heroLeftPx)
        assertEquals(159, FishRecordDetailFrozenGeometry.heroTopPx)
        assertEquals(891, FishRecordDetailFrozenGeometry.heroRightPx)
        assertEquals(699, FishRecordDetailFrozenGeometry.heroBottomPx)
        assertEquals(841, FishRecordDetailFrozenGeometry.heroWidthPx)
        assertEquals(540, FishRecordDetailFrozenGeometry.heroHeightPx)

        val expected = mapOf(320 to (288 to 185), 360 to (320 to 205), 393 to (353 to 227), 411 to (363 to 233))
        expected.forEach { (width, size) ->
            val geometry = FishRecordDetailGeometryResolver.resolve(width)
            assertEquals(size.first, geometry.heroWidthDp)
            assertEquals(size.second, geometry.heroHeightDp)
        }
    }

    @Test
    fun safeInsetsAndLargeFontScaleDoNotChangeHeroAspectOrEscapeSafeWidth() {
        val profile = resolveAdaptiveLayoutProfile(
            windowWidthDp = 393f,
            windowHeightDp = 852f,
            fontScale = 1.3f,
            safeInsets = com.yujian.ai.ui.adaptive.SafeDrawingInsetsDp(
                top = 32.dp,
                bottom = 24.dp,
                start = 8.dp,
                end = 8.dp,
            ),
        )
        val geometry = FishRecordDetailGeometryResolver.resolve(profile)
        val expectedWidth = profile.safeWidthDp.toInt() - 2 * geometry.horizontalMarginDp
        val renderedAspect = geometry.heroWidthDp.toFloat() / geometry.heroHeightDp

        assertEquals(expectedWidth, geometry.heroWidthDp)
        assertEquals(377f, profile.safeWidthDp, 0.01f)
        assertTrue(profile.accessibilityFontScale)
        assertTrue(kotlin.math.abs(renderedAspect - (841f / 540f)) / (841f / 540f) <= 0.01f)
    }
}
