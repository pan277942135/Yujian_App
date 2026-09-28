#!/usr/bin/env bash

gate_test_classes() {
  printf '%s\n' 'com.yujian.ai.EmptyHomeV2RuntimeContractTest'
}

gate_collect_evidence() {
  local home_dir="$YUJIAN_EVIDENCE_DIR/home-evidence"
  mkdir -p "$home_dir"

  if ! (
    set -euo pipefail

    # Emulator runner disables animations for deterministic instrumentation.
    # Empty Home parity must observe the production motion contract.
    "${YUJIAN_ADB_BIN}" shell settings put global animator_duration_scale 1.0 || true
    "${YUJIAN_ADB_BIN}" shell settings put global transition_animation_scale 1.0 || true
    "${YUJIAN_ADB_BIN}" shell settings put global window_animation_scale 1.0 || true

    "${YUJIAN_ADB_BIN}" shell pm clear "$YUJIAN_APP_PACKAGE"
    "${YUJIAN_ADB_BIN}" shell wm size 1080x1920
    "${YUJIAN_ADB_BIN}" logcat -c

    # Launch the production activity, then wait only for the real resumed
    # Activity. Compose text is intentionally NOT read through UIAutomator:
    # that bridge is unreliable for this screen and previously produced false
    # FAIL_EVIDENCE while the page was visibly rendered.
    "${YUJIAN_ADB_BIN}" shell monkey -p "$YUJIAN_APP_PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null

    home_resumed=0
    for attempt in $(seq 1 20); do
      resumed="$("${YUJIAN_ADB_BIN}" shell dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|ResumedActivity' | head -n 1 || true)"
      printf 'EMPTY_HOME_ACTIVITY_READINESS attempt=%s resumed=%s\n' "$attempt" "$resumed"
      if [[ "$resumed" == *"$YUJIAN_APP_PACKAGE/com.yujian.ai.MainActivity"* ]]; then
        home_resumed=1
        break
      fi
      sleep 1
    done

    if (( home_resumed != 1 )); then
      echo 'EMPTY_HOME_ACTIVITY_NOT_RESUMED' >&2
      "${YUJIAN_ADB_BIN}" exec-out screencap -p > "$home_dir/startup_not_ready.png" || true
      "${YUJIAN_ADB_BIN}" logcat -d -b all -v threadtime > "$home_dir/startup_logcat.txt" || true
      exit 1
    fi

    # Compose can report the Activity as resumed before decoded scene assets
    # have reached the first real frame. Wait on pixels/parity only; do not
    # use UIAutomator text as a readiness signal for this Compose screen.
    render_ready=0
    for attempt in $(seq 1 20); do
      candidate="$home_dir/runtime_static_candidate.png"
      "${YUJIAN_ADB_BIN}" exec-out screencap -p > "$candidate"
      if python3 - "$candidate" "$YUJIAN_REPO_ROOT/design/system/core_visual_v1/reference/empty_home_v2.png" <<'PY'
from PIL import Image, ImageStat
import sys
from PIL import ImageChops

img = Image.open(sys.argv[1]).convert("RGB")
stat = ImageStat.Stat(img)
mean = sum(stat.mean) / 3.0
spread = sum(stat.stddev) / 3.0
if (mean > 245 and spread < 8) or (mean < 8 and spread < 8):
    raise SystemExit(f"EMPTY_HOME_BLANK_FRAME mean={mean:.2f} spread={spread:.2f}")

reference = Image.open(sys.argv[2]).convert("RGB").resize(img.size, Image.Resampling.LANCZOS)
diff = ImageChops.difference(reference, img)
histogram = diff.histogram()
mae = sum(index % 256 * count for index, count in enumerate(histogram)) / (img.width * img.height * 3)
if mae > 40:
    raise SystemExit(f"EMPTY_HOME_PARITY_NOT_READY mean={mean:.2f} spread={spread:.2f} mae={mae:.4f}")
print(f"EMPTY_HOME_FRAME_READY mean={mean:.2f} spread={spread:.2f} mae={mae:.4f}")
PY
      then
        mv "$candidate" "$home_dir/runtime_static.png"
        render_ready=1
        break
      fi
      printf 'EMPTY_HOME_RENDER_SETTLE attempt=%s\n' "$attempt"
      sleep 1
    done

    if (( render_ready != 1 )); then
      echo 'EMPTY_HOME_RENDER_NOT_READY'
      "${YUJIAN_ADB_BIN}" exec-out screencap -p > "$home_dir/render_not_ready.png" || true
      exit 1
    fi

    cp "$home_dir/runtime_static.png" "$home_dir/01_empty_home_static.png"
    sleep 4
    "${YUJIAN_ADB_BIN}" exec-out screencap -p > "$home_dir/runtime_4s.png"

    "${YUJIAN_ADB_BIN}" shell rm -f /sdcard/full_runtime_15s.mp4
    "${YUJIAN_ADB_BIN}" shell screenrecord --time-limit 15 /sdcard/full_runtime_15s.mp4
    "${YUJIAN_ADB_BIN}" pull /sdcard/full_runtime_15s.mp4 "$home_dir/full_runtime_15s.mp4" >/dev/null
    cp "$home_dir/full_runtime_15s.mp4" "$home_dir/Empty_Home_V2_Parity_Runtime.mp4"

    ffmpeg -y -ss 0.5 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 -vf 'crop=iw*0.39:ih*0.27:iw*0.31:ih*0.69' "$home_dir/02_camera_button_closeup.png"
    ffmpeg -y -ss 0.5 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 -vf 'crop=iw/3:ih*0.22:iw*0.324:ih*0.484' "$home_dir/03_bobber_water_contact.png"
    ffmpeg -y -ss 0.2 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 -vf 'crop=iw/3:ih*0.22:iw*0.324:ih*0.484' "$home_dir/04_ripple_visible.png"
    ffmpeg -y -ss 2.5 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 "$home_dir/05_gold_rim_sweep_before.png"
    ffmpeg -y -ss 3.7 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 "$home_dir/06_gold_rim_sweep_peak.png"
    ffmpeg -y -ss 5.0 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 "$home_dir/07_gold_rim_sweep_after.png"
    ffmpeg -y -ss 1.0 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 -vf 'crop=iw*0.39:ih*0.27:iw*0.31:ih*0.69' "$home_dir/08_camera_breath_min.png"
    ffmpeg -y -ss 3.5 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 -vf 'crop=iw*0.39:ih*0.27:iw*0.31:ih*0.69' "$home_dir/09_camera_breath_max.png"
    ffmpeg -y -ss 2.4 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 "$home_dir/10_sun_particle_frame.png"
    ffmpeg -y -ss 0.2 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 "$home_dir/11_cloud_start.png"

    sleep 50
    "${YUJIAN_ADB_BIN}" exec-out screencap -p > "$home_dir/12_cloud_later.png"

    ffmpeg -y -i "$home_dir/full_runtime_15s.mp4" -t 10 -c copy "$home_dir/environment_motion.mp4"
    ffmpeg -y -i "$home_dir/full_runtime_15s.mp4" -vf 'crop=iw/3:ih*0.22:iw*0.324:ih*0.484' -an "$home_dir/bobber_ripple_crop.mp4"
    ffmpeg -y -i "$home_dir/full_runtime_15s.mp4" -vf 'crop=iw*0.39:ih*0.27:iw*0.31:ih*0.69' -an "$home_dir/camera_motion.mp4"

    python3 "$YUJIAN_REPO_ROOT/scripts/build_empty_home_runtime_evidence.py" --evidence-dir "$home_dir" --build-sha "$YUJIAN_BUILD_SHA"
    parity_status="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["status"])' "$home_dir/visual_parity_report.json")"
    if [[ "$parity_status" != "PASS" ]]; then
      cat "$home_dir/visual_parity_report.json" >&2
      echo "EMPTY_HOME_VISUAL_PARITY_FAILED status=$parity_status" >&2
      exit 1
    fi
    cp "$YUJIAN_REPO_ROOT/design/pages/home/empty_home/source/frozen/Empty_Home_Final_Design_V2_normalized_1080x1920.png" "$home_dir/static_reference.png"
  ); then
    runtime_set_failure "EVIDENCE" "EMPTY_HOME_V2_EVIDENCE_COMMAND_FAILED"
    return "$EXIT_FAIL_EVIDENCE"
  fi

  local missing=0 file
  for file in \
    "$home_dir/runtime_static.png" \
    "$home_dir/runtime_4s.png" \
    "$home_dir/Empty_Home_V2_Parity_Runtime.mp4" \
    "$home_dir/visual_parity_report.json" \
    "$home_dir/runtime_debug.json" \
    "$home_dir/static_reference.png"
  do
    if [[ ! -s "$file" ]]; then
      printf 'MISSING_EVIDENCE=%s\n' "$file" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
      missing=1
    fi
  done

  if (( missing != 0 )); then
    runtime_set_failure "EVIDENCE" "EMPTY_HOME_V2_EVIDENCE_MISSING"
    return "$EXIT_FAIL_EVIDENCE"
  fi
  return "$EXIT_PASS"
}
