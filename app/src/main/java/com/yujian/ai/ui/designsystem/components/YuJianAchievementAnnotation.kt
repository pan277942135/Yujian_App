package com.yujian.ai.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.ui.mycatches.GrowthMark
import com.yujian.ai.ui.mycatches.GrowthMarkType

@Composable
fun YuJianAchievementAnnotation(annotation: GrowthMark, modifier: Modifier = Modifier) {
    val ink = when (annotation.type) {
        GrowthMarkType.CountMilestone -> Color(0xFF8A713A)
        GrowthMarkType.FirstSpecies -> Color(0xFF697E72)
        GrowthMarkType.Longest, GrowthMarkType.Heaviest -> Color(0xFF607D87)
    }
    val surface = when (annotation.type) {
        GrowthMarkType.CountMilestone -> Color(0xFFF1E8CF)
        GrowthMarkType.FirstSpecies -> Color(0xFFE6ECE7)
        GrowthMarkType.Longest, GrowthMarkType.Heaviest -> Color(0xFFE4ECEE)
    }
    Row(
        modifier = modifier
            .heightIn(min = 22.dp)
            .background(surface.copy(alpha = 0.78f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(annotation.text, color = ink.copy(alpha = 0.88f), fontSize = 10.sp, maxLines = 1)
    }
}
