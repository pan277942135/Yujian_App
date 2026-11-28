package com.yujian.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yujian.ai.ai.FishRecognitionPipeline
import com.yujian.ai.ai.RecognitionModelIdentity
import com.yujian.ai.ai.subject.FishSubjectQuality
import com.yujian.ai.ai.subject.SlimSamRuntimeEvent
import com.yujian.ai.ai.subject.SlimSamRuntimeManager
import com.yujian.ai.ai.subject.SlimSamRuntimeState
import com.yujian.ai.ai.subject.SlimSamSegmenter
import com.yujian.ai.media.RecognitionImageStore
import com.yujian.ai.ui.screens.extractContour
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Captures the production inference path on the pinned Fish-Mask Demo photo before any runtime tuning. */
@RunWith(AndroidJUnit4::class)
class FishMaskApi36PerformanceBaselineTest {
    @Test
    fun coldStartWaitsForRealVisionAndDecoderSessions() = runBlocking {
        assertEquals("cold-start evidence requires API36", 36, android.os.Build.VERSION.SDK_INT)
        val sampleId = InstrumentationRegistry.getArguments().getString("cold_start_id")
            ?: error("cold_start_id instrumentation argument is required")
        require(sampleId in setOf("1", "2", "3")) { "unexpected cold-start sample id: $sampleId" }
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = (target.applicationContext as YujianApplication).slimSamRuntimeManager
        runtime.awaitReady()
        val state = runtime.state.value
        assertEquals(SlimSamRuntimeState.READY, state.state)
        assertTrue(state.visionSessionReady && state.decoderSessionReady)
        assertEquals(SlimSamSegmenter.VISION_ENCODER_SHA256, state.visionModelSha256)
        assertEquals(SlimSamSegmenter.DECODER_MODEL_SHA256, state.decoderModelSha256)
        val events = runtime.snapshotEvents()
        val appCreate = events.firstOrNull { it.eventName == "APP_CREATE_START" }
            ?: error("Application cold-start timestamp was not recorded")
        val ready = events.firstOrNull { it.eventName == "SLIMSAM_RUNTIME_READY" }
            ?: error("SlimSAM runtime READY receipt was not recorded")
        check(ready.endUptimeNs >= appCreate.startUptimeNs)
        val sample = JSONObject()
            .put("sample_id", sampleId)
            .put("status", "PASS")
            .put("app_pid", Process.myPid())
            .put("thread_id", Process.myTid())
            .put("app_create_start_uptime_ns", appCreate.startUptimeNs)
            .put("runtime_ready_uptime_ns", ready.endUptimeNs)
            .put("cold_start_to_runtime_ready_ms", (ready.endUptimeNs - appCreate.startUptimeNs) / 1_000_000.0)
            .put("vision_session_ready", state.visionSessionReady)
            .put("decoder_session_ready", state.decoderSessionReady)
            .put("vision_model_sha256", state.visionModelSha256)
            .put("decoder_model_sha256", state.decoderModelSha256)
            .put("intra_op_threads", state.intraOpThreads ?: JSONObject.NULL)
            .put("api_level", android.os.Build.VERSION.SDK_INT)
        val root = File(target.cacheDir, "YuJian_SlimSAM_Preload_Evidence/performance/baseline")
        check(root.isDirectory || root.mkdirs())
        File(root, "cold_start_$sampleId.json").writeText(sample.toString(2) + "\n")
        val samples = (1..3).mapNotNull { index ->
            File(root, "cold_start_$index.json").takeIf { it.isFile }?.let { JSONObject(it.readText()) }
        }
        if (samples.size == 3) {
            assertTrue(samples.all { it.optString("status") == "PASS" })
            assertEquals(listOf("1", "2", "3"), samples.map { it.optString("sample_id") })
            assertTrue(samples.all {
                it.optLong("app_pid") > 0 &&
                    it.optLong("app_create_start_uptime_ns") > 0 &&
                    it.optLong("runtime_ready_uptime_ns") > it.optLong("app_create_start_uptime_ns")
            })
            assertEquals(3, samples.map { it.optLong("app_create_start_uptime_ns") }.toSet().size)
            File(root, "cold_start_summary.json").writeText(JSONObject()
                .put("status", "THREE_COLD_STARTS_CAPTURED")
                .put("sample_count", samples.size)
                .put("samples", JSONArray().apply { samples.forEach { put(it) } })
                .toString(2) + "\n")
        }
    }

