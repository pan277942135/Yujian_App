package com.yujian.ai.ui.screens

import androidx.compose.runtime.Composable
import com.yujian.ai.ui.designsystem.background.YuJianMorningLakeBackground
import com.yujian.ai.ui.designsystem.background.YuJianMorningLakeVariant

/** Shared content backdrop used by App and recognition/result screens. */
@Composable
fun BgContentSurface() {
    YuJianMorningLakeBackground(variant = YuJianMorningLakeVariant.CONTENT)
}
