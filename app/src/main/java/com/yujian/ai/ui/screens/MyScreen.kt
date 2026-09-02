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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.account.CatchStatistics
import com.yujian.ai.account.RemoteCatch
import com.yujian.ai.account.UserSession
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
    onGuide: () -> Unit,
    onCatch: () -> Unit,
    session: UserSession?,
    statistics: CatchStatistics?,
    recentCatches: List<RemoteCatch>,
    loading: Boolean,
    error: String?,
    resolveImageUrl: (String?) -> String?,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onCatchClick: (RemoteCatch) -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(WarmBackground),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("我的", color = DeepInk, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                    Text("收藏每一次渔获，也收藏自己的钓鱼轨迹", color = MutedInk, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
                if (session != null) {
                    TextButton(onClick = onLogout) { Text("退出", color = MutedInk) }
                }
            }
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
                    Text(session?.nickname ?: "渔见用户", color = DeepInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (session == null) "登录后保存你的 AI 鱼获" else "已收藏 ${statistics?.totalCatches ?: 0} 次鱼获",
                        color = MutedInk, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp),
                    )
                }
                if (session == null) {
                    Button(
                        onClick = onLogin,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WaterTeal),
                    ) { Text("登录") }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileStat(Modifier.weight(1f), "${statistics?.totalCatches ?: 0}", "累计鱼获")
                ProfileStat(Modifier.weight(1f), "${statistics?.speciesCount ?: 0}", "已发现鱼种")
                ProfileStat(Modifier.weight(1f), statistics?.topSpecies ?: "—", "最常钓")
            }
        }
        if (loading) {
            item { Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WaterTeal) } }
        }
        if (!error.isNullOrBlank()) {
            item { Text(error, color = Color(0xFFB42318), fontSize = 12.sp) }
        }
        item { MenuCard("我的图鉴", "看看已经遇见过哪些鱼", onGuide) }
        item { MenuCard("我的鱼获", "回看刚刚保存的记录", onCatch) }
        if (recentCatches.isNotEmpty()) {
            item { Text("最近鱼获", color = DeepInk, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            items(recentCatches.take(3), key = { it.id }) { item ->
                CatchPreviewRow(item, resolveImageUrl, { onCatchClick(item) })
            }
        }
    }
}

@Composable
private fun ProfileStat(modifier: Modifier, value: String, label: String) {
    Column(modifier.background(CardWhite, RoundedCornerShape(20.dp)).padding(14.dp)) {
        Text(value, color = DeepInk, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = MutedInk, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun MenuCard(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(CardWhite, RoundedCornerShape(22.dp)).clickable(onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = DeepInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Text("›", color = WaterTeal, fontSize = 28.sp)
    }
}

@Composable
internal fun CatchPreviewRow(
    item: RemoteCatch,
    resolveImageUrl: (String?) -> String?,
    onClick: () -> Unit,
    token: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(CardWhite, RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(
            url = resolveImageUrl(item.imageUrl),
            modifier = Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)).background(SoftWater),
            contentDescription = item.speciesName,
            contentScale = ContentScale.Crop,
            headers = token?.let { mapOf("Authorization" to "Bearer \$it") }.orEmpty(),
            placeholder = { FishIllustration(size = 42.dp, bodyColor = WaterTeal) },
        )
        Column(Modifier.padding(start = 13.dp).weight(1f)) {
            Text(item.speciesName.ifBlank { "未命名鱼获" }, color = DeepInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("${(item.confidence * 100).toInt()}% · ${formatCatchDate(item.createdAt ?: item.capturedAt)}", color = MutedInk, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Text("›", color = WaterTeal, fontSize = 24.sp)
    }
}

internal fun formatCatchDate(value: String?): String =
    value?.replace('T', ' ')?.take(16)?.ifBlank { "刚刚" } ?: "刚刚"
