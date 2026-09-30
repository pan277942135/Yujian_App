package com.yujian.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.designsystem.components.YuJianActionButtonVariant
import com.yujian.ai.ui.designsystem.components.YuJianBackTitleTopBar
import com.yujian.ai.ui.designsystem.components.YuJianPrimaryButton
import com.yujian.ai.ui.identify.RecognitionUiState
import com.yujian.ai.ui.identify.resolveRecognitionUiState
import com.yujian.ai.ui.recognition.result.RecognitionResultGeometryResolver
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.MutedInk

@Composable
fun RecognitionIssueScreen(
    image: SelectedImage?,
    result: ProductionRecognitionResult?,
    technicalFailure: Boolean = false,
    onBack: () -> Unit,
    onChooseAnother: () -> Unit,
    onChooseGallery: () -> Unit,
) {
    val state = result?.let(::resolveRecognitionUiState)
    val geometry = RecognitionResultGeometryResolver.resolve(
        androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp,
        androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp,
    )
    val copy = when {
        technicalFailure || state == RecognitionUiState.TECHNICAL_FAILURE ->
            "识别没有完成" to "请重新拍摄或选择照片。"
        state == RecognitionUiState.ERROR_NO_FISH ->
            "没有找到可识别的鱼" to "请让鱼完整出现在画面中，再试一次。"
        else ->
            "照片不够清晰，无法识别" to "请拍摄更清晰的照片，确保鱼的整体轮廓清晰、没有遮挡。"
    }
    Box(Modifier.fillMaxSize()) {
        BgContentSurface()
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            YuJianBackTitleTopBar(title = "识别结果", onBack = onBack)
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                Spacer(Modifier.height(8.dp))
                image?.let {
                    ResultHeroViewport(
                        bitmap = it.bitmap.asImageBitmap(), bbox = null,
                        widthDp = geometry.heroWidthDp, heightDp = geometry.heroHeightDp,
                        evidenceFirst = true,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = geometry.horizontalMarginDp.dp)
                        .heightIn(min = 128.dp).background(Color(0xDDF7FAFB), RoundedCornerShape(20.dp)).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(copy.first, color = DeepInk, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
                    Text(copy.second, color = MutedInk, fontSize = 15.sp, lineHeight = 23.sp)
                }
                Spacer(Modifier.height(20.dp))
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    YuJianPrimaryButton(
                        text = "重新拍摄", onClick = onChooseAnother,
                        variant = YuJianActionButtonVariant.PRIMARY,
                    )
                    YuJianPrimaryButton(
                        text = "从相册选择", onClick = onChooseGallery,
                        variant = YuJianActionButtonVariant.SECONDARY_STRONG,
                    )
                }
            }
        }
    }
}
