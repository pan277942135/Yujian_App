package com.yujian.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
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
import com.yujian.ai.ui.components.YujianTopBar
import com.yujian.ai.ui.theme.Achievement
import com.yujian.ai.ui.theme.AchievementInk
import com.yujian.ai.ui.theme.CardWhite
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.SoftWater
import com.yujian.ai.ui.theme.WarmBackground
import com.yujian.ai.ui.theme.WaterTeal

@Composable
fun CatchDetailScreen(
    catch: CatchRecord,
    onBack: () -> Unit,
    onShare: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(WarmBackground),
        contentPadding = PaddingValues(bottom = 38.dp),
    ) {
        item { YujianTopBar(title = "我的鱼获", subtitle = catch.timeLabel, onBack = onBack) }
        item {
            Box(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(300.dp)
                    .shadow(18.dp, RoundedCornerShape(28.dp), ambientColor = Color.Black.copy(alpha = .05f), spotColor = Color.Black.copy(alpha = .05f))
                    .background(SoftWater, RoundedCornerShape(28.dp)),
            ) {
                FishIllustration(Modifier.align(Alignment.Center), size = 220.dp, bodyColor = WaterTeal.copy(alpha = .72f))
                if (catch.isNewRecord) {
                    Box(Modifier.align(Alignment.TopEnd).padding(16.dp).background(Achievement, RoundedCornerShape(50)).padding(horizontal = 13.dp, vertical = 7.dp)) {
                        Text("新记录", color = AchievementInk, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Box(Modifier.align(Alignment.BottomStart).padding(16.dp).background(Color.White.copy(alpha = .80f), RoundedCornerShape(50)).padding(horizontal = 13.dp, vertical = 7.dp)) {
                    Text("AI 识别 · ${catch.confidence}% 把握", color = WaterTeal, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
                Text(catch.speciesName, color = DeepInk, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("${catch.location}  ·  ${catch.timeLabel}", color = MutedInk, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            Row(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(CardWhite, RoundedCornerShape(24.dp)).padding(18.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                Metric("重量", "${catch.weightKg} kg")
                Metric("长度", "${catch.lengthCm} cm")
                Metric("识别", "${catch.confidence}%")
            }
        }
        item {
            Column(Modifier.padding(20.dp).fillMaxWidth().background(CardWhite, RoundedCornerShape(24.dp)).padding(18.dp)) {
                Text("这次鱼获", color = DeepInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(catch.note, color = DeepInk, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
        item {
            Button(
                onClick = onShare,
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WaterTeal),
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null)
                Text("分享这次鱼获", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = DeepInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
