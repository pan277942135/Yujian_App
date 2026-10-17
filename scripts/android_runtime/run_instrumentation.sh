#!/usr/bin/env bash

android_runtime_run_instrumentation() {
  local test_classes="$1"
  local append_log="${2:-0}"
  local timeout_override="${3:-}"
  local log="$YUJIAN_EVIDENCE_DIR/instrumentation.log"
  mkdir -p "$YUJIAN_EVIDENCE_DIR"
  if [[ "$append_log" != "1" ]]; then
    : > "$log"
  fi
  {
    printf 'TEST_CLASSES=%s\n' "$test_classes"
    printf 'COMMAND=adb shell am instrument -w -r -e class %s %s\n' "$test_classes" "$YUJIAN_INSTRUMENTATION_TARGET"
  } >> "$log"

  # Keep the default bounded while allowing a gate with a larger, explicitly
  # measured suite to declare its own budget. Direct instrumentation still
  # runs exactly once; a failing assertion is never hidden by a rerun.
  local timeout_seconds=180
  if [[ -n "$timeout_override" ]]; then
    timeout_seconds="$timeout_override"
  elif declare -F gate_instrumentation_timeout_seconds >/dev/null 2>&1; then
    timeout_seconds="$(gate_instrumentation_timeout_seconds)"
  fi
  if [[ ! "$timeout_seconds" =~ ^[1-9][0-9]*$ ]]; then
    runtime_set_failure "CONFIG" "INSTRUMENTATION_TIMEOUT_INVALID"
    return "$EXIT_FAIL_ARTIFACT"
  fi
  printf 'TIMEOUT_SECONDS=%s\n' "$timeout_seconds" >> "$log"
  timeout "${timeout_seconds}s" "${YUJIAN_ADB_BIN}" shell am instrument \
    -w \
    -r \
    -e class "$test_classes" \
    "$YUJIAN_INSTRUMENTATION_TARGET" >> "$log" 2>&1
  local runner_rc=$?
  if (( runner_rc == 124 )); then
    printf 'INSTRUMENTATION_TIMEOUT=%ss\n' "$timeout_seconds" >> "$log"
    local state
    state="$(runtime_adb_state)"
    if [[ "$state" == "device" ]]; then
      runtime_set_failure "INSTRUMENTATION" "INSTRUMENTATION_TIMEOUT"
      return "$EXIT_FAIL_TEST"
    fi
    runtime_set_failure "INSTRUMENTATION" "ADB_TRANSPORT_DURING_INSTRUMENTATION_TIMEOUT"
    return "$EXIT_BLOCKED_INFRA"
  fi

  if runtime_is_transport_failure_file "$log"; then
    runtime_set_failure "INSTRUMENTATION" "ADB_TRANSPORT_DURING_INSTRUMENTATION"
    return "$EXIT_BLOCKED_INFRA"
  fi
  # AndroidJUnitRunner on API 28 may emit INSTRUMENTATION_CODE: -1 even
  # after a clean "OK (N tests)" completion. Classify from explicit failure
  # markers first, then require a positive completion marker below.
  if grep -Eiq 'INSTRUMENTATION_FAILED|FAILURES!!!|Process (crashed|has died)|Process .* (crashed|has died)|Assertion(Error|FailedError)|There were test failures|test failure' "$log"; then
    runtime_set_failure "INSTRUMENTATION" "TEST_ASSERTION_FAILED"
    return "$EXIT_FAIL_TEST"
  fi
  if (( runner_rc != 0 )); then
    runtime_set_failure "INSTRUMENTATION" "TEST_RUNNER_FAILED"
    return "$EXIT_FAIL_TEST"
  fi
  if ! grep -Eq 'INSTRUMENTATION_CODE:[[:space:]]*0|OK \([0-9]+ test' "$log"; then
    runtime_set_failure "INSTRUMENTATION" "INSTRUMENTATION_NO_COMPLETION"
    return "$EXIT_BLOCKED_INFRA"
  fi

  runtime_log "Instrumentation PASS classes=$test_classes"
  return "$EXIT_PASS"
}
