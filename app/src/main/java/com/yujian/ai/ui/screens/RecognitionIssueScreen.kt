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
import com.yujian.ai.ui.identify.IdentifyState
import com.yujian.ai.ui.identify.resolveIdentifyResultState
import com.yujian.ai.ui.theme.*

@Composable
fun RecognitionIssueScreen(
    image: SelectedImage?,
    result: ProductionRecognitionResult,
    onBack: () -> Unit,
    onChooseAnother: () -> Unit,
    onRetry: () -> Unit,
) {
    val state = resolveIdentifyResultState(result.status, result.prediction)
    val copy = when (state) {
        IdentifyState.NO_FISH -> "没有找到鱼获主体" to "请重新拍摄，尽量让鱼体完整地进入画面。"
        IdentifyState.TOO_FAR -> "鱼距离太远" to "靠近一点再拍，鱼体会更容易被识别。"
        else -> when (result.status) {
            FishInputStatus.MULTIPLE_FISH -> "画面里有不止一条鱼" to "请换一张主体更明确的照片。"
            FishInputStatus.INCOMPLETE_FISH -> "鱼体没有完整进入画面" to "请尽量保留鱼头、鱼尾和主要鳍部。"
            else -> "这张照片还不够确定" to "换一张光线更好、遮挡更少的照片试试。"
        }
    }

    Column(Modifier.fillMaxSize().background(WarmBackground)) {
        Text("再看一眼", color = DeepInk, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 24.dp))
        Text("没有关系，重新拍一张就好", color = MutedInk, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 10.dp))
        if (image != null) {
            DetectorOverlayImage(
                bitmap = image.bitmap,
                detectorBox = result.assessment.primary?.box,
                cropBox = null,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(290.dp).clip(RoundedCornerShape(28.dp)),
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(20.dp).background(CardWhite, RoundedCornerShape(24.dp)).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(copy.first, color = DeepInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(copy.second, color = MutedInk, fontSize = 14.sp, lineHeight = 22.sp)
            result.assessment.primary?.let {
                Text("已经找到鱼体主体，但画面需要更近一点", color = WaterTeal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = onChooseAnother, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(26.dp), colors = ButtonDefaults.buttonColors(containerColor = WaterTeal)) { Text("重新选择照片", fontWeight = FontWeight.SemiBold) }
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(24.dp)) { Text("再检测一次") }
        }
    }
}
