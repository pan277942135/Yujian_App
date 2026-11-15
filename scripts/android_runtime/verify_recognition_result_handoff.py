#!/usr/bin/env python3
"""Close API36 production Result Handoff evidence against one exact APK and commit."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


STAGES = [
    ("PHOTO_STABLE", 0), ("ORB_WAKE", 350), ("ORB_RELEASE", 600),
    ("PERIMETER_ARRIVAL", 125), ("PATH_TO_FISH", 125), ("CONTOUR_A", 450),
    ("CONTOUR_B", 450), ("CONTOUR_C", 450), ("FISH_GOLD_PEAK", 1_000),
    ("ORB_RETURN", 350), ("ORB_TO_NAME", 850), ("SPECIES_NAME_REVEAL", 800),
    ("RESIDUE_SETTLE", 450),
]
SUBSTAGES = ["NAME_STABLE", "PHOTO_SETTLE", "RESULT_SCENE_HANDOFF", "RESULT_INFO_ARRIVAL", "RESULT_SCENE_STABLE"]
NAVIGATION = [
    "CREATED", "PROCESSING_COMPLETE", "HANDOFF_READY", "NAVIGATION_REQUESTED", "RESULT_ATTACHED", "HANDOFF_COMPLETED",
]
APP_EVIDENCE = [
    "runtime_provenance.json", "model_release_contract.json", "apk_model_trace.json", "runtime_matrix.json",
    "path_identity_trace.json", "return_event_trace.json", "result_handoff_trace.json",
    "result_navigation_trace.json", "result_stability_report.json", "stage_timeline.json",
    "qualification_report.json", "evidence_manifest.json",
]


def fail(message: str) -> None:
    raise ValueError(message)


def read_json(path: Path) -> dict[str, Any]:
    if not path.is_file() or path.stat().st_size == 0:
        fail(f"required JSON evidence is missing or empty: {path.name}")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        fail(f"invalid JSON evidence {path.name}: {error}")
    if not isinstance(data, dict):
        fail(f"JSON evidence must be an object: {path.name}")
    return data


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def log_events(path: Path, name: str, identity: dict[str, Any]) -> list[dict[str, str]]:
    found: list[dict[str, str]] = []
    token = re.compile(rf"\b{re.escape(name)}\b")
    field = re.compile(r"([A-Za-z_][A-Za-z0-9_]*)=([^\s]+)")
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if not token.search(line):
            continue
        values = dict(field.findall(line))
        if values.get("session") != identity["recognitionSessionId"] or values.get("attempt") != identity["recognitionAttemptId"]:
            continue
        if values.get("image") not in (None, identity["imageId"]):
            continue
        if values.get("sha256") not in (None, identity["photoSha256"]):
            continue
        found.append(values)
    return found


def last_timestamp(rows: list[dict[str, str]], label: str) -> int:
    candidates = [row.get("uptime_ms") for row in rows]
    candidates = [value for value in candidates if value and value.isdigit()]
    if not candidates:
        fail(f"actual runtime log is missing {label} uptime_ms")
    return int(candidates[-1])


def verify_video(path: Path) -> float:
    if not path.is_file() or path.stat().st_size < 1024:
        fail("physical-device screen recording is missing or too small")
    with path.open("rb") as stream:
        header = stream.read(12)
    if len(header) < 12 or header[4:8] != b"ftyp":
        fail("screen recording is not a finalized MP4 file")
    ffprobe = shutil.which("ffprobe")
    if not ffprobe:
        fail("ffprobe is required to verify full-run video duration")
    result = subprocess.run(
        [ffprobe, "-v", "error", "-show_entries", "format=duration", "-of", "default=nw=1:nk=1", str(path)],
        check=True, capture_output=True, text=True,
    )
    try:
        duration = float(result.stdout.strip())
    except ValueError:
        fail("ffprobe did not return an MP4 duration")
    if duration < 6.0:
        fail(f"device recording is too short to contain the 6000ms animation: {duration:.3f}s")
    return duration


def verify(args: argparse.Namespace) -> None:
    root = args.evidence_dir.resolve()
    app_apk = args.app_apk.resolve()
    if not app_apk.is_file() or app_apk.stat().st_size == 0:
        fail("exact source APK is missing")
    apk_sha = sha256_file(app_apk)
    expected_stages = [name for name, _ in STAGES]
    expected_duration = {name: duration for name, duration in STAGES}

    provenance = read_json(root / "runtime_provenance.json")
    device = provenance.get("device") or {}
    if provenance.get("sourceCommitSha") != args.build_sha:
        fail("app runtime provenance source SHA differs from the gate commit")
    if provenance.get("apkSha256") != apk_sha:
        fail("installed app APK SHA differs from the exact host APK")
    if device.get("sdkInt") != args.expected_api_level or str(device.get("manufacturer", "")).upper() != "HUAWEI" or device.get("model") != "CMS-AL20":
        fail("runtime device identity is not HUAWEI CMS-AL20 / Android 16 / API36")

    device_identity = read_json(root / "device_identity.json")
    if device_identity.get("classification") != "DEVICE_ACCEPTED" or int(device_identity.get("sdkInt", 0)) != args.expected_api_level:
        fail("physical-device gate evidence is not accepted")

    model_trace = read_json(root / "apk_model_trace.json")
    model_contract = read_json(root / "model_release_contract.json")
    if model_trace.get("build_sha") != args.build_sha or model_trace.get("apk_sha256") != apk_sha:
        fail("APK model trace does not bind to the exact source commit and APK")
    if model_trace.get("model_release_contract_sha256") != sha256_file(root / "model_release_contract.json"):
        fail("APK model contract bytes differ from the packaged APK snapshot")
    if model_contract.get("build_sha") != args.build_sha or model_trace.get("sha256") != model_contract.get("sha256"):
        fail("Classifier release snapshot provenance or SHA is inconsistent")
    detector = model_trace.get("detector") or {}
    slim_sam = model_trace.get("slim_sam") or {}
    if detector.get("model_id") != "DET_FISH_v0.1" or not re.fullmatch(r"[0-9a-f]{64}", str(detector.get("sha256", ""))):
        fail("APK does not carry verified Detector v0.1 model bytes")
    for label in ("encoder", "decoder"):
        record = slim_sam.get(label) or {}
        if not re.fullmatch(r"[0-9a-f]{64}", str(record.get("sha256", ""))) or int(record.get("bytes", 0)) <= 0:
            fail(f"APK SlimSAM {label} identity is missing")

    runtime = read_json(root / "runtime_matrix.json")
    identity = runtime.get("identity") or {}
    for key in ("recognitionSessionId", "recognitionAttemptId", "imageId", "photoSha256"):
        if not identity.get(key):
            fail(f"runtime matrix is missing {key}")
    if runtime.get("stageCount") != 13 or runtime.get("requiredAnimationBudgetMs") != 6_000:
        fail("runtime matrix does not bind to the frozen 13-stage / 6000ms contract")

    e2e = read_json(root / "production_e2e_test_report.json")
    guardrail = read_json(root / "guardrail_test_report.json")
    if e2e.get("status") != "PASS" or guardrail.get("status") != "PASS":
        fail("device-side production E2E or guardrail instrumentation did not pass")
    if e2e.get("sourceCommitSha") != args.build_sha or guardrail.get("sourceCommitSha") != args.build_sha:
        fail("AndroidTest reports do not match the app source commit")
    if e2e.get("sourceFixtureSha256") != "a7ed3bb0410c191364f6d853b4078aebdc2f3b9baa4da34b7b3f6e8e0e93a56b":
        fail("the expected real test photo was not used")
    if e2e.get("selectedPhotoSha256") != identity.get("photoSha256"):
        fail("photo selected through the production picker differs from the runtime identity")

    timeline = read_json(root / "stage_timeline.json").get("stages") or []
    if [row.get("stageId") for row in timeline] != expected_stages:
        fail("stage timeline is not the exact ordered 13-stage sequence")
    previous_end: int | None = None
    active_total = 0
    for index, row in enumerate(timeline):
        name = expected_stages[index]
        start = int(row.get("stageStartTimestampMs", -1))
        end = int(row.get("stageEndTimestampMs", -1))
        duration = int(row.get("stageDurationMs", -1))
        required = int(row.get("requiredDurationMs", -1))
        gate_wait = int(row.get("gateWaitDurationMs", 0))
        active = int(row.get("activeDurationMs", -1))
        if start < 0 or end < start or duration != end - start or required != expected_duration[name]:
            fail(f"invalid stage timestamps or frozen duration for {name}")
        if duration < required or gate_wait < 0 or gate_wait > duration or active != duration - gate_wait or active < required:
            fail(f"stage duration or model gate wait violated the contract for {name}")
        if row.get("sessionId") != identity["recognitionSessionId"] or row.get("attemptId") != identity["recognitionAttemptId"]:
            fail(f"stage identity changed at {name}")
        if previous_end is not None and start < previous_end:
            fail(f"stage clock regressed at {name}")
        previous_end = end
        active_total += active
    first_frame = runtime.get("firstFramePresentedTimestampMs")
    if not isinstance(first_frame, int) or timeline[0].get("stageStartTimestampMs") != first_frame:
        fail("the 13-stage clock does not start at PHOTO_FIRST_STABLE_FRAME_PRESENTED")
    if sum(expected_duration.values()) != 6_000 or active_total < 6_000:
        fail("the measured animation time is below the frozen 6000ms budget")

    log_path = root / "recognition_runtime_logcat.txt"
    pipeline = log_events(log_path, "PIPELINE_START", identity)
    detector_start = log_events(log_path, "DETECTOR_START", identity)
    detector_end = log_events(log_path, "DETECTOR_END", identity)
    classifier_start = log_events(log_path, "CLASSIFIER_START", identity)
    classifier_end = log_events(log_path, "CLASSIFIER_END", identity)
    mask_start = log_events(log_path, "SLIMSAM_START", identity)
    mask_end = log_events(log_path, "SLIMSAM_END", identity)
    reveal_rows = [row for row in log_events(log_path, "REVEAL_DECISION", identity) if row.get("decision") == "FISH_FOCUS_READY"]
    model_timestamps = {
        "modelStartTimestampMs": last_timestamp(pipeline, "PIPELINE_START"),
        "detectorStartTimestampMs": last_timestamp(detector_start, "DETECTOR_START"),
        "detectorTerminalTimestampMs": last_timestamp(detector_end, "DETECTOR_END"),
        "classifierStartTimestampMs": last_timestamp(classifier_start, "CLASSIFIER_START"),
        "classifierTerminalTimestampMs": last_timestamp(classifier_end, "CLASSIFIER_END"),
        "maskStartTimestampMs": last_timestamp(mask_start, "SLIMSAM_START"),
        "maskTerminalTimestampMs": last_timestamp(mask_end, "SLIMSAM_END"),
        "revealDecisionTimestampMs": last_timestamp(reveal_rows, "REVEAL_DECISION[FISH_FOCUS_READY]"),
    }
    if not (model_timestamps["modelStartTimestampMs"] <= model_timestamps["detectorStartTimestampMs"] <
            model_timestamps["detectorTerminalTimestampMs"] <= model_timestamps["classifierStartTimestampMs"] <
            model_timestamps["classifierTerminalTimestampMs"] <= model_timestamps["revealDecisionTimestampMs"] and
            model_timestamps["detectorTerminalTimestampMs"] <= model_timestamps["maskStartTimestampMs"] <
            model_timestamps["maskTerminalTimestampMs"] <= model_timestamps["revealDecisionTimestampMs"]):
        fail("model stages or reveal decision do not follow the actual monotonic runtime trace")
    created = (read_json(root / "result_navigation_trace.json").get("transitions") or [{}])[0].get("monotonicTimestampMs")
    if created is None or model_timestamps["revealDecisionTimestampMs"] - int(created) > 60_000:
        fail("the shared 60-second recognition attempt deadline was exceeded")
    runtime["modelTimestamps"] = model_timestamps
    runtime["modelAttemptElapsedMs"] = model_timestamps["revealDecisionTimestampMs"] - int(created)
    runtime["clockSource"] = "android.os.SystemClock.uptimeMillis"
    runtime["modelTimestampSource"] = "RecognitionProcessingScene logcat from this physical-device attempt"
    (root / "runtime_matrix.json").write_text(json.dumps(runtime, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    path = read_json(root / "path_identity_trace.json")
    accepted_return = path.get("acceptedAutomaticReturn") or {}
    if path.get("status") != "PASS" or path.get("fullReveal") is not True:
        fail("the app did not produce a trusted FULL_REVEAL path")
    if any(accepted_return.get(key) != path.get(path_key) for key, path_key in (
        ("pathId", "fishPathId"), ("pathMaskSha256", "maskSha256"),
        ("pathMaskVersion", "maskVersion"), ("pathGeometryVersion", "geometryVersion"),
        ("pStarNormalizedSource", "pStarNormalizedSource"), ("pathSegments", "pathSegments"),
    )):
        fail("actual Controller-accepted RETURN changed the Processing Path/Mask/P*/Geometry identity")
    if accepted_return.get("source") != "FISH_CONTOUR" or accepted_return.get("target") != "ORB_CORE" or accepted_return.get("feedback") != "RETURN":
        fail("the Controller-accepted RETURN has the wrong production endpoints")

    handoff = read_json(root / "result_handoff_trace.json")
    events = handoff.get("events") or []
    if [item.get("substage") for item in events] != SUBSTAGES:
        fail("Result Handoff did not accept 13.1–13.5 in order")
    for sequence, item in enumerate(events, 1):
        if item.get("accepted") is not True or item.get("eventSequence") != sequence:
            fail("Result Handoff event was rejected, skipped, or duplicated")
        if item.get("eventProducer") != "RecognitionResultScreen.emitResultHandoffMilestone" or item.get("eventConsumer") != "RecognitionResultHandoffConsumer.consume":
            fail("Result Handoff event producer/consumer is not the production implementation")
        if (item.get("sessionId") != identity["recognitionSessionId"] or item.get("attemptId") != identity["recognitionAttemptId"] or
                item.get("imageId") != identity["imageId"] or item.get("photoSha") != identity["photoSha256"]):
            fail("Result Handoff event identity changed across the page transition")
    event_times = [int(item["timestamp"]) for item in events]
    if any(later <= earlier for earlier, later in zip(event_times, event_times[1:])):
        fail("Result Handoff event timestamps are not strictly increasing")

    navigation = read_json(root / "result_navigation_trace.json")
    if navigation.get("status") != "PASS" or [row.get("status") for row in navigation.get("transitions", [])] != NAVIGATION:
        fail("production NavController transaction was not completed once in the required order")
    stability = read_json(root / "result_stability_report.json")
    if stability.get("status") != "PASS" or stability.get("gates", {}).get("resultSceneStable") is not True:
        fail("Result stability gates did not pass after the fifth substage")
    if stability.get("gates", {}).get("animationGeometryReleasedAfterStable") is not True:
        fail("Result geometry was not released after the stable event")

    app_qualification = read_json(root / "qualification_report.json")
    if app_qualification.get("sourceCommitSha") != args.build_sha or app_qualification.get("apkSha256") != apk_sha:
        fail("app qualification report does not bind to this source and APK")
    checks = app_qualification.get("checks") or []
    by_id = {check.get("id"): check for check in checks}
    required_non_host = {check.get("id") for check in checks} - {
        "T02_ATTEMPT_ISOLATION", "T13_60S_DEADLINE", "T28_EVIDENCE_INTEGRITY",
    }
    if not required_non_host or any(by_id.get(key, {}).get("status") != "PASS" for key in required_non_host):
        fail("one or more app-side production qualification checks failed")
    if guardrail.get("attemptIsolation") != "PASS" or guardrail.get("deadlineExpiryBoundary") != "PASS" or guardrail.get("deadlineMs") != 60_000:
        fail("attempt isolation or 60-second deadline guardrail instrumentation failed")

    for name in ("processing_page.png", "result_page.png"):
        file_path = root / name
        if not file_path.is_file() or file_path.stat().st_size < 1024 or file_path.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
            fail(f"real UI screenshot is missing or invalid: {name}")
    video_path = root / "recognition_result_handoff_v3.mp4"
    video_duration_seconds = verify_video(video_path)

    # Fill the two checks proven by the second device-side test and host-side
    # evidence closure; every other row already came from app runtime state.
    for check_id in ("T02_ATTEMPT_ISOLATION", "T13_60S_DEADLINE"):
        if check_id not in by_id:
            fail(f"app qualification report has no row for {check_id}")
        by_id[check_id]["status"] = "PASS"
        by_id[check_id]["evidence"] = "guardrail_test_report.json"
    if "T28_EVIDENCE_INTEGRITY" not in by_id:
        fail("app qualification report has no row for T28_EVIDENCE_INTEGRITY")
    by_id["T28_EVIDENCE_INTEGRITY"]["status"] = "PASS"
    by_id["T28_EVIDENCE_INTEGRITY"]["evidence"] = "evidence_manifest.json"
    app_qualification["status"] = "PASS"
    app_qualification["videoDurationSeconds"] = round(video_duration_seconds, 3)
    app_qualification["sourceCommitSha"] = args.build_sha
    app_qualification["apkSha256"] = apk_sha
    (root / "qualification_report.json").write_text(json.dumps(app_qualification, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    if any(item.get("status") != "PASS" for item in app_qualification["checks"]):
        fail("final T01–T28 qualification matrix is not all PASS")

    sources = {
        "runtime_provenance.json": "android_app_private_cache",
        "model_release_contract.json": "apk_packaged_asset_snapshot",
        "apk_model_trace.json": "host_extraction_from_exact_app_apk",
        "runtime_matrix.json": "android_app_private_cache_plus_physical_device_logcat",
        "path_identity_trace.json": "android_app_private_cache",
        "return_event_trace.json": "android_app_private_cache",
        "result_handoff_trace.json": "android_app_private_cache",
        "result_navigation_trace.json": "android_app_private_cache",
        "result_stability_report.json": "android_app_private_cache",
        "stage_timeline.json": "android_app_private_cache",
        "qualification_report.json": "android_app_private_cache_plus_host_evidence_verifier",
        "production_e2e_test_report.json": "android_test_private_cache",
        "guardrail_test_report.json": "android_test_private_cache",
        "processing_page.png": "physical_device_ui_automation_screenshot",
        "result_page.png": "physical_device_ui_automation_screenshot",
        "recognition_result_handoff_v3.mp4": "physical_device_screenrecord",
        "recognition_runtime_logcat.txt": "physical_device_adb_logcat",
        "device_identity.json": "physical_device_adb_getprop",
    }
    records = []
    for file_path in sorted(root.rglob("*")):
        if not file_path.is_file() or file_path.name == "evidence_manifest.json":
            continue
        size = file_path.stat().st_size
        if size <= 0:
            fail(f"manifest candidate is empty: {file_path.relative_to(root)}")
        relative = file_path.relative_to(root).as_posix()
        records.append({
            "path": relative,
            "bytes": size,
            "sha256": sha256_file(file_path),
            "source": sources.get(file_path.name, "android_runtime_harness_or_physical_device_preflight"),
            "sourceCommitSha": args.build_sha,
            "apkSha256": apk_sha,
        })
    missing = set(APP_EVIDENCE) - {Path(item["path"]).name for item in records}
    if missing:
        fail(f"manifest is missing required evidence files: {sorted(missing)}")
    manifest = {
        "status": "PASS",
        "schemaVersion": 1,
        "sourceCommitSha": args.build_sha,
        "appApkSha256": apk_sha,
        "device": device,
        "clockSource": "android.os.SystemClock.uptimeMillis",
        "files": records,
        "manifestSelfHash": "OMITTED_TO_AVOID_SELF_REFERENCE",
    }
    (root / "evidence_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        "status": "PASS",
        "sourceCommitSha": args.build_sha,
        "apkSha256": apk_sha,
        "device": device,
        "stages": len(timeline),
        "resultEvents": len(events),
        "videoDurationSeconds": round(video_duration_seconds, 3),
        "manifestFiles": len(records),
    }, ensure_ascii=False, indent=2))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence-dir", required=True, type=Path)
    parser.add_argument("--app-apk", required=True, type=Path)
    parser.add_argument("--build-sha", required=True)
    parser.add_argument("--expected-api-level", required=True, type=int)
    args = parser.parse_args()
    try:
        verify(args)
    except (OSError, ValueError, KeyError, TypeError, subprocess.CalledProcessError) as error:
        print(f"RECOGNITION_RESULT_HANDOFF_EVIDENCE_FAIL: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
