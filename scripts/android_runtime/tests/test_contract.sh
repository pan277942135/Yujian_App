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
    # Stub container metadata for the fake-adb PASS path. The production
    # API28 gate uses the real ffprobe installed by its runtime workflow job.
    mkdir -p "$tmp/bin"
    printf 'synthetic test-only recording\n' > "$tmp/fake-rr09-recording.mp4"
    cat > "$tmp/bin/ffprobe" <<'SH'
#!/usr/bin/env sh
printf '%s\n' '{"streams":[{"codec_type":"video","width":320,"height":640}],"format":{"duration":"1.0"}}'
SH
    chmod +x "$tmp/bin/ffprobe"
  fi
  local app_apk_sha256
  app_apk_sha256="$(sha256sum "$tmp/app-debug.apk" | awk '{print $1}')"
  set +e
    PATH="$tmp/bin:$PATH" \
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
    [[ "${instrumentation_timeouts[*]}" == "600 420" ]] || {
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

run_only_representative_adaptive_matrix() {
  local tmp rc trace adb_trace install_count
  tmp="$(mktemp -d)"
  mkdir -p "$tmp/bin" "$tmp/runtime-apks"
  trace="$tmp/call-order.txt"
  adb_trace="$tmp/adb-commands.txt"
  printf 'fake app apk\n' > "$tmp/runtime-apks/app-debug.apk"
  printf 'fake test apk\n' > "$tmp/runtime-apks/app-debug-androidTest.apk"
  cat > "$tmp/bin/adb" <<'SH'
#!/bin/sh
printf '%s\n' "$*" >> "$NH01_ADB_TRACE"
case "$*" in
  "devices"|"devices -l")
    printf 'List of devices attached\nemulator-5554 device product:fake model:fake transport_id:1\n'
    ;;
  *"get-state")
    printf 'device\n'
    ;;
  *"getprop sys.boot_completed")
    printf '1\n'
    ;;
  *"shell df -k /data")
    printf 'Filesystem 1K-blocks Used Available Use%% Mounted on\n/dev/fake 2000000 100000 1900000 5%% /data\n'
    ;;
  *"shell pm path "*)
    printf 'package:/data/app/fake/base.apk\n'
    ;;
  *)
    exit 0
    ;;
esac
SH
  cat > "$tmp/bin/python3" <<'SH'
#!/bin/sh
exit 0
SH
  cat > "$tmp/bin/bash" <<'SH'
#!/bin/sh
case "$1" in
  scripts/run_android_runtime_gate.sh)
    printf 'unexpected-legacy-gate\n' >> "$NH01_TEST_TRACE"
    exit 99
    ;;
  scripts/android_runtime/gates/run_nh01_adaptive_device_matrix.sh)
    printf 'matrix:%s:%s\n' "${YUJIAN_NH01_PROFILE_FILTER:-FULL}" "${YUJIAN_NH01_VARIANT_FILTER:-BOTH}" >> "$NH01_TEST_TRACE"
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
      NH01_ADB_TRACE="$adb_trace" \
      YUJIAN_RUNTIME_EVIDENCE="$tmp/evidence" \
      YUJIAN_SOURCE_SHA="fake-sha" \
      YUJIAN_APP_APK="$tmp/runtime-apks/app-debug.apk" \
      YUJIAN_TEST_APK="$tmp/runtime-apks/app-debug-androidTest.apk" \
      /bin/bash "$ROOT_DIR/scripts/design/run_nh01_emulator_runtime.sh" > "$tmp/output.log" 2>&1
    rc=$?
  set -e
  [[ "$rc" == "40" ]] || {
    echo "expected incomplete synthetic matrix evidence to fail closed with 40; got $rc" >&2
    cat "$tmp/output.log" >&2
    exit 1
  }
  [[ "$(cat "$trace")" == "matrix:M04,M07,M10:ADAPTIVE_V2" ]] || {
    echo "the workflow did not run exactly the three representative ADAPTIVE_V2 profiles" >&2
    cat "$trace" >&2 || true
    exit 1
  }
  install_count="$(grep -c '^install -r -t ' "$adb_trace" || true)"
  [[ "$install_count" == "2" ]] || {
    echo "expected one app APK and one AndroidTest APK installation, got $install_count commands" >&2
    cat "$adb_trace" >&2 || true
    exit 1
  }
  ! grep -q 'unexpected-legacy-gate' "$trace" || {
    echo "the out-of-scope legacy 1080×1920 gate was still invoked" >&2
    cat "$tmp/output.log" >&2
    exit 1
  }
  printf '%s\n' 'PASS NH01 runtime installs one APK pair and runs only M04/M07/M10 ADAPTIVE_V2'
  rm -rf "$tmp"
}

run_only_representative_adaptive_matrix
