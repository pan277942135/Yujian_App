package com.yujian.ai.ui.fishguide

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yujian.ai.ui.designsystem.color.YuJianColors

@Composable
fun FishGuideProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    durationMillis: Int = 300,
) {
    val progress by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = if (reduceMotion) 100 else durationMillis),
        label = "FishGuideDiscoveryProgress",
    )
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(shape)
            .background(YuJianColors.MistBlueGray.copy(alpha = 0.18f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(6.dp)
                .clip(shape)
                .background(YuJianColors.MorningGold.copy(alpha = 0.72f)),
        )
    }
}
