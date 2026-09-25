package com.yujian.ai.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.yujian.ai.ai.RecognitionFailureCode
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.identify.IdentifyState
import com.yujian.ai.ui.identify.resolveIdentifyResultState
import com.yujian.ai.ui.recognition.RecognitionErrorState
import com.yujian.ai.ui.recognition.RecognitionPhotoBackdrop
import com.yujian.ai.ui.recognition.RecognitionPresentation
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
    val errorState = RecognitionPresentation.errorState(result)
    val copy = when (errorState) {
        RecognitionErrorState.NO_FISH ->
            "没有找到可识别的鱼" to "请让鱼完整出现在画面中，再试一次。"
        RecognitionErrorState.TECHNICAL -> when (result.failureCode) {
            RecognitionFailureCode.CLASSIFIER_FAILED -> "识别模型暂时没有完成" to "请保持网络和设备状态稳定，再试一次。"
            RecognitionFailureCode.INVALID_CROP -> "照片主体没有形成有效取景" to "换一张主体更完整的照片试试。"
            else -> "识别没有完成" to "请重新拍摄或从相册选择一张照片。"
        }
        RecognitionErrorState.IMAGE_QUALITY -> when (result.status) {
            FishInputStatus.FISH_TOO_SMALL -> "鱼体距离太远，无法识别" to "请靠近一点，让鱼体完整出现在画面中。"
            FishInputStatus.MULTIPLE_FISH -> "画面里有不止一条鱼" to "换一张主体更明确的照片。"
            FishInputStatus.INCOMPLETE_FISH -> "鱼体没有完整进入画面" to "尽量保留鱼头、鱼尾和主要鳍部。"
            else -> "照片不够清晰，无法识别" to "请拍摄更清晰、轮廓完整且没有遮挡的照片。"
        }
    }

    Box(Modifier.fillMaxSize()) {
        image?.let {
            RecognitionPhotoBackdrop(it.bitmap, Modifier.fillMaxSize())
        }
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = DeepInk)
                }
                Text("识别结果", color = DeepInk, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            image?.let {
                Image(
                    bitmap = it.bitmap.asImageBitmap(),
                    contentDescription = "本次识别照片",
                    modifier = Modifier.fillMaxWidth().height(290.dp).padding(horizontal = 20.dp).clip(RoundedCornerShape(28.dp)).background(SoftWater),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(
                Modifier.fillMaxWidth().padding(20.dp).background(Color.White.copy(alpha = 0.82f), RoundedCornerShape(24.dp)).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(copy.first, color = DeepInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(copy.second, color = MutedInk, fontSize = 14.sp, lineHeight = 22.sp)
                Button(
                    onClick = onChooseAnother,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WaterTeal),
                ) { Text("重新拍摄", fontWeight = FontWeight.SemiBold) }
                OutlinedButton(
                    onClick = onChooseGallery,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                ) { Text("从相册选择") }
            }
        }
    }
}
