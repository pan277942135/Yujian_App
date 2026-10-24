package com.yujian.ai.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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
private const val GeminiHomeCardAspectRatio = 247f / 293f
private const val GeminiHomeNeighborHeightFraction = 0.667f
private val GeminiHomePeekTopOffset = 15.dp
// NH01_AVATAR_VISUAL_V1_1: frozen mist glass and lake-ink profile illustration.
private val GeminiMistAvatarFill = Color(0xFFEEF3F2).copy(alpha = 0.36f)
private val GeminiMistAvatarBorder = Color.White.copy(alpha = 0.48f)
private val GeminiMistAvatarInk = Color(0xFF214454)

/** Test-visible layout signal; the UI does not announce this as accessibility copy. */
internal val GeminiHomeTextHasVisualOverflow = SemanticsPropertyKey<Boolean>("GeminiHomeTextHasVisualOverflow")
internal val GeminiHomeRenderedFontSizeSp = SemanticsPropertyKey<Float>("GeminiHomeRenderedFontSizeSp")
internal val GeminiHomeTextLineCount = SemanticsPropertyKey<Int>("GeminiHomeTextLineCount")
internal val GeminiHomeTextLastLineCharacterCount = SemanticsPropertyKey<Int>("GeminiHomeTextLastLineCharacterCount")

