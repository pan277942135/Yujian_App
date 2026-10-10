package com.yujian.ai.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.yujian.ai.ui.components.AssetImage
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.R
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.adaptive.SafeDrawingInsetsDp
import com.yujian.ai.ui.components.RemoteImage
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.typography.YuJianTypography
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

private const val NormalHomeReferenceWidth = 1080f
private const val NormalHomeHeaderX = 88f
private const val NormalHomeHeaderY = 104f
private const val NormalHomeHeaderWidth = 904f
private const val NormalHomeHeaderHeight = 104f
private const val NormalHomeStatsY = 304f
private const val NormalHomeRecentHeaderY = 494f
private const val NormalHomeRecentHeaderHeight = 70f
private const val NormalHomeCardY = 596f
private const val NormalHomeCardWidth = 740f
private const val NormalHomeCardHeight = 880f
private const val NormalHomeCtaY = 1512f
private const val NormalHomeCtaHeight = 58f
private const val NormalHomeCameraY = 1588f
private const val NormalHomeCameraSize = 200f
private const val NormalHomeCameraTouchSize = 208f
private const val NormalHomeHeroCornerRadius = 32f
private const val NormalHomeFooterHorizontalInset = 56f
private const val NormalHomeFooterVerticalInset = 32f

/**
 * Real-data Normal Home mapped onto the 1080-wide Frozen authority.
 *
 * Horizontal geometry and primary vertical anchors intentionally use the
 * approved 1080x1920 reference space. Taller devices keep the same visual
 * hierarchy and gain breathing room below the capture action instead of
 * stretching the card or collapsing the top sections.
 */
