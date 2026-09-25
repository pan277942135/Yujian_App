package com.yujian.ai.ui.designsystem.glass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
            .shadow(style.elevation, shape, clip = false)
            .clip(shape)
            .background(style.fill)
            .border(1.dp, style.border, shape),
    ) {
        // Compose has no cross-version backdrop-blur primitive. On API 31+
        // this softly blurs the mist layer; older API levels retain the same
        // alpha, border and elevation treatment without a fake bitmap effect.
        val mistModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.blur(style.blurRadius)
        } else {
            Modifier
        }
        Box(
            modifier = mistModifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            YuJianColors.MistWhite.copy(alpha = 0.22f),
                            YuJianColors.MistWhite.copy(alpha = 0.04f),
                        ),
                    ),
                ),
        )
        content()
    }
}
