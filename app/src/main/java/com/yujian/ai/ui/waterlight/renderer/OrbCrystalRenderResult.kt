package com.yujian.ai.ui.waterlight.renderer

import com.yujian.ai.ui.waterlight.core.RecognitionSceneFrame
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage

internal enum class RenderPassStatus { NOT_READY, NOT_RUN, PASS, FAIL }

internal enum class OrbRenderFallbackStatus { ACTIVE, RELEASED }

/** Immutable record of the actual GL work and the host's presentation decision for one SceneFrame. */
internal data class OrbCrystalRenderResult(
    val identity: RecognitionSceneIdentity,
    val sceneClockMs: Long,
    val frameSequence: Long,
    val stage: RecognitionSceneStage,
    val stageProgress: Float,
    val eventSequence: Long,
    val geometryVersion: Long,
    val timestampMs: Long,
    val coreTextureStatus: RenderPassStatus,
    val coreAlphaStatus: RenderPassStatus,
    val shaderCompileLinkStatus: RenderPassStatus,
    val backFboStatus: RenderPassStatus,
    val residentBackStatus: RenderPassStatus,
    val coreFluidStatus: RenderPassStatus,
    val refractionStatus: RenderPassStatus,
    val r01bMaskStatus: RenderPassStatus,
    val r02DropletStatus: RenderPassStatus,
    val r03LightStatus: RenderPassStatus,
    val r05CausticsStatus: RenderPassStatus,
    val residentFrontStatus: RenderPassStatus,
    val bloomExtractStatus: RenderPassStatus,
    val bloomBlurHStatus: RenderPassStatus,
    val bloomBlurVStatus: RenderPassStatus,
    val compositeStatus: RenderPassStatus,
    val eglSwapStatus: RenderPassStatus = RenderPassStatus.NOT_RUN,
    val presentedFrameSequence: Long? = null,
    val fallbackStatus: OrbRenderFallbackStatus = OrbRenderFallbackStatus.ACTIVE,
    val failureReason: String? = null,
) {
    init {
        require(sceneClockMs >= 0L)
        require(frameSequence > 0L)
        require(stageProgress in 0f..1f)
        require(eventSequence >= 0L)
        require(geometryVersion >= 0L)
        require(timestampMs >= 0L)
        require(presentedFrameSequence == null || presentedFrameSequence > 0L)
    }

    val ownerAccepted: Boolean get() = fallbackStatus == OrbRenderFallbackStatus.RELEASED

    fun matches(frame: RecognitionSceneFrame): Boolean =
        identity == frame.identity &&
            sceneClockMs == frame.sceneElapsedMs &&
            stage == frame.stage &&
            stageProgress == frame.stageProgress &&
            eventSequence == frame.eventSequence &&
            geometryVersion == frame.geometryVersion

    /** Completes the immutable renderer receipt with the EGL host's real swap result. */
    fun withPresentation(
        swapStatus: RenderPassStatus,
        presentedSequence: Long?,
        hostFailureReason: String? = null,
    ): OrbCrystalRenderResult {
        val candidate = copy(
            eglSwapStatus = swapStatus,
            presentedFrameSequence = presentedSequence,
            fallbackStatus = OrbRenderFallbackStatus.ACTIVE,
            failureReason = hostFailureReason ?: failureReason,
        )
        val rejection = candidate.rejectionReason()
        return candidate.copy(
            fallbackStatus = if (rejection == null) OrbRenderFallbackStatus.RELEASED
            else OrbRenderFallbackStatus.ACTIVE,
            failureReason = rejection,
        )
    }

    fun rejectionReason(): String? {
        if (!hasValidIdentity()) return "FRAME_IDENTITY_INVALID"
        if (!failureReason.isNullOrBlank()) return failureReason
        val passFailure = passFailureReason()
        if (passFailure != null) return passFailure
        if (eglSwapStatus != RenderPassStatus.PASS) return "EGL_SWAP_${eglSwapStatus.name}"
        if (presentedFrameSequence != frameSequence) return "PRESENTED_SEQUENCE_MISMATCH"
        return null
    }

    /** Passes the renderer must validate before the EGL host is allowed to swap. */
    fun prePresentationFailureReason(expectedFrame: RecognitionSceneFrame): String? {
        if (!hasValidIdentity()) return "FRAME_IDENTITY_INVALID"
        if (!matches(expectedFrame)) return "FRAME_IDENTITY_OR_SCENE_MISMATCH"
        if (!failureReason.isNullOrBlank()) return failureReason
        return passFailureReason()
    }

    private fun hasValidIdentity(): Boolean = identity.sessionId.isNotBlank() &&
        identity.attemptId.isNotBlank() && identity.imageId.isNotBlank() &&
        !identity.photoSha256.isNullOrBlank() && identity.photoContentVersion >= 0L

    private fun passFailureReason(): String? {
        val statuses = requiredStatuses().filter { it.first != "eglSwapStatus" }
        val notReady = statuses.filter { it.second == RenderPassStatus.NOT_READY }
        if (notReady.isNotEmpty()) return "PASS_NOT_READY:${notReady.joinToString(",") { it.first }}"
        val notRun = statuses.filter { it.second == RenderPassStatus.NOT_RUN }
        if (notRun.isNotEmpty()) return "PASS_NOT_RUN:${notRun.joinToString(",") { it.first }}"
        val failed = statuses.filter { it.second == RenderPassStatus.FAIL }
        if (failed.isNotEmpty()) return "PASS_FAILED:${failed.joinToString(",") { it.first }}"
        if (statuses.any { it.second != RenderPassStatus.PASS }) return "PASS_STATUS_UNRESOLVED"
        return null
    }

    fun diagnosticFields(): Map<String, Any?> = linkedMapOf(
        "renderResultVersion" to 1,
        "sceneClockMs" to sceneClockMs,
        "frameSequence" to frameSequence,
        "presentedFrameSequence" to presentedFrameSequence,
        "stage" to stage.name,
        "stageProgress" to stageProgress,
        "eventSequence" to eventSequence,
        "geometryVersion" to geometryVersion,
        "timestampMs" to timestampMs,
        "coreTextureStatus" to coreTextureStatus.name,
        "coreAlphaStatus" to coreAlphaStatus.name,
        "shaderCompileLinkStatus" to shaderCompileLinkStatus.name,
        "backFboStatus" to backFboStatus.name,
        "residentBackStatus" to residentBackStatus.name,
        "coreFluidStatus" to coreFluidStatus.name,
        "refractionStatus" to refractionStatus.name,
        "r01bMaskStatus" to r01bMaskStatus.name,
        "r02DropletStatus" to r02DropletStatus.name,
        "r03LightStatus" to r03LightStatus.name,
        "r05CausticsStatus" to r05CausticsStatus.name,
        "residentFrontStatus" to residentFrontStatus.name,
        "bloomExtractStatus" to bloomExtractStatus.name,
        "bloomBlurHStatus" to bloomBlurHStatus.name,
        "bloomBlurVStatus" to bloomBlurVStatus.name,
        "compositeStatus" to compositeStatus.name,
        "eglSwapStatus" to eglSwapStatus.name,
        "fallbackStatus" to fallbackStatus.name,
        "ownerAccepted" to ownerAccepted,
        "failureReason" to failureReason,
    )

    private fun requiredStatuses(): List<Pair<String, RenderPassStatus>> = listOf(
        "coreTextureStatus" to coreTextureStatus,
        "coreAlphaStatus" to coreAlphaStatus,
        "shaderCompileLinkStatus" to shaderCompileLinkStatus,
        "backFboStatus" to backFboStatus,
        "residentBackStatus" to residentBackStatus,
        "coreFluidStatus" to coreFluidStatus,
        "refractionStatus" to refractionStatus,
        "r01bMaskStatus" to r01bMaskStatus,
        "r02DropletStatus" to r02DropletStatus,
        "r03LightStatus" to r03LightStatus,
        "r05CausticsStatus" to r05CausticsStatus,
        "residentFrontStatus" to residentFrontStatus,
        "bloomExtractStatus" to bloomExtractStatus,
        "bloomBlurHStatus" to bloomBlurHStatus,
        "bloomBlurVStatus" to bloomBlurVStatus,
        "compositeStatus" to compositeStatus,
        "eglSwapStatus" to eglSwapStatus,
    )
}

