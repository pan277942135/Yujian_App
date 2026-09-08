package com.yujian.ai.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.R
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.components.RemoteImage

private val Ink = Color(0xFF18324A)
private val Muted = Color(0xCC18324A)
private val Gold = Color(0xFFE8D5A7)
private val Glass = Color(0x59FFFFFF)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    nickname: String,
    statistics: CatchStatistics,
    recentCatches: List<RemoteCatch>,
    resolveImageUrl: (String?) -> String?,
    accessToken: String,
    onIdentify: () -> Unit,
    onSpeciesClick: () -> Unit,
    onCatchesClick: () -> Unit,
    onRecordDaysClick: () -> Unit,
    onProfileClick: () -> Unit,
    onCatchClick: (String) -> Unit,
) {
    val view = LocalView.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val transition = rememberInfiniteTransition(label = "p01-home")
    val waterAlpha by transition.animateFloat(0.20f, 0.40f, infiniteRepeatable(tween(24000), RepeatMode.Reverse), label = "water")
    val fogX by transition.animateFloat(0f, 18f, infiniteRepeatable(tween(30000), RepeatMode.Reverse), label = "fog")
    val fishY by transition.animateFloat(0f, -8f, infiniteRepeatable(tween(4000), RepeatMode.Reverse), label = "fish")
    val cameraScale by transition.animateFloat(1f, 1.05f, infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "camera")
    var selectedIndex by remember { mutableStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.home_morning_bg), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Image(painterResource(R.drawable.mountain_fog_layer), null, Modifier.fillMaxSize().graphicsLayer { translationX = fogX }, contentScale = ContentScale.Crop)
        Image(painterResource(R.drawable.water_surface_overlay), null, Modifier.fillMaxSize().alpha(waterAlpha), contentScale = ContentScale.Crop)

        Column(
            modifier = Modifier.fillMaxSize().padding(top = topInset + 18.dp, bottom = bottomInset + 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Column(Modifier.align(Alignment.CenterStart)) {
                    Text("渔见", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("拍照收藏每次渔获", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Image(
                    painterResource(R.drawable.profile_button_glass),
                    contentDescription = "个人中心",
                    modifier = Modifier.size(54.dp).align(Alignment.CenterEnd).clickable(onClick = onProfileClick),
                )
            }

            StatisticsSummary(statistics, recentCatches, onSpeciesClick, onCatchesClick, onRecordDaysClick)

            Spacer(Modifier.weight(1f))
            if (recentCatches.isEmpty()) {
                EmptyCatchCard()
            } else {
                CatchCarousel(
                    catches = recentCatches,
                    selectedIndex = selectedIndex,
                    fishY = fishY,
                    resolveImageUrl = resolveImageUrl,
                    accessToken = accessToken,
                    onIndexChanged = { selectedIndex = it },
                    onCatchClick = onCatchClick,
                )
            }
            Spacer(Modifier.weight(1f))

            Box(Modifier.size(126.dp).graphicsLayer { scaleX = cameraScale; scaleY = cameraScale }.clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onIdentify()
            }) {
                Image(painterResource(R.drawable.camera_outer_ring), null, Modifier.fillMaxSize())
                Image(painterResource(R.drawable.camera_icon), "开始识鱼", Modifier.size(52.dp).align(Alignment.Center))
            }
            Text("拍照识鱼", color = Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun StatisticsSummary(
    statistics: CatchStatistics,
    catches: List<RemoteCatch>,
    onSpeciesClick: () -> Unit,
    onCatchesClick: () -> Unit,
    onRecordDaysClick: () -> Unit,
) {
    val days = remember(catches) { catches.map { it.createdAt.take(10) }.filter(String::isNotBlank).distinct().size }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp).clip(RoundedCornerShape(32.dp)).background(Glass).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Stat("${statistics.speciesCount}", "鱼种", onSpeciesClick)
        Stat("${statistics.totalCatches}", "渔获", onCatchesClick)
        Stat("$days", "天记录", onRecordDaysClick)
    }
}

@Composable
private fun Stat(value: String, label: String, onClick: () -> Unit) {
    Column(Modifier.width(88.dp).clickable(onClick = onClick).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun CatchCarousel(
    catches: List<RemoteCatch>, selectedIndex: Int, fishY: Float,
    resolveImageUrl: (String?) -> String?, accessToken: String,
    onIndexChanged: (Int) -> Unit, onCatchClick: (String) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = selectedIndex, pageCount = { catches.size })
    LaunchedEffect(pagerState.currentPage) { onIndexChanged(pagerState.currentPage) }
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 52.dp),
        pageSpacing = 14.dp,
    ) { index ->
        val item = catches[index]
        CatchCard(item, index == pagerState.currentPage, fishY, resolveImageUrl, accessToken) { onCatchClick(item.id) }
    }
}

@Composable
private fun CatchCard(item: RemoteCatch, active: Boolean, fishY: Float, resolveImageUrl: (String?) -> String?, token: String, onClick: () -> Unit) {
    Box(Modifier.size(width = 286.dp, height = 356.dp).graphicsLayer { scaleX = if (active) 1f else .92f; scaleY = if (active) 1f else .92f; alpha = if (active) 1f else .72f }.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(Color(0x4DFFFFFF)))
        RemoteImage(resolveImageUrl(item.imageUrl), Modifier.fillMaxSize().padding(22.dp).graphicsLayer { translationY = fishY }, "${item.speciesName} 鱼获照片", ContentScale.Crop, token) {
            Image(painterResource(R.drawable.fish_subject), "鱼获主体", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        Image(painterResource(R.drawable.fish_card_frame), null, Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0xA6FFFFFF)).padding(18.dp)) {
            Text(item.speciesName, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(item.capturedAt.ifBlank { item.createdAt.take(16) }, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun EmptyCatchCard() {
    Column(Modifier.padding(horizontal = 34.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Glass).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("还没有鱼获", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("拍下第一条鱼，\n它会收藏在这里", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}
