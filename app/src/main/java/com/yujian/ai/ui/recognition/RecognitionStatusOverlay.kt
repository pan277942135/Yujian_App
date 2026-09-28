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
private val StatusBlue = Color(0xFFD8F5FA)

/**
 * Processing status remains subordinate to the photo/fish focus.
 * V1.2 only links the dark glass capsule to the same blue-gold energy language;
 * copy, size, hierarchy and semantics remain frozen.
 */
@Composable
fun RecognitionStatusOverlay(
    phase: RecognitionPhase,
    modifier: Modifier = Modifier,
    resolveProgress: Float = 0f,
    reduceMotion: Boolean = false,
) {
    // RESULT is a routing state only. Never expose a fifth processing card.
    if (phase == RecognitionPhase.RESULT) return

    val copy = when (phase) {
        RecognitionPhase.CAPTURED -> "正在准备识别" to "AI 已获取这张照片"
        RecognitionPhase.DETECTING -> "正在理解这张照片" to "寻找这次鱼获的线索"
        RecognitionPhase.OUTLINE -> "已定位到鱼体" to "正在分析这次鱼获"
        RecognitionPhase.CLASSIFYING -> "正在认识这条鱼" to "分析鱼体特征"
        RecognitionPhase.RESULT -> return
        RecognitionPhase.FAILURE -> "识别没有完成" to "请重新拍摄或选择照片"
    }

    val light = phase == RecognitionPhase.DETECTING
    val shape = RoundedCornerShape(30.dp)
    val energyBorder = Brush.horizontalGradient(
        colors = listOf(
            Color(0xB8FFE7AE),
            Color(0x70F6D99B),
            Color(0x70BFEAF2),
            Color(0xB8D8F5FA),
        ),
    )

    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = 1f - resolveProgress.coerceIn(0f, 1f) }
            .then(
                if (light) {
                    Modifier
                } else {
                    Modifier
                        .height(112.dp)
                        .clip(shape)
                        .background(Color(0xD91A2C35))
                        .border(width = 1.dp, brush = energyBorder, shape = shape)
                },
            )
            .padding(
                horizontal = if (light) 22.dp else 28.dp,
                vertical = if (light) 14.dp else 0.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(if (light) 40.dp else 54.dp),
        ) {
            if (reduceMotion) {
                Box(
                    Modifier
                        .matchParentSize()
                        .border(
                            width = if (light) 3.dp else 4.dp,
                            color = StatusGold,
                            shape = CircleShape,
                        ),
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.matchParentSize(),
                    color = StatusGold,
                    trackColor = Color(0x668A979B),
                    strokeWidth = if (light) 3.dp else 4.dp,
                )
            }
            Box(
                Modifier
                    .size(if (light) 9.dp else 12.dp)
                    .clip(CircleShape)
                    .background(StatusGold),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                copy.first,
                color = Color.White,
                fontSize = if (light) 18.sp else 20.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(copy.second, color = Color(0xFFBFD0D7), fontSize = 14.sp)
        }
    }
}
