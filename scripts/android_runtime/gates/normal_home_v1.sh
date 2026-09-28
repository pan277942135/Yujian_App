#!/usr/bin/env bash

gate_test_classes() {
  printf '%s\n' 'com.yujian.ai.NormalHomeRuntimeContractTest'
}

normal_home_run_seed() {
  local method="$1"
  local log="$2"
  timeout 60s "$YUJIAN_ADB_BIN" shell am instrument -w -r \
    -e class "com.yujian.ai.HomeVisualEvidenceSeedTest#$method" \
    "$YUJIAN_INSTRUMENTATION_TARGET" > "$log" 2>&1
  local rc=$?
  if (( rc != 0 )) || grep -Eiq 'FAILURES!!!|INSTRUMENTATION_FAILED|Assertion(Error|FailedError)|Process (crashed|has died)|Process .* (crashed|has died)|shortMsg=Process crashed' "$log"; then
    cat "$log" >&2
    return 1
  fi
  grep -Eq 'INSTRUMENTATION_CODE:[[:space:]]*0|OK \([0-9]+ test' "$log"
}

normal_home_launch_app() {
  "$YUJIAN_ADB_BIN" shell am force-stop "$YUJIAN_APP_PACKAGE"
  "$YUJIAN_ADB_BIN" shell monkey -p "$YUJIAN_APP_PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null

  local attempt resumed dump
  for attempt in $(seq 1 20); do
    resumed="$("$YUJIAN_ADB_BIN" shell dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|ResumedActivity' | head -n 1 || true)"
    "$YUJIAN_ADB_BIN" shell uiautomator dump /sdcard/normal_home_window.xml >/dev/null 2>&1 || true
    dump="$("$YUJIAN_ADB_BIN" shell cat /sdcard/normal_home_window.xml 2>/dev/null || true)"
    printf 'NORMAL_HOME_ACTIVITY_READINESS attempt=%s resumed=%s recent_visible=%s\n' \
      "$attempt" "$resumed" "$([[ "$dump" == *"最近鱼获"* ]] && echo 1 || echo 0)"
    if [[ "$resumed" == *"$YUJIAN_APP_PACKAGE/com.yujian.ai.MainActivity"* && "$dump" == *"最近鱼获"* ]]; then
      sleep 1
      return 0
    fi
    sleep 1
  done
  echo 'NORMAL_HOME_CONTENT_NOT_READY' >&2
  "$YUJIAN_ADB_BIN" exec-out screencap -p > "$YUJIAN_EVIDENCE_DIR/normal-home-v1/content_not_ready.png" || true
  return 1
}