    @Test
    fun tenPresetFishCacheMissesRunProductionRecognitionAndMask() = runBlocking {
        assertEquals("Fish-Mask performance baseline requires a real API36 runtime", 36, android.os.Build.VERSION.SDK_INT)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val test = instrumentation.context
        val app = target.applicationContext as YujianApplication
        val runtime = app.slimSamRuntimeManager
        runtime.startPreload()
        runtime.awaitReady()
        assertEquals(SlimSamRuntimeState.READY, runtime.state.value.state)

        val assetPath = "performance/fishmask-demo/preset_fish.jpg"
        val assetBytes = test.assets.open(assetPath).use { it.readBytes() }
        val assetSha = assetBytes.sha256()
        assertEquals(PRESET_FISH_SHA256, assetSha)
        val decodeNormalizeStartedNs = SystemClock.elapsedRealtimeNanos()
        val selected = RecognitionImageStore.normalizeAsset(test, target, assetPath, "api36_fishmask_performance_baseline")
        val normalizedPhotoReadyNs = SystemClock.elapsedRealtimeNanos()
        val photoIdentity = requireNotNull(selected.photoIdentity)
        assertTrue(photoIdentity.isValid())
        assertEquals(assetSha, photoIdentity.sourceBytesSha256)
        assertEquals(selected.photoSha256, photoIdentity.canonicalPixelSha256)

        val evidenceRoot = File(target.cacheDir, "YuJian_SlimSAM_Preload_Evidence/performance/baseline")
        check(evidenceRoot.isDirectory || evidenceRoot.mkdirs())
        val rows = Collections.synchronizedList(mutableListOf<JSONObject>())
        val repeatHits = Collections.synchronizedList(mutableListOf<JSONObject>())
        val runtimeReadyEvent = runtime.snapshotEvents().firstOrNull { it.eventName == "SLIMSAM_RUNTIME_READY" }
        val failure = java.util.concurrent.atomic.AtomicReference<String?>(null)
        var lastIdentity: RecognitionModelIdentity? = null
        // The production UI owns one pipeline across attempts. Reuse it here so the
        // baseline measures independent photo attempts without recreating model sessions.
        val pipeline = FishRecognitionPipeline(target)

        try {
            repeat(10) { index ->
                val attemptId = "fishmask-preset-fish-baseline-${index + 1}"
                val identity = RecognitionModelIdentity(
                    sessionId = "fishmask-api36-baseline",
                    attemptId = attemptId,
                    imageId = selected.imageId,
                    photoSha256 = selected.photoSha256,
                    photoIdentity = photoIdentity,
                )
                lastIdentity = identity
                val memoryBefore = memorySample("RUN_${index + 1}_START", runtime)
                val rowStartedNs = SystemClock.elapsedRealtimeNanos()
                val cpuStartedMs = Process.getElapsedCpuTime()
                val gcCountStarted = Debug.getGlobalGcInvocationCount()
                val allocCountStarted = Debug.getGlobalAllocCount()
                val pipelineEvents = Collections.synchronizedList(mutableListOf<JSONObject>())
                var pipelineDurationMs: Double? = null
                var recognitionStatus = "NOT_RUN"
                var maskStatus = "NOT_RUN"
                var maskQuality = "NOT_RUN"
                var maskReadyNs: Long? = null
                var contourClosedNs: Long? = null
                var contourExtractMs: Double? = null
                var maskPngDecodeMs: Double? = null
                var contourCount = 0
                var contourClosed = false
                var maskSha256: String? = null
                var error: String? = null
                try {
                    val recognitionStartedNs = SystemClock.elapsedRealtimeNanos()
                    val recognition = coroutineScope {
                        val embedding = async(Dispatchers.Default) { runtime.prepare(selected, identity) }
                        val result = async(Dispatchers.Default) {
                            pipeline.recognize(
                                bitmap = selected.bitmap,
                                onProgress = { progress ->
                                    pipelineEvents += JSONObject()
                                        .put("event", "PIPELINE_PHASE")
                                        .put("phase", progress.phase.name)
                                        .put("uptime_ns", SystemClock.elapsedRealtimeNanos())
                                        .put("thread_id", Process.myTid())
                                        .put("thread_name", Thread.currentThread().name)
                                },
                                modelIdentity = identity,
                                onDiagnostic = { event, stage, status, details, cause ->
                                    pipelineEvents += JSONObject()
                                        .put("event", event)
                                        .put("stage", stage)
                                        .put("status", status)
                                        .put("details", JSONObject(details))
                                        .put("error", cause?.javaClass?.name ?: JSONObject.NULL)
                                        .put("uptime_ns", SystemClock.elapsedRealtimeNanos())
                                        .put("thread_id", Process.myTid())
                                        .put("thread_name", Thread.currentThread().name)
                                },
                            )
                        }
                        embedding.await()
                        result.await()
                    }
                    pipelineDurationMs = (SystemClock.elapsedRealtimeNanos() - recognitionStartedNs) / 1_000_000.0
                    recognitionStatus = recognition.status.name
                    check(recognition.ready) { "DETECTOR_CLASSIFIER_NOT_READY:${recognition.failureCode}" }
                    val prediction = requireNotNull(recognition.prediction)
                    check(prediction.classifierInputReceipt?.isValid() == true) { "CLASSIFIER_INPUT_RECEIPT_INVALID" }
                    check(prediction.classifierOutputReceipt?.isValid() == true) { "CLASSIFIER_OUTPUT_RECEIPT_INVALID" }
                    check(prediction.classifierInputReceipt?.attemptId == attemptId) { "CLASSIFIER_ATTEMPT_ID_MISMATCH" }
                    check(prediction.classifierOutputReceipt?.photoId == selected.imageId) { "CLASSIFIER_PHOTO_ID_MISMATCH" }

                    val detector = requireNotNull(recognition.assessment.primary) { "DETECTOR_PRIMARY_MISSING" }
                    val mask = runtime.generate(selected, detector.box.normalized(), identity)
                    maskStatus = mask.status.name
                    maskQuality = mask.quality?.name ?: "UNKNOWN"
                    maskSha256 = mask.maskSha256
                    if (mask.status == com.yujian.ai.ai.subject.SubjectStatus.READY && mask.quality == FishSubjectQuality.GOOD) {
                        maskReadyNs = SystemClock.elapsedRealtimeNanos()
                    }
                    check(maskReadyNs != null) { "MASK_NOT_TRUSTED:${mask.status}:${mask.quality}:${mask.errorCode}" }
                    check(mask.maskSha256.isNotBlank()) { "MASK_SHA256_MISSING" }
                    check(File(requireNotNull(mask.bitmapPath)).readBytes().sha256() == mask.maskSha256) {
                        "MASK_PNG_SHA256_MISMATCH"
                    }
                    check(mask.geometry?.matches(
                        identity.sessionId,
                        identity.attemptId,
                        selected.imageId,
                        selected.photoSha256,
                        selected.bitmap.width,
                        selected.bitmap.height,
                    ) == true) { "MASK_GEOMETRY_IDENTITY_MISMATCH" }

                    val maskPngDecodeStartedNs = SystemClock.elapsedRealtimeNanos()
                    val maskBitmap = BitmapFactory.decodeFile(requireNotNull(mask.bitmapPath))
                        ?: error("MASK_PNG_DECODE_FAILED")
                    maskPngDecodeMs = (SystemClock.elapsedRealtimeNanos() - maskPngDecodeStartedNs) / 1_000_000.0
                    val contourStartNs = SystemClock.elapsedRealtimeNanos()
                    try {
                        val contour = extractContour(maskBitmap)
                        contourExtractMs = (SystemClock.elapsedRealtimeNanos() - contourStartNs) / 1_000_000.0
                        contourCount = contour.size
                        contourClosed = contour.any { it.closesContour }
                        if (contourClosed) contourClosedNs = SystemClock.elapsedRealtimeNanos()
                    } finally {
                        maskBitmap.recycle()
                    }
                    check(contourClosed) { "PRODUCTION_MASK_CONTOUR_NOT_CLOSED segments=$contourCount" }

                    val attemptEvents = runtime.snapshotEvents().filter { it.recognitionAttemptId == attemptId }
                    check(attemptEvents.count { it.eventName == "EMBEDDING_CACHE_MISS" } == 1) {
                        "EXPECTED_ONE_EMBEDDING_CACHE_MISS attempt=$attemptId"
                    }
                    check(attemptEvents.count { it.eventName == "VISION_ENCODER_END" && it.result == "PASS" } == 1) {
                        "EXPECTED_ONE_REAL_ENCODER_RUN attempt=$attemptId"
                    }
                    check(attemptEvents.count { it.eventName == "MASK_DECODER_END" && it.result == "PASS" } == 1) {
                        "EXPECTED_ONE_REAL_DECODER_RUN attempt=$attemptId"
                    }
                    rows += buildRunRow(
                        index = index + 1,
                        identity = identity,
                        selected = selected,
                        measurementStartNs = rowStartedNs,
                        rowStartedNs = rowStartedNs,
                        pipelineDurationMs = pipelineDurationMs,
                        recognitionStatus = recognitionStatus,
                        maskStatus = maskStatus,
                        maskQuality = maskQuality,
                        maskReadyNs = maskReadyNs,
                        contourClosedNs = contourClosedNs,
                        contourExtractMs = contourExtractMs,
                        maskPngDecodeMs = maskPngDecodeMs,
                        contourCount = contourCount,
                        contourClosed = contourClosed,
                        maskSha256 = maskSha256,
                        runtimeEvents = attemptEvents,
                        pipelineEvents = synchronized(pipelineEvents) { pipelineEvents.toList() },
                        cpuMs = Process.getElapsedCpuTime() - cpuStartedMs,
                        gcCount = Debug.getGlobalGcInvocationCount() - gcCountStarted,
                        allocationCount = Debug.getGlobalAllocCount() - allocCountStarted,
                        memoryBefore = memoryBefore,
                        memoryAfter = memorySample("RUN_${index + 1}_END", runtime),
                        result = "PASS",
                        error = null,
                    )
                } catch (t: Throwable) {
                    error = "${t.javaClass.simpleName}:${t.message.orEmpty()}"
                    val attemptEvents = runtime.snapshotEvents().filter { it.recognitionAttemptId == attemptId }
                    rows += buildRunRow(
                        index = index + 1,
                        identity = identity,
                        selected = selected,
                        measurementStartNs = rowStartedNs,
                        rowStartedNs = rowStartedNs,
                        pipelineDurationMs = pipelineDurationMs,
                        recognitionStatus = recognitionStatus,
                        maskStatus = maskStatus,
                        maskQuality = maskQuality,
                        maskReadyNs = maskReadyNs,
                        contourClosedNs = contourClosedNs,
                        contourExtractMs = contourExtractMs,
                        maskPngDecodeMs = maskPngDecodeMs,
                        contourCount = contourCount,
                        contourClosed = contourClosed,
                        maskSha256 = maskSha256,
                        runtimeEvents = attemptEvents,
                        pipelineEvents = synchronized(pipelineEvents) { pipelineEvents.toList() },
                        cpuMs = Process.getElapsedCpuTime() - cpuStartedMs,
                        gcCount = Debug.getGlobalGcInvocationCount() - gcCountStarted,
                        allocationCount = Debug.getGlobalAllocCount() - allocCountStarted,
                        memoryBefore = memoryBefore,
                        memoryAfter = memorySample("RUN_${index + 1}_FAIL", runtime),
                        result = "FAIL",
                        error = error,
                    )
                }
                writeBaselineArtifacts(
                    root = evidenceRoot,
                    rows = rows.toList(),
                    cacheHits = repeatHits.toList(),
                    selected = selected,
                    assetSha = assetSha,
                    normalizedPhotoReadyNs = normalizedPhotoReadyNs,
                    decodeNormalizeStartedNs = decodeNormalizeStartedNs,
                    runtimeReadyEvent = runtimeReadyEvent,
                    runtime = runtime,
                    buildSha = InstrumentationRegistry.getArguments().getString("source_sha") ?: "NOT_PROVIDED",
                    failure = failure.get(),
                )
            }

            val cacheIdentity = requireNotNull(lastIdentity)
            repeat(5) { hitIndex ->
                val started = SystemClock.elapsedRealtimeNanos()
                runtime.prepare(selected, cacheIdentity)
                val ended = SystemClock.elapsedRealtimeNanos()
                val hits = runtime.snapshotEvents().count {
                    it.recognitionAttemptId == cacheIdentity.attemptId && it.eventName == "EMBEDDING_CACHE_HIT"
                }
                check(hits >= hitIndex + 1) { "CACHE_HIT_NOT_RECORDED iteration=${hitIndex + 1}" }
                repeatHits += JSONObject()
                    .put("iteration", hitIndex + 1)
                    .put("attempt_id", cacheIdentity.attemptId)
                    .put("photo_id", selected.imageId)
                    .put("photo_sha256", selected.photoSha256)
                    .put("start_uptime_ns", started)
                    .put("end_uptime_ns", ended)
                    .put("duration_ms", (ended - started) / 1_000_000.0)
                    .put("cache_status", "HIT")
                writeBaselineArtifacts(
                    root = evidenceRoot,
                    rows = rows.toList(),
                    cacheHits = repeatHits.toList(),
                    selected = selected,
                    assetSha = assetSha,
                    normalizedPhotoReadyNs = normalizedPhotoReadyNs,
                    decodeNormalizeStartedNs = decodeNormalizeStartedNs,
                    runtimeReadyEvent = runtimeReadyEvent,
                    runtime = runtime,
                    buildSha = InstrumentationRegistry.getArguments().getString("source_sha") ?: "NOT_PROVIDED",
                    failure = failure.get(),
                )
            }
            assertEquals("baseline must contain 10 independent real Encoder misses", 10,
                rows.count { it.optString("cache_status") == "MISS" })
            assertEquals("all 10 full recognitions must produce trusted masks and closed contours", 10,
                rows.count { it.optString("result") == "PASS" && it.optBoolean("mask_trusted") && it.optBoolean("contour_closed") })
            assertEquals("five same-photo Embedding cache hits must be measured", 5, repeatHits.size)
        } catch (t: Throwable) {
            failure.set("${t.javaClass.simpleName}:${t.message.orEmpty()}")
            throw t
        } finally {
            try {
                writeBaselineArtifacts(
                    root = evidenceRoot,
                    rows = rows.toList(),
                    cacheHits = repeatHits.toList(),
                    selected = selected,
                    assetSha = assetSha,
                    normalizedPhotoReadyNs = normalizedPhotoReadyNs,
                    decodeNormalizeStartedNs = decodeNormalizeStartedNs,
                    runtimeReadyEvent = runtimeReadyEvent,
                    runtime = runtime,
                    buildSha = InstrumentationRegistry.getArguments().getString("source_sha") ?: "NOT_PROVIDED",
                    failure = failure.get(),
                )
            } finally {
                try {
                    pipeline.close()
                } finally {
                    File(selected.filePath).delete()
                    if (!selected.bitmap.isRecycled) selected.bitmap.recycle()
                }
            }
        }
    }

