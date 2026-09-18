package com.yujian.ai.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.R
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.components.AssetImage
import com.yujian.ai.ui.components.RemoteImage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF18324A)
private val LightInk = Color.White.copy(alpha = 0.92f)
private const val GUEST_AVATAR = "home_normal_v1_2/assets/avatar/guest_avatar.png"

@Composable
fun NormalHomeContent(
    statistics: CatchStatistics,
    recentCatches: List<RemoteCatch>,
    resolveImageUrl: (String?) -> String?,
    accessToken: String,
    isLoggedIn: Boolean,
    avatarUrl: String?,
    onIdentify: () -> Unit,
    onSpeciesClick: () -> Unit,
    onCatchesClick: () -> Unit,
    onRecordDaysClick: () -> Unit,
    onProfileClick: () -> Unit,
    onCatchClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = rememberHomeMotionRunning()
    val density = LocalDensity.current
    val cardScale = remember { Animatable(1f) }
    val cardY = remember { Animatable(0f) }
    val recent = remember(recentCatches) {
        recentCatches.maxByOrNull { catchTimestamp(it) }
    }

    LaunchedEffect(running, recent?.id) {
        if (!running || recent == null) {
            cardScale.snapTo(1f)
            cardY.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            coroutineScope {
                launch {
                    cardScale.animateTo(1f, keyframes {
                        durationMillis = 6000
                        1.008f at 3000
                    })
                }
                launch {
                    cardY.animateTo(0f, keyframes {
                        durationMillis = 6000
                        -2f at 3000
                    })
                }
            }
        }
    }

    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("我的鱼获", color = Ink.copy(alpha = 0.9f), fontSize = 21.sp, fontWeight = FontWeight.Normal)
            if (isLoggedIn) {
                RemoteImage(
                    url = resolveImageUrl(avatarUrl),
                    authToken = accessToken,
                    modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onProfileClick),
                    contentDescription = "个人中心",
                    contentScale = ContentScale.Crop,
                ) {
                    Image(
                        painter = painterResource(R.drawable.profile_login_v12),
                        contentDescription = "个人中心",
                        modifier = Modifier.fillMaxSize().padding(7.dp),
                    )
                }
            } else {
                AssetImage(
                    GUEST_AVATAR,
                    Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onProfileClick),
                    contentDescription = "登录或注册",
                    contentScale = ContentScale.Crop,
                )
            }
        }

        HomeStats(statistics, recentCatches, onSpeciesClick, onCatchesClick, onRecordDaysClick)

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("最近鱼获", color = LightInk, fontSize = 20.sp, fontWeight = FontWeight.Normal)
            Row(
                Modifier.clickable(onClick = onCatchesClick).padding(horizontal = 2.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("全部", color = LightInk, fontSize = 14.sp)
                Image(
                    painter = painterResource(R.drawable.all_chevron_v12),
                    contentDescription = "全部鱼获",
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        if (recent != null) {
            Box(
                Modifier
                    .fillMaxWidth(0.78f)
                    .aspectRatio(0.80f)
                    .padding(top = 4.dp)
                    .graphicsLayerForCard(cardScale.value, cardY.value, density),
            ) {
                RecentFishCard(
                    item = recent,
                    imageUrl = resolveImageUrl(recent.imageUrl),
                    accessToken = accessToken,
                    onClick = { onCatchClick(recent.id) },
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Text("记录下一条鱼", color = LightInk, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
        HomeCameraButton(onClick = onIdentify)
    }
}

private fun Modifier.graphicsLayerForCard(scale: Float, translationY: Float, density: androidx.compose.ui.unit.Density): Modifier = graphicsLayer {
    scaleX = scale
    scaleY = scale
    this.translationY = with(density) { translationY.dp.toPx() }
}

private fun catchTimestamp(item: RemoteCatch): Date {
    val value = item.capturedAt.ifBlank { item.createdAt }
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(value)
    }.getOrNull() ?: Date(0)
}
