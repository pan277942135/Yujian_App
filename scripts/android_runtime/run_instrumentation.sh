#!/usr/bin/env bash

android_runtime_run_instrumentation() {
  local test_classes="$1"
  local log="$YUJIAN_EVIDENCE_DIR/instrumentation.log"
  mkdir -p "$YUJIAN_EVIDENCE_DIR"
  : > "$log"
  {
    printf 'TEST_CLASSES=%s\n' "$test_classes"
    printf 'COMMAND=adb shell am instrument -w -r -e class %s %s\n' "$test_classes" "$YUJIAN_INSTRUMENTATION_TARGET"
  } >> "$log"

  # Direct adb instrumentation is intentionally executed exactly once.  A
  # failing assertion must never be hidden by an automatic rerun.
  timeout 180s "${YUJIAN_ADB_BIN}" shell am instrument \
    -w \
    -r \
    -e class "$test_classes" \
    -e buildSha "$YUJIAN_BUILD_SHA" \
    "$YUJIAN_INSTRUMENTATION_TARGET" >> "$log" 2>&1
  local runner_rc=$?
  if (( runner_rc == 124 )); then
    printf 'INSTRUMENTATION_TIMEOUT=180s\n' >> "$log"
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
    printf 'ANDROID_RUNTIME_INSTRUMENTATION_LOG_BEGIN reason=TEST_ASSERTION_FAILED\\n' >&2
    cat "$log" >&2
    printf 'ANDROID_RUNTIME_INSTRUMENTATION_LOG_END\\n' >&2
    return "$EXIT_FAIL_TEST"
  fi
  if (( runner_rc != 0 )); then
    runtime_set_failure "INSTRUMENTATION" "TEST_RUNNER_FAILED"
    printf 'ANDROID_RUNTIME_INSTRUMENTATION_LOG_BEGIN reason=TEST_RUNNER_FAILED\\n' >&2
    cat "$log" >&2
    printf 'ANDROID_RUNTIME_INSTRUMENTATION_LOG_END\\n' >&2
    return "$EXIT_FAIL_TEST"
  fi
  if ! grep -Eq 'INSTRUMENTATION_CODE:[[:space:]]*0|OK \([0-9]+ test' "$log"; then
    runtime_set_failure "INSTRUMENTATION" "INSTRUMENTATION_NO_COMPLETION"
    return "$EXIT_BLOCKED_INFRA"
  fi

  runtime_log "Instrumentation PASS classes=$test_classes"
  return "$EXIT_PASS"
}
