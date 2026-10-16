package com.yujian.ai.ui.recognition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.ai.RecognitionPhase

/** Frozen V3.1 status card; each real pipeline beat has its own copy. */
@Composable
fun RecognitionStatusOverlay(
    phase: RecognitionPhase,
    modifier: Modifier = Modifier,
    resolveProgress: Float = 0f,
    reduceMotion: Boolean = false,
    visualClockMs: Long = 0L,
) {
    if (phase == RecognitionPhase.RESULT) return

    val copy = recognitionStatusCopy(phase)
    val shape = RecognitionProcessingVisualTokens.statusShape
    val energyBorder = remember {
        Brush.horizontalGradient(
            colors = listOf(
                RecognitionProcessingVisualTokens.statusGold.copy(alpha = .44f),
                RecognitionProcessingVisualTokens.statusGoldCore.copy(alpha = .28f),
                RecognitionProcessingVisualTokens.statusBlueCore.copy(alpha = .28f),
                RecognitionProcessingVisualTokens.statusBlueHot.copy(alpha = .44f),
            ),
        )
    }
    val energyRingBrush = remember {
        Brush.sweepGradient(
            colors = listOf(
                RecognitionProcessingVisualTokens.statusGold.copy(alpha = .22f),
                RecognitionProcessingVisualTokens.statusGoldCore,
                RecognitionProcessingVisualTokens.statusGold,
                RecognitionProcessingVisualTokens.statusBlueCore.copy(alpha = .85f),
                RecognitionProcessingVisualTokens.statusGold.copy(alpha = .22f),
            ),
            center = Offset.Unspecified,
        )
    }

    Row(
        modifier
            .fillMaxWidth()
            .height(RecognitionProcessingVisualTokens.statusHeight)
            .graphicsLayer { alpha = 1f - resolveProgress.coerceIn(0f, 1f) }
            .clip(shape)
            .background(RecognitionProcessingVisualTokens.statusSurface)
            .border(width = .75.dp, brush = energyBorder, shape = shape)
            .padding(horizontal = RecognitionProcessingVisualTokens.statusHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(RecognitionProcessingVisualTokens.statusIndicatorSize),
        ) {
            Canvas(Modifier.size(RecognitionProcessingVisualTokens.statusIndicatorSize)) {
                val stroke = RecognitionProcessingVisualTokens.statusIndicatorStroke.toPx()
                drawArc(
                    color = Color(0x668A979B),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = stroke),
                )
                drawArc(
                    brush = energyRingBrush,
                    startAngle = recognitionEnergyRingStartAngle(visualClockMs, reduceMotion),
                    sweepAngle = RecognitionProcessingVisualTokens.energyRingSweepDegrees,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                copy.title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                copy.subtitle,
                color = RecognitionProcessingVisualTokens.statusSecondary,
                fontSize = 14.sp,
            )
        }
    }
}
