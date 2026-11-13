#!/usr/bin/env bash

YUJIAN_RESULT_HANDOFF_DEVICE_RECORDING="/sdcard/recognition_result_handoff_v3.mp4"
YUJIAN_RESULT_HANDOFF_RECORDING_PID_FILE="${YUJIAN_EVIDENCE_DIR}/infra/recognition_screenrecord.pid"

gate_test_classes() {
  printf '%s\n' 'com.yujian.ai.RecognitionResultHandoffProductionNavigationTest,com.yujian.ai.RecognitionResultHandoffGuardrailRuntimeTest'
}

gate_instrumentation_timeout_seconds() {
  printf '%s\n' '480'
}

# This gate is physical-device-only. Check before the generic harness preflight,
# which otherwise attempts ADB recovery and would classify an absent handset as
# a generic infrastructure failure.
gate_device_preflight() {
  local device_list serial sdk manufacturer model qemu boot
  local -a connected=()
  device_list="$("${YUJIAN_ADB_BIN}" devices -l 2>&1 || true)"
  printf '%s\n' "$device_list" > "$YUJIAN_EVIDENCE_DIR/infra/physical_device_preflight.txt"
  while IFS= read -r serial; do [[ -n "$serial" ]] && connected+=("$serial"); done < <(
    awk 'NR > 1 && $2 == "device" {print $1}' <<< "$device_list"
  )
  if (( ${#connected[@]} != 1 )); then
    runtime_set_failure "DEVICE" "EXPECTED_ONE_CONNECTED_API36_HUAWEI_CMS_AL20_PHYSICAL_DEVICE"
    python3 - "$YUJIAN_EVIDENCE_DIR/infra/device_identity.json" "${#connected[@]}" <<'PY'
import json, sys
from pathlib import Path
Path(sys.argv[1]).write_text(json.dumps({
    "classification": "BLOCKED_DEVICE",
    "reason": "EXPECTED_ONE_CONNECTED_API36_HUAWEI_CMS_AL20_PHYSICAL_DEVICE",
    "connectedDeviceCount": int(sys.argv[2]),
}, indent=2) + "\n")
PY
    return 1
  fi

  serial="${connected[0]}"
  sdk="$("${YUJIAN_ADB_BIN}" -s "$serial" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
  manufacturer="$("${YUJIAN_ADB_BIN}" -s "$serial" shell getprop ro.product.manufacturer 2>/dev/null | tr -d '\r')"
  model="$("${YUJIAN_ADB_BIN}" -s "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
  qemu="$("${YUJIAN_ADB_BIN}" -s "$serial" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r')"
  boot="$("${YUJIAN_ADB_BIN}" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  if [[ "$sdk" != "36" || "${manufacturer^^}" != "HUAWEI" || "$model" != "CMS-AL20" || "$qemu" == "1" ]]; then
    runtime_set_failure "DEVICE" "DEVICE_IDENTITY_MISMATCH_API36_HUAWEI_CMS_AL20_PHYSICAL_REQUIRED"
    python3 - "$YUJIAN_EVIDENCE_DIR/infra/device_identity.json" "$serial" "$sdk" "$manufacturer" "$model" "$qemu" "$boot" <<'PY'
import json, sys
from pathlib import Path
Path(sys.argv[1]).write_text(json.dumps({
    "classification": "BLOCKED_DEVICE",
    "reason": "DEVICE_IDENTITY_MISMATCH_API36_HUAWEI_CMS_AL20_PHYSICAL_REQUIRED",
    "serial": sys.argv[2], "sdkInt": sys.argv[3], "manufacturer": sys.argv[4],
    "model": sys.argv[5], "roKernelQemu": sys.argv[6], "bootCompleted": sys.argv[7],
}, indent=2) + "\n")
PY
    return 1
  fi

  YUJIAN_DEVICE_STATE="device"
  YUJIAN_BOOT_COMPLETED="$boot"
  python3 - "$YUJIAN_EVIDENCE_DIR/infra/device_identity.json" "$serial" "$sdk" "$manufacturer" "$model" "$qemu" "$boot" <<'PY'
import json, sys
from pathlib import Path
Path(sys.argv[1]).write_text(json.dumps({
    "classification": "DEVICE_ACCEPTED",
    "serial": sys.argv[2], "sdkInt": int(sys.argv[3]), "manufacturer": sys.argv[4],
    "model": sys.argv[5], "roKernelQemu": sys.argv[6], "bootCompleted": sys.argv[7],
}, indent=2) + "\n")
PY
  return 0
}

gate_before_instrumentation() {
  "${YUJIAN_ADB_BIN}" shell "rm -f '${YUJIAN_RESULT_HANDOFF_DEVICE_RECORDING}'" >/dev/null 2>&1 || true
  "${YUJIAN_ADB_BIN}" shell \
    "screenrecord --bit-rate 6000000 --time-limit 180 '${YUJIAN_RESULT_HANDOFF_DEVICE_RECORDING}' >/dev/null 2>&1 & echo \$!" \
    2>/dev/null | tr -d '\r' > "$YUJIAN_RESULT_HANDOFF_RECORDING_PID_FILE" || true
  sleep 1
}

gate_after_instrumentation() {
  local pid
  pid="$(cat "$YUJIAN_RESULT_HANDOFF_RECORDING_PID_FILE" 2>/dev/null || true)"
  if [[ "$pid" =~ ^[0-9]+$ ]]; then
    "${YUJIAN_ADB_BIN}" shell kill -2 "$pid" >/dev/null 2>&1 || true
  else
    "${YUJIAN_ADB_BIN}" shell "pidof screenrecord" 2>/dev/null | tr -d '\r' | while read -r pid; do
      [[ "$pid" =~ ^[0-9]+$ ]] && "${YUJIAN_ADB_BIN}" shell kill -2 "$pid" >/dev/null 2>&1 || true
    done
  fi
  local attempt size previous=0 stable=0
  for attempt in $(seq 1 30); do
    size="$("${YUJIAN_ADB_BIN}" shell "wc -c < '${YUJIAN_RESULT_HANDOFF_DEVICE_RECORDING}' 2>/dev/null || echo 0" 2>/dev/null | tr -d '\r' | tail -n 1)"
    if [[ "$size" =~ ^[0-9]+$ ]] && (( size > 1024 )); then
      if [[ "$size" == "$previous" ]]; then
        stable=$((stable + 1))
        (( stable >= 2 )) && break
      else
        stable=0
      fi
      previous="$size"
    fi
    sleep 1
  done
}

gate_instrumentation_failure_classification() {
  if [[ "$("${YUJIAN_ADB_BIN}" get-state 2>/dev/null || true)" != "device" ]]; then
    runtime_set_failure "ADB" "PHYSICAL_DEVICE_DISCONNECTED_DURING_PRODUCTION_E2E"
    return "$EXIT_BLOCKED_INFRA"
  fi
  return "$EXIT_FAIL_TEST"
}

gate_collect_evidence() {
  local names=(
    runtime_provenance.json runtime_matrix.json stage_timeline.json path_identity_trace.json
    return_event_trace.json result_handoff_trace.json result_navigation_trace.json
    result_stability_report.json qualification_report.json
  )
  local name output="$YUJIAN_EVIDENCE_DIR"
  for name in "${names[@]}"; do
    if ! "${YUJIAN_ADB_BIN}" exec-out run-as "$YUJIAN_APP_PACKAGE" cat \
      "cache/recognition-runtime-result-handoff/$name" > "$output/$name"; then
      runtime_set_failure "EVIDENCE" "APP_PRIVATE_EVIDENCE_MISSING:$name"
      return "$EXIT_FAIL_EVIDENCE"
    fi
  done
  if ! "${YUJIAN_ADB_BIN}" exec-out run-as "$YUJIAN_APP_PACKAGE" cat \
    cache/recognition-runtime-result-handoff/model_release_contract.json > "$output/model_release_contract.json"; then
    runtime_set_failure "EVIDENCE" "APP_PRIVATE_EVIDENCE_MISSING:model_release_contract.json"
    return "$EXIT_FAIL_EVIDENCE"
  fi

  local test_names=(processing_page.png result_page.png production_e2e_test_report.json guardrail_test_report.json)
  for name in "${test_names[@]}"; do
    if ! "${YUJIAN_ADB_BIN}" exec-out run-as "$YUJIAN_TEST_PACKAGE" cat \
      "cache/recognition-result-handoff/$name" > "$output/$name"; then
      runtime_set_failure "EVIDENCE" "ANDROID_TEST_PRIVATE_EVIDENCE_MISSING:$name"
      return "$EXIT_FAIL_EVIDENCE"
    fi
  done

  if ! "${YUJIAN_ADB_BIN}" exec-out cat "$YUJIAN_RESULT_HANDOFF_DEVICE_RECORDING" > "$output/recognition_result_handoff_v3.mp4"; then
    runtime_set_failure "EVIDENCE" "DEVICE_SCREENRECORD_MISSING"
    return "$EXIT_FAIL_EVIDENCE"
  fi
  "${YUJIAN_ADB_BIN}" logcat -d -v threadtime -s \
    RecognitionProcessingScene:I RecognitionResultHandoff:I RecognitionResultEvidence:I \
    RecognitionRevealHandoff:I MainActivity:I > "$output/recognition_runtime_logcat.txt" 2>&1 || true
  cp "$YUJIAN_EVIDENCE_DIR/infra/device_identity.json" "$output/device_identity.json"

  if ! python3 "$YUJIAN_REPO_ROOT/scripts/export_apk_model_trace.py" \
    --apk "$YUJIAN_APP_APK" --output-dir "$output" --build-sha "$YUJIAN_BUILD_SHA"; then
    runtime_set_failure "EVIDENCE" "APK_MODEL_TRACE_FAILED"
    return "$EXIT_FAIL_EVIDENCE"
  fi
  if ! python3 "$YUJIAN_REPO_ROOT/scripts/android_runtime/verify_recognition_result_handoff.py" \
    --evidence-dir "$output" --app-apk "$YUJIAN_APP_APK" --build-sha "$YUJIAN_BUILD_SHA" \
    --expected-api-level 36; then
    runtime_set_failure "EVIDENCE" "PRODUCTION_EVIDENCE_CONTRACT_FAILED"
    return "$EXIT_FAIL_EVIDENCE"
  fi
  return "$EXIT_PASS"
}
