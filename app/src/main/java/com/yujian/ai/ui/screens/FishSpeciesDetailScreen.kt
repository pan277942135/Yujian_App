package com.yujian.ai.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.knowledge.FishGuideItem
import com.yujian.ai.knowledge.FishKnowledgeDetail
import com.yujian.ai.knowledge.toFallbackDetail
import com.yujian.ai.ui.components.RemoteImage
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.components.YuJianBackTitleTopBar
import com.yujian.ai.ui.designsystem.components.YuJianGlassCard
import com.yujian.ai.ui.designsystem.components.YuJianPrimaryButton
import com.yujian.ai.ui.designsystem.components.YuJianTextAction
import com.yujian.ai.ui.designsystem.components.YuJianTextActionRole
import com.yujian.ai.ui.designsystem.glass.YuJianGlassLevel
import com.yujian.ai.ui.designsystem.radius.YuJianRadius
import com.yujian.ai.ui.designsystem.spacing.YuJianSpacing
import com.yujian.ai.ui.designsystem.typography.YuJianTypography
import com.yujian.ai.ui.fishguide.FishGuideBackground
import com.yujian.ai.ui.fishguide.FishGuideKnowledgeCardPresentation
import com.yujian.ai.ui.fishguide.savedRecordsForSpecies
import com.yujian.ai.ui.fishguide.toKnowledgeCardPresentations
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.animation.core.tween

private val knowledgeCardGold = YuJianColors.SoftGold
private val knowledgeCardShape = RoundedCornerShape(24.dp)

@Composable
fun FishSpeciesDetailScreen(
    detail: FishKnowledgeDetail?,
    fallback: FishGuideItem?,
    savedCatches: List<RemoteCatch>,
    loading: Boolean,
    offlinePreview: Boolean,
    error: String?,
    resolveAssetUrl: (String?) -> String?,
    resolveCatchImageUrl: (String?) -> String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onRecordCatch: () -> Unit,
    onOpenCatch: (String) -> Unit,
    onOpenSpeciesCatches: (String) -> Unit,
) {
    val content = detail?.takeIf {
        it.species.status.equals("ACTIVE", ignoreCase = true) &&
            (fallback == null || it.species.id.equals(fallback.id, ignoreCase = true))
    }
        ?: fallback?.takeIf { it.catalogStatus.equals("ACTIVE", ignoreCase = true) }?.toFallbackDetail()

    FishGuideBackground {
        if (content == null) {
            SpeciesDetailUnavailable(
                loading = loading,
                onRetry = onRetry,
                onBack = onBack,
            )
            return@FishGuideBackground
        }

        SpeciesDetailContent(
            detail = content,
            savedCatches = savedCatches,
            loading = loading,
            offlinePreview = offlinePreview || error != null,
            resolveAssetUrl = resolveAssetUrl,
            resolveCatchImageUrl = resolveCatchImageUrl,
            onRetry = onRetry,
            onBack = onBack,
            onRecordCatch = onRecordCatch,
            onOpenCatch = onOpenCatch,
            onOpenSpeciesCatches = onOpenSpeciesCatches,
        )
    }
}

