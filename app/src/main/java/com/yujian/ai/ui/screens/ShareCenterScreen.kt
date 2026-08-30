package com.yujian.ai.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.model.CatchRecord
import com.yujian.ai.model.SharePeriod
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
fun ShareCenterScreen(
    catch: CatchRecord,
    onBack: () -> Unit,
) {
    var selected by remember { mutableStateOf(SharePeriod.SINGLE) }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().background(WarmBackground)) {
        YujianTopBar(title = "分享鱼获", subtitle = "选一个固定模板，马上分享", onBack = onBack)

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SharePeriod.entries.forEach { period ->
                val active = selected == period
                Box(
                    modifier = Modifier
                        .background(if (active) WaterTeal else CardWhite, RoundedCornerShape(50))
                        .clickable { selected = period }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                ) {
                    Text(period.label, color = if (active) Color.White else DeepInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            ShareCard(period = selected, catch = catch)
        }

        Button(
            onClick = {
                val text = shareCopy(selected, catch)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }, "分享渔见鱼获"))
            },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp).fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WaterTeal),
        ) {
            Icon(Icons.Rounded.IosShare, contentDescription = null)
            Text("分享这个模板", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun ShareCard(period: SharePeriod, catch: CatchRecord) {
    val stats = when (period) {
        SharePeriod.SINGLE -> Triple("${catch.weightKg} kg", "${catch.lengthCm} cm", catch.speciesName)
        SharePeriod.TODAY -> Triple("3 条", "2 种", "4.8 kg")
        SharePeriod.WEEK -> Triple("8 条", "4 种", "11.6 kg")
        SharePeriod.MONTH -> Triple("18 条", "7 种", "26.4 kg")
        SharePeriod.YEAR -> Triple("36 条", "12 种", "52.7 kg")
        SharePeriod.ALL -> Triple("36 条", "12 / 200", "52.7 kg")
    }
    val title = if (period == SharePeriod.SINGLE) "今天遇见一条 ${catch.speciesName}" else "我的${period.label}鱼获"

    Column(
        modifier = Modifier.width(330.dp).shadow(24.dp, RoundedCornerShape(30.dp), ambientColor = Color.Black.copy(alpha = .10f), spotColor = Color.Black.copy(alpha = .10f))
            .background(CardWhite, RoundedCornerShape(30.dp)).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("渔见 AI", color = DeepInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("收藏每一次渔获", color = MutedInk, fontSize = 10.sp)
            }
            Box(Modifier.background(SoftWater, RoundedCornerShape(50)).padding(horizontal = 11.dp, vertical = 6.dp)) {
                Text(period.label, color = WaterTeal, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().height(190.dp).background(SoftWater, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
            FishIllustration(size = 170.dp, bodyColor = WaterTeal.copy(alpha = .72f))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, color = DeepInk, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (period == SharePeriod.SINGLE) {
            Text("${catch.location}  ·  ${catch.timeLabel}", color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ShareMetric(stats.first, if (period == SharePeriod.SINGLE) "重量" else "鱼获")
            ShareMetric(stats.second, if (period == SharePeriod.SINGLE) "长度" else "鱼种")
            ShareMetric(stats.third, if (period == SharePeriod.SINGLE) "鱼种" else "累计重量")
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().background(Achievement, RoundedCornerShape(18.dp)).padding(12.dp), contentAlignment = Alignment.Center) {
            Text(if (period == SharePeriod.ALL) "已经解锁 12 种鱼，继续去遇见新的鱼。" else "每一次出钓，都值得被记住。", color = AchievementInk, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ShareMetric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = DeepInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = MutedInk, fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

private fun shareCopy(period: SharePeriod, catch: CatchRecord): String = when (period) {
    SharePeriod.SINGLE -> "我用渔见记录了一条${catch.speciesName}：${catch.weightKg}kg / ${catch.lengthCm}cm，${catch.location}。\n\n渔见 AI · 收藏每一次渔获"
    SharePeriod.TODAY -> "今天鱼获：3条 / 2种 / 4.8kg。\n\n渔见 AI · 收藏每一次渔获"
    SharePeriod.WEEK -> "本周鱼获：8条 / 4种 / 11.6kg。\n\n渔见 AI · 收藏每一次渔获"
    SharePeriod.MONTH -> "本月鱼获：18条 / 7种 / 26.4kg。\n\n渔见 AI · 收藏每一次渔获"
    SharePeriod.YEAR -> "本年鱼获：36条 / 12种 / 52.7kg。\n\n渔见 AI · 收藏每一次渔获"
    SharePeriod.ALL -> "累计鱼获36条，已经发现12种鱼。\n\n渔见 AI · 收藏每一次渔获"
}
