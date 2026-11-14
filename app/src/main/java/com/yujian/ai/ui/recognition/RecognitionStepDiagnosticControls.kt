package com.yujian.ai.ui.recognition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.ui.waterlight.core.DIAGNOSTIC_STEP_DURATION_MS
import com.yujian.ai.ui.waterlight.core.RecognitionProgressDisplayMode
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.ScenePlaybackMode
import com.yujian.ai.ui.designsystem.components.aiorb.OrbRenderIsolationMode

/** Shared Debug-only overlay used by Processing and Result so both pages operate one controller. */
@Composable
fun RecognitionStepDiagnosticControls(
    displayMode: RecognitionProgressDisplayMode,
    stage: RecognitionSceneStage,
    playbackMode: ScenePlaybackMode,
    previewElapsedMs: Long,
    previewCompleted: Boolean,
    playbackPaused: Boolean,
    mappedReferenceMs: Long,
    modelStatus: String,
    detectorStatus: String = "待机",
    classifierStatus: String = "待机",
    slimSamStatus: String = "PENDING",
    maskStatus: String,
    geometryStatus: String = "PENDING",
    sceneStatus: String = "RUNNING",
    stepStatus: String,
    normalProgressText: String,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    canControl: Boolean,
    onDisplayModeChange: (RecognitionProgressDisplayMode) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPauseResume: () -> Unit,
    onReplay: () -> Unit,
    onAutoPlay: () -> Unit,
    orbIsolationMode: OrbRenderIsolationMode? = null,
    onOrbIsolationModeChange: ((OrbRenderIsolationMode) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    var detailsExpanded by remember { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.TopEnd)
                .padding(top = safeInsets.calculateTopPadding() + 10.dp, end = 12.dp)
                .width(156.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Row(
                modifier = Modifier.width(142.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color(0xDDFFFFFF)).padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                listOf(
                    RecognitionProgressDisplayMode.STEP_DIAGNOSTIC to "分步诊断",
                    RecognitionProgressDisplayMode.NORMAL_PROGRESS to "正常进度",
                ).forEach { (mode, label) ->
                    val selected = displayMode == mode
                    Text(
                        label,
                        color = if (selected) Color.White else Color(0xFF37666C),
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .background(if (selected) Color(0xFF287E79) else Color.Transparent)
                            .clickable { onDisplayModeChange(mode) }
                            .padding(horizontal = 3.dp, vertical = 5.dp),
                    )
                }
            }
            if (displayMode == RecognitionProgressDisplayMode.STEP_DIAGNOSTIC) {
                if (orbIsolationMode != null && onOrbIsolationModeChange != null) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp).width(156.dp)
                            .clip(RoundedCornerShape(10.dp)).background(Color(0xDDF8FFFC)).padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        listOf(
                            OrbRenderIsolationMode.RAW_CORE to "水核",
                            OrbRenderIsolationMode.RAW_CORE_WITH_R06 to "水核+光丝",
                            OrbRenderIsolationMode.NORMAL to "完整效果",
                        ).forEach { (mode, label) ->
                            val selected = orbIsolationMode == mode
                            Text(
                                label,
                                color = if (selected) Color.White else Color(0xFF37666C),
                                fontSize = 8.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) Color(0xFF287E79) else Color.Transparent)
                                    .clickable { onOrbIsolationModeChange(mode) }
                                    .padding(horizontal = 1.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
                val elapsed = previewElapsedMs.coerceIn(0L, DIAGNOSTIC_STEP_DURATION_MS)
                val previewState = when {
                    stepStatus.startsWith("等待") -> stepStatus
                    previewCompleted -> "本步完成 · 等待下一步"
                    playbackPaused -> "已暂停 · ${(elapsed / 1000f).formatSeconds()} / 3.00s"
                    playbackMode == ScenePlaybackMode.AUTO -> "自动播放 · ${mappedReferenceMs}ms"
                    else -> "播放中 · ${(elapsed / 1000f).formatSeconds()} / 3.00s"
                }
                Column(
                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp)).background(Color(0xDDF8FFFC))
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "%02d / 13".format(stage.ordinal + 1),
                            color = Color(0xFF1D726D), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stage.displayName,
                            color = Color(0xFF163F43), fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 4.dp), maxLines = 1,
                        )
                    }
                    Text(
                        "${stage.name} · ${stage.timeAnchorLabel}",
                        color = Color(0xFF40696A), fontSize = 8.sp, maxLines = 1,
                    )
                    Text(
                        if (playbackMode == ScenePlaybackMode.MANUAL) {
                            "$previewState · 剩余 ${(DIAGNOSTIC_STEP_DURATION_MS - elapsed).coerceAtLeast(0L).let { (it / 1000f).formatSeconds() }}s"
                        } else "$previewState · ${mappedReferenceMs}ms",
                        color = if (previewState.startsWith("等待")) Color(0xFF9B6228) else Color(0xFF287E79),
                        fontSize = 8.sp, maxLines = 1,
                    )
                    if (playbackMode == ScenePlaybackMode.MANUAL) {
                        Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                            val y = size.height / 2f
                            drawLine(Color(0x443A7772), androidx.compose.ui.geometry.Offset(0f, y),
                                androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = size.height)
                            val progressX = size.width * (elapsed.toFloat() / DIAGNOSTIC_STEP_DURATION_MS).coerceIn(0f, 1f)
                            drawLine(Color(0xFF3E9B8E), androidx.compose.ui.geometry.Offset(0f, y),
                                androidx.compose.ui.geometry.Offset(progressX, y), strokeWidth = size.height)
                        }
                    }
                    Text(
                        if (detailsExpanded) "收起模型状态 −" else "模型状态 +",
                        modifier = Modifier.clickable { detailsExpanded = !detailsExpanded },
                        color = Color(0xFF386766), fontSize = 8.sp, maxLines = 1,
                    )
                    if (detailsExpanded) {
                        Text(modelStatus, color = Color(0xFF567577), fontSize = 9.sp, maxLines = 1)
                        Text(
                            "Detector:$detectorStatus · Classifier:$classifierStatus",
                            color = Color(0xFF567577), fontSize = 8.sp, maxLines = 2,
                        )
                        Text("SlimSAM:$slimSamStatus · $maskStatus", color = Color(0xFF567577), fontSize = 8.sp, maxLines = 2)
                        Text("Geometry:$geometryStatus · Scene:$sceneStatus", color = Color(0xFF567577), fontSize = 8.sp, maxLines = 2)
                    }
                    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                        val current = stage.ordinal
                        val radius = 2.5.dp.toPx()
                        val firstX = radius
                        val lastX = (size.width - radius).coerceAtLeast(firstX)
                        val stepX = (lastX - firstX) / (RecognitionSceneStage.entries.size - 1)
                        val centerY = size.height / 2f
                        drawLine(Color(0x553C7774), androidx.compose.ui.geometry.Offset(firstX, centerY),
                            androidx.compose.ui.geometry.Offset(lastX, centerY), 1.dp.toPx())
                        RecognitionSceneStage.entries.forEachIndexed { index, _ ->
                            val x = firstX + stepX * index
                            val color = when {
                                index < current -> Color(0xFF48A69A)
                                index == current && stepStatus.startsWith("等待") -> Color(0xFFE3A34A)
                                index == current -> Color(0xFFFFC45D)
                                else -> Color(0xFFB7C9C5)
                            }
                            drawCircle(color, radius = if (index == current) radius * 1.35f else radius,
                                center = androidx.compose.ui.geometry.Offset(x, centerY))
                            if (index == current) drawCircle(Color(0xFF476F6D), radius * 1.75f,
                                center = androidx.compose.ui.geometry.Offset(x, centerY),
                                style = Stroke(width = 1.dp.toPx()))
                        }
                    }
                }
            } else if (normalProgressText.isNotBlank()) {
                Text(
                    normalProgressText,
                    modifier = Modifier.padding(top = 5.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color(0xDDF8FFFC)).padding(horizontal = 8.dp, vertical = 6.dp),
                    color = Color(0xFF386766), fontSize = 10.sp, textAlign = TextAlign.End,
                )
            }
        }
        if (displayMode == RecognitionProgressDisplayMode.STEP_DIAGNOSTIC) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = safeInsets.calculateBottomPadding() + 20.dp)
                    .fillMaxWidth().padding(horizontal = 10.dp)
                    .clip(RoundedCornerShape(14.dp)).background(Color(0xDDF8FFFC)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Control("上一步", canControl && canGoPrevious, onPrevious, Modifier.weight(1f))
                Control("下一步", canControl && canGoNext, onNext, Modifier.weight(1.1f), primary = true)
                Control(
                    if (playbackPaused && previewCompleted) "本步完成" else if (playbackPaused) "继续" else "暂停",
                    canControl && !previewCompleted,
                    onPauseResume,
                    Modifier.weight(1f),
                )
                Control("重播本步", canControl, onReplay, Modifier.weight(1.05f))
                Control("自动播放", canControl, onAutoPlay, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Control(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    primary: Boolean = false,
) {
    val foreground = when {
        !enabled -> Color(0xFF9AABA8)
        primary -> Color.White
        else -> Color(0xFF386766)
    }
    Text(
        text,
        modifier = modifier.clip(RoundedCornerShape(10.dp))
            .background(if (primary && enabled) Color(0xFF287E79) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        color = foreground,
        fontSize = 9.sp,
        fontWeight = if (primary && enabled) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

private fun Float.formatSeconds(): String = "%1.2f".format(this)
