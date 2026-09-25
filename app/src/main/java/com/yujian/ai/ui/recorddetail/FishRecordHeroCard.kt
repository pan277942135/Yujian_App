package com.yujian.ai.ui.recorddetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.components.RemoteImage
import com.yujian.ai.ui.designsystem.components.YuJianHeroCard
import com.yujian.ai.ui.designsystem.components.YuJianHeroVariant

@Composable
fun FishRecordHeroCard(
    record: RemoteCatch,
    imageUrl: String?,
    accessToken: String,
    onEdit: () -> Unit,
) {
    val metadata = FishRecordDetailPresentation.measurement(record)?.let { measurement ->
        listOfNotNull(measurement, FishRecordDetailPresentation.location(record))
    } ?: listOfNotNull(FishRecordDetailPresentation.location(record))

    YuJianHeroCard(
        title = record.speciesName.ifBlank { "鱼获" },
        metadata = metadata,
        variant = YuJianHeroVariant.DETAIL,
        editLabel = "编辑 >",
        onClick = onEdit,
        media = {
            RemoteImage(
                url = imageUrl,
                authToken = accessToken,
                modifier = Modifier.fillMaxSize(),
                contentDescription = "${record.speciesName} 鱼获照片",
                contentScale = ContentScale.Crop,
                placeholder = {
                    Box(Modifier.fillMaxSize().background(Color(0xFF6D8491)))
                },
            )
        },
    )
}
