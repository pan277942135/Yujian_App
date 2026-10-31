package com.yujian.ai.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.exp
import kotlin.math.sin

internal fun captureZoomFraction(ratio: Float, minimum: Float, maximum: Float): Float =
    if (maximum <= minimum || minimum <= 0f) 0f
    else (ln((ratio.coerceIn(minimum, maximum) / minimum).toDouble()) /
        ln((maximum / minimum).toDouble())).toFloat().coerceIn(0f, 1f)

internal fun captureZoomRatio(fraction: Float, minimum: Float, maximum: Float): Float =
    if (maximum <= minimum || minimum <= 0f) minimum
    else (minimum * exp(fraction.coerceIn(0f, 1f) * ln((maximum / minimum).toDouble()))).toFloat().coerceIn(minimum, maximum)

/** The camera's observed ZoomState is the only displayed zoom source. */
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
    val pillWidth = (80f + (LocalDensity.current.fontScale - 1f).coerceIn(0f, .5f) * 40f).dp
    val ink = Color(0xFF19565B)
    val latestRatio = rememberUpdatedState(current)
    val latestZoom = rememberUpdatedState(onZoom)
    val latestExpand = rememberUpdatedState(onExpandedChange)
    fun Modifier.zoomDrag() = pointerInput(minimum, maximum) {
        var fraction = 0f
        detectDragGestures(
            onDragStart = {
                fraction = captureZoomFraction(latestRatio.value, minimum, maximum)
                latestExpand.value(true)
            },
        ) { change, drag ->
            change.consume()
            fraction = (fraction + drag.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
            latestZoom.value(captureZoomRatio(fraction, minimum, maximum))
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(expanded) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.background(Color(0xD9E2F2ED), RoundedCornerShape(20.dp)).padding(6.dp)) {
                Text(String.format(Locale.US, "%.1fx", current), color = ink, fontSize = 18.sp)
                Canvas(Modifier.width(260.dp).height(64.dp).testTag("capture-fine-zoom-dial")
                    .semantics { contentDescription = "左右滑动精细变焦" }.zoomDrag()) {
                    val cx = size.width / 2f
                    val cy = size.height * 1.6f
                    val radius = size.width * .375f
                    val fraction = captureZoomFraction(current, minimum, maximum)
                    fun point(f: Float, r: Float): Offset {
                        val angle = Math.PI * (.82 - .64 * f)
                        return Offset(cx + r * cos(angle).toFloat(), cy - r * sin(angle).toFloat())
                    }
                    for (index in 0..24) {
                        val f = index / 24f
                        drawLine(ink.copy(alpha = .45f), point(f, radius),
                            point(f, radius - if (index % 4 == 0) 12.dp.toPx() else 6.dp.toPx()), 1.dp.toPx())
                    }
                    drawLine(Color(0xFFB78735), point(fraction, radius + 2.dp.toPx()), point(fraction, radius - 14.dp.toPx()), 2.dp.toPx())
                    drawCircle(Color(0xFFD8B36A), 3.dp.toPx(), point(fraction, radius + 2.dp.toPx()))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    val presets = buildList {
                        if (minimum < .99f) add("W" to minimum)
                        listOf(1f, 2f, 4f, 10f).filter { it in minimum..maximum }.forEach { add("${it.toInt()}x" to it) }
                    }
                    presets.forEach { (label, ratio) ->
                        Box(Modifier.size(48.dp).background(if (abs(current - ratio) < .04f) Color.White.copy(alpha = .8f) else Color.Transparent, CircleShape)
                            .clickable { onZoom(ratio) }.semantics { contentDescription = "变焦 $label" }, contentAlignment = Alignment.Center) {
                            Text(label, color = ink, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        Box(Modifier.width(pillWidth).height(48.dp).testTag("capture-zoom-entry").zoomDrag()
            .clickable { onExpandedChange(!expanded) }, contentAlignment = Alignment.Center) {
            Row(Modifier.height(40.dp).background(Color.White.copy(alpha = .35f), RoundedCornerShape(20.dp))
                .border(1.dp, Color.White.copy(alpha = .65f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.ZoomIn, null, tint = ink, modifier = Modifier.size(18.dp))
                Text(String.format(Locale.US, "%.1fx", current), color = ink, fontSize = 16.sp, maxLines = 1)
            }
        }
    }
}
