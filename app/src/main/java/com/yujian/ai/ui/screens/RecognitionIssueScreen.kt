package com.yujian.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.ai.FishInputStatus
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.components.YujianTopBar
import com.yujian.ai.ui.theme.CardWhite
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.WarmBackground
import com.yujian.ai.ui.theme.WaterTeal

private data class IssueCopy(val title: String, val body: String)

@Composable
fun RecognitionIssueScreen(
    image: SelectedImage?,
    result: ProductionRecognitionResult,
    onBack: () -> Unit,
    onChooseAnother: () -> Unit,
    onRetry: () -> Unit,
) {
    val copy = when (result.status) {
        FishInputStatus.NO_FISH -> IssueCopy(
            "没有检测到鱼",
            "请重新拍摄或选择包含鱼的照片。",
        )
        FishInputStatus.UNCERTAIN -> IssueCopy(
            "没有确认到清晰鱼体",
            "照片里可能有鱼，但当前画面不够确定。请换一张鱼体更清晰的照片。",
        )
        FishInputStatus.MULTIPLE_FISH -> IssueCopy(
            "检测到多条鱼",
            "请重新拍摄单条鱼，或选择主体更明确的照片。",
        )
        FishInputStatus.INCOMPLETE_FISH -> IssueCopy(
            "鱼体没有完整进入画面",
            "请尽量让鱼头、鱼尾和主要鳍部完整出现在照片中。",
        )
        FishInputStatus.FISH_TOO_SMALL -> IssueCopy(
            "鱼离镜头有点远",
            "靠近一点再拍，更容易准确识别鱼种。",
        )
        FishInputStatus.READY -> IssueCopy(
            "鱼体已准备好",
            "正在进入鱼种识别。",
        )
    }

    Column(Modifier.fillMaxSize().background(WarmBackground)) {
        YujianTopBar(title = "检查照片", subtitle = "先确认鱼体，再判断鱼种", onBack = onBack)
        if (image != null) {
            DetectorOverlayImage(
                bitmap = image.bitmap,
                detectorBox = result.assessment.primary?.box,
                cropBox = result.assessment.cropBox,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).height(290.dp)
                    .clip(RoundedCornerShape(28.dp)),
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp).background(CardWhite, RoundedCornerShape(24.dp)).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(copy.title, color = DeepInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(copy.body, color = MutedInk, fontSize = 14.sp, lineHeight = 22.sp)
            Text(
                "Quality Gate ${result.assessment.qualityLevel.name} · ${result.assessment.qualityReason}",
                color = Color(0xFFB24A3A),
                fontSize = 11.sp,
            )
            result.assessment.primary?.let { primary ->
                Text(
                    "鱼体检测置信度 ${(primary.confidence * 100).toInt()}%",
                    color = WaterTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                "Detector ${result.detectorRun.modelVersion} · ${result.detectorRun.latencyMs} ms",
                color = Color(0xFF89938F),
                fontSize = 11.sp,
            )
        }

        Spacer(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = onChooseAnother,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WaterTeal),
            ) { Text("重新选择照片", fontWeight = FontWeight.SemiBold) }
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(24.dp),
            ) { Text("再检测一次") }
        }
    }
}
