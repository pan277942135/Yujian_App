package com.yujian.ai.ui.recognition

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.yujian.ai.ui.designsystem.components.aiorb.AIOrbCore
import com.yujian.ai.ui.designsystem.components.aiorb.aiOrbProcessingState

/** Compatibility adapter; the only Orb renderer now lives in the shared AI Orb Core. */
@Composable
fun ProcessingWaterOrb(
    coreSize: Dp,
    entranceElapsedMs: Long,
    elapsedMs: Long,
    completion: Float,
    reduceMotion: Boolean,
    lowPerformance: Boolean,
    fromCamera: Boolean,
    modifier: Modifier = Modifier,
) {
    AIOrbCore(
        state = aiOrbProcessingState(entranceElapsedMs, fromCamera, reduceMotion, completion),
        coreSize = coreSize,
        entranceElapsedMs = entranceElapsedMs,
        elapsedMs = elapsedMs,
        completion = completion,
        reduceMotion = reduceMotion,
        lowPerformance = lowPerformance,
        fromCamera = fromCamera,
        modifier = modifier,
    )
}
