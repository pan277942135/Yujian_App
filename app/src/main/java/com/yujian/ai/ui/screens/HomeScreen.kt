package com.yujian.ai.ui.screens

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.yujian.ai.R
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.components.AssetImage
import com.yujian.ai.ui.components.RemoteImage
import com.yujian.ai.ui.home.HomeCameraButton
import com.yujian.ai.ui.home.HomeEmptyScene
import com.yujian.ai.ui.home.EmptyHomeLayoutMapping
import com.yujian.ai.ui.home.EmptyHomeFrozenLayoutGeometry
import com.yujian.ai.ui.home.EmptyHomeRuntimeAssets
import com.yujian.ai.ui.home.calculateEmptyHomeLayoutMapping
import com.yujian.ai.ui.home.HomeMotionState
import com.yujian.ai.ui.home.GeminiNormalHomeContent
import com.yujian.ai.ui.home.GeminiHomeLayoutVariant
import com.yujian.ai.ui.adaptive.AdaptiveWidthClass
import com.yujian.ai.ui.adaptive.rememberSafeDrawingInsets
import com.yujian.ai.ui.home.rememberEmptyHomeRuntimeAssets
import com.yujian.ai.ui.home.rememberGeminiNormalHomeRuntimeAssets
import com.yujian.ai.ui.home.rememberHomeMotionState

