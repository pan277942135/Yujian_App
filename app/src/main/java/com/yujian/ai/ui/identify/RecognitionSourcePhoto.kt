package com.yujian.ai.ui.identify

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity

/** Draw the original pixels with the exact scale and translation used by focus geometry. */
@Composable
fun RecognitionSourcePhoto(
    bitmap: ImageBitmap,
    transform: RecognitionImageTransform,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val offsetX = with(density) { transform.translationX.toDp() }
    val offsetY = with(density) { transform.translationY.toDp() }
    val width = with(density) { transform.drawnWidth.toDp() }
    val height = with(density) { transform.drawnHeight.toDp() }

    Box(modifier.fillMaxSize().clipToBounds()) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .align(Alignment.TopStart)
                .absoluteOffset(x = offsetX, y = offsetY)
                .requiredSize(width = width, height = height),
        )
    }
}
