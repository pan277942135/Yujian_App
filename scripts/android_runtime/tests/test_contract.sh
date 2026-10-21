#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
ENTRYPOINT="$ROOT_DIR/scripts/run_android_runtime_gate.sh"
FAKE_ADB="$ROOT_DIR/scripts/android_runtime/tests/fake_adb.sh"

grep -q 'gemini-home-v2' "$ENTRYPOINT" || {
  echo "Gemini Home V2 runtime gate is not registered" >&2
  exit 1
}

assert_result() {
  local result_file="$1"
  local expected_classification="$2"
  local expected_rc="$3"
  local output_log="${5:-}"
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
    echo "runtime gate result:" >&2
    cat "$result_file" >&2 || true
    if [[ -n "$output_log" && -s "$output_log" ]]; then
      echo "runtime gate output:" >&2
      cat "$output_log" >&2 || true
    fi
    exit 1
  }
  [[ "$actual_rc" == "$expected_rc" ]] || {
    echo "expected exit=$expected_rc actual=$actual_rc" >&2
    echo "runtime gate result:" >&2
    cat "$result_file" >&2 || true
    if [[ -n "$output_log" && -s "$output_log" ]]; then
      echo "runtime gate output:" >&2
      cat "$output_log" >&2 || true
    fi
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
  if [[ "$mode" == "ready" ]]; then
    # The evidence packager validates a real MP4 container. Generate a tiny
    # synthetic recording for the fake-adb PASS path; it is not runtime proof.
    ffmpeg -hide_banner -loglevel error -y \
      -f lavfi -i color=c=black:s=320x640:r=30 -t 1 -an \
      -c:v mpeg4 -q:v 5 -pix_fmt yuv420p -movflags +faststart \
      "$tmp/fake-rr09-recording.mp4"
  fi
  local app_apk_sha256
  app_apk_sha256="$(sha256sum "$tmp/app-debug.apk" | awk '{print $1}')"
  set +e
    FAKE_ADB_MODE="$mode" \
    FAKE_APP_APK_SHA256="$app_apk_sha256" \
    FAKE_RR09_VIDEO_FILE="$tmp/fake-rr09-recording.mp4" \
    YUJIAN_CAPTURE_RECOGNITION_VIDEO=1 \
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
  assert_result "$tmp/evidence/runtime_gate_result.json" "$expected_classification" "$expected_rc" "$rc" "$tmp/output.log"
  if [[ "$name" == "offline" ]]; then
    restarts="$(cat "$tmp/evidence/infra/adb_restart_count")"
    [[ "$restarts" == "1" ]] || {
      echo "expected exactly one kill/start ADB restart (count=1), observed count=$restarts" >&2
      exit 1
    }
  fi
  if [[ "$name" == "pass" ]]; then
    instrumentation_classes=()
    while IFS= read -r instrumentation_class; do
      instrumentation_classes+=("$instrumentation_class")
    done < <(sed -n 's/^TEST_CLASSES=//p' "$tmp/evidence/instrumentation.log")
    instrumentation_timeouts=()
    while IFS= read -r instrumentation_timeout; do
      instrumentation_timeouts+=("$instrumentation_timeout")
    done < <(sed -n 's/^TIMEOUT_SECONDS=//p' "$tmp/evidence/instrumentation.log")
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
run_case pass ready PASS 0

run_adaptive_matrix_even_when_legacy_suite_fails() {
  local tmp rc trace
  tmp="$(mktemp -d)"
  mkdir -p "$tmp/bin"
  trace="$tmp/call-order.txt"
  cat > "$tmp/bin/adb" <<'SH'
#!/bin/sh
exit 0
SH
  cat > "$tmp/bin/python3" <<'SH'
#!/bin/sh
exit 0
SH
  cat > "$tmp/bin/bash" <<'SH'
#!/bin/sh
case "$1" in
  scripts/run_android_runtime_gate.sh)
    printf 'legacy\n' >> "$NH01_TEST_TRACE"
    exit 30
    ;;
  scripts/android_runtime/gates/run_nh01_adaptive_device_matrix.sh)
    printf 'matrix\n' >> "$NH01_TEST_TRACE"
    exit 0
    ;;
  *)
    printf 'unexpected child command: %s\n' "$*" >&2
    exit 99
    ;;
esac
SH
  chmod +x "$tmp/bin/adb" "$tmp/bin/python3" "$tmp/bin/bash"
  set +e
    PATH="$tmp/bin:$PATH" \
      NH01_TEST_TRACE="$trace" \
      YUJIAN_RUNTIME_EVIDENCE="$tmp/evidence" \
      YUJIAN_SOURCE_SHA="fake-sha" \
      /bin/bash "$ROOT_DIR/scripts/design/run_nh01_emulator_runtime.sh" > "$tmp/output.log" 2>&1
    rc=$?
  set -e
  [[ "$rc" == "30" ]] || {
    echo "expected core FAIL_TEST exit 30 after matrix attempt; got $rc" >&2
    cat "$tmp/output.log" >&2
    exit 1
  }
  [[ "$(cat "$trace")" == $'legacy\nmatrix' ]] || {
    echo "adaptive matrix did not run after the independent legacy failure" >&2
    cat "$trace" >&2 || true
    exit 1
  }
  grep -q 'NH01_ADAPTIVE_DEVICE_MATRIX=PASS source_sha=fake-sha' "$tmp/output.log" || {
    echo "successful adaptive matrix result was not recorded" >&2
    cat "$tmp/output.log" >&2
    exit 1
  }
  printf '%s\n' 'PASS NH01 adaptive matrix runs even after independent legacy FAIL_TEST'
  rm -rf "$tmp"
}

run_adaptive_matrix_even_when_legacy_suite_fails
