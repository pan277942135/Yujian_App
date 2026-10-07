#!/usr/bin/env bash

gate_test_classes() {
  printf '%s\n' 'com.yujian.ai.RecognitionFrozenFlowEmulatorTest,com.yujian.ai.RecognitionImageStoreTest,com.yujian.ai.PipelineTraceTest,com.yujian.ai.DetectorOrientationRetryRuntimeTest'
}
# Video evidence is captured outside instrumentation so observation cannot
# perturb Compose timing on API28. Instrumentation owns only semantic/timing
# assertions and single proof screenshots.
YUJIAN_RECOGNITION_DEVICE_RECORDING="/sdcard/recognition_processing_runtime_host.mp4"
YUJIAN_RECOGNITION_OUTPUT_NAME="recognition_processing_v1_2.mp4"

gate_before_instrumentation() {
  if [[ "${YUJIAN_CAPTURE_RECOGNITION_VIDEO:-1}" != "1" ]]; then
    return 0
  fi
  "${YUJIAN_ADB_BIN}" shell "rm -f '${YUJIAN_RECOGNITION_DEVICE_RECORDING}'" >/dev/null 2>&1 || true
  "${YUJIAN_ADB_BIN}" shell \
    "screenrecord --size 320x640 --bit-rate 4000000 --time-limit 45 '${YUJIAN_RECOGNITION_DEVICE_RECORDING}' >/dev/null 2>&1 &" \
    >/dev/null 2>&1 || true
  sleep 0.5
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

  local name
  for name in \
    01_image_recognizing_early.png \
    02_image_recognizing_late.png \
    03_fish_located.png \
    04_species_recognizing.png \
    05_resolve.png \
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
    "cache/recognition-evidence/recognition_processing_timing_v1_2.txt" \
    > "$output_dir/recognition_processing_timing_v1_2.txt" 2>/dev/null || true
  if [[ ! -s "$output_dir/recognition_processing_timing_v1_2.txt" ]]; then
    rm -f "$output_dir/recognition_processing_timing_v1_2.txt"
  fi

  "${YUJIAN_ADB_BIN}" exec-out run-as "$YUJIAN_APP_PACKAGE" cat \
    "cache/recognition-evidence/recognition_production_flow_trace_v1_2.json" \
    > "$output_dir/recognition_production_flow_trace_v1_2.json" 2>/dev/null || true
  if [[ ! -s "$output_dir/recognition_production_flow_trace_v1_2.json" ]]; then
    rm -f "$output_dir/recognition_production_flow_trace_v1_2.json"
  fi

  for name in recognition_motion_trace_v1_2.json recognition_accessibility_trace_v1_2.json recognition_visual_qa_v1_3.json fish_focus_bbox_mapping.json recognition_visual_parity_v1_2.json recognition_visual_parity_contact_sheet_v1_2.png portrait_low_confidence_original.jpg portrait_low_confidence_classifier_crop.png portrait_low_confidence_model_input_224.png portrait_low_confidence_inference_report.txt portrait_low_confidence_class_map.json portrait_low_confidence_diagnostic.json; do
    "${YUJIAN_ADB_BIN}" exec-out run-as "$YUJIAN_APP_PACKAGE" cat \
      "cache/recognition-evidence/${name}" > "$output_dir/${name}" 2>/dev/null || true
    if [[ ! -s "$output_dir/${name}" ]]; then rm -f "$output_dir/${name}"; fi
  done

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

  if [[ -s "$output_dir/01_image_recognizing_early.png" &&
        -s "$output_dir/02_image_recognizing_late.png" &&
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
video_path = output_dir / "recognition_processing_v1_2.mp4"
validation_path = output_dir / "recognition_processing_video_validation.txt"
reference_names = [
    "01_image_recognizing_early.png",
    "02_image_recognizing_late.png",
    "03_fish_located.png",
    "04_species_recognizing.png",
    "05_resolve.png",
]
references = [Image.open(output_dir / name).convert("RGB") for name in reference_names]
if len(references) != 5:
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

  if ! python3 scripts/verify_recognition_runtime_evidence_v1_2.py --evidence-dir "$output_dir"; then
    runtime_set_failure "EVIDENCE" "RECOGNITION_V1_2_EVIDENCE_INVALID"
    return "$EXIT_FAIL_EVIDENCE"
  fi
  return "$EXIT_PASS"
}
