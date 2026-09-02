package com.yujian.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.session.UserSession
import com.yujian.ai.ui.components.FishIllustration
import com.yujian.ai.ui.components.RemoteImage
import com.yujian.ai.ui.theme.CardWhite
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.SoftWater
import com.yujian.ai.ui.theme.WarmBackground
import com.yujian.ai.ui.theme.WaterTeal

@Composable
fun MyScreen(
    session: UserSession,
    statistics: CatchStatistics,
    catches: List<RemoteCatch>,
    loading: Boolean,
    error: String?,
    resolveImageUrl: (String?) -> String?,
    onGuide: () -> Unit,
    onSpecies: (String) -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(WarmBackground),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("我的", color = DeepInk, fontSize = 29.sp, fontWeight = FontWeight.Bold)
            Text("收藏每一次渔获，也收藏自己的钓鱼轨迹", color = MutedInk, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().background(SoftWater, RoundedCornerShape(28.dp)).padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(74.dp).background(Color.White, RoundedCornerShape(37.dp)), contentAlignment = Alignment.Center) {
                    FishIllustration(size = 56.dp, bodyColor = WaterTeal)
                }
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(session.nickname.ifBlank { session.username }, color = DeepInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("已保存 ${statistics.totalCatches} 次鱼获", color = MutedInk, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                }
                TextButton(onClick = onLogout) { Text("退出", color = MutedInk, fontSize = 12.sp) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileStat(Modifier.weight(1f), statistics.speciesCount.toString(), "已识别鱼种")
                ProfileStat(Modifier.weight(1f), statistics.totalCatches.toString(), "累计鱼获")
                ProfileStat(Modifier.weight(1f), statistics.topSpecies.firstOrNull()?.speciesName ?: "—", "最常钓")
            }
        }
        item { Text("最近鱼获", color = DeepInk, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        if (loading) {
            item { Text("正在同步你的鱼获…", color = MutedInk, fontSize = 13.sp) }
        } else if (!error.isNullOrBlank()) {
            item {
                Column(Modifier.fillMaxWidth().background(CardWhite, RoundedCornerShape(20.dp)).padding(16.dp)) {
                    Text(error, color = MutedInk, fontSize = 12.sp)
                    Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = WaterTeal), modifier = Modifier.padding(top = 10.dp)) {
                        Text("重新加载")
                    }
                }
            }
        } else if (catches.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().background(CardWhite, RoundedCornerShape(22.dp)).padding(20.dp)) {
                    Text("还没有鱼获记录", color = DeepInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("拍一张鱼获照片，AI 识别后保存到这里。", color = MutedInk, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        } else {
            items(catches, key = { it.id }) { catch ->
                CatchRow(catch, resolveImageUrl, session.accessToken) { onSpecies(catch.speciesId) }
            }
        }
        item { MenuCard("我的图鉴", "看看已认识的鱼种", onGuide) }
    }
}

@Composable
private fun CatchRow(
    catch: RemoteCatch,
    resolveImageUrl: (String?) -> String?,
    accessToken: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = .04f), spotColor = Color.Black.copy(alpha = .04f))
            .background(CardWhite, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)).background(SoftWater), contentAlignment = Alignment.Center) {
            RemoteImage(
                url = resolveImageUrl(catch.imageUrl),
                authToken = accessToken,
                modifier = Modifier.fillMaxSize(),
                contentDescription = "${catch.speciesName} 鱼获照片",
                contentScale = ContentScale.Crop,
            ) { FishIllustration(size = 52.dp, bodyColor = WaterTeal.copy(alpha = .7f)) }
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(catch.speciesName, color = DeepInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("${catch.confidencePercent}% 把握", color = WaterTeal, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Text(formatCatchDate(catch.createdAt), color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
        }
        Text("›", color = WaterTeal, fontSize = 28.sp)
    }
}

private fun formatCatchDate(value: String): String = value.take(10).replace('-', '.')

@Composable
private fun ProfileStat(modifier: Modifier, value: String, label: String) {
    Column(modifier.background(CardWhite, RoundedCornerShape(20.dp)).padding(14.dp)) {
        Text(value, color = DeepInk, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = MutedInk, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun MenuCard(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = .04f), spotColor = Color.Black.copy(alpha = .04f))
            .background(CardWhite, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = DeepInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Text("›", color = WaterTeal, fontSize = 28.sp)
    }
}
