#!/usr/bin/env bash

# The frozen Recognition gate includes the Recognition Flow suite and RR09's
# 12-profile layout capture. The prior exact-SHA run reached test 34/52 when
# the 300-second harness limit expired, so give this gate enough time to finish.
gate_instrumentation_timeout_seconds() {
  printf '%s\n' '600'
}

gate_test_classes() {
  printf '%s\n' 'com.yujian.ai.ui.identify.RecognitionFirstCaptureGuideRuntimeTest,com.yujian.ai.RecognitionFrozenFlowEmulatorTest,com.yujian.ai.RecognitionOpticalSegmentRuntimeTest,com.yujian.ai.RecognitionFishContourRuntimeTest,com.yujian.ai.RecognitionImageStoreTest,com.yujian.ai.catches.GuestCatchRepositoryRuntimeTest,com.yujian.ai.FishDetectionQualityGateTest,com.yujian.ai.PipelineTraceTest,com.yujian.ai.DetectorOrientationRetryRuntimeTest,com.yujian.ai.DetectorGoldenParityTest'
}

gate_additional_test_classes() {
  printf '%s\n' 'com.yujian.ai.RecognitionOpticalFidelityRuntimeTest'
}

gate_additional_test_timeout_seconds() {
  printf '%s\n' '420'
}
# Video evidence is captured outside instrumentation so observation cannot
# perturb Compose timing on API28. Instrumentation owns only semantic/timing
# assertions and single proof screenshots.
YUJIAN_RECOGNITION_DEVICE_RECORDING="/sdcard/recognition_processing_runtime_host.mp4"
YUJIAN_RECOGNITION_OUTPUT_NAME="recognition_processing_v3_1.mp4"

gate_before_instrumentation() {
  if [[ "${YUJIAN_CAPTURE_RECOGNITION_VIDEO:-1}" != "1" ]]; then
    printf 'PRETEST_STATUS=SKIPPED_RECORDING_DISABLED\n' > "$YUJIAN_EVIDENCE_DIR/rr09_production_pretest_status.txt"
    return 0
  fi
  "${YUJIAN_ADB_BIN}" shell "rm -f '${YUJIAN_RECOGNITION_DEVICE_RECORDING}'" >/dev/null 2>&1 || true
  "${YUJIAN_ADB_BIN}" shell 'cut -d" " -f1 /proc/uptime' 2>/dev/null | tr -d '\r' \
    > "$YUJIAN_EVIDENCE_DIR/rr09_gate_recording_start_uptime_seconds.txt" || true
  "${YUJIAN_ADB_BIN}" shell \
    "screenrecord --bit-rate 4000000 --time-limit 120 '${YUJIAN_RECOGNITION_DEVICE_RECORDING}' >/dev/null 2>&1 &" \
    >/dev/null 2>&1 || true
  sleep 0.5

  local pretest_log="$YUJIAN_EVIDENCE_DIR/rr09_production_pretest.log"
  local pretest_status="$YUJIAN_EVIDENCE_DIR/rr09_production_pretest_status.txt"
  local pretest_rc=0
  timeout 180s "${YUJIAN_ADB_BIN}" shell am instrument -w -r \
    -e class com.yujian.ai.RecognitionRevealProductionRuntimeTest \
    "$YUJIAN_INSTRUMENTATION_TARGET" > "$pretest_log" 2>&1 || pretest_rc=$?

  local pids pid
  pids="$("${YUJIAN_ADB_BIN}" shell "pidof screenrecord 2>/dev/null || true" 2>/dev/null | tr -d '\r')"
  for pid in $pids; do "${YUJIAN_ADB_BIN}" shell kill -2 "$pid" >/dev/null 2>&1 || true; done
  local recording_bytes=0 attempt
  for attempt in $(seq 1 24); do
    recording_bytes="$("${YUJIAN_ADB_BIN}" shell \
      "wc -c < '${YUJIAN_RECOGNITION_DEVICE_RECORDING}' 2>/dev/null || echo 0" \
      2>/dev/null | tr -d '\r' | tail -n 1)"
    if [[ "$recording_bytes" =~ ^[0-9]+$ ]] && (( recording_bytes > 1024 )); then break; fi
    sleep 0.25
  done
  if (( pretest_rc == 0 )) && ! grep -Eiq 'FAILURES!!!|INSTRUMENTATION_FAILED|Assertion(Error|FailedError)|Process (crashed|has died)' "$pretest_log" && \
    grep -Eq 'INSTRUMENTATION_CODE:[[:space:]]*0|OK \([0-9]+ test' "$pretest_log"; then
    printf 'PRETEST_STATUS=PASS\nRECORDING_BYTES=%s\n' "$recording_bytes" > "$pretest_status"
  else
    printf 'PRETEST_STATUS=FAIL\nPRETEST_EXIT_CODE=%s\nRECORDING_BYTES=%s\n' "$pretest_rc" "$recording_bytes" > "$pretest_status"
    runtime_set_failure "INSTRUMENTATION" "RR09_PRODUCTION_QUALIFICATION_FAILED"
  fi
}

