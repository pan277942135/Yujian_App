package com.yujian.ai.ui.recognition

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.ai.RecognitionPhase

/**
 * Three-state Recognition status overlay.
 *
 * CAPTURED and DETECTING deliberately share one visible product state and one
 * stable copy. RESULT is routing-only and never shows a fourth status card.
 */
@Composable
fun RecognitionStatusOverlay(
    phase: RecognitionPhase,
    modifier: Modifier = Modifier,
    resolveProgress: Float = 0f,
    reduceMotion: Boolean = false,
) {
    if (phase == RecognitionPhase.RESULT) return

    val copy = recognitionStatusCopy(phase)
    // DETECTING remains supported for direct callers and legacy previews, but
    // the V1.2 presentation controller normally renders it as CAPTURED.
    val lightweight = phase == RecognitionPhase.DETECTING
    val shape = RecognitionProcessingVisualTokens.statusShape
    val energyBorder = Brush.horizontalGradient(
        colors = listOf(
            RecognitionProcessingVisualTokens.statusGold.copy(alpha = .44f),
            RecognitionProcessingVisualTokens.statusGoldCore.copy(alpha = .28f),
            RecognitionProcessingVisualTokens.statusBlueCore.copy(alpha = .28f),
            RecognitionProcessingVisualTokens.statusBlueHot.copy(alpha = .44f),
        ),
    )

    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = 1f - resolveProgress.coerceIn(0f, 1f)
            }
            .then(
                if (lightweight) {
                    Modifier
                        .clip(shape)
                        .background(Color(0x3D10262D))
                } else {
                    Modifier
                        .height(RecognitionProcessingVisualTokens.statusHeight)
                        .clip(shape)
                        .background(RecognitionProcessingVisualTokens.statusSurface)
                        .border(width = .75.dp, brush = energyBorder, shape = shape)
                },
            )
            .padding(
                horizontal = if (lightweight) 22.dp else RecognitionProcessingVisualTokens.statusHorizontalPadding,
                vertical = if (lightweight) 14.dp else 0.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (lightweight) 18.dp else 16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(
                if (lightweight) 40.dp else RecognitionProcessingVisualTokens.statusIndicatorSize,
            ),
        ) {
            if (reduceMotion) {
                Box(
                    Modifier
                        .matchParentSize()
                        .border(
                            width = if (lightweight) 3.dp else RecognitionProcessingVisualTokens.statusIndicatorStroke,
                            color = RecognitionProcessingVisualTokens.statusGold,
                            shape = CircleShape,
                        ),
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.matchParentSize(),
                    color = RecognitionProcessingVisualTokens.statusGold,
                    trackColor = Color(0x668A979B),
                    strokeWidth = if (lightweight) 3.dp else RecognitionProcessingVisualTokens.statusIndicatorStroke,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                copy.title,
                color = Color.White,
                fontSize = if (lightweight) 18.sp else 20.sp,
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

