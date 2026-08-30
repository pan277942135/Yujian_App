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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.ui.components.FishIllustration
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
                Column(Modifier.padding(start = 16.dp)) {
                    Text("渔见钓友", color = DeepInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("已收藏 36 次鱼获", color = MutedInk, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileStat(Modifier.weight(1f), "12", "已发现鱼种")
                ProfileStat(Modifier.weight(1f), "36", "累计鱼获")
                ProfileStat(Modifier.weight(1f), "6", "草鱼记录")
            }
        }
        item { MenuCard("我的图鉴", "看看已经遇见过哪些鱼", onGuide) }
        item { MenuCard("最近鱼获", "回看刚刚保存的记录", onCatch) }
    }
}

@Composable
private fun ProfileStat(modifier: Modifier, value: String, label: String) {
    Column(modifier.background(CardWhite, RoundedCornerShape(20.dp)).padding(14.dp)) {
        Text(value, color = DeepInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
