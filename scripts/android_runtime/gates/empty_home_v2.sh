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

    # Resolve through the manifest launcher instead of hard-coding the Kotlin Activity package.
    # Then wait for actual Home semantics; a running pid can still be only the startup window.
    "${YUJIAN_ADB_BIN}" shell monkey -p "$YUJIAN_APP_PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null

    home_ready=0
    for attempt in $(seq 1 30); do
      "${YUJIAN_ADB_BIN}" shell uiautomator dump /sdcard/empty_home_window.xml >/dev/null 2>&1 || true
      window_xml="$("${YUJIAN_ADB_BIN}" shell cat /sdcard/empty_home_window.xml 2>/dev/null | tr -d '\r' || true)"
      resumed="$("${YUJIAN_ADB_BIN}" shell dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|ResumedActivity' | head -n 1 || true)"
      printf 'EMPTY_HOME_UI_READINESS attempt=%s resumed=%s\n' "$attempt" "$resumed"
      if [[ "$window_xml" == *"对准鱼获，拍一张"* ]] || [[ "$window_xml" == *"现在，轮到你记录第一条鱼"* ]]; then
        home_ready=1
        break
      fi
      sleep 1
    done

    if (( home_ready != 1 )); then
      echo 'EMPTY_HOME_UI_NOT_READY' >&2
      "${YUJIAN_ADB_BIN}" exec-out screencap -p > "$home_dir/startup_not_ready.png" || true
      "${YUJIAN_ADB_BIN}" logcat -d -b all -v threadtime > "$home_dir/startup_logcat.txt" || true
      exit 1
    fi

    sleep 1
    "${YUJIAN_ADB_BIN}" exec-out screencap -p > "$home_dir/runtime_static.png"
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
