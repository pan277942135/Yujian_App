package com.yujian.ai.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.glass.YuJianGlassLevel
import com.yujian.ai.ui.designsystem.radius.YuJianRadius
import com.yujian.ai.ui.designsystem.spacing.YuJianSpacing
import com.yujian.ai.ui.designsystem.typography.YuJianTypography

private val FishRowThumbnailSize = 72.dp

/** Shared chronological fish-record row for the My Catches timeline. */
@Composable
fun YuJianFishRow(
    title: String,
    measurements: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    annotation: String? = null,
    onClick: (() -> Unit)? = null,
    thumbnail: @Composable BoxScope.() -> Unit = { YuJianFishThumbnailPlaceholder() },
) {
    YuJianGlassCard(
        modifier = modifier,
        level = YuJianGlassLevel.Medium,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(YuJianSpacing.sm),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(FishRowThumbnailSize)
                    .clip(YuJianRadius.button),
                content = thumbnail,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = YuJianSpacing.sm),
            ) {
                Text(text = title, style = YuJianTypography.sectionTitle)
                Text(
                    text = measurements,
                    style = YuJianTypography.body,
                    modifier = Modifier.padding(top = YuJianSpacing.xs),
                )
                Text(
                    text = subtitle,
                    style = YuJianTypography.caption,
                    modifier = Modifier.padding(top = YuJianSpacing.xs),
                )
                if (annotation != null) {
                    Text(
                        text = annotation,
                        style = YuJianTypography.caption.copy(color = YuJianColors.DeepInk),
                        modifier = Modifier
                            .padding(top = YuJianSpacing.xs)
                            .clip(YuJianRadius.pill)
                            .background(YuJianColors.SoftGold.copy(alpha = 0.36f))
                            .padding(horizontal = YuJianSpacing.xs, vertical = YuJianSpacing.xs),
                    )
                }
            }
            Spacer(Modifier.width(YuJianSpacing.xs))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "查看鱼获",
                tint = YuJianColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun BoxScope.YuJianFishThumbnailPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(YuJianColors.LakeBlue, YuJianColors.DeepLakeBlue),
                ),
            ),
    )
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .width(42.dp)
            .fillMaxHeight()
            .padding(vertical = YuJianSpacing.sm)
            .clip(YuJianRadius.pill)
            .background(YuJianColors.MistWhite.copy(alpha = 0.24f)),
    )
}
