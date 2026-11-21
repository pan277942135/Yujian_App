package com.yujian.ai.ui.adaptive

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

enum class AdaptiveWidthClass { COMPACT, STANDARD, MEDIUM, WIDE }

enum class AdaptiveHeightClass { SHORT, STANDARD, TALL }

data class SafeDrawingInsetsDp(
    val top: Dp = 0.dp,
    val bottom: Dp = 0.dp,
    val start: Dp = 0.dp,
    val end: Dp = 0.dp,
)

/**
 * Shared responsive profile for page geometry. Width and height are the safe
 * drawing viewport, after status bar, cutout, gesture/navigation and side
 * insets have been accounted for.
 */
data class AdaptiveLayoutProfile(
    val safeWidthDp: Float,
    val safeHeightDp: Float,
    val fontScale: Float,
    val widthClass: AdaptiveWidthClass,
    val heightClass: AdaptiveHeightClass,
    val horizontalContentInsetDp: Float,
    val accessibilityFontScale: Boolean,
) {
    val isShortViewport: Boolean get() = heightClass == AdaptiveHeightClass.SHORT
    val requiresScrollableContent: Boolean
        get() = isShortViewport || accessibilityFontScale
}

fun resolveAdaptiveLayoutProfile(
    windowWidthDp: Float,
    windowHeightDp: Float,
    fontScale: Float,
    safeInsets: SafeDrawingInsetsDp = SafeDrawingInsetsDp(),
): AdaptiveLayoutProfile {
    require(windowWidthDp > 0f && windowHeightDp > 0f)
    require(fontScale > 0f)

    val safeWidth = (windowWidthDp - safeInsets.start.value - safeInsets.end.value).coerceAtLeast(1f)
    val safeHeight = (windowHeightDp - safeInsets.top.value - safeInsets.bottom.value).coerceAtLeast(1f)
    val widthClass = when {
        safeWidth <= 320f -> AdaptiveWidthClass.COMPACT
        safeWidth <= 360f -> AdaptiveWidthClass.STANDARD
        safeWidth < 411f -> AdaptiveWidthClass.MEDIUM
        else -> AdaptiveWidthClass.WIDE
    }
    val heightClass = when {
        safeHeight < 600f -> AdaptiveHeightClass.SHORT
        safeHeight < 720f -> AdaptiveHeightClass.STANDARD
        else -> AdaptiveHeightClass.TALL
    }
    val horizontalInset = when (widthClass) {
        AdaptiveWidthClass.COMPACT -> 16f
        AdaptiveWidthClass.STANDARD -> 20f
        AdaptiveWidthClass.MEDIUM -> 22f
        AdaptiveWidthClass.WIDE -> 24f
    }

    return AdaptiveLayoutProfile(
        safeWidthDp = safeWidth,
        safeHeightDp = safeHeight,
        fontScale = fontScale,
        widthClass = widthClass,
        heightClass = heightClass,
        horizontalContentInsetDp = horizontalInset,
        accessibilityFontScale = fontScale >= 1.3f,
    )
}

/** Minimum height for a required text block. Callers grow or scroll; they do not shrink text. */
fun requiredTextBlockHeightDp(
    lineHeightSp: Float,
    lineCount: Int,
    fontScale: Float,
    verticalPaddingDp: Float = 0f,
): Float {
    require(lineHeightSp > 0f && lineCount > 0 && fontScale > 0f && verticalPaddingDp >= 0f)
    return ceil(lineHeightSp * fontScale * lineCount + verticalPaddingDp)
}

@Composable
fun rememberSafeDrawingInsets(): SafeDrawingInsetsDp {
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val direction = LocalLayoutDirection.current
    val density = LocalDensity.current
    return remember(
        safe.calculateTopPadding(),
        safe.calculateBottomPadding(),
        safe.calculateStartPadding(direction),
        safe.calculateEndPadding(direction),
        density,
    ) {
        SafeDrawingInsetsDp(
            top = safe.calculateTopPadding(),
            bottom = safe.calculateBottomPadding(),
            start = safe.calculateStartPadding(direction),
            end = safe.calculateEndPadding(direction),
        )
    }
}

@Composable
fun rememberAdaptiveLayoutProfile(windowWidthDp: Dp, windowHeightDp: Dp): AdaptiveLayoutProfile {
    val safeInsets = rememberSafeDrawingInsets()
    val fontScale = LocalDensity.current.fontScale
    return remember(windowWidthDp, windowHeightDp, fontScale, safeInsets) {
        resolveAdaptiveLayoutProfile(
            windowWidthDp = windowWidthDp.value,
            windowHeightDp = windowHeightDp.value,
            fontScale = fontScale,
            safeInsets = safeInsets,
        )
    }
}
