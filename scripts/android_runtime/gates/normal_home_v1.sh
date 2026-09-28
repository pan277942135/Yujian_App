#!/usr/bin/env bash

gate_test_classes() {
  printf '%s\n' 'com.yujian.ai.NormalHomeRuntimeTest'
}

gate_collect_evidence() {
  local out="$YUJIAN_EVIDENCE_DIR/normal-home-v1"
  local remote="/sdcard/Android/data/$YUJIAN_APP_PACKAGE/files/normal_home_v1"
  mkdir -p "$out"
  "$YUJIAN_ADB_BIN" shell ls -la "$remote" > "$out/device_files.txt" 2>&1 || true
  "$YUJIAN_ADB_BIN" pull "$remote/." "$out/" > "$out/adb_pull.log" 2>&1 || {
    runtime_set_failure "EVIDENCE" "NORMAL_HOME_SCREENSHOT_PULL_FAILED"
    return "$EXIT_FAIL_EVIDENCE"
  }

  local missing=0 file
  for file in 01_single_16_9.png 02_single_19_5_9.png 03_multiple_20_9.png 04_multiple_21_9.png; do
    if [[ ! -s "$out/$file" ]]; then
      printf "MISSING_EVIDENCE=%s\n" "$out/$file" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
      missing=1
    fi
  done
  if (( missing != 0 )); then
    runtime_set_failure "EVIDENCE" "NORMAL_HOME_SCREENSHOT_EVIDENCE_MISSING"
    return "$EXIT_FAIL_EVIDENCE"
  fi

  if ! python3 "$YUJIAN_REPO_ROOT/scripts/build_normal_home_runtime_evidence.py" \
    --reference "$YUJIAN_REPO_ROOT/design/system/core_visual_v1/reference/normal_home_v1.png" \
    --runtime "$out/01_single_16_9.png" \
    --out-dir "$out" \
    --build-sha "$YUJIAN_BUILD_SHA"; then
    runtime_set_failure "EVIDENCE" "NORMAL_HOME_VISUAL_PARITY_FAILED"
    return "$EXIT_FAIL_EVIDENCE"
  fi

  if ! (
    set -euo pipefail
    "$YUJIAN_ADB_BIN" shell settings put global animator_duration_scale 1.0 || true
    "$YUJIAN_ADB_BIN" shell settings put global transition_animation_scale 1.0 || true
    "$YUJIAN_ADB_BIN" shell settings put global window_animation_scale 1.0 || true
    "$YUJIAN_ADB_BIN" shell wm size reset
    sleep 1
    "$YUJIAN_ADB_BIN" exec-out screencap -p > "$out/motion_start.png"
    "$YUJIAN_ADB_BIN" shell rm -f /sdcard/normal_home_motion_10s.mp4
    "$YUJIAN_ADB_BIN" shell screenrecord --size 320x640 --time-limit 10 /sdcard/normal_home_motion_10s.mp4
    "$YUJIAN_ADB_BIN" pull /sdcard/normal_home_motion_10s.mp4 "$out/normal_home_motion_10s.mp4" >/dev/null
    ffmpeg -loglevel error -y -ss 1.0 -i "$out/normal_home_motion_10s.mp4" -frames:v 1 "$out/motion_frame_1s.png"
    ffmpeg -loglevel error -y -ss 4.0 -i "$out/normal_home_motion_10s.mp4" -frames:v 1 "$out/motion_frame_4s.png"
    python3 - "$out/normal_home_motion_10s.mp4" "$out/motion_frame_1s.png" "$out/motion_frame_4s.png" <<'PY'
import subprocess, sys
from PIL import Image, ImageChops, ImageStat
video, first, second = sys.argv[1:]
duration = float(subprocess.check_output(
    ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=nw=1:nk=1", video],
    text=True,
).strip())
a = Image.open(first).convert("RGB")
b = Image.open(second).convert("RGB").resize(a.size)
diff = sum(ImageStat.Stat(ImageChops.difference(a, b)).mean) / 3.0
print(f"NORMAL_HOME_MOTION duration={duration:.3f} frame_mae={diff:.5f}")
if duration < 9.0 or diff < 0.015:
    raise SystemExit("NORMAL_HOME_MOTION_INVALID")
PY
    "$YUJIAN_ADB_BIN" shell wm size 1080x1920
  ); then
    runtime_set_failure "EVIDENCE" "NORMAL_HOME_MOTION_EVIDENCE_FAILED"
    return "$EXIT_FAIL_EVIDENCE"
  fi

  for file in visual_parity_report.json frozen_runtime_side_by_side.png frozen_runtime_blend.png normal_home_motion_10s.mp4 motion_frame_1s.png motion_frame_4s.png; do
    if [[ ! -s "$out/$file" ]]; then
      printf "MISSING_EVIDENCE=%s\n" "$out/$file" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
      missing=1
    fi
  done
  if (( missing != 0 )); then
    runtime_set_failure "EVIDENCE" "NORMAL_HOME_FINAL_EVIDENCE_MISSING"
    return "$EXIT_FAIL_EVIDENCE"
  fi
  return "$EXIT_PASS"
}
