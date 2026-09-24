package com.yujian.ai.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.components.YuJianCaptureButton
import com.yujian.ai.ui.designsystem.components.YuJianFishRow
import com.yujian.ai.ui.designsystem.components.YuJianGlassCard
import com.yujian.ai.ui.designsystem.components.YuJianHeroCard
import com.yujian.ai.ui.designsystem.components.YuJianHeroVariant
import com.yujian.ai.ui.designsystem.components.YuJianPrimaryButton
import com.yujian.ai.ui.designsystem.components.YuJianPrimaryButtonTone
import com.yujian.ai.ui.designsystem.components.YuJianTopBar
import com.yujian.ai.ui.designsystem.glass.YuJianGlassLevel
import com.yujian.ai.ui.designsystem.radius.YuJianRadius
import com.yujian.ai.ui.designsystem.spacing.YuJianSpacing
import com.yujian.ai.ui.designsystem.typography.YuJianTypography
import com.yujian.ai.ui.theme.YujianTheme

/** Sections can be rendered independently by the debug runtime evidence activity. */
enum class DesignSystemPreviewSection(val argument: String) {
    All("all"),
    Colors("colors"),
    Typography("typography"),
    Glass("glass"),
    Components("components"),
    ;

    companion object {
        fun fromArgument(value: String?): DesignSystemPreviewSection =
            entries.firstOrNull { it.argument == value } ?: All
    }
}

/** Standalone development gallery; it is intentionally not a product route. */
@Composable
fun DesignSystemPreview(
    section: DesignSystemPreviewSection = DesignSystemPreviewSection.All,
    modifier: Modifier = Modifier,
) {
    val showAll = section == DesignSystemPreviewSection.All
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(YuJianColors.LakeWhite),
        verticalArrangement = Arrangement.spacedBy(YuJianSpacing.lg),
    ) {
        item {
            YuJianTopBar(
                title = "Core UI V1",
                modifier = Modifier.padding(top = YuJianSpacing.sm),
            )
        }
        if (showAll || section == DesignSystemPreviewSection.Colors) {
            item { PreviewSection(title = "Colors") { ColorGallery() } }
        }
        if (showAll || section == DesignSystemPreviewSection.Typography) {
            item { PreviewSection(title = "Typography") { TypographyGallery() } }
        }
        if (showAll || section == DesignSystemPreviewSection.Glass) {
            item { PreviewSection(title = "Glass") { GlassGallery() } }
        }
        if (showAll || section == DesignSystemPreviewSection.Components) {
            item { PreviewSection(title = "Components") { ComponentGallery() } }
        }
    }
}

@Composable
private fun PreviewSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = YuJianSpacing.md),
        verticalArrangement = Arrangement.spacedBy(YuJianSpacing.sm),
    ) {
        Text(text = title, style = YuJianTypography.sectionTitle)
        content()
    }
}

@Composable
private fun ColorGallery() {
    val tokens = listOf(
        "Lake Blue" to YuJianColors.LakeBlue,
        "Morning Gold" to YuJianColors.MorningGold,
        "Deep Ink" to YuJianColors.DeepInk,
        "Mist White" to YuJianColors.MistWhite,
        "Glass White" to YuJianColors.GlassWhite,
        "Glass Border" to YuJianColors.GlassBorder,
        "Text Primary" to YuJianColors.TextPrimary,
        "Text Secondary" to YuJianColors.TextSecondary,
    )
    Column(verticalArrangement = Arrangement.spacedBy(YuJianSpacing.xs)) {
        tokens.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(YuJianSpacing.sm)) {
                row.forEach { (label, color) ->
                    ColorSwatch(label = label, color = color, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) SpacerSwatch()
            }
        }
    }
}

@Composable
private fun ColorSwatch(label: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YuJianSpacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(YuJianSpacing.xxl)
                .clip(YuJianRadius.button)
                .background(color),
        )
        Text(text = label, style = YuJianTypography.caption)
    }
}

@Composable
private fun SpacerSwatch() {
    Box(Modifier.width(YuJianSpacing.xxl))
}

@Composable
private fun TypographyGallery() {
    Column(verticalArrangement = Arrangement.spacedBy(YuJianSpacing.xs)) {
        Text(text = "Page Title", style = YuJianTypography.pageTitle)
        Text(text = "Section Title", style = YuJianTypography.sectionTitle)
        Text(text = "Hero Title", style = YuJianTypography.heroTitle.copy(color = YuJianColors.LakeBlue))
        Text(text = "Body — 每一次渔获都值得留下记忆", style = YuJianTypography.body)
        Text(text = "Caption · 千岛湖 · 2026-09-24", style = YuJianTypography.caption)
        Text(text = "42.8 cm", style = YuJianTypography.dataNumber)
        Text(text = "Button Text", style = YuJianTypography.buttonText.copy(color = YuJianColors.LakeBlue))
    }
}

@Composable
private fun GlassGallery() {
    Column(verticalArrangement = Arrangement.spacedBy(YuJianSpacing.sm)) {
        YuJianGlassCard(level = YuJianGlassLevel.Light) {
            Text(text = "Light Glass · overlay and pill", style = YuJianTypography.body)
        }
        YuJianGlassCard(level = YuJianGlassLevel.Medium) {
            Text(text = "Medium Glass · story and statistics", style = YuJianTypography.body)
        }
        YuJianGlassCard(level = YuJianGlassLevel.Strong) {
            Text(text = "Strong Glass · primary content", style = YuJianTypography.body)
        }
    }
}

@Composable
private fun ComponentGallery() {
    Column(verticalArrangement = Arrangement.spacedBy(YuJianSpacing.md)) {
        YuJianPrimaryButton(text = "保存鱼获", onClick = {})
        YuJianPrimaryButton(
            text = "记录重要时刻",
            tone = YuJianPrimaryButtonTone.Gold,
            onClick = {},
        )
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            YuJianCaptureButton(onClick = {})
        }
        YuJianHeroCard(
            title = "草鱼",
            metadata = listOf("42.8 cm", "千岛湖", "刚刚"),
            variant = YuJianHeroVariant.HOME,
        )
        YuJianFishRow(
            title = "草鱼",
            measurements = "42.8 cm · 1.8 kg",
            subtitle = "千岛湖 · 今天",
            annotation = "最长记录",
            onClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DesignSystemPreviewAll() {
    YujianTheme { DesignSystemPreview() }
}
