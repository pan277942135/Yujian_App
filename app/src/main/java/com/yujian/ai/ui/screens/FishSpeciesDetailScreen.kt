package com.yujian.ai.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
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
import com.yujian.ai.model.FishSpecies
import com.yujian.ai.ui.components.FishIllustration
import com.yujian.ai.ui.components.TagChip
import com.yujian.ai.ui.components.YujianTopBar
import com.yujian.ai.ui.theme.CardWhite
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.SoftWater
import com.yujian.ai.ui.theme.WarmBackground
import com.yujian.ai.ui.theme.WaterTeal

@Composable
fun FishSpeciesDetailScreen(
    fish: FishSpecies,
    onBack: () -> Unit,
    onOpenCatch: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(WarmBackground),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item { YujianTopBar(title = fish.name, subtitle = fish.aliases, onBack = onBack) }
        item {
            Box(
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(286.dp)
                    .shadow(18.dp, RoundedCornerShape(28.dp), ambientColor = WaterTeal.copy(alpha = .08f), spotColor = WaterTeal.copy(alpha = .08f))
                    .background(SoftWater, RoundedCornerShape(28.dp)),
            ) {
                Box(Modifier.align(Alignment.TopStart).padding(18.dp).background(Color.White.copy(alpha = .72f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Text(if (fish.discovered) "● 已发现" else "○ 尚未发现", color = if (fish.discovered) WaterTeal else MutedInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                FishIllustration(Modifier.align(Alignment.Center), size = 210.dp, bodyColor = WaterTeal.copy(alpha = .72f))
            }
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(fish.name, color = DeepInk, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Text(fish.aliases, color = MutedInk, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Box(Modifier.size(44.dp).background(CardWhite, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Bookmark, contentDescription = "收藏", tint = WaterTeal)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
                    TagChip(fish.category.substringBefore(" · "), true)
                    TagChip(fish.category.substringAfter(" · "))
                }
                Text(fish.description, color = DeepInk, fontSize = 14.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 20.dp))
            }
        }

        item {
            InfoGrid(fish)
        }

        item {
            Column(
                Modifier.padding(20.dp).fillMaxWidth().background(CardWhite, RoundedCornerShape(24.dp)).padding(18.dp),
            ) {
                Text("简单钓鱼知识", color = DeepInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(fish.tip, color = DeepInk, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }

        if (fish.catches > 0) {
            item {
                Button(
                    onClick = onOpenCatch,
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WaterTeal),
                ) {
                    Text("查看我的 ${fish.name} 鱼获 · ${fish.catches}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun InfoGrid(fish: FishSpecies) {
    Row(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(CardWhite, RoundedCornerShape(24.dp)).padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InfoItem("常见水域", fish.habitat, Modifier.weight(1f))
        Box(Modifier.height(52.dp).size(width = 1.dp, height = 52.dp).background(Color(0xFFE6E9E5)))
        InfoItem("食性", fish.diet, Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
    Row(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(CardWhite, RoundedCornerShape(24.dp)).padding(18.dp),
    ) {
        InfoItem("活跃季节", fish.season, Modifier.weight(1f))
        InfoItem("我的记录", if (fish.catches > 0) "${fish.catches} 次" else "还没有", Modifier.weight(1f))
    }
}

@Composable
private fun InfoItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = MutedInk, fontSize = 11.sp)
        Text(value, color = DeepInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
    }
}
