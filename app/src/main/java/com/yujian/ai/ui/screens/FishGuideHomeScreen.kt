package com.yujian.ai.ui.screens

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yujian.ai.knowledge.FishGuideItem
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.components.YuJianBackTitleTopBar
import com.yujian.ai.ui.designsystem.components.YuJianGlassCard
import com.yujian.ai.ui.designsystem.components.YuJianPrimaryButton
import com.yujian.ai.ui.designsystem.glass.YuJianGlassLevel
import com.yujian.ai.ui.designsystem.spacing.YuJianSpacing
import com.yujian.ai.ui.designsystem.typography.YuJianTypography
import com.yujian.ai.ui.fishguide.FishGuideBackground
import com.yujian.ai.ui.fishguide.FishGuideCarousel
import com.yujian.ai.ui.fishguide.FishGuideProgress
import com.yujian.ai.ui.fishguide.FishGuideResponsiveGeometryResolver
import com.yujian.ai.ui.fishguide.litCount
import com.yujian.ai.ui.fishguide.progressFraction
import com.yujian.ai.ui.fishguide.selectionIndex
import com.yujian.ai.ui.fishguide.toFishGuidePresentation

private const val FISH_GUIDE_PREFERENCES = "fish_guide_home"
private const val DISCOVER_HINT_SHOWN = "carousel_discover_hint_shown"

@Composable
fun FishGuideHomeScreen(
    species: List<FishGuideItem>,
    loading: Boolean,
    offlinePreview: Boolean,
    error: String?,
    resolveAssetUrl: (String?) -> String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSpeciesClick: (FishGuideItem) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) {
        context.getSharedPreferences(FISH_GUIDE_PREFERENCES, Context.MODE_PRIVATE)
    }
    var hintShown by remember(context) {
        mutableStateOf(preferences.getBoolean(DISCOVER_HINT_SHOWN, false))
    }
    var selectedSpeciesId by rememberSaveable { mutableStateOf<String?>(null) }
    val reduceMotion = rememberFishGuideReduceMotion()
    val lifecycleOwner = LocalLifecycleOwner.current
    var screenResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> screenResumed = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> screenResumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    FishGuideBackground {
        if (loading && species.isEmpty()) {
            FishGuideLoadingState(onBack)
            return@FishGuideBackground
        }

        val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
        val layoutDirection = LocalLayoutDirection.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = safeInsets.calculateBottomPadding() + YuJianSpacing.md),
        ) {
            YuJianBackTitleTopBar(
                title = "鱼鉴",
                onBack = onBack,
                modifier = Modifier.padding(horizontal = YuJianSpacing.xs),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = YuJianSpacing.sm)
                    .padding(
                        start = safeInsets.calculateStartPadding(layoutDirection),
                        end = safeInsets.calculateEndPadding(layoutDirection),
                    ),
                verticalArrangement = Arrangement.spacedBy(YuJianSpacing.sm),
            ) {
                if (offlinePreview) {
                    Text(
                        text = "离线内容",
                        style = YuJianTypography.caption.copy(color = YuJianColors.TextSecondary.copy(alpha = 0.82f)),
                        modifier = Modifier.padding(horizontal = YuJianSpacing.md),
                    )
                } else if (error != null && species.isNotEmpty()) {
                    Text(
                        text = "鱼种资料暂时无法更新",
                        style = YuJianTypography.caption.copy(color = YuJianColors.TextSecondary.copy(alpha = 0.82f)),
                        modifier = Modifier.padding(horizontal = YuJianSpacing.md),
                    )
                }

                if (species.isNotEmpty()) FishGuideProgressHeader(species, reduceMotion)

                if (species.isEmpty()) {
                    FishGuideEmptyState(
                        error = error,
                        loading = loading,
                        onRetry = onRetry,
                    )
                } else {
                    FishGuideCarousel(
                        items = species.toFishGuidePresentation(resolveAssetUrl),
                        selectedId = selectedSpeciesId,
                        onSelectionChanged = { selectedSpeciesId = it },
                        onSpeciesClick = { item -> onSpeciesClick(item.source) },
                        discoverHintShown = hintShown,
                        reduceMotion = reduceMotion,
                        screenResumed = screenResumed,
                        onDiscoverHintConsumed = {
                            if (!hintShown) {
                                preferences.edit().putBoolean(DISCOVER_HINT_SHOWN, true).apply()
                                hintShown = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fish_guide_carousel"),
                    )
                    Text(
                        text = "${selectionIndex(species, selectedSpeciesId) + 1} / ${species.size}",
                        style = YuJianTypography.caption.copy(fontSize = 14.sp, lineHeight = 20.sp),
                        color = YuJianColors.DeepInk.copy(alpha = 0.88f),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun FishGuideProgressHeader(species: List<FishGuideItem>, reduceMotion: Boolean) {
    val speciesIds = remember(species) { species.map { it.id } }
    val currentLitCount = species.litCount()
    var previousLitCount by remember(speciesIds) { mutableIntStateOf(currentLitCount) }
    val progressDuration = if (currentLitCount < previousLitCount) 240 else 300
    SideEffect { previousLitCount = currentLitCount }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = YuJianSpacing.lg),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(YuJianSpacing.xs),
    ) {
        Text(
            text = "已点亮 ${species.litCount()} / ${species.size} 种",
            style = YuJianTypography.caption.copy(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = YuJianColors.DeepInk.copy(alpha = 0.88f),
            ),
        )
        FishGuideProgress(
            fraction = species.progressFraction(),
            modifier = Modifier.fillMaxWidth(0.42f),
            reduceMotion = reduceMotion,
            durationMillis = progressDuration,
        )
    }
}

@Composable
private fun FishGuideLoadingState(onBack: () -> Unit) {
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = safeInsets.calculateBottomPadding() + YuJianSpacing.md)
            .semantics { contentDescription = "正在加载鱼鉴资料" },
    ) {
        YuJianBackTitleTopBar(
            title = "鱼鉴",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = YuJianSpacing.xs),
        )
        Column(
            Modifier.fillMaxWidth().padding(
                start = safeInsets.calculateStartPadding(layoutDirection) + YuJianSpacing.lg,
                end = safeInsets.calculateEndPadding(layoutDirection) + YuJianSpacing.lg,
            ),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(YuJianSpacing.xs),
        ) {
            Box(Modifier.fillMaxWidth(0.26f).height(14.dp).background(YuJianColors.MistBlueGray.copy(alpha = 0.30f), RoundedCornerShape(7.dp)))
            Box(Modifier.fillMaxWidth(0.42f).height(6.dp).background(YuJianColors.MistBlueGray.copy(alpha = 0.24f), RoundedCornerShape(4.dp)))
        }
        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(
                start = safeInsets.calculateStartPadding(layoutDirection),
                end = safeInsets.calculateEndPadding(layoutDirection),
                top = 30.dp,
            ),
            contentAlignment = Alignment.Center,
        ) {
            val geometry = FishGuideResponsiveGeometryResolver.resolveHome(maxWidth.value)
            Box(
                Modifier
                    .width(geometry.cardWidthDp.dp)
                    .height(geometry.cardHeightDp.dp)
                    .background(YuJianColors.MistWhite.copy(alpha = 0.62f), RoundedCornerShape(28.dp)),
            )
        }
        Box(
            Modifier.fillMaxWidth().padding(top = YuJianSpacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(60.dp).height(12.dp).background(YuJianColors.MistBlueGray.copy(alpha = 0.24f), RoundedCornerShape(6.dp)))
        }
    }
}