/**
 * NH01 Gemini V3 page. The Compose tree follows Gemini's Top / Middle / Footer
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
    layoutVariant: GeminiHomeLayoutVariant = GeminiHomeLayoutVariant.ADAPTIVE_V2,
    modifier: Modifier = Modifier,
) {
    val records = remember(recentCatches) { orderedHomeRecords(recentCatches) }
    BoxWithConstraints(modifier = modifier.testTag("gemini-home-screen")) {
        val safeWidth = (maxWidth - safeInsets.start - safeInsets.end).coerceAtLeast(1.dp)
        val safeHeight = (maxHeight - safeInsets.top - safeInsets.bottom).coerceAtLeast(1.dp)
        val fontScale = LocalDensity.current.fontScale
        val layoutSpec = remember(safeWidth, safeHeight, fontScale, layoutVariant) {
            resolveGeminiHomeLayoutSpec(
                safeWidthDp = safeWidth.value,
                safeHeightDp = safeHeight.value,
                fontScale = fontScale,
                variant = layoutVariant,
            )
        }
        val avatarSize = geminiHomeAvatarVisualSize(safeWidth.value).dp
        val cameraVisualSize = geminiHomeCameraVisualSize(safeWidth.value, safeHeight.value).dp
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
                            alignment = Alignment.TopCenter,
                        )
                    } ?: AssetImage(
                        GeminiHomeBackground,
                        Modifier.fillMaxSize().testTag("gemini-home-background"),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
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
                    brandToStatsGap = if (safeHeight.value < 460f) {
                        4.dp
                    } else {
                        layoutSpec.brandToStatsGap ?: clampDp(safeHeight.value * 0.042f, 18f, 34f)
                    },
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
                    layoutSpec = layoutSpec,
                    layoutVariant = layoutVariant,
                    onCatchesClick = onCatchesClick,
                    onCatchClick = onCatchClick,
                )
                GeminiFooterSection(
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                        .align(Alignment.CenterHorizontally),
                    safeWidth = safeWidth,
                    safeHeight = safeHeight,
                    safeBottomInset = safeInsets.bottom,
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
    brandToStatsGap: Dp,
    onLoginClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSpeciesClick: () -> Unit,
    onCatchesClick: () -> Unit,
) {
    // Keep a stable status-area buffer; the policy bounds the header-to-stats
    // gap. System safe insets are already applied by the parent.
    val topInset = if (safeHeight.value < 460f) 2.dp else 8.dp
    val horizontalInset = clampDp(safeWidth.value * 0.075f, 24f, 40f)
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
        Spacer(Modifier.height(brandToStatsGap))
        GeminiStatsBar(
            statistics = statistics,
            catches = catches,
            isResolving = isResolving,
            valueFontSize = responsiveSp(safeHeight, safeWidth, 0.03f, 17f, 21f),
            labelFontSize = responsiveSp(safeHeight, safeWidth, 0.019f, 11f, 13f),
            dividerHeight = 26.dp,
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
                .background(GeminiMistAvatarFill)
                .testTag("gemini-home-avatar-visual"),
        ) {
            // NH01-only fallback; the registered legacy V2 bitmap is left untouched.
            Canvas(Modifier.fillMaxSize().testTag("gemini-home-default-avatar-mist")) {
                val inkStroke = Stroke(
                    width = 1.65.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                )
                drawCircle(
                    color = GeminiMistAvatarInk,
                    radius = size.minDimension * 0.12f,
                    center = Offset(size.width * 0.50f, size.height * 0.36f),
                    style = inkStroke,
                )
                val shoulders = Path().apply {
                    moveTo(size.width * 0.26f, size.height * 0.77f)
                    cubicTo(
                        size.width * 0.26f, size.height * 0.62f,
                        size.width * 0.34f, size.height * 0.57f,
                        size.width * 0.50f, size.height * 0.57f,
                    )
                    cubicTo(
                        size.width * 0.66f, size.height * 0.57f,
                        size.width * 0.74f, size.height * 0.62f,
                        size.width * 0.74f, size.height * 0.77f,
                    )
                }
                drawPath(path = shoulders, color = GeminiMistAvatarInk, style = inkStroke)
            }
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
            // Draw last to keep the quiet outline visible over real user photos.
            Box(
                Modifier.fillMaxSize()
                    .border(0.8.dp, GeminiMistAvatarBorder, CircleShape)
                    .testTag("gemini-home-avatar-outline"),
            )
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
        Box(
            Modifier.width(1.dp).height(dividerHeight)
                .background(YuJianColors.TextPrimary.copy(alpha = 0.20f))
                .testTag("gemini-home-stats-divider"),
        )
        GeminiStatItem(
            tag = "catches", value = if (isResolving) "—" else values.catchCount.toString(),
            label = "鱼获", valueFontSize = valueFontSize, labelFontSize = labelFontSize,
            onClick = onCatchesClick.takeUnless { isResolving },
        )
        Box(
            Modifier.width(1.dp).height(dividerHeight)
                .background(YuJianColors.TextPrimary.copy(alpha = 0.20f))
                .testTag("gemini-home-stats-divider"),
        )
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
            modifier = Modifier
                .semantics { this[GeminiHomeRenderedFontSizeSp] = valueFontSize.value }
                .testTag("gemini-home-stat-$tag-value"),
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
            modifier = Modifier.padding(top = 2.dp)
                .semantics { this[GeminiHomeRenderedFontSizeSp] = labelFontSize.value }
                .testTag("gemini-home-stat-$tag-label"),
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
    layoutSpec: GeminiHomeLayoutSpec,
    layoutVariant: GeminiHomeLayoutVariant,
    onCatchesClick: () -> Unit,
    onCatchClick: (String) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    val titleFontSize = responsiveSp(safeHeight, safeWidth, 0.027f, 16f, 19f)
    val actionFontSize = responsiveSp(safeHeight, safeWidth, 0.021f, 13f, 15f)
    val titleLineHeight = titleFontSize.value * 1.2f * fontScale
    val headerHeight = maxOf(48.dp, (titleLineHeight + 8f).dp)
    val measuredTitleHeightPx = remember(titleFontSize, fontScale) { mutableStateOf(0) }
    val measuredTitleHeightDp = measuredTitleHeightPx.value.takeIf { it > 0 }?.let { heightPx ->
        with(LocalDensity.current) { heightPx.toDp().value }
    } ?: titleLineHeight
    // Measure the actual Text bounds inside the 48dp header row. When a compact
    // profile needs an 8dp gap, shift the card and its 48dp action target upward
    // together instead of allowing the legacy row inset to override the frozen target.
    val titleRowInset = ((headerHeight.value - measuredTitleHeightDp) / 2f).coerceAtLeast(0f)
    val rawHeaderGap = layoutSpec.titleToCardGap.value - titleRowInset
    val cardVerticalOffset = if (layoutVariant == GeminiHomeLayoutVariant.ADAPTIVE_V2) {
        minOf(0f, rawHeaderGap).dp
    } else {
        0.dp
    }
    val headerGap = rawHeaderGap.coerceAtLeast(0f).dp
    val trackWidth = safeWidth.coerceAtMost(560.dp)
    val desiredCardWidth = geminiHomeCardWidth(trackWidth.value, layoutSpec).dp
    val desiredCardHeight = (desiredCardWidth / GeminiHomeCardAspectRatio)
        .coerceAtMost(layoutSpec.maxCardHeight)

    BoxWithConstraints(modifier = modifier) {
        val adaptiveLayout = layoutSpec.topAlignMiddleGroup
        val configuredLeadingGap = layoutSpec.statsToTitleGap?.let {
            (it.value - titleRowInset).coerceAtLeast(0f)
        } ?: clampDp(safeHeight.value * 0.01f, 4f, 10f).value
        val cardShiftUp = (-cardVerticalOffset.value).coerceAtLeast(0f)
        // Keep the button's 48dp target above the card, and never overlap the Stats row.
        val leadingGap = maxOf(configuredLeadingGap, cardShiftUp).dp
        val configuredTrailingGap = layoutSpec.cardToFooterGap ?: clampDp(safeHeight.value * 0.01f, 4f, 10f)
        // The visual card is offset up, so reduce the reserved trailing spacer by the same
        // amount; this preserves the card-to-CTA breathing room instead of creating blank space.
        val trailingGap = (configuredTrailingGap.value - cardShiftUp).coerceAtLeast(0f).dp
        val availableCardHeight = (maxHeight - leadingGap - headerHeight - headerGap - trailingGap)
            .coerceAtLeast(0.dp)
        // Compute against the actual height left after Top and Footer have
        // been measured by the parent Column. Keep the ratio when reducing.
        val cardHeight = if (layoutSpec.contentFirstSizing) {
            geminiHomeResolvedCardHeight(
                desiredCardHeightDp = desiredCardHeight.value,
                availableCardHeightDp = availableCardHeight.value,
                minReadableCardHeightDp = layoutSpec.minReadableCardHeight.value,
            ).dp
        } else if (layoutVariant == GeminiHomeLayoutVariant.V3_BASELINE) {
            // Keep the diagnostic baseline monotonic across larger viewports.
            // When its target Hero height cannot fit, scroll the Middle instead
            // of shrinking the width below the same-font-scale smaller viewport.
            desiredCardHeight
        } else if (availableCardHeight >= layoutSpec.minReadableCardHeight) {
            minOf(desiredCardHeight, availableCardHeight)
        } else {
            desiredCardHeight
        }
        val cardWidth = cardHeight * GeminiHomeCardAspectRatio
        val groupHeight = leadingGap + headerHeight + headerGap + cardHeight + trailingGap
        val needsScroll = groupHeight > maxHeight
        // Frozen Foundation V1.1 splits positive free space equally between the
        // A→B and B→C boundaries. Keep it out of groupHeight so it is allocated
        // once, only for production Adaptive V2 when required content fits.
        val interBandSurplus = if (layoutSpec.contentFirstSizing && !needsScroll) {
            geminiHomeAllocateInterBandSurplus((maxHeight.value - groupHeight.value).coerceAtLeast(0f))
        } else {
            GeminiHomeInterBandSurplus(aToB = 0f, bToC = 0f)
        }
        val extraGapAToB = interBandSurplus.aToB.dp
        val extraGapBToC = interBandSurplus.bToC.dp
        val scrollState = rememberScrollState()
        val contentModifier = if (needsScroll) {
            Modifier.fillMaxWidth().heightIn(min = maxHeight).verticalScroll(scrollState)
                .testTag("gemini-home-short-screen-scroll")
        } else {
            Modifier.fillMaxSize().testTag("gemini-home-middle-layout")
        }

        Column(
            modifier = contentModifier,
            verticalArrangement = if (adaptiveLayout || needsScroll) Arrangement.Top else Arrangement.Center,
        ) {
            // Nonzero spacer width keeps test-tag bounds visible to boundsInWindow checks.
            Spacer(Modifier.fillMaxWidth().height(extraGapAToB).testTag("gemini-home-surplus-a-to-b"))
            Spacer(Modifier.fillMaxWidth().height(leadingGap).testTag("gemini-home-base-gap-a-to-b"))
            GeminiSectionHeader(
                modifier = Modifier.fillMaxWidth().height(headerHeight).testTag("gemini-home-section-header-row"),
                safeWidth = safeWidth,
                titleFontSize = titleFontSize,
                actionFontSize = actionFontSize,
                actionVerticalOffset = cardVerticalOffset,
                onCatchesClick = onCatchesClick,
                onTitleMeasuredHeight = { heightPx -> measuredTitleHeightPx.value = heightPx },
            )
            Spacer(Modifier.fillMaxWidth().height(headerGap).testTag("gemini-home-title-to-hero-gap"))
            if (records.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(cardHeight)
                        .semantics {
                            collectionInfo = CollectionInfo(rowCount = 1, columnCount = 0)
                            stateDescription = "暂无鱼获，0 条记录"
                        }
                        .testTag("gemini-home-empty-carousel"),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(width = cardWidth, height = cardHeight)
                            .clip(RoundedCornerShape(22.dp))
                            .background(YuJianColors.MistWhite.copy(alpha = 0.26f)),
                    )
                }
            } else {
                GeminiCatchCarousel(
                    modifier = Modifier.offset(y = cardVerticalOffset).fillMaxWidth().height(cardHeight)
                        .testTag("gemini-home-carousel-track"),
                    records = records,
                    resolveImageUrl = resolveImageUrl,
                    accessToken = accessToken,
                    safeWidth = safeWidth,
                    safeHeight = safeHeight,
                    cardWidth = cardWidth,
                    cardHeight = cardHeight,
                    runtimeAssets = runtimeAssets,
                    layoutVariant = layoutVariant,
                    onCatchClick = onCatchClick,
                )
            }
            Spacer(Modifier.fillMaxWidth().height(trailingGap).testTag("gemini-home-base-gap-b-to-c"))
            Spacer(Modifier.fillMaxWidth().height(extraGapBToC).testTag("gemini-home-surplus-b-to-c"))
        }
    }
}

@Composable
private fun GeminiSectionHeader(
    modifier: Modifier,
    safeWidth: Dp,
    titleFontSize: TextUnit,
    actionFontSize: TextUnit,
    actionVerticalOffset: Dp,
    onCatchesClick: () -> Unit,
    onTitleMeasuredHeight: (Int) -> Unit,
) {
    val horizontalInset = clampDp(safeWidth.value * 0.088f, 24f, 42f)
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
            onTextLayout = { onTitleMeasuredHeight(it.size.height) },
            modifier = Modifier
                .semantics { this[GeminiHomeRenderedFontSizeSp] = titleFontSize.value }
                .testTag("gemini-home-section-title"),
        )
        Row(
            modifier = Modifier.requiredSizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .offset(y = actionVerticalOffset)
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
                modifier = Modifier
                    .semantics { this[GeminiHomeRenderedFontSizeSp] = actionFontSize.value }
                    .testTag("gemini-home-section-all-label"),
            )
            Text(
                text = "›",
                style = YuJianTypography.body.copy(
                    color = YuJianColors.OnDark.copy(alpha = 0.95f),
                    fontSize = actionFontSize,
                    lineHeight = (actionFontSize.value * 1.2f).sp,
                ),
                maxLines = 1,
                modifier = Modifier
                    .semantics { this[GeminiHomeRenderedFontSizeSp] = actionFontSize.value }
                    .testTag("gemini-home-section-chevron"),
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
    cardWidth: Dp,
    cardHeight: Dp,
    runtimeAssets: GeminiNormalHomeRuntimeAssets?,
    layoutVariant: GeminiHomeLayoutVariant,
    onCatchClick: (String) -> Unit,
) {
    val pageCount = records.size
    val hasNeighborPages = pageCount > 1
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })
    val trackWidth = safeWidth.coerceAtMost(560.dp)
    val pageSpacing = clampDp(trackWidth.value * 0.032f, 10f, 16f)
    val neighborWidth = (if (layoutVariant == GeminiHomeLayoutVariant.ADAPTIVE_V2) {
        geminiHomeNeighborWidthAdaptiveV2(trackWidth.value, cardWidth.value, pageSpacing.value)
    } else {
        geminiHomeNeighborWidth(trackWidth.value, cardWidth.value, pageSpacing.value)
    }).dp
    val sideScaleX = (neighborWidth / cardWidth).coerceIn(0.10f, 1f)
    val sideScaleY = GeminiHomeNeighborHeightFraction
    val neighborTranslationY = with(LocalDensity.current) {
        (GeminiHomePeekTopOffset - cardHeight * ((1f - sideScaleY) / 2f)).toPx()
    }
    val contentInset = ((trackWidth - cardWidth) / 2).coerceAtLeast(0.dp)

    val activeId = records.getOrNull(pagerState.currentPage)?.id
    Box(
        modifier = modifier.semantics {
            collectionInfo = CollectionInfo(rowCount = 1, columnCount = pageCount)
            stateDescription = activeId?.let { "当前鱼获 $it，第 ${pagerState.currentPage + 1} 条，共 $pageCount 条" }
                ?: "暂无鱼获，0 条记录"
        },
        contentAlignment = Alignment.TopCenter,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(cardHeight)
                .semantics {
                    collectionInfo = CollectionInfo(rowCount = 1, columnCount = pageCount)
                    stateDescription = activeId?.let {
                        "当前鱼获 $it，第 ${pagerState.currentPage + 1} 条，共 $pageCount 条"
                    } ?: "暂无鱼获，0 条记录"
                }
                .testTag("gemini-home-catch-pager"),
            pageSize = PageSize.Fixed(cardWidth),
            contentPadding = PaddingValues(horizontal = contentInset),
            pageSpacing = pageSpacing,
            beyondViewportPageCount = if (hasNeighborPages) 1 else 0,
            userScrollEnabled = hasNeighborPages,
            key = { page -> records[page].id },
        ) { page ->
            val record = records[page]
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
                    translationY = if (active) 0f else neighborTranslationY
                    alpha = 1f - distance * 0.08f
                }.testTag(pageTag),
                contentAlignment = Alignment.TopCenter,
            ) {
                GeminiCatchCard(
                    item = record,
                    imageUrl = resolveImageUrl(record.imageUrl),
                    accessToken = accessToken,
                    safeWidth = safeWidth,
                    cardWidth = cardWidth,
                    cardHeight = cardHeight,
                    safeHeight = safeHeight,
                    runtimeAssets = runtimeAssets,
                    isActive = active,
                    layoutVariant = layoutVariant,
                    onClick = { onCatchClick(record.id) },
                )
            }
        }
    }
}

@Composable
private fun GeminiCatchCard(
    item: RemoteCatch,
    imageUrl: String?,
    accessToken: String,
    safeWidth: Dp,
    cardWidth: Dp,
    cardHeight: Dp,
    safeHeight: Dp,
    runtimeAssets: GeminiNormalHomeRuntimeAssets?,
    isActive: Boolean,
    layoutVariant: GeminiHomeLayoutVariant,
    onClick: () -> Unit,
) {
    val textGap = clampDp(safeHeight.value * 0.006f, 2f, 5f)
    val currentFontScale = LocalDensity.current.fontScale
    val allowAuxiliaryWrapping = geminiHomeShouldWrapAuxiliaryText(
        cardWidthDp = cardWidth.value,
        fontScale = currentFontScale,
        variant = layoutVariant,
    )
    val allowSpeciesWrapping = geminiHomeShouldWrapSpeciesTitle(
        cardWidthDp = cardWidth.value,
        fontScale = currentFontScale,
        variant = layoutVariant,
    )
    val speciesSize = responsiveSp(safeHeight, safeWidth, 0.036f, 20f, 26f)
    val measurementSize = responsiveSp(safeHeight, safeWidth, 0.023f, 13f, 16f)
    val metadataSize = responsiveSp(safeHeight, safeWidth, 0.019f, 11f, 13f)
    val footerVerticalPadding = clampDp(cardHeight.value * 0.07f, 10f, 20f)
    val footerHorizontalPadding = if (layoutVariant == GeminiHomeLayoutVariant.ADAPTIVE_V2) {
        // Scale the inner inset continuously so the long metadata line budget gets
        // usable width on compact cards without changing the Hero's outer geometry.
        clampDp(cardWidth.value * 0.06f, 8f, 20f)
    } else {
        if (cardWidth.value < 190f) 12.dp else 20.dp
    }
    val species = presentationSpeciesName(item.speciesName)
    val measurements = buildList {
        item.lengthCm?.takeIf { it > 0f }?.let { add("${formatGeminiNumber(it)} cm") }
        item.weightKg?.takeIf { it > 0f }?.let { add("${formatGeminiNumber(it)} kg") }
    }.joinToString(" · ")
    val time = PresentationSanitizer.formatHomeTimestamp(item.capturedAt, item.createdAt)
        ?.takeIf { it.isNotBlank() }
    val location = sanitizeOptionalText(item.location)
        ?.let(::normalizeGeminiHomeLocation)
        ?.takeIf { it.isNotBlank() }
    val metadataStyle = YuJianTypography.caption.copy(
        color = YuJianColors.OnDark.copy(alpha = 0.86f),
        fontSize = metadataSize,
        lineHeight = (metadataSize.value * 1.3f).sp,
    )
    val metadataTextMeasurer = rememberTextMeasurer()
    val metadataMaxWidthPx = with(LocalDensity.current) {
        (cardWidth - footerHorizontalPadding * 2).coerceAtLeast(1.dp).roundToPx()
    }
    // Keep the time intact. If time+location cannot fit on one line, put the time
    // on line one and pre-ellipsize the optional location to one complete line.
    // This prevents a final lone CJK character such as “号” and keeps TextLayoutResult
    // free of accidental overflow even when Compose would otherwise report no overflow.
    val metadata = remember(time, location, metadataStyle, metadataMaxWidthPx, currentFontScale, metadataTextMeasurer) {
        val combined = listOfNotNull(time, location).joinToString(" · ")
        when {
            combined.isBlank() -> ""
            time != null && location != null &&
                !geminiHomeTextFitsSingleLine(combined, metadataTextMeasurer, metadataStyle, metadataMaxWidthPx) -> {
                val fittedLocation = geminiHomeEllipsizeSingleLine(
                    location, metadataTextMeasurer, metadataStyle, metadataMaxWidthPx,
                )
                if (fittedLocation.isBlank()) time else "$time\n$fittedLocation"
            }
            time != null && location != null -> combined
            time != null -> time
            location != null -> geminiHomeEllipsizeSingleLine(
                location, metadataTextMeasurer, metadataStyle, metadataMaxWidthPx,
            )
            else -> ""
        }
    }
    val metadataMaxLines = if (metadata.contains('\n')) 2 else 1
    val speciesTextOverflow = remember(item.id) { mutableStateOf(false) }
    val measurementTextOverflow = remember(item.id) { mutableStateOf(false) }
    val metadataTextOverflow = remember(item.id) { mutableStateOf(false) }
    val metadataLineCount = remember(item.id) { mutableStateOf(0) }
    val metadataLastLineCharacterCount = remember(item.id) { mutableStateOf(0) }

    YuJianHeroCard(
        title = species,
        metadata = emptyList(),
        variant = YuJianHeroVariant.HOME,
        modifier = Modifier.fillMaxSize(),
        heightOverride = cardHeight,
        cornerRadius = 22.dp,
        footerPaddingHorizontal = footerHorizontalPadding,
        footerPaddingVertical = footerVerticalPadding,
        footerContentFillWidth = true,
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
                    maxLines = if (allowSpeciesWrapping) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { speciesTextOverflow.value = it.hasVisualOverflow },
                    modifier = Modifier
                        .semantics {
                            this[GeminiHomeTextHasVisualOverflow] = speciesTextOverflow.value
                            this[GeminiHomeRenderedFontSizeSp] = speciesSize.value
                        }
                        .testTag("gemini-home-catch-species-${item.id}"),
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
                        maxLines = if (allowAuxiliaryWrapping) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { measurementTextOverflow.value = it.hasVisualOverflow },
                        modifier = Modifier.padding(top = textGap)
                            .semantics {
                            this[GeminiHomeTextHasVisualOverflow] = measurementTextOverflow.value
                            this[GeminiHomeRenderedFontSizeSp] = measurementSize.value
                        }
                            .testTag("gemini-home-catch-measurement-${item.id}"),
                    )
                }
                if (metadata.isNotBlank()) {
                    Text(
                        text = metadata,
                        style = metadataStyle,
                        maxLines = metadataMaxLines,
                        overflow = TextOverflow.Clip,
                        onTextLayout = {
                            metadataTextOverflow.value = it.hasVisualOverflow
                            metadataLineCount.value = it.lineCount
                            metadataLastLineCharacterCount.value = if (it.lineCount > 0) {
                                it.getLineEnd(it.lineCount - 1, visibleEnd = true) -
                                    it.getLineStart(it.lineCount - 1)
                            } else {
                                0
                            }
                        },
                        modifier = Modifier.padding(top = textGap)
                            .semantics {
                                this[GeminiHomeTextHasVisualOverflow] = metadataTextOverflow.value
                                this[GeminiHomeRenderedFontSizeSp] = metadataSize.value
                                this[GeminiHomeTextLineCount] = metadataLineCount.value
                                this[GeminiHomeTextLastLineCharacterCount] = metadataLastLineCharacterCount.value
                            }
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
                    smartFishPhoto = true,
                    fishPhotoSourceVersion = item.id,
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
    safeWidth: Dp,
    safeHeight: Dp,
    safeBottomInset: Dp,
    isResolving: Boolean,
    motionState: HomeMotionState,
    runtimeAssets: GeminiNormalHomeRuntimeAssets?,
    cameraVisualSize: Dp,
    cameraTouchSize: Dp,
    onIdentify: () -> Unit,
) {
    val compactViewport = safeHeight.value < 460f
    val verticalPadding = when {
        compactViewport -> 2.dp
        safeBottomInset >= 40.dp -> 6.dp
        else -> 8.dp
    }
    val gap = if (compactViewport) 6.dp else 11.dp
    val labelSize = responsiveSp(safeHeight, safeWidth, 0.021f, 12f, 14f)
    val horizontalInset = clampDp(safeHeight.value * 0.01f, 8f, 12f)
    Column(
        modifier = modifier.testTag("gemini-home-footer-cta")
            .padding(horizontal = horizontalInset)
            .padding(top = if (compactViewport) 2.dp else 4.dp, bottom = verticalPadding),
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
                modifier = Modifier
                    .semantics { this[GeminiHomeRenderedFontSizeSp] = labelSize.value }
                    .testTag("gemini-home-cta-label"),
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

private fun responsiveSp(height: Dp, width: Dp, ratio: Float, minimum: Float, maximum: Float) =
    ((height.value * ratio).coerceIn(minimum, maximum) *
        geminiHomeResponsiveFontScale(width.value, height.value)).sp

private fun clampDp(value: Float, minimum: Float, maximum: Float) =
    value.coerceIn(minimum, maximum).dp

private fun formatGeminiNumber(value: Float): String =
    String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')


private fun normalizeGeminiHomeLocation(value: String): String =
    value.trim().trimEnd(' ', '·', '•', ',', '，', '.', '。', ';', '；', ':', '：', '、', '-', '—', '_').trim()

private fun geminiHomeTextFitsSingleLine(
    value: String,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    maxWidthPx: Int,
): Boolean {
    if (value.isBlank() || maxWidthPx <= 0) return false
    val layout = textMeasurer.measure(
        text = AnnotatedString(value),
        style = style,
        overflow = TextOverflow.Clip,
        softWrap = false,
        maxLines = 1,
        constraints = Constraints(maxWidth = maxWidthPx),
    )
    // A clipped single-line layout can report no visual overflow even when its
    // visible text range does not reach the source's final character.
    return layout.lineCount == 1 && !layout.hasVisualOverflow &&
        layout.getLineEnd(0, visibleEnd = true) == value.length
}

private fun geminiHomeEllipsizeSingleLine(
    value: String,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    maxWidthPx: Int,
): String {
    val normalized = normalizeGeminiHomeLocation(value)
    if (normalized.isBlank() || maxWidthPx <= 0) return ""
    if (geminiHomeTextFitsSingleLine(normalized, textMeasurer, style, maxWidthPx)) return normalized
    val ellipsis = "…"
    if (!geminiHomeTextFitsSingleLine(ellipsis, textMeasurer, style, maxWidthPx)) return ""
    val codePoints = normalized.codePointCount(0, normalized.length)
    if (codePoints < 2) return ""
    var low = 2
    var high = codePoints
    var best = 0
    while (low <= high) {
        val middle = (low + high) / 2
        val end = normalized.offsetByCodePoints(0, middle)
        val prefix = normalizeGeminiHomeLocation(normalized.substring(0, end))
        val candidate = prefix + ellipsis
        if (prefix.codePointCount(0, prefix.length) >= 2 &&
            geminiHomeTextFitsSingleLine(candidate, textMeasurer, style, maxWidthPx)
        ) {
            best = middle
            low = middle + 1
        } else {
            high = middle - 1
        }
    }
    if (best < 2) return ""
    val end = normalized.offsetByCodePoints(0, best)
    return normalizeGeminiHomeLocation(normalized.substring(0, end)) + ellipsis
}
