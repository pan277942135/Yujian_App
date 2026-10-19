package com.yujian.ai.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.R
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.presentation.PresentationSanitizer
import com.yujian.ai.presentation.presentationSpeciesName
import com.yujian.ai.presentation.sanitizeOptionalText
import com.yujian.ai.ui.adaptive.SafeDrawingInsetsDp
import com.yujian.ai.ui.components.AssetImage
import com.yujian.ai.ui.components.RemoteImage
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.components.YuJianHeroCard
import com.yujian.ai.ui.designsystem.components.YuJianHeroDecoration
import com.yujian.ai.ui.designsystem.components.YuJianHeroVariant
import com.yujian.ai.ui.designsystem.typography.YuJianTypography
import java.util.Locale
import kotlin.math.abs

private const val GeminiHomeBackground = "normal_home_runtime_v1/static/scene_base.png"

/**
 * NH01 Gemini V2 page. The Compose tree follows Gemini's Top / Middle / Footer
 * containers and deliberately does not consume legacy NormalHome coordinates.
 */
@Composable
internal fun GeminiNormalHomeContent(
    statistics: CatchStatistics,
    recentCatches: List<RemoteCatch>,
    resolveImageUrl: (String?) -> String?,
    accessToken: String,
    isLoggedIn: Boolean,
    avatarUrl: String?,
    onIdentify: () -> Unit,
    onLoginClick: () -> Unit,
    onSpeciesClick: () -> Unit,
    onCatchesClick: () -> Unit,
    onProfileClick: () -> Unit,
    onCatchClick: (String) -> Unit,
    isResolving: Boolean = false,
    motionState: HomeMotionState,
    runtimeAssets: GeminiNormalHomeRuntimeAssets?,
    safeInsets: SafeDrawingInsetsDp = SafeDrawingInsetsDp(),
    modifier: Modifier = Modifier,
) {
    val records = remember(recentCatches) { orderedHomeRecords(recentCatches) }
    BoxWithConstraints(modifier = modifier.testTag("gemini-home-screen")) {
        val safeWidth = (maxWidth - safeInsets.start - safeInsets.end).coerceAtLeast(1.dp)
        val safeHeight = (maxHeight - safeInsets.top - safeInsets.bottom).coerceAtLeast(1.dp)
        val avatarSize = clampDp(safeHeight.value * 0.06f, 36f, 44f)
        val cameraVisualSize = clampDp(safeHeight.value * 0.095f, 54f, 66f)
        val cameraTouchSize = maxOf(72.dp, cameraVisualSize)

        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().testTag("gemini-layer-bg-scenic-group")) {
                Box(Modifier.fillMaxSize().testTag("gemini-layer-bg-mountain-mist")) {
                    runtimeAssets?.sceneBase?.let { scene ->
                        Image(
                            bitmap = scene.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().testTag("gemini-home-background"),
                            contentScale = ContentScale.Crop,
                        )
                    } ?: AssetImage(
                        GeminiHomeBackground,
                        Modifier.fillMaxSize().testTag("gemini-home-background"),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxSize()
                    .padding(start = safeInsets.start, top = safeInsets.top, end = safeInsets.end, bottom = safeInsets.bottom)
                    .testTag("gemini-home-content"),
            ) {
                GeminiTopSection(
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                        .align(Alignment.CenterHorizontally),
                    safeWidth = safeWidth,
                    safeHeight = safeHeight,
                    statistics = statistics,
                    catches = records,
                    isResolving = isResolving,
                    isLoggedIn = isLoggedIn,
                    avatarUrl = avatarUrl,
                    resolveImageUrl = resolveImageUrl,
                    accessToken = accessToken,
                    avatarSize = avatarSize,
                    onLoginClick = onLoginClick,
                    onProfileClick = onProfileClick,
                    onSpeciesClick = onSpeciesClick,
                    onCatchesClick = onCatchesClick,
                )
                GeminiMiddleSection(
                    modifier = Modifier.weight(1f).widthIn(max = 560.dp).fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                        .testTag("gemini-home-middle-section"),
                    safeWidth = safeWidth,
                    safeHeight = safeHeight,
                    records = records,
                    resolveImageUrl = resolveImageUrl,
                    accessToken = accessToken,
                    runtimeAssets = runtimeAssets,
                    onCatchesClick = onCatchesClick,
                    onCatchClick = onCatchClick,
                )
                GeminiFooterSection(
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                        .align(Alignment.CenterHorizontally),
                    safeHeight = safeHeight,
                    isResolving = isResolving,
                    motionState = motionState,
                    runtimeAssets = runtimeAssets,
                    cameraVisualSize = cameraVisualSize,
                    cameraTouchSize = cameraTouchSize,
                    onIdentify = onIdentify,
                )
            }
        }
    }
}

