package com.yujian.ai.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.ui.components.FishIllustration
import com.yujian.ai.ui.components.YujianTopBar
import com.yujian.ai.ui.theme.CardWhite
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.SoftWater
import com.yujian.ai.ui.theme.WarmBackground
import com.yujian.ai.ui.theme.WaterTeal
import kotlinx.coroutines.delay

@Composable
fun RecognizingScreen(
    image: Bitmap?,
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    var stage by remember { mutableIntStateOf(0) }

    LaunchedEffect(image) {
        stage = 1
        delay(650)
        stage = 2
        delay(650)
        stage = 3
        delay(650)
        onFinished()
    }

    Column(Modifier.fillMaxSize().background(WarmBackground)) {
        YujianTopBar(title = "正在认识这条鱼", subtitle = "再等我一小会儿", onBack = onBack)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .height(312.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(SoftWater),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = "正在识别的鱼获照片",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .34f)))
            } else {
                FishIllustration(size = 150.dp, bodyColor = WaterTeal.copy(alpha = .78f))
            }

            Box(Modifier.size(216.dp).background(Color.Transparent, CircleShape))
            Box(
                Modifier
                    .size(184.dp)
                    .background(Color.White.copy(alpha = .18f), CircleShape),
            )
            Box(
                Modifier
                    .size(150.dp)
                    .background(Color.White.copy(alpha = .20f), CircleShape),
            )
            FishIllustration(size = 126.dp, bodyColor = WaterTeal.copy(alpha = .72f))

            Text(
                "识别中",
                color = WaterTeal,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopStart).padding(18.dp).background(Color.White.copy(alpha = .72f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp).background(CardWhite, RoundedCornerShape(24.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("我正在看这些", color = DeepInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            RecognitionStep("识别轮廓", stage >= 1, stage == 1)
            RecognitionStep("分析鱼鳍与嘴型", stage >= 2, stage == 2)
            RecognitionStep("比对颜色和花纹", stage >= 3, stage == 3)
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 64.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("通常 2–3 秒就能认出来", color = MutedInk, fontSize = 12.sp)
            Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(7.dp).background(Color(0xFFE1E6E4), RoundedCornerShape(50))) {
                val fraction = when (stage) { 1 -> .34f; 2 -> .68f; else -> 1f }
                Box(Modifier.fillMaxWidth(fraction).height(7.dp).background(WaterTeal, RoundedCornerShape(50)))
            }
        }
    }
}

@Composable
private fun RecognitionStep(label: String, completed: Boolean, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(28.dp).background(
                when {
                    completed -> WaterTeal
                    active -> SoftWater
                    else -> Color(0xFFE7ECEA)
                }, CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (completed) "✓" else "·", color = if (completed) Color.White else MutedInk, fontWeight = FontWeight.Bold)
        }
        Text(label, color = DeepInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 14.dp))
        Text(
            when {
                completed && !active -> "完成"
                active -> "正在进行"
                else -> "等待"
            },
            color = if (active) Color(0xFFFF8A34) else if (completed) WaterTeal else MutedInk,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
