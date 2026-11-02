package com.yujian.ai.ui.identify

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect

/**
 * First-use guidance is shown only after the CameraX preview is ready. Approved
 * standalone photos provide the guide artwork; the frozen full-page reference
 * PNGs are never used as runtime images. Completing the guide returns to the
 * canonical camera route and does not trigger the shutter.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecognitionFirstCaptureGuideOverlay(
    initialPage: RecognitionFirstCaptureGuidePage = RecognitionFirstCaptureGuidePage.P01,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
    onPageChanged: (RecognitionFirstCaptureGuidePage) -> Unit = {},
) {
    val pagerState = rememberPagerState(initialPage = initialPage.index, pageCount = { 3 })
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
        val currentPageSpec = recognitionFirstCaptureGuidePageSpec(
            RecognitionFirstCaptureGuidePage.fromIndex(pagerState.currentPage),
        )
        Image(
            painter = painterResource(currentPageSpec.backgroundAsset),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().testTag("recognition-guide-background-${pagerState.currentPage + 1}"),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x38051624),
                        0.12f to Color(0x10051624),
                        0.28f to Color.Transparent,
                        0.46f to Color(0x12051624),
                        0.70f to Color(0x28051624),
                        1f to Color(0x3E051624),
                    ),
                )
                .testTag("recognition-guide-ambient-gradient"),
        )
        // This no-op click target is above the camera controls and consumes
        // taps on the non-action portions of the overlay.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .clickable(
                    onClick = {},
                )
                .clearAndSetSemantics { }
                .testTag("recognition-guide-touch-shield"),
        )

        // The ZIP's lower-control gradient is restored with its original stops.
        // It sits behind the controls while the live CameraX preview remains visible.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(188.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color(0x18030E17),
                        1f to Color(0x3A030E17),
                    ),
                )
                .testTag("recognition-guide-bottom-gradient"),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = startInset + 16.dp,
                    end = endInset + 16.dp,
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
                val isFinalPage = pagerState.currentPage == RecognitionFirstCaptureGuidePage.P03.index
                val fontScale = LocalDensity.current.fontScale
                val fullWidthDp = maxWidth.value + startInset.value + endInset.value + 32f
                val stackActions = shouldStackRecognitionGuideActions(
                    availableWidthDp = fullWidthDp,
                    fontScale = fontScale,
                )

                @Composable
                fun ActionButtons() {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = onComplete,
                            modifier = Modifier
                                .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                                .testTag("recognition-guide-skip")
                                .semantics { contentDescription = "跳过首次拍照教学" },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = "跳过",
                                color = Color.White,
                                fontSize = 15.sp,
                                textDecoration = TextDecoration.Underline,
                            )
                        }
                        if (pagerState.currentPage > 0) {
                            Text(
                                text = "|",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                            TextButton(
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                },
                                modifier = Modifier
                                    .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                                    .testTag("recognition-guide-previous")
                                    .semantics { contentDescription = "上一张教学示例" },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = "上一张",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    textDecoration = TextDecoration.Underline,
                                )
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
                            .height(48.dp)
                            .widthIn(min = 146.dp)
                            .shadow(elevation = 6.dp, shape = CircleShape)
                            .testTag("recognition-guide-primary-action")
                            .semantics {
                                contentDescription = if (isFinalPage) "开始拍摄，返回拍摄页面" else "下一张教学示例"
                            },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF7F5EE),
                            contentColor = Color(0xFF102D44),
                        ),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = if (isFinalPage) "开始拍摄 →" else "下一张 →",
                            textAlign = TextAlign.Center,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                if (stackActions) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ActionButtons()
                        }
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
    val isVerticalFish = spec.fishOrientation == RecognitionGuideFishOrientation.VERTICAL

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .padding(bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .padding(top = 4.dp),
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
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "用一张清晰的照片，记录你的每一次相遇",
                        color = Color.White.copy(alpha = 0.90f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                Box(Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .heightIn(min = 138.dp, max = if (isVerticalFish) 236.dp else 190.dp)
                    .aspectRatio(if (isVerticalFish) 0.82f else 1.55f)
                    .semantics {
                        contentDescription = "正确示范区域，$orientationLabel，鱼体完整，可拍摄"
                    }
                    .testTag("recognition-guide-hero-${page.index + 1}"),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(spec.heroAsset),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .border(0.75.dp, Color(0x66FFFFFF), RoundedCornerShape(10.dp))
                        .testTag("recognition-guide-hero-image-${page.index + 1}"),
                    contentScale = ContentScale.Fit,
                )
                CornerFrameMarkers(
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 2.5f,
                    cornerLength = 26f,
                    color = Color.White.copy(alpha = 0.95f),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 4.dp)
                    .semantics(mergeDescendants = true) {
                        selected = true
                        stateDescription = "第${page.index + 1}页，共3页"
                    },
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = "0${page.index + 1} / 03",
                    color = Color(0xFFF4D581),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.testTag("recognition-guide-page-number-${page.index + 1}"),
                )
                Spacer(Modifier.height(2.dp))
                Column(
                    modifier = Modifier.wrapContentSize(),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = spec.title,
                        color = Color.White,
                        fontSize = 25.sp,
                        lineHeight = 31.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("recognition-guide-page-title-${page.index + 1}"),
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp, bottom = 6.dp)
                            .height(1.5.dp)
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    0f to Color(0xFFE7C96D),
                                    0.85f to Color(0xFFE7C96D),
                                    1f to Color(0x66E7C96D),
                                ),
                            ),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = spec.subtitle,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 14.5.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp),
            ) {
                val wrapToTwoColumns = maxWidth < 352.dp
                if (wrapToTwoColumns) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            spec.errors.take(2).forEachIndexed { index, example ->
                                Box(Modifier.weight(1f)) {
                                    AvoidanceExampleCard(
                                        example = example,
                                        imageRes = spec.errorAssets[index],
                                        pageIndex = page.index,
                                        cardIndex = index,
                                    )
                                }
                            }
                        }
                        if (spec.errors.size > 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(Modifier.weight(0.5f)) {
                                    AvoidanceExampleCard(
                                        example = spec.errors[2],
                                        imageRes = spec.errorAssets[2],
                                        pageIndex = page.index,
                                        cardIndex = 2,
                                    )
                                }
                                Spacer(Modifier.weight(0.5f))
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        spec.errors.forEachIndexed { index, example ->
                            Box(Modifier.weight(1f)) {
                                AvoidanceExampleCard(
                                    example = example,
                                    imageRes = spec.errorAssets[index],
                                    pageIndex = page.index,
                                    cardIndex = index,
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = 56.dp)
                        .height(1.dp)
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0x80F4D581)))),
                )
                Spacer(Modifier.width(8.dp))
                VectorFishOutlineIcon(Modifier.size(width = 22.dp, height = 12.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "鱼体完整，可拍摄",
                    color = Color(0xFFF4D581),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp,
                    modifier = Modifier.testTag("recognition-guide-hero-caption-${page.index + 1}"),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = 56.dp)
                        .height(1.dp)
                        .background(Brush.horizontalGradient(listOf(Color(0x80F4D581), Color.Transparent))),
                )
            }
        }
    }
}

@Composable
private fun AvoidanceExampleCard(
    example: RecognitionFirstCaptureGuideErrorExample,
    @androidx.annotation.DrawableRes imageRes: Int,
    pageIndex: Int,
    cardIndex: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "避免：${example.label}，${if (example.orientation == RecognitionGuideFishOrientation.HORIZONTAL) "横向鱼体" else "竖直鱼体"}"
            }
            .testTag("recognition-guide-error-${pageIndex + 1}-${cardIndex + 1}"),
        horizontalAlignment = Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.28f)
                .clip(RoundedCornerShape(8.dp))
                .border(0.75.dp, Color(0x55FFFFFF), RoundedCornerShape(8.dp))
                .testTag("recognition-guide-error-image-${pageIndex + 1}-${cardIndex + 1}"),
        ) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            AvoidanceCancelMicroIcon(Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = "避免",
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            text = example.label,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun AvoidanceCancelMicroIcon(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White.copy(alpha = 0.95f),
    crossColor: Color = Color(0xFF10283A),
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = backgroundColor, radius = radius, center = center)
        val mark = radius * 0.40f
        val stroke = 1.35.dp.toPx()
        drawLine(
            color = crossColor,
            start = Offset(center.x - mark, center.y - mark),
            end = Offset(center.x + mark, center.y + mark),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = crossColor,
            start = Offset(center.x - mark, center.y + mark),
            end = Offset(center.x + mark, center.y - mark),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun VectorFishOutlineIcon(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFF4D581),
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val stroke = 1.25.dp.toPx()
        val path = Path().apply {
            moveTo(width, height * 0.18f)
            cubicTo(width * 0.68f, height * 0.86f, width * 0.30f, height * 0.92f, width * 0.04f, height * 0.50f)
            cubicTo(width * 0.30f, height * 0.08f, width * 0.68f, height * 0.14f, width, height * 0.82f)
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawCircle(color = color, radius = 1.dp.toPx(), center = Offset(width * 0.22f, height * 0.48f))
    }
}

@Composable
private fun CornerFrameMarkers(
    modifier: Modifier = Modifier,
    strokeWidth: Float = 2.5f,
    cornerLength: Float = 26f,
    color: Color = Color.White,
) {
    val density = LocalDensity.current
    val strokePx = with(density) { strokeWidth.dp.toPx() }
    val lengthPx = with(density) { cornerLength.dp.toPx() }
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        drawLine(color, Offset(0f, 0f), Offset(lengthPx, 0f), strokeWidth = strokePx, cap = StrokeCap.Round)
        drawLine(color, Offset(0f, 0f), Offset(0f, lengthPx), strokeWidth = strokePx, cap = StrokeCap.Round)
        drawLine(color, Offset(width, 0f), Offset(width - lengthPx, 0f), strokeWidth = strokePx, cap = StrokeCap.Round)
        drawLine(color, Offset(width, 0f), Offset(width, lengthPx), strokeWidth = strokePx, cap = StrokeCap.Round)
        drawLine(color, Offset(0f, height), Offset(lengthPx, height), strokeWidth = strokePx, cap = StrokeCap.Round)
        drawLine(color, Offset(0f, height), Offset(0f, height - lengthPx), strokeWidth = strokePx, cap = StrokeCap.Round)
        drawLine(color, Offset(width, height), Offset(width - lengthPx, height), strokeWidth = strokePx, cap = StrokeCap.Round)
        drawLine(color, Offset(width, height), Offset(width, height - lengthPx), strokeWidth = strokePx, cap = StrokeCap.Round)
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
                    .padding(horizontal = 4.dp)
                    .size(16.dp)
                    .semantics {
                        this.selected = selected
                        stateDescription = if (selected) "当前页" else "第${index + 1}页"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = if (selected) {
                        Modifier.size(8.dp).background(Color(0xFFF4D581), CircleShape)
                    } else {
                        Modifier.size(8.dp).border(1.dp, Color.White.copy(alpha = 0.88f), CircleShape)
                    },
                )
            }
        }
    }
}
