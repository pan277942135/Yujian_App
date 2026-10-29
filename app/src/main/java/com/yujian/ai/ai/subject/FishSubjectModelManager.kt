package com.yujian.ai.ai.subject

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SubjectModelStatus { NOT_READY, DOWNLOADING, READY, FAILED }

data class SubjectModelState(
    val status: SubjectModelStatus = SubjectModelStatus.READY,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

/**
 * Manages SlimSAM model readiness.
 *
 * SlimSAM quantized ONNX models are embedded directly inside Android assets
 * (slimsam/vision_encoder_quantized.onnx, slimsam/prompt_encoder_mask_decoder_quantized.onnx),
 * eliminating runtime network downloads.
 */
class FishSubjectModelManager(
    private val context: Context,
    @Suppress("UNUSED_PARAMETER") segmenterProvider: (() -> Any)? = null,
) : AutoCloseable {

    suspend fun checkStatus(): SubjectModelState = withContext(Dispatchers.IO) {
        val ready = try {
            context.assets.open(SlimSamSegmenter.VISION_ENCODER_MODEL).close()
            context.assets.open(SlimSamSegmenter.MASK_DECODER_MODEL).close()
            true
        } catch (_: Exception) {
            false
        }
        if (ready) {
            SubjectModelState(SubjectModelStatus.READY)
        } else {
            SubjectModelState(
                status = SubjectModelStatus.FAILED,
                errorCode = "MODEL_ASSET_MISSING",
                errorMessage = "SlimSAM model assets not found in APK",
            )
        }
    }

    suspend fun prepareSubjectModel(): SubjectModelState = checkStatus()

    override fun close() {
        // Assets are managed by Android AssetManager
    }
}