gate_collect_evidence() {
  local out="$YUJIAN_EVIDENCE_DIR/normal-home-v1"
  mkdir -p "$out"

  (
    set -euo pipefail
    "$YUJIAN_ADB_BIN" shell settings put global animator_duration_scale 1.0 || true
    "$YUJIAN_ADB_BIN" shell settings put global transition_animation_scale 1.0 || true
    "$YUJIAN_ADB_BIN" shell settings put global window_animation_scale 1.0 || true

    "$YUJIAN_ADB_BIN" shell wm size 1080x1920
    normal_home_run_seed seedSingleGuestCatch "$out/seed_single.log"
    normal_home_launch_app
    "$YUJIAN_ADB_BIN" exec-out screencap -p > "$out/normal_home_frozen_geometry.png"

    "$YUJIAN_ADB_BIN" shell wm size 1080x2340
    normal_home_run_seed seedSingleGuestCatch "$out/seed_single_19_5_9.log"
    normal_home_launch_app
    "$YUJIAN_ADB_BIN" exec-out screencap -p > "$out/normal_home_single_19_5_9.png"

    "$YUJIAN_ADB_BIN" shell wm size 1080x2400
    normal_home_run_seed seedMultipleGuestCatches "$out/seed_multiple_20_9.log"
    normal_home_launch_app
    "$YUJIAN_ADB_BIN" exec-out screencap -p > "$out/normal_home_multiple_20_9.png"

    "$YUJIAN_ADB_BIN" shell wm size 1080x2520
    sleep 2
    "$YUJIAN_ADB_BIN" exec-out screencap -p > "$out/normal_home_multiple_21_9.png"

    python3 - "$YUJIAN_REPO_ROOT/design/system/core_visual_v1/reference/normal_home_v1.png" "$out/normal_home_frozen_geometry.png" "$out" <<'PY'
from __future__ import annotations
import json
import sys
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw, ImageStat

reference_path = Path(sys.argv[1])
runtime_path = Path(sys.argv[2])
out = Path(sys.argv[3])
reference = Image.open(reference_path).convert("RGB")
runtime = Image.open(runtime_path).convert("RGB")
runtime = runtime.resize(reference.size, Image.Resampling.LANCZOS)

def mae(a, b):
    return sum(ImageStat.Stat(ImageChops.difference(a, b)).mean) / 3.0

regions = {
    "header": (0.04, 0.03, 0.92, 0.13),
    "stats": (0.06, 0.13, 0.88, 0.12),
    "recent_header": (0.06, 0.20, 0.88, 0.10),
    "card_frame": (0.12, 0.26, 0.76, 0.34),
    "cta": (0.22, 0.62, 0.56, 0.20),
}
thresholds = {
    "header": 72.0,
    "stats": 72.0,
    "recent_header": 72.0,
    "card_frame": 88.0,
    "cta": 78.0,
}
metrics = {}
for name, (x, y, w, h) in regions.items():
    box = (
        int(reference.width*x), int(reference.height*y),
        int(reference.width*(x+w)), int(reference.height*(y+h)),
    )
    metrics[name] = round(mae(reference.crop(box), runtime.crop(box)), 4)
status = "PASS" if all(metrics[k] <= thresholds[k] for k in thresholds) else "FAIL"
report = {
    "status": status,
    "authority": "normal_home_v1.png",
    "authority_sha256": "6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377",
    "metrics_mae": metrics,
    "thresholds": thresholds,
}
(out / "visual_parity_report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
canvas = Image.new("RGB", (reference.width * 2, reference.height), "white")
canvas.paste(reference, (0, 0))
canvas.paste(runtime, (reference.width, 0))
draw = ImageDraw.Draw(canvas)
draw.text((12, 12), "Frozen", fill="black")
draw.text((reference.width + 12, 12), "Runtime", fill="black")
canvas.save(out / "normal_home_frozen_runtime_side_by_side.png")
print("NORMAL_HOME_VISUAL_PARITY", json.dumps(report, ensure_ascii=False))
if status != "PASS":
    raise SystemExit("NORMAL_HOME_VISUAL_PARITY_FAILED")
PY

    "$YUJIAN_ADB_BIN" shell wm size reset
    sleep 1
    "$YUJIAN_ADB_BIN" shell rm -f /sdcard/normal_home_motion.mp4
    "$YUJIAN_ADB_BIN" shell screenrecord --size 320x640 --time-limit 10 /sdcard/normal_home_motion.mp4 > "$out/screenrecord.log" 2>&1
    "$YUJIAN_ADB_BIN" pull /sdcard/normal_home_motion.mp4 "$out/Normal_Home_V1_Runtime_10s.mp4" >/dev/null
    duration="$(ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 "$out/Normal_Home_V1_Runtime_10s.mp4")"
    python3 - "$duration" <<'PY'
import sys
duration=float(sys.argv[1])
print(f"NORMAL_HOME_MOTION_DURATION={duration:.3f}")
if duration < 9.5:
    raise SystemExit("NORMAL_HOME_MOTION_TOO_SHORT")
PY
  )
  local rc=$?
  if (( rc != 0 )); then
    runtime_set_failure "EVIDENCE" "NORMAL_HOME_V1_EVIDENCE_FAILED"
    return "$EXIT_FAIL_EVIDENCE"
  fi

  local missing=0 file
  for file in \
    "$out/normal_home_frozen_geometry.png" \
    "$out/normal_home_single_19_5_9.png" \
    "$out/normal_home_multiple_20_9.png" \
    "$out/normal_home_multiple_21_9.png" \
    "$out/normal_home_frozen_runtime_side_by_side.png" \
    "$out/visual_parity_report.json" \
    "$out/Normal_Home_V1_Runtime_10s.mp4"
  do
    if [[ ! -s "$file" ]]; then
      printf 'MISSING_EVIDENCE=%s\n' "$file" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
      missing=1
    fi
  done
  if (( missing != 0 )); then
    runtime_set_failure "EVIDENCE" "NORMAL_HOME_V1_EVIDENCE_MISSING"
    return "$EXIT_FAIL_EVIDENCE"
  fi
  return "$EXIT_PASS"
}