@Composable
internal fun NormalHomeContent(
    statistics: CatchStatistics,
    recentCatches: List<RemoteCatch>,
    resolveImageUrl: (String?) -> String?,
    accessToken: String,
    isLoggedIn: Boolean,
    avatarUrl: String?,
    onIdentify: () -> Unit,
    onSpeciesClick: () -> Unit,
    onCatchesClick: () -> Unit,
    onProfileClick: () -> Unit,
    onCatchClick: (String) -> Unit,
    isResolving: Boolean = false,
    motionState: HomeMotionState,
    runtimeAssets: NormalHomeRuntimeAssets?,
    safeInsets: SafeDrawingInsetsDp = SafeDrawingInsetsDp(),
    modifier: Modifier = Modifier,
) {
    val recent = remember(recentCatches) {
        orderedHomeRecords(recentCatches)
    }

    BoxWithConstraints(modifier = modifier.testTag("normal-home-content-root")) {
        val referenceScale = maxWidth / NormalHomeReferenceWidth
        fun ref(value: Float): Dp = normalHomeReferenceDp(value, maxWidth.value).dp
        val density = LocalDensity.current
        val typography = remember(maxWidth.value, density.density) {
            normalHomeTypographyContract(maxWidth.value, density.density)
        }
        val spacing = remember(maxWidth.value) { normalHomeSpacingContract(maxWidth.value) }
        val usableHeight = (maxHeight.value - safeInsets.top.value - safeInsets.bottom.value).coerceAtLeast(0f)
        val verticalOffset = normalHomeVerticalOffset(referenceScale.value, usableHeight).dp
        val layoutDirection = LocalLayoutDirection.current
        fun refY(value: Float): Dp = ref(value) + verticalOffset

        val cardWidth = ref(NormalHomeCardWidth)
        val cardHeight = ref(NormalHomeCardHeight)
        val pagerSidePadding = (maxWidth - cardWidth) / 2
        val fixedTextOverflow = remember(maxWidth.value, maxHeight.value, safeInsets, density.fontScale) {
            mutableStateOf(false)
        }
        val cameraTouchSize = maxOf(ref(NormalHomeCameraTouchSize), 48.dp)
        val safeOverflow = normalHomeRequiresSafeOverflow(
            referenceScale = referenceScale.value,
            windowWidthDp = maxWidth.value,
            windowHeightDp = maxHeight.value,
            safeInsets = safeInsets,
            layoutDirection = layoutDirection,
            verticalOffsetDp = verticalOffset.value,
            cameraTouchSizeDp = cameraTouchSize.value,
            fixedTextOverflow = fixedTextOverflow.value,
        )

        if (safeOverflow) {
            NormalHomeSafeOverflow(
                statistics = statistics,
                recent = recent,
                resolveImageUrl = resolveImageUrl,
                accessToken = accessToken,
                isLoggedIn = isLoggedIn,
                avatarUrl = avatarUrl,
                onIdentify = onIdentify,
                onSpeciesClick = onSpeciesClick,
                onCatchesClick = onCatchesClick,
                onProfileClick = onProfileClick,
                onCatchClick = onCatchClick,
                isResolving = isResolving,
                motionState = motionState,
                runtimeAssets = runtimeAssets,
                safeInsets = safeInsets,
            )
        } else {
        Box(
            modifier = Modifier
                .offset(x = ref(NormalHomeHeaderX), y = refY(NormalHomeHeaderY))
                .width(ref(NormalHomeHeaderWidth))
                .height(ref(NormalHomeHeaderHeight))
                .testTag("normal-home-header"),
        ) {
            NormalHomeHeader(
                isLoggedIn = isLoggedIn,
                avatarUrl = avatarUrl,
                resolveImageUrl = resolveImageUrl,
                accessToken = accessToken,
                onProfileClick = onProfileClick,
                avatarSize = ref(92f),
                typography = typography,
                brandMinWidth = ref(148f),
                onBrandTextLayout = { layout ->
                    if (normalHomeTextIsClipped(layout, expectedCharacters = 2)) {
                        fixedTextOverflow.value = true
                    }
                },
            )
        }

        Box(
            modifier = Modifier
                .offset(y = refY(NormalHomeStatsY))
                .fillMaxWidth()
                .testTag("normal-home-stats"),
            contentAlignment = Alignment.Center,
        ) {
            HomeStats(
                statistics = statistics,
                catches = recent,
                onSpeciesClick = onSpeciesClick,
                onCatchesClick = onCatchesClick,
                isResolving = isResolving,
                dividerHeight = ref(56f),
                dividerWidth = ref(1f),
                horizontalPadding = ref(150f),
                verticalPadding = spacing.statVerticalPadding,
                labelSpacing = spacing.statLabelSpacing,
                typography = typography,
            )
        }

        if (isResolving) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = refY(NormalHomeCardY))
                    .width(cardWidth)
                    .height(cardHeight)
                    .clip(RoundedCornerShape(ref(NormalHomeHeroCornerRadius)))
                    .background(YuJianColors.MistWhite.copy(alpha = 0.24f))
                    .testTag("normal-home-resolving-hero"),
            )
        } else {
            Box(
                modifier = Modifier
                    .offset(y = refY(NormalHomeRecentHeaderY))
                    .fillMaxWidth()
                    .height(ref(NormalHomeRecentHeaderHeight))
                    .testTag("normal-home-recent-header"),
                contentAlignment = Alignment.Center,
            ) {
                RecentCatchSectionHeader(
                    onCatchesClick = onCatchesClick,
                    typography = typography,
                    horizontalPadding = spacing.recentHeaderHorizontalInset,
                    actionSpacing = spacing.recentActionSpacing,
                    chevronSize = spacing.recentChevronSize,
                )
            }

            RecentCatchPager(
                catches = recent,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                sidePadding = pagerSidePadding,
                resolveImageUrl = resolveImageUrl,
                accessToken = accessToken,
                onCatchClick = onCatchClick,
                motionEnabled = normalHomeCatchMotionActive(motionState.running, motionState.reduceMotion),
                runtimeAssets = runtimeAssets,
                typography = typography,
                cornerRadius = ref(NormalHomeHeroCornerRadius),
                footerPaddingHorizontal = ref(NormalHomeFooterHorizontalInset),
                footerPaddingVertical = ref(NormalHomeFooterVerticalInset),
                metadataSpacing = spacing.heroMetadataSpacing,
                pageSpacing = spacing.pagerSpacing,
                referenceScale = referenceScale.value,
                modifier = Modifier.offset(y = refY(NormalHomeCardY)),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = refY(NormalHomeCtaY))
                    .height(ref(NormalHomeCtaHeight))
                    .width(ref(420f))
                    .testTag("normal-home-capture-cta"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "记录下一条鱼",
                    style = YuJianTypography.body.copy(
                        color = YuJianColors.OnDark.copy(alpha = 0.92f),
                        fontSize = typography.captureCta.fontSize,
                        lineHeight = typography.captureCta.lineHeight,
                        letterSpacing = (-4.2f / density.density).sp,
                        shadow = Shadow(YuJianColors.DeepLakeBlue.copy(alpha = 0.24f), blurRadius = 3f),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { layout ->
                        if (normalHomeTextIsClipped(layout, expectedCharacters = "记录下一条鱼".length)) {
                            fixedTextOverflow.value = true
                        }
                    },
                    modifier = Modifier.testTag("normal-home-capture-cta-text"),
                )
            }
        }

        val cameraSize = ref(NormalHomeCameraSize)
        HomeCameraButton(
            onClick = onIdentify,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = refY(NormalHomeCameraY))
                .testTag("normal-home-camera"),
            motionState = motionState,
            runtimeAssets = runtimeAssets,
            visualSize = cameraSize,
            touchTargetSize = cameraTouchSize,
        )
        }
    }
}

