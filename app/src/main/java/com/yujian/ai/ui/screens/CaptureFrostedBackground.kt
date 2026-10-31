package com.yujian.ai.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.yujian.ai.R

/** Frozen mint wallpaper: camera initialization base, always behind PreviewView. */
@Composable
internal fun CaptureFrostedBackground(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.camera_mint_background_v2), null,
        modifier = modifier, contentScale = ContentScale.Crop)
}
