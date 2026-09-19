package com.yujian.ai.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.ai.FishInputStatus
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.identify.IdentifyState
import com.yujian.ai.ui.identify.resolveIdentifyResultState
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.SoftWater
import com.yujian.ai.ui.theme.WarmBackground
import com.yujian.ai.ui.theme.WaterTeal

@Composable
fun RecognitionIssueScreen(
    image: SelectedImage?,
    result: ProductionRecognitionResult,
    onBack: () -> Unit,
    onChooseAnother: () -> Unit,
    onChooseGallery: () -> Unit,
    onRetry: () -> Unit,
) {
    val state = resolveIdentifyResultState(result.status, result.prediction)
    val copy = when (state) {
        IdentifyState.NO_FISH -> "没有找到可识别的鱼获主体" to "让鱼体尽量完整地进入画面，再试一次。"
        IdentifyState.TOO_FAR -> "这条鱼离得有点远" to "靠近一点再拍，鱼体会更容易被识别。"
        else -> when (result.status) {
            FishInputStatus.MULTIPLE_FISH -> "画面里有不止一条鱼" to "换一张主体更明确的照片。"
            FishInputStatus.INCOMPLETE_FISH -> "鱼体没有完整进入画面" to "尽量保留鱼头、鱼尾和主要鳍部。"
            else -> "这张照片还不够确定" to "换一张光线更好、遮挡更少的照片试试。"
        }
    }

    Column(
        Modifier.fillMaxSize().background(WarmBackground),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "再看一眼",
            color = DeepInk,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            "没有关系，重新拍一张就好",
            color = MutedInk,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
        )
        image?.let {
            Image(
                bitmap = it.bitmap.asImageBitmap(),
                contentDescription = "本次识别照片",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(SoftWater),
                contentScale = ContentScale.Fit,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .background(Color.White.copy(alpha = 0.72f), RoundedCornerShape(24.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(copy.first, color = DeepInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(copy.second, color = MutedInk, fontSize = 14.sp, lineHeight = 22.sp)
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onChooseAnother,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WaterTeal),
            ) { Text("重新拍摄", fontWeight = FontWeight.SemiBold) }
            OutlinedButton(
                onClick = onChooseGallery,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(26.dp),
            ) { Text("从相册选择") }
        }
    }
}