    private fun buildRunRow(
        index: Int,
        identity: RecognitionModelIdentity,
        selected: com.yujian.ai.model.SelectedImage,
        measurementStartNs: Long,
        rowStartedNs: Long,
        pipelineDurationMs: Double?,
        recognitionStatus: String,
        maskStatus: String,
        maskQuality: String,
        maskReadyNs: Long?,
        contourClosedNs: Long?,
        contourExtractMs: Double?,
        maskPngDecodeMs: Double?,
        contourCount: Int,
        contourClosed: Boolean,
        maskSha256: String?,
        runtimeEvents: List<SlimSamRuntimeEvent>,
        pipelineEvents: List<JSONObject>,
        cpuMs: Long,
        gcCount: Int,
        allocationCount: Int,
        memoryBefore: JSONObject,
        memoryAfter: JSONObject,
        result: String,
        error: String?,
    ): JSONObject {
        fun eventDuration(vararg names: String): Double? = runtimeEvents
            .firstOrNull { it.eventName in names && it.result == "PASS" }
            ?.durationMs
        fun pipelineDuration(event: String): Double? = pipelineEvents
            .firstOrNull { it.optString("event") == event }
            ?.optJSONObject("details")?.optDouble("duration_ms")
            ?.takeIf { it.isFinite() }
        val encoder = runtimeEvents.firstOrNull { it.eventName == "VISION_ENCODER_END" && it.result == "PASS" }
        val encoderStart = runtimeEvents.firstOrNull { it.eventName == "VISION_ENCODER_START" }?.startUptimeNs
        val decoder = runtimeEvents.firstOrNull { it.eventName == "MASK_DECODER_END" && it.result == "PASS" }
        val detectorStart = pipelineEvents.firstOrNull { it.optString("event") == "DETECTOR_START" }?.optLong("uptime_ns")
        val detectorEnd = pipelineEvents.firstOrNull { it.optString("event") == "DETECTOR_END" }?.optLong("uptime_ns")
        val classifierStart = pipelineEvents.firstOrNull { it.optString("event") == "CLASSIFIER_START" }?.optLong("uptime_ns")
        val classifierEnd = pipelineEvents.firstOrNull { it.optString("event") == "CLASSIFIER_END" }?.optLong("uptime_ns")
        return JSONObject()
            .put("iteration", index)
            .put("session_id", identity.sessionId)
            .put("attempt_id", identity.attemptId)
            .put("photo_id", selected.imageId)
            .put("source_bytes_sha256", identity.photoIdentity?.sourceBytesSha256)
            .put("canonical_pixel_sha256", selected.photoSha256)
            .put("cache_status", if (runtimeEvents.any { it.eventName == "EMBEDDING_CACHE_MISS" }) "MISS" else "MISS_NOT_CONFIRMED")
            .put("measurement_start_uptime_ns", measurementStartNs)
            .put("measurement_start_event", "INSTRUMENTATION_PRODUCTION_ATTEMPT_START")
            .put("run_start_uptime_ns", rowStartedNs)
            .put("detector_duration_ms", pipelineDuration("DETECTOR_END") ?: JSONObject.NULL)
            .put("classifier_duration_ms", pipelineDuration("CLASSIFIER_END") ?: JSONObject.NULL)
            .put("recognition_duration_ms", pipelineDurationMs ?: JSONObject.NULL)
            .put("sam_preprocess_ms", eventDuration("PHOTO_PREPROCESS_END") ?: JSONObject.NULL)
            .put("tensor_create_ms", eventDuration("ONNX_INPUT_TENSOR_CREATE_END") ?: JSONObject.NULL)
            .put("input_tensor_sha_ms", eventDuration("INPUT_TENSOR_SHA256_END") ?: JSONObject.NULL)
            .put("encoder_native_ms", encoder?.durationMs ?: JSONObject.NULL)
            .put("output_validate_sha_ms", eventDuration("OUTPUT_VALIDATE_AND_SHA256_END") ?: JSONObject.NULL)
            .put("embedding_copy_ms", eventDuration("EMBEDDING_COPY_END") ?: JSONObject.NULL)
            .put("decoder_native_ms", decoder?.durationMs ?: JSONObject.NULL)
            .put("mask_postprocess_ms", eventDuration("MASK_POSTPROCESS_END") ?: JSONObject.NULL)
            .put("mask_png_write_ms", eventDuration("MASK_PNG_WRITE_END") ?: JSONObject.NULL)
            .put("mask_sha_verify_ms", eventDuration("MASK_SHA_VERIFY_END") ?: JSONObject.NULL)
            .put("contour_extract_ms", contourExtractMs ?: JSONObject.NULL)
            .put("mask_png_decode_ms", maskPngDecodeMs ?: JSONObject.NULL)
            .put("detector_encoder_overlap_ms", overlapMs(encoderStart, encoder?.endUptimeNs, detectorStart, detectorEnd))
            .put("classifier_encoder_overlap_ms", overlapMs(encoderStart, encoder?.endUptimeNs, classifierStart, classifierEnd))
            .put("mask_ready_uptime_ns", maskReadyNs ?: JSONObject.NULL)
            .put("mask_ready_ms_from_attempt_start", maskReadyNs?.let { (it - measurementStartNs) / 1_000_000.0 } ?: JSONObject.NULL)
            .put("contour_closed_uptime_ns", contourClosedNs ?: JSONObject.NULL)
            .put("contour_closed_ms_from_attempt_start", contourClosedNs?.let { (it - measurementStartNs) / 1_000_000.0 } ?: JSONObject.NULL)
            .put("mask_trusted", maskStatus == "READY" && maskQuality == FishSubjectQuality.GOOD.name)
            .put("mask_status", maskStatus)
            .put("mask_quality", maskQuality)
            .put("mask_sha256", maskSha256 ?: JSONObject.NULL)
            .put("contour_segments", contourCount)
            .put("contour_closed", contourClosed)
            .put("result_handoff", "NOT_CAPTURED_BY_PERFORMANCE_LOOP")
            .put("result_action_ready", "NOT_CAPTURED_BY_PERFORMANCE_LOOP")
            .put("process_cpu_ms", cpuMs)
            .put("gc_count_delta", gcCount)
            .put("allocation_count_delta", allocationCount)
            .put("memory_before", memoryBefore)
            .put("memory_after", memoryAfter)
            .put("process_tid_at_record", Process.myTid())
            .put("recognition_status", recognitionStatus)
            .put("result", result)
            .put("error", error ?: JSONObject.NULL)
            .put("runtime_events", JSONArray().apply { runtimeEvents.forEach { put(eventJson(it)) } })
            .put("pipeline_events", JSONArray().apply { pipelineEvents.forEach { put(it) } })
    }

