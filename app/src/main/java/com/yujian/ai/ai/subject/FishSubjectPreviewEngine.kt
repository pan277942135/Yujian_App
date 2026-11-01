package com.yujian.ai.ai.subject

import android.content.Context
import android.graphics.Bitmap
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.model.SelectedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SubjectCropConfig(val expandRatio: Float = 0.12f) {
    init { require(expandRatio >= 0f && expandRatio <= 1f) }
}

/**
 * Post-recognition preview engine.
 *
 * It never runs detection or changes the classifier input.
 * Backed by built-in SlimSAM pixel segmentation ONNX models.
 */
class FishSubjectPreviewEngine(
    private val context: Context,
    private val config: SubjectCropConfig = SubjectCropConfig(),
) : AutoCloseable {

    private val slimSamSegmenter: SlimSamSegmenter by lazy {
        SlimSamSegmenter(context, config.expandRatio)
    }

    suspend fun generate(source: Bitmap, detectorBbox: NormalizedFishBox): FishSubjectResult =
        withContext(Dispatchers.Default) {
            slimSamSegmenter.segment(source, detectorBbox)
        }

    suspend fun generate(selected: SelectedImage, detectorBbox: NormalizedFishBox, sessionId: String, attemptId: String): FishSubjectResult =
        withContext(Dispatchers.Default) {
            slimSamSegmenter.segment(selected.bitmap, detectorBbox, sessionId, attemptId, selected.imageId, selected.photoSha256)
        }

    override fun close() {
        slimSamSegmenter.close()
    }
}
