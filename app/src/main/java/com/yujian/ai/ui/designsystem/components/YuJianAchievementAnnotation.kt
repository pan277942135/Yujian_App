package com.yujian.ai.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.ui.mycatches.GrowthMark
import com.yujian.ai.ui.theme.AchievementInk

@Composable
fun YuJianAchievementAnnotation(annotation: GrowthMark, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color(0xFFEDE9D8).copy(alpha = 0.72f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(annotation.text, color = AchievementInk.copy(alpha = 0.82f), fontSize = 11.sp)
    }
}
