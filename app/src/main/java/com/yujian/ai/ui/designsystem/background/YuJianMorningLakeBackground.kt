package com.yujian.ai.ui.designsystem.background

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.yujian.ai.R

enum class YuJianMorningLakeVariant {
    CONTENT,
    DATA,
}

/** One Android renderer for the frozen BG_CONTENT and BG_DATA treatments. */
@Composable
fun YuJianMorningLakeBackground(
    variant: YuJianMorningLakeVariant,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val treatment = when (variant) {
        YuJianMorningLakeVariant.CONTENT -> BackgroundTreatment(0.15f, BgContentColorMatrix)
        YuJianMorningLakeVariant.DATA -> BackgroundTreatment(0.30f, BgDataColorMatrix)
    }
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.account_privacy_morning_lake),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.colorMatrix(treatment.colorMatrix),
        )
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = treatment.veilAlpha)))
        content()
    }
}

private data class BackgroundTreatment(
    val veilAlpha: Float,
    val colorMatrix: ColorMatrix,
)

private val BgContentColorMatrix = ColorMatrix(
    floatArrayOf(
        0.828f, 0.057f, 0.006f, 0f, 22f,
        0.017f, 0.867f, 0.006f, 0f, 22f,
        0.017f, 0.057f, 0.816f, 0f, 22f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

private val BgDataColorMatrix = ColorMatrix(
    floatArrayOf(
        0.690f, 0.124f, 0.012f, 0f, 29.733f,
        0.037f, 0.777f, 0.012f, 0f, 29.733f,
        0.037f, 0.124f, 0.666f, 0f, 29.733f,
        0f, 0f, 0f, 1f, 0f,
    ),
)
