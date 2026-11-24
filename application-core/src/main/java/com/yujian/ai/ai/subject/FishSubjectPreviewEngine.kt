package com.yujian.ai.ai.subject

import android.graphics.Bitmap
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ai.RecognitionModelIdentity
import com.yujian.ai.model.SelectedImage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SubjectCropConfig(val expandRatio: Float = 0.12f) {
    init { require(expandRatio >= 0f && expandRatio <= 1f) }
}

/** UI-facing facade for the Application-owned SlimSAM runtime. Closing this facade never closes Sessions. */
class FishSubjectPreviewEngine(
    private val runtime: SlimSamRuntimeManager,
    @Suppress("UNUSED_PARAMETER") private val config: SubjectCropConfig = SubjectCropConfig(),
) : AutoCloseable {
    /** Serializes this screen's prepare and decoder requests; the process runtime adds a global guard. */
    private val generationMutex = Mutex()

    suspend fun prepare(selected: SelectedImage, identity: RecognitionModelIdentity? = null) =
        generationMutex.withLock {
            runtime.prepare(selected, identity)
        }

    suspend fun generate(source: Bitmap, detectorBbox: NormalizedFishBox): FishSubjectResult =
        generationMutex.withLock {
            runtime.generate(source, detectorBbox)
        }

    suspend fun generate(
        selected: SelectedImage,
        detectorBbox: NormalizedFishBox,
        sessionId: String,
        attemptId: String,
    ): FishSubjectResult = generationMutex.withLock {
        val identity = RecognitionModelIdentity(
            sessionId, attemptId, selected.imageId, selected.photoSha256, selected.photoIdentity,
        )
        runtime.generate(selected, detectorBbox, identity)
    }

    override fun close() {
        // The facade is composition-scoped. Process Sessions are owned and closed by SlimSamRuntimeManager.
    }
}
