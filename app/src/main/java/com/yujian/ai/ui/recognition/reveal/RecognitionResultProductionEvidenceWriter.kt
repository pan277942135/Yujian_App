package com.yujian.ai.ui.recognition.reveal

import android.content.Context
import android.os.Build
import android.util.Log
import com.yujian.ai.BuildConfig
import com.yujian.ai.ui.waterlight.core.EnergyAnchor
import com.yujian.ai.ui.waterlight.core.EnergyEvent
import com.yujian.ai.ui.waterlight.core.EnergyFeedback
import com.yujian.ai.ui.waterlight.core.RecognitionSceneAnimationController
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStageTraceEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Writes app-private evidence from the current production SessionStore and shared scene controller. */
object RecognitionResultProductionEvidenceWriter {
    const val DIRECTORY_NAME = "recognition-runtime-result-handoff"
    private const val TAG = "RecognitionResultEvidence"

    fun reset(context: Context) {
        directory(context).apply {
            deleteRecursively()
            mkdirs()
        }
    }

    fun directory(context: Context): File = File(context.cacheDir, DIRECTORY_NAME)

    fun writeStableResult(
        context: Context,
        key: RecognitionRevealKey,
        store: RecognitionRevealSessionStore,
        controller: RecognitionSceneAnimationController,
    ): Boolean = runCatching {
        val output = directory(context).apply { mkdirs() }
        val snapshot = requireNotNull(store.resultSnapshot(key)) { "active result snapshot is missing" }
        val state = snapshot.handoffState
        val path = snapshot.fishPathSnapshot
        val returnEvent = controller.acceptedEnergyEventsSnapshot().singleOrNull {
            it.stage == RecognitionSceneStage.ORB_RETURN && it.feedback == EnergyFeedback.RETURN &&
                it.source == EnergyAnchor.FISH_CONTOUR
        }
        val timeline = controller.stageTimelineSnapshot()
        val expectedStages = RecognitionSceneStage.entries
        val handoffEvents = state.acceptedEvents
        val eventSequencePass = handoffEvents.map { it.eventSequence } == (1L..handoffEvents.size.toLong()).toList()
        val eventTimePass = handoffEvents.zipWithNext().all { (a, b) -> b.monotonicTimestampMs > a.monotonicTimestampMs }
        val identityPass = snapshot.image.imageId.isNotBlank() && snapshot.image.photoSha256.isNotBlank() &&
            key.recognitionSessionId == snapshot.handoff.key.recognitionSessionId &&
            key.recognitionAttemptId == snapshot.handoff.key.recognitionAttemptId &&
            handoffEvents.all {
                it.identity.sessionId == key.recognitionSessionId && it.identity.attemptId == key.recognitionAttemptId &&
                    it.identity.imageId == snapshot.image.imageId && it.identity.photoSha256 == snapshot.image.photoSha256 &&
                    it.resultIdentity == snapshot.prediction.top1.speciesKey &&
                    it.eventProducer == RECOGNITION_RESULT_HANDOFF_EVENT_PRODUCER &&
                    it.eventConsumer == RECOGNITION_RESULT_HANDOFF_EVENT_CONSUMER
            }
        val pathPass = path != null && path.isValid() && path.sessionId == key.recognitionSessionId &&
            path.attemptId == key.recognitionAttemptId && path.imageId == snapshot.image.imageId &&
            path.photoSha256 == snapshot.image.photoSha256 && returnEvent != null &&
            returnEvent.pathId == path.fishPathId && returnEvent.pathMaskSha256 == path.maskSha256 &&
            returnEvent.pathMaskVersion == path.maskVersion && returnEvent.pathGeometryVersion == path.geometryVersion &&
            returnEvent.pathContactPointNormalizedSource == path.pStarNormalizedSource &&
            returnEvent.pathSegments == path.returnPathSegments
        val timelineSequencePass = timeline.map { it.stage } == expectedStages
        val timelineContractPass = timelineSequencePass && timeline.all { it.stageDurationMs >= it.requiredDurationMs } &&
            timeline.sumOf { it.requiredDurationMs } == 6_000L
        val navStatuses = state.navigationTrace.map { it.status }
        val expectedNavigationStatuses = listOf(
            RecognitionHandoffTransactionStatus.CREATED,
            RecognitionHandoffTransactionStatus.PROCESSING_COMPLETE,
            RecognitionHandoffTransactionStatus.HANDOFF_READY,
            RecognitionHandoffTransactionStatus.NAVIGATION_REQUESTED,
            RecognitionHandoffTransactionStatus.RESULT_ATTACHED,
            RecognitionHandoffTransactionStatus.HANDOFF_COMPLETED,
        )
        val navigationPass = navStatuses == expectedNavigationStatuses &&
            state.navigationTrace.zipWithNext().all { (a, b) -> b.monotonicTimestampMs >= a.monotonicTimestampMs }
        val substagePass = state.completedSubstages == ResultHandoffSubstage.entries.toSet() &&
            handoffEvents.map { it.substage } == ResultHandoffSubstage.entries
        val subject = snapshot.subjectResult
        val geometry = snapshot.subjectGeometry
        val geometryPass = geometry != null && geometry.imageId == snapshot.image.imageId &&
            geometry.photoSha256 == snapshot.image.photoSha256 && geometry.contour.isNotEmpty() &&
            geometry.sourceWidthPx == snapshot.image.bitmap.width && geometry.sourceHeightPx == snapshot.image.bitmap.height
        val resultGeometryVersion = handoffEvents.lastOrNull()?.geometryVersion ?: 0L
        val resultGeometryPass = controller.resultGeometryIsReady() && resultGeometryVersion != 0L &&
            resultGeometryVersion in controller.resultGeometryVersionsSnapshot()
        val stablePass = state.resultSceneStable && state.playbackStatus == RevealPlaybackStatus.COMPLETED &&
            state.animationGeometryReleased && resultGeometryPass && identityPass && pathPass && substagePass &&
            snapshot.revealEligibility == RecognitionRevealEligibility.FULL_REVEAL &&
            snapshot.glyphSpeciesKey == snapshot.prediction.top1.speciesKey &&
            snapshot.glyphSpeciesName == snapshot.prediction.top1.speciesName && geometryPass &&
            state.has(ResultHandoffSubstage.PHOTO_SETTLE) && state.has(ResultHandoffSubstage.RESULT_INFO_ARRIVAL)

        val modelContractText = context.assets.open("model_release_contract.json").bufferedReader().use { it.readText() }
        writeText(output, "model_release_contract.json", modelContractText)

        val apkPath = File(context.applicationInfo.sourceDir)
        val apkSha = sha256(apkPath)
        writeJson(output, "runtime_provenance.json", JSONObject()
            .put("sourceCommitSha", BuildConfig.SOURCE_REVISION)
            .put("packageName", context.packageName)
            .put("apkPath", apkPath.name)
            .put("apkBytes", apkPath.length())
            .put("apkSha256", apkSha)
            .put("device", JSONObject()
                .put("sdkInt", Build.VERSION.SDK_INT)
                .put("manufacturer", Build.MANUFACTURER)
                .put("model", Build.MODEL)
                .put("fingerprint", Build.FINGERPRINT))
            .put("clockSource", "android.os.SystemClock.uptimeMillis")
            .put("capturedAtUptimeMs", android.os.SystemClock.uptimeMillis()))

        val identityJson = JSONObject()
            .put("recognitionSessionId", key.recognitionSessionId)
            .put("recognitionAttemptId", key.recognitionAttemptId)
            .put("imageId", snapshot.image.imageId)
            .put("photoSha256", snapshot.image.photoSha256)
            .put("photoContentVersion", snapshot.image.bitmap.generationId.toLong().coerceAtLeast(0L))
        writeJson(output, "runtime_matrix.json", JSONObject()
            .put("identity", identityJson)
            .put("firstFramePresentedTimestampMs", controller.firstFramePresentedTimestampMs() ?: JSONObject.NULL)
            .put("stageCount", timeline.size)
            .put("stageSequence", JSONArray().apply { expectedStages.forEach { put(it.name) } })
            .put("requiredAnimationBudgetMs", timeline.sumOf { it.requiredDurationMs })
            .put("activeAnimationElapsedMs", timeline.sumOf { it.activeDurationMs })
            .put("gateWaitElapsedMs", timeline.sumOf { it.gateWaitDurationMs })
            .put("stageSequencePass", timelineSequencePass)
            .put("stageDurationContractPass", timelineContractPass)
            .put("resultNavigationTimestampMs", state.navigationTrace.lastOrNull {
                it.status == RecognitionHandoffTransactionStatus.NAVIGATION_REQUESTED
            }?.monotonicTimestampMs ?: JSONObject.NULL)
            .put("resultStableTimestampMs", state.resultStableTimestampMs ?: JSONObject.NULL))
        writeJson(output, "stage_timeline.json", stageTimelineJson(timeline, identityJson))

        writeJson(output, "path_identity_trace.json", JSONObject()
            .put("identity", identityJson)
            .put("fullReveal", snapshot.revealEligibility == RecognitionRevealEligibility.FULL_REVEAL)
            .put("fishPathId", path?.fishPathId ?: JSONObject.NULL)
            .put("returnPathId", path?.returnPathId ?: JSONObject.NULL)
            .put("maskSha256", path?.maskSha256 ?: JSONObject.NULL)
            .put("maskVersion", path?.maskVersion ?: JSONObject.NULL)
            .put("geometryVersion", path?.geometryVersion ?: JSONObject.NULL)
            .put("pStarNormalizedSource", path?.pStarNormalizedSource?.let {
                JSONObject().put("x", it.x.toDouble()).put("y", it.y.toDouble())
            } ?: JSONObject.NULL)
            .put("pathSegments", path?.returnPathSegments?.let(::segmentsJson) ?: JSONArray())
            .put("acceptedAutomaticReturn", returnEvent?.let(::energyEventJson) ?: JSONObject.NULL)
            .put("status", if (pathPass) "PASS" else "FAIL"))
        writeJson(output, "return_event_trace.json", JSONObject()
            .put("acceptedAutomaticReturn", returnEvent?.let(::energyEventJson) ?: JSONObject.NULL)
            .put("accepted", returnEvent != null && pathPass)
            .put("source", returnEvent?.source?.name ?: JSONObject.NULL)
            .put("target", returnEvent?.target?.name ?: JSONObject.NULL))

        val handoffTrace = JSONObject(requireNotNull(store.resultHandoffTraceJson(key)))
        writeJson(output, "result_handoff_trace.json", handoffTrace)
        val navigationJson = JSONObject().put("identity", identityJson).put("transitions", JSONArray().apply {
            state.navigationTrace.forEach { item -> put(JSONObject()
                .put("status", item.status.name)
                .put("monotonicTimestampMs", item.monotonicTimestampMs)
                .put("source", item.source)
                .put("eventSequence", item.eventSequence)) }
        }).put("status", if (navigationPass) "PASS" else "FAIL")
        writeJson(output, "result_navigation_trace.json", navigationJson)

        val gates = JSONObject()
            .put("photoIdentity", snapshot.image.imageId.isNotBlank() && snapshot.image.photoSha256.isNotBlank())
            .put("speciesIdentity", snapshot.glyphSpeciesKey == snapshot.prediction.top1.speciesKey &&
                snapshot.glyphSpeciesName == snapshot.prediction.top1.speciesName)
            .put("detectorAndClassifierReady", snapshot.productionResult.ready &&
                snapshot.prediction.top1.speciesKey == "yellow_catfish" && snapshot.prediction.top1.confidence >= .55f)
            .put("expectedSpeciesKey", "yellow_catfish")
            .put("speciesConfidence", snapshot.prediction.top1.confidence.toDouble())
            .put("revealEligibility", snapshot.revealEligibility.name)
            .put("slimSamReady", subject.status.name == "READY" && subject.maskSha256.isNotBlank())
            .put("maskQualityGood", subject.quality?.name == "GOOD" && geometry?.quality?.name == "GOOD")
            .put("maskGeometryMapsToOriginalPhoto", geometryPass)
            .put("pathLedgerSnapshotPresent", pathPass)
            .put("automaticReturnAccepted", returnEvent != null && pathPass)
            .put("productionNavTransactionComplete", navigationPass)
            .put("sharedControllerResultGeometryReady", resultGeometryPass)
            .put("resultHandoffSequenceComplete", substagePass)
            .put("eventSequenceContiguous", eventSequencePass)
            .put("eventTimestampsStrictlyIncreasing", eventTimePass)
            .put("resultSceneStable", state.resultSceneStable)
            .put("playbackCompletedAfterStable", state.playbackStatus == RevealPlaybackStatus.COMPLETED && state.resultStableTimestampMs != null)
            .put("animationGeometryReleasedAfterStable", state.animationGeometryReleased &&
                state.geometryReleasedTimestampMs?.let { it >= (state.resultStableTimestampMs ?: Long.MAX_VALUE) } == true)
        writeJson(output, "result_stability_report.json", JSONObject()
            .put("status", if (stablePass) "PASS" else "FAIL")
            .put("resultGeometryVersion", resultGeometryVersion)
            .put("resultStableTimestampMs", state.resultStableTimestampMs ?: JSONObject.NULL)
            .put("geometryReleasedTimestampMs", state.geometryReleasedTimestampMs ?: JSONObject.NULL)
            .put("gates", gates))

        val checks = listOf(
            "T01_SESSION_IDENTITY" to (identityPass to "runtime_matrix.json"),
            "T02_ATTEMPT_ISOLATION" to (false to "NOT_EXERCISED_BY_THIS_ATTEMPT"),
            "T03_PHOTO_IDENTITY" to (identityPass to "runtime_matrix.json"),
            "T04_DETECTOR" to (snapshot.productionResult.ready to "model_release_contract.json"),
            "T05_CLASSIFIER" to ((snapshot.prediction.top1.speciesKey == "yellow_catfish" &&
                snapshot.prediction.top1.confidence >= .55f) to "result_handoff_trace.json"),
            "T06_SLIMSAM" to ((subject.status.name == "READY" && subject.quality?.name == "GOOD") to "path_identity_trace.json"),
            "T07_MASK_GEOMETRY" to (geometryPass to "result_stability_report.json"),
            "T08_PATH_LEDGER" to (pathPass to "path_identity_trace.json"),
            "T09_RETURN" to ((returnEvent != null && pathPass) to "return_event_trace.json"),
            "T10_SHARED_CONTROLLER" to ((resultGeometryPass && substagePass) to "stage_timeline.json"),
            "T11_CLOCK" to ((controller.firstFramePresentedTimestampMs() != null &&
                timeline.firstOrNull()?.stageStartTimestampMs == controller.firstFramePresentedTimestampMs() &&
                state.resultStableTimestampMs?.let { it > (controller.firstFramePresentedTimestampMs() ?: Long.MAX_VALUE) } == true) to "stage_timeline.json"),
            "T12_MODEL_GATE" to ((snapshot.productionResult.ready && subject.status.name == "READY") to "result_stability_report.json"),
            "T13_60S_DEADLINE" to (false to "REQUIRES_TIMEOUT_PATH_TEST"),
            "T14_STAGE_SEQUENCE" to (timelineSequencePass to "runtime_matrix.json"),
            "T15_STAGE_DURATION" to (timelineContractPass to "runtime_matrix.json"),
            "T16_PRODUCTION_NAVIGATION" to (navigationPass to "result_navigation_trace.json"),
            "T17_HANDOFF_IDENTITY" to (identityPass && pathPass to "result_handoff_trace.json"),
            "T18_NAME_STABLE" to (state.has(ResultHandoffSubstage.NAME_STABLE) to "result_handoff_trace.json"),
            "T19_PHOTO_SETTLE" to (state.has(ResultHandoffSubstage.PHOTO_SETTLE) to "result_handoff_trace.json"),
            "T20_RESULT_SCENE_HANDOFF" to (state.has(ResultHandoffSubstage.RESULT_SCENE_HANDOFF) to "result_handoff_trace.json"),
            "T21_RESULT_INFO_ARRIVAL" to (state.has(ResultHandoffSubstage.RESULT_INFO_ARRIVAL) to "result_handoff_trace.json"),
            "T22_RESULT_SCENE_STABLE" to (state.has(ResultHandoffSubstage.RESULT_SCENE_STABLE) to "result_handoff_trace.json"),
            "T23_EVENT_ORDERING" to (eventSequencePass && eventTimePass to "result_handoff_trace.json"),
            "T24_EVENT_IDEMPOTENCY" to (eventSequencePass && handoffEvents.distinctBy { it.eventSequence }.size == handoffEvents.size to "result_handoff_trace.json"),
            "T25_RESULT_STABLE" to (stablePass to "result_stability_report.json"),
            "T26_PLAYBACK" to ((state.playbackStatus == RevealPlaybackStatus.COMPLETED && state.resultSceneStable) to "result_stability_report.json"),
            "T27_APK_PROVENANCE" to (apkSha.isNotBlank() && BuildConfig.SOURCE_REVISION != "LOCAL_UNVERIFIED" to "runtime_provenance.json"),
            "T28_EVIDENCE_INTEGRITY" to (false to "HOST_MANIFEST_NOT_YET_BUILT"),
        )
        writeJson(output, "qualification_report.json", JSONObject()
            .put("status", if (checks.filter { it.first !in setOf("T02_ATTEMPT_ISOLATION", "T13_60S_DEADLINE", "T28_EVIDENCE_INTEGRITY") }.all { it.second.first }) "PASS_WITH_UNEXECUTED_CHECKS" else "FAIL")
            .put("sourceCommitSha", BuildConfig.SOURCE_REVISION)
            .put("apkSha256", apkSha)
            .put("checks", JSONArray().apply {
                checks.forEach { (id, value) -> put(JSONObject()
                    .put("id", id)
                    .put("status", if (value.first) "PASS" else if (id in setOf("T02_ATTEMPT_ISOLATION", "T13_60S_DEADLINE", "T28_EVIDENCE_INTEGRITY")) "NOT_EXECUTED" else "FAIL")
                    .put("evidence", value.second)) }
            }))
        Log.i(TAG, "stable evidence written=${stablePass} path=${output.absolutePath} source=${BuildConfig.SOURCE_REVISION}")
        stablePass
    }.onFailure { error ->
        Log.e(TAG, "unable to write production recognition evidence", error)
    }.getOrDefault(false)

