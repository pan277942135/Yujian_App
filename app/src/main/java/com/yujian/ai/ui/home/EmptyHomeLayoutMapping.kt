package com.yujian.ai.ui.home

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import com.yujian.ai.ui.adaptive.AdaptiveLayoutProfile
import com.yujian.ai.ui.adaptive.SafeDrawingInsetsDp
import com.yujian.ai.ui.adaptive.resolveAdaptiveLayoutProfile
import com.yujian.ai.ui.adaptive.requiredTextBlockHeightDp
import kotlin.math.max
import kotlin.math.min

/** A page-local rectangle in Compose density-independent pixels. */
internal data class EmptyHomeBoundsDp(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

/**
 * One translation of the frozen V2/V2.2 geometry into the live Home viewport.
 * Scene layers use the same reference-space transform; native UI uses the safe
 * drawing viewport and keeps content dimensions independent of scene cropping.
 */
internal data class EmptyHomeLayoutMapping(
    val profile: AdaptiveLayoutProfile,
    val safeViewport: EmptyHomeBoundsDp,
    val sceneTransform: ReferenceSceneTransform,
    val headerBounds: EmptyHomeBoundsDp,
    val heroBounds: EmptyHomeBoundsDp,
    val promptBounds: EmptyHomeBoundsDp,
    val cameraBounds: EmptyHomeBoundsDp,
    val albumBounds: EmptyHomeBoundsDp,
    val windowWidthDp: Float,
    val windowHeightDp: Float,
)

/** Typed values mirrored by design/pages/home/empty_home/shared/contracts. */
internal object EmptyHomeFrozenLayoutGeometry {
    const val REFERENCE_WIDTH_PX = 1080f
    const val REFERENCE_HEIGHT_PX = 1920f
    const val HERO_X_PX = 50f
    const val HERO_Y_PX = 224f
    const val HERO_WIDTH_PX = 620f
    const val HERO_HEIGHT_PX = 310f
    const val HERO_ASSET_WIDTH_PX = 625f
    const val HERO_ASSET_HEIGHT_PX = 311f
    const val PROMPT_Y_PX = 1448f
    const val CAMERA_X_PX = 430f
    const val CAMERA_Y_PX = 1537f
    const val CAMERA_SIZE_PX = 220f
    const val ALBUM_Y_PX = 1780f
    const val PROMPT_CAMERA_GAP_MIN_PX = 25f
    const val CAMERA_ALBUM_GAP_MIN_PX = 23f
    const val SAFE_HEADER_INSET_DP = 16f
    const val HEADER_MAX_WIDTH_DP = 430f
    const val HEADER_MIN_TOUCH_TARGET_DP = 48f
    const val PROMPT_LINE_HEIGHT_SP = 24f
    const val ALBUM_TOUCH_TARGET_DP = 48f
}

internal fun calculateEmptyHomeLayoutMapping(
    windowWidthDp: Float,
    windowHeightDp: Float,
    density: Float,
    fontScale: Float,
    safeInsets: SafeDrawingInsetsDp,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
): EmptyHomeLayoutMapping {
    require(windowWidthDp > 0f && windowHeightDp > 0f && density > 0f && fontScale > 0f)
    val profile = resolveAdaptiveLayoutProfile(
        windowWidthDp = windowWidthDp,
        windowHeightDp = windowHeightDp,
        fontScale = fontScale,
        safeInsets = safeInsets,
    )
    val safeLeft = if (layoutDirection == LayoutDirection.Ltr) {
        safeInsets.start.value
    } else {
        safeInsets.end.value
    }
    val safeTop = safeInsets.top.value
    val safe = EmptyHomeBoundsDp(
        left = safeLeft,
        top = safeTop,
        width = profile.safeWidthDp,
        height = profile.safeHeightDp,
    )
    val referenceWidth = EmptyHomeFrozenLayoutGeometry.REFERENCE_WIDTH_PX
    val referenceHeight = EmptyHomeFrozenLayoutGeometry.REFERENCE_HEIGHT_PX
    val groupScale = profile.safeWidthDp / referenceWidth
    val transform = calculateReferenceSceneTransform(
        containerWidthPx = windowWidthDp * density,
        containerHeightPx = windowHeightDp * density,
    )

    val heroWidth = min(
        EmptyHomeFrozenLayoutGeometry.HERO_WIDTH_PX,
        profile.safeWidthDp * EmptyHomeFrozenLayoutGeometry.HERO_WIDTH_PX / referenceWidth,
    )
    val heroHeight = heroWidth *
        (EmptyHomeFrozenLayoutGeometry.HERO_HEIGHT_PX / EmptyHomeFrozenLayoutGeometry.HERO_WIDTH_PX)
    val hero = EmptyHomeBoundsDp(
        left = safe.left + profile.safeWidthDp * EmptyHomeFrozenLayoutGeometry.HERO_X_PX / referenceWidth,
        top = safe.top + profile.safeHeightDp * EmptyHomeFrozenLayoutGeometry.HERO_Y_PX / referenceHeight,
        width = heroWidth,
        height = heroHeight,
    )

    val cameraSize = EmptyHomeFrozenLayoutGeometry.CAMERA_SIZE_PX * groupScale
    val albumTouchHeight = EmptyHomeFrozenLayoutGeometry.ALBUM_TOUCH_TARGET_DP
    val mappedAlbumBottomReserve = (referenceHeight - EmptyHomeFrozenLayoutGeometry.ALBUM_Y_PX) * groupScale
    val bottomCompensation = max(0f, albumTouchHeight - mappedAlbumBottomReserve)
    val ctaTranslationY = profile.safeHeightDp - referenceHeight * groupScale - bottomCompensation
    val cameraTop = safe.top + ctaTranslationY + EmptyHomeFrozenLayoutGeometry.CAMERA_Y_PX * groupScale
    val minimumPromptGap = EmptyHomeFrozenLayoutGeometry.PROMPT_CAMERA_GAP_MIN_PX * groupScale
    val promptHeight = requiredTextBlockHeightDp(
        lineHeightSp = EmptyHomeFrozenLayoutGeometry.PROMPT_LINE_HEIGHT_SP,
        lineCount = 1,
        fontScale = fontScale,
    )
    val frozenPromptOffset =
        (EmptyHomeFrozenLayoutGeometry.CAMERA_Y_PX - EmptyHomeFrozenLayoutGeometry.PROMPT_Y_PX) * groupScale
    val promptTop = cameraTop - max(frozenPromptOffset, promptHeight + minimumPromptGap)
    val camera = EmptyHomeBoundsDp(
        left = safe.left + EmptyHomeFrozenLayoutGeometry.CAMERA_X_PX * groupScale,
        top = cameraTop,
        width = cameraSize,
        height = cameraSize,
    )
    val albumTop = safe.top + ctaTranslationY + EmptyHomeFrozenLayoutGeometry.ALBUM_Y_PX * groupScale
    val minimumAlbumGap = EmptyHomeFrozenLayoutGeometry.CAMERA_ALBUM_GAP_MIN_PX * groupScale
    val album = EmptyHomeBoundsDp(
        left = safe.left + profile.safeWidthDp / 2f,
        top = max(albumTop, camera.bottom + minimumAlbumGap),
        width = 0f,
        height = albumTouchHeight,
    )
    val prompt = EmptyHomeBoundsDp(
        left = safe.left,
        top = promptTop,
        width = profile.safeWidthDp,
        height = promptHeight,
    )
    val headerWidth = min(profile.safeWidthDp, EmptyHomeFrozenLayoutGeometry.HEADER_MAX_WIDTH_DP)
    val header = EmptyHomeBoundsDp(
        left = safe.left + (profile.safeWidthDp - headerWidth) / 2f,
        top = safe.top + EmptyHomeFrozenLayoutGeometry.SAFE_HEADER_INSET_DP,
        width = headerWidth,
        height = EmptyHomeFrozenLayoutGeometry.HEADER_MIN_TOUCH_TARGET_DP,
    )

    return EmptyHomeLayoutMapping(
        profile = profile,
        safeViewport = safe,
        sceneTransform = transform,
        headerBounds = header,
        heroBounds = hero,
        promptBounds = prompt,
        cameraBounds = camera,
        albumBounds = album,
        windowWidthDp = windowWidthDp,
        windowHeightDp = windowHeightDp,
    )
}
