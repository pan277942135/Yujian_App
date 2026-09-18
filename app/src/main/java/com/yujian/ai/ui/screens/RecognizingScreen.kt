package com.yujian.ai.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.yujian.ai.ai.FishInputAssessment
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ai.RecognitionProgress
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.theme.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RecognizingScreen(
    image: SelectedImage?,
    onBack: () -> Unit,
    recognize: suspend ((RecognitionProgress) -> Unit) -> ProductionRecognitionResult,
    onFinished: (ProductionRecognitionResult) -> Unit,
) {
    var phase by remember { mutableStateOf(RecognitionPhase.CAPTURED) }
    var assessment by remember { mutableStateOf<FishInputAssessment?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(image?.filePath) {
        if (image == null) {
            error = "没有可识别的照片"
            return@LaunchedEffect
        }
        phase = RecognitionPhase.CAPTURED
        assessment = null
        error = null
        coroutineScope {
            val recognition = async {
                runCatching {
                    recognize { progress ->
                        phase = progress.phase
                        progress.assessment?.let { assessment = it }
                    }
                }
            }
            launch {
                delay(800)
                if (phase.ordinal < RecognitionPhase.DETECTING.ordinal) phase = RecognitionPhase.DETECTING
            }
            launch {
                delay(1500)
                if (phase.ordinal < RecognitionPhase.OUTLINE.ordinal) phase = RecognitionPhase.OUTLINE
            }
            launch {
                delay(2300)
                if (phase.ordinal < RecognitionPhase.CLASSIFYING.ordinal) phase = RecognitionPhase.CLASSIFYING
            }
            launch {
                delay(3000)
                if (phase.ordinal < RecognitionPhase.RESULT.ordinal) phase = RecognitionPhase.RESULT
            }
            recognition.await()
                .onSuccess(onFinished)
                .onFailure { error = it.message ?: "识别失败，请重新选择照片" }
        }
    }

    Column(Modifier.fillMaxSize().background(WarmBackground)) {
        Text(
            "正在认识这条鱼",
            color = DeepInk,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 24.dp),
        )
        Text(
            if (error == null) "先找鱼，再识别鱼种" else "这次没有完成识别",
            color = MutedInk,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 10.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(312.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(SoftWater),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                if (assessment?.primary != null) {
                    DetectorOverlayImage(
                        bitmap = image.bitmap,
                        detectorBox = assessment?.primary?.box,
                        cropBox = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Image(
                        image.bitmap.asImageBitmap(),
                        "正在识别的鱼获照片",
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .10f)))
            }
            Text(
                phaseLabel(phase),
                color = WaterTeal,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(18.dp)
                    .background(Color.White.copy(alpha = .88f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .background(CardWhite, RoundedCornerShape(24.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StageRow("拍好的照片", phase.ordinal >= RecognitionPhase.CAPTURED.ordinal, phase == RecognitionPhase.CAPTURED)
            StageRow("寻找鱼体", phase.ordinal >= RecognitionPhase.DETECTING.ordinal, phase == RecognitionPhase.DETECTING)
            StageRow("勾勒鱼体轮廓", phase.ordinal >= RecognitionPhase.OUTLINE.ordinal, phase == RecognitionPhase.OUTLINE)
            StageRow("比对鱼种特征", phase.ordinal >= RecognitionPhase.CLASSIFYING.ordinal, phase == RecognitionPhase.CLASSIFYING)
        }

        if (error != null) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error.orEmpty(), color = Color(0xFFB24A3A), fontSize = 13.sp)
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WaterTeal),
                ) { Text("重新选择照片", fontWeight = FontWeight.SemiBold) }
            }
        } else {
            Text(
                "检测与识别都在手机本地完成",
                color = MutedInk,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

private fun phaseLabel(phase: RecognitionPhase): String = when (phase) {
    RecognitionPhase.CAPTURED -> "已拍下"
    RecognitionPhase.DETECTING -> "正在找鱼"
    RecognitionPhase.OUTLINE -> "鱼体已找到"
    RecognitionPhase.CLASSIFYING -> "正在比对鱼种"
    RecognitionPhase.RESULT -> "认识完成"
}

@Composable
private fun StageRow(label: String, completed: Boolean, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (completed) "✓" else "·",
            color = if (completed) WaterTeal else MutedInk,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(24.dp),
        )
        Text(
            label,
            color = DeepInk,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            when {
                active -> "正在进行"
                completed -> "完成"
                else -> "等待"
            },
            color = if (active) Color(0xFFC47832) else if (completed) WaterTeal else MutedInk,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
