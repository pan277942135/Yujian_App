package com.yujian.ai.ui.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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
    animateEncounterTransition: Boolean = false,
    animateRecordCountChange: Boolean = false,
    animateFirstRecordReveal: Boolean = false,
    reduceMotion: Boolean = false,
) {
    val isUnlit = variant == YuJianFishGuideCardVariant.UNLIT
    val isAdjacent = variant == YuJianFishGuideCardVariant.ADJACENT_PREVIEW
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.985f else 1f,
        animationSpec = tween(durationMillis = if (pressed) 70 else 100),
        label = "FishGuideCardPress",
    )
    val settledEncounter by animateFloatAsState(
        targetValue = if (isUnlit) 0f else 1f,
        animationSpec = tween(
            durationMillis = when {
                !animateEncounterTransition -> 0
                reduceMotion -> 100
                isUnlit -> 260
                else -> 420
            },
        ),
        label = "FishGuideEncounterState",
    )
    val stateCopyFadeDuration = when {
        !animateEncounterTransition -> 0
        reduceMotion -> 100
        else -> 160
    }
    val recordCountExitDuration = when {
        !animateEncounterTransition -> 0
        reduceMotion -> 100
        else -> 260
    }
    val countRevealDuration = when {
        !animateFirstRecordReveal -> 0
        reduceMotion -> 100
        else -> 180
    }
    val countChangeDuration = when {
        !animateRecordCountChange -> 0
        reduceMotion -> 100
        else -> 160
    }
    val interactionModifier = if (onClick != null) {
        Modifier
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
    } else Modifier
    val unlitImageFilter = remember(isUnlit) {
        if (isUnlit) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.84f) }) else null
    }
    YuJianGlassCard(
        modifier = modifier.then(interactionModifier),
        level = if (isUnlit) YuJianGlassLevel.Light else YuJianGlassLevel.Medium,
        shape = YuJianRadius.heroCard,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Box(Modifier.fillMaxSize().clip(YuJianRadius.heroCard)) {
            if (item.imageUrl != null) {
                RemoteImage(
                    url = item.imageUrl,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = null,
                    // LIT and UNLIT share one published COVER_HERO source and geometry.
                    // Fit preserves the entire authored image in both states.
                    contentScale = ContentScale.Fit,
                    colorFilter = unlitImageFilter,
                    placeholder = { MissingSpeciesArtwork(item, isUnlit) },
                )
            } else {
                MissingSpeciesArtwork(item, isUnlit)
            }

            if (isUnlit || animateEncounterTransition) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(YuJianColors.MistBlueGray.copy(alpha = 0.08f * (1f - settledEncounter))),
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.56f to Color.Transparent,
                            1f to YuJianColors.DeepOverlay.copy(
                                alpha = if (isUnlit) 0.20f + settledEncounter * 0.03f else 0.78f + settledEncounter * 0.12f,
                            ),
                        ),
                ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.name,
                    style = YuJianTypography.heroTitle.copy(color = YuJianColors.DeepInk),
                )
                Box(
                    Modifier
                        .width(44.dp)
                        .height(3.dp)
                        .background(YuJianColors.MorningGold, YuJianRadius.pill),
                )
                item.category.takeIf { it.isNotBlank() }?.let { category ->
                    Text(
                        text = category,
                        style = YuJianTypography.body.copy(color = YuJianColors.DeepInk.copy(alpha = 0.88f)),
                    )
                }
            }

            if (!isAdjacent) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val recordText = formatRecordCount(item.catches)
                    AnimatedVisibility(
                        visible = isUnlit,
                        enter = fadeIn(tween(durationMillis = stateCopyFadeDuration)),
                        exit = fadeOut(tween(durationMillis = stateCopyFadeDuration)),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "尚未点亮",
                                style = YuJianTypography.body.copy(color = YuJianColors.OnDark.copy(alpha = 0.92f)),
                            )
                            Text(
                                text = "还没有我的记录",
                                style = YuJianTypography.caption.copy(color = YuJianColors.OnDark.copy(alpha = 0.84f)),
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = !isUnlit && recordText != null,
                        enter = fadeIn(
                            tween(
                                durationMillis = countRevealDuration,
                                delayMillis = if (animateFirstRecordReveal && !reduceMotion) 180 else 0,
                            ),
                        ),
                        exit = fadeOut(
                            tween(
                                durationMillis = recordCountExitDuration,
                            ),
                        ),
                    ) {
                        AnimatedContent(
                            targetState = recordText.orEmpty(),
                            transitionSpec = {
                                fadeIn(tween(countChangeDuration)) togetherWith fadeOut(tween(countChangeDuration))
                            },
                            label = "FishGuideSavedCatchCount",
                        ) { visibleCount ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .background(YuJianColors.SoftGold, YuJianRadius.avatar),
                                )
                                Text(
                                    text = visibleCount,
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

@Composable
private fun MissingSpeciesArtwork(item: FishGuidePresentationItem, isUnlit: Boolean) {
    val baseColor = if (isUnlit) YuJianColors.MistBlueGray else YuJianColors.LakeBlue
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                        listOf(
                            baseColor.copy(alpha = 0.30f),
                            YuJianColors.LakeBlue.copy(alpha = 0.16f),
                        ),
                    ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "${item.name} · 鱼鉴主视觉暂不可用",
                style = YuJianTypography.caption.copy(color = YuJianColors.TextSecondary),
            )
        }
    }
}