@Composable
private fun SpeciesDetailContent(
    detail: FishKnowledgeDetail,
    savedCatches: List<RemoteCatch>,
    loading: Boolean,
    offlinePreview: Boolean,
    resolveAssetUrl: (String?) -> String?,
    resolveCatchImageUrl: (String?) -> String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onRecordCatch: () -> Unit,
    onOpenCatch: (String) -> Unit,
    onOpenSpeciesCatches: (String) -> Unit,
) {
    val species = detail.species
    val guideIdentity = remember(species.id, species.nameCn, species.status) {
        FishGuideItem(
            id = species.id,
            nameCn = species.nameCn,
            aliases = species.aliases,
            category = species.category,
            summary = species.summary,
            coverImage = species.coverImage,
            catalogStatus = species.status,
        )
    }
    val records = remember(guideIdentity, savedCatches) { savedRecordsForSpecies(guideIdentity, savedCatches) }
    val cards = remember(detail) { detail.toKnowledgeCardPresentations() }
    val activePageStore = rememberSaveable(species.id) { mutableIntStateOf(0) }
    val initialPage = activePageStore.intValue.coerceIn(0, cards.lastIndex)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { cards.size })
    val reduceMotion = rememberFishGuideReduceMotion()
    val scope = rememberCoroutineScope()
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    var settledPage by remember(species.id) { mutableIntStateOf(initialPage) }

    LaunchedEffect(pagerState, species.id) {
        snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
            .filter { (_, scrolling) -> !scrolling }
            .map { (page, _) -> page.coerceIn(0, cards.lastIndex) }
            .distinctUntilChanged()
            .collect { page ->
                settledPage = page
                activePageStore.intValue = page
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding() + YuJianSpacing.lg),
    ) {
        YuJianBackTitleTopBar(
            title = "",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = YuJianSpacing.xs),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = safeInsets.calculateStartPadding(layoutDirection),
                    end = safeInsets.calculateEndPadding(layoutDirection),
                ),
        ) {
            SpeciesHeader(detail)

            if (offlinePreview || loading) {
                Text(
                    text = if (loading) "正在加载鱼种资料" else "离线内容",
                    style = YuJianTypography.caption.copy(color = YuJianColors.TextSecondary),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp),
                )
            }

            SpeciesKnowledgeCarousel(
                cards = cards,
                pagerState = pagerState,
                settledPage = settledPage,
                reduceMotion = reduceMotion,
                resolveAssetUrl = resolveAssetUrl,
                onAdjacentTap = { page ->
                    scope.launch {
                        if (reduceMotion) pagerState.scrollToPage(page)
                        else pagerState.animateScrollToPage(page)
                    }
                },
            )

            Text(
                text = cards[settledPage].pageLabel,
                style = YuJianTypography.body.copy(color = YuJianColors.TextSecondary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                textAlign = TextAlign.Center,
            )

            MySpeciesSection(
                species = species,
                records = records,
                resolveCatchImageUrl = resolveCatchImageUrl,
                onRecordCatch = onRecordCatch,
                onOpenCatch = onOpenCatch,
                onOpenSpeciesCatches = { onOpenSpeciesCatches(species.id) },
                modifier = Modifier.padding(horizontal = YuJianSpacing.md, vertical = 20.dp),
            )

            if (offlinePreview && !loading) {
                YuJianTextAction(
                    text = "重试",
                    onClick = onRetry,
                    role = YuJianTextActionRole.NORMAL,
                    showChevron = true,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

@Composable
private fun SpeciesHeader(detail: FishKnowledgeDetail) {
    val species = detail.species
    val descriptor = remember(detail) {
        listOfNotNull(
            species.category.trim().takeIf(String::isNotEmpty),
            detail.profile.bodyShape?.trim()?.takeIf(String::isNotEmpty),
        ).distinct().joinToString(" · ")
            .ifBlank { species.family?.trim().orEmpty() }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = species.nameCn,
            style = YuJianTypography.heroTitle.copy(fontSize = 34.sp, color = YuJianColors.DeepInk),
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        if (descriptor.isNotBlank()) {
            Text(
                text = descriptor,
                style = YuJianTypography.body.copy(color = YuJianColors.DeepInk.copy(alpha = 0.82f)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        Box(
            Modifier
                .padding(top = 3.dp)
                .width(44.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(YuJianColors.MorningGold),
        )
    }
}

@Composable
private fun SpeciesKnowledgeCarousel(
    cards: List<FishGuideKnowledgeCardPresentation>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    settledPage: Int,
    reduceMotion: Boolean,
    resolveAssetUrl: (String?) -> String?,
    onAdjacentTap: (Int) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cardWidth = maxWidth * 0.84f
        val sidePeek = (maxWidth - cardWidth) / 2f
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 390.dp)
                .testTag("fish_species_knowledge_carousel"),
            pageSize = PageSize.Fixed(cardWidth),
            contentPadding = PaddingValues(horizontal = sidePeek),
            pageSpacing = 8.dp,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = tween(durationMillis = if (reduceMotion) 100 else 260),
            ),
            verticalAlignment = Alignment.Top,
        ) { page ->
            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .heightIn(min = 390.dp)
                    .semantics {
                        contentDescription = "${cards[page].label}，${cards[page].title}"
                        stateDescription = "第 ${page + 1} 张，共 5 张${if (page == settledPage && !pagerState.isScrollInProgress) "，当前选中" else ""}"
                    },
            ) {
                KnowledgeCardSurface(
                    card = cards[page],
                    resolveAssetUrl = resolveAssetUrl,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = if (page == pagerState.currentPage) null else ({ onAdjacentTap(page) }),
                )
            }
        }
    }
}

@Composable
private fun KnowledgeCardSurface(
    card: FishGuideKnowledgeCardPresentation,
    resolveAssetUrl: (String?) -> String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val clickInteraction = remember { MutableInteractionSource() }
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = clickInteraction,
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
    } else Modifier
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 390.dp)
            .clip(knowledgeCardShape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF090B0B), Color(0xFF17191A), Color(0xFF070808)),
                ),
            )
            .border(1.dp, knowledgeCardGold.copy(alpha = 0.82f), knowledgeCardShape)
            .then(clickableModifier)
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = card.label.uppercase(),
            color = knowledgeCardGold.copy(alpha = 0.88f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.1.sp,
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(knowledgeCardGold.copy(alpha = 0.38f)))
        Text(
            text = card.title,
            color = Color(0xFFF4DB9C),
            fontSize = 25.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (card.type == "HERO") {
            val imageUrl = resolveAssetUrl(card.subjectImageUrl).takeIf { !it.isNullOrBlank() }
            if (imageUrl != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp, max = 178.dp)
                        .background(Color(0xFF0C1111), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    RemoteImage(
                        url = imageUrl,
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                        contentDescription = "${card.title}鱼种影像",
                        contentScale = ContentScale.Fit,
                        placeholder = { Text("鱼种影像暂不可用", color = Color(0xFFBFC5C0), fontSize = 13.sp) },
                    )
                }
            }
        }

        card.summary?.let { summary ->
            Text(
                text = summary,
                color = Color(0xFFE5E4DD),
                fontSize = 15.sp,
                lineHeight = 23.sp,
            )
        }

        if (card.facts.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                card.facts.forEach { fact ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            Modifier
                                .padding(top = 7.dp)
                                .size(5.dp)
                                .background(knowledgeCardGold, RoundedCornerShape(50)),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = fact.label,
                                color = knowledgeCardGold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = fact.value,
                                color = Color(0xFFF0F0EB),
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                            )
                        }
                    }
                }
            }
        }

        if (!card.available) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "内容暂不可用",
                color = Color(0xFFE4DABF),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "该卡片资料正在完善",
                color = Color(0xFFB7B9B3),
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
    }
}

