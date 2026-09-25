package com.yujian.ai.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.yujian.ai.ui.components.FishIllustration
import com.yujian.ai.ui.components.RemoteImage
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.glass.YuJianGlassLevel
import com.yujian.ai.ui.designsystem.radius.YuJianRadius
import com.yujian.ai.ui.designsystem.typography.YuJianTypography
import com.yujian.ai.ui.fishguide.FishGuidePresentationItem
import com.yujian.ai.ui.fishguide.formatRecordCount

enum class YuJianFishGuideCardVariant {
    LIT,
    UNLIT,
    ADJACENT_PREVIEW,
}

/** One natural field-guide card system with lit, unlit, and adjacent variants. */
@Composable
fun YuJianFishGuideCard(
    item: FishGuidePresentationItem,
    variant: YuJianFishGuideCardVariant,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val isUnlit = variant == YuJianFishGuideCardVariant.UNLIT
    val isAdjacent = variant == YuJianFishGuideCardVariant.ADJACENT_PREVIEW
    YuJianGlassCard(
        modifier = modifier,
        level = if (isUnlit) YuJianGlassLevel.Light else YuJianGlassLevel.Medium,
        shape = YuJianRadius.heroCard,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize().clip(YuJianRadius.heroCard)) {
            if (item.imageUrl != null) {
                RemoteImage(
                    url = item.imageUrl,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    YuJianColors.MistBlueGray.copy(alpha = 0.30f),
                                    YuJianColors.LakeBlue.copy(alpha = 0.16f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    FishIllustration(
                        size = 180.dp,
                        bodyColor = if (isUnlit) {
                            YuJianColors.MistBlueGray.copy(alpha = 0.36f)
                        } else {
                            YuJianColors.LakeBlue.copy(alpha = 0.72f)
                        },
                    )
                }
            }

            if (isUnlit) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(YuJianColors.MistBlueGray.copy(alpha = 0.28f)),
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.56f to Color.Transparent,
                            1f to YuJianColors.DeepOverlay.copy(alpha = if (isUnlit) 0.78f else 0.90f),
                        ),
                    ),
            )

            if (!isAdjacent) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = item.name,
                        style = YuJianTypography.heroTitle.copy(color = YuJianColors.OnDark),
                    )
                    item.category.takeIf { it.isNotBlank() }?.let { category ->
                        Text(
                            text = category,
                            style = YuJianTypography.body.copy(color = YuJianColors.OnDark.copy(alpha = 0.92f)),
                        )
                    }
                    if (isUnlit) {
                        Text(
                            text = "还没有我的记录",
                            style = YuJianTypography.caption.copy(color = YuJianColors.OnDark.copy(alpha = 0.84f)),
                        )
                    } else {
                        formatRecordCount(item.catches)?.let { recordText ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .background(YuJianColors.SoftGold, YuJianRadius.avatar),
                                )
                                Text(
                                    text = recordText,
                                    style = YuJianTypography.caption.copy(color = YuJianColors.OnDark.copy(alpha = 0.88f)),
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
