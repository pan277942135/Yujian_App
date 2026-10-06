package com.yujian.ai.ui.recognition

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yujian.ai.ai.RecognitionPhase

internal object RecognitionProcessingVisualTokens {
    val statusSurface = Color(0xD91A2C35)
    val statusSecondary = Color(0xFFBFD0D7)
    val statusGold = Color(0xFFFFE7AE)
    val statusGoldCore = Color(0xFFF6D99B)
    val statusBlueCore = Color(0xFFBFEAF2)
    val statusBlueHot = Color(0xFFD8F5FA)
    val statusShape = RoundedCornerShape(30.dp)
    val statusHeight = 112.dp
    val statusHorizontalPadding = 28.dp
    val statusIndicatorSize = 54.dp
    val statusIndicatorStroke = 4.dp
}

internal data class RecognitionStatusCopy(
    val title: String,
    val subtitle: String,
)

internal fun recognitionStatusCopy(visualPhase: RecognitionPhase): RecognitionStatusCopy = when (visualPhase) {
    RecognitionPhase.CAPTURED,
    RecognitionPhase.DETECTING -> RecognitionStatusCopy("图片识别中", "正在理解照片并寻找鱼获线索")
    RecognitionPhase.OUTLINE -> RecognitionStatusCopy("已定位到鱼体", "正在分析这次鱼获")
    RecognitionPhase.CLASSIFYING -> RecognitionStatusCopy("正在认识这条鱼", "分析鱼体特征")
    RecognitionPhase.RESULT -> RecognitionStatusCopy("", "")
    RecognitionPhase.FAILURE -> RecognitionStatusCopy("识别没有完成", "请重新拍摄或选择照片")
}

