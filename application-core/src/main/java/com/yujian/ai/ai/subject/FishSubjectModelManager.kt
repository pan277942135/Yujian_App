package com.yujian.ai.ai.subject

import kotlinx.coroutines.flow.StateFlow

enum class SubjectModelStatus { NOT_READY, DOWNLOADING, READY, FAILED }

data class SubjectModelState(
    val status: SubjectModelStatus = SubjectModelStatus.NOT_READY,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

/** UI state adapter. READY is reported only after both ONNX Sessions pass metadata validation. */
class FishSubjectModelManager(
    private val runtime: SlimSamRuntimeManager,
) : AutoCloseable {
    val runtimeState: StateFlow<SlimSamRuntimeSnapshot> = runtime.state

    fun checkStatus(): SubjectModelState = runtime.state.value.toSubjectModelState()

    suspend fun prepareSubjectModel(): SubjectModelState {
        runtime.startPreload()
        return runCatching { runtime.awaitReady() }
            .fold(
                onSuccess = { checkStatus() },
                onFailure = { checkStatus() },
            )
    }

    override fun close() {
        // Asset and Session lifetimes belong to the process runtime.
    }
}

fun SlimSamRuntimeSnapshot.toSubjectModelState(): SubjectModelState = when (state) {
    SlimSamRuntimeState.READY -> SubjectModelState(SubjectModelStatus.READY)
    SlimSamRuntimeState.FAILED, SlimSamRuntimeState.DEGRADED -> SubjectModelState(
        status = SubjectModelStatus.FAILED,
        errorCode = errorCode ?: "SLIMSAM_RUNTIME_NOT_READY",
        errorMessage = errorMessage ?: "SlimSAM Session initialization did not complete",
    )
    SlimSamRuntimeState.NOT_STARTED -> SubjectModelState(SubjectModelStatus.NOT_READY)
    SlimSamRuntimeState.ASSETS_VERIFYING,
    SlimSamRuntimeState.ORT_INITIALIZING,
    SlimSamRuntimeState.VISION_LOADING,
    SlimSamRuntimeState.DECODER_LOADING -> SubjectModelState(SubjectModelStatus.DOWNLOADING)
}
