#!/usr/bin/env bash

gate_test_classes() {
  printf '%s\n' 'com.yujian.ai.RecognitionFrozenFlowEmulatorTest'
}

# UiAutomation's screenrecord command can outlive the instrumentation PFD and
# leave a syntactically valid but blank API 28 MP4. The runtime harness owns a
# separate host-side recorder so evidence collection controls its lifecycle.
YUJIAN_RECOGNITION_HOST_RECORDING_PATH="/sdcard/recognition_processing_runtime_host.mp4"
YUJIAN_RECOGNITION_HOST_RECORDING_PID=""
YUJIAN_RECOGNITION_HOST_RECORDING_LOG="$YUJIAN_EVIDENCE_DIR/recognition_screenrecord.log"

gate_prepare_instrumentation() {
  mkdir -p "$YUJIAN_EVIDENCE_DIR"
  : > "$YUJIAN_RECOGNITION_HOST_RECORDING_LOG"

  local display_size record_size="320x640"
  display_size="$(${YUJIAN_ADB_BIN} shell wm size 2>/dev/null | tr -d '\r' | tail -n 1)"
  printf 'HOST_RECORDING_PATH=%s\nHOST_DISPLAY_SIZE=%s\nHOST_RECORDING_SIZE=%s\n' \
    "$YUJIAN_RECOGNITION_HOST_RECORDING_PATH" "$display_size" "$record_size" \
    >> "$YUJIAN_RECOGNITION_HOST_RECORDING_LOG"

  "${YUJIAN_ADB_BIN}" shell rm -f "$YUJIAN_RECOGNITION_HOST_RECORDING_PATH" \
    >> "$YUJIAN_RECOGNITION_HOST_RECORDING_LOG" 2>&1 || true
  (
    "${YUJIAN_ADB_BIN}" shell screenrecord \
      --size "$record_size" \
      --bit-rate 4000000 \
      --time-limit 15 \
      --verbose \
      "$YUJIAN_RECOGNITION_HOST_RECORDING_PATH"
  ) >> "$YUJIAN_RECOGNITION_HOST_RECORDING_LOG" 2>&1 &
  YUJIAN_RECOGNITION_HOST_RECORDING_PID=$!
  printf 'HOST_RECORDING_PID=%s\n' "$YUJIAN_RECOGNITION_HOST_RECORDING_PID" \
    >> "$YUJIAN_RECOGNITION_HOST_RECORDING_LOG"
}

gate_finalize_instrumentation() {
  if [[ -z "$YUJIAN_RECOGNITION_HOST_RECORDING_PID" ]]; then
    return 0
  fi

  local attempt
  for attempt in $(seq 1 50); do
    if ! kill -0 "$YUJIAN_RECOGNITION_HOST_RECORDING_PID" 2>/dev/null; then
      break
    fi
    sleep 0.5
  done
  if kill -0 "$YUJIAN_RECOGNITION_HOST_RECORDING_PID" 2>/dev/null; then
    printf 'HOST_RECORDING_CLIENT_TIMEOUT=25s\n' >> "$YUJIAN_RECOGNITION_HOST_RECORDING_LOG"
    kill "$YUJIAN_RECOGNITION_HOST_RECORDING_PID" 2>/dev/null || true
  fi
  wait "$YUJIAN_RECOGNITION_HOST_RECORDING_PID" 2>/dev/null || true
  printf 'HOST_RECORDING_CLIENT_FINALIZED=1\n' >> "$YUJIAN_RECOGNITION_HOST_RECORDING_LOG"
}

gate_collect_evidence() {
  local output_dir="$YUJIAN_EVIDENCE_DIR/ui_rework_v1/recognition"
  mkdir -p "$output_dir"

  local name
  for name in \
    01_capture_transition.png \
    02_ai_understanding.png \
    03_fish_highlight.png \
    04_fish_identifying.png \
    05_result_high.png \
    06_result_medium.png \
    07_result_low.png \
    08_error_no_fish.png \
    09_error_image_quality.png
  do
    "${YUJIAN_ADB_BIN}" exec-out run-as "$YUJIAN_APP_PACKAGE" cat "cache/recognition-evidence/${name}" \
      > "$output_dir/$name" 2>/dev/null || true
    if [[ ! -s "$output_dir/$name" ]]; then
      rm -f "$output_dir/$name"
    fi
  done

  "${YUJIAN_ADB_BIN}" exec-out run-as "$YUJIAN_APP_PACKAGE" cat \
    "cache/recognition-evidence/recognition_processing_timing.txt" \
    > "$output_dir/recognition_processing_timing.txt" 2>/dev/null || true
  if [[ ! -s "$output_dir/recognition_processing_timing.txt" ]]; then
    rm -f "$output_dir/recognition_processing_timing.txt"
  fi

  # The host-side recorder is finalized after instrumentation and before this
  # evidence phase. Keep a short device-side wait for API 28 file visibility.
  local recording_bytes=0 attempt
  for attempt in $(seq 1 20); do
    recording_bytes="$(${YUJIAN_ADB_BIN} shell \
      "wc -c < ${YUJIAN_RECOGNITION_HOST_RECORDING_PATH} 2>/dev/null || echo 0" \
      2>/dev/null | tr -d '\r' | tail -n 1)"
    if [[ "$recording_bytes" =~ ^[0-9]+$ ]] && (( recording_bytes > 1024 )); then
      break
    fi
    sleep 0.25
  done

  if [[ "$recording_bytes" =~ ^[0-9]+$ ]] && (( recording_bytes > 1024 )); then
    "${YUJIAN_ADB_BIN}" pull "$YUJIAN_RECOGNITION_HOST_RECORDING_PATH" \
      "$output_dir/recognition_processing_v1_1.mp4" >/dev/null 2>&1 || true
  fi
  if [[ ! -s "$output_dir/recognition_processing_v1_1.mp4" ]]; then
    printf 'RECORDING_BYTES=%s\n' "$recording_bytes" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
    rm -f "$output_dir/recognition_processing_v1_1.mp4"
  fi

  if [[ -s "$output_dir/recognition_processing_v1_1.mp4" && "${YUJIAN_VALIDATE_RECOGNITION_VIDEO:-1}" == "1" ]]; then
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
video_path = output_dir / "recognition_processing_v1_1.mp4"
validation_path = output_dir / "recognition_processing_video_validation.txt"
references = [
    Image.open(path).convert("RGB")
    for path in sorted(output_dir.glob("0*.png"))
]
if len(references) != 9:
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

sample_times = [0.5 + index for index in range(12) if 0.5 + index < duration - 0.2]
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

  local missing=0
  for name in \
    01_capture_transition.png \
    02_ai_understanding.png \
    03_fish_highlight.png \
    04_fish_identifying.png \
    05_result_high.png \
    06_result_medium.png \
    07_result_low.png \
    08_error_no_fish.png \
    09_error_image_quality.png \
    recognition_processing_timing.txt \
    recognition_processing_v1_1.mp4
  do
    if [[ ! -s "$output_dir/$name" ]]; then
      printf 'MISSING_EVIDENCE=%s\n' "$name" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
      missing=1
    fi
  done

  if (( missing != 0 )); then
    runtime_set_failure "EVIDENCE" "RECOGNITION_EVIDENCE_MISSING"
    return "$EXIT_FAIL_EVIDENCE"
  fi
  return "$EXIT_PASS"
}
