#!/usr/bin/env bash

gate_test_classes() {
  printf '%s\n' 'com.yujian.ai.RecognitionImageStoreTest'
}

gate_collect_evidence() {
  local log="$YUJIAN_EVIDENCE_DIR/instrumentation.log"
  local output="$YUJIAN_EVIDENCE_DIR/camera_runtime_smoke"
  mkdir -p "$output"
  if [[ ! -s "$log" ]] || ! grep -Eq 'INSTRUMENTATION_CODE:[[:space:]]*0|OK \([0-9]+ test' "$log"; then
    runtime_set_failure "EVIDENCE" "RUNTIME_SMOKE_INSTRUMENTATION_LOG_MISSING_OR_INCOMPLETE"
    return "$EXIT_FAIL_EVIDENCE"
  fi
  cp "$log" "$output/instrumentation.log"
  return "$EXIT_PASS"
}
