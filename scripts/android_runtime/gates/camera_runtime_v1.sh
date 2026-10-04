#!/usr/bin/env bash

gate_test_classes() {
  printf '%s\n' 'com.yujian.ai.RecognitionCameraRuntimeTest'
}

gate_before_instrumentation() {
  local output="$YUJIAN_EVIDENCE_DIR/camera_runtime_v1"
  mkdir -p "$output"
  "$YUJIAN_ADB_BIN" shell am force-stop "$YUJIAN_APP_PACKAGE" >/dev/null 2>&1 || true
  local clear_result grant_result
  clear_result="$("$YUJIAN_ADB_BIN" shell pm clear "$YUJIAN_APP_PACKAGE" 2>&1 | tr -d '\r')"
  grant_result="$("$YUJIAN_ADB_BIN" shell pm grant "$YUJIAN_APP_PACKAGE" android.permission.CAMERA 2>&1 | tr -d '\r')"
  {
    printf 'APP_DATA_RESET=%s\n' "$clear_result"
    printf 'CAMERA_PERMISSION_GRANT=%s\n' "$grant_result"
  } > "$output/permission_preflight.txt"
  if [[ "$clear_result" != *"Success"* || ( "$grant_result" != *"Success"* && "$grant_result" != *"granted"* ) ]]; then
    runtime_set_failure "CAMERA_PERMISSION_SETUP" "APP_RESET_OR_CAMERA_PERMISSION_GRANT_FAILED"
    return "$EXIT_BLOCKED_INFRA"
  fi
  "$YUJIAN_ADB_BIN" logcat -c >/dev/null 2>&1 || true
  return "$EXIT_PASS"
}

camera_runtime_pull_file() {
  local source="$1"
  local destination="$2"
  "$YUJIAN_ADB_BIN" exec-out run-as "$YUJIAN_APP_PACKAGE" cat "$source" \
    > "$destination" 2>/dev/null || true
  if [[ ! -s "$destination" ]]; then rm -f "$destination"; fi
}

gate_after_instrumentation() {
  local output="$YUJIAN_EVIDENCE_DIR/camera_runtime_v1"
  mkdir -p "$output"
  local name
  for name in \
    01_camera_open.png \
    02_preview_streaming.png \
    03_capture_ready.png \
    04_capture_pressed.png \
    05_post_capture.png \
    06_recognition_handoff.png \
    camera_preflight.txt \
    camera_runtime_log.txt \
    camera_capture_trace.txt \
    camera_service_after_test.txt
  do
    camera_runtime_pull_file "cache/camera-runtime-v1/$name" "$output/$name"
  done

  "$YUJIAN_ADB_BIN" logcat -d -v threadtime \
    -s RecognitionCameraCapture:D CameraX:D Camera2CameraImpl:D Camera2CameraInfo:D \
       Camera2CameraControl:D Camera2CameraCaptureSession:D Camera2CameraDevice:D \
       CameraManagerGlobal:W AndroidRuntime:E \
    > "$output/camera_runtime_log.txt" 2>&1 || true
  "$YUJIAN_ADB_BIN" shell dumpsys media.camera \
    > "$output/camera_service_after_test.txt" 2>&1 || \
    "$YUJIAN_ADB_BIN" shell dumpsys camera \
      > "$output/camera_service_after_test.txt" 2>&1 || true

  if [[ -s "$output/camera_preflight.txt" ]]; then
    cp "$output/camera_preflight.txt" "$YUJIAN_EVIDENCE_DIR/camera_preflight.txt"
  fi
  if [[ -s "$output/camera_runtime_log.txt" ]]; then
    cp "$output/camera_runtime_log.txt" "$YUJIAN_EVIDENCE_DIR/camera_runtime_log.txt"
  fi
  if [[ -s "$output/camera_capture_trace.txt" ]]; then
    cp "$output/camera_capture_trace.txt" "$YUJIAN_EVIDENCE_DIR/camera_capture_trace.txt"
  fi
}

gate_instrumentation_failure_classification() {
  local preflight="$YUJIAN_EVIDENCE_DIR/camera_preflight.txt"
  if [[ -s "$preflight" ]] && {
    grep -q '^BACK_CAMERA_COUNT=0$' "$preflight" ||
      grep -q '^CAMERA_SERVICE_RESPONSIVE=false$' "$preflight"
  }; then
    runtime_set_failure "CAMERA_PREFLIGHT" "REAR_CAMERA_NOT_ENUMERATED"
    return "$EXIT_BLOCKED_INFRA"
  fi
  return "$EXIT_FAIL_TEST"
}

gate_collect_evidence() {
  local output="$YUJIAN_EVIDENCE_DIR/camera_runtime_v1"
  local name
  if [[ ! -s "$output/camera_preflight.txt" ]]; then
    runtime_set_failure "EVIDENCE" "CAMERA_PREFLIGHT_REPORT_MISSING"
    return "$EXIT_FAIL_EVIDENCE"
  fi
  if grep -q '^BACK_CAMERA_COUNT=0$' "$output/camera_preflight.txt" ||
     grep -q '^CAMERA_SERVICE_RESPONSIVE=false$' "$output/camera_preflight.txt"; then
    runtime_set_failure "CAMERA_PREFLIGHT" "REAR_CAMERA_NOT_ENUMERATED"
    return "$EXIT_BLOCKED_INFRA"
  fi
  for name in \
    01_camera_open.png \
    02_preview_streaming.png \
    03_capture_ready.png \
    04_capture_pressed.png \
    05_post_capture.png \
    06_recognition_handoff.png \
    camera_capture_trace.txt \
    camera_runtime_log.txt
  do
    if [[ ! -s "$output/$name" ]]; then
      printf 'MISSING_CAMERA_EVIDENCE=%s\n' "$name" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
      runtime_set_failure "EVIDENCE" "CAMERA_RUNTIME_EVIDENCE_INCOMPLETE"
      return "$EXIT_FAIL_EVIDENCE"
    fi
  done
  return "$EXIT_PASS"
}