    private fun writeBaselineArtifacts(
        root: File,
        rows: List<JSONObject>,
        cacheHits: List<JSONObject>,
        selected: com.yujian.ai.model.SelectedImage,
        assetSha: String,
        normalizedPhotoReadyNs: Long,
        decodeNormalizeStartedNs: Long,
        runtimeReadyEvent: SlimSamRuntimeEvent?,
        runtime: SlimSamRuntimeManager,
        buildSha: String,
        failure: String?,
    ) {
        val environment = JSONObject()
            .put("api_level", android.os.Build.VERSION.SDK_INT)
            .put("fingerprint", android.os.Build.FINGERPRINT)
            .put("hardware", android.os.Build.HARDWARE)
            .put("available_processors", Runtime.getRuntime().availableProcessors())
            .put("java_heap_max_bytes", Runtime.getRuntime().maxMemory())
            .put("ort_version", SlimSamSegmenter.ORT_VERSION)
            .put("intra_op_threads", runtime.state.value.intraOpThreads ?: JSONObject.NULL)
            .put("vision_model_sha256", runtime.state.value.visionModelSha256 ?: JSONObject.NULL)
            .put("decoder_model_sha256", runtime.state.value.decoderModelSha256 ?: JSONObject.NULL)
            .put("build_sha", buildSha)
            .put("production_source_baseline_sha", "16464298cd7ab90737f6b636b2377a8c53a66794")
        val coldStartSummary = File(root, "cold_start_summary.json")
            .takeIf { it.isFile }
            ?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
        val coldStartsCaptured = coldStartSummary?.optString("status") == "THREE_COLD_STARTS_CAPTURED" &&
            coldStartSummary?.optInt("sample_count") == 3
        assertTrue("three force-stopped process cold-start measurements are required", coldStartsCaptured)
        val summary = JSONObject()
            .put("schema_version", 1)
            .put("status", if (failure == null && rows.size == 10 && rows.all { it.optString("result") == "PASS" } && cacheHits.size == 5 && coldStartsCaptured) "BASELINE_CAPTURED" else "BASELINE_INCOMPLETE")
            .put("measurement_mode", "PRODUCTION_PIPELINE_AND_RUNTIME_WITH_PRELOADED_SESSION_NO_UI_HANDOFF")
            .put("photo", JSONObject()
                .put("asset", "Fish-Mask-Gold-Contour-Lab-Demo/preset_fish.jpg")
                .put("raw_sha256", assetSha)
                .put("photo_id", selected.imageId)
                .put("canonical_pixel_sha256", selected.photoSha256)
                .put("source_bytes_sha256", selected.photoIdentity?.sourceBytesSha256)
                .put("width", selected.bitmap.width)
                .put("height", selected.bitmap.height)
                .put("raw_exif_orientation", selected.sourceExifOrientationRaw)
                .put("normalized_exif_orientation", selected.sourceExifOrientation)
                .put("normalized_test_photo_ready_uptime_ns", normalizedPhotoReadyNs)
                .put("normalized_photo_ready_source", "NORMALIZED_TEST_BITMAP_READY; UI_PHOTO_STABLE_EVENT_NOT_CAPTURED")
                .put("decode_and_exif_normalize_ms", (normalizedPhotoReadyNs - decodeNormalizeStartedNs) / 1_000_000.0))
            .put("environment", environment)
            .put("runtime_ready_uptime_ns", runtimeReadyEvent?.endUptimeNs ?: JSONObject.NULL)
            .put("embedding_cache_miss_count", rows.count { it.optString("cache_status") == "MISS" })
            .put("full_recognition_mask_runs", rows.size)
            .put("trusted_mask_count", rows.count { it.optBoolean("mask_trusted") })
            .put("closed_contour_count", rows.count { it.optBoolean("contour_closed") })
            .put("mask_under_5s_count_from_attempt_start", rows.count { (it.optDouble("mask_ready_ms_from_attempt_start", Double.POSITIVE_INFINITY)) <= 5_000.0 })
            .put("closed_contour_under_5s_count_from_attempt_start", rows.count { (it.optDouble("contour_closed_ms_from_attempt_start", Double.POSITIVE_INFINITY)) <= 5_000.0 })
            .put("result_action_ready_under_6s_count", JSONObject.NULL)
            .put("result_action_ready_status", "NOT_CAPTURED_BY_PERFORMANCE_LOOP")
            .put("cache_hit_count", cacheHits.size)
            .put("cold_start_session_preload_samples", coldStartSummary ?: "NOT_CAPTURED")
            .put("cache_hits", JSONArray().apply { cacheHits.forEach { put(it) } })
            .put("runs", JSONArray().apply { rows.forEach { put(it) } })
            .put("failure", failure ?: JSONObject.NULL)
        val jsonFile = File(root, "api36_baseline.json")
        jsonFile.writeText(summary.toString(2) + "\n")
        File(root, "api36_baseline.csv").writeText(toCsv(rows) + "\n")
        val maskTimes = rows.mapNotNull { it.optDouble("mask_ready_ms_from_attempt_start", Double.NaN).takeIf(Double::isFinite) }
        val contourTimes = rows.mapNotNull { it.optDouble("contour_closed_ms_from_attempt_start", Double.NaN).takeIf(Double::isFinite) }
        val markdown = buildString {
            appendLine("# API36 Production Performance Baseline")
            appendLine()
            appendLine("- Status: **${summary.getString("status")}**")
            appendLine("- Production source baseline: `${environment.getString("production_source_baseline_sha")}`")
            appendLine("- Instrumentation build: `${buildSha}`")
            appendLine("- API / device: ${environment.getInt("api_level")} / `${environment.getString("fingerprint")}`")
            appendLine("- ORT: ${environment.getString("ort_version")}; encoder threads: ${environment.opt("intra_op_threads")}")
            appendLine("- Model SHA256: encoder `${environment.opt("vision_model_sha256")}`, decoder `${environment.opt("decoder_model_sha256")}`")
            appendLine("- Photo: `preset_fish.jpg`, raw SHA256 `${assetSha}`, canonical pixel SHA256 `${selected.photoSha256}`, ${selected.bitmap.width}×${selected.bitmap.height}, EXIF ${selected.sourceExifOrientationRaw} → ${selected.sourceExifOrientation}")
            appendLine("- Runtime state at sample time: ${runtime.state.value.state}; RUNTIME_READY at `${runtimeReadyEvent?.endUptimeNs ?: "NOT_CAPTURED"}` ns")
            appendLine("- Encoder cache MISS: ${rows.count { it.optString("cache_status") == "MISS" }}/10; full recognition + mask: ${rows.count { it.optString("result") == "PASS" }}/10; trusted masks: ${summary.getInt("trusted_mask_count")}/10; closed contours: ${summary.getInt("closed_contour_count")}/10")
            appendLine("- Same-photo cache HIT: ${cacheHits.size}/5")
            appendLine("- From each direct production attempt start (UI PHOTO_STABLE not captured): mask under 5 s: ${summary.getInt("mask_under_5s_count_from_attempt_start")}/10; closed contour under 5 s: ${summary.getInt("closed_contour_under_5s_count_from_attempt_start")}/10")
            appendLine("- Result action readiness: NOT_CAPTURED in this non-UI performance loop; separate production UI smoke remains required.")
            appendLine("- Three cold-start/session-preload samples: ${coldStartSummary?.optString("status") ?: "NOT_CAPTURED"}.")
            appendLine("- Mask latency median/max: ${formatStats(maskTimes)}")
            appendLine("- Closed contour latency median/max: ${formatStats(contourTimes)}")
            appendLine("- PHOTO_DECODE + EXIF_NORMALIZE: ${(normalizedPhotoReadyNs - decodeNormalizeStartedNs) / 1_000_000.0} ms combined (not split by the existing production API).")
            appendLine()
            appendLine("| Run | Cache | Detector | Classifier | SAM preprocess | Tensor | Input SHA | Encoder | Output validate/SHA | Embedding copy | Decoder | Mask postprocess | Mask ready | Closed contour | Process CPU | GC Δ | RSS peak | Result |")
            appendLine("|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|")
            rows.forEach { row ->
                appendLine("| ${row.optInt("iteration")} | ${row.optString("cache_status")} | ${cell(row, "detector_duration_ms")} | ${cell(row, "classifier_duration_ms")} | ${cell(row, "sam_preprocess_ms")} | ${cell(row, "tensor_create_ms")} | ${cell(row, "input_tensor_sha_ms")} | ${cell(row, "encoder_native_ms")} | ${cell(row, "output_validate_sha_ms")} | ${cell(row, "embedding_copy_ms")} | ${cell(row, "decoder_native_ms")} | ${cell(row, "mask_postprocess_ms")} | ${cell(row, "mask_ready_ms_from_attempt_start")} | ${cell(row, "contour_closed_ms_from_attempt_start")} | ${cell(row, "process_cpu_ms")} | ${cell(row, "gc_count_delta")} | ${cellMemory(row, "rss_peak_kb")} | ${row.optString("result")} ${row.optString("error").takeUnless { it == "null" }.orEmpty()} |")
            }
        }
        File(root, "api36_baseline.md").writeText(markdown)
    }