gate_after_instrumentation() {
  if [[ "${YUJIAN_CAPTURE_RECOGNITION_VIDEO:-1}" != "1" ]]; then
    return 0
  fi
  local pids pid
  pids="$("${YUJIAN_ADB_BIN}" shell "pidof screenrecord 2>/dev/null || true" 2>/dev/null | tr -d '\r')"
  for pid in $pids; do
    "${YUJIAN_ADB_BIN}" shell kill -2 "$pid" >/dev/null 2>&1 || true
  done

  # SIGINT lets screenrecord finalize MP4 metadata. Wait for a non-trivial,
  # stable file before evidence collection pulls it to the host.
  local previous=0 stable=0 size=0 attempt
  for attempt in $(seq 1 24); do
    size="$("${YUJIAN_ADB_BIN}" shell \
      "wc -c < '${YUJIAN_RECOGNITION_DEVICE_RECORDING}' 2>/dev/null || echo 0" \
      2>/dev/null | tr -d '\r' | tail -n 1)"
    if [[ "$size" =~ ^[0-9]+$ ]] && (( size > 1024 )); then
      if [[ "$size" == "$previous" ]]; then
        stable=$((stable + 1))
        if (( stable >= 2 )); then
          break
        fi
      else
        stable=0
      fi
      previous="$size"
    fi
    sleep 0.25
  done
  return 0
}

