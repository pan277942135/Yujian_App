package com.yujian.ai.ai.subject

import android.content.Context
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallClient
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

enum class SubjectModelStatus { NOT_READY, DOWNLOADING, READY, FAILED }

data class SubjectModelState(
    val status: SubjectModelStatus = SubjectModelStatus.NOT_READY,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

class FishSubjectModelManager(context: Context) : AutoCloseable {
    private val segmenter: SubjectSegmenter = SubjectSegmentation.getClient(
        SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build()
    )
    private val moduleInstallClient: ModuleInstallClient = ModuleInstall.getClient(context)

    suspend fun checkStatus(): SubjectModelState = withContext(Dispatchers.IO) {
        try {
            val availability = Tasks.await(moduleInstallClient.areModulesAvailable(segmenter))
            if (availability.areModulesAvailable()) SubjectModelState(SubjectModelStatus.READY)
            else SubjectModelState(SubjectModelStatus.NOT_READY)
        } catch (error: Exception) {
            failed(error)
        }
    }

    suspend fun prepareSubjectModel(): SubjectModelState = withContext(Dispatchers.IO) {
        try {
            val request = ModuleInstallRequest.newBuilder().addApi(segmenter).build()
            Tasks.await(moduleInstallClient.installModules(request))
            val deadline = System.currentTimeMillis() + DOWNLOAD_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                val availability = Tasks.await(moduleInstallClient.areModulesAvailable(segmenter))
                if (availability.areModulesAvailable()) return@withContext SubjectModelState(SubjectModelStatus.READY)
                delay(POLL_INTERVAL_MS)
            }
            SubjectModelState(SubjectModelStatus.FAILED, "MODEL_DOWNLOAD_TIMEOUT", "optional module did not become available before timeout")
        } catch (error: Exception) {
            failed(error)
        }
    }

    private fun failed(error: Exception): SubjectModelState = SubjectModelState(
        status = SubjectModelStatus.FAILED,
        errorCode = error::class.java.simpleName.ifBlank { "MODEL_DOWNLOAD_FAILED" },
        errorMessage = error.message ?: error.cause?.message ?: "model download failed",
    )

    override fun close() { segmenter.close() }

    private companion object {
        const val POLL_INTERVAL_MS = 500L
        const val DOWNLOAD_TIMEOUT_MS = 120_000L
    }
}
