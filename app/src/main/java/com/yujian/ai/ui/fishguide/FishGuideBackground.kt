package com.yujian.ai.ui.fishguide

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yujian.ai.ui.designsystem.background.YuJianMorningLakeBackground
import com.yujian.ai.ui.designsystem.background.YuJianMorningLakeVariant

/** Frozen BG_DATA wrapper retained for Fish Guide call sites. */
@Composable
fun FishGuideBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    YuJianMorningLakeBackground(
        variant = YuJianMorningLakeVariant.DATA,
        modifier = modifier,
        content = content,
    )
}
