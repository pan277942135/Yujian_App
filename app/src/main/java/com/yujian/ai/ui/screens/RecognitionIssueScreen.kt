package com.yujian.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.designsystem.components.YuJianActionButtonVariant
import com.yujian.ai.ui.designsystem.components.YuJianBackCenterTitleTopBar
import com.yujian.ai.ui.designsystem.components.YuJianPrimaryButton
import com.yujian.ai.ui.identify.RecognitionUiState
import com.yujian.ai.ui.identify.resolveRecognitionUiState
import com.yujian.ai.ui.adaptive.rememberAdaptiveLayoutProfile
import com.yujian.ai.ui.adaptive.rememberSafeDrawingInsets
import com.yujian.ai.ui.recognition.result.RecognitionResultGeometryResolver
import com.yujian.ai.ui.recognition.result.RecognitionResultVisualState
import com.yujian.ai.ui.recognition.result.usesSourceDerivedHeroBackdrop
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
    val configuration = LocalConfiguration.current
    val safeInsets = rememberSafeDrawingInsets()
    val adaptiveProfile = rememberAdaptiveLayoutProfile(
        configuration.screenWidthDp.dp,
        configuration.screenHeightDp.dp,
    )
    val isNoFish = !technicalFailure && state == RecognitionUiState.ERROR_NO_FISH
    val visualState = if (isNoFish) {
        RecognitionResultVisualState.NO_FISH
    } else {
        RecognitionResultVisualState.IMAGE_QUALITY
    }
    val geometry = RecognitionResultGeometryResolver.resolve(adaptiveProfile, visualState)
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
            YuJianBackCenterTitleTopBar(title = "识别结果", onBack = onBack)
            Column(
                Modifier.fillMaxWidth().weight(1f)
                    .padding(start = safeInsets.start, end = safeInsets.end)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 20.dp + safeInsets.bottom),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                Spacer(Modifier.height(8.dp))
                image?.let {
                    ResultHeroViewport(
                        bitmap = it.bitmap.asImageBitmap(), bbox = null,
                        widthDp = geometry.heroWidthDp, heightDp = geometry.heroHeightDp,
                        evidenceFirst = true,
                        sourceBackdropEnabled = visualState.usesSourceDerivedHeroBackdrop(),
                    )
                }
                Spacer(Modifier.height(16.dp))
                ResultRecoverySurface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = geometry.horizontalMarginDp.dp),
                    fillAlpha = if (isNoFish) 0.89f else 0.91f,
                    borderAlpha = if (isNoFish) 0.80f else 0.84f,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            copy.first,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = DeepInk,
                            fontSize = 22.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            copy.second,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = MutedInk,
                            fontSize = 15.sp,
                            lineHeight = 23.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        // RR04 draws these actions directly on its Result surface, avoiding the shared Material button's extra surface/elevation layer.
                        if (isNoFish) {
                            ResultSurfaceActionButton(
                                text = "重新拍摄",
                                icon = Icons.Rounded.CameraAlt,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = true,
                                loading = false,
                                compactLayout = geometry.heroWidthDp <= 322,
                                emphasized = true,
                                testTag = "recognition-no-fish-retake",
                                onClick = onChooseAnother,
                            )
                        } else {
                            YuJianPrimaryButton(
                                text = "重新拍摄", onClick = onChooseAnother,
                                modifier = Modifier.fillMaxWidth(),
                                variant = YuJianActionButtonVariant.PRIMARY,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.CameraAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                            )
                        }
                        if (isNoFish) {
                            ResultSurfaceActionButton(
                                text = "从相册选择",
                                icon = Icons.Rounded.PhotoLibrary,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = true,
                                loading = false,
                                compactLayout = geometry.heroWidthDp <= 322,
                                emphasized = false,
                                testTag = "recognition-no-fish-gallery",
                                onClick = onChooseGallery,
                            )
                        } else {
                            YuJianPrimaryButton(
                                text = "从相册选择", onClick = onChooseGallery,
                                modifier = Modifier.fillMaxWidth(),
                                variant = YuJianActionButtonVariant.SECONDARY_STRONG,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.PhotoLibrary,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
