package com.yujian.ai.ai

/**
 * The single runtime source for the recognition processing timeline.
 *
 * The values mirror app/src/main/assets/identify/animation/identify_timeline.json.
 * Keeping the schedule in a small, platform-neutral object makes the Compose
 * layer deterministic and keeps instrumentation tests independent of a clock.
 */
object RecognitionRuntimeContract {
    const val CONTRACT_VERSION = "RECOGNITION_RUNTIME_v1"
    const val CAPTURED_END_MS = 800L
    const val DETECTING_END_MS = 1_500L
    const val OUTLINE_END_MS = 2_300L
    const val RESULT_START_MS = 3_000L

    val timeline: List<RecognitionTimelineStep> = listOf(
        RecognitionTimelineStep(RecognitionPhase.CAPTURED, 0L, "照片已准备好"),
        RecognitionTimelineStep(RecognitionPhase.DETECTING, CAPTURED_END_MS, "正在寻找鱼体"),
        RecognitionTimelineStep(RecognitionPhase.OUTLINE, DETECTING_END_MS, "鱼体轮廓出现"),
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
