package com.yujian.ai.ui.designsystem.background

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yujian.ai.ui.designsystem.lakeglass.LakeGlassBackgroundHost

/** Legacy call-site name retained while all theme backgrounds route through the V2 Host. */
enum class YuJianMorningLakeVariant {
    CONTENT,
    DATA,
}

@Composable
fun YuJianMorningLakeBackground(
    @Suppress("UNUSED_PARAMETER") variant: YuJianMorningLakeVariant,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    LakeGlassBackgroundHost(
        modifier = modifier.fillMaxSize(),
        content = content,
    )
}
