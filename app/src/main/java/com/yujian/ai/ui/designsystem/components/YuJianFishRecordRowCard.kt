package com.yujian.ai.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.components.FishIllustration
import com.yujian.ai.ui.components.RemoteImage
import com.yujian.ai.ui.mycatches.FishRecordPresentation
import com.yujian.ai.ui.theme.CardWhite
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.FishGreen
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.SoftWater

@Composable
fun YuJianFishRecordRowCard(
    record: RemoteCatch,
    presentation: FishRecordPresentation,
    accessToken: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardWhite.copy(alpha = 0.90f))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(82.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SoftWater),
                contentAlignment = Alignment.Center,
            ) {
                RemoteImage(
                    url = presentation.imageUrl,
                    authToken = accessToken,
                    modifier = Modifier.size(82.dp),
                    contentDescription = "${presentation.speciesName} 鱼获照片",
                    contentScale = ContentScale.Crop,
                ) {
                    FishIllustration(size = 50.dp, bodyColor = FishGreen.copy(alpha = 0.62f))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(presentation.speciesName, color = DeepInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                presentation.measurementLabel?.let { Text(it, color = DeepInk.copy(alpha = 0.82f), fontSize = 12.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presentation.location?.let { Text(it, color = MutedInk, fontSize = 11.sp, maxLines = 1) }
                    Text(presentation.dateLabel, color = MutedInk, fontSize = 11.sp)
                }
            }
            Text("›", color = MutedInk, fontSize = 28.sp, modifier = Modifier.widthIn(min = 24.dp))
        }
        if (presentation.annotations.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presentation.annotations.forEach { annotation -> YuJianAchievementAnnotation(annotation) }
            }
        }
    }
}
