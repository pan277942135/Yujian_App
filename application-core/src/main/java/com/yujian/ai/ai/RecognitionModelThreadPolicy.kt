package com.yujian.ai.ai

/**
 * Experimental intra-op worker allocation for recognition runtimes.
 * Detector and Classifier run in sequence; SlimSAM may start after detector output while
 * classifier inference is still running. This candidate must be measured on the
 * API 36 runner; its presence and unit tests do not establish a performance improvement.
 * Runtime settings have a minimum of one worker per model, so devices exposing fewer than
 * three processors necessarily exceed that aggregate floor.
 */
internal data class RecognitionModelThreadAllocation(
    val availableProcessors: Int,
    val visionEncoderThreads: Int,
    val detectorThreads: Int,
    val classifierThreads: Int,
)

internal object RecognitionModelThreadPolicy {
    const val VALIDATION_STATUS = "PENDING_API36_RUNTIME"

    fun allocate(
        availableProcessors: Int = Runtime.getRuntime().availableProcessors(),
    ): RecognitionModelThreadAllocation {
        val processors = availableProcessors.coerceAtLeast(1)
        val visionThreads = (processors / 2).coerceIn(1, 4)
        val detectorThreads = (processors / 4).coerceIn(1, 2)
        val classifierThreads = (processors - visionThreads - detectorThreads).coerceIn(1, 4)
        return RecognitionModelThreadAllocation(
            availableProcessors = processors,
            visionEncoderThreads = visionThreads,
            detectorThreads = detectorThreads,
            classifierThreads = classifierThreads,
        )
    }
}
