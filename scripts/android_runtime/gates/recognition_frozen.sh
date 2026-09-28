#!/usr/bin/env bash

gate_test_classes() {
  printf '%s\n' 'com.yujian.ai.RecognitionFrozenFlowEmulatorTest'
}

# Instrumentation owns the screenshot permission for the Compose test Activity.
# The test stores its real runtime frames in this app-cache subdirectory; this
# gate pulls and encodes them after instrumentation has finalized the files.
YUJIAN_RECOGNITION_RUNTIME_FRAME_SOURCE="cache/recognition-evidence/runtime-video-frames"
YUJIAN_RECOGNITION_RUNTIME_FRAME_DIR="$YUJIAN_EVIDENCE_DIR/recognition-video-frames"
YUJIAN_RECOGNITION_CAPTURE_FRAME_RATE=10
YUJIAN_RECOGNITION_OUTPUT_NAME="recognition_processing_v1_1.mp4"

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

  local pulled_frames=0
  if [[ "${YUJIAN_CAPTURE_RECOGNITION_VIDEO:-1}" == "1" ]]; then
    mkdir -p "$YUJIAN_RECOGNITION_RUNTIME_FRAME_DIR"
    rm -f "$YUJIAN_RECOGNITION_RUNTIME_FRAME_DIR"/frame-*.png
    local missing_streak=0 index frame_name frame_path png_signature
    for index in $(seq 0 299); do
      frame_name="runtime_frame_$(printf '%05d' "$index").png"
      frame_path="$YUJIAN_RECOGNITION_RUNTIME_FRAME_DIR/frame-$(printf '%05d' "$index").png"
      "${YUJIAN_ADB_BIN}" exec-out run-as "$YUJIAN_APP_PACKAGE" cat \
        "$YUJIAN_RECOGNITION_RUNTIME_FRAME_SOURCE/$frame_name" \
        > "$frame_path" 2>/dev/null || true
      if [[ -s "$frame_path" ]]; then
        # adb exec-out/run-as can surface a missing-file diagnostic through
        # the captured stream. Never let non-empty error text enter ffmpeg as
        # if it were a PNG; the instrumentation names frames contiguously, so
        # the first invalid frame terminates the sequence.
        png_signature="$(head -c 8 "$frame_path" | od -An -t x1 | tr -d '[:space:]')"
        if [[ "$png_signature" == "89504e470d0a1a0a" ]]; then
          pulled_frames=$((pulled_frames + 1))
          missing_streak=0
        else
          rm -f "$frame_path"
          break
        fi
      else
        rm -f "$frame_path"
        if (( pulled_frames > 0 )); then
          missing_streak=$((missing_streak + 1))
          if (( missing_streak >= 2 )); then
            break
          fi
        fi
      fi
    done

    if (( pulled_frames >= 2 )); then
      ffmpeg -loglevel error -y \
        -framerate "$YUJIAN_RECOGNITION_CAPTURE_FRAME_RATE" \
        -i "$YUJIAN_RECOGNITION_RUNTIME_FRAME_DIR/frame-%05d.png" \
        -vf 'scale=320:640:flags=lanczos' \
        -c:v libx264 -preset veryfast -pix_fmt yuv420p \
        "$output_dir/$YUJIAN_RECOGNITION_OUTPUT_NAME" \
        > "$YUJIAN_EVIDENCE_DIR/recognition_video_encode.log" 2>&1 || true
    fi
  else
    # Contract tests use a fake adb and exercise classification, not video
    # encoding. Keep their deterministic evidence fixture available.
    local recording_bytes=0 attempt
    for attempt in $(seq 1 20); do
      recording_bytes="$(${YUJIAN_ADB_BIN} shell \
        "wc -c < /sdcard/recognition_processing_runtime_host.mp4 2>/dev/null || echo 0" \
        2>/dev/null | tr -d '\r' | tail -n 1)"
      if [[ "$recording_bytes" =~ ^[0-9]+$ ]] && (( recording_bytes > 1024 )); then
        break
      fi
      sleep 0.25
    done
    if [[ "$recording_bytes" =~ ^[0-9]+$ ]] && (( recording_bytes > 1024 )); then
      "${YUJIAN_ADB_BIN}" pull /sdcard/recognition_processing_runtime_host.mp4 \
        "$output_dir/$YUJIAN_RECOGNITION_OUTPUT_NAME" >/dev/null 2>&1 || true
    fi
  fi
  if [[ ! -s "$output_dir/$YUJIAN_RECOGNITION_OUTPUT_NAME" ]]; then
    printf 'RUNTIME_FRAME_COUNT=%s\n' "$pulled_frames" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
    rm -f "$output_dir/$YUJIAN_RECOGNITION_OUTPUT_NAME"
  fi

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
