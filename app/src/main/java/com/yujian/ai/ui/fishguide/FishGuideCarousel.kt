package com.yujian.ai.ui.fishguide

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

    LaunchedEffect(pagerState, itemIds, selectedId) {
        val target = selectionIndex(items.map { it.source }, selectedId).coerceIn(0, items.lastIndex)
        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }
    LaunchedEffect(pagerState, itemIds) {
        snapshotPagerSelection(pagerState, items, onSelectionChanged)
    }
    LaunchedEffect(pagerState, itemIds, reduceMotion, screenResumed, hintCanceledByUser) {
        if (discoverHintShown || reduceMotion || !screenResumed || items.size < 2 || hintCanceledByUser) return@LaunchedEffect
        delay(YuJianMotion.CarouselDiscoverHintDelayMillis.toLong())
        if (pagerState.isScrollInProgress) return@LaunchedEffect

        onDiscoverHintConsumed()
        try {
            coroutineScope {
                val hintJob = launch {
                    hintTranslation.animateTo(
                        -YuJianMotion.CarouselDiscoverHintOffsetDp,
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
            if (!discoverHintShown && !reduceMotion) onDiscoverHintConsumed()
        }
    }
    val currentMarkUserInteraction by rememberUpdatedState(markUserInteraction)

    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(pagerState, itemIds) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    currentMarkUserInteraction()
                }
            }
            .graphicsLayer { translationX = hintTranslation.value.dp.toPx() },
        pageSize = PageSize.Fixed(284.dp),
        contentPadding = PaddingValues(horizontal = 36.dp),
        pageSpacing = 12.dp,
        verticalAlignment = Alignment.CenterVertically,
    ) { page ->
        val item = items[page]
        val distance = abs((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
        val emphasis = lerp(0.94f, 1f, (1f - distance).coerceIn(0f, 1f))
        val alpha = lerp(0.58f, 1f, (1f - distance).coerceIn(0f, 1f))
        Box(
            modifier = Modifier
                .height(472.dp)
                .graphicsLayer {
                    scaleX = emphasis
                    scaleY = emphasis
                    this.alpha = alpha
                },
            contentAlignment = Alignment.Center,
        ) {
            YuJianFishGuideCard(
                item = item,
                variant = if (page == pagerState.currentPage) {
                    if (item.discovered) YuJianFishGuideCardVariant.LIT else YuJianFishGuideCardVariant.UNLIT
                } else {
                    YuJianFishGuideCardVariant.ADJACENT_PREVIEW
                },
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    markUserInteraction()
                    if (page == pagerState.currentPage) {
                        onSpeciesClick(item)
                    } else {
                        coroutineScope.launch { pagerState.animateScrollToPage(page) }
                    }
                },
            )
        }
    }
}

private suspend fun snapshotPagerSelection(
    pagerState: PagerState,
    items: List<FishGuidePresentationItem>,
    onSelectionChanged: (String) -> Unit,
) {
    snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
        .drop(1)
        .filter { (_, scrolling) -> !scrolling }
        .map { (page, _) -> items.getOrNull(page)?.id }
        .distinctUntilChanged()
        .collect { selectedId -> selectedId?.let(onSelectionChanged) }
}
