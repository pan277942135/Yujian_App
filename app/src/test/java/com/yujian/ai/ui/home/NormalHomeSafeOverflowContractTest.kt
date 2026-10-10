package com.yujian.ai.ui.home

import androidx.compose.ui.unit.dp
import com.yujian.ai.ui.adaptive.SafeDrawingInsetsDp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalHomeSafeOverflowContractTest {
    @Test
    fun frozen1080x1920IdentityKeepsNormalFixedLayout() {
        val scale = 1f
        assertFalse(
            normalHomeRequiresSafeOverflow(
                referenceScale = scale,
                windowWidthDp = 1080f,
                windowHeightDp = 1920f,
                safeInsets = SafeDrawingInsetsDp(),
                verticalOffsetDp = normalHomeVerticalOffset(scale, 1920f),
                cameraTouchSizeDp = 208f,
                fixedTextOverflow = false,
            ),
        )
    }

    @Test
    fun twentyOneByNineTallViewportKeepsNormalFixedLayout() {
        val scale = 1f
        val height = 2340f
        assertFalse(
            normalHomeRequiresSafeOverflow(
                referenceScale = scale,
                windowWidthDp = 1080f,
                windowHeightDp = height,
                safeInsets = SafeDrawingInsetsDp(),
                verticalOffsetDp = normalHomeVerticalOffset(scale, height),
                cameraTouchSizeDp = 208f,
                fixedTextOverflow = false,
            ),
        )
    }

    @Test
    fun shortSafeViewportEntersOverflowFromTouchBounds() {
        val scale = 320f / 1080f
        val height = 480f
        val insets = SafeDrawingInsetsDp(top = 24.dp, bottom = 24.dp)
        assertTrue(
            normalHomeRequiresSafeOverflow(
                referenceScale = scale,
                windowWidthDp = 320f,
                windowHeightDp = height,
                safeInsets = insets,
                verticalOffsetDp = normalHomeVerticalOffset(scale, height - 48f),
                cameraTouchSizeDp = maxOf(208f * scale, 48f),
                fixedTextOverflow = false,
            ),
        )
    }

    @Test
    fun asymmetricHorizontalInsetsMoveLayoutToSafeOverflow() {
        val scale = 360f / 1080f
        assertTrue(
            normalHomeRequiresSafeOverflow(
                referenceScale = scale,
                windowWidthDp = 360f,
                windowHeightDp = 640f,
                safeInsets = SafeDrawingInsetsDp(start = 34.dp, end = 12.dp),
                verticalOffsetDp = normalHomeVerticalOffset(scale, 640f),
                cameraTouchSizeDp = maxOf(208f * scale, 48f),
                fixedTextOverflow = false,
            ),
        )
    }

    @Test
    fun visibleSingleLineDoesNotOverflowForPlatformLineBoxOverhang() {
        // Android reports lineBottom=107 for this title while the measured Text
        // height is 104px and raster ink remains within the 104px header.
        assertFalse(normalHomeTextIsActuallyTruncated(lineCount = 1, visibleEnd = 2, expectedCharacters = 2))
    }

    @Test
    fun wrappedOrEllipsizedTextEntersOverflow() {
        assertTrue(normalHomeTextIsActuallyTruncated(lineCount = 2, visibleEnd = 2, expectedCharacters = 2))
        assertTrue(normalHomeTextIsActuallyTruncated(lineCount = 1, visibleEnd = 1, expectedCharacters = 2))
    }

    @Test
    fun clippedFixedTextAlsoEntersOverflow() {
        assertTrue(
            normalHomeRequiresSafeOverflow(
                referenceScale = 1f,
                windowWidthDp = 1080f,
                windowHeightDp = 1920f,
                safeInsets = SafeDrawingInsetsDp(),
                verticalOffsetDp = 0f,
                cameraTouchSizeDp = 208f,
                fixedTextOverflow = true,
            ),
        )
    }
}