    private fun memorySample(label: String, runtime: SlimSamRuntimeManager): JSONObject {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        val status = runCatching { File("/proc/self/status").readLines() }.getOrDefault(emptyList())
        fun value(name: String): Long? = status.firstOrNull { it.startsWith("$name:") }
            ?.substringAfter(':')?.trim()?.substringBefore(' ')?.toLongOrNull()
        return JSONObject()
            .put("label", label)
            .put("uptime_ns", SystemClock.elapsedRealtimeNanos())
            .put("native_heap_allocated_bytes", Debug.getNativeHeapAllocatedSize())
            .put("native_heap_size_bytes", Debug.getNativeHeapSize())
            .put("total_pss_kb", info.totalPss)
            .put("native_pss_kb", info.nativePss)
            .put("dalvik_pss_kb", info.dalvikPss)
            .put("rss_kb", value("VmRSS") ?: JSONObject.NULL)
            .put("rss_peak_kb", value("VmHWM") ?: JSONObject.NULL)
            .put("swap_kb", value("VmSwap") ?: JSONObject.NULL)
            .put("java_heap_used_bytes", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())
            .put("gc_count", Debug.getGlobalGcInvocationCount())
            .put("allocation_count", Debug.getGlobalAllocCount())
            .put("active_operations", runtime.metricsSnapshot().activeOperationCount)
            .put("cached_photo_count", runtime.metricsSnapshot().cachedPhotoCount)
    }

