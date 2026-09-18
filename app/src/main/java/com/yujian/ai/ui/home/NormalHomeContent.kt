package com.yujian.ai.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.yujian.ai.ui.components.RemoteImage

private val Ink = Color(0xFF18324A)
private val Muted = Color(0xCC18324A)

@Composable
fun NormalHomeContent(
    statistics: CatchStatistics,
    recentCatches: List<RemoteCatch>,
    resolveImageUrl: (String?) -> String?,
    accessToken: String,
    isLoggedIn: Boolean,
    onIdentify: () -> Unit,
    onSpeciesClick: () -> Unit,
    onCatchesClick: () -> Unit,
    onRecordDaysClick: () -> Unit,
    onProfileClick: () -> Unit,
    onCatchClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "normal-home")
    val cameraScale by transition.animateFloat(
        1f,
        1.045f,
        infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "camera-breath",
    )
    val cardY by transition.animateFloat(
        0f,
        -3f,
        infiniteRepeatable(tween(3800), RepeatMode.Reverse),
        label = "recent-card-idle",
    )
    val recent = recentCatches.firstOrNull()

    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("我的鱼获", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Medium)
            if (isLoggedIn) {
                Image(
                    painterResource(R.drawable.profile_button_glass),
                    "个人中心",
                    Modifier.size(54.dp).clickable(onClick = onProfileClick),
                )
            } else {
                AssetImage(
                    "home_normal/avatar/avatar_placeholder.png",
                    Modifier.size(54.dp).clip(CircleShape).clickable(onClick = onProfileClick),
                    contentDescription = "登录或注册",
                    contentScale = ContentScale.Crop,
                )
            }
        }

        HomeStats(statistics, recentCatches, onSpeciesClick, onCatchesClick, onRecordDaysClick)

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("最近鱼获", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text("全部 〉", color = Muted, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onCatchesClick).padding(8.dp))
        }

        if (recent != null) {
            Box(Modifier.fillMaxWidth().graphicsLayer { translationY = cardY }) {
                RecentFishCard(
                    item = recent,
                    imageUrl = resolveImageUrl(recent.imageUrl),
                    accessToken = accessToken,
                    onClick = { onCatchClick(recent.id) },
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Text("记录下一条鱼", color = Ink, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
        HomeCameraButton(cameraScale = cameraScale, onClick = onIdentify)
        Text("拍照识鱼", color = Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}
