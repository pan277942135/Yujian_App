package com.yujian.ai.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.identify.RecognitionUiState
import com.yujian.ai.ui.identify.resolveRecognitionUiState
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.WarmBackground
import com.yujian.ai.ui.theme.WaterTeal

@Composable
fun RecognitionIssueScreen(
    image: SelectedImage?,
    result: ProductionRecognitionResult?,
    technicalFailure: Boolean = false,
    onBack: () -> Unit,
    onChooseAnother: () -> Unit,
    onChooseGallery: () -> Unit,
    onRetry: () -> Unit,
) {
    val state = result?.let(::resolveRecognitionUiState)
    val compactHeight = LocalConfiguration.current.screenHeightDp < 700
    val heroHeight = if (compactHeight) 276.dp else 340.dp
    val panelOuterPadding = if (compactHeight) 14.dp else 20.dp
    val panelInnerPadding = if (compactHeight) 18.dp else 24.dp
    val panelSpacing = if (compactHeight) 10.dp else 14.dp
    val actionHeight = if (compactHeight) 50.dp else 54.dp
    val copy = when {
        technicalFailure || state == RecognitionUiState.TECHNICAL_FAILURE ->
            "识别没有完成" to "请重新拍摄或选择照片。"
        state == RecognitionUiState.ERROR_NO_FISH ->
            "没有找到可识别的鱼" to "请让鱼完整出现在画面中，再试一次。"
        else ->
            "照片不够清晰，无法识别" to "请拍摄更清晰的照片，确保鱼的整体轮廓清晰、没有遮挡。"
    }

    Column(
        Modifier.fillMaxSize().background(WarmBackground),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        image?.let {
            Image(
                bitmap = it.bitmap.asImageBitmap(),
                contentDescription = "本次识别照片",
                modifier = Modifier.fillMaxWidth().height(heroHeight).padding(horizontal = 20.dp).clip(RoundedCornerShape(28.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(panelOuterPadding).background(Color(0xE6F4F8FC), RoundedCornerShape(24.dp)).padding(panelInnerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(panelSpacing),
        ) {
            Text(copy.first, color = DeepInk, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(copy.second, color = MutedInk, fontSize = 15.sp, lineHeight = 23.sp)
            Button(
                onClick = onChooseAnother,
                modifier = Modifier.fillMaxWidth().height(actionHeight),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WaterTeal),
            ) { Text("重新拍摄", fontWeight = FontWeight.SemiBold) }
            OutlinedButton(
                onClick = onChooseGallery,
                modifier = Modifier.fillMaxWidth().height(actionHeight),
                shape = RoundedCornerShape(27.dp),
            ) { Text("从相册选择", color = DeepInk) }
        }
        Spacer(Modifier.weight(1f))
        Text("‹ 返回", color = MutedInk, modifier = Modifier
            .clickable(onClick = onBack)
            .padding(bottom = 22.dp)
            .padding(12.dp))
    }
}
