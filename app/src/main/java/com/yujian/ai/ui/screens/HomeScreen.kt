package com.yujian.ai.ui.screens

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
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
import com.yujian.ai.ui.home.EmptyHomeRuntimeAssets
import com.yujian.ai.ui.home.HomeMotionState
import com.yujian.ai.ui.home.NormalHomeContent
import com.yujian.ai.ui.home.rememberEmptyHomeRuntimeAssets
import com.yujian.ai.ui.home.rememberHomeMotionState

private val Ink = Color(0xFF18324A)
private const val HomeBackground =
    "home_empty_v1_3/assets/background/home_empty_bg_no_bobber.webp"

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
    onIdentify: () -> Unit,
    onAlbumClick: () -> Unit,
    onLoginClick: () -> Unit,
    onSpeciesClick: () -> Unit,
    onCatchesClick: () -> Unit,
    onRecordDaysClick: () -> Unit,
    onProfileClick: () -> Unit,
    onCatchClick: (String) -> Unit,
) {
    val view = LocalView.current
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val homeMotionState = rememberHomeMotionState()
    val emptyRuntimeAssets = rememberEmptyHomeRuntimeAssets(enabled = showEmptyState)

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

    Box(Modifier.fillMaxSize()) {
        if (showEmptyState) {
            HomeEmptyScene(
                modifier = Modifier.fillMaxSize(),
                motionState = homeMotionState,
                runtimeAssets = emptyRuntimeAssets,
            )
        } else {
            AssetImage(
                HomeBackground,
                Modifier.fillMaxSize(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = safeInsets.calculateTopPadding() + 16.dp,
                    bottom = safeInsets.calculateBottomPadding() + 28.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showEmptyState) {
                EmptyHomeContent(
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
                NormalHomeContent(
                    statistics = statistics,
                    recentCatches = recentCatches,
                    resolveImageUrl = resolveImageUrl,
                    accessToken = accessToken,
                    isLoggedIn = isLoggedIn,
                    avatarUrl = avatarUrl,
                    onIdentify = onIdentify,
                    onSpeciesClick = onSpeciesClick,
                    onCatchesClick = onCatchesClick,
                    onRecordDaysClick = onRecordDaysClick,
                    onProfileClick = onProfileClick,
                    onCatchClick = onCatchClick,
                    motionState = homeMotionState,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.EmptyHomeContent(
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

    Box(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 430.dp)
            .padding(horizontal = 24.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = "渔见",
                color = Ink.copy(alpha = 0.84f),
                fontSize = 19.sp,
                fontWeight = FontWeight.Normal,
            )
            Text(
                text = "拍照收藏每次渔获",
                color = Ink.copy(alpha = 0.62f),
                fontSize = 9.sp,
                letterSpacing = 0.4.sp,
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
        painter = painterResource(R.drawable.empty_home_title),
        contentDescription = "现在，轮到你记录第一条鱼",
        modifier = Modifier
            .fillMaxWidth(0.90f)
            .widthIn(max = 360.dp)
            .padding(top = 16.dp),
        contentScale = ContentScale.Fit,
    )

    Spacer(Modifier.weight(1f))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 430.dp)
            .height(190.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "对准鱼获，拍一张",
                color = Color.White.copy(alpha = 0.94f),
                fontSize = 16.sp,
                style = TextStyle(
                    shadow = Shadow(Color.Black.copy(alpha = 0.28f), blurRadius = 3f),
                ),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            HomeCameraButton(
                onClick = onIdentify,
                motionState = motionState,
                runtimeAssets = runtimeAssets,
            )
            Row(
                modifier = Modifier
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
