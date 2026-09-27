#!/usr/bin/env bash

gate_test_classes() {
  printf '%s\n' \
    'com.yujian.ai.InferenceParityTest,com.yujian.ai.DetectorGoldenParityTest,com.yujian.ai.PipelineTraceTest,com.yujian.ai.FishDetectionQualityGateTest,com.yujian.ai.RecognitionRuntimeAssetContractTest,com.yujian.ai.RecognitionVisualRuntimeTest,com.yujian.ai.EmptyHomeV2RuntimeContractTest,com.yujian.ai.ui.designsystem.DesignSystemComponentPreviewTest'
}

gate_collect_evidence() {
  local home_dir="$YUJIAN_EVIDENCE_DIR/home-evidence"
  local design_dir="$YUJIAN_EVIDENCE_DIR/designsystem-evidence"
  local app_apk="$YUJIAN_APP_APK"
  local build_sha="$YUJIAN_BUILD_SHA"

  if ! (
    set -euo pipefail
    mkdir -p "$home_dir" "$design_dir"

    # The shared capture script is evidence-only.  The frozen APK was already
    # installed by the unified Harness.
    DESIGNSYSTEM_CAPTURE_SETTLE_SECONDS=5 \
      DESIGNSYSTEM_DISMISS_SYSTEM_DIALOGS=1 \
      bash "$YUJIAN_REPO_ROOT/scripts/capture_designsystem_runtime_evidence.sh" \
        "$app_apk" "$design_dir"

    "${YUJIAN_ADB_BIN}" shell settings put global animator_duration_scale 1.0 || true
    "${YUJIAN_ADB_BIN}" shell settings put global transition_animation_scale 1.0 || true
    "${YUJIAN_ADB_BIN}" shell settings put global window_animation_scale 1.0 || true
    "${YUJIAN_ADB_BIN}" shell pm clear "$YUJIAN_APP_PACKAGE"
    "${YUJIAN_ADB_BIN}" shell wm size 1080x1920
    "${YUJIAN_ADB_BIN}" logcat -c
    "${YUJIAN_ADB_BIN}" shell monkey -p "$YUJIAN_APP_PACKAGE" -c android.intent.category.LAUNCHER 1
    sleep 5
    if ! "${YUJIAN_ADB_BIN}" shell pidof "$YUJIAN_APP_PACKAGE" | grep -q '[0-9]'; then
      echo 'EMPTY_HOME_APP_NOT_RUNNING' >&2
      "${YUJIAN_ADB_BIN}" logcat -d -b all -v threadtime | tail -n 400 >&2
      exit 1
    fi
    "${YUJIAN_ADB_BIN}" exec-out screencap -p > "$home_dir/runtime_static.png"
    cp "$home_dir/runtime_static.png" "$home_dir/01_empty_home_static.png"
    sleep 4
    "${YUJIAN_ADB_BIN}" exec-out screencap -p > "$home_dir/runtime_4s.png"
    "${YUJIAN_ADB_BIN}" shell screenrecord --time-limit 15 /sdcard/full_runtime_15s.mp4
    "${YUJIAN_ADB_BIN}" pull /sdcard/full_runtime_15s.mp4 "$home_dir/full_runtime_15s.mp4" >/dev/null
    cp "$home_dir/full_runtime_15s.mp4" "$home_dir/Empty_Home_V2_Parity_Runtime.mp4"

    ffmpeg -y -ss 0.5 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 \
      -vf 'crop=iw*0.39:ih*0.27:iw*0.31:ih*0.69' "$home_dir/02_camera_button_closeup.png"
    ffmpeg -y -ss 0.5 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 \
      -vf 'crop=iw/3:ih*0.22:iw*0.324:ih*0.484' "$home_dir/03_bobber_water_contact.png"
    ffmpeg -y -ss 0.2 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 \
      -vf 'crop=iw/3:ih*0.22:iw*0.324:ih*0.484' "$home_dir/04_ripple_visible.png"
    ffmpeg -y -ss 2.5 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 "$home_dir/05_gold_rim_sweep_before.png"
    ffmpeg -y -ss 3.7 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 "$home_dir/06_gold_rim_sweep_peak.png"
    ffmpeg -y -ss 5.0 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 "$home_dir/07_gold_rim_sweep_after.png"
    ffmpeg -y -ss 1.0 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 \
      -vf 'crop=iw*0.39:ih*0.27:iw*0.31:ih*0.69' "$home_dir/08_camera_breath_min.png"
    ffmpeg -y -ss 3.5 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 \
      -vf 'crop=iw*0.39:ih*0.27:iw*0.31:ih*0.69' "$home_dir/09_camera_breath_max.png"
    ffmpeg -y -ss 2.4 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 "$home_dir/10_sun_particle_frame.png"
    ffmpeg -y -ss 0.2 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 "$home_dir/11_cloud_start.png"
    sleep 50
    "${YUJIAN_ADB_BIN}" exec-out screencap -p > "$home_dir/12_cloud_later.png"
    ffmpeg -y -i "$home_dir/full_runtime_15s.mp4" -t 10 -c copy "$home_dir/environment_motion.mp4"
    ffmpeg -y -i "$home_dir/full_runtime_15s.mp4" \
      -vf 'crop=iw/3:ih*0.22:iw*0.324:ih*0.484' -an "$home_dir/bobber_ripple_crop.mp4"
    ffmpeg -y -i "$home_dir/full_runtime_15s.mp4" \
      -vf 'crop=iw*0.39:ih*0.27:iw*0.31:ih*0.69' -an "$home_dir/camera_motion.mp4"
    python3 "$YUJIAN_REPO_ROOT/scripts/build_empty_home_runtime_evidence.py" \
      --evidence-dir "$home_dir" --build-sha "$build_sha"
    cp "$YUJIAN_REPO_ROOT/design/pages/home/empty_home/source/frozen/Empty_Home_Final_Design_V2_normalized_1080x1920.png" \
      "$home_dir/static_reference.png"
  ); then
    runtime_set_failure "EVIDENCE" "RUNTIME_PARITY_EVIDENCE_COMMAND_FAILED"
    return "$EXIT_FAIL_EVIDENCE"
  fi

  local missing=0 file
  for file in \
    "$home_dir/runtime_static.png" \
    "$home_dir/Empty_Home_V2_Parity_Runtime.mp4" \
    "$home_dir/visual_parity_report.json" \
    "$home_dir/runtime_debug.json" \
    "$home_dir/static_reference.png" \
    "$design_dir/manifest.json" \
    "$design_dir/motion_preview.mp4"
  do
    if [[ ! -s "$file" ]]; then
      printf 'MISSING_EVIDENCE=%s\n' "$file" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
      missing=1
    fi
  done
  if (( missing != 0 )); then
    runtime_set_failure "EVIDENCE" "RUNTIME_PARITY_EVIDENCE_MISSING"
    return "$EXIT_FAIL_EVIDENCE"
  fi
  return "$EXIT_PASS"
}
