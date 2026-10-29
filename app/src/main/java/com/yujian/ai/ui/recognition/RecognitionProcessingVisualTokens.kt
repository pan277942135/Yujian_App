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
    val statusHeight = 88.dp
    val statusHorizontalPadding = 20.dp
    val statusIndicatorSize = 54.dp
    val statusIndicatorStroke = 4.dp
    const val energyRingCycleMs = 1_600L
    const val energyRingSweepDegrees = 112f
}

internal fun recognitionEnergyRingStartAngle(clockMs: Long, reduceMotion: Boolean): Float {
    if (reduceMotion) return -90f
    val cyclePosition = ((clockMs % RecognitionProcessingVisualTokens.energyRingCycleMs) +
        RecognitionProcessingVisualTokens.energyRingCycleMs) %
        RecognitionProcessingVisualTokens.energyRingCycleMs
    return -90f + cyclePosition / RecognitionProcessingVisualTokens.energyRingCycleMs.toFloat() * 360f
}

internal data class RecognitionStatusCopy(
    val title: String,
    val subtitle: String,
)

internal fun recognitionStatusCopy(visualPhase: RecognitionPhase): RecognitionStatusCopy = when (visualPhase) {
    RecognitionPhase.CAPTURED -> RecognitionStatusCopy("正在准备识别", "AI 已获取这张照片")
    RecognitionPhase.DETECTING -> RecognitionStatusCopy("正在理解这张照片", "寻找这次鱼获的线索")
    RecognitionPhase.OUTLINE -> RecognitionStatusCopy("已定位到鱼体", "正在分析这次鱼获")
    RecognitionPhase.CLASSIFYING -> RecognitionStatusCopy("正在认识这条鱼", "分析鱼体特征")
    RecognitionPhase.RESULT -> RecognitionStatusCopy("", "")
    RecognitionPhase.FAILURE -> RecognitionStatusCopy("识别没有完成", "请重新拍摄或选择照片")
}