internal fun normalHomeRequiresSafeOverflow(
    referenceScale: Float,
    windowWidthDp: Float,
    windowHeightDp: Float,
    safeInsets: SafeDrawingInsetsDp,
    layoutDirection: androidx.compose.ui.unit.LayoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr,
    verticalOffsetDp: Float,
    cameraTouchSizeDp: Float,
    fixedTextOverflow: Boolean,
): Boolean {
    val safeBottom = windowHeightDp - safeInsets.bottom.value
    val headerTop = NormalHomeHeaderY * referenceScale + verticalOffsetDp
    val headerHeight = NormalHomeHeaderHeight * referenceScale
    val avatarTouchSize = maxOf(92f * referenceScale, 48f)
    val avatarTop = headerTop + (headerHeight - avatarTouchSize) / 2f
    val ctaTop = NormalHomeCtaY * referenceScale + verticalOffsetDp
    val ctaBottom = ctaTop + NormalHomeCtaHeight * referenceScale
    val cameraTop = NormalHomeCameraY * referenceScale + verticalOffsetDp
    val cameraBottom = cameraTop + cameraTouchSizeDp
    val heroBottom = (NormalHomeCardY + NormalHomeCardHeight) * referenceScale + verticalOffsetDp
    val physicalLeftInset = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) {
        safeInsets.start.value
    } else {
        safeInsets.end.value
    }
    val physicalRightInset = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) {
        safeInsets.end.value
    } else {
        safeInsets.start.value
    }
    val safeLeft = physicalLeftInset
    val safeRight = windowWidthDp - physicalRightInset
    val headerLeft = NormalHomeHeaderX * referenceScale
    val headerRight = (NormalHomeHeaderX + NormalHomeHeaderWidth) * referenceScale
    val actionsHalfWidth = maxOf(420f * referenceScale, cameraTouchSizeDp) / 2f
    val actionCenter = windowWidthDp / 2f

    return fixedTextOverflow ||
        avatarTop < safeInsets.top.value ||
        headerLeft < safeLeft ||
        headerRight > safeRight ||
        actionCenter - actionsHalfWidth < safeLeft ||
        actionCenter + actionsHalfWidth > safeRight ||
        ctaBottom > safeBottom ||
        cameraBottom > safeBottom ||
        heroBottom > ctaTop
}