    private fun stageTimelineJson(timeline: List<RecognitionSceneStageTraceEntry>, identity: JSONObject) =
        JSONObject().put("identity", identity).put("stages", JSONArray().apply {
            timeline.forEach { row -> put(JSONObject()
                .put("stageId", row.stage.name)
                .put("stageOrdinal", row.stageOrdinal)
                .put("stageStartTimestampMs", row.stageStartTimestampMs)
                .put("stageEndTimestampMs", row.stageEndTimestampMs)
                .put("stageDurationMs", row.stageDurationMs)
                .put("requiredDurationMs", row.requiredDurationMs)
                .put("activeDurationMs", row.activeDurationMs)
                .put("gateWaitDurationMs", row.gateWaitDurationMs)
                .put("gateWaitReason", row.gateWaitReason ?: JSONObject.NULL)
                .put("localProgress", row.localProgress.toDouble())
                .put("sessionId", row.sessionId)
                .put("attemptId", row.attemptId)
                .put("eventSequence", row.eventSequence)) }
        })

    private fun energyEventJson(event: EnergyEvent) = JSONObject()
        .put("sessionId", event.sessionId)
        .put("attemptId", event.attemptId)
        .put("imageId", event.imageId)
        .put("photoSha256", event.photoSha256)
        .put("photoContentVersion", event.photoContentVersion)
        .put("source", event.source.name)
        .put("target", event.target.name)
        .put("feedback", event.feedback.name)
        .put("geometryVersion", event.geometryVersion)
        .put("pathId", event.pathId ?: JSONObject.NULL)
        .put("pathMaskSha256", event.pathMaskSha256 ?: JSONObject.NULL)
        .put("pathMaskVersion", event.pathMaskVersion ?: JSONObject.NULL)
        .put("pathGeometryVersion", event.pathGeometryVersion ?: JSONObject.NULL)
        .put("pStarNormalizedSource", event.pathContactPointNormalizedSource?.let {
            JSONObject().put("x", it.x.toDouble()).put("y", it.y.toDouble())
        } ?: JSONObject.NULL)
        .put("pathSegments", segmentsJson(event.pathSegments))
        .put("eventSequence", event.eventSequence)
        .put("monotonicTimestampMs", event.monotonicTimestampMs)
        .put("stage", event.stage.name)
        .put("renderStatus", event.renderStatus)

    private fun segmentsJson(segments: List<com.yujian.ai.ui.waterlight.core.WaterLightPathGeometry>) =
        JSONArray().apply { segments.forEach { segment -> put(JSONObject()
            .put("start", pointJson(segment.start))
            .put("control1", pointJson(segment.control1))
            .put("control2", pointJson(segment.control2))
            .put("end", pointJson(segment.end))
            .put("coordinateSpace", segment.coordinateSpace)) } }

    private fun pointJson(point: com.yujian.ai.ui.waterlight.core.WaterLightPathPoint) =
        JSONObject().put("x", point.x.toDouble()).put("y", point.y.toDouble())

    private fun writeJson(directory: File, name: String, value: JSONObject) =
        writeText(directory, name, value.toString(2) + "\n")

    private fun writeText(directory: File, name: String, value: String) {
        directory.resolve(name).writeText(value, Charsets.UTF_8)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
