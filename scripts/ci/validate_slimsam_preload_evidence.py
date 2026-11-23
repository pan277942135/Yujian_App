#!/usr/bin/env python3
"""Fail closed on missing or incomplete real SlimSAM startup/runtime evidence."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


VISION_SHA256 = "cce23c7b2e5d4f330932738fb67ba518e04b0d99ccdd1cccd22a7da4e01f2971"
DECODER_SHA256 = "cb90b279f549d2cab7fd6e20c38522438c65d84bdcca3d2a764cff7d857fdce2"
REQUIRED_FILES = (
    "model_runtime_state.json",
    "slimsam_timing.json",
    "recognition_stage_timing.json",
    "memory_profile.json",
    "slimsam_execution_trace.csv",
)
INITIALIZATION_EVENTS = (
    "APP_CREATE_START",
    "APP_CREATE_END",
    "APP_FIRST_FRAME",
    "MODEL_VERIFY_START",
    "MODEL_VERIFY_END",
    "ORT_INITIALIZATION_START",
    "ORT_INITIALIZATION_END",
    "VISION_SESSION_CREATE_START",
    "VISION_SESSION_CREATE_END",
    "DECODER_SESSION_CREATE_START",
    "DECODER_SESSION_CREATE_END",
    "SLIMSAM_RUNTIME_READY",
)


def _object(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{path.name} must be a JSON object")
    return value


def validate(evidence_dir: Path) -> dict[str, Any]:
    absent = [name for name in REQUIRED_FILES if not (evidence_dir / name).is_file()]
    if absent:
        return {"passed": False, "reason": "REQUIRED_SLIMSAM_EVIDENCE_MISSING", "missing_files": absent}
    try:
        runtime = _object(evidence_dir / "model_runtime_state.json")
        timing = _object(evidence_dir / "slimsam_timing.json")
        stages = _object(evidence_dir / "recognition_stage_timing.json")
        memory = _object(evidence_dir / "memory_profile.json")
    except (OSError, json.JSONDecodeError, ValueError) as error:
        return {"passed": False, "reason": "SLIMSAM_EVIDENCE_JSON_INVALID", "detail": str(error)}

    events = runtime.get("events")
    if not isinstance(events, list) or not all(isinstance(event, dict) for event in events):
        return {"passed": False, "reason": "RUNTIME_EVENT_TRACE_MISSING"}
    names = [event.get("eventName") for event in events]
    for name in INITIALIZATION_EVENTS:
        if name not in names:
            return {"passed": False, "reason": f"STARTUP_EVENT_MISSING:{name}"}
    if runtime.get("status") != "READY" or runtime.get("visionSessionReady") is not True or runtime.get("decoderSessionReady") is not True:
        return {"passed": False, "reason": "BOTH_ONNX_SESSIONS_NOT_READY"}
    if runtime.get("visionModelSha256") != VISION_SHA256 or runtime.get("decoderModelSha256") != DECODER_SHA256:
        return {"passed": False, "reason": "MODEL_SHA256_CONTRACT_MISMATCH"}
    if runtime.get("visionSessionCount") != 1 or runtime.get("decoderSessionCount") != 1:
        return {"passed": False, "reason": "SESSION_COUNT_NOT_EXACTLY_ONE"}
    if runtime.get("failure") not in (None, ""):
        return {"passed": False, "reason": "INSTRUMENTATION_RECORDED_FAILURE", "detail": runtime.get("failure")}

    startup = timing.get("startup_timing")
    if not isinstance(startup, dict) or startup.get("preload_completed") is not True:
        return {"passed": False, "reason": "ASYNC_PRELOAD_COMPLETION_NOT_RECORDED"}
    first_frame_ms = startup.get("app_first_frame_duration_ms")
    if not isinstance(first_frame_ms, (int, float)) or isinstance(first_frame_ms, bool) or first_frame_ms <= 0:
        return {"passed": False, "reason": "APP_FIRST_FRAME_TIMING_MISSING"}
    repeats = timing.get("samePhotoRepeatTimingsMs")
    if not isinstance(repeats, list) or len(repeats) != 5 or not all(isinstance(value, (int, float)) for value in repeats):
        return {"passed": False, "reason": "FIVE_SAME_PHOTO_CACHE_REPEATS_MISSING"}
    if timing.get("samePhotoCacheHitCount", 0) < 5:
        return {"passed": False, "reason": "SAME_PHOTO_EMBEDDING_CACHE_HITS_INSUFFICIENT"}
    if timing.get("visionEncoderExecutionCount", 0) < 1 or timing.get("decoderExecutionCount", 0) < 1:
        return {"passed": False, "reason": "REAL_ENCODER_OR_DECODER_EXECUTION_MISSING"}

    detector_overlap = stages.get("encoderDetectorOverlapMs")
    classifier_overlap = stages.get("encoderClassifierOverlapMs")
    if not isinstance(detector_overlap, (int, float)) or detector_overlap <= 0:
        return {"passed": False, "reason": "ENCODER_DETECTOR_OVERLAP_NOT_OBSERVED"}
    if not isinstance(classifier_overlap, (int, float)) or classifier_overlap <= 0:
        return {"passed": False, "reason": "ENCODER_CLASSIFIER_OVERLAP_NOT_OBSERVED"}

    workload = timing.get("distinctPhotoWorkload")
    if not isinstance(workload, list) or len(workload) != 5:
        return {"passed": False, "reason": "FIVE_DISTINCT_PHOTO_WORKLOAD_MISSING"}
    photo_hashes = [row.get("photo_sha256") for row in workload if isinstance(row, dict)]
    if len(photo_hashes) != 5 or len(set(photo_hashes)) != 5:
        return {"passed": False, "reason": "DISTINCT_PHOTO_IDENTITIES_DUPLICATED_OR_MISSING"}
    for index, row in enumerate(workload, start=1):
        if row.get("recognition_ready") is not True:
            return {"passed": False, "reason": f"PRODUCTION_RECOGNITION_NOT_READY_FOR_PHOTO_{index}"}
        if row.get("vision_encoder_count") != 1 or row.get("mask_decoder_count") != 1:
            return {"passed": False, "reason": f"PHOTO_{index}_REAL_ENCODER_OR_DECODER_COUNT_INVALID"}
        if row.get("vision_session_count") != 1 or row.get("decoder_session_count") != 1 or row.get("cached_photo_count") != 1:
            return {"passed": False, "reason": f"PHOTO_{index}_SESSION_OR_CACHE_LIFECYCLE_INVALID"}
        if row.get("encoder_detector_overlap_ms", 0) <= 0 or row.get("encoder_classifier_overlap_ms", 0) <= 0:
            return {"passed": False, "reason": f"PHOTO_{index}_THREE_WAY_OVERLAP_NOT_OBSERVED"}

    cancellation = timing.get("cancelledPhotoReplacement")
    if not isinstance(cancellation, dict) or cancellation.get("cancellation_requested_after_encoder_start") is not True:
        return {"passed": False, "reason": "IN_FLIGHT_PHOTO_CANCELLATION_NOT_EXERCISED"}
    if cancellation.get("cancelled_task_observed") is not True or cancellation.get("replacement_embedding_ready") is not True:
        return {"passed": False, "reason": "CANCELLED_PHOTO_REPLACEMENT_NOT_ISOLATED"}
    if cancellation.get("active_operation_count_after_replace") != 0 or cancellation.get("cached_photo_count_after_replace") != 1:
        return {"passed": False, "reason": "CANCELLED_PHOTO_LEFT_STALE_RUNTIME_STATE"}

    samples = memory.get("samples")
    if not isinstance(samples, list) or len(samples) < 5:
        return {"passed": False, "reason": "MEMORY_PROFILE_SAMPLES_INSUFFICIENT"}
    required_metrics = (
        "java_heap_used_bytes", "native_heap_allocated_bytes", "total_pss_kb",
        "embedding_buffer_bytes", "vision_session_count", "decoder_session_count",
        "cached_photo_count", "active_operation_count",
    )
    if any(not isinstance(sample, dict) or any(metric not in sample for metric in required_metrics) for sample in samples):
        return {"passed": False, "reason": "MEMORY_PROFILE_METRIC_MISSING"}
    growth = memory.get("five_photo_growth_delta")
    if not isinstance(growth, dict) or growth.get("sample_count") != 5:
        return {"passed": False, "reason": "FIVE_PHOTO_MEMORY_GROWTH_NOT_MEASURED"}

    return {
        "passed": True,
        "reason": "NONE",
        "startup": {
            "app_first_frame_ms": first_frame_ms,
            "runtime_ready_timestamp_ns": startup.get("runtime_ready_uptime_ns"),
        },
        "session_counts": {"vision": 1, "decoder": 1},
        "same_photo_cache_hits": timing["samePhotoCacheHitCount"],
        "same_photo_repeat_count": len(repeats),
        "distinct_photo_count": len(photo_hashes),
        "encoder_detector_overlap_ms": detector_overlap,
        "encoder_classifier_overlap_ms": classifier_overlap,
        "five_photo_memory_growth": growth,
        "cancellation_replacement": cancellation,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence-dir", required=True, type=Path)
    args = parser.parse_args()
    result = validate(args.evidence_dir)
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result.get("passed") is True else 1


if __name__ == "__main__":
    sys.exit(main())
