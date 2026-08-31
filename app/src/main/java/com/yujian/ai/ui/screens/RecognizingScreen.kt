package com.yujian.ai.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.components.FishIllustration
import com.yujian.ai.ui.components.YujianTopBar
import com.yujian.ai.ui.theme.*
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

@Composable
fun RecognizingScreen(
    image: SelectedImage?,
    onBack: () -> Unit,
    recognize: suspend () -> ProductionRecognitionResult,
    onFinished: (ProductionRecognitionResult) -> Unit,
) {
    var stage by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(image?.filePath) {
        if (image == null) { error = "没有可识别的照片"; return@LaunchedEffect }
        error = null
        stage = 1
        val result = async { runCatching { recognize() } }
        delay(400); stage = 2
        delay(400); stage = 3
        delay(250)
        result.await().onSuccess(onFinished).onFailure {
            error = it.message ?: "识别失败，请重新选择照片"
        }
    }

    Column(Modifier.fillMaxSize().background(WarmBackground)) {
        YujianTopBar(title = "正在认识这条鱼", subtitle = if (error == null) "先找鱼，再识别鱼种" else "这次没认出来", onBack = onBack)
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).height(312.dp)
                .clip(RoundedCornerShape(28.dp)).background(SoftWater), contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Image(image.bitmap.asImageBitmap(), "正在识别的鱼获照片", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .34f)))
            } else FishIllustration(size = 150.dp, bodyColor = WaterTeal.copy(alpha = .78f))
            Box(Modifier.size(184.dp).background(Color.White.copy(alpha = .18f), CircleShape))
            Box(Modifier.size(150.dp).background(Color.White.copy(alpha = .20f), CircleShape))
            FishIllustration(size = 126.dp, bodyColor = WaterTeal.copy(alpha = .72f))
            Text(if (error == null) "本机识别中" else "识别未完成", color = WaterTeal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopStart).padding(18.dp).background(Color.White.copy(alpha = .76f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp))
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp).background(CardWhite, RoundedCornerShape(24.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("我正在看这些", color = DeepInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            RecognitionStep("检测照片中的鱼体", stage >= 1, stage == 1 && error == null)
            RecognitionStep("检查鱼体是否完整清晰", stage >= 2, stage == 2 && error == null)
            RecognitionStep("比对鱼种特征", stage >= 3, stage == 3 && error == null)
        }

        if (error != null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!, color = Color(0xFFB24A3A), fontSize = 13.sp)
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(50.dp), shape = RoundedCornerShape(25.dp), colors = ButtonDefaults.buttonColors(containerColor = WaterTeal)) {
                    Text("重新选择照片", fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 64.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("检测与识别都在手机本地完成", color = MutedInk, fontSize = 12.sp)
                Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(7.dp).background(Color(0xFFE1E6E4), RoundedCornerShape(50))) {
                    val fraction = when (stage) { 1 -> .34f; 2 -> .68f; 3 -> .92f; else -> .05f }
                    Box(Modifier.fillMaxWidth(fraction).height(7.dp).background(WaterTeal, RoundedCornerShape(50)))
                }
            }
        }
    }
}

@Composable
private fun RecognitionStep(label: String, completed: Boolean, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).background(if (completed) WaterTeal else Color(0xFFE7ECEA), CircleShape), contentAlignment = Alignment.Center) {
            Text(if (completed) "✓" else "·", color = if (completed) Color.White else MutedInk, fontWeight = FontWeight.Bold)
        }
        Text(label, color = DeepInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 14.dp))
        Text(when { active -> "正在进行"; completed -> "完成"; else -> "等待" }, color = if (active) Color(0xFFFF8A34) else if (completed) WaterTeal else MutedInk, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
