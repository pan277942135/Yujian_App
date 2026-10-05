package com.yujian.ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.FishGreen

@Composable
fun FishIllustration(
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
    bodyColor: Color = FishGreen,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = this.size.width
            val h = this.size.height
            val bodyRect = Rect(w * .23f, h * .34f, w * .84f, h * .68f)
            rotate(-2f, pivot = bodyRect.center) {
                drawOval(color = bodyColor, topLeft = bodyRect.topLeft, size = bodyRect.size)
            }

            val tail = Path().apply {
                moveTo(w * .25f, h * .51f)
                lineTo(w * .06f, h * .34f)
                lineTo(w * .06f, h * .69f)
                close()
            }
            drawPath(tail, bodyColor.copy(alpha = .95f))

            val fin = Path().apply {
                moveTo(w * .50f, h * .35f)
                lineTo(w * .59f, h * .21f)
                lineTo(w * .67f, h * .35f)
                close()
            }
            drawPath(fin, bodyColor.copy(alpha = .78f))

            drawOval(
                color = DeepInk,
                topLeft = Offset(w * .73f, h * .43f),
                size = Size(w * .038f, w * .038f),
            )
        }
    }
}