@Composable
private fun FishGuideEmptyState(
    error: String?,
    loading: Boolean,
    onRetry: () -> Unit,
) {
    val failed = error != null
    YuJianGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = YuJianSpacing.md, vertical = YuJianSpacing.md),
        level = YuJianGlassLevel.Light,
        contentPadding = PaddingValues(YuJianSpacing.lg),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(YuJianSpacing.xs),
        ) {
            Text(
                text = when {
                    failed -> "当前无法加载鱼种资料"
                    loading -> "正在加载鱼种资料"
                    else -> "暂无可用鱼种资料"
                },
                style = YuJianTypography.sectionTitle,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (failed) "检查网络后重试" else "稍后可以再次查看鱼种资料。",
                style = YuJianTypography.caption,
                textAlign = TextAlign.Center,
            )
            if (failed && !loading) {
                YuJianPrimaryButton(
                    text = "检查网络后重试",
                    onClick = onRetry,
                    leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                    modifier = Modifier.padding(top = YuJianSpacing.xs),
                )
            }
        }
    }
}

@Composable
internal fun rememberFishGuideReduceMotion(): Boolean {
    val context = LocalContext.current.applicationContext
    var reduceMotion by remember(context) { mutableStateOf(readFishGuideReduceMotion(context)) }

    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduceMotion = readFishGuideReduceMotion(context)
            }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        runCatching { resolver.registerContentObserver(uri, false, observer) }
        val transitionUri = Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE)
        runCatching { resolver.registerContentObserver(transitionUri, false, observer) }
        onDispose { runCatching { resolver.unregisterContentObserver(observer) } }
    }

    return reduceMotion
}

private fun readFishGuideReduceMotion(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) <= 0f ||
        Settings.Global.getFloat(context.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) <= 0f
}.getOrDefault(false)
