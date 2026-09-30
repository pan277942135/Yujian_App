package com.yujian.ai.ui.recorddetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.components.RemoteImage
import com.yujian.ai.ui.designsystem.components.YuJianHeroCard
import com.yujian.ai.ui.designsystem.components.YuJianHeroVariant
import com.yujian.ai.ui.designsystem.components.YuJianIconAction
import com.yujian.ai.ui.designsystem.components.YuJianIconActionFamily
import com.yujian.ai.ui.designsystem.components.YuJianIconActionTone
import com.yujian.ai.presentation.presentationSpeciesName

@Composable
fun FishRecordHeroCard(
    record: RemoteCatch,
    imageUrl: String?,
    bsideUrl: String?,
    showBside: Boolean,
    canFlip: Boolean,
    bsideReloadToken: Int,
    accessToken: String,
    onEdit: () -> Unit,
    onFlip: () -> Unit,
    onBsideLoadResult: (Boolean) -> Unit,
) {
    val metadata = FishRecordDetailPresentation.measurement(record)?.let { measurement ->
        listOfNotNull(measurement, FishRecordDetailPresentation.location(record))
    } ?: listOfNotNull(FishRecordDetailPresentation.location(record))
    val displayedUrl = if (showBside) bsideUrl else imageUrl
    val speciesName = presentationSpeciesName(record.speciesName)

    YuJianHeroCard(
        title = speciesName,
        metadata = metadata,
        variant = YuJianHeroVariant.DETAIL,
        editLabel = "编辑 >",
        onClick = onEdit,
        mediaAction = {
            if (canFlip) {
                YuJianIconAction(
                    icon = Icons.Rounded.Flip,
                    contentDescription = if (showBside) "切回鱼获照片" else "切换鱼获记忆",
                    onClick = onFlip,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    family = YuJianIconActionFamily.UTILITY,
                    tone = YuJianIconActionTone.ON_MEDIA,
                    supportDisc = true,
                )
            }
        },
        media = {
            RemoteImage(
                url = displayedUrl,
                authToken = accessToken,
                modifier = Modifier.fillMaxSize(),
                contentDescription = if (showBside) "$speciesName 鱼获记忆" else "$speciesName 鱼获照片",
                contentScale = ContentScale.Crop,
                reloadToken = if (showBside) bsideReloadToken else 0,
                onLoadResult = if (showBside) onBsideLoadResult else null,
                placeholder = {
                    Box(Modifier.fillMaxSize().background(Color(0xFF6D8491)))
                },
            )
        },
    )
}
