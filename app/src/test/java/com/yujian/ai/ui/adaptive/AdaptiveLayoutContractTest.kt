package com.yujian.ai.ui.adaptive

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutContractTest {
    @Test
    fun frozenResponsiveViewportProfilesResolveBySafeWidthAndHeight() {
        val profiles = listOf(
            Triple(320f, 640f, AdaptiveWidthClass.COMPACT),
            Triple(360f, 780f, AdaptiveWidthClass.STANDARD),
            Triple(393f, 852f, AdaptiveWidthClass.MEDIUM),
            Triple(411f, 891f, AdaptiveWidthClass.WIDE),
        ).map { (width, height, widthClass) ->
            resolveAdaptiveLayoutProfile(width, height, 1f).also {
                assertEquals(widthClass, it.widthClass)
                assertEquals(AdaptiveHeightClass.TALL, it.heightClass)
                assertFalse(it.accessibilityFontScale)
            }
        }

        assertEquals(listOf(16f, 20f, 22f, 24f), profiles.map { it.horizontalContentInsetDp })
    }

    @Test
    fun safeDrawingInsetsReduceGeometryForCutoutAndGestureNavigation() {
        val profile = resolveAdaptiveLayoutProfile(
            windowWidthDp = 393f,
            windowHeightDp = 852f,
            fontScale = 1f,
            safeInsets = SafeDrawingInsetsDp(
                top = 36.dp,
                bottom = 24.dp,
                start = 8.dp,
                end = 8.dp,
            ),
        )

        assertEquals(377f, profile.safeWidthDp, 0.01f)
        assertEquals(792f, profile.safeHeightDp, 0.01f)
        assertEquals(AdaptiveWidthClass.MEDIUM, profile.widthClass)
        assertEquals(AdaptiveHeightClass.TALL, profile.heightClass)
    }

    @Test
    fun shortAndStandardHeightBoundariesAreExplicit() {
        assertEquals(AdaptiveHeightClass.SHORT, resolveAdaptiveLayoutProfile(320f, 599f, 1f).heightClass)
        assertEquals(AdaptiveHeightClass.STANDARD, resolveAdaptiveLayoutProfile(360f, 600f, 1f).heightClass)
        assertEquals(AdaptiveHeightClass.STANDARD, resolveAdaptiveLayoutProfile(393f, 719f, 1f).heightClass)
        assertEquals(AdaptiveHeightClass.TALL, resolveAdaptiveLayoutProfile(411f, 720f, 1f).heightClass)
        assertTrue(resolveAdaptiveLayoutProfile(320f, 580f, 1f).requiresScrollableContent)
    }

    @Test
    fun accessibilityFontScaleGrowsRequiredTextBlocksInsteadOfShrinkingTypography() {
        val regular = resolveAdaptiveLayoutProfile(320f, 640f, 1f)
        val accessible = resolveAdaptiveLayoutProfile(320f, 640f, 1.3f)
        val oneLineLabelHeight = requiredTextBlockHeightDp(
            lineHeightSp = 16f,
            lineCount = 1,
            fontScale = accessible.fontScale,
            verticalPaddingDp = 8f,
        )

        assertFalse(regular.accessibilityFontScale)
        assertTrue(accessible.accessibilityFontScale)
        assertTrue(accessible.requiresScrollableContent)
        assertEquals(29f, oneLineLabelHeight, 0.01f)
        assertTrue(oneLineLabelHeight >= 16f * 1.3f + 8f)
    }
}