    private fun eventJson(event: SlimSamRuntimeEvent) = JSONObject()
        .put("session_id", event.recognitionSessionId)
        .put("attempt_id", event.recognitionAttemptId)
        .put("photo_id", event.imageId)
        .put("photo_sha256", event.photoSha256)
        .put("model_sha256", event.modelSha256 ?: JSONObject.NULL)
        .put("stage", event.stageName)
        .put("event", event.eventName)
        .put("start_uptime_ns", event.startUptimeNs)
        .put("end_uptime_ns", event.endUptimeNs)
        .put("duration_ms", event.durationMs)
        .put("thread_name", event.threadName)
        .put("result", event.result)
        .put("error_code", event.errorCode)
        .put("ort_version", event.ortVersion)
        .put("tensor_metadata", event.tensorMetadata ?: JSONObject.NULL)
        .put("rss_kb", event.androidProcessRssKb ?: JSONObject.NULL)
        .put("pss_kb", event.androidProcessPssKb ?: JSONObject.NULL)
        .put("native_heap_allocated_bytes", event.androidNativeHeapAllocatedBytes ?: JSONObject.NULL)

    private fun overlapMs(aStart: Long?, aEnd: Long?, bStart: Long?, bEnd: Long?): Double {
        if (aStart == null || aEnd == null || bStart == null || bEnd == null) return 0.0
        return (minOf(aEnd, bEnd) - maxOf(aStart, bStart)).coerceAtLeast(0L) / 1_000_000.0
    }

