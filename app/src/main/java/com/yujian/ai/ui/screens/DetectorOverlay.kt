package com.yujian.ai.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.SoftWater
import com.yujian.ai.ui.theme.WaterTeal
import kotlin.math.min

private val CropOverlayOrange = Color(0xFFF29C38)

/**
 * Draws detector coordinates in the same FIT_CENTER rect used by the source image.
 * The detector and crop boxes are normalized to the EXIF-corrected original bitmap.
 */
@Composable
fun DetectorOverlayImage(
    bitmap: Bitmap,
    detectorBox: NormalizedFishBox?,
    cropBox: NormalizedFishBox?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(SoftWater), contentAlignment = Alignment.Center) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "原图与鱼体检测框",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        Canvas(Modifier.fillMaxSize()) {
            val scale = min(size.width / bitmap.width.toFloat(), size.height / bitmap.height.toFloat())
            val drawnWidth = bitmap.width * scale
            val drawnHeight = bitmap.height * scale
            val left = (size.width - drawnWidth) / 2f
            val top = (size.height - drawnHeight) / 2f

            fun NormalizedFishBox.toRect(): androidx.compose.ui.geometry.Rect {
                val normalized = normalized()
                return androidx.compose.ui.geometry.Rect(
                    left = left + normalized.x1 * drawnWidth,
                    top = top + normalized.y1 * drawnHeight,
                    right = left + normalized.x2 * drawnWidth,
                    bottom = top + normalized.y2 * drawnHeight,
                )
            }

            cropBox?.let { crop ->
                drawRect(
                    color = CropOverlayOrange,
                    topLeft = crop.toRect().topLeft,
                    size = crop.toRect().size,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            detectorBox?.let { detector ->
                drawRect(
                    color = WaterTeal,
                    topLeft = detector.toRect().topLeft,
                    size = detector.toRect().size,
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }
    }
}

@Composable
fun DetectorOverlayLegend(
    showCrop: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(WaterTeal, "原图 bbox")
        if (showCrop) LegendItem(CropOverlayOrange, "expand 后 crop")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(width = 16.dp, height = 3.dp).background(color))
        Text(label, color = MutedInk, fontSize = 10.sp)
    }
}