@Composable
private fun GeminiTopSection(
    modifier: Modifier,
    safeWidth: Dp,
    safeHeight: Dp,
    statistics: CatchStatistics,
    catches: List<RemoteCatch>,
    isResolving: Boolean,
    isLoggedIn: Boolean,
    avatarUrl: String?,
    resolveImageUrl: (String?) -> String?,
    accessToken: String,
    avatarSize: Dp,
    onLoginClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSpeciesClick: () -> Unit,
    onCatchesClick: () -> Unit,
) {
    val topInset = clampDp(safeHeight.value * 0.055f, 24f, 44f)
    val horizontalInset = clampDp(safeWidth.value * 0.075f, 24f, 40f)
    val headerGap = clampDp(safeHeight.value * 0.036f, 14f, 32f)
    val avatarTargetSize = maxOf(48.dp, avatarSize + 4.dp)
    val brandWidth = clampDp(safeWidth.value * (155f / 1080f), 46f, 70f)
    val brandHeight = brandWidth * (80f / 155f)

    Column(
        modifier = modifier.testTag("gemini-home-top-section")
            .padding(horizontal = horizontalInset)
            .padding(top = topInset),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = avatarTargetSize)
                .testTag("gemini-home-header-bar"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Image(
                painter = painterResource(R.drawable.brand_title_primary),
                contentDescription = "渔见",
                modifier = Modifier.width(brandWidth).height(brandHeight)
                    .testTag("gemini-home-brand-title"),
                contentScale = ContentScale.Fit,
            )
            GeminiAvatarButton(
                isLoggedIn = isLoggedIn,
                avatarUrl = avatarUrl,
                resolveImageUrl = resolveImageUrl,
                accessToken = accessToken,
                avatarSize = avatarSize,
                touchTargetSize = avatarTargetSize,
                onLoginClick = onLoginClick,
                onProfileClick = onProfileClick,
            )
        }
        Spacer(Modifier.height(headerGap))
        GeminiStatsBar(
            statistics = statistics,
            catches = catches,
            isResolving = isResolving,
            valueFontSize = responsiveSp(safeHeight, 0.03f, 17f, 21f),
            labelFontSize = responsiveSp(safeHeight, 0.019f, 11f, 13f),
            dividerHeight = clampDp(safeHeight.value * 0.08f, 40f, 56f),
            onSpeciesClick = onSpeciesClick,
            onCatchesClick = onCatchesClick,
        )
    }
}

