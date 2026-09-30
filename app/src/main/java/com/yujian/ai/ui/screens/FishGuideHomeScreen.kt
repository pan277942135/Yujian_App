package com.yujian.ai.ui.screens

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.yujian.ai.ui.fishguide.litCount
import com.yujian.ai.ui.fishguide.progressFraction
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
            FishGuideLoadingState()
            return@FishGuideBackground
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = YuJianSpacing.md),
            verticalArrangement = Arrangement.spacedBy(YuJianSpacing.sm),
        ) {
            YuJianBackTitleTopBar(
                title = "鱼鉴",
                onBack = onBack,
                modifier = Modifier.padding(horizontal = YuJianSpacing.xs),
            )

            if (offlinePreview) {
                Text(
                    text = "离线预览中，仍显示当前鱼种档案",
                    style = YuJianTypography.caption.copy(color = YuJianColors.TextSecondary.copy(alpha = 0.82f)),
                    modifier = Modifier.padding(horizontal = YuJianSpacing.md),
                )
            } else if (error != null && species.isNotEmpty()) {
                Text(
                    text = "鱼种档案暂时未更新",
                    style = YuJianTypography.caption.copy(color = YuJianColors.TextSecondary.copy(alpha = 0.82f)),
                    modifier = Modifier.padding(horizontal = YuJianSpacing.md),
                )
            }

            if (species.isNotEmpty()) FishGuideProgressHeader(species)

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
                        .height(472.dp)
                        .testTag("fish_guide_carousel"),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FishGuideProgressHeader(species: List<FishGuideItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = YuJianSpacing.lg),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(YuJianSpacing.xs),
    ) {
        Text(
            text = "已点亮 ${species.litCount()} / ${species.size} 种",
            style = YuJianTypography.caption.copy(color = YuJianColors.TextSecondary),
        )
        FishGuideProgress(
            fraction = species.progressFraction(),
            modifier = Modifier.fillMaxWidth(0.42f),
        )
    }
}

@Composable
private fun FishGuideLoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("正在加载鱼鉴…", style = YuJianTypography.caption)
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
                    failed -> "鱼种档案暂时无法加载"
                    loading -> "正在加载鱼种档案"
                    else -> "暂时还没有鱼种档案"
                },
                style = YuJianTypography.sectionTitle,
                textAlign = TextAlign.Center,
            )
            Text(
                text = error ?: if (loading) "请稍候。" else "连接后会继续读取鱼种资料。",
                style = YuJianTypography.caption,
                textAlign = TextAlign.Center,
            )
            if (failed && !loading) {
                YuJianPrimaryButton(
                    text = "重试",
                    onClick = onRetry,
                    leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                    modifier = Modifier.padding(top = YuJianSpacing.xs),
                )
            }
        }
    }
}

@Composable
private fun rememberFishGuideReduceMotion(): Boolean {
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
