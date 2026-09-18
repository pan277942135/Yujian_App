package com.yujian.ai.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.R
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.components.AssetImage
import com.yujian.ai.ui.home.HomeCameraButton
import com.yujian.ai.ui.home.HomeEmptyScene
import com.yujian.ai.ui.home.NormalHomeContent

private val Ink = Color(0xFF18324A)
private val HomeBackground = "home_empty_v1_2/assets/background/home_empty_bg_no_bobber.webp"

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
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    Box(Modifier.fillMaxSize()) {
        if (showEmptyState) {
            HomeEmptyScene(Modifier.fillMaxSize())
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
                    onIdentify = onIdentify,
                    onAlbumClick = onAlbumClick,
                    onLoginClick = onLoginClick,
                    onProfileClick = onProfileClick,
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
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.EmptyHomeContent(
    isLoggedIn: Boolean,
    onIdentify: () -> Unit,
    onAlbumClick: () -> Unit,
    onLoginClick: () -> Unit,
    onProfileClick: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text(
            text = "渔见",
            color = Ink.copy(alpha = 0.84f),
            fontSize = 19.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        if (!isLoggedIn) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable(onClick = onLoginClick)
                    .padding(horizontal = 2.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("登录", color = Ink.copy(alpha = 0.84f), fontSize = 14.sp, fontWeight = FontWeight.Normal)
                Image(
                    painter = painterResource(R.drawable.login_chevron_v12),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            Image(
                painterResource(R.drawable.profile_button_glass),
                "个人中心",
                Modifier.size(48.dp).align(Alignment.CenterEnd).clickable(onClick = onProfileClick),
            )
        }
    }
    Image(
        painter = painterResource(R.drawable.empty_home_title),
        contentDescription = "现在，轮到你记录第一条鱼",
        modifier = Modifier.fillMaxWidth(0.84f).padding(top = 16.dp),
        contentScale = ContentScale.Fit,
    )
    Spacer(Modifier.weight(1f))
    Text(
        text = "对准鱼获，拍一张",
        color = Color.White.copy(alpha = 0.94f),
        fontSize = 16.sp,
        style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.28f), blurRadius = 3f)),
        modifier = Modifier.padding(bottom = 8.dp),
    )
    HomeCameraButton(onClick = onIdentify)
    Row(
        modifier = Modifier
            .clickable(onClick = onAlbumClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.album_icon_v12),
            contentDescription = null,
            modifier = Modifier.size(17.dp),
        )
        Text(
            text = "从相册选择",
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 13.sp,
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.24f), blurRadius = 2f)),
        )
    }
}
