package com.yujian.ai.ui.recorddetail

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yujian.ai.ui.adaptive.rememberAdaptiveLayoutProfile
import com.yujian.ai.ui.adaptive.rememberSafeDrawingInsets
import com.yujian.ai.catches.BsideStatus
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.components.YuJianBackAction
import com.yujian.ai.ui.designsystem.components.YuJianBackTitleTopBar
import com.yujian.ai.ui.designsystem.components.YuJianGlassCard
import com.yujian.ai.ui.designsystem.components.YuJianIconAction
import com.yujian.ai.ui.designsystem.components.YuJianIconActionFamily
import com.yujian.ai.ui.designsystem.components.YuJianIconActionTone
import com.yujian.ai.ui.designsystem.components.YuJianTextAction
import com.yujian.ai.ui.designsystem.components.YuJianPrimaryButton
import com.yujian.ai.ui.designsystem.radius.YuJianRadius
import com.yujian.ai.ui.designsystem.spacing.YuJianSpacing
import com.yujian.ai.ui.designsystem.typography.YuJianTypography
import com.yujian.ai.ui.screens.BgContentSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val FishRecordDetailPreferences = "fish_record_detail_v1"

@Composable
fun FishRecordDetailScreen(
    uiState: FishRecordDetailUiState,
    initialSection: String = "",
    imageUrlFor: (RemoteCatch) -> String?,
    bsideUrlFor: (RemoteCatch) -> String?,
    accessToken: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenFishGuide: (RemoteCatch) -> Unit,
    onShare: (RemoteCatch) -> Unit,
    onEditRecord: (RemoteCatch) -> Unit,
    onAddMedia: (RemoteCatch) -> Unit,
    onContinuePhoto: (RemoteCatch) -> Unit,
    onRecordVideo: (RemoteCatch) -> Unit,
    onGenerateMemory: ((RemoteCatch) -> Unit)?,
    onRefreshBsideStatus: suspend (String) -> Boolean,
) {
    val configuration = LocalConfiguration.current
    val safeInsets = rememberSafeDrawingInsets()
    val adaptiveProfile = rememberAdaptiveLayoutProfile(
        configuration.screenWidthDp.dp,
        configuration.screenHeightDp.dp,
    )
    val geometry = FishRecordDetailGeometryResolver.resolve(adaptiveProfile)
    Box(modifier = Modifier.fillMaxSize()) {
        BgContentSurface()
        when (uiState) {
            FishRecordDetailUiState.Loading -> DetailStateFrame(onBack) {
                LoadingDetailState(geometry.heroHeightDp.dp)
            }
            FishRecordDetailUiState.Empty -> DetailStateFrame(onBack) {
                DetailMessage(
                    message = "这条鱼获记录已不可用",
                    actionLabel = "返回我的鱼获",
                    onAction = onBack,
                )
            }
            is FishRecordDetailUiState.Error -> DetailStateFrame(onBack) {
                DetailMessage(
                    message = "暂时无法打开这条鱼获",
                    actionLabel = "重新加载",
                    onAction = onRetry,
                    secondaryLabel = "返回我的鱼获",
                    onSecondary = onBack,
                )
            }
            is FishRecordDetailUiState.Success -> {
                val record = uiState.record
                val context = LocalContext.current
                val coroutineScope = rememberCoroutineScope()
                val lifecycleOwner = LocalLifecycleOwner.current
                val bsideUrl = bsideUrlFor(record)
                val listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = if (initialSection.equals("memory", true)) 2 else 0,
                )
                val revealPreferences = remember(context) {
                    context.getSharedPreferences(FishRecordDetailPreferences, Context.MODE_PRIVATE)
                }
                val revealKey = remember(record.id) { "first_b_reveal_done:${record.id}" }
                var firstRevealDone by remember(record.id) {
                    mutableStateOf(revealPreferences.getBoolean(revealKey, false))
                }
                var showingBside by remember(record.id) { mutableStateOf(false) }
                var autoRevealStarted by remember(record.id) { mutableStateOf(false) }
                var heroVisible by remember(record.id) { mutableStateOf(false) }
                var pageResumed by remember(record.id, lifecycleOwner) {
                    mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                }
                var bsideImageLoaded by remember(record.id, bsideUrl) { mutableStateOf(false) }
                var bsideUnavailable by remember(record.id, bsideUrl) {
                    mutableStateOf(record.bsideStatus == BsideStatus.READY && bsideUrl.isNullOrBlank())
                }
                var bsideReloadToken by remember(record.id) { mutableStateOf(0) }

                DisposableEffect(lifecycleOwner, record.id) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> pageResumed = true
                            Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> pageResumed = false
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                LaunchedEffect(listState, record.id) {
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.index == 0 } }
                        .distinctUntilChanged()
                        .collect { heroVisible = it }
                }

                LaunchedEffect(record.id, record.bsideStatus, bsideUrl) {
                    if (record.bsideStatus != BsideStatus.READY) {
                        showingBside = false
                        bsideUnavailable = false
                        bsideImageLoaded = false
                    } else if (bsideUrl.isNullOrBlank()) {
                        showingBside = false
                        bsideUnavailable = true
                        bsideImageLoaded = false
                    } else {
                        bsideUnavailable = false
                        bsideImageLoaded = false
                    }
                }

                LaunchedEffect(
                    record.id,
                    record.bsideStatus,
                    bsideUrl,
                    firstRevealDone,
                    autoRevealStarted,
                    heroVisible,
                    pageResumed,
                    bsideUnavailable,
                ) {
                    if (
                        heroVisible &&
                        pageResumed &&
                        !bsideUnavailable &&
                        FishRecordDetailPresentation.shouldAutoRevealBside(
                            status = record.bsideStatus,
                            hasAsset = !bsideUrl.isNullOrBlank(),
                            firstRevealDone = firstRevealDone,
                        ) &&
                        !autoRevealStarted
                    ) {
                        // The first A-side frame is presented before this effect changes the Hero.
                        autoRevealStarted = true
                        showingBside = true
                    }
                }

                LaunchedEffect(record.id, showingBside, bsideImageLoaded, heroVisible, pageResumed, firstRevealDone) {
                    if (showingBside && bsideImageLoaded && heroVisible && pageResumed && !firstRevealDone) {
                        withFrameNanos { }
                        if (showingBside && bsideImageLoaded && heroVisible && pageResumed) {
                            revealPreferences.edit().putBoolean(revealKey, true).apply()
                            firstRevealDone = true
                        }
                    }
                }

                LaunchedEffect(record.id, record.bsideStatus, pageResumed) {
                    if (record.bsideStatus == BsideStatus.GENERATING && pageResumed) {
                        for (attempt in 0 until 8) {
                            delay(2_000)
                            if (onRefreshBsideStatus(record.id)) break
                        }
                    }
                }

                Column(Modifier.fillMaxSize()) {
                    FishRecordDetailTopBar(
                        onBack = onBack,
                        onOpenFishGuide = { onOpenFishGuide(record) },
                        onShare = { onShare(record) },
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth()
                            .padding(start = safeInsets.start, end = safeInsets.end),
                        state = listState,
                        contentPadding = PaddingValues(
                            start = geometry.horizontalMarginDp.dp,
                            end = geometry.horizontalMarginDp.dp,
                            top = YuJianSpacing.xs,
                            bottom = geometry.bottomContentPaddingDp.dp + safeInsets.bottom,
                        ),
                        verticalArrangement = Arrangement.spacedBy(geometry.sectionGapDp.dp),
                    ) {
                    item {
                        FishRecordHeroCard(
                            record = record,
                            imageUrl = imageUrlFor(record),
                            bsideUrl = bsideUrl,
                            showBside = showingBside && !bsideUnavailable,
                            canFlip = record.bsideStatus == BsideStatus.READY &&
                                !bsideUrl.isNullOrBlank() &&
                                !bsideUnavailable,
                            bsideReloadToken = bsideReloadToken,
                            accessToken = accessToken,
                            heroHeight = geometry.heroHeightDp.dp,
                            onEdit = { onEditRecord(record) },
                            onFlip = { showingBside = !showingBside },
                            onBsideLoadResult = { loaded ->
                                if (showingBside) {
                                    if (loaded) {
                                        bsideImageLoaded = true
                                    } else {
                                        bsideImageLoaded = false
                                        bsideUnavailable = true
                                        showingBside = false
                                    }
                                }
                            },
                        )
                    }
                    item {
                        AboutCatchSection(
                            story = FishRecordDetailPresentation.story(record),
                            onEdit = { onEditRecord(record) },
                        )
                    }
                    item {
                        FishMediaPicker(
                            media = record.memoryMedia,
                            onAddPhotosOrVideos = { onAddMedia(record) },
                            onContinuePhoto = { onContinuePhoto(record) },
                            onRecordVideo = { onRecordVideo(record) },
                            fontScale = adaptiveProfile.fontScale,
                        )
                    }
                    item {
                        FishMemorySection(
                            record = record,
                            generationEnabled = onGenerateMemory != null,
                            bsideUnavailable = bsideUnavailable,
                            onGenerateMemory = onGenerateMemory?.let { callback -> { callback(record) } },
                            onRetryBside = {
                                if (!bsideUrl.isNullOrBlank()) {
                                    bsideUnavailable = false
                                    bsideReloadToken += 1
                                    showingBside = true
                                }
                                coroutineScope.launch { onRefreshBsideStatus(record.id) }
                            },
                        )
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun DetailStateFrame(onBack: () -> Unit, content: @Composable () -> Unit) {
    val safeInsets = rememberSafeDrawingInsets()
    Column(modifier = Modifier.fillMaxSize()) {
        YuJianBackTitleTopBar(title = "鱼获详情", onBack = onBack)
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .padding(start = safeInsets.start + YuJianSpacing.md, end = safeInsets.end + YuJianSpacing.md, bottom = safeInsets.bottom),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun LoadingDetailState(heroHeight: Dp) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(YuJianSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(heroHeight)
                .background(YuJianColors.MistBlueGray.copy(alpha = 0.18f), YuJianRadius.heroCard),
        )
        YuJianGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(YuJianSpacing.xs)) {
                Box(
                    modifier = Modifier.fillMaxWidth(0.42f).height(18.dp)
                        .background(YuJianColors.MistBlueGray.copy(alpha = 0.24f), YuJianRadius.pill),
                )
                Box(
                    modifier = Modifier.fillMaxWidth().height(14.dp)
                        .background(YuJianColors.MistBlueGray.copy(alpha = 0.16f), YuJianRadius.pill),
                )
            }
        }
        Text("正在打开这条鱼获…", style = YuJianTypography.caption, color = YuJianColors.MistBlueGray)
    }
}

@Composable
private fun AboutCatchSection(story: String?, onEdit: () -> Unit) {
    YuJianGlassCard(
        modifier = Modifier.fillMaxWidth(),
        level = com.yujian.ai.ui.designsystem.glass.YuJianGlassLevel.Strong,
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("关于这次鱼获", modifier = Modifier.weight(1f), style = YuJianTypography.sectionTitle)
            YuJianIconAction(
                icon = Icons.Rounded.Edit,
                contentDescription = "编辑鱼获信息",
                onClick = onEdit,
                family = YuJianIconActionFamily.UTILITY,
                tone = YuJianIconActionTone.ON_LIGHT,
            )
        }
        story?.takeIf(String::isNotBlank)?.let { note ->
            Text(
                text = note,
                modifier = Modifier.fillMaxWidth().padding(top = YuJianSpacing.xs),
                style = YuJianTypography.body,
                color = YuJianColors.DeepInk,
            )
        }
    }
}

@Composable
private fun FishRecordDetailTopBar(
    onBack: () -> Unit,
    onOpenFishGuide: () -> Unit,
    onShare: () -> Unit,
) {
    val safe = rememberSafeDrawingInsets()
    Box(
        modifier = Modifier.fillMaxWidth()
            .padding(top = safe.top, start = safe.start + 8.dp, end = safe.end + 8.dp)
            .heightIn(min = 64.dp),
    ) {
        YuJianBackAction(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            text = "鱼获详情",
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 88.dp),
            style = YuJianTypography.sectionTitle.copy(fontSize = 21.sp, lineHeight = 27.sp),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FishRecordDetailTopAction(Icons.Rounded.MenuBook, "鱼鉴", onOpenFishGuide)
            FishRecordDetailTopAction(Icons.Rounded.Share, "分享", onShare)
        }
    }
}

@Composable
private fun FishRecordDetailTopAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(48.dp).heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = YuJianColors.DeepLakeBlue,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = YuJianTypography.caption.copy(fontSize = 10.sp, lineHeight = 14.sp),
            color = YuJianColors.DeepLakeBlue,
            maxLines = 1,
        )
    }
}

@Composable
private fun DetailMessage(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(YuJianSpacing.sm),
    ) {
        Text(
            message,
            style = YuJianTypography.body,
            color = YuJianColors.DeepInk,
            textAlign = TextAlign.Center,
        )
        YuJianPrimaryButton(
            text = actionLabel,
            onClick = onAction,
            modifier = Modifier.padding(horizontal = YuJianSpacing.xs),
        )
        if (secondaryLabel != null && onSecondary != null) {
            YuJianTextAction(text = secondaryLabel, onClick = onSecondary)
        }
    }
}