@Composable
private fun GeminiAvatarButton(
    isLoggedIn: Boolean,
    avatarUrl: String?,
    resolveImageUrl: (String?) -> String?,
    accessToken: String,
    avatarSize: Dp,
    touchTargetSize: Dp,
    onLoginClick: () -> Unit,
    onProfileClick: () -> Unit,
) {
    val resolvedUrl = if (isLoggedIn) resolveImageUrl(avatarUrl)?.takeIf(String::isNotBlank) else null
    val loadResult = remember(isLoggedIn, resolvedUrl, accessToken) { mutableStateOf<Boolean?>(null) }
    val avatarState = normalHomeAvatarState(isLoggedIn, resolvedUrl, loadResult.value)
    val description = if (isLoggedIn) "个人中心" else "登录或注册"
    val click = if (isLoggedIn) onProfileClick else onLoginClick
    val stateLabel = when (avatarState) {
        NormalHomeAvatarState.GUEST_DEFAULT -> "访客默认头像"
        NormalHomeAvatarState.PROFILE_DEFAULT -> "默认头像"
        NormalHomeAvatarState.PROFILE_LOADING -> "头像加载中"
        NormalHomeAvatarState.PROFILE_IMAGE -> "用户头像"
    }

    Box(
        modifier = Modifier.size(touchTargetSize)
            .semantics {
                contentDescription = description
                role = Role.Button
                stateDescription = stateLabel
            }
            .clickable(role = Role.Button, onClick = click)
            .testTag("gemini-home-avatar-button"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(avatarSize).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.96f))
                .border(2.dp, Color.White.copy(alpha = 0.98f), CircleShape)
                .testTag("gemini-home-avatar-visual"),
        ) {
            Image(
                painter = painterResource(R.drawable.normal_home_default_avatar_v2),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
                    .testTag("gemini-home-default-avatar-v2"),
                contentScale = ContentScale.Fit,
            )
            if (avatarState == NormalHomeAvatarState.PROFILE_LOADING ||
                avatarState == NormalHomeAvatarState.PROFILE_IMAGE
            ) {
                key(isLoggedIn, resolvedUrl, accessToken) {
                    RemoteImage(
                        url = resolvedUrl,
                        authToken = accessToken,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                            .testTag("gemini-home-remote-avatar"),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onLoadResult = { loadResult.value = it },
                        placeholder = { Box(Modifier.fillMaxSize()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GeminiStatsBar(
    statistics: CatchStatistics,
    catches: List<RemoteCatch>,
    isResolving: Boolean,
    valueFontSize: androidx.compose.ui.unit.TextUnit,
    labelFontSize: androidx.compose.ui.unit.TextUnit,
    dividerHeight: Dp,
    onSpeciesClick: () -> Unit,
    onCatchesClick: () -> Unit,
) {
    val values = remember(statistics, catches) { resolveHomeStatValues(statistics, catches) }
    Row(
        modifier = Modifier.fillMaxWidth().testTag("gemini-home-stats-bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        GeminiStatItem(
            tag = "species", value = if (isResolving) "—" else values.speciesCount.toString(),
            label = "鱼种", valueFontSize = valueFontSize, labelFontSize = labelFontSize,
            onClick = onSpeciesClick.takeUnless { isResolving },
        )
        Box(Modifier.width(1.dp).height(dividerHeight).background(YuJianColors.TextPrimary.copy(alpha = 0.20f)))
        GeminiStatItem(
            tag = "catches", value = if (isResolving) "—" else values.catchCount.toString(),
            label = "鱼获", valueFontSize = valueFontSize, labelFontSize = labelFontSize,
            onClick = onCatchesClick.takeUnless { isResolving },
        )
        Box(Modifier.width(1.dp).height(dividerHeight).background(YuJianColors.TextPrimary.copy(alpha = 0.20f)))
        GeminiStatItem(
            tag = "record-days", value = if (isResolving) "—" else values.recordDays.toString(),
            label = "记录天数", valueFontSize = valueFontSize, labelFontSize = labelFontSize,
            onClick = null,
        )
    }
}

@Composable
private fun RowScope.GeminiStatItem(
    tag: String,
    value: String,
    label: String,
    valueFontSize: androidx.compose.ui.unit.TextUnit,
    labelFontSize: androidx.compose.ui.unit.TextUnit,
    onClick: (() -> Unit)?,
) {
    val action = if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick)
    Column(
        modifier = Modifier.weight(1f).requiredSizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .then(action)
            .semantics { if (onClick != null) role = Role.Button }
            .testTag("gemini-home-stat-$tag"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = YuJianTypography.dataNumber.copy(
                color = YuJianColors.TextPrimary,
                fontSize = valueFontSize,
                lineHeight = (valueFontSize.value * 1.14f).sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("gemini-home-stat-$tag-value"),
        )
        Text(
            text = label,
            style = YuJianTypography.caption.copy(
                color = YuJianColors.TextPrimary.copy(alpha = 0.78f),
                fontWeight = FontWeight.Normal,
                fontSize = labelFontSize,
                lineHeight = (labelFontSize.value * 1.2f).sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp).testTag("gemini-home-stat-$tag-label"),
        )
    }
}

@Composable
private fun GeminiMiddleSection(
    modifier: Modifier,
    safeWidth: Dp,
    safeHeight: Dp,
    records: List<RemoteCatch>,
    resolveImageUrl: (String?) -> String?,
    accessToken: String,
    runtimeAssets: GeminiNormalHomeRuntimeAssets?,
    onCatchesClick: () -> Unit,
    onCatchClick: (String) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    val verticalMargin = clampDp(safeHeight.value * 0.015f, 6f, 16f)
    val headerGap = clampDp(safeHeight.value * 0.018f, 8f, 14f)
    val headerHeight = maxOf(48.dp, responsiveSp(safeHeight, 0.027f, 16f, 19f).value.dp * 1.25f)
    val minCardTrackHeight = maxOf(180.dp, 180.dp * fontScale.coerceIn(1f, 1.5f))

    BoxWithConstraints(modifier = modifier) {
        val contentHeight = (maxHeight - verticalMargin * 2 - headerHeight - headerGap).coerceAtLeast(0.dp)
        val needsScroll = maxHeight < verticalMargin * 2 + headerHeight + headerGap + minCardTrackHeight
        val scrollState = rememberScrollState()
        val contentModifier = if (needsScroll) {
            Modifier.fillMaxWidth().heightIn(min = maxHeight).verticalScroll(scrollState)
                .testTag("gemini-home-short-screen-scroll")
        } else {
            Modifier.fillMaxSize().testTag("gemini-home-middle-layout")
        }
        val trackHeight = if (needsScroll) maxOf(contentHeight, minCardTrackHeight) else contentHeight

        Column(modifier = contentModifier) {
            Spacer(Modifier.height(verticalMargin))
            GeminiSectionHeader(
                modifier = Modifier.fillMaxWidth().height(headerHeight),
                safeWidth = safeWidth,
                safeHeight = safeHeight,
                onCatchesClick = onCatchesClick,
            )
            Spacer(Modifier.height(headerGap))
            if (records.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(trackHeight).testTag("gemini-home-empty-carousel"),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(width = minOf(safeWidth * 0.686f, 360.dp), height = minOf(trackHeight, 480.dp))
                            .clip(RoundedCornerShape(22.dp))
                            .background(YuJianColors.MistWhite.copy(alpha = 0.26f)),
                    )
                }
            } else {
                GeminiCatchCarousel(
                    modifier = Modifier.fillMaxWidth().height(trackHeight).testTag("gemini-home-carousel-track"),
                    records = records,
                    resolveImageUrl = resolveImageUrl,
                    accessToken = accessToken,
                    safeWidth = safeWidth,
                    safeHeight = safeHeight,
                    trackHeight = trackHeight,
                    runtimeAssets = runtimeAssets,
                    onCatchClick = onCatchClick,
                )
            }
            Spacer(Modifier.height(verticalMargin))
        }
    }
}

@Composable
private fun GeminiSectionHeader(
    modifier: Modifier,
    safeWidth: Dp,
    safeHeight: Dp,
    onCatchesClick: () -> Unit,
) {
    val horizontalInset = clampDp(safeWidth.value * 0.088f, 24f, 42f)
    val titleFontSize = responsiveSp(safeHeight, 0.027f, 16f, 19f)
    val actionFontSize = responsiveSp(safeHeight, 0.021f, 13f, 15f)
    Row(
        modifier = modifier.padding(horizontal = horizontalInset).testTag("gemini-home-section-header"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "最近鱼获",
            style = YuJianTypography.sectionTitle.copy(
                color = YuJianColors.OnDark,
                fontSize = titleFontSize,
                lineHeight = (titleFontSize.value * 1.2f).sp,
                shadow = Shadow(YuJianColors.DeepLakeBlue.copy(alpha = 0.45f), blurRadius = 3f),
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("gemini-home-section-title"),
        )
        Row(
            modifier = Modifier.requiredSizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "全部鱼获"
                    role = Role.Button
                }
                .clickable(role = Role.Button, onClick = onCatchesClick)
                .testTag("gemini-home-section-all"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "全部",
                style = YuJianTypography.body.copy(
                    color = YuJianColors.OnDark.copy(alpha = 0.95f),
                    fontSize = actionFontSize,
                    lineHeight = (actionFontSize.value * 1.2f).sp,
                    shadow = Shadow(YuJianColors.DeepLakeBlue.copy(alpha = 0.45f), blurRadius = 3f),
                ),
                maxLines = 1,
            )
            Text(
                text = "›",
                style = YuJianTypography.body.copy(
                    color = YuJianColors.OnDark.copy(alpha = 0.95f),
                    fontSize = actionFontSize,
                    lineHeight = (actionFontSize.value * 1.2f).sp,
                ),
                maxLines = 1,
                modifier = Modifier.testTag("gemini-home-section-chevron"),
            )
        }
    }
}

@Composable
private fun GeminiCatchCarousel(
    modifier: Modifier,
    records: List<RemoteCatch>,
    resolveImageUrl: (String?) -> String?,
    accessToken: String,
    safeWidth: Dp,
    safeHeight: Dp,
    trackHeight: Dp,
    runtimeAssets: GeminiNormalHomeRuntimeAssets?,
    onCatchClick: (String) -> Unit,
) {
    val hasNeighborPages = records.size > 1
    val pageCount = if (hasNeighborPages) records.size + 2 else records.size
    val initialPage = if (hasNeighborPages) 1 else 0
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pageCount })
    val trackWidth = safeWidth.coerceAtMost(560.dp)
    val cardWidth = minOf(trackWidth * 0.686f, 360.dp)
    val cardHeight = minOf(trackHeight, 480.dp)
    val neighborWidth = minOf(trackWidth * 0.62f, 260.dp)
    val neighborHeight = minOf(cardHeight * 0.67f, 340.dp)
    val sideScaleX = (neighborWidth / cardWidth).coerceIn(0.5f, 1f)
    val sideScaleY = if (cardHeight > 0.dp) (neighborHeight / cardHeight).coerceIn(0.5f, 1f) else 1f
    val pageSpacing = clampDp(trackWidth.value * 0.032f, 10f, 16f)
    val contentInset = ((trackWidth - cardWidth) / 2).coerceAtLeast(0.dp)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(cardHeight).testTag("gemini-home-catch-pager"),
            pageSize = PageSize.Fixed(cardWidth),
            contentPadding = PaddingValues(horizontal = contentInset),
            pageSpacing = pageSpacing,
            beyondViewportPageCount = if (hasNeighborPages) 1 else 0,
            userScrollEnabled = hasNeighborPages,
            key = { page -> "gemini-page-$page-${recordAtVirtualPage(records, page, pageCount).id}" },
        ) { page ->
            val record = recordAtVirtualPage(records, page, pageCount)
            val distance = abs((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
            val active = page == pagerState.currentPage
            val pageTag = when {
                !hasNeighborPages -> "gemini-home-card-main-active"
                page < pagerState.currentPage -> "gemini-home-card-peek-left"
                page > pagerState.currentPage -> "gemini-home-card-peek-right"
                else -> "gemini-home-card-main-active"
            }
            Box(
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = 1f - distance * (1f - sideScaleX)
                    scaleY = 1f - distance * (1f - sideScaleY)
                    alpha = 1f - distance * 0.08f
                }.testTag(pageTag),
                contentAlignment = Alignment.Center,
            ) {
                GeminiCatchCard(
                    item = record,
                    imageUrl = resolveImageUrl(record.imageUrl),
                    accessToken = accessToken,
                    cardHeight = cardHeight,
                    safeHeight = safeHeight,
                    runtimeAssets = runtimeAssets,
                    isActive = active,
                    onClick = { onCatchClick(record.id) },
                )
            }
        }
    }
}

private fun recordAtVirtualPage(records: List<RemoteCatch>, page: Int, pageCount: Int): RemoteCatch {
    if (records.size <= 1) return records.first()
    return when (page) {
        0 -> records.last()
        pageCount - 1 -> records.first()
        else -> records[(page - 1).coerceIn(0, records.lastIndex)]
    }
}

@Composable
private fun GeminiCatchCard(
    item: RemoteCatch,
    imageUrl: String?,
    accessToken: String,
    cardHeight: Dp,
    safeHeight: Dp,
    runtimeAssets: GeminiNormalHomeRuntimeAssets?,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val textGap = clampDp(safeHeight.value * 0.006f, 2f, 5f)
    val speciesSize = responsiveSp(safeHeight, 0.036f, 20f, 26f)
    val measurementSize = responsiveSp(safeHeight, 0.023f, 13f, 16f)
    val metadataSize = responsiveSp(safeHeight, 0.019f, 11f, 13f)
    val species = presentationSpeciesName(item.speciesName)
    val measurements = buildList {
        item.lengthCm?.takeIf { it > 0f }?.let { add("${formatGeminiNumber(it)} cm") }
        item.weightKg?.takeIf { it > 0f }?.let { add("${formatGeminiNumber(it)} kg") }
    }.joinToString(" · ")
    val time = PresentationSanitizer.formatHomeTimestamp(item.capturedAt, item.createdAt)
    val location = sanitizeOptionalText(item.location)
    val metadata = listOfNotNull(time?.takeIf { it.isNotBlank() }, location).joinToString(" · ")

    YuJianHeroCard(
        title = species,
        metadata = emptyList(),
        variant = YuJianHeroVariant.HOME,
        modifier = Modifier.fillMaxSize(),
        heightOverride = cardHeight,
        cornerRadius = 22.dp,
        footerPaddingHorizontal = 20.dp,
        footerPaddingVertical = 20.dp,
        onClick = onClick,
        semanticsTag = if (isActive) "gemini-home-catch-card-${item.id}" else "gemini-home-catch-neighbor-${item.id}",
        footerTag = "gemini-home-catch-footer-${item.id}",
        decoration = YuJianHeroDecoration(
            shadow = runtimeAssets?.fishCardShadow?.asImageBitmap(),
            gradient = runtimeAssets?.fishCardGradient?.asImageBitmap(),
            outline = runtimeAssets?.fishCardOutline?.asImageBitmap(),
        ),
        footerContent = {
            if (isActive) {
                Text(
                    text = species,
                    style = YuJianTypography.sectionTitle.copy(
                        color = YuJianColors.OnDark,
                        fontSize = speciesSize,
                        lineHeight = (speciesSize.value * 1.2f).sp,
                        fontWeight = FontWeight.Bold,
                        shadow = Shadow(YuJianColors.DeepLakeBlue.copy(alpha = 0.38f), blurRadius = 2f),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("gemini-home-catch-species-${item.id}"),
                )
                if (measurements.isNotBlank()) {
                    Text(
                        text = measurements,
                        style = YuJianTypography.body.copy(
                            color = YuJianColors.OnDark.copy(alpha = 0.95f),
                            fontSize = measurementSize,
                            lineHeight = (measurementSize.value * 1.2f).sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = textGap)
                            .testTag("gemini-home-catch-measurement-${item.id}"),
                    )
                }
                if (metadata.isNotBlank()) {
                    Text(
                        text = metadata,
                        style = YuJianTypography.caption.copy(
                            color = YuJianColors.OnDark.copy(alpha = 0.86f),
                            fontSize = metadataSize,
                            lineHeight = (metadataSize.value * 1.3f).sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = textGap)
                            .testTag("gemini-home-catch-metadata-${item.id}"),
                    )
                }
            }
        },
        media = {
            Box(Modifier.fillMaxSize()) {
                RemoteImage(
                    url = imageUrl,
                    authToken = accessToken,
                    modifier = Modifier.fillMaxSize().testTag("gemini-home-catch-media-${item.id}"),
                    contentDescription = "${presentationSpeciesName(item.speciesName)} 鱼获照片",
                    preserveEvidenceWithFitBackdrop = true,
                    placeholder = {
                        Box(
                            Modifier.fillMaxSize()
                                .background(Color(0xFF294858).copy(alpha = 0.50f))
                                .testTag("gemini-home-catch-image-placeholder"),
                        )
                    },
                )
                if (!isActive) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF0F172A).copy(alpha = 0.15f)))
                }
            }
        },
    )
}

