package com.yujian.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.model.CatchRecord
import com.yujian.ai.ui.components.FishIllustration
import com.yujian.ai.ui.theme.CardWhite
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.SoftWater
import com.yujian.ai.ui.theme.WarmBackground
import com.yujian.ai.ui.theme.WaterTeal

@Composable
fun HomeScreen(
    recentCatch: CatchRecord,
    onIdentify: () -> Unit,
    onGuide: () -> Unit,
    onRecentCatch: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(WarmBackground),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(42.dp).background(SoftWater, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    FishIllustration(size = 34.dp, bodyColor = WaterTeal)
                }
                Column(Modifier.padding(start = 10.dp)) {
                    Text("渔见 AI", color = DeepInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("收藏每一次渔获", color = MutedInk, fontSize = 12.sp)
                }
            }
        }

        item {
            Text("下午好 :)", color = MutedInk, fontSize = 14.sp)
            Text("今天钓到什么？", color = DeepInk, fontSize = 29.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(18.dp, RoundedCornerShape(28.dp), ambientColor = WaterTeal.copy(alpha = .08f), spotColor = WaterTeal.copy(alpha = .08f))
                    .background(SoftWater, RoundedCornerShape(28.dp))
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(92.dp).background(Color.White, RoundedCornerShape(46.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    FishIllustration(size = 70.dp, bodyColor = WaterTeal)
                }
                Column(Modifier.weight(1f).padding(start = 18.dp)) {
                    Text("拍照识鱼", color = DeepInk, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("拍下鱼获，让 AI 帮你认出来", color = DeepInk, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
                    Text("鱼体完整 · 光线充足 · 少遮挡", color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                    Button(
                        onClick = onIdentify,
                        modifier = Modifier.padding(top = 16.dp).height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WaterTeal),
                    ) {
                        Icon(Icons.Rounded.PhotoCamera, contentDescription = null)
                        Text("拍照识鱼", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("我的探索", color = DeepInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("查看图鉴", color = WaterTeal, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onGuide).padding(8.dp))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(modifier = Modifier.weight(1f), value = "12 / 200", label = "已发现鱼种")
                StatCard(modifier = Modifier.weight(1f), value = "36", label = "累计鱼获")
            }
        }

        item {
            Text("最近鱼获", color = DeepInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = .04f), spotColor = Color.Black.copy(alpha = .04f))
                    .background(CardWhite, RoundedCornerShape(24.dp))
                    .clickable(onClick = onRecentCatch)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(88.dp).background(SoftWater, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    FishIllustration(size = 70.dp, bodyColor = Color(0xFF748F78))
                }
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(recentCatch.speciesName, color = DeepInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${recentCatch.weightKg} kg", color = WaterTeal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                    Text("${recentCatch.location} · ${recentCatch.timeLabel}", color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp))
                }
                if (recentCatch.isNewRecord) {
                    Box(Modifier.background(Color(0xFFFFF7DA), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text("新记录", color = Color(0xFFA17114), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, value: String, label: String) {
    Column(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = .04f), spotColor = Color.Black.copy(alpha = .04f))
            .background(CardWhite, RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
        Box(Modifier.size(34.dp).background(SoftWater, RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
            Text("◒", color = WaterTeal, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Text(value, color = DeepInk, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(label, color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
