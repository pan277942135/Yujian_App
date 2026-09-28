#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
ENTRYPOINT="$ROOT_DIR/scripts/run_android_runtime_gate.sh"
FAKE_ADB="$ROOT_DIR/scripts/android_runtime/tests/fake_adb.sh"

assert_result() {
  local result_file="$1"
  local expected_classification="$2"
  local expected_rc="$3"
  local actual_classification actual_rc
  actual_classification="$(python3 - "$result_file" <<'PY'
import json
import sys
print(json.load(open(sys.argv[1], encoding="utf-8"))["classification"])
PY
)"
  actual_rc="$4"
  [[ "$actual_classification" == "$expected_classification" ]] || {
    echo "expected classification=$expected_classification actual=$actual_classification" >&2
    exit 1
  }
  [[ "$actual_rc" == "$expected_rc" ]] || {
    echo "expected exit=$expected_rc actual=$actual_rc" >&2
    exit 1
  }
}

run_case() {
  local name="$1"
  local mode="$2"
  local expected_classification="$3"
  local expected_rc="$4"
  local tmp rc restarts
  tmp="$(mktemp -d)"
  printf 'fake-apk' > "$tmp/app-debug.apk"
  printf 'fake-test-apk' > "$tmp/app-debug-androidTest.apk"
  set +e
    FAKE_ADB_MODE="$mode" \
    FAKE_ADB_RESTART_LOG="$tmp/restarts" \
    YUJIAN_CAPTURE_RECOGNITION_VIDEO=0 \
    YUJIAN_VALIDATE_RECOGNITION_VIDEO=0 \
    ADB="$FAKE_ADB" \
    YUJIAN_WAIT_ATTEMPTS=1 \
    YUJIAN_PREFLIGHT_ATTEMPTS=1 \
    YUJIAN_PREFLIGHT_SLEEP_SECONDS=0 \
    bash "$ENTRYPOINT" \
      --gate recognition-frozen \
      --api-level 28 \
      --build-sha fake-sha \
      --app-apk "$tmp/app-debug.apk" \
      --test-apk "$tmp/app-debug-androidTest.apk" \
      --evidence-dir "$tmp/evidence" > "$tmp/output.log" 2>&1
  rc=$?
  set -e
  assert_result "$tmp/evidence/runtime_gate_result.json" "$expected_classification" "$expected_rc" "$rc"
  if [[ "$name" == "offline" ]]; then
    restarts="$(wc -l < "$tmp/restarts")"
    [[ "$restarts" == "2" ]] || {
      echo "expected exactly one kill/start ADB restart, observed $restarts calls" >&2
      exit 1
    }
  fi
  printf 'PASS fake-adb scenario=%s classification=%s exit=%s\n' "$name" "$expected_classification" "$rc"
  rm -rf "$tmp"
}

run_case offline offline BLOCKED_INFRA 10
run_case boot-timeout boot-timeout BLOCKED_INFRA 10
run_case low-disk low-disk BLOCKED_INFRA 10
run_case invalid-artifact invalid-apk FAIL_ARTIFACT 20
run_case test-failure test-fail FAIL_TEST 30
run_case process-crash process-crash FAIL_TEST 30
run_case evidence-missing missing-evidence FAIL_EVIDENCE 40
run_case pass ready PASS 0
