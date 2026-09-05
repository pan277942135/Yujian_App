package com.yujian.ai.ai.subject

enum class SubjectStatus { IDLE, PROCESSING, READY, FAILED }

enum class FishSubjectQuality { GOOD, WARNING, INVALID }

data class FishSubjectResult(
    val status: SubjectStatus,
    val bitmapPath: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val errorCode: String? = null,
    val processingMs: Long = 0L,
    val maskAreaRatio: Float = 0f,
    val quality: FishSubjectQuality? = null,
)