private val Ink = Color(0xFF18324A)
@Composable
fun HomeScreen(
    nickname: String,
    statistics: CatchStatistics,
    recentCatches: List<RemoteCatch>,
    resolveImageUrl: (String?) -> String?,
    accessToken: String,
    isLoggedIn: Boolean,
    avatarUrl: String?,
    showEmptyState: Boolean,
    isResolving: Boolean = false,
    onIdentify: () -> Unit,
    onAlbumClick: () -> Unit,
    onLoginClick: () -> Unit,
    onSpeciesClick: () -> Unit,
    onCatchesClick: () -> Unit,
    onProfileClick: () -> Unit,
    onCatchClick: (String) -> Unit,
    layoutVariant: GeminiHomeLayoutVariant = GeminiHomeLayoutVariant.ADAPTIVE_V2,
) {
    val view = LocalView.current
    val safeInsets = rememberSafeDrawingInsets()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val homeMotionState = rememberHomeMotionState()
    val emptyRuntimeAssets = rememberEmptyHomeRuntimeAssets(enabled = showEmptyState)
    val geminiRuntimeAssets = rememberGeminiNormalHomeRuntimeAssets(enabled = !showEmptyState)

    DisposableEffect(view) {
        val activity = view.context as? Activity
        activity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = AndroidColor.TRANSPARENT
            window.navigationBarColor = AndroidColor.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.navigationBarDividerColor = AndroidColor.TRANSPARENT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = false
            }
        }
        onDispose { }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (showEmptyState) {
            val mapping = remember(maxWidth, maxHeight, density, safeInsets, layoutDirection) {
                calculateEmptyHomeLayoutMapping(
                    windowWidthDp = maxWidth.value,
                    windowHeightDp = maxHeight.value,
                    density = density.density,
                    fontScale = density.fontScale,
                    safeInsets = safeInsets,
                    layoutDirection = layoutDirection,
                )
            }
            HomeEmptyScene(
                layoutMapping = mapping,
                modifier = Modifier.fillMaxSize(),
                motionState = homeMotionState,
                runtimeAssets = emptyRuntimeAssets,
            )
            EmptyHomeContent(
                modifier = Modifier.fillMaxSize(),
                layoutMapping = mapping,
                isLoggedIn = isLoggedIn,
                avatarUrl = avatarUrl,
                resolveImageUrl = resolveImageUrl,
                accessToken = accessToken,
                onIdentify = onIdentify,
                onAlbumClick = onAlbumClick,
                onLoginClick = onLoginClick,
                onProfileClick = onProfileClick,
                motionState = homeMotionState,
                runtimeAssets = emptyRuntimeAssets,
            )
        } else {
            GeminiNormalHomeContent(
                statistics = statistics,
                recentCatches = recentCatches,
                resolveImageUrl = resolveImageUrl,
                accessToken = accessToken,
                isLoggedIn = isLoggedIn,
                avatarUrl = avatarUrl,
                onIdentify = onIdentify,
                onLoginClick = onLoginClick,
                onSpeciesClick = onSpeciesClick,
                onCatchesClick = onCatchesClick,
                onProfileClick = onProfileClick,
                onCatchClick = onCatchClick,
                isResolving = isResolving,
                motionState = homeMotionState,
                runtimeAssets = geminiRuntimeAssets,
                safeInsets = safeInsets,
                layoutVariant = layoutVariant,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun EmptyHomeContent(
    modifier: Modifier,
    layoutMapping: EmptyHomeLayoutMapping,
    isLoggedIn: Boolean,
    avatarUrl: String?,
    resolveImageUrl: (String?) -> String?,
    accessToken: String,
    onIdentify: () -> Unit,
    onAlbumClick: () -> Unit,
    onLoginClick: () -> Unit,
    onProfileClick: () -> Unit,
    motionState: HomeMotionState,
    runtimeAssets: EmptyHomeRuntimeAssets?,
) {
    val loginClick = rememberDebouncedClick(onLoginClick)
    val albumClick = rememberDebouncedClick(onAlbumClick)

    Box(modifier) {
        val header = layoutMapping.headerBounds
        val compactHeader = layoutMapping.profile.widthClass == AdaptiveWidthClass.COMPACT
        Box(
            Modifier
                .offset(x = header.left.dp, y = header.top.dp)
                .width(header.width.dp)
                .heightIn(min = header.height.dp)
                .padding(horizontal = layoutMapping.profile.horizontalContentInsetDp.dp),
        ) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalArrangement = Arrangement.spacedBy(
                    if (compactHeader) EmptyHomeFrozenLayoutGeometry.COMPACT_HEADER_VERTICAL_GAP_DP.dp else 1.dp,
                ),
            ) {
                Text(
                    text = "渔见",
                    color = Ink.copy(alpha = 0.84f),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Normal,
                    style = if (compactHeader) {
                        TextStyle(
                            lineHeight = EmptyHomeFrozenLayoutGeometry.COMPACT_HEADER_TITLE_LINE_HEIGHT_SP.sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                        )
                    } else {
                        TextStyle.Default
                    },
                )
                Text(
                    text = "拍照收藏每次渔获",
                    color = Ink.copy(alpha = 0.62f),
                    fontSize = 9.sp,
                    letterSpacing = 0.4.sp,
                    style = if (compactHeader) {
                        TextStyle(
                            lineHeight = EmptyHomeFrozenLayoutGeometry.COMPACT_HEADER_SUBTITLE_LINE_HEIGHT_SP.sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                        )
                    } else {
                        TextStyle.Default
                    },
                )
            }
            if (!isLoggedIn) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics {
                            contentDescription = "登录"
                            role = Role.Button
                        }
                        .clickable(onClick = loginClick),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        "登录",
                        color = Ink.copy(alpha = 0.84f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                    )
                    Image(
                        painter = painterResource(R.drawable.login_chevron_v13),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(48.dp)
                        .semantics {
                            contentDescription = "个人中心"
                            role = Role.Button
                        }
                        .clickable(onClick = onProfileClick),
                    contentAlignment = Alignment.Center,
                ) {
                    RemoteImage(
                        url = resolveImageUrl(avatarUrl),
                        authToken = accessToken,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                        placeholder = {
                            Image(
                                painter = painterResource(R.drawable.profile_fallback_v13),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                    )
                }
            }
        }

        Image(
            painter = painterResource(R.drawable.empty_home_title_v2),
            contentDescription = "现在，轮到你记录第一条鱼",
            modifier = Modifier
                .offset(
                    x = layoutMapping.heroBounds.left.dp,
                    // At compact width with an accessibility font scale, the
                    // canonical header line boxes can grow by a few pixels
                    // beyond their 48dp touch container. Keep the frozen
                    // reference mapping unchanged, but give the semantic hero
                    // copy the smallest clearance required to avoid overlap.
                    y = (layoutMapping.heroBounds.top +
                        if (compactHeader && layoutMapping.profile.accessibilityFontScale) 1.dp.value else 0f).dp,
                )
                .width(layoutMapping.heroBounds.width.dp)
                .height(layoutMapping.heroBounds.height.dp),
            contentScale = ContentScale.Fit,
        )

        Box(
            modifier = Modifier
                .offset(
                    x = layoutMapping.promptBounds.left.dp,
                    y = layoutMapping.promptBounds.top.dp,
                )
                .width(layoutMapping.promptBounds.width.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "对准鱼获，拍一张",
                color = Color.White.copy(alpha = 0.94f),
                fontSize = 16.sp,
                style = TextStyle(
                    shadow = Shadow(Color.Black.copy(alpha = 0.28f), blurRadius = 3f),
                ),
            )
        }

        val cameraBounds = layoutMapping.cameraBounds
        HomeCameraButton(
            onClick = onIdentify,
            modifier = Modifier.offset(
                x = cameraBounds.left.dp,
                y = cameraBounds.top.dp,
            ),
            motionState = motionState,
            runtimeAssets = runtimeAssets,
            visualSize = cameraBounds.width.dp,
            touchTargetSize = cameraBounds.width.dp,
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(
                    x = (layoutMapping.albumBounds.left - layoutMapping.windowWidthDp / 2f).dp,
                    y = layoutMapping.albumBounds.top.dp,
                )
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics {
                    contentDescription = "从相册选择照片"
                    role = Role.Button
                }
                .clickable(onClick = albumClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.album_icon_v13),
                contentDescription = null,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = "从相册选择",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 13.sp,
                style = TextStyle(
                    shadow = Shadow(Color.Black.copy(alpha = 0.24f), blurRadius = 2f),
                ),
            )
        }
    }
}

@Composable
private fun rememberDebouncedClick(
    onClick: () -> Unit,
    intervalMs: Long = 500L,
): () -> Unit {
    val latestClick by rememberUpdatedState(onClick)
    var lastClickAt by remember { mutableStateOf(0L) }
    return remember(intervalMs) {
        {
            val now = SystemClock.elapsedRealtime()
            if (now - lastClickAt >= intervalMs) {
                lastClickAt = now
                latestClick()
            }
        }
    }
}
