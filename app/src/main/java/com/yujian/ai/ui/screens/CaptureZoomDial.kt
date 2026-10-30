package com.yujian.ai.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** All three inputs (presets, dial and PreviewView pinch) observe CameraX zoomState. */
@Composable
internal fun CaptureZoomDial(
    current: Float,
    minimum: Float,
    maximum: Float,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onZoom: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val range = (maximum - minimum).coerceAtLeast(0f)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(expanded) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(String.format(Locale.US, "%.1fx", current), color = Color.White, fontSize = 20.sp)
                Canvas(Modifier.width(260.dp).height(75.dp).pointerInput(minimum, maximum, current) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        if (range > 0f) onZoom((current + drag.x / size.width * range).coerceIn(minimum, maximum))
                    }
                }) {
                    val cx = size.width / 2f
                    val cy = size.height * 1.65f
                    val radius = size.width * .47f
                    for (index in 0..24) {
                        val fraction = index / 24f
                        val angle = Math.PI * (1.18 - .36 * fraction)
                        val x = cx + radius * cos(angle).toFloat()
                        val y = cy - radius * sin(angle).toFloat()
                        val selected = abs(fraction - (current - minimum) / range.coerceAtLeast(.001f)) < .025f
                        drawLine(if (selected) Color(0xFFD8B36A) else Color.White.copy(alpha = .45f),
                            Offset(x, y), Offset(x, y + if (index % 4 == 0) 13.dp.toPx() else 7.dp.toPx()),
                            if (selected) 2.dp.toPx() else 1.dp.toPx())
                    }
                }
                Text("向下滑动收起", color = Color.White.copy(alpha = .65f), fontSize = 11.sp,
                    modifier = Modifier.pointerInput(Unit) { detectVerticalDragGestures { change, delta ->
                        change.consume(); if (delta > 8f) onExpandedChange(false)
                    } })
                Spacer(Modifier.height(6.dp))
            }
        }
        Row(
            Modifier.background(Color(0xB3111A1D), RoundedCornerShape(28.dp))
                .border(1.dp, Color.White.copy(alpha = .24f), RoundedCornerShape(28.dp))
                .pointerInput(expanded) { detectVerticalDragGestures { change, delta ->
                    change.consume(); if (delta < -8f) onExpandedChange(true)
                    if (delta > 8f) onExpandedChange(false)
                } }.padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("W" to .5f, "1x" to 1f, "2x" to 2f, "4x" to 4f, "10x" to 10f).forEach { (label, ratio) ->
                val available = ratio in minimum..maximum
                val selected = available && abs(current - ratio) < .08f
                Text(label, color = if (selected) Color(0xFF111A1D) else Color.White.copy(alpha = if (available) 1f else .3f),
                    fontSize = 13.sp,
                    modifier = Modifier.size(39.dp).background(if (selected) Color(0xFFE9ECE9) else Color.Transparent, CircleShape)
                        .clickable(enabled = available) { onZoom(ratio) }.padding(top = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}
