package com.yujian.ai.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.yujian.ai.R
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.components.AssetImage
import com.yujian.ai.ui.components.RemoteImage
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.spacing.YuJianSpacing
import com.yujian.ai.ui.designsystem.typography.YuJianTypography
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import com.yujian.ai.presentation.PresentationSanitizer
import kotlin.math.abs

private const val GUEST_AVATAR = "home_normal_v1_2/assets/avatar/guest_avatar.png"
private const val RecentCardAspectRatio = 0.84f
private const val RecentCardWidthFraction = 0.69f

/**
 * Real-data Home state.
 *
 * The lake scene and capture action remain owned by HomeScreen and the Empty
 * Home runtime. This content layer only maps saved RemoteCatch records into
 * the Normal Home hierarchy: identity, secondary statistics, and the recent
 * catch focus pager.
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
    onRecordDaysClick: () -> Unit,
    onProfileClick: () -> Unit,
    onCatchClick: (String) -> Unit,
    motionState: HomeMotionState,
    modifier: Modifier = Modifier,
) {
    val recent = remember(recentCatches) {
        recentCatches.sortedByDescending { catchTimestamp(it) ?: Long.MIN_VALUE }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        // Keep the focused card close to the frozen 1080×1920 composition on
        // tall phones while avoiding vertical clipping on compact displays.
        val cardWidth = minOf(
            maxWidth * RecentCardWidthFraction,
            maxHeight * 0.47f * RecentCardAspectRatio,
        )
        val cardHeight = cardWidth / RecentCardAspectRatio
        val pagerSidePadding = (maxWidth - cardWidth) / 2

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 430.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NormalHomeHeader(
                isLoggedIn = isLoggedIn,
                avatarUrl = avatarUrl,
                resolveImageUrl = resolveImageUrl,
                accessToken = accessToken,
                onProfileClick = onProfileClick,
            )

            Spacer(Modifier.height(YuJianSpacing.lg))

            HomeStats(
                statistics = statistics,
                catches = recent,
                onSpeciesClick = onSpeciesClick,
                onCatchesClick = onCatchesClick,
                onRecordDaysClick = onRecordDaysClick,
            )

            Spacer(Modifier.height(YuJianSpacing.lg))

            RecentCatchSectionHeader(onCatchesClick = onCatchesClick)

            Spacer(Modifier.height(YuJianSpacing.xs))

            RecentCatchPager(
                catches = recent,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                sidePadding = pagerSidePadding,
                resolveImageUrl = resolveImageUrl,
                accessToken = accessToken,
                onCatchClick = onCatchClick,
                motionEnabled = motionState.running,
            )

            Spacer(Modifier.height(YuJianSpacing.md))

            Text(
                text = "记录下一条鱼",
                style = YuJianTypography.body.copy(
                    color = YuJianColors.OnDark.copy(alpha = 0.92f),
                    shadow = Shadow(YuJianColors.DeepLakeBlue.copy(alpha = 0.24f), blurRadius = 3f),
                ),
            )

            Spacer(Modifier.height(YuJianSpacing.xs))

            HomeCameraButton(
                onClick = onIdentify,
                motionState = motionState,
            )
        }
    }
}

@Composable
private fun NormalHomeHeader(
    isLoggedIn: Boolean,
    avatarUrl: String?,
    resolveImageUrl: (String?) -> String?,
    accessToken: String,
    onProfileClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = YuJianSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "渔见",
            style = YuJianTypography.brand.copy(
                color = YuJianColors.TextPrimary.copy(alpha = 0.94f),
            ),
        )
        if (isLoggedIn) {
            RemoteImage(
                url = resolveImageUrl(avatarUrl),
                authToken = accessToken,
                modifier = Modifier
                    .size(YuJianSpacing.xl)
                    .clip(CircleShape)
                    .clickable(onClick = onProfileClick),
                contentDescription = "个人中心",
                contentScale = ContentScale.Crop,
            ) {
                Image(
                    painter = painterResource(R.drawable.profile_fallback_v13),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            AssetImage(
                GUEST_AVATAR,
                modifier = Modifier
                    .size(YuJianSpacing.xl)
                    .clip(CircleShape)
                    .clickable(onClick = onProfileClick),
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
            style = YuJianTypography.sectionTitle.copy(color = YuJianColors.OnDark),
        )
        Row(
            modifier = Modifier
                .clickable(onClick = onCatchesClick)
                .padding(vertical = YuJianSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YuJianSpacing.xs),
        ) {
            Text(
                text = "全部",
                style = YuJianTypography.body.copy(color = YuJianColors.OnDark),
            )
            Image(
                painter = painterResource(R.drawable.all_chevron_v12),
                contentDescription = "全部鱼获",
                modifier = Modifier.size(YuJianSpacing.sm),
            )
        }
    }
}

@Composable
private fun RecentCatchPager(
    catches: List<RemoteCatch>,
    cardWidth: androidx.compose.ui.unit.Dp,
    cardHeight: androidx.compose.ui.unit.Dp,
    sidePadding: androidx.compose.ui.unit.Dp,
    resolveImageUrl: (String?) -> String?,
    accessToken: String,
    onCatchClick: (String) -> Unit,
    motionEnabled: Boolean,
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
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight),
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

internal fun catchTimestamp(item: RemoteCatch): Long? =
    PresentationSanitizer.resolveTimestamp(item.capturedAt, item.createdAt).millis
