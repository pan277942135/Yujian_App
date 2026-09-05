package com.yujian.ai.ai.subject

enum class SubjectStatus { IDLE, PROCESSING, READY, FAILED }

enum class FishSubjectQuality { GOOD, WARNING, INVALID }

data class FishSubjectResult(
    val status: SubjectStatus,
    val bitmapPath: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val exceptionClass: String? = null,
    val mlKitErrorCode: Int? = null,
    val rootCause: String? = null,
    val processingMs: Long = 0L,
    val roiWidth: Int = 0,
    val roiHeight: Int = 0,
    val maskSize: Int = 0,
    val expectedMaskSize: Int = 0,
    val maskAreaRatio: Float = 0f,
    val quality: FishSubjectQuality? = null,
)
