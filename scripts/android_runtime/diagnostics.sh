#!/usr/bin/env bash

android_runtime_collect_diagnostics() {
  mkdir -p "$YUJIAN_INFRA_DIR"
  "${YUJIAN_ADB_BIN}" devices -l > "$YUJIAN_INFRA_DIR/device.txt" 2>&1 || true
  "${YUJIAN_ADB_BIN}" get-state > "$YUJIAN_INFRA_DIR/adb_state.txt" 2>&1 || true
  "${YUJIAN_ADB_BIN}" shell getprop > "$YUJIAN_INFRA_DIR/getprop.txt" 2>&1 || true
  "${YUJIAN_ADB_BIN}" shell df -h /data > "$YUJIAN_INFRA_DIR/disk.txt" 2>&1 || true
  "${YUJIAN_ADB_BIN}" shell pm list packages > "$YUJIAN_INFRA_DIR/packages.txt" 2>&1 || true

  local state
  state="$(runtime_adb_state)"
  if [[ "$state" == "device" ]]; then
    if [[ "${YUJIAN_GATE:-}" == "camera-runtime-v1" ]]; then
      timeout 20s "${YUJIAN_ADB_BIN}" logcat -d -v threadtime 2>&1 |
        grep -Ei 'RecognitionCameraCapture|CameraX|Camera2|CameraManager|CameraService|AndroidRuntime' |
        tail -n 3000 > "$YUJIAN_INFRA_DIR/logcat.txt" || true
      timeout 15s "${YUJIAN_ADB_BIN}" shell dumpsys media.camera \
        > "$YUJIAN_INFRA_DIR/camera_service.txt" 2>&1 || \
        timeout 15s "${YUJIAN_ADB_BIN}" shell dumpsys camera \
          > "$YUJIAN_INFRA_DIR/camera_service.txt" 2>&1 || true
    else
          "${YUJIAN_ADB_BIN}" logcat -d -b all -v threadtime > "$YUJIAN_INFRA_DIR/logcat.txt" 2>&1 || true
    fi
  else
    printf 'logcat skipped because adb_state=%s\n' "$state" > "$YUJIAN_INFRA_DIR/logcat.txt"
  fi
}
