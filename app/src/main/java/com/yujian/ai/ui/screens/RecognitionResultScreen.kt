package com.yujian.ai.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.model.CatchRecord
import com.yujian.ai.model.DemoData
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
fun RecognitionResultScreen(
    image: Bitmap?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSave: (CatchRecord) -> Unit,
) {
    var selectedKey by remember { mutableStateOf("grass_carp") }
    var showCorrection by remember { mutableStateOf(false) }
    val fish = DemoData.species.firstOrNull { it.key == selectedKey } ?: DemoData.species.first()
    val confidence = if (selectedKey == "grass_carp") 92 else 78

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(WarmBackground),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            YujianTopBar(title = "识别结果", onBack = onBack)
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(270.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(SoftWater),
                contentAlignment = Alignment.Center,
            ) {
                if (image != null) {
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = "已识别鱼获照片",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .12f)))
                } else {
                    FishIllustration(size = 170.dp, bodyColor = Color(0xFF748F78))
                }
                Box(
                    Modifier.align(Alignment.TopStart).padding(18.dp).background(Color.White.copy(alpha = .88f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(WaterTeal, CircleShape))
                        Text("识别完成", color = WaterTeal, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 7.dp))
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("看起来是", color = MutedInk, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(fish.name, color = DeepInk, fontSize = 31.sp, fontWeight = FontWeight.Bold)
                    Box(Modifier.padding(start = 14.dp).background(SoftWater, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 7.dp)) {
                        Text("${confidence}% 把握", color = WaterTeal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                    TagChip(fish.category.substringBefore(" · "))
                    TagChip(fish.category.substringAfter(" · ", "常见"))
                    TagChip("常见", emphasized = true)
                }
                Text(fish.description, color = DeepInk, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 16.dp))
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .shadow(8.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = .04f), spotColor = Color.Black.copy(alpha = .04f))
                    .background(CardWhite, RoundedCornerShape(24.dp))
                    .padding(18.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("常见水域", color = MutedInk, fontSize = 11.sp)
                    Text(fish.habitat, color = DeepInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 5.dp))
                }
                Box(Modifier.height(50.dp).size(width = 1.dp, height = 50.dp).background(Color(0xFFE1E6E4)))
                Column(Modifier.weight(1f).padding(start = 18.dp)) {
                    Text("食性", color = MutedInk, fontSize = 11.sp)
                    Text(fish.diet, color = DeepInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }

        if (showCorrection) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(CardWhite, RoundedCornerShape(24.dp)).padding(vertical = 16.dp)) {
                    Text("选一个更接近的鱼种", color = DeepInk, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(DemoData.species.take(6), key = FishSpecies::key) { option ->
                            Box(Modifier.clickable { selectedKey = option.key }) {
                                TagChip(option.name, emphasized = option.key == selectedKey)
                            }
                        }
                    }
                    Text("你的纠正会帮助后续识别变得更准", color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }

        item {
            Button(
                onClick = {
                    onSave(
                        DemoData.catch.copy(
                            speciesKey = fish.key,
                            speciesName = fish.name,
                            confidence = confidence,
                            note = "由识鱼结果保存。${DemoData.catch.note}",
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(54.dp),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WaterTeal),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text("保存这次鱼获", modifier = Modifier.padding(start = 8.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onRetry) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, tint = WaterTeal)
                    Text("重新识别", color = WaterTeal, modifier = Modifier.padding(start = 5.dp))
                }
                TextButton(onClick = { showCorrection = !showCorrection }) {
                    Text(if (showCorrection) "收起纠正" else "这不是我要的鱼", color = MutedInk)
                }
            }
        }
    }
}
