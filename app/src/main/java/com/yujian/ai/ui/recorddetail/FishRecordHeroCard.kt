package com.yujian.ai.ui.recorddetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.components.RemoteImage
import com.yujian.ai.ui.designsystem.components.YuJianIconAction
import com.yujian.ai.ui.designsystem.components.YuJianIconActionFamily
import com.yujian.ai.ui.designsystem.components.YuJianIconActionTone
import com.yujian.ai.ui.designsystem.typography.YuJianTypography
import com.yujian.ai.ui.designsystem.components.YuJianTextAction
import com.yujian.ai.ui.designsystem.components.YuJianTextActionRole
import com.yujian.ai.ui.designsystem.components.YuJianTextActionTone

@Composable
fun FishRecordHeroCard(
    record: RemoteCatch,
    imageUrl: String?,
    bsideUrl: String?,
    showBside: Boolean,
    canFlip: Boolean,
    bsideReloadToken: Int,
    accessToken: String,
    heroHeight: Dp,
    onEdit: () -> Unit,
    onFlip: () -> Unit,
    onBsideLoadResult: (Boolean) -> Unit,
) {
    val metadata = FishRecordDetailPresentation.heroMetadata(record)
    val displayedUrl = if (showBside) bsideUrl else imageUrl
    val mediaMode = FishRecordHeroMediaPolicy.mode(showBside)
    val heroShape = RoundedCornerShape(20.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .clip(heroShape)
            .clickable(onClick = onEdit),
    ) {
        RemoteImage(
            url = displayedUrl,
            authToken = accessToken,
            modifier = Modifier.fillMaxSize(),
            contentDescription = if (showBside) "${metadata.speciesName} 鱼获记忆" else "${metadata.speciesName} 鱼获照片",
            contentScale = if (mediaMode == FishRecordHeroImageMode.EVIDENCE_FIT) ContentScale.Fit else ContentScale.Crop,
            preserveEvidenceWithFitBackdrop = mediaMode == FishRecordHeroImageMode.EVIDENCE_FIT,
            trimVerifiedLetterbox = mediaMode == FishRecordHeroImageMode.EVIDENCE_FIT,
            reloadToken = if (showBside) bsideReloadToken else 0,
            onLoadResult = if (showBside) onBsideLoadResult else null,
            placeholder = {
                Box(Modifier.fillMaxSize().background(Color(0xFF6D8491)))
            },
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.04f),
                        Color(0x80081926),
                    ),
                ),
            ),
        )
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
        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = metadata.speciesName,
                    modifier = Modifier.weight(1f),
                    style = YuJianTypography.heroTitle.copy(fontSize = 28.sp, lineHeight = 34.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                YuJianTextAction(
                    text = "编辑",
                    onClick = onEdit,
                    modifier = Modifier.padding(start = 4.dp),
                    role = YuJianTextActionRole.NORMAL,
                    tone = YuJianTextActionTone.ON_MEDIA,
                    showChevron = true,
                )
            }
            metadata.measurement?.let { measurement ->
                Text(
                    text = measurement,
                    modifier = Modifier.padding(top = 2.dp),
                    style = YuJianTypography.caption.copy(fontSize = 14.sp, lineHeight = 18.sp, color = Color.White),
                    maxLines = 1,
                )
            }
            metadata.location?.let { location ->
                Text(
                    text = location,
                    modifier = Modifier.fillMaxWidth().padding(top = 1.dp),
                    style = YuJianTypography.caption.copy(fontSize = 14.sp, lineHeight = 18.sp, color = Color.White),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.88f), heroShape),
        )
    }
}
