package com.yujian.ai.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.haptic.rememberYuJianHaptic
import com.yujian.ai.ui.designsystem.radius.YuJianRadius
import com.yujian.ai.ui.designsystem.spacing.YuJianSpacing
import com.yujian.ai.ui.designsystem.typography.YuJianTypography

enum class YuJianHeroVariant {
    HOME,
    DETAIL,
}

private val HomeHeroHeight = 220.dp
private val DetailHeroHeight = 320.dp

/**
 * One dynamic catch Hero family for Home and FishRecordDetail.
 *
 * Consumers supply real or approved page-specific media through [media]; this
 * component never consumes a frozen full-page reference image as a card asset.
 */
@Composable
fun YuJianHeroCard(
    title: String,
    metadata: List<String>,
    variant: YuJianHeroVariant,
    modifier: Modifier = Modifier,
    editLabel: String? = if (variant == YuJianHeroVariant.DETAIL) "编辑 >" else null,
    onClick: (() -> Unit)? = null,
    media: @Composable BoxScope.() -> Unit = { YuJianHeroPlaceholder() },
) {
    val haptic = rememberYuJianHaptic()
    val interactionModifier = if (onClick != null) {
        Modifier.clickable {
            haptic.performCardClick()
            onClick()
        }
    } else {
        Modifier
    }
    val height = if (variant == YuJianHeroVariant.HOME) HomeHeroHeight else DetailHeroHeight
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(YuJianRadius.heroCard)
            .then(interactionModifier),
    ) {
        media()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            YuJianColors.DeepOverlay.copy(alpha = 0f),
                            YuJianColors.DeepOverlay.copy(alpha = 0.10f),
                            YuJianColors.DeepOverlay,
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(YuJianSpacing.md),
        ) {
            Text(text = title, style = YuJianTypography.heroTitle)
            if (metadata.isNotEmpty()) {
                Text(
                    text = metadata.joinToString(separator = " · "),
                    style = YuJianTypography.caption.copy(color = YuJianColors.OnDark),
                    modifier = Modifier.padding(top = YuJianSpacing.xs),
                )
            }
        }
        if (editLabel != null) {
            Text(
                text = editLabel,
                style = YuJianTypography.caption.copy(color = YuJianColors.OnDark),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(YuJianSpacing.md),
            )
        }
    }
}

@Composable
private fun BoxScope.YuJianHeroPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        YuJianColors.LakeBlue,
                        YuJianColors.DeepLakeBlue,
                        YuJianColors.LakeBlue.copy(alpha = 0.82f),
                    ),
                ),
            ),
    )
    Row(
        modifier = Modifier
            .align(Alignment.Center)
            .width(136.dp)
            .height(48.dp)
            .clip(YuJianRadius.pill)
            .background(YuJianColors.MistWhite.copy(alpha = 0.18f)),
    ) {}
}