@Composable
private fun GeminiFooterSection(
    modifier: Modifier,
    safeHeight: Dp,
    isResolving: Boolean,
    motionState: HomeMotionState,
    runtimeAssets: GeminiNormalHomeRuntimeAssets?,
    cameraVisualSize: Dp,
    cameraTouchSize: Dp,
    onIdentify: () -> Unit,
) {
    val verticalPadding = clampDp(safeHeight.value * 0.042f, 18f, 36f)
    val gap = clampDp(safeHeight.value * 0.016f, 8f, 14f)
    val labelSize = responsiveSp(safeHeight, 0.021f, 12f, 14f)
    val horizontalInset = clampDp(safeHeight.value * 0.01f, 8f, 12f)
    Column(
        modifier = modifier.testTag("gemini-home-footer-cta")
            .padding(horizontal = horizontalInset)
            .padding(top = 4.dp, bottom = verticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        if (!isResolving) {
            Text(
                text = "记录下一条鱼",
                style = YuJianTypography.body.copy(
                    color = YuJianColors.OnDark.copy(alpha = 0.96f),
                    fontSize = labelSize,
                    lineHeight = (labelSize.value * 1.25f).sp,
                    shadow = Shadow(YuJianColors.DeepLakeBlue.copy(alpha = 0.40f), blurRadius = 3f),
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("gemini-home-cta-label"),
            )
        }
        if (runtimeAssets != null) {
            HomeCameraButton(
                onClick = onIdentify,
                modifier = Modifier.testTag("gemini-home-camera"),
                motionState = motionState,
                runtimeAssets = runtimeAssets,
                visualSize = cameraVisualSize,
                touchTargetSize = cameraTouchSize,
            )
        } else {
            // Show the approved camera art while runtime motion masks decode.
            // No vector or hand-drawn fallback is used.
            Box(
                modifier = Modifier.size(cameraTouchSize)
                    .semantics {
                        contentDescription = "开始识鱼"
                        role = Role.Button
                    }
                    .clickable(role = Role.Button, onClick = onIdentify)
                    .testTag("gemini-home-camera"),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.normal_home_camera_primary_v1),
                    contentDescription = null,
                    modifier = Modifier.size(cameraVisualSize).testTag("gemini-home-camera-art"),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

private fun responsiveSp(height: Dp, ratio: Float, minimum: Float, maximum: Float) =
    (height.value * ratio).coerceIn(minimum, maximum).sp

private fun clampDp(value: Float, minimum: Float, maximum: Float) =
    value.coerceIn(minimum, maximum).dp

private fun formatGeminiNumber(value: Float): String =
    String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
