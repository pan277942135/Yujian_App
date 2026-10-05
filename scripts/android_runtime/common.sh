#!/usr/bin/env bash

# Shared state and policy for every Android runtime gate.  This file deliberately
# contains no product- or test-specific logic.

YUJIAN_RUNTIME_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YUJIAN_REPO_ROOT="$(cd "${YUJIAN_RUNTIME_DIR}/../.." && pwd)"
YUJIAN_ADB_BIN="${ADB:-adb}"
YUJIAN_APP_PACKAGE="${YUJIAN_APP_PACKAGE:-com.yujian.ai.uiv2}"
YUJIAN_TEST_PACKAGE="${YUJIAN_TEST_PACKAGE:-com.yujian.ai.uiv2.test}"
YUJIAN_TEST_RUNNER="${YUJIAN_TEST_RUNNER:-androidx.test.runner.AndroidJUnitRunner}"
YUJIAN_INSTRUMENTATION_TARGET="${YUJIAN_TEST_PACKAGE}/${YUJIAN_TEST_RUNNER}"
YUJIAN_EVIDENCE_DIR="${EVIDENCE_DIR:-evidence/runtime/unknown}"
YUJIAN_INFRA_DIR="${YUJIAN_EVIDENCE_DIR}/infra"
YUJIAN_RESULT_PATH="${YUJIAN_EVIDENCE_DIR}/runtime_gate_result.json"
YUJIAN_BUILD_SHA="${BUILD_SHA:-${GITHUB_SHA:-unknown}}"
YUJIAN_API_LEVEL="${API_LEVEL:-unknown}"
YUJIAN_MIN_DATA_FREE_KB="${YUJIAN_MIN_DATA_FREE_KB:-524288}"
YUJIAN_ADB_RESTART_STATE="${YUJIAN_INFRA_DIR}/adb_restart_count"
YUJIAN_WAIT_ATTEMPTS="${YUJIAN_WAIT_ATTEMPTS:-15}"
YUJIAN_PREFLIGHT_ATTEMPTS="${YUJIAN_PREFLIGHT_ATTEMPTS:-30}"
YUJIAN_PREFLIGHT_SLEEP_SECONDS="${YUJIAN_PREFLIGHT_SLEEP_SECONDS:-2}"

EXIT_PASS=0
EXIT_BLOCKED_INFRA=10
EXIT_FAIL_ARTIFACT=20
EXIT_FAIL_TEST=30
EXIT_FAIL_EVIDENCE=40

YUJIAN_FAILURE_PHASE=""
YUJIAN_FAILURE_REASON=""
YUJIAN_DEVICE_STATE="unknown"
YUJIAN_BOOT_COMPLETED="unknown"
YUJIAN_DATA_FREE_KB=""

runtime_log() {
  printf '[android-runtime] %s\n' "$*" >&2
}

runtime_normalize() {
  tr -d '\r' | sed '/^[[:space:]]*$/d' | head -n 1
}

runtime_adb_state() {
  "${YUJIAN_ADB_BIN}" get-state 2>&1 | runtime_normalize
}

runtime_is_transport_failure_file() {
  local file="$1"
  # Only explicit adb/device-transport signatures are infrastructure loss.
  # CameraX application errors such as "Camera is closed" remain FAIL_TEST.
  grep -Eiq \
    '(^|[[:space:]])(adb: )?error: (device offline|device unauthorized|device .*not found|no devices/emulators found|more than one device/emulator|transport [^[:space:]]+ not found)|cannot connect to daemon|failed to get feature set: (device offline|device unauthorized|device .*not found)|adb.*protocol fault|device transport (lost|closed|offline)|transport (error|lost|closed|offline):' \
    "$file"
}

runtime_is_storage_failure_file() {
  local file="$1"
  grep -Eiq 'INSUFFICIENT_STORAGE|INSTALL_FAILED_INSUFFICIENT_STORAGE|No space left on device' "$file"
}

runtime_is_install_artifact_failure_file() {
  local file="$1"
  grep -Eiq \
    'INSTALL_FAILED_INVALID_APK|INSTALL_PARSE_FAILED_|INSTALL_FAILED_BAD_PACKAGE_NAME|INSTALL_FAILED_CONFLICTING_PROVIDER|INSTALL_FAILED_DEXOPT|INSTALL_FAILED_DUPLICATE_PACKAGE|INSTALL_FAILED_OLDER_SDK|INSTALL_FAILED_NEWER_SDK|INSTALL_FAILED_VERSION_DOWNGRADE|INSTALL_FAILED_UPDATE_INCOMPATIBLE|INSTALL_FAILED_SHARED_USER_INCOMPATIBLE|INSTALL_FAILED_NO_MATCHING_ABIS|INSTALL_FAILED_INVALID_URI|INSTALL_FAILED_CONTAINER_ERROR|INSTALL_FAILED_CPU_ABI_INCOMPATIBLE|INSTALL_FAILED_TEST_ONLY|signature|package mismatch' \
    "$file"
}

runtime_restart_adb_once() {
  mkdir -p "$YUJIAN_INFRA_DIR"
  local count=0
  if [[ -f "$YUJIAN_ADB_RESTART_STATE" ]]; then
    count="$(tr -d '[:space:]' < "$YUJIAN_ADB_RESTART_STATE")"
  fi
  if [[ "$count" =~ ^[0-9]+$ ]] && (( count >= 1 )); then
    runtime_log "ADB restart budget already consumed"
    return 2
  fi

  printf '1\n' > "$YUJIAN_ADB_RESTART_STATE"
  runtime_log "Restarting ADB once"
  "${YUJIAN_ADB_BIN}" kill-server >/dev/null 2>&1 || true
  "${YUJIAN_ADB_BIN}" start-server >/dev/null 2>&1
}

runtime_wait_for_device() {
  # adb wait-for-device has no native timeout.  Bound it so a broken transport
  # cannot hold a GitHub runner forever.
  if command -v timeout >/dev/null 2>&1; then
    timeout 15s "${YUJIAN_ADB_BIN}" wait-for-device >/dev/null 2>&1 || true
  else
    "${YUJIAN_ADB_BIN}" wait-for-device >/dev/null 2>&1 || true
  fi

  local attempt state
  for attempt in $(seq 1 "$YUJIAN_WAIT_ATTEMPTS"); do
    state="$(runtime_adb_state)"
    if [[ "$state" == "device" ]]; then
      return 0
    fi
    sleep 1
  done
  return 1
}

runtime_set_failure() {
  YUJIAN_FAILURE_PHASE="$1"
  YUJIAN_FAILURE_REASON="$2"
}

runtime_capture_command() {
  local output="$1"
  shift
  mkdir -p "$(dirname "$output")"
  "$@" > "$output" 2>&1
  return $?
}