private fun normalHomeTextIsClipped(layout: TextLayoutResult, expectedCharacters: Int): Boolean {
    if (layout.lineCount != 1) return true
    if (layout.getLineEnd(0, visibleEnd = true) < expectedCharacters) return true
    // didOverflowWidth can be true for a tight intrinsic Text width even when
    // every character is visible. The adaptive switch uses truncation and line
    // box clipping, which are measurable content loss rather than that flag.
    return layout.getLineTop(0) < -1f || layout.getLineBottom(0) > layout.size.height + 1f
}

/** Scroll required top content while pinning capture actions within the safe area. */
@Composable
private fun NormalHomeSafeOverflow(
    statistics: CatchStatistics,
    recent: List<RemoteCatch>,
    resolveImageUrl: (String?) -> String?,
    accessToken: String,
    isLoggedIn: Boolean,
    avatarUrl: String?,
    onIdentify: () -> Unit,
    onSpeciesClick: () -> Unit,
    onCatchesClick: () -> Unit,
    onProfileClick: () -> Unit,
    onCatchClick: (String) -> Unit,
    isResolving: Boolean,
    motionState: HomeMotionState,
    runtimeAssets: NormalHomeRuntimeAssets?,
    safeInsets: SafeDrawingInsetsDp,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = safeInsets.start,
                top = safeInsets.top,
                end = safeInsets.end,
                bottom = safeInsets.bottom,
            )
            .testTag("normal-home-safe-overflow-root"),
    ) {
        fun ref(value: Float): Dp = normalHomeReferenceDp(value, maxWidth.value).dp
        val referenceScale = maxWidth.value / NormalHomeReferenceWidth
        val density = LocalDensity.current
        val typography = remember(maxWidth.value, density.density, density.fontScale) {
            normalHomeTypographyContract(maxWidth.value, density.density)
        }
        val spacing = remember(maxWidth.value) { normalHomeSpacingContract(maxWidth.value) }
        val cardWidth = ref(NormalHomeCardWidth)
        val cardHeight = ref(NormalHomeCardHeight)
        val pagerSidePadding = (maxWidth - cardWidth) / 2
        val cameraTouchSize = maxOf(ref(NormalHomeCameraTouchSize), 48.dp)
        val cameraVisualSize = ref(NormalHomeCameraSize)
        val ctaHeight = maxOf(
            ref(NormalHomeCtaHeight),
            48.dp,
            (typography.captureCta.fontSize.value * density.fontScale * 1.5f).dp,
        )
        val dockGap = ref(11f)
        val dockBottomClearance = 16.dp
        val dockContentHeight = cameraTouchSize.value +
            (if (isResolving) 0f else ctaHeight.value + dockGap.value)
        val scrollViewportHeight = (
            maxHeight.value - dockContentHeight - dockBottomClearance.value
        ).coerceAtLeast(0f)
        val scrollState = rememberScrollState()

        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(scrollViewportHeight.dp)
                    .verticalScroll(scrollState)
                    .testTag(
                        if (scrollViewportHeight >= 160f) {
                            "normal-home-safe-scroll"
                        } else {
                            "normal-home-safe-scroll-below-160dp-contract"
                        },
                    ),
            ) {
                Spacer(Modifier.height(ref(NormalHomeHeaderY)))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ref(NormalHomeHeaderX))
                        .height(maxOf(ref(NormalHomeHeaderHeight), 48.dp))
                        .testTag("normal-home-header"),
                ) {
                    NormalHomeHeader(
                        isLoggedIn = isLoggedIn,
                        avatarUrl = avatarUrl,
                        resolveImageUrl = resolveImageUrl,
                        accessToken = accessToken,
                        onProfileClick = onProfileClick,
                        avatarSize = ref(92f),
                        typography = typography,
                        brandMinWidth = ref(148f),
                    )
                }

                Spacer(Modifier.height(ref(96f)))
                HomeStats(
                    statistics = statistics,
                    catches = recent,
                    onSpeciesClick = onSpeciesClick,
                    onCatchesClick = onCatchesClick,
                    isResolving = isResolving,
                    dividerHeight = ref(56f),
                    dividerWidth = ref(1f),
                    horizontalPadding = ref(150f),
                    verticalPadding = spacing.statVerticalPadding,
                    labelSpacing = spacing.statLabelSpacing,
                    typography = typography,
                )

                Spacer(Modifier.height(ref(96f)))
                if (isResolving) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(cardWidth)
                            .height(cardHeight)
                            .clip(RoundedCornerShape(ref(NormalHomeHeroCornerRadius)))
                            .background(YuJianColors.MistWhite.copy(alpha = 0.24f))
                            .testTag("normal-home-resolving-hero"),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(maxOf(ref(NormalHomeRecentHeaderHeight), 48.dp))
                            .testTag("normal-home-recent-header"),
                        contentAlignment = Alignment.Center,
                    ) {
                        RecentCatchSectionHeader(
                            onCatchesClick = onCatchesClick,
                            typography = typography,
                            horizontalPadding = spacing.recentHeaderHorizontalInset,
                            actionSpacing = spacing.recentActionSpacing,
                            chevronSize = spacing.recentChevronSize,
                        )
                    }
                    Spacer(Modifier.height(ref(24f)))
                    RecentCatchPager(
                        catches = recent,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        sidePadding = pagerSidePadding,
                        resolveImageUrl = resolveImageUrl,
                        accessToken = accessToken,
                        onCatchClick = onCatchClick,
                        motionEnabled = normalHomeCatchMotionActive(motionState.running, motionState.reduceMotion),
                        runtimeAssets = runtimeAssets,
                        typography = typography,
                        cornerRadius = ref(NormalHomeHeroCornerRadius),
                        footerPaddingHorizontal = ref(NormalHomeFooterHorizontalInset),
                        footerPaddingVertical = ref(NormalHomeFooterVerticalInset),
                        metadataSpacing = spacing.heroMetadataSpacing,
                        pageSpacing = spacing.pagerSpacing,
                        referenceScale = referenceScale,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = dockBottomClearance)
                    .testTag("normal-home-safe-action-dock"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!isResolving) {
                    Box(
                        modifier = Modifier
                            .width(ref(420f))
                            .heightIn(min = ctaHeight)
                            .testTag("normal-home-capture-cta"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "记录下一条鱼",
                            style = YuJianTypography.body.copy(
                                color = YuJianColors.OnDark.copy(alpha = 0.92f),
                                fontSize = typography.captureCta.fontSize,
                                lineHeight = typography.captureCta.lineHeight,
                                letterSpacing = (-4.2f / density.density).sp,
                                shadow = Shadow(YuJianColors.DeepLakeBlue.copy(alpha = 0.24f), blurRadius = 3f),
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("normal-home-capture-cta-text"),
                        )
                    }
                    Spacer(Modifier.height(dockGap))
                }
                HomeCameraButton(
                    onClick = onIdentify,
                    modifier = Modifier.testTag("normal-home-camera"),
                    motionState = motionState,
                    runtimeAssets = runtimeAssets,
                    visualSize = cameraVisualSize,
                    touchTargetSize = cameraTouchSize,
                )
            }
        }
    }
}