gate_collect_evidence() {
  local output_dir="$YUJIAN_EVIDENCE_DIR/recognition_v1_2"
  mkdir -p "$output_dir"

  local first_guide_output="$YUJIAN_EVIDENCE_DIR/first_use_camera_guide"
  mkdir -p "$first_guide_output"
  local name
  for name in P01_compose_component.png P02_compose_component.png P03_compose_component.png first_use_camera_guide_runtime_report.json; do
    "${YUJIAN_ADB_BIN}" exec-out run-as "$YUJIAN_APP_PACKAGE" cat \
      "cache/recognition-first-use-camera-guide/${name}" \
      > "$first_guide_output/$name" 2>/dev/null || true
    if [[ ! -s "$first_guide_output/$name" ]]; then
      printf 'MISSING_EVIDENCE=%s\n' "$first_guide_output/$name" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
      runtime_set_failure "EVIDENCE" "FIRST_USE_CAMERA_GUIDE_EVIDENCE_MISSING"
      return "$EXIT_FAIL_EVIDENCE"
    fi
  done
  if ! python3 - "$first_guide_output/first_use_camera_guide_runtime_report.json" \
    "$YUJIAN_BUILD_SHA" "${YUJIAN_APP_APK:-}" <<'PY'
import hashlib
import json
import sys
from pathlib import Path

report_path = Path(sys.argv[1])
build_sha = sys.argv[2]
apk_path = Path(sys.argv[3]) if sys.argv[3] else None
report = json.loads(report_path.read_text(encoding="utf-8"))
report["source_commit_sha"] = build_sha
report["apk_sha256_host"] = None
report["apk_bytes_host"] = None
report["apk_sha256_match"] = None
if apk_path and apk_path.is_file():
    hasher = hashlib.sha256()
    with apk_path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            hasher.update(block)
    digest = hasher.hexdigest()
    report["apk_sha256_host"] = digest
    report["apk_bytes_host"] = apk_path.stat().st_size
    report["apk_sha256_match"] = report.get("apk_sha256") == digest
    if not report["apk_sha256_match"]:
        report["status"] = "APK_SHA_MISMATCH"
        report["failure_classification"] = "APK_SHA_MISMATCH"
report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
if report["apk_sha256_match"] is False:
    raise SystemExit("installed APK SHA-256 does not match the exact host build APK")
PY
  then
    runtime_set_failure "EVIDENCE" "FIRST_USE_CAMERA_GUIDE_APK_SHA_MISMATCH"
    return "$EXIT_FAIL_EVIDENCE"
  fi

  for name in \
    02_image_recognizing_late.png \
    03_fish_located.png \
    04_species_recognizing.png \
    05_result_high.png \
    06_result_medium.png \
    07_result_low.png \
    08_error_no_fish.png \
    09_error_image_quality.png \
    10_issue_technical_failure.png \
    level_a_real_contour.png \
    06_edge_field_crop.png \
    07_fish_focus_crop.png \
    08_contour_closeup.png \
    quality_full.png \
    quality_balanced.png \
    quality_lite.png \
    reduce_motion_static.png \
    degradation_d0_d4_contact_sheet.png
  do
    "${YUJIAN_ADB_BIN}" exec-out run-as "$YUJIAN_APP_PACKAGE" cat "cache/recognition-evidence/${name}" \
      > "$output_dir/$name" 2>/dev/null || true
    if [[ ! -s "$output_dir/$name" ]]; then
      rm -f "$output_dir/$name"
    fi
  done

  "${YUJIAN_ADB_BIN}" exec-out run-as "$YUJIAN_APP_PACKAGE" cat \
    "cache/recognition-evidence/recognition_processing_timing_v3_1.txt" \
    > "$output_dir/recognition_processing_timing_v3_1.txt" 2>/dev/null || true
  if [[ ! -s "$output_dir/recognition_processing_timing_v3_1.txt" ]]; then
    rm -f "$output_dir/recognition_processing_timing_v3_1.txt"
  fi

  "${YUJIAN_ADB_BIN}" exec-out run-as "$YUJIAN_APP_PACKAGE" cat \
    "cache/recognition-evidence/recognition_production_flow_trace_v3_1.json" \
    > "$output_dir/recognition_production_flow_trace_v3_1.json" 2>/dev/null || true
  if [[ ! -s "$output_dir/recognition_production_flow_trace_v3_1.json" ]]; then
    rm -f "$output_dir/recognition_production_flow_trace_v3_1.json"
  fi

  for name in recognition_motion_trace_v3_1.json recognition_accessibility_trace_v3_1.json recognition_visual_qa_v1_3.json fish_focus_bbox_mapping.json recognition_visual_parity_v1_2.json recognition_visual_parity_contact_sheet_v1_2.png portrait_low_confidence_original.jpg portrait_low_confidence_classifier_crop.png portrait_low_confidence_model_input_224.png portrait_low_confidence_inference_report.txt portrait_low_confidence_class_map.json portrait_low_confidence_diagnostic.json; do
    "${YUJIAN_ADB_BIN}" exec-out run-as "$YUJIAN_APP_PACKAGE" cat \
      "cache/recognition-evidence/${name}" > "$output_dir/${name}" 2>/dev/null || true
    if [[ ! -s "$output_dir/${name}" ]]; then rm -f "$output_dir/${name}"; fi
  done

  local rr09_output="$output_dir/rr09-gold-transfer"
  mkdir -p "$rr09_output"
  local rr09_name
  for rr09_name in \
    K01_processing_fixture_final.png \
    K02_120ms.png \
    K03_290ms.png \
    K04_465ms.png \
    K05_610ms.png \
    K06_850ms.png \
    RR09_Frames_Manifest.json \
    RR09_Motion_Trace.json \
    RR09_API28_Test_Report.json \
    RR09_Performance_Report.json \
    RR09_Responsive_Matrix_Report.json
  do
    "${YUJIAN_ADB_BIN}" exec-out run-as "$YUJIAN_APP_PACKAGE" cat \
      "cache/rr09-gold-transfer/${rr09_name}" > "$rr09_output/${rr09_name}" 2>/dev/null || true
    if [[ ! -s "$rr09_output/${rr09_name}" ]]; then
      printf 'MISSING_EVIDENCE=%s\n' "$rr09_output/${rr09_name}" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
      runtime_set_failure "EVIDENCE" "RR09_NATIVE_FRAME_EVIDENCE_MISSING"
      return "$EXIT_FAIL_EVIDENCE"
    fi
  done
  # Reject adb's textual "cat: ... No such file" output as an artifact. A
  # non-empty file is not sufficient evidence for a PNG frame.
  for rr09_name in \
    K01_processing_fixture_final.png \
    K02_120ms.png \
    K03_290ms.png \
    K04_465ms.png \
    K05_610ms.png \
    K06_850ms.png
  do
    if ! python3 - "$rr09_output/$rr09_name" <<'PY'
import sys
from pathlib import Path

path = Path(sys.argv[1])
try:
    with path.open("rb") as image:
        valid = image.read(8) == b"\x89PNG\r\n\x1a\n"
except OSError:
    valid = False
if not valid:
    raise SystemExit(f"invalid PNG evidence: {path}")
PY
    then
      printf 'INVALID_EVIDENCE=%s\n' "$rr09_output/$rr09_name" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
      runtime_set_failure "EVIDENCE" "RR09_NATIVE_FRAME_EVIDENCE_INVALID"
      return "$EXIT_FAIL_EVIDENCE"
    fi
  done
  local rr09_dimensions rr09_font
  for rr09_dimensions in 320x480 360x640 390x844 430x932; do
    for rr09_font in 1p0 1p3 1p5; do
      rr09_name="responsive_${rr09_dimensions}_font${rr09_font}.png"
      "${YUJIAN_ADB_BIN}" exec-out run-as "$YUJIAN_APP_PACKAGE" cat \
        "cache/rr09-gold-transfer/responsive/${rr09_name}" > "$rr09_output/${rr09_name}" 2>/dev/null || true
      if [[ ! -s "$rr09_output/${rr09_name}" ]]; then
        printf 'MISSING_EVIDENCE=%s\n' "$rr09_output/${rr09_name}" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
        runtime_set_failure "EVIDENCE" "RR09_RESPONSIVE_MATRIX_FRAME_MISSING"
        return "$EXIT_FAIL_EVIDENCE"
      fi
    done
  done
  local rr09_production_dir="$rr09_output/production_model_flow"
  mkdir -p "$rr09_production_dir"
  if ! grep -Fxq 'PRETEST_STATUS=PASS' "$YUJIAN_EVIDENCE_DIR/rr09_production_pretest_status.txt" 2>/dev/null; then
    printf 'RR09 production runtime class did not produce a successful API28 qualification. See rr09_production_pretest.log.\n' >&2
    runtime_set_failure "INSTRUMENTATION" "RR09_PRODUCTION_QUALIFICATION_FAILED"
    return "$EXIT_FAIL_EVIDENCE"
  fi
  local rr09_production_name
  for rr09_production_name in \
    RR09_Production_K01_processing.png \
    RR09_Production_K02_120ms.png \
    RR09_Production_K03_290ms.png \
    RR09_Production_K04_465ms.png \
    RR09_Production_K05_610ms.png \
    RR09_Production_K06_850ms.png \
    RR09_Production_Qualification_Report.json
  do
    "${YUJIAN_ADB_BIN}" exec-out run-as "$YUJIAN_APP_PACKAGE" cat \
      "cache/rr09-gold-transfer-production/${rr09_production_name}" \
      > "$rr09_production_dir/${rr09_production_name}" 2>/dev/null || true
    if [[ ! -s "$rr09_production_dir/${rr09_production_name}" ]]; then
      printf 'MISSING_EVIDENCE=%s\n' "$rr09_production_dir/${rr09_production_name}" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
      runtime_set_failure "EVIDENCE" "RR09_PRODUCTION_MODEL_FLOW_EVIDENCE_MISSING"
      return "$EXIT_FAIL_EVIDENCE"
    fi
  done
  "${YUJIAN_ADB_BIN}" pull "$YUJIAN_RECOGNITION_DEVICE_RECORDING" \
    "$rr09_production_dir/RR09_Production_Recognition_Flow_Recording.mp4" >/dev/null 2>&1 || true
  if [[ ! -s "$rr09_production_dir/RR09_Production_Recognition_Flow_Recording.mp4" ]]; then
    printf 'MISSING_EVIDENCE=%s\n' "$rr09_production_dir/RR09_Production_Recognition_Flow_Recording.mp4" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
    runtime_set_failure "EVIDENCE" "RR09_PRODUCTION_FLOW_RECORDING_MISSING"
    return "$EXIT_FAIL_EVIDENCE"
  fi
  cp "$YUJIAN_EVIDENCE_DIR/rr09_gate_recording_start_uptime_seconds.txt" \
    "$rr09_production_dir/recording_start_uptime_seconds.txt" 2>/dev/null || true
  cp "$rr09_production_dir/RR09_Production_Recognition_Flow_Recording.mp4" \
    "$rr09_output/RR09_Recognition_Flow_Recording.mp4"
  if ! python3 - "$rr09_output" "$YUJIAN_BUILD_SHA" "$YUJIAN_APP_APK" "RR09_Recognition_Flow_Recording.mp4" "$YUJIAN_REPO_ROOT/design/system/core_visual_v1/reference/recognition_result_v1.png" <<'PY'
import hashlib
import json
import os
import subprocess
import sys
from pathlib import Path
from PIL import Image, ImageChops, ImageStat

evidence = Path(sys.argv[1])
build_sha = sys.argv[2]
apk = Path(sys.argv[3]) if sys.argv[3] else None
recording_name = sys.argv[4]
frozen_reference = Path(sys.argv[5])
manifest_path = evidence / "RR09_Frames_Manifest.json"
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
manifest["source_commit_sha"] = build_sha
manifest["runtime_device_api"] = 28
manifest["runtime_recording"] = recording_name
manifest["runtime_recording_status"] = "CAPTURED_BY_API28_GATE_SCREENRECORD"
manifest["review_status"] = "VISUAL_PARITY_REVIEW_REQUIRED"
manifest["workflow_run_id"] = os.environ.get("GITHUB_RUN_ID")
manifest["apk_sha256"] = None
manifest["recording_source"] = "API28 gate screenrecord captured during the real production-data reveal test"
manifest["notes"] = [
    "Fixture frames use the deterministic RR09 clock override; they are not samples from the live monotonic playback.",
    "The fixture fish alpha and species result are instrumentation-only; production model identity and segmentation are reported separately under production_model_flow.",
    "K01 is the last Processing fixture frame, not a camera-driven RESULT_READY capture.",
    "The continuous recording is the API28 gate screenrecord from the real production-data reveal qualification.",
]
production_dir = evidence / "production_model_flow"
production = json.loads((production_dir / "RR09_Production_Qualification_Report.json").read_text(encoding="utf-8"))
production_recording = production_dir / "RR09_Production_Recognition_Flow_Recording.mp4"
production_probe = subprocess.run(
    ["ffprobe", "-v", "error", "-show_streams", "-show_format", "-of", "json", str(production_recording)],
    check=True,
    capture_output=True,
    text=True,
)
production_video = json.loads(production_probe.stdout)
production_stream = next(item for item in production_video["streams"] if item.get("codec_type") == "video")
production_duration = float(production_video["format"]["duration"])
if production_duration < 0.85 or int(production_stream["width"]) < 320 or int(production_stream["height"]) < 640:
    raise SystemExit(f"RR09_PRODUCTION_RECORDING_INVALID duration={production_duration} dimensions={production_stream.get('width')}x{production_stream.get('height')}")
production["source_commit_sha"] = build_sha
production["workflow_run_id"] = os.environ.get("GITHUB_RUN_ID")
production["apk_sha256"] = None
production["recording_file"] = production_recording.name
production["recording_duration_seconds"] = production_duration
production["recording_dimensions"] = [int(production_stream["width"]), int(production_stream["height"])]
start_uptime = (production_dir / "recording_start_uptime_seconds.txt").read_text(encoding="utf-8").strip()
production["recording_start_uptime_seconds"] = start_uptime
production["recording_source"] = "API28 screenrecord started by the runtime gate before this production-data instrumentation class"
production_frame_specs = [
    ("RR09_Production_K01_processing.png", None),
    ("RR09_Production_K02_120ms.png", 120),
    ("RR09_Production_K03_290ms.png", 290),
    ("RR09_Production_K04_465ms.png", 465),
    ("RR09_Production_K05_610ms.png", 610),
    ("RR09_Production_K06_850ms.png", 850),
]
manifest["production_model_flow"] = {
    "status": production.get("status"),
    "source_asset": production.get("source_asset"),
    "source_sha256": production.get("source_sha256"),
    "result_entry_mode": production.get("result_entry_mode"),
    "frames": [
        {"file": f"production_model_flow/{name}", "sample_ms": sample}
        for name, sample in production_frame_specs
    ],
}
manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

report = json.loads((evidence / "RR09_API28_Test_Report.json").read_text(encoding="utf-8"))
matrix = json.loads((evidence / "RR09_Responsive_Matrix_Report.json").read_text(encoding="utf-8"))
if matrix.get("status") != "PASS" or matrix.get("case_count") != 12:
    raise SystemExit(f"RR09_RESPONSIVE_MATRIX_INVALID case_count={matrix.get('case_count')}")
matrix["source_commit_sha"] = build_sha
matrix["workflow_run_id"] = os.environ.get("GITHUB_RUN_ID")
(evidence / "RR09_Responsive_Matrix_Report.json").write_text(
    json.dumps(matrix, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
)
recording = evidence / "RR09_Recognition_Flow_Recording.mp4"
probe = subprocess.run(
    ["ffprobe", "-v", "error", "-show_streams", "-show_format", "-of", "json", str(recording)],
    check=True,
    capture_output=True,
    text=True,
)
metadata = json.loads(probe.stdout)
video_stream = next(item for item in metadata["streams"] if item.get("codec_type") == "video")
duration = float(metadata["format"]["duration"])
if duration < 0.85 or int(video_stream["width"]) < 320 or int(video_stream["height"]) < 640:
    raise SystemExit(f"RR09_RECORDING_INVALID duration={duration} dimensions={video_stream.get('width')}x{video_stream.get('height')}")
report["recording_duration_seconds"] = duration
report["recording_dimensions"] = [int(video_stream["width"]), int(video_stream["height"])]
report.update({
    "source_commit_sha": build_sha,
    "apk_sha256": None,
    "apk_bytes": None,
    "android_build": "PENDING_EXACT_SHA_BUILD_SUMMARY",
    "api28_runtime": "PASS_FOR_INSTRUMENTATION_FIXTURE",
    "responsive_matrix": "PASS_12_CASE_API28_RUNTIME",
    "production_model_reveal": production.get("status", "NOT_RUN"),
    "api28_production_runtime": "PASS",
    "production_model_species_id": production.get("species_id"),
    "production_model_confidence": production.get("species_confidence"),
    "production_geometry_trust": production.get("geometry_trust"),
    "production_geometry_status": production.get("result_geometry_status"),
    "production_recording_status": "API28_GATE_SCREENRECORD_INCLUDES_PRODUCTION_TEST_FIRST",
    "visual_parity_review": "REVIEW_REQUIRED",
    "performance": "NOT_RUN",
    "frame_sampling": "DETERMINISTIC_TEST_CLOCK",
    "recording_source": "API28_GATE_SCREENRECORD_DURING_PRODUCTION_DATA_REVEAL",
})
if apk and apk.is_file():
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    report["apk_sha256"] = digest
    report["apk_bytes"] = apk.stat().st_size
    manifest["apk_sha256"] = digest
    production["apk_sha256"] = digest
    (evidence / "RR09_Gold_Transfer_Android_Debug.apk").write_bytes(apk.read_bytes())
    (evidence / "RR09_Gold_Transfer_Android_Debug_SHA256.txt").write_text(
        f"{digest}  RR09_Gold_Transfer_Android_Debug.apk\n", encoding="utf-8"
    )
else:
    raise SystemExit(f"RR09_APK_MISSING path={apk}")
for production_frame in production_frame_specs:
    frame_name, _sample = production_frame
    frame_path = production_dir / frame_name
    if not frame_path.is_file():
        raise SystemExit(f"RR09_PRODUCTION_FRAME_MISSING path={frame_path}")
    with Image.open(frame_path) as image:
        width, height = image.size
    manifest["production_model_flow"]["frames"][production_frame_specs.index(production_frame)].update({
        "width_px": width,
        "height_px": height,
        "bytes": frame_path.stat().st_size,
        "sha256": hashlib.sha256(frame_path.read_bytes()).hexdigest(),
    })
manifest["production_model_flow"]["recording"] = {
    "file": "production_model_flow/RR09_Production_Recognition_Flow_Recording.mp4",
    "duration_seconds": production_duration,
    "dimensions": [int(production_stream["width"]), int(production_stream["height"])],
    "sha256": hashlib.sha256(production_recording.read_bytes()).hexdigest(),
}
production["recording_sha256"] = manifest["production_model_flow"]["recording"]["sha256"]
(production_dir / "RR09_Production_Qualification_Report.json").write_text(
    json.dumps(production, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
)
for frame in manifest.get("frames", []):
    frame_path = evidence / frame["file"]
    if not frame_path.is_file():
        raise SystemExit(f"RR09_FRAME_MISSING path={frame_path}")
    with Image.open(frame_path) as image:
        frame["width_px"], frame["height_px"] = image.size
    frame["bytes"] = frame_path.stat().st_size
    frame["sha256"] = hashlib.sha256(frame_path.read_bytes()).hexdigest()
manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
performance = json.loads((evidence / "RR09_Performance_Report.json").read_text(encoding="utf-8"))
performance["source_commit_sha"] = build_sha
performance["workflow_run_id"] = __import__("os").environ.get("GITHUB_RUN_ID")
performance["apk_sha256"] = report["apk_sha256"]
(evidence / "RR09_Performance_Report.json").write_text(
    json.dumps(performance, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
)
report["performance"] = "CAPTURED_FRAME_METRICS_WITH_ALLOCATION_RATE_NOT_RUN"
report["workflow_run_id"] = __import__("os").environ.get("GITHUB_RUN_ID")
(evidence / "RR09_API28_Test_Report.json").write_text(
    json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
)
(evidence / "RR09_Android_Runtime_Report.md").write_text(
    "# RR09 Android Runtime Report\n\n"
    f"- Source SHA: {build_sha}\n"
    f"- Android workflow run: {__import__('os').environ.get('GITHUB_RUN_ID')}\n"
    "- Runtime: Android API 28 instrumentation on the frozen Pixel 2 emulator configuration (1080 x 1920).\n"
    "- Native Compose reveal: PASS_FOR_INSTRUMENTATION_FIXTURE.\n"
    f"- Six deterministic fixture frames were captured from the Android test app; the continuous native production reveal recording is the API28 gate screenrecord ({duration:.2f} seconds).\n"
    f"- Production model result and ML Kit segmentation: {production.get('status')} for {production.get('species_id')} (confidence {production.get('species_confidence')}); geometry {production.get('geometry_trust')} / {production.get('result_geometry_status')}.\n"
    "- Production-data frame captures: six screenshots under production_model_flow. Result entry in this isolated production-data qualification test is test-controlled after the actual model RESULT_READY; automatic V3.1 handoff is tested separately.\n"
    f"- Production recording: {production_recording.name}, {production_duration:.2f}s, {production_stream['width']} x {production_stream['height']}; API28 screenrecord begins before the production runtime class.\n"
    "- Responsive matrix: PASS, 12 API28 cases across 320×480, 360×640, 390×844, 430×932 at font scales 1.0, 1.3, 1.5.\n"
    "- Frame timing captured from Android Window FrameMetrics; allocation rate: NOT_RUN.\n"
    f"- APK SHA-256: {report['apk_sha256']}\n"
    "- Visual parity: REVIEW_REQUIRED pending human review against the frozen Result design and browser prototype.\n"
    "\nThe production qualification uses the SHA-verified registered night-catch photo, actual production detector/classifier and ML Kit subject mask. The separate baseline RR09 fixture frames still use test-only alpha and species data.\n",
    encoding="utf-8",
)

if not frozen_reference.is_file():
    raise SystemExit(f"RR09_FROZEN_REFERENCE_MISSING path={frozen_reference}")
final_frame = Image.open(evidence / "K06_850ms.png").convert("RGB")
reference = Image.open(frozen_reference).convert("RGB")
final_scaled = final_frame.resize(reference.size, Image.Resampling.LANCZOS)
diff = ImageChops.difference(final_scaled, reference)
overlay = Image.blend(reference, final_scaled, 0.5)
diff_path = evidence / "RR09_Android_Static_Pixel_Diff.png"
overlay_path = evidence / "RR09_Android_Static_Overlay.png"
diff.save(diff_path)
overlay.save(overlay_path)
mean_absolute_error = sum(ImageStat.Stat(diff).mean) / 3.0
changed_ratio = sum(1 for pixel in diff.getdata() if max(pixel) > 18) / (reference.width * reference.height)
android_static = Image.open(Path(sys.argv[1]).parent / "05_result_high.png").convert("RGB")
android_final = final_frame.resize(android_static.size, Image.Resampling.LANCZOS)
android_diff = ImageChops.difference(android_static, android_final)
android_mean_absolute_error = sum(ImageStat.Stat(android_diff).mean) / 3.0
android_changed_ratio = sum(1 for pixel in android_diff.getdata() if max(pixel) > 18) / (android_static.width * android_static.height)
(evidence / "RR09_Visual_Comparison_Report.md").write_text(
    "# RR09 Visual Comparison Report\n\n"
    f"- Source SHA: {build_sha}\n"
    f"- Android final screenshot: K06_850ms.png ({final_frame.width} x {final_frame.height})\n"
    f"- Frozen Result reference: {frozen_reference} ({reference.width} x {reference.height}), SHA-256 {hashlib.sha256(frozen_reference.read_bytes()).hexdigest()}\n"
    f"- Final Android to frozen reference: mean absolute RGB difference {mean_absolute_error:.3f}; pixels with max-channel delta above 18: {changed_ratio:.3%}.\n"
    f"- Final Android to static Android Result capture: mean absolute RGB difference {android_mean_absolute_error:.3f}; pixels with max-channel delta above 18: {android_changed_ratio:.3%}.\n"
    "- Pixel diff and 50% overlay files are included. Resizing to reference dimensions is used only for comparison; the frozen PNG bytes are unchanged.\n"
    "- Component bounds in window pixels are recorded in RR09_Frames_Manifest.json.\n"
    "- Dynamic browser-to-Android comparison: REVIEW_REQUIRED; browser run is Design Governance 39584894137 and its recording was not downloaded into this runtime artifact.\n"
    "- VISUAL_PARITY_REVIEW_REQUIRED. Pixel metrics are evidence and are not an automatic visual approval.\n",
    encoding="utf-8",
)
PY
  then
    runtime_set_failure "EVIDENCE" "RR09_NATIVE_FRAME_EVIDENCE_INVALID"
    return "$EXIT_FAIL_EVIDENCE"
  fi

  local optical_dir="$output_dir/optical-fidelity"
  mkdir -p "$optical_dir"
  for name in recognition_optical_fidelity_v3_1.json optical_fidelity_full.png optical_fidelity_lite.png; do
    "${YUJIAN_ADB_BIN}" exec-out run-as "$YUJIAN_APP_PACKAGE" cat \
      "cache/recognition-optical-fidelity/${name}" > "$optical_dir/${name}" 2>/dev/null || true
    if [[ ! -s "$optical_dir/${name}" ]]; then
      printf 'MISSING_EVIDENCE=%s\n' "$optical_dir/${name}" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
      runtime_set_failure "EVIDENCE" "RECOGNITION_OPTICAL_FIDELITY_EVIDENCE_MISSING"
      return "$EXIT_FAIL_EVIDENCE"
    fi
  done
  if ! python3 "$YUJIAN_REPO_ROOT/scripts/verify_recognition_optical_fidelity_evidence.py" \
    --evidence-dir "$optical_dir"; then
    runtime_set_failure "EVIDENCE" "RECOGNITION_OPTICAL_FIDELITY_EVIDENCE_INVALID"
    return "$EXIT_FAIL_EVIDENCE"
  fi

  for name in \
    portrait_low_confidence_original.jpg \
    portrait_low_confidence_classifier_crop.png \
    portrait_low_confidence_model_input_224.png \
    portrait_low_confidence_inference_report.txt \
    portrait_low_confidence_class_map.json \
    portrait_low_confidence_diagnostic.json
  do
    if [[ ! -s "$output_dir/$name" ]]; then
      printf 'MISSING_EVIDENCE=%s\n' "$output_dir/$name" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
      runtime_set_failure "EVIDENCE" "PORTRAIT_LOW_CONFIDENCE_REPLAY_EVIDENCE_MISSING"
      return "$EXIT_FAIL_EVIDENCE"
    fi
  done

  "${YUJIAN_ADB_BIN}" exec-out run-as "$YUJIAN_APP_PACKAGE" cat \
    "cache/recognition-evidence/recognition_focus_diagnostic.txt" \
    > "$output_dir/recognition_focus_diagnostic.txt" 2>/dev/null || true
  if [[ ! -s "$output_dir/recognition_focus_diagnostic.txt" ]]; then
    rm -f "$output_dir/recognition_focus_diagnostic.txt"
  fi

  local recording_bytes=0 attempt
  for attempt in $(seq 1 20); do
    recording_bytes="$("${YUJIAN_ADB_BIN}" shell \
      "wc -c < '${YUJIAN_RECOGNITION_DEVICE_RECORDING}' 2>/dev/null || echo 0" \
      2>/dev/null | tr -d '\r' | tail -n 1)"
    if [[ "$recording_bytes" =~ ^[0-9]+$ ]] && (( recording_bytes > 1024 )); then
      break
    fi
    sleep 0.25
  done
  if [[ "$recording_bytes" =~ ^[0-9]+$ ]] && (( recording_bytes > 1024 )); then
    "${YUJIAN_ADB_BIN}" pull "$YUJIAN_RECOGNITION_DEVICE_RECORDING" \
      "$output_dir/$YUJIAN_RECOGNITION_OUTPUT_NAME" >/dev/null 2>&1 || true
  fi
  if [[ ! -s "$output_dir/$YUJIAN_RECOGNITION_OUTPUT_NAME" ]]; then
    printf 'HOST_RECORDING_BYTES=%s\n' "$recording_bytes" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
    rm -f "$output_dir/$YUJIAN_RECOGNITION_OUTPUT_NAME"
  fi
  if [[ -s "$output_dir/02_image_recognizing_late.png" &&
        -s "$output_dir/03_fish_located.png" &&
        -s "$output_dir/04_species_recognizing.png" ]]; then
    python3 "$YUJIAN_REPO_ROOT/scripts/verify_recognition_visual_parity_v1_1.py" \
      --runtime-dir "$output_dir" \
      --reference-dir "$YUJIAN_REPO_ROOT/design/pages/recognition/design" \
      --output-dir "$output_dir" || {
        runtime_set_failure "EVIDENCE" "RECOGNITION_VISUAL_PARITY_INVALID"
        return "$EXIT_FAIL_EVIDENCE"
    }
  fi

  # The frozen evidence manifest retains the older "issue" names while the
  # production flow now emits the clearer "error" names. Keep both manifest
  # paths bound to the exact same captured evidence.
  [[ ! -s "$output_dir/08_error_no_fish.png" ]] || cp "$output_dir/08_error_no_fish.png" "$output_dir/08_issue_no_fish.png"
  [[ ! -s "$output_dir/09_error_image_quality.png" ]] || cp "$output_dir/09_error_image_quality.png" "$output_dir/09_issue_image_quality.png"

  if [[ -s "$output_dir/$YUJIAN_RECOGNITION_OUTPUT_NAME" && "${YUJIAN_VALIDATE_RECOGNITION_VIDEO:-1}" == "1" ]]; then
    local validation_rc=0
    python3 - "$output_dir" <<'PY' || validation_rc=$?
from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image, ImageChops, ImageStat

output_dir = Path(sys.argv[1])
video_path = output_dir / "recognition_processing_v3_1.mp4"
validation_path = output_dir / "recognition_processing_video_validation.txt"
reference_names = [
    "02_image_recognizing_late.png",
    "03_fish_located.png",
    "04_species_recognizing.png",
    "05_result_high.png",
]
references = [Image.open(output_dir / name).convert("RGB") for name in reference_names]
if len(references) != len(reference_names):
    raise SystemExit(f"RECOGNITION_VIDEO_REFERENCE_COUNT={len(references)}")

probe = subprocess.run(
    [
        "ffprobe", "-v", "error", "-count_frames",
        "-select_streams", "v:0", "-show_streams", "-show_format",
        "-of", "json", str(video_path),
    ],
    check=True,
    capture_output=True,
    text=True,
)
metadata = json.loads(probe.stdout)
stream = next(item for item in metadata["streams"] if item.get("codec_type") == "video")
duration = float(metadata["format"]["duration"])
width = int(stream["width"])
height = int(stream["height"])
frame_count_raw = stream.get("nb_read_frames") or stream.get("nb_frames") or "0"
try:
    frame_count = int(frame_count_raw)
except ValueError:
    frame_count = 0
if duration < 5.0:
    raise SystemExit(f"RECOGNITION_VIDEO_TOO_SHORT duration={duration:.3f}")
if frame_count < 30:
    raise SystemExit(f"RECOGNITION_VIDEO_TOO_FEW_FRAMES frames={frame_count}")
if width < 320 or height < 640:
    raise SystemExit(f"RECOGNITION_VIDEO_DIMENSIONS_INVALID dimensions={width}x{height}")

def mae(left: Image.Image, right: Image.Image) -> float:
    diff = ImageChops.difference(left, right)
    return sum(ImageStat.Stat(diff).mean) / 3.0

sample_times = []
timestamp = 0.5
while timestamp < duration - 0.2:
    sample_times.append(timestamp)
    timestamp += 1.0
live_frames = []
matched_frames = []
with tempfile.TemporaryDirectory() as temp_dir:
    for index, timestamp in enumerate(sample_times):
        frame_path = Path(temp_dir) / f"frame-{index:02d}.png"
        extracted = subprocess.run(
            [
                "ffmpeg", "-loglevel", "error", "-y", "-ss", f"{timestamp:.3f}",
                "-i", str(video_path), "-frames:v", "1", str(frame_path),
            ],
            check=False,
            capture_output=True,
            text=True,
        )
        if extracted.returncode != 0 or not frame_path.is_file():
            continue
        frame = Image.open(frame_path).convert("RGB")
        spread = sum(ImageStat.Stat(frame).stddev) / 3.0
        if spread >= 35.0:
            live_frames.append(timestamp)
        best_match = min(
            mae(frame, reference.resize(frame.size, Image.Resampling.LANCZOS))
            for reference in references
        )
        if best_match <= 70.0:
            matched_frames.append((timestamp, best_match))

validation_path.write_text(
    "Duration seconds: %.3f\n"
    "Dimensions: %sx%s\n"
    "Frame count: %s\n"
    "Non-uniform runtime frames: %s\n"
    "Runtime-reference matches: %s\n"
    "Match details: %s\n"
    % (
        duration,
        width,
        height,
        frame_count,
        len(live_frames),
        len(matched_frames),
        ", ".join(f"{timestamp:.1f}s/{score:.2f}" for timestamp, score in matched_frames),
    ),
    encoding="utf-8",
)
if len(live_frames) < 3:
    raise SystemExit(f"RECOGNITION_VIDEO_RUNTIME_FRAMES_INVALID count={len(live_frames)}")
if len(matched_frames) < 3:
    raise SystemExit(f"RECOGNITION_VIDEO_RUNTIME_MATCH_INVALID count={len(matched_frames)}")
print(
    "RECOGNITION_VIDEO_VALID "
    f"duration={duration:.3f} dimensions={width}x{height} "
    f"frames={frame_count} live_frames={len(live_frames)} "
    f"reference_matches={len(matched_frames)}"
)
PY
    if (( validation_rc != 0 )); then
      runtime_set_failure "EVIDENCE" "RECOGNITION_VIDEO_INVALID"
      return "$EXIT_FAIL_EVIDENCE"
    fi
  fi

  if ! python3 "$YUJIAN_REPO_ROOT/scripts/verify_recognition_runtime_evidence_v3_1.py" --evidence-dir "$output_dir"; then
    runtime_set_failure "EVIDENCE" "RECOGNITION_V3_1_EVIDENCE_INVALID"
    return "$EXIT_FAIL_EVIDENCE"
  fi
  return "$EXIT_PASS"
}
