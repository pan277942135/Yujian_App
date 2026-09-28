package com.yujian.ai.ai

/**
 * Compatibility representation of the frozen Recognition Processing V1.1 timeline.
 *
 * Product presentation is driven by RecognitionVisualStateController. These cumulative
 * boundaries exist only for packaged contract assets and tests; they MUST mirror the
 * V1.1 Design Closure and must never become a second timing authority.
 */
object RecognitionRuntimeContract {
    const val CONTRACT_VERSION = "RECOGNITION_RUNTIME_v1_1"

    // Cumulative boundaries for 350 / 600 / 600 / 1250 ms.
    const val CAPTURED_END_MS = 350L
    const val DETECTING_END_MS = 950L
    const val OUTLINE_END_MS = 1_550L
    const val RESULT_START_MS = 2_800L

    val timeline: List<RecognitionTimelineStep> = listOf(
        RecognitionTimelineStep(RecognitionPhase.CAPTURED, 0L, "正在准备识别"),
        RecognitionTimelineStep(RecognitionPhase.DETECTING, CAPTURED_END_MS, "正在理解这张照片"),
        RecognitionTimelineStep(RecognitionPhase.OUTLINE, DETECTING_END_MS, "已定位到鱼体"),
        RecognitionTimelineStep(RecognitionPhase.CLASSIFYING, OUTLINE_END_MS, "正在认识这条鱼"),
        RecognitionTimelineStep(RecognitionPhase.RESULT, RESULT_START_MS, "认识完成"),
    )

    init {
        require(timeline.first().startMs == 0L)
        require(timeline.zipWithNext().all { (current, next) -> current.startMs < next.startMs })
        require(timeline.last().phase == RecognitionPhase.RESULT)
    }

    fun phaseAt(elapsedMs: Long): RecognitionPhase {
        val elapsed = elapsedMs.coerceAtLeast(0L)
        return timeline.lastOrNull { elapsed >= it.startMs }?.phase ?: RecognitionPhase.CAPTURED
    }

    fun labelFor(phase: RecognitionPhase): String =
        timeline.first { it.phase == phase }.label
}

data class RecognitionTimelineStep(
    val phase: RecognitionPhase,
    val startMs: Long,
    val label: String,
)
