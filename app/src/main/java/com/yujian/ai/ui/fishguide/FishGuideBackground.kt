package com.yujian.ai.ui.fishguide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.yujian.ai.ui.designsystem.color.YuJianColors

/** Low-salience BG_DATA treatment: morning lake atmosphere without a bitmap. */
@Composable
fun FishGuideBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        YuJianColors.LakeWhite,
                        YuJianColors.MistWhite.copy(alpha = 0.72f),
                        YuJianColors.LakeBlue.copy(alpha = 0.28f),
                    ),
                ),
            ),
        content = content,
    )
}
