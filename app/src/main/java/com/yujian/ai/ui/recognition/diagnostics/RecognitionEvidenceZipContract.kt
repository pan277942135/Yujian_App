package com.yujian.ai.ui.recognition.diagnostics

/** Minimum durable entries every schema-v2 archive must contain, including explicit missing-state files. */
object RecognitionEvidenceZipContract {
    val requiredEntries: Set<String> = setOf(
        "manifest.json",
        "session/session.json",
        "session/session_identity.json",
        "session/session_summary.json",
        "session/stage_timeline.json",
        "attempt.json",
        "frames/frame_index.json",
        "frames/pixelcopy_manifest.jsonl",
        "logs/recognition_events.jsonl",
        "logs/stage_timeline.jsonl",
        "logs/model_inference.jsonl",
        "logs/mask_runtime.jsonl",
        "logs/waterlight_paths.jsonl",
        "logs/result_handoff.jsonl",
        "logs/gpu_trace.jsonl",
        "logs/gl_trace.jsonl",
        "logs/surface_trace.jsonl",
        "reports/model_summary.json",
        "reports/mask_summary.json",
        "reports/geometry_summary.json",
        "reports/path_summary.json",
        "reports/result_evidence_status.json",
        "crash_report.json",
        "recovery_report.json",
        "device.json",
        "build.json",
        "source.json",
        "resource_manifest.json",
    )

    fun missingEntries(entries: Set<String>): Set<String> = requiredEntries - entries
}
