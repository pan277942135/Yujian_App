package com.yujian.ai.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.R
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.components.AssetImage
import com.yujian.ai.ui.components.RemoteImage
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.radius.YuJianRadius
import com.yujian.ai.ui.designsystem.spacing.YuJianSpacing
import com.yujian.ai.ui.designsystem.typography.YuJianTypography
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

private const val GUEST_AVATAR = "normal_home_runtime_v1/avatar/guest_avatar.png"
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
private const val NormalHomeCtaY = 1504f
private const val NormalHomeCameraY = 1582f
private const val NormalHomeCameraSize = 200f
private const val NormalHomeCameraTouchSize = 208f

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
    modifier: Modifier = Modifier,
) {
    val recent = remember(recentCatches) {
        orderedHomeRecords(recentCatches)
    }

    BoxWithConstraints(modifier = modifier) {
        val referenceScale = maxWidth / NormalHomeReferenceWidth
        fun ref(value: Float): Dp = referenceScale * value
        val verticalOffset = normalHomeVerticalOffset(referenceScale.value, maxHeight.value).dp
        fun refY(value: Float): Dp = ref(value) + verticalOffset

        val cardWidth = ref(NormalHomeCardWidth)
        val cardHeight = ref(NormalHomeCardHeight)
        val pagerSidePadding = (maxWidth - cardWidth) / 2

        Box(
            modifier = Modifier
                .offset(x = ref(NormalHomeHeaderX), y = refY(NormalHomeHeaderY))
                .width(ref(NormalHomeHeaderWidth))
                .height(ref(NormalHomeHeaderHeight)),
        ) {
            NormalHomeHeader(
                isLoggedIn = isLoggedIn,
                avatarUrl = avatarUrl,
                resolveImageUrl = resolveImageUrl,
                accessToken = accessToken,
                onProfileClick = onProfileClick,
                runtimeAssets = runtimeAssets,
                avatarSize = ref(92f),
            )
        }

        Box(
            modifier = Modifier
                .offset(y = refY(NormalHomeStatsY))
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            HomeStats(
                statistics = statistics,
                catches = recent,
                onSpeciesClick = onSpeciesClick,
                onCatchesClick = onCatchesClick,
                isResolving = isResolving,
                dividerHeight = ref(56f),
                horizontalPadding = ref(150f),
            )
        }

        if (isResolving) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = refY(NormalHomeCardY))
                    .width(cardWidth)
                    .height(cardHeight)
                    .clip(YuJianRadius.heroCard)
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
                RecentCatchSectionHeader(onCatchesClick = onCatchesClick)
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
                modifier = Modifier.offset(y = refY(NormalHomeCardY)),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = refY(NormalHomeCtaY))
                    .height(ref(58f))
                    .width(ref(420f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "记录下一条鱼",
                    style = YuJianTypography.body.copy(
                        color = YuJianColors.OnDark.copy(alpha = 0.92f),
                        fontSize = 20.sp,
                        lineHeight = 28.sp,
                        shadow = Shadow(YuJianColors.DeepLakeBlue.copy(alpha = 0.24f), blurRadius = 3f),
                    ),
                )
            }
        }

        val cameraSize = ref(NormalHomeCameraSize)
        HomeCameraButton(
            onClick = onIdentify,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = refY(NormalHomeCameraY)),
            motionState = motionState,
            runtimeAssets = runtimeAssets,
            visualSize = cameraSize,
            touchTargetSize = ref(NormalHomeCameraTouchSize),
        )
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
    runtimeAssets: NormalHomeRuntimeAssets?,
    avatarSize: Dp,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "渔见",
            style = YuJianTypography.brand.copy(
                color = YuJianColors.TextPrimary.copy(alpha = 0.94f),
                fontSize = 32.sp,
                lineHeight = 40.sp,
            ),
        )
        if (isLoggedIn) {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .semantics {
                        contentDescription = "个人中心"
                        role = Role.Button
                    }
                    .clickable(onClick = onProfileClick),
            ) {
                RemoteImage(
                    url = resolveImageUrl(avatarUrl),
                    authToken = accessToken,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = {
                        Image(
                            painter = painterResource(R.drawable.profile_fallback_v13),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().testTag("normal-home-default-profile-avatar"),
                        )
                    },
                )
            }
        } else {
            runtimeAssets?.guestAvatar?.let { avatar ->
                Image(
                    bitmap = avatar.asImageBitmap(),
                    modifier = Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onProfileClick),
                    contentDescription = "登录或注册",
                    contentScale = ContentScale.Crop,
                )
            } ?: AssetImage(
                GUEST_AVATAR,
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onProfileClick),
                contentDescription = "登录或注册",
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun RecentCatchSectionHeader(onCatchesClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = YuJianSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "最近鱼获",
            style = YuJianTypography.sectionTitle.copy(
                color = YuJianColors.OnDark,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                shadow = Shadow(YuJianColors.DeepLakeBlue.copy(alpha = 0.30f), blurRadius = 3f),
            ),
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
            horizontalArrangement = Arrangement.spacedBy(YuJianSpacing.xs),
        ) {
            Text(
                text = "全部",
                style = YuJianTypography.body.copy(
                    color = YuJianColors.OnDark,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    shadow = Shadow(YuJianColors.DeepLakeBlue.copy(alpha = 0.30f), blurRadius = 3f),
                ),
            )
            Image(
                painter = painterResource(R.drawable.all_chevron_v12),
                contentDescription = null,
                modifier = Modifier.size(YuJianSpacing.sm),
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
                        -2f at 3_000
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
        pageSpacing = YuJianSpacing.sm,
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
                    translationY = if (selected) activeCardOffset.value else 4f * distance,
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
