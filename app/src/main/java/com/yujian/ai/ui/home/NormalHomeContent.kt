package com.yujian.ai.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
private const val NormalHomeCtaY = 1495f
private const val NormalHomeCtaGlyphOffsetY = 17f
// The source raster mask resolves visible ink; these are not historic touch-box coordinates.
private const val NormalHomeCameraY = 1583f
private const val NormalHomeCameraX = -1.5f
private const val NormalHomeCameraSize = 208f
private const val NormalHomeCameraTouchSize = 216f
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
        fun refY(value: Float): Dp = ref(value) + verticalOffset

        val captureCtaTextHeight = remember(maxWidth.value, density.fontScale) {
            mutableStateOf(ref(58f))
        }
        // The frozen glyph anchor sits 17 reference px below the layout origin;
        // include that placement in the measured parent so semantics and bounds
        // contain the rendered text at every system font scale.
        val captureCtaHeight = maxOf(
            ref(58f),
            captureCtaTextHeight.value + ref(NormalHomeCtaGlyphOffsetY),
        )
        val cameraY = maxOf(
            refY(NormalHomeCameraY),
            refY(NormalHomeCtaY) + captureCtaHeight + ref(8f),
        )

        val cardWidth = ref(NormalHomeCardWidth)
        val cardHeight = ref(NormalHomeCardHeight)
        val pagerSidePadding = (maxWidth - cardWidth) / 2
        val cameraTouchBottom = cameraY.value + ref(NormalHomeCameraTouchSize).value
        val shortScreenMode = normalHomeNeedsSafeOverflow(
            cameraTouchBottom = cameraTouchBottom,
            windowHeight = maxHeight.value,
            safeBottom = safeInsets.bottom.value,
        )
        val contentHeight = normalHomeScrollableContentHeight(
            windowHeight = maxHeight.value,
            cameraTouchBottom = cameraTouchBottom,
            safeBottom = safeInsets.bottom.value,
        ).dp
        val scrollState = rememberScrollState()
        val initialEmptyResolution = remember { mutableStateOf(isResolving && recent.isEmpty()) }
        val revealFirstResolvedCatch = initialEmptyResolution.value && !isResolving && recent.isNotEmpty()
        SideEffect {
            if (isResolving && recent.isEmpty() && !initialEmptyResolution.value) {
                initialEmptyResolution.value = true
            } else if (!isResolving && recent.isNotEmpty() && initialEmptyResolution.value) {
                initialEmptyResolution.value = false
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState, enabled = shortScreenMode)
                .testTag(if (shortScreenMode) "normal-home-short-screen-scroll" else "normal-home-fixed-layout"),
        ) {
            Box(Modifier.fillMaxWidth().height(contentHeight)) {

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
                        guestAvatar = runtimeAssets?.guestAvatar,
                        brandMinWidth = ref(148f),
                        brandArtworkWidth = ref(155f),
                        brandArtworkHeight = ref(80f),
                        brandArtworkOffsetX = ref(7f),
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
                        revealInitialResolvedCatch = revealFirstResolvedCatch,
                        modifier = Modifier.offset(y = refY(NormalHomeCardY)),
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = refY(NormalHomeCtaY))
                            .heightIn(min = captureCtaHeight)
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
                                shadow = Shadow(YuJianColors.DeepLakeBlue.copy(alpha = 0.24f), blurRadius = 3f),
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { result ->
                                val measuredHeight = with(density) { result.size.height.toDp() }
                                if (measuredHeight != captureCtaTextHeight.value) {
                                    captureCtaTextHeight.value = measuredHeight
                                }
                            },
                            modifier = Modifier
                                .offset(y = ref(NormalHomeCtaGlyphOffsetY))
                                .testTag("normal-home-capture-cta-text"),
                        )
                    }
                }

                val cameraSize = ref(NormalHomeCameraSize)
                HomeCameraButton(
                    onClick = onIdentify,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(x = ref(NormalHomeCameraX), y = cameraY)
                        .testTag("normal-home-camera"),
                    motionState = motionState,
                    runtimeAssets = runtimeAssets,
                    visualSize = cameraSize,
                    touchTargetSize = ref(NormalHomeCameraTouchSize),
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

internal fun normalHomeNeedsSafeOverflow(
    cameraTouchBottom: Float,
    windowHeight: Float,
    safeBottom: Float,
): Boolean = cameraTouchBottom > (windowHeight - safeBottom).coerceAtLeast(0f)

internal fun normalHomeScrollableContentHeight(
    windowHeight: Float,
    cameraTouchBottom: Float,
    safeBottom: Float,
): Float = maxOf(windowHeight, cameraTouchBottom + safeBottom)

@Composable
private fun NormalHomeHeader(
    isLoggedIn: Boolean,
    avatarUrl: String?,
    resolveImageUrl: (String?) -> String?,
    accessToken: String,
    onProfileClick: () -> Unit,
    avatarSize: Dp,
    guestAvatar: android.graphics.Bitmap?,
    brandMinWidth: Dp,
    brandArtworkWidth: Dp,
    brandArtworkHeight: Dp,
    brandArtworkOffsetX: Dp,
) {
    val resolvedAvatarUrl = resolveImageUrl(avatarUrl)
    val avatarLoadResult = remember(resolvedAvatarUrl) { mutableStateOf<Boolean?>(null) }
    val avatarState = normalHomeAvatarState(isLoggedIn, resolvedAvatarUrl, avatarLoadResult.value)
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .width(brandMinWidth)
                .height(brandArtworkHeight)
                .testTag("normal-home-brand-title"),
            contentAlignment = Alignment.CenterStart,
        ) {
            Image(
                painter = painterResource(R.drawable.brand_title_primary),
                contentDescription = "渔见",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .offset(x = brandArtworkOffsetX)
                    .requiredSize(width = brandArtworkWidth, height = brandArtworkHeight),
            )
        }
        if (isLoggedIn) {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .semantics {
                        contentDescription = "个人中心"
                        role = Role.Button
                    }
                    .clickable(onClick = onProfileClick),
            ) {
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
        } else {
            val guestModifier = Modifier
                .size(avatarSize)
                .clickable(role = Role.Button, onClick = onProfileClick)
                .testTag("normal-home-guest-avatar")
            if (guestAvatar != null) {
                Image(
                    bitmap = guestAvatar.asImageBitmap(),
                    contentDescription = "登录或注册",
                    contentScale = ContentScale.Fit,
                    modifier = guestModifier,
                )
            } else {
                // Keep the distinct Guest identity out of the signed-in fallback while its
                // independently registered runtime bitmap is being decoded.
                Box(
                    guestModifier
                        .semantics {
                            contentDescription = "登录或注册"
                            role = Role.Button
                        }
                        .testTag("normal-home-guest-avatar-loading"),
                )
            }
        }
    }
}

@Composable
private fun NormalHomeDefaultAvatar(modifier: Modifier) {
    Image(
        painter = painterResource(R.drawable.nh07_default_avatar_3x_192),
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
    revealInitialResolvedCatch: Boolean,
    modifier: Modifier = Modifier,
) {
    if (catches.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { catches.size })
    val density = LocalDensity.current
    val activeCardScale = remember { Animatable(1f) }
    val activeCardOffset = remember { Animatable(0f) }
    val initialRevealRequested = remember { revealInitialResolvedCatch }
    val initialRevealAlpha = remember { Animatable(if (initialRevealRequested) 0f else 1f) }
    val selectedCatchId = catches.getOrNull(pagerState.currentPage)?.id ?: catches.first().id

    LaunchedEffect(initialRevealRequested, motionEnabled) {
        if (initialRevealRequested && motionEnabled) {
            initialRevealAlpha.animateTo(1f, tween(durationMillis = 160, easing = LinearEasing))
        } else {
            initialRevealAlpha.snapTo(1f)
        }
    }

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
                        (-2f) at 3_000
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
                    alpha = (1f - distance * 0.12f) * initialRevealAlpha.value,
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