/** Applies the single NH05 vertical shift after safe-area insets are removed. */
internal fun normalHomeVerticalOffset(referenceScale: Float, usableHeight: Float): Float {
    val referenceHeight = 1920f * referenceScale
    val excessHeight = (usableHeight - referenceHeight).coerceAtLeast(0f)
    return min(excessHeight * 0.36f, 180f * referenceScale)
}

@Composable
private fun NormalHomeHeader(
    isLoggedIn: Boolean,
    avatarUrl: String?,
    resolveImageUrl: (String?) -> String?,
    accessToken: String,
    onProfileClick: () -> Unit,
    avatarSize: Dp,
    typography: NormalHomeTypographyContract,
    brandMinWidth: Dp,
    onBrandTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val resolvedAvatarUrl = resolveImageUrl(avatarUrl)
    val avatarLoadResult = remember(resolvedAvatarUrl) { mutableStateOf<Boolean?>(null) }
    val avatarState = normalHomeAvatarState(isLoggedIn, resolvedAvatarUrl, avatarLoadResult.value)
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "渔见",
            style = YuJianTypography.brand.copy(
                color = YuJianColors.TextPrimary.copy(alpha = 0.94f),
                fontSize = typography.brand.fontSize,
                lineHeight = typography.brand.lineHeight,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = onBrandTextLayout,
            // Preserve the 72 px glyph contract while giving Android's measured
            // two-glyph outline room for its reported 3 px intrinsic overhang.
            modifier = Modifier
                .widthIn(min = brandMinWidth)
                .testTag("normal-home-brand-title"),
        )
        if (isLoggedIn) {
            val profileAvatarStateTag = when (avatarState) {
                NormalHomeAvatarState.PROFILE_IMAGE -> "normal-home-profile-avatar-loaded"
                NormalHomeAvatarState.PROFILE_LOADING -> "normal-home-profile-avatar-loading"
                else -> "normal-home-profile-avatar-fallback"
            }
            Box(
                modifier = Modifier
                    .requiredSizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics {
                        contentDescription = "个人中心"
                        role = Role.Button
                    }
                    .clickable(onClick = onProfileClick)
                    .testTag(profileAvatarStateTag),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(avatarSize)) {
                    NormalHomeDefaultAvatar(Modifier.fillMaxSize())
                    if (avatarState == NormalHomeAvatarState.PROFILE_LOADING ||
                        avatarState == NormalHomeAvatarState.PROFILE_IMAGE
                    ) {
                        Box(Modifier.fillMaxSize().clip(CircleShape)) {
                            RemoteImage(
                                url = resolvedAvatarUrl,
                                authToken = accessToken,
                                modifier = Modifier.fillMaxSize(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                onLoadResult = { avatarLoadResult.value = it },
                                placeholder = { Box(Modifier.fillMaxSize()) },
                            )
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .requiredSizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics {
                        contentDescription = "登录或注册"
                        role = Role.Button
                    }
                    .clickable(onClick = onProfileClick),
                contentAlignment = Alignment.Center,
            ) {
                AssetImage(
                    assetPath = NORMAL_HOME_GUEST_AVATAR_ASSET_PATH,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(avatarSize)
                        .testTag("normal-home-guest-avatar"),
                )
            }
        }
    }
}

@Composable
private fun NormalHomeDefaultAvatar(modifier: Modifier) {
    Image(
        painter = painterResource(R.drawable.normal_home_default_avatar_v2),
        contentDescription = null,
        modifier = modifier.testTag("normal-home-default-profile-avatar"),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun RecentCatchSectionHeader(
    onCatchesClick: () -> Unit,
    typography: NormalHomeTypographyContract,
    horizontalPadding: Dp,
    actionSpacing: Dp,
    chevronSize: Dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "最近鱼获",
            style = YuJianTypography.sectionTitle.copy(
                color = YuJianColors.OnDark,
                fontSize = typography.sectionTitle.fontSize,
                lineHeight = typography.sectionTitle.lineHeight,
                shadow = Shadow(YuJianColors.DeepLakeBlue.copy(alpha = 0.30f), blurRadius = 3f),
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("normal-home-recent-title"),
        )
        Row(
            modifier = Modifier
                .requiredSizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "全部鱼获"
                    role = Role.Button
                }
                .clickable(role = Role.Button, onClick = onCatchesClick)
                .testTag("normal-home-recent-all"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(actionSpacing),
        ) {
            Text(
                text = "全部",
                style = YuJianTypography.body.copy(
                    color = YuJianColors.OnDark,
                    fontSize = typography.action.fontSize,
                    lineHeight = typography.action.lineHeight,
                    shadow = Shadow(YuJianColors.DeepLakeBlue.copy(alpha = 0.30f), blurRadius = 3f),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("normal-home-recent-all-label"),
            )
            Image(
                painter = painterResource(R.drawable.all_chevron_v12),
                contentDescription = null,
                modifier = Modifier.size(chevronSize),
            )
        }
    }
}

@Composable
private fun RecentCatchPager(
    catches: List<RemoteCatch>,
    cardWidth: Dp,
    cardHeight: Dp,
    sidePadding: Dp,
    resolveImageUrl: (String?) -> String?,
    accessToken: String,
    onCatchClick: (String) -> Unit,
    motionEnabled: Boolean,
    runtimeAssets: NormalHomeRuntimeAssets?,
    typography: NormalHomeTypographyContract,
    cornerRadius: Dp,
    footerPaddingHorizontal: Dp,
    footerPaddingVertical: Dp,
    metadataSpacing: Dp,
    pageSpacing: Dp,
    referenceScale: Float,
    modifier: Modifier = Modifier,
) {
    if (catches.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { catches.size })
    val density = LocalDensity.current
    val activeCardScale = remember { Animatable(1f) }
    val activeCardOffset = remember { Animatable(0f) }
    val selectedCatchId = catches.getOrNull(pagerState.currentPage)?.id ?: catches.first().id

    LaunchedEffect(motionEnabled, pagerState.currentPage, selectedCatchId) {
        if (!motionEnabled) {
            activeCardScale.snapTo(1f)
            activeCardOffset.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            coroutineScope {
                launch {
                    activeCardScale.animateTo(1f, keyframes {
                        durationMillis = 6_000
                        1.008f at 3_000
                    })
                }
                launch {
                    activeCardOffset.animateTo(0f, keyframes {
                        durationMillis = 6_000
                        (-2f * referenceScale) at 3_000
                    })
                }
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxWidth()
            .height(cardHeight)
            .testTag("normal-home-catch-pager"),
        pageSize = PageSize.Fixed(cardWidth),
        contentPadding = PaddingValues(horizontal = sidePadding),
        pageSpacing = pageSpacing,
        beyondViewportPageCount = if (catches.size > 1) 1 else 0,
    ) { page ->
        val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
        val distance = abs(pageOffset).coerceAtMost(1f)
        val selected = page == pagerState.currentPage
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayerForPagerCard(
                    scale = if (selected) activeCardScale.value else 1f - distance * 0.055f,
                    translationY = if (selected) activeCardOffset.value else 4f * distance * referenceScale,
                    alpha = 1f - distance * 0.12f,
                    density = density,
                ),
        ) {
            val item = catches[page]
            RecentFishCard(
                item = item,
                imageUrl = resolveImageUrl(item.imageUrl),
                accessToken = accessToken,
                onClick = { onCatchClick(item.id) },
                cardHeight = cardHeight,
                runtimeAssets = runtimeAssets,
                typography = typography,
                cornerRadius = cornerRadius,
                footerPaddingHorizontal = footerPaddingHorizontal,
                footerPaddingVertical = footerPaddingVertical,
                metadataSpacing = metadataSpacing,
            )
        }
    }
}

private fun Modifier.graphicsLayerForPagerCard(
    scale: Float,
    translationY: Float,
    alpha: Float,
    density: Density,
): Modifier = graphicsLayer {
    scaleX = scale
    scaleY = scale
    this.translationY = with(density) { translationY.dp.toPx() }
    this.alpha = alpha
}
