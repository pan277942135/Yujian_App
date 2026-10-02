package com.yujian.ai.ui.recognition

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.ai.RecognitionPhase

private val StatusGold = Color(0xFFFFE7AE)

/**
 * Three-state Recognition status overlay.
 *
 * CAPTURED and DETECTING deliberately share one visible product state:
 * 图片识别中. RESULT is routing-only and never shows a fourth status card.
 */
@Composable
fun RecognitionStatusOverlay(
    phase: RecognitionPhase,
    modifier: Modifier = Modifier,
    resolveProgress: Float = 0f,
    reduceMotion: Boolean = false,
) {
    if (phase == RecognitionPhase.RESULT) return

    val copy = when (phase) {
        RecognitionPhase.CAPTURED,
        RecognitionPhase.DETECTING ->
            "图片识别中" to "正在理解照片并寻找鱼获线索"
        RecognitionPhase.OUTLINE ->
            "已定位到鱼体" to "正在分析这次鱼获"
        RecognitionPhase.CLASSIFYING ->
            "鱼种识别中" to "正在分析鱼体特征"
        RecognitionPhase.RESULT -> return
        RecognitionPhase.FAILURE ->
            "识别没有完成" to "请重新拍摄或选择照片"
    }

    val opening = phase == RecognitionPhase.CAPTURED || phase == RecognitionPhase.DETECTING
    val shape = RoundedCornerShape(if (opening) 26.dp else 30.dp)
    val energyBorder = Brush.horizontalGradient(
        colors = listOf(
            Color(0x70FFE7AE),
            Color(0x42F6D99B),
            Color(0x42BFEAF2),
            Color(0x70D8F5FA),
        ),
    )

    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = 1f - resolveProgress.coerceIn(0f, 1f)
            }
            .then(
                if (opening) {
                    Modifier
                        .clip(shape)
                        .background(Color(0x3D10262D))
                } else {
                    Modifier
                        .height(112.dp)
                        .clip(shape)
                        .background(Color(0xA31A2C35))
                        .border(width = .75.dp, brush = energyBorder, shape = shape)
                },
            )
            .padding(
                horizontal = if (opening) 22.dp else 28.dp,
                vertical = if (opening) 14.dp else 0.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (opening) 18.dp else 16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(if (opening) 40.dp else 54.dp),
        ) {
            if (reduceMotion) {
                Box(
                    Modifier
                        .matchParentSize()
                        .border(
                            width = if (opening) 3.dp else 4.dp,
                            color = StatusGold,
                            shape = CircleShape,
                        ),
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.matchParentSize(),
                    color = StatusGold,
                    trackColor = Color(0x668A979B),
                    strokeWidth = if (opening) 3.dp else 4.dp,
                )
            }
            Box(
                Modifier
                    .size(if (opening) 9.dp else 10.dp)
                    .clip(CircleShape)
                    .background(StatusGold),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                copy.first,
                color = Color.White,
                fontSize = if (opening) 18.sp else 20.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                copy.second,
                color = Color(0xFFBFD0D7),
                fontSize = 14.sp,
            )
        }
    }
}
