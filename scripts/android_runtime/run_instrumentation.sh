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
  "${YUJIAN_ADB_BIN}" shell am instrument \
    -w \
    -r \
    -e class "$test_classes" \
    "$YUJIAN_INSTRUMENTATION_TARGET" >> "$log" 2>&1
  local runner_rc=$?

  if runtime_is_transport_failure_file "$log"; then
    runtime_set_failure "INSTRUMENTATION" "ADB_TRANSPORT_DURING_INSTRUMENTATION"
    return "$EXIT_BLOCKED_INFRA"
  fi
  if grep -Eiq 'INSTRUMENTATION_FAILED|FAILURES!!!|Process .* (crashed|has died)|Assertion(Error|FailedError)|There were test failures|INSTRUMENTATION_CODE: -1|test failure' "$log"; then
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
