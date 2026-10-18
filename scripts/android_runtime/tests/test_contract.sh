#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
ENTRYPOINT="$ROOT_DIR/scripts/run_android_runtime_gate.sh"
FAKE_ADB="$ROOT_DIR/scripts/android_runtime/tests/fake_adb.sh"

python3 "$ROOT_DIR/scripts/android_runtime/tests/test_recognition_m1_authority_map.py"
python3 "$ROOT_DIR/scripts/android_runtime/tests/test_recognition_m1_v3_1_finalizer.py"

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

assert_failure_reason() {
  local result_file="$1"
  local expected_reason="$2"
  local actual_reason
  actual_reason="$(python3 - "$result_file" <<'PY'
import json
import sys
print(json.load(open(sys.argv[1], encoding="utf-8"))["failure_reason"])
PY
)"
  [[ "$actual_reason" == "$expected_reason" ]] || {
    echo "expected failure_reason=$expected_reason actual=$actual_reason" >&2
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
  (cd "$tmp" && sha256sum app-debug.apk app-debug-androidTest.apk > SHA256SUMS)
  local app_apk_sha256
  app_apk_sha256="$(sha256sum "$tmp/app-debug.apk" | awk '{print $1}')"
  set +e
    FAKE_ADB_MODE="$mode" \
    FAKE_M1_SHORT_BEAT="$([[ "$name" == "evidence-invalid-v3_1-beat" ]] && printf 1 || printf 0)" \
    FAKE_APP_APK_SHA256="$app_apk_sha256" \
    GITHUB_RUN_ID=999999999 \
    YUJIAN_CAPTURE_RECOGNITION_VIDEO=1 \
    YUJIAN_VALIDATE_RECOGNITION_VIDEO=0 \
    ADB="$FAKE_ADB" \
    YUJIAN_WAIT_ATTEMPTS=1 \
    YUJIAN_PREFLIGHT_ATTEMPTS=1 \
    YUJIAN_PREFLIGHT_SLEEP_SECONDS=0 \
    bash "$ENTRYPOINT" \
      --gate recognition-frozen \
      --api-level 28 \
      --build-sha 0000000000000000000000000000000000000001 \
      --app-apk "$tmp/app-debug.apk" \
      --test-apk "$tmp/app-debug-androidTest.apk" \
      --evidence-dir "$tmp/evidence" > "$tmp/output.log" 2>&1
  rc=$?
  set -e
  assert_result "$tmp/evidence/runtime_gate_result.json" "$expected_classification" "$expected_rc" "$rc"
  if [[ "$name" == "offline" ]]; then
    restarts="$(cat "$tmp/evidence/infra/adb_restart_count")"
    [[ "$restarts" == "1" ]] || {
      echo "expected exactly one kill/start ADB restart (count=1), observed count=$restarts" >&2
      exit 1
    }
  fi
  if [[ "$name" == "evidence-invalid-v3_1-beat" ]]; then
    assert_failure_reason "$tmp/evidence/runtime_gate_result.json" "M1_V3_1_EVIDENCE_OR_PROVENANCE_INVALID"
    mapfile -t instrumentation_classes < <(sed -n 's/^TEST_CLASSES=//p' "$tmp/evidence/instrumentation.log")
    mapfile -t instrumentation_timeouts < <(sed -n 's/^TIMEOUT_SECONDS=//p' "$tmp/evidence/instrumentation.log")
    [[ "${#instrumentation_classes[@]}" == "2" ]] || {
      echo "expected the required suite and one additional evidence invocation" >&2
      exit 1
    }
    [[ "${instrumentation_classes[0]}" == *"DetectorGoldenParityTest" ]] || {
      echo "required Recognition suite was not run as the first invocation" >&2
      exit 1
    }
    [[ "${instrumentation_classes[1]}" == "com.yujian.ai.RecognitionOpticalFidelityRuntimeTest" ]] || {
      echo "live optical-fidelity evidence was not isolated into its own invocation" >&2
      exit 1
    }
    [[ "${instrumentation_timeouts[*]}" == "300 420" ]] || {
      echo "unexpected instrumentation timeout budgets: ${instrumentation_timeouts[*]}" >&2
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
run_case camera-application-error camera-closed FAIL_TEST 30
run_case process-crash process-crash FAIL_TEST 30
run_case evidence-missing missing-evidence FAIL_EVIDENCE 40
run_case evidence-invalid-v3_1-beat ready FAIL_EVIDENCE 40
