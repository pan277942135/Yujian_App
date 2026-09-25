package com.yujian.ai.ui.recognition

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.yujian.ai.ui.theme.WarmBackground

@Composable
fun RecognitionPhotoBackdrop(bitmap: Bitmap, modifier: Modifier = Modifier) {
    Box(modifier.background(WarmBackground)) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(24.dp).graphicsLayer { alpha = 0.30f },
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Color(0xBFEAF4F8)))
    }
}