@Composable
private fun MySpeciesSection(
    species: com.yujian.ai.knowledge.FishKnowledgeSpecies,
    records: List<RemoteCatch>,
    resolveCatchImageUrl: (String?) -> String?,
    onRecordCatch: () -> Unit,
    onOpenCatch: (String) -> Unit,
    onOpenSpeciesCatches: () -> Unit,
    modifier: Modifier = Modifier,
) {
    YuJianGlassCard(
        modifier = modifier.fillMaxWidth(),
        level = YuJianGlassLevel.Medium,
        shape = YuJianRadius.glassCard,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val headerInteraction = remember { MutableInteractionSource() }
            val headerPressed by headerInteraction.collectIsPressedAsState()
            val headerAlpha by animateFloatAsState(
                targetValue = if (records.isNotEmpty() && headerPressed) 0.92f else 1f,
                animationSpec = tween(durationMillis = if (headerPressed) 70 else 100),
                label = "FishGuideMySpeciesHeaderPress",
            )
            val headerModifier = if (records.isNotEmpty()) {
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .graphicsLayer { alpha = headerAlpha }
                    .clickable(
                        interactionSource = headerInteraction,
                        indication = null,
                        role = Role.Button,
                        onClick = onOpenSpeciesCatches,
                    )
                    .semantics {
                        contentDescription = "我的${species.nameCn}，${records.size}次记录，打开该鱼种鱼获"
                    }
            } else {
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
            }
            Row(
                modifier = headerModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "我的${species.nameCn}",
                    color = YuJianColors.DeepInk,
                    fontSize = 23.sp,
                    lineHeight = 29.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${records.size}次记录",
                        color = YuJianColors.DeepInk.copy(alpha = 0.88f),
                        fontSize = 15.sp,
                    )
                    if (records.isNotEmpty()) {
                        Text(
                            text = "›",
                            color = YuJianColors.DeepInk,
                            fontSize = 27.sp,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            if (records.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "还没有记录",
                        color = YuJianColors.DeepInk.copy(alpha = 0.84f),
                        fontSize = 15.sp,
                    )
                    YuJianTextAction(
                        text = "去记录鱼获",
                        onClick = onRecordCatch,
                        role = YuJianTextActionRole.STRONG,
                        showChevron = true,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    records.take(2).forEach { record ->
                        CatchPreviewTile(
                            record = record,
                            speciesName = species.nameCn,
                            imageUrl = resolveCatchImageUrl(record.imageUrl).takeIf { !it.isNullOrBlank() },
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenCatch(record.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatchPreviewTile(
    record: RemoteCatch,
    speciesName: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = if (pressed) 70 else 100),
        label = "FishGuideCatchPreviewPress",
    )
    val date = record.capturedAt.ifBlank { record.createdAt }.take(10)
    val description = buildString {
        append("打开${speciesName}鱼获记录")
        if (date.isNotBlank()) append("，$date")
        if (imageUrl == null) append("，照片暂不可用")
    }
    Box(
        modifier = modifier
            .heightIn(min = 88.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.50f))
            .border(1.dp, Color.White.copy(alpha = 0.74f), RoundedCornerShape(14.dp))
            .graphicsLayer { alpha = pressAlpha }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                contentDescription = description
                stateDescription = date
            },
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            RemoteImage(
                url = imageUrl,
                modifier = Modifier.fillMaxSize(),
                contentDescription = "${speciesName}鱼获，$date",
                contentScale = ContentScale.Crop,
                placeholder = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("照片暂不可用", color = YuJianColors.TextSecondary, fontSize = 12.sp)
                    }
                },
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("照片暂不可用", color = YuJianColors.TextSecondary, fontSize = 12.sp)
                if (date.isNotBlank()) Text(date, color = YuJianColors.DeepInk, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SpeciesDetailUnavailable(
    loading: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val safeStart = safeInsets.calculateStartPadding(layoutDirection)
    val safeEnd = safeInsets.calculateEndPadding(layoutDirection)
    Column(Modifier.fillMaxSize().padding(bottom = safeInsets.calculateBottomPadding())) {
        YuJianBackTitleTopBar(
            title = "鱼种详情",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = YuJianSpacing.xs),
        )
        if (loading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = safeStart + YuJianSpacing.lg,
                        end = safeEnd + YuJianSpacing.lg,
                        top = 22.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Box(Modifier.fillMaxWidth(0.42f).height(24.dp).background(YuJianColors.MistBlueGray.copy(alpha = 0.24f), RoundedCornerShape(8.dp)))
                Box(Modifier.fillMaxWidth(0.30f).height(12.dp).background(YuJianColors.MistBlueGray.copy(alpha = 0.20f), RoundedCornerShape(6.dp)))
                Box(Modifier.fillMaxWidth().height(390.dp).background(YuJianColors.MistWhite.copy(alpha = 0.58f), knowledgeCardShape))
                Box(Modifier.fillMaxWidth().height(126.dp).background(YuJianColors.MistWhite.copy(alpha = 0.58f), YuJianRadius.glassCard))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = safeStart + YuJianSpacing.lg,
                        end = safeEnd + YuJianSpacing.lg,
                        top = 48.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "该鱼种资料暂不可用",
                    style = YuJianTypography.sectionTitle,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "检查网络后重试",
                    style = YuJianTypography.caption,
                    textAlign = TextAlign.Center,
                )
                YuJianPrimaryButton(
                    text = "重试",
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 6.dp),
                    leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(17.dp)) },
                )
            }
        }
    }
}
