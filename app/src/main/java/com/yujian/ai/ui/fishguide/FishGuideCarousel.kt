package com.yujian.ai.ui.fishguide

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.yujian.ai.ui.designsystem.components.YuJianFishGuideCard
import com.yujian.ai.ui.designsystem.components.YuJianFishGuideCardVariant
import com.yujian.ai.ui.designsystem.motion.YuJianMotion
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
fun FishGuideCarousel(
    items: List<FishGuidePresentationItem>,
    selectedId: String?,
    onSelectionChanged: (String) -> Unit,
    onSpeciesClick: (FishGuidePresentationItem) -> Unit,
    discoverHintShown: Boolean,
    reduceMotion: Boolean,
    screenResumed: Boolean,
    onDiscoverHintConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val itemIds = remember(items) { items.map { it.id } }
    val selectedIndex = selectionIndex(items.map { it.source }, selectedId).coerceIn(0, items.lastIndex)
    val pagerState = rememberPagerState(initialPage = selectedIndex, pageCount = { items.size })
    val coroutineScope = rememberCoroutineScope()
    val hintTranslation = remember(pagerState) { Animatable(0f) }
    var hintCanceledByUser by remember(pagerState, itemIds) { mutableStateOf(false) }
    var settledPage by remember(itemIds) { mutableIntStateOf(selectedIndex) }

    LaunchedEffect(pagerState, itemIds, selectedId, reduceMotion) {
        val target = selectionIndex(items.map { it.source }, selectedId).coerceIn(0, items.lastIndex)
        if (pagerState.currentPage != target) {
            if (reduceMotion) pagerState.scrollToPage(target) else pagerState.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pagerState, itemIds) {
        snapshotPagerSelection(pagerState, items) { id, page ->
            settledPage = page
            onSelectionChanged(id)
        }
    }
    LaunchedEffect(pagerState, itemIds, reduceMotion, screenResumed, hintCanceledByUser) {
        if (discoverHintShown || reduceMotion || !screenResumed || items.size < 2 || hintCanceledByUser) return@LaunchedEffect
        delay(YuJianMotion.CarouselDiscoverHintDelayMillis.toLong())
        if (pagerState.isScrollInProgress) return@LaunchedEffect

        onDiscoverHintConsumed()
        val nudgeDirection = if (pagerState.currentPage >= items.lastIndex) 1f else -1f
        try {
            coroutineScope {
                val hintJob = launch {
                    hintTranslation.animateTo(
                        nudgeDirection * YuJianMotion.CarouselDiscoverHintOffsetDp,
                        animationSpec = tween(
                            durationMillis = YuJianMotion.CarouselDiscoverHintOutDurationMillis,
                            easing = YuJianMotion.calmEasing,
                        ),
                    )
                    hintTranslation.animateTo(
                        0f,
                        animationSpec = tween(
                            durationMillis = YuJianMotion.CarouselDiscoverHintReturnDurationMillis,
                            easing = YuJianMotion.calmEasing,
                        ),
                    )
                }
                val cancelOnSwipe = launch {
                    snapshotFlow { pagerState.isScrollInProgress }.filter { it }.first()
                    hintJob.cancelAndJoin()
                }
                hintJob.join()
                cancelOnSwipe.cancel()
            }
        } finally {
            withContext(NonCancellable) { hintTranslation.snapTo(0f) }
        }
    }

    val markUserInteraction = {
        if (items.size > 1 && !hintCanceledByUser) {
            hintCanceledByUser = true
            if (!discoverHintShown) onDiscoverHintConsumed()
        }
    }
    val currentMarkUserInteraction by rememberUpdatedState(markUserInteraction)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val geometry = FishGuideResponsiveGeometryResolver.resolveHome(maxWidth.value)
        val cardWidth = geometry.cardWidthDp.dp
        val cardHeight = geometry.cardHeightDp.dp
        val sidePeek = geometry.sidePaddingDp.dp
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .pointerInput(pagerState, itemIds) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        currentMarkUserInteraction()
                    }
                }
                .graphicsLayer { translationX = hintTranslation.value.dp.toPx() },
            pageSize = PageSize.Fixed(cardWidth),
            contentPadding = PaddingValues(horizontal = sidePeek),
            pageSpacing = geometry.pageSpacingDp.dp,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = tween(durationMillis = if (reduceMotion) 100 else 260),
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) { page ->
            val item = items[page]
            var displayedDiscovered by remember(item.id) { mutableStateOf(item.discovered) }
            var displayedCatchCount by remember(item.id) { mutableIntStateOf(item.catches) }
            var animateEncounterTransition by remember(item.id) { mutableStateOf(false) }
            var animateRecordCountChange by remember(item.id) { mutableStateOf(false) }
            var animateFirstRecordReveal by remember(item.id) { mutableStateOf(false) }
            val pageIsSettledActive = page == settledPage && page == pagerState.currentPage && !pagerState.isScrollInProgress
            LaunchedEffect(item.id, item.discovered, item.catches, pageIsSettledActive, reduceMotion) {
                val discoveryChanged = displayedDiscovered != item.discovered
                val countChanged = displayedCatchCount != item.catches
                if (!discoveryChanged && !countChanged) {
                    animateEncounterTransition = false
                    animateRecordCountChange = false
                    animateFirstRecordReveal = false
                    return@LaunchedEffect
                }

                displayedDiscovered = item.discovered
                displayedCatchCount = item.catches
                animateEncounterTransition = pageIsSettledActive && discoveryChanged
                animateRecordCountChange = pageIsSettledActive && countChanged
                animateFirstRecordReveal = pageIsSettledActive && discoveryChanged && countChanged && item.discovered
                if (!pageIsSettledActive) return@LaunchedEffect

                val duration = when {
                    reduceMotion -> 100L
                    discoveryChanged && item.discovered -> 420L
                    discoveryChanged -> 260L
                    else -> 160L
                }
                try {
                    delay(duration)
                } finally {
                    animateEncounterTransition = false
                    animateRecordCountChange = false
                    animateFirstRecordReveal = false
                }
            }
            val displayedItem = item.copy(
                source = item.source.copy(
                    discovered = displayedDiscovered,
                    catches = displayedCatchCount,
                ),
            )
            val distance = abs((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
            val emphasis = lerp(0.94f, 1f, (1f - distance).coerceIn(0f, 1f))
            val alpha = lerp(0.58f, 1f, (1f - distance).coerceIn(0f, 1f))
            val isSettledActive = page == settledPage && !pagerState.isScrollInProgress
            val stateText = buildString {
                append("第 ${page + 1} 种，共 ${items.size} 种")
                if (isSettledActive && page == pagerState.currentPage) {
                    append(if (displayedItem.discovered) "，已点亮" else "，尚未点亮")
                    if (displayedItem.catches > 0) append("，${displayedItem.catches} 次鱼获记录")
                    append("，当前选中")
                }
            }
            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .height(cardHeight)
                    .testTag("fish_guide_card_${item.id}")
                    .graphicsLayer {
                        scaleX = if (reduceMotion && !pagerState.isScrollInProgress) 1f else emphasis
                        scaleY = if (reduceMotion && !pagerState.isScrollInProgress) 1f else emphasis
                        this.alpha = if (reduceMotion && !pagerState.isScrollInProgress && page != pagerState.currentPage) 0.68f else alpha
                    }
                    .semantics {
                        contentDescription = item.name
                        stateDescription = stateText
                    },
                contentAlignment = Alignment.Center,
            ) {
                YuJianFishGuideCard(
                    item = displayedItem,
                    variant = if (page == pagerState.currentPage) {
                        if (displayedItem.discovered) YuJianFishGuideCardVariant.LIT else YuJianFishGuideCardVariant.UNLIT
                    } else {
                        YuJianFishGuideCardVariant.ADJACENT_PREVIEW
                    },
                    modifier = Modifier.fillMaxWidth(),
                    animateEncounterTransition = animateEncounterTransition,
                    animateRecordCountChange = animateRecordCountChange,
                    animateFirstRecordReveal = animateFirstRecordReveal,
                    reduceMotion = reduceMotion,
                    onClick = {
                        markUserInteraction()
                        if (page == pagerState.currentPage) {
                            onSpeciesClick(item)
                        } else {
                            coroutineScope.launch {
                                if (reduceMotion) pagerState.scrollToPage(page)
                                else pagerState.animateScrollToPage(page)
                            }
                        }
                    },
                )
            }
        }
    }
}

private suspend fun snapshotPagerSelection(
    pagerState: PagerState,
    items: List<FishGuidePresentationItem>,
    onSelectionChanged: (String, Int) -> Unit,
) {
    snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
        .drop(1)
        .filter { (_, scrolling) -> !scrolling }
        .map { (page, _) -> page to items.getOrNull(page)?.id }
        .distinctUntilChanged()
        .collect { (page, selectedId) -> selectedId?.let { onSelectionChanged(it, page) } }
}