internal data class GlFrameExecution(
    val drawStatus: RenderPassStatus,
    val renderResult: OrbCrystalRenderResult? = null,
    val failureReason: String? = null,
)

internal data class GlFramePresentationDecision(
    val maySwap: Boolean,
    val rejectionReason: String? = null,
)

/** Shared host gate used by production EGL and deterministic fault-injection tests. */
internal object GlFramePresentationGate {
    fun beforeSwap(
        execution: GlFrameExecution,
        expectedFrame: RecognitionSceneFrame?,
        expectedFrameSequence: Long? = null,
        requireResult: Boolean = false,
    ): GlFramePresentationDecision {
        if (execution.drawStatus != RenderPassStatus.PASS) {
            return GlFramePresentationDecision(false, execution.failureReason ?: "DRAW_PIPELINE_${execution.drawStatus.name}")
        }
        val result = execution.renderResult
        if (result == null) {
            return if (requireResult) GlFramePresentationDecision(false, "RENDER_RESULT_MISSING")
            else GlFramePresentationDecision(true)
        }
        if (expectedFrame == null) return GlFramePresentationDecision(false, "SCENE_FRAME_NOT_BOUND")
        if (expectedFrameSequence != null && result.frameSequence != expectedFrameSequence) {
            return GlFramePresentationDecision(false, "FRAME_SEQUENCE_MISMATCH")
        }
        val failure = result.prePresentationFailureReason(expectedFrame)
        return GlFramePresentationDecision(failure == null, failure)
    }

    fun afterSwap(
        result: OrbCrystalRenderResult,
        swapStatus: RenderPassStatus,
        presentedSequence: Long?,
        failureReason: String? = null,
    ): OrbCrystalRenderResult = result.withPresentation(
        swapStatus = swapStatus,
        presentedSequence = presentedSequence,
        hostFailureReason = failureReason,
    )
}

internal object OrbRenderOwnershipPolicy {
    fun accept(result: OrbCrystalRenderResult, expectedFrame: RecognitionSceneFrame): Boolean =
        result.matches(expectedFrame) && result.rejectionReason() == null &&
            result.fallbackStatus == OrbRenderFallbackStatus.RELEASED
}

/** UI ownership reducer. It never advances SceneClock and ignores stale or cross-scene receipts. */
internal class OrbRenderOwnershipState {
    private var lastAppliedFrameSequence = 0L
    private var accepted = false

    fun apply(result: OrbCrystalRenderResult, currentFrame: RecognitionSceneFrame): Boolean {
        val sameOwner = result.identity == currentFrame.identity &&
            result.geometryVersion == currentFrame.geometryVersion
        if (!sameOwner || result.frameSequence <= lastAppliedFrameSequence) return accepted
        lastAppliedFrameSequence = result.frameSequence
        // The EGL host already checked the exact submitted SceneFrame before swap. The UI may
        // have advanced to a later clock sample by the time this receipt reaches the main thread.
        accepted = result.ownerAccepted && result.rejectionReason() == null
        return accepted
    }
}
