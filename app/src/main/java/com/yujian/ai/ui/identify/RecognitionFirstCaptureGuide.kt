package com.yujian.ai.ui.identify

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect

/**
 * First-use guidance is an overlay over the live CameraX surface. It never
 * replaces the camera route and never invokes capture itself.
 *
 * The camera preview supplies the full-bleed photographic environment. The
 * separately sourced still-photo examples are not present in the repository
 * yet; their missing provenance is recorded as ASSET_SOURCE_BLOCKED instead
 * of using the frozen design screenshots as runtime artwork.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecognitionFirstCaptureGuideOverlay(
    initialPage: RecognitionFirstCaptureGuidePage = RecognitionFirstCaptureGuidePage.P01,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
    onPageChanged: (RecognitionFirstCaptureGuidePage) -> Unit = {},
) {
    val pagerState = rememberPagerState(initialPage = initialPage.index, pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val startInset = safeInsets.calculateStartPadding(layoutDirection)
    val endInset = safeInsets.calculateEndPadding(layoutDirection)

    BackHandler(enabled = true, onBack = onDismiss)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { index -> onPageChanged(RecognitionFirstCaptureGuidePage.fromIndex(index)) }
    }

    AnimatedVisibility(
        visible = true,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(durationMillis = 180)) +
            slideInVertically(animationSpec = tween(durationMillis = 180)) { height -> height / 48 },
    ) {
      Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("recognition-capture-first-guide"),
      ) {
        // This no-op click target is above the camera controls and consumes
        // taps on the non-action portions of the overlay.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x40223D52),
                            Color(0x8A102E40),
                            Color(0xA60B2C3E),
                        ),
                    ),
                )
                .clickable(
                    onClick = {},
                )
                .clearAndSetSemantics { }
                .testTag("recognition-guide-touch-shield"),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = startInset + 10.dp,
                    end = endInset + 10.dp,
                    top = safeInsets.calculateTopPadding(),
                    bottom = safeInsets.calculateBottomPadding(),
                ),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("recognition-guide-pager"),
            ) { pageIndex ->
                val page = RecognitionFirstCaptureGuidePage.fromIndex(pageIndex)
                RecognitionFirstCaptureGuidePageContent(
                    page = page,
                    onDismiss = onDismiss,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            RecognitionFirstCaptureGuidePageIndicator(
                currentPage = pagerState.currentPage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
            ) {
                val isFinalPage = pagerState.currentPage == RecognitionFirstCaptureGuidePage.P02.index
                val fontScale = LocalDensity.current.fontScale
                val stackActions = maxWidth < 380.dp || fontScale >= 1.8f

                @Composable
                fun ActionButtons() {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = onComplete,
                            modifier = Modifier
                                .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                                .testTag("recognition-guide-skip")
                                .semantics { contentDescription = "跳过首次拍照教学" },
                        ) {
                            Text("跳过", color = Color.White, fontSize = 15.sp)
                        }
                        if (pagerState.currentPage > 0) {
                            TextButton(
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                },
                                modifier = Modifier
                                    .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                                    .testTag("recognition-guide-previous")
                                    .semantics { contentDescription = "上一张教学示例" },
                            ) {
                                Text("上一张", color = Color.White, fontSize = 15.sp)
                            }
                        }
                    }
                }

                @Composable
                fun PrimaryAction() {
                    Button(
                        onClick = {
                            if (isFinalPage) {
                                onComplete()
                            } else {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            }
                        },
                        modifier = Modifier
                            .widthIn(min = 132.dp)
                            .sizeIn(minHeight = 48.dp)
                            .testTag("recognition-guide-primary-action")
                            .semantics {
                                contentDescription = if (isFinalPage) "开始拍摄，返回拍摄页面" else "下一张教学示例"
                            },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF7F5ED),
                            contentColor = Color(0xFF163B55),
                        ),
                    ) {
                        Text(
                            text = if (isFinalPage) "开始拍摄 →" else "下一张 →",
                            textAlign = TextAlign.Center,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                if (stackActions) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ActionButtons()
                        PrimaryAction()
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        ActionButtons()
                        PrimaryAction()
                    }
                }
            }
        }
      }
    }
}

@Composable
private fun RecognitionFirstCaptureGuidePageContent(
    page: RecognitionFirstCaptureGuidePage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = recognitionFirstCaptureGuidePageSpec(page)
    val orientationLabel = when (spec.fishOrientation) {
        RecognitionGuideFishOrientation.HORIZONTAL -> "横向鱼体"
        RecognitionGuideFishOrientation.VERTICAL -> "竖直鱼体"
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                    .testTag("recognition-guide-back")
                    .semantics { contentDescription = "返回拍摄页面" },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "拍照识鱼",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "用一张清晰的照片，记录你的每一次相遇",
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Box(Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .semantics(mergeDescendants = true) {
                    selected = true
                    stateDescription = "第${page.index + 1}页，共2页"
                },
        ) {
            Text(
                text = "0${page.index + 1} / 02",
                color = Color(0xFFF4D581),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("recognition-guide-page-number-${page.index + 1}"),
            )
            Text(
                text = spec.title,
                color = Color.White,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .testTag("recognition-guide-page-title-${page.index + 1}"),
            )
            Box(
                modifier = Modifier
                    .padding(top = 7.dp, bottom = 10.dp)
                    .height(1.dp)
                    .fillMaxWidth(0.52f)
                    .background(Color(0xFFE7C96D)),
            )
            Text(
                text = spec.subtitle,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 16.sp,
                lineHeight = 23.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // The live CameraX image remains visible through this region. A
        // distinct still photo is required for the frozen Hero demonstration;
        // do not substitute or crop the page-level Frozen PNG.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .semantics {
                    contentDescription = "正确示范区域，$orientationLabel，鱼体完整，可拍摄"
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                text = "鱼体完整，可拍摄",
                color = Color(0xFFF4D581),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .testTag("recognition-guide-hero-caption-${page.index + 1}"),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            spec.errors.forEachIndexed { index, example ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "避免：${example.label}，$orientationLabel"
                        }
                        .testTag("recognition-guide-error-${page.index + 1}-${index + 1}"),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = "× 避免",
                        color = Color.White.copy(alpha = 0.94f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = example.label,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecognitionFirstCaptureGuidePageIndicator(
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(30.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "教学页码"
                stateDescription = "第${currentPage + 1}页，共3页"
            }
            .testTag("recognition-guide-page-indicator"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val selected = index == currentPage
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .sizeIn(minWidth = 12.dp, minHeight = 12.dp)
                    .semantics {
                        this.selected = selected
                        stateDescription = if (selected) "当前页" else "第${index + 1}页"
                    },
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(
                            color = if (selected) Color(0xFFF4D581) else Color.White.copy(alpha = 0.78f),
                            shape = androidx.compose.foundation.shape.CircleShape,
                        )
                        .then(
                            if (selected) Modifier.sizeIn(minWidth = 10.dp, minHeight = 10.dp)
                            else Modifier.sizeIn(minWidth = 7.dp, minHeight = 7.dp),
                        ),
                )
            }
        }
    }
}
