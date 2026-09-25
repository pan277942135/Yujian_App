package com.yujian.ai.ui.fishguide

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.yujian.ai.ui.designsystem.components.YuJianFishGuideCard
import com.yujian.ai.ui.designsystem.components.YuJianFishGuideCardVariant

@Composable
fun FishGuideCarousel(
    items: List<FishGuidePresentationItem>,
    selectedId: String?,
    onSelectionChanged: (String) -> Unit,
    onSpeciesClick: (FishGuidePresentationItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val selectedIndex = selectionIndex(items.map { it.source }, selectedId).coerceIn(0, items.lastIndex)
    val pagerState = rememberPagerState(initialPage = selectedIndex, pageCount = { items.size })
    LaunchedEffect(items.map { it.id }) {
        val target = selectionIndex(items.map { it.source }, selectedId).coerceIn(0, items.lastIndex)
        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }
    LaunchedEffect(pagerState, items.map { it.id }) {
        snapshotPagerSelection(pagerState, items, onSelectionChanged)
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxWidth(),
        pageSize = PageSize.Fixed(284.dp),
        contentPadding = PaddingValues(horizontal = 36.dp),
        pageSpacing = 12.dp,
        verticalAlignment = Alignment.CenterVertically,
    ) { page ->
        val item = items[page]
        val distance = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).let { kotlin.math.abs(it) }
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
                onClick = { onSpeciesClick(item) },
            )
        }
    }
}

private suspend fun snapshotPagerSelection(
    pagerState: PagerState,
    items: List<FishGuidePresentationItem>,
    onSelectionChanged: (String) -> Unit,
) {
    snapshotFlow { pagerState.currentPage }.collect { page ->
        items.getOrNull(page)?.id?.let(onSelectionChanged)
    }
}
