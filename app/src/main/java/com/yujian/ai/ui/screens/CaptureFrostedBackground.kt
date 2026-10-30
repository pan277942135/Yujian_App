package com.yujian.ai.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Size

/** Camera loading/empty-state base. It is always behind the real PreviewView. */
@Composable
internal fun CaptureFrostedBackground(modifier: Modifier = Modifier) {
    val grain = remember {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        var seed = 0x5EED1234
        for (y in 0 until 96) for (x in 0 until 96) {
            seed = seed * 1664525 + 1013904223
            val shade = 80 + ((seed ushr 24) and 0x7f)
            bitmap.setPixel(x, y, android.graphics.Color.argb(5, shade, shade, shade))
        }
        bitmap.asImageBitmap()
    }
    val grainBrush = remember(grain) {
        object : ShaderBrush() {
            override fun createShader(size: Size): Shader =
                ImageShader(grain, TileMode.Repeated, TileMode.Repeated)
        }
    }
    Canvas(modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color(0xFF0B1618),
                .52f to Color(0xFF0E1B1E),
                1f to Color(0xFF111A1D),
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF779297).copy(alpha = .018f), Color.Transparent),
                center = Offset(size.width * .5f, size.height * .1f),
                radius = size.width * 1.3f,
            ),
        )
        drawRect(brush = grainBrush)
    }
}
