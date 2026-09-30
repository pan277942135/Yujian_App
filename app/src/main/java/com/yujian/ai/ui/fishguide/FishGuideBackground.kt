package com.yujian.ai.ui.fishguide

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

/** Frozen Morning Lake master with the shared BG_DATA treatment used by My Catches. */
@Composable
fun FishGuideBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.account_privacy_morning_lake),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.colorMatrix(FishGuideBgDataColorMatrix),
        )
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.30f)))
        content()
    }
}

private val FishGuideBgDataColorMatrix = ColorMatrix(
    floatArrayOf(
        0.690f, 0.124f, 0.012f, 0f, 29.733f,
        0.037f, 0.777f, 0.012f, 0f, 29.733f,
        0.037f, 0.124f, 0.666f, 0f, 29.733f,
        0f, 0f, 0f, 1f, 0f,
    ),
)
