#!/usr/bin/env bash

set -uo pipefail

usage() {
  cat >&2 <<'USAGE'
Usage:
  bash scripts/run_recognition_result_handoff_gate.sh \
    --app-apk path/to/app-debug.apk \
    --test-apk path/to/app-debug-androidTest.apk \
    --evidence-dir evidence/runtime/recognition-result-handoff \
    [--api-level 36] [--build-sha SHA]
USAGE
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNTIME_DIR="$SCRIPT_DIR/android_runtime"
APP_APK=""
TEST_APK=""
EVIDENCE_DIR=""
API_LEVEL="${API_LEVEL:-36}"
BUILD_SHA="${BUILD_SHA:-${GITHUB_SHA:-unknown}}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --app-apk) APP_APK="${2:-}"; shift 2 ;;
    --test-apk) TEST_APK="${2:-}"; shift 2 ;;
    --evidence-dir) EVIDENCE_DIR="${2:-}"; shift 2 ;;
    --api-level) API_LEVEL="${2:-}"; shift 2 ;;
    --build-sha) BUILD_SHA="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown argument: %s\n' "$1" >&2; usage; exit 20 ;;
  esac
done

if [[ -z "$APP_APK" || -z "$TEST_APK" || -z "$EVIDENCE_DIR" || "$API_LEVEL" != "36" ]]; then
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
source "$RUNTIME_DIR/gates/recognition_result_handoff.sh"

YUJIAN_GATE="recognition-result-handoff"
YUJIAN_APP_APK="$APP_APK"
YUJIAN_TEST_APK="$TEST_APK"
YUJIAN_PREFLIGHT_STATUS="NOT_RUN"
YUJIAN_INSTALL_STATUS="NOT_RUN"
YUJIAN_INSTRUMENTATION_STATUS="NOT_RUN"
YUJIAN_EVIDENCE_STATUS="NOT_RUN"
YUJIAN_CLASSIFICATION="BLOCKED_INFRA"

mkdir -p "$YUJIAN_EVIDENCE_DIR" "$YUJIAN_INFRA_DIR"

finish_gate() {
  local exit_code="$1" classification="$2"
  YUJIAN_CLASSIFICATION="$classification"
  if (( exit_code != EXIT_PASS )); then
    android_runtime_collect_diagnostics
  fi
  android_runtime_write_result
  return "$exit_code"
}

gate_test_classes="$(gate_test_classes)"
if [[ -z "$gate_test_classes" ]]; then
  runtime_set_failure "CONFIG" "TEST_CLASS_LIST_EMPTY"
  finish_gate "$EXIT_FAIL_ARTIFACT" "FAIL_ARTIFACT"
  exit $?
fi

gate_device_preflight
if (( $? != 0 )); then
  YUJIAN_PREFLIGHT_STATUS="FAIL"
  finish_gate "$EXIT_BLOCKED_INFRA" "BLOCKED_DEVICE"
  exit $?
fi

android_runtime_preflight
preflight_rc=$?
if (( preflight_rc != EXIT_PASS )); then
  YUJIAN_PREFLIGHT_STATUS="FAIL"
  finish_gate "$EXIT_BLOCKED_INFRA" "BLOCKED_INFRA"
  exit $?
fi
YUJIAN_PREFLIGHT_STATUS="PASS"

install_started_epoch="$(date +%s)"
android_runtime_install_apks "$YUJIAN_APP_APK" "$YUJIAN_TEST_APK"
install_rc=$?
printf 'apk_install_seconds=%s\n' "$(( $(date +%s) - install_started_epoch ))" \
  | tee -a "$YUJIAN_EVIDENCE_DIR/infra/runtime_timings.log"
if (( install_rc != EXIT_PASS )); then
  YUJIAN_INSTALL_STATUS="FAIL"
  if (( install_rc == EXIT_FAIL_ARTIFACT )); then
    finish_gate "$EXIT_FAIL_ARTIFACT" "FAIL_ARTIFACT"
  else
    finish_gate "$EXIT_BLOCKED_INFRA" "BLOCKED_INFRA"
  fi
  exit $?
fi
YUJIAN_INSTALL_STATUS="PASS"

gate_before_instrumentation
android_runtime_run_instrumentation "$gate_test_classes"
instrumentation_rc=$?
gate_after_instrumentation || true
if (( instrumentation_rc != EXIT_PASS )); then
  YUJIAN_INSTRUMENTATION_STATUS="FAIL"
  if (( instrumentation_rc == EXIT_BLOCKED_INFRA )); then
    finish_gate "$EXIT_BLOCKED_INFRA" "BLOCKED_INFRA"
  else
    gate_instrumentation_failure_classification
    failure_classification_rc=$?
    if (( failure_classification_rc == EXIT_BLOCKED_INFRA )); then
      finish_gate "$EXIT_BLOCKED_INFRA" "BLOCKED_INFRA"
    else
      finish_gate "$EXIT_FAIL_TEST" "FAIL_TEST"
    fi
  fi
  exit $?
fi
YUJIAN_INSTRUMENTATION_STATUS="PASS"

gate_collect_evidence
evidence_rc=$?
if (( evidence_rc != EXIT_PASS )); then
  YUJIAN_EVIDENCE_STATUS="FAIL"
  if (( evidence_rc == EXIT_BLOCKED_INFRA )); then
    finish_gate "$EXIT_BLOCKED_INFRA" "BLOCKED_INFRA"
  else
    finish_gate "$EXIT_FAIL_EVIDENCE" "FAIL_EVIDENCE"
  fi
  exit $?
fi
YUJIAN_EVIDENCE_STATUS="PASS"
finish_gate "$EXIT_PASS" "PASS"
exit $?
