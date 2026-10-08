package com.yujian.ai.ui.recorddetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.presentation.presentationSpeciesName
import com.yujian.ai.ui.components.CatchHeroVariant
import com.yujian.ai.ui.components.RemoteImage
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.components.YuJianIconAction
import com.yujian.ai.ui.designsystem.components.YuJianIconActionFamily
import com.yujian.ai.ui.designsystem.components.YuJianIconActionTone
import com.yujian.ai.ui.designsystem.typography.YuJianTypography

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
    val metadata = FishRecordDetailPresentation.measurement(record)?.let { measurement ->
        listOfNotNull(measurement, FishRecordDetailPresentation.location(record))
    } ?: listOfNotNull(FishRecordDetailPresentation.location(record))
    val displayedUrl = if (showBside) bsideUrl else imageUrl
    val speciesName = presentationSpeciesName(record.speciesName)
    val heroShape = RoundedCornerShape(20.dp)
    val editShape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .clip(heroShape)
            .testTag("fish-record-hero-card")
            .clickable(onClick = onEdit),
    ) {
        RemoteImage(
            url = displayedUrl,
            authToken = accessToken,
            modifier = Modifier.fillMaxSize().testTag("fish-record-hero-media"),
            contentDescription = if (showBside) "$speciesName 鱼获记忆" else "$speciesName 鱼获照片",
            contentScale = ContentScale.Crop,
            preservePortraitWithFitBackdrop = false,
            trimVerifiedLetterbox = !showBside,
            adaptiveHeroVariant = if (showBside) null else CatchHeroVariant.DETAIL_A,
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
                        Color.Black.copy(alpha = 0.08f),
                        Color(0xB3081926),
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
            Text(
                text = speciesName,
                style = YuJianTypography.heroTitle.copy(fontSize = 28.sp, lineHeight = 34.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = metadata.joinToString(" · "),
                    modifier = Modifier.weight(1f),
                    style = YuJianTypography.caption.copy(fontSize = 14.sp, lineHeight = 18.sp, color = Color.White),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .clip(editShape)
                        .background(Color(0x33202E38))
                        .border(1.dp, YuJianColors.MorningGold.copy(alpha = 0.96f), editShape)
                        .clickable(onClick = onEdit)
                        .testTag("fish-record-hero-edit-entry")
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "编辑",
                        style = YuJianTypography.caption.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                        ),
                    )
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = YuJianColors.MorningGold,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Box(
            Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.88f), heroShape),
        )
    }
}