    private fun toCsv(rows: List<JSONObject>): String {
        val columns = listOf(
            "iteration", "attempt_id", "cache_status", "recognition_status", "recognition_duration_ms",
            "detector_duration_ms", "classifier_duration_ms", "sam_preprocess_ms", "tensor_create_ms",
            "input_tensor_sha_ms", "encoder_native_ms", "output_validate_sha_ms", "embedding_copy_ms",
            "decoder_native_ms", "mask_postprocess_ms", "mask_png_write_ms", "mask_sha_verify_ms",
            "mask_ready_ms_from_attempt_start", "contour_closed_ms_from_attempt_start", "mask_png_decode_ms", "contour_extract_ms", "mask_trusted",
            "contour_closed", "process_cpu_ms", "gc_count_delta", "allocation_count_delta", "mask_sha256", "result", "error",
        )
        return buildString {
            appendLine(columns.joinToString(","))
            rows.forEach { row ->
                appendLine(columns.joinToString(",") { column -> csv(row.opt(column).toString()) })
            }
        }.trimEnd()
    }

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""
    private fun cell(row: JSONObject, key: String): String = row.opt(key).let { if (it == null || it == JSONObject.NULL) "NA" else it.toString() }
    private fun cellMemory(row: JSONObject, key: String): String = row.optJSONObject("memory_after")?.opt(key)?.toString() ?: "NA"
    private fun formatStats(values: List<Double>): String {
        if (values.isEmpty()) return "NOT_CAPTURED"
        val sorted = values.sorted()
        return "median=${sorted[sorted.size / 2]}ms max=${sorted.last()}ms"
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this).joinToString("") { "%02x".format(it) }

    private companion object {
        const val PRESET_FISH_SHA256 = "4f0bda4d423239352ccc6f453ec7c02349203929e9ffde15cb547c6572bb7933"
    }
}
