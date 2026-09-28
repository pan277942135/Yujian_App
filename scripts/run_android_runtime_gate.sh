#!/usr/bin/env bash

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNTIME_DIR="$SCRIPT_DIR/android_runtime"

usage() {
  cat >&2 <<'USAGE'
Usage:
  bash scripts/run_android_runtime_gate.sh \
    --gate recognition-frozen|data-sanitization|empty-home-v2|runtime-parity \
    --app-apk path/to/app-debug.apk \
    --test-apk path/to/app-debug-androidTest.apk \
    --evidence-dir evidence/runtime/<gate> \
    [--api-level 28] [--build-sha SHA]
USAGE
}

GATE=""
APP_APK=""
TEST_APK=""
EVIDENCE_DIR=""
API_LEVEL="${API_LEVEL:-unknown}"
BUILD_SHA="${BUILD_SHA:-${GITHUB_SHA:-unknown}}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --gate) GATE="${2:-}"; shift 2 ;;
    --app-apk) APP_APK="${2:-}"; shift 2 ;;
    --test-apk) TEST_APK="${2:-}"; shift 2 ;;
    --evidence-dir) EVIDENCE_DIR="${2:-}"; shift 2 ;;
    --api-level) API_LEVEL="${2:-}"; shift 2 ;;
    --build-sha) BUILD_SHA="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown argument: %s\n' "$1" >&2; usage; exit 20 ;;
  esac
done

if [[ -z "$GATE" || -z "$APP_APK" || -z "$TEST_APK" || -z "$EVIDENCE_DIR" ]]; then
  usage
  exit 20
fi

export EVIDENCE_DIR API_LEVEL BUILD_SHA
source "$RUNTIME_DIR/common.sh"
source "$RUNTIME_DIR/result.sh"
source "$RUNTIME_DIR/preflight.sh"
source "$RUNTIME_DIR/install_apks.sh"
source "$RUNTIME_DIR/run_instrumentation.sh"
source "$RUNTIME_DIR/diagnostics.sh"

YUJIAN_GATE="$GATE"
YUJIAN_APP_APK="$APP_APK"
YUJIAN_TEST_APK="$TEST_APK"
YUJIAN_PREFLIGHT_STATUS="NOT_RUN"
YUJIAN_INSTALL_STATUS="NOT_RUN"
YUJIAN_INSTRUMENTATION_STATUS="NOT_RUN"
YUJIAN_EVIDENCE_STATUS="NOT_RUN"
YUJIAN_CLASSIFICATION="BLOCKED_INFRA"

mkdir -p "$YUJIAN_EVIDENCE_DIR" "$YUJIAN_INFRA_DIR"

case "$GATE" in
  recognition-frozen)
    source "$RUNTIME_DIR/gates/recognition_frozen.sh"
    ;;
  data-sanitization)
    source "$RUNTIME_DIR/gates/data_sanitization.sh"
    ;;
  empty-home-v2)
    source "$RUNTIME_DIR/gates/empty_home_v2.sh"
    ;;
  runtime-parity)
    source "$RUNTIME_DIR/gates/runtime_parity.sh"
    ;;
  *)
    runtime_set_failure "CONFIG" "UNKNOWN_GATE"
    YUJIAN_CLASSIFICATION="FAIL_ARTIFACT"
    android_runtime_write_result
    exit 20
    ;;
esac

finish_gate() {
  local exit_code="$1"
  local classification="$2"
  YUJIAN_CLASSIFICATION="$classification"
  if (( exit_code != EXIT_PASS )); then
    android_runtime_collect_diagnostics
  fi
  android_runtime_write_result
  return "$exit_code"
}

test_classes="$(gate_test_classes)"
if [[ -z "$test_classes" ]]; then
  runtime_set_failure "CONFIG" "TEST_CLASS_LIST_EMPTY"
  finish_gate "$EXIT_FAIL_ARTIFACT" "FAIL_ARTIFACT"
  final_rc=$?
  exit "$final_rc"
fi

android_runtime_preflight
preflight_rc=$?
if (( preflight_rc != EXIT_PASS )); then
  YUJIAN_PREFLIGHT_STATUS="FAIL"
  finish_gate "$EXIT_BLOCKED_INFRA" "BLOCKED_INFRA"
  final_rc=$?
  exit "$final_rc"
fi
YUJIAN_PREFLIGHT_STATUS="PASS"

android_runtime_install_apks "$APP_APK" "$TEST_APK"
install_rc=$?
if (( install_rc != EXIT_PASS )); then
  YUJIAN_INSTALL_STATUS="FAIL"
  if (( install_rc == EXIT_FAIL_ARTIFACT )); then
    finish_gate "$EXIT_FAIL_ARTIFACT" "FAIL_ARTIFACT"
  else
    finish_gate "$EXIT_BLOCKED_INFRA" "BLOCKED_INFRA"
  fi
  final_rc=$?
  exit "$final_rc"
fi
YUJIAN_INSTALL_STATUS="PASS"

android_runtime_run_instrumentation "$test_classes"
instrumentation_rc=$?
if (( instrumentation_rc != EXIT_PASS )); then
  YUJIAN_INSTRUMENTATION_STATUS="FAIL"
  if (( instrumentation_rc == EXIT_BLOCKED_INFRA )); then
    finish_gate "$EXIT_BLOCKED_INFRA" "BLOCKED_INFRA"
  else
    finish_gate "$EXIT_FAIL_TEST" "FAIL_TEST"
  fi
  final_rc=$?
  exit "$final_rc"
fi
YUJIAN_INSTRUMENTATION_STATUS="PASS"

gate_collect_evidence
evidence_rc=$?
if (( evidence_rc != EXIT_PASS )); then
  YUJIAN_EVIDENCE_STATUS="FAIL"
  finish_gate "$EXIT_FAIL_EVIDENCE" "FAIL_EVIDENCE"
  final_rc=$?
  exit "$final_rc"
fi
YUJIAN_EVIDENCE_STATUS="PASS"

finish_gate "$EXIT_PASS" "PASS"
final_rc=$?
exit "$final_rc"
