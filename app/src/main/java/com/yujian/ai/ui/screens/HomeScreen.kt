package com.yujian.ai.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.R
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.components.AssetImage
import com.yujian.ai.ui.home.HomeCameraButton
import com.yujian.ai.ui.home.NormalHomeContent

private val Ink = Color(0xFF18324A)

@Composable
fun HomeScreen(
    nickname: String,
    statistics: CatchStatistics,
    recentCatches: List<RemoteCatch>,
    resolveImageUrl: (String?) -> String?,
    accessToken: String,
    isLoggedIn: Boolean,
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
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize().clipToBounds()) {
        // Empty and Normal are two states of the same Home world. The Normal
        // addon explicitly depends on this already-frozen public background.
        Image(
            painterResource(R.drawable.empty_home_bg),
            null,
            Modifier.fillMaxSize().graphicsLayer { scaleX = 1.01f; scaleY = 1.01f },
            contentScale = ContentScale.Crop,
        )
        if (showEmptyState) EmptyHomeMotion(Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxSize().padding(top = topInset + 18.dp, bottom = bottomInset + 14.dp),
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
private fun EmptyHomeContent(
    isLoggedIn: Boolean,
    onIdentify: () -> Unit,
    onAlbumClick: () -> Unit,
    onLoginClick: () -> Unit,
    onProfileClick: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text(
            "渔见",
            color = Ink.copy(alpha = 0.86f),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        if (!isLoggedIn) {
            Text(
                "登录 >",
                color = Ink.copy(alpha = 0.86f),
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable(onClick = onLoginClick)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        } else {
            Image(
                painterResource(R.drawable.profile_button_glass),
                "个人中心",
                Modifier.size(54.dp).align(Alignment.CenterEnd).clickable(onClick = onProfileClick),
            )
        }
    }
    Image(
        painter = painterResource(R.drawable.empty_home_title),
        contentDescription = "现在，轮到你记录第一条鱼",
        modifier = Modifier.fillMaxWidth(0.84f).padding(top = 22.dp),
        contentScale = ContentScale.Fit,
    )
    Spacer(Modifier.weight(1f))
    Text(
        "对准鱼获，拍一张",
        color = Color.White.copy(alpha = 0.94f),
        fontSize = 16.sp,
        modifier = Modifier.padding(bottom = 10.dp),
    )
    HomeCameraButton(onClick = onIdentify)
    Text(
        "从相册选择",
        color = Color.White.copy(alpha = 0.92f),
        fontSize = 13.sp,
        modifier = Modifier.clickable(onClick = onAlbumClick).padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

/** Shared Empty Home lake motion from home_motion V1.1. */
@Composable
private fun EmptyHomeMotion(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "empty-home-motion")
    val floatY by transition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(4500), RepeatMode.Reverse),
        label = "bobber-y",
    )
    val floatRotation by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4500), RepeatMode.Reverse),
        label = "bobber-rotation",
    )
    val rippleScale by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(5000), RepeatMode.Reverse),
        label = "ripple-scale",
    )
    val rippleAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(tween(5000), RepeatMode.Reverse),
        label = "ripple-alpha",
    )

    BoxWithConstraints(modifier) {
        val anchorX = maxWidth * 0.68f
        val anchorY = maxHeight * 0.62f

        AssetImage(
            "home_motion/float/ripple_outer.png",
            Modifier
                .offset(x = anchorX - 56.dp, y = anchorY - 10.dp)
                .size(112.dp, 34.dp)
                .graphicsLayer { scaleX = rippleScale; scaleY = rippleScale; alpha = rippleAlpha },
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
        )
        AssetImage(
            "home_motion/float/ripple_inner.png",
            Modifier
                .offset(x = anchorX - 48.dp, y = anchorY - 8.dp)
                .size(96.dp, 30.dp)
                .graphicsLayer { scaleX = rippleScale; scaleY = rippleScale; alpha = rippleAlpha * 0.82f },
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
        )
        AssetImage(
            "home_motion/float/bobber_shadow.png",
            Modifier
                .offset(x = anchorX - 29.dp, y = anchorY + 4.dp)
                .size(58.dp, 18.dp)
                .graphicsLayer { alpha = 0.6f },
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
        )
        AssetImage(
            "home_motion/float/bobber.png",
            Modifier
                .offset(x = anchorX - 25.dp, y = anchorY - 68.dp)
                .size(50.dp, 90.dp)
                .graphicsLayer { translationY = floatY; rotationZ = floatRotation },
            contentDescription = "湖面鱼漂",
            contentScale = ContentScale.Fit,
        )
    }
}
