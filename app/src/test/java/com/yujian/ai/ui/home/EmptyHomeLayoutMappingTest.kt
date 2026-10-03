package com.yujian.ai.ui.home

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import com.yujian.ai.ui.adaptive.AdaptiveHeightClass
import com.yujian.ai.ui.adaptive.AdaptiveWidthClass
import com.yujian.ai.ui.adaptive.SafeDrawingInsetsDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmptyHomeLayoutMappingTest {
    private val insets = SafeDrawingInsetsDp(
        top = 24.dp,
        bottom = 34.dp,
        start = 0.dp,
        end = 0.dp,
    )

    @Test
    fun frozenGeometryMapsAcrossRequiredPortraitProfilesAndFontScales() {
        val profiles = listOf(
            Triple(320f, 640f, AdaptiveWidthClass.COMPACT),
            Triple(360f, 780f, AdaptiveWidthClass.STANDARD),
            Triple(393f, 852f, AdaptiveWidthClass.MEDIUM),
            Triple(411f, 891f, AdaptiveWidthClass.WIDE),
        )

        profiles.forEach { (width, height, widthClass) ->
            listOf(1f, 1.3f).forEach { fontScale ->
                val mapping = calculateEmptyHomeLayoutMapping(
                    windowWidthDp = width,
                    windowHeightDp = height,
                    density = 1f,
                    fontScale = fontScale,
                    safeInsets = insets,
                )
                val safeRight = mapping.safeViewport.right
                val safeBottom = mapping.safeViewport.bottom
                val referenceScale = mapping.profile.safeWidthDp / EmptyHomeFrozenLayoutGeometry.REFERENCE_WIDTH_PX

                assertEquals(widthClass, mapping.profile.widthClass)
                assertEquals(2f, mapping.heroBounds.width / mapping.heroBounds.height, 0.01f)
                assertTrue(mapping.heroBounds.left >= mapping.safeViewport.left)
                assertTrue(mapping.heroBounds.right <= safeRight)
                assertTrue(mapping.headerBounds.top >= mapping.safeViewport.top)
                assertTrue(mapping.headerBounds.bottom <= safeBottom)
                assertTrue(mapping.headerBounds.width <= EmptyHomeFrozenLayoutGeometry.HEADER_MAX_WIDTH_DP)
                assertTrue(mapping.headerBounds.height >= EmptyHomeFrozenLayoutGeometry.HEADER_MIN_TOUCH_TARGET_DP)
                assertTrue(mapping.headerBounds.bottom <= mapping.heroBounds.top)
                assertTrue(mapping.cameraBounds.left >= mapping.safeViewport.left)
                assertTrue(mapping.cameraBounds.right <= safeRight)
                assertTrue(mapping.cameraBounds.bottom <= safeBottom)
                assertTrue(mapping.albumBounds.height >= EmptyHomeFrozenLayoutGeometry.ALBUM_TOUCH_TARGET_DP)
                assertTrue(mapping.albumBounds.bottom <= safeBottom + 0.01f)
                assertTrue(
                    mapping.cameraBounds.top - mapping.promptBounds.bottom >=
                        EmptyHomeFrozenLayoutGeometry.PROMPT_CAMERA_GAP_MIN_PX * referenceScale - 0.01f,
                )
                assertTrue(
                    mapping.albumBounds.top - mapping.cameraBounds.bottom >=
                        EmptyHomeFrozenLayoutGeometry.CAMERA_ALBUM_GAP_MIN_PX * referenceScale - 0.01f,
                )
                val expectedSceneScale = maxOf(width / 1080f, height / 1920f)
                assertEquals(expectedSceneScale, mapping.sceneTransform.scale, 0.0001f)
            }
        }
    }

    @Test
    fun compact320By640UsesShortProfileWithoutShrinkingHeroOrTouchTarget() {
        val mapping = calculateEmptyHomeLayoutMapping(
            windowWidthDp = 320f,
            windowHeightDp = 640f,
            density = 1f,
            fontScale = 1.3f,
            safeInsets = insets,
        )

        assertEquals(AdaptiveHeightClass.SHORT, mapping.profile.heightClass)
        assertTrue(mapping.profile.requiresScrollableContent)
        assertEquals(2f, mapping.heroBounds.width / mapping.heroBounds.height, 0.01f)
        assertEquals(48f, mapping.albumBounds.height, 0.001f)
    }

    @Test
    fun horizontalSafeInsetsUsePhysicalLeftEdgeInBothLayoutDirections() {
        val asymmetricInsets = insets.copy(start = 20.dp, end = 8.dp)
        val ltr = calculateEmptyHomeLayoutMapping(
            windowWidthDp = 320f,
            windowHeightDp = 640f,
            density = 1f,
            fontScale = 1f,
            safeInsets = asymmetricInsets,
            layoutDirection = LayoutDirection.Ltr,
        )
        val rtl = calculateEmptyHomeLayoutMapping(
            windowWidthDp = 320f,
            windowHeightDp = 640f,
            density = 1f,
            fontScale = 1f,
            safeInsets = asymmetricInsets,
            layoutDirection = LayoutDirection.Rtl,
        )

        assertEquals(20f, ltr.safeViewport.left, 0.001f)
        assertEquals(8f, rtl.safeViewport.left, 0.001f)
        assertTrue(ltr.heroBounds.right <= ltr.safeViewport.right)
        assertTrue(rtl.heroBounds.right <= rtl.safeViewport.right)
    }

    @Test
    fun canonicalHeroFitPreservesSourceAspectWithinOnePercent() {
        val sourceWidth = EmptyHomeFrozenLayoutGeometry.HERO_ASSET_WIDTH_PX
        val sourceHeight = EmptyHomeFrozenLayoutGeometry.HERO_ASSET_HEIGHT_PX
        val boxWidth = EmptyHomeFrozenLayoutGeometry.HERO_WIDTH_PX
        val boxHeight = EmptyHomeFrozenLayoutGeometry.HERO_HEIGHT_PX
        val fitScale = minOf(boxWidth / sourceWidth, boxHeight / sourceHeight)
        val renderedWidth = sourceWidth * fitScale
        val renderedHeight = sourceHeight * fitScale
        val sourceAspect = sourceWidth / sourceHeight
        val renderedAspect = renderedWidth / renderedHeight

        assertTrue(abs(renderedAspect / sourceAspect - 1f) <= 0.01f)
    }
}
