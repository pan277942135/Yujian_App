package com.yujian.ai.ui.designsystem.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.radius.YuJianRadius

/** The three sanctioned Core UI V1 glass strengths. */
enum class YuJianGlassLevel {
    Light,
    Medium,
    Strong,
}

@Immutable
data class YuJianGlassStyle(
    val fill: Color,
    val border: Color,
    val blurRadius: Dp,
    val elevation: Dp,
)

/** Runtime mapping of GLASS_A, GLASS_B, and GLASS_C. */
object YuJianGlassStyles {
    fun forLevel(level: YuJianGlassLevel): YuJianGlassStyle = when (level) {
        YuJianGlassLevel.Light -> YuJianGlassStyle(
            fill = YuJianColors.GlassWhite.copy(alpha = 0.58f),
            border = YuJianColors.GlassBorder.copy(alpha = 0.52f),
            blurRadius = 8.dp,
            elevation = 2.dp,
        )
        YuJianGlassLevel.Medium -> YuJianGlassStyle(
            fill = YuJianColors.GlassWhite.copy(alpha = 0.68f),
            border = YuJianColors.GlassBorder.copy(alpha = 0.64f),
            blurRadius = 12.dp,
            elevation = 4.dp,
        )
        YuJianGlassLevel.Strong -> YuJianGlassStyle(
            fill = YuJianColors.GlassWhite.copy(alpha = 0.80f),
            border = YuJianColors.GlassBorder.copy(alpha = 0.76f),
            blurRadius = 16.dp,
            elevation = 6.dp,
        )
    }
}

/**
 * Shared glass substrate. It owns surface alpha, soft blur, border, radius,
 * and shadow so pages never recreate a white-alpha card ad hoc.
 */
@Composable
fun MistGlass(
    level: YuJianGlassLevel,
    modifier: Modifier = Modifier,
    shape: Shape = YuJianRadius.glassCard,
    content: @Composable BoxScope.() -> Unit,
) {
    val style = remember(level) { YuJianGlassStyles.forLevel(level) }
    Box(
        modifier = modifier
            // No shadow: on some render paths the elevation shadow bleeds a
            // gradient inside/around the card edge, so padding and content
            // regions read at different brightness. Flat fill + border only.
            .clip(shape)
            .background(style.fill)
            .border(1.dp, style.border, shape),
    ) {
        content()
    }
}
