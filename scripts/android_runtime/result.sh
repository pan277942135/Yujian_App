#!/usr/bin/env bash

android_runtime_write_result() {
  local summary_file="${GITHUB_STEP_SUMMARY:-}"
  python3 "${YUJIAN_RUNTIME_DIR}/result.py" \
    --output "$YUJIAN_RESULT_PATH" \
    --gate "$YUJIAN_GATE" \
    --api-level "$YUJIAN_API_LEVEL" \
    --build-sha "$YUJIAN_BUILD_SHA" \
    --adb-state "$YUJIAN_DEVICE_STATE" \
    --boot-completed "$YUJIAN_BOOT_COMPLETED" \
    --data-free-kb "${YUJIAN_DATA_FREE_KB:-}" \
    --preflight "$YUJIAN_PREFLIGHT_STATUS" \
    --install "$YUJIAN_INSTALL_STATUS" \
    --instrumentation "$YUJIAN_INSTRUMENTATION_STATUS" \
    --evidence "$YUJIAN_EVIDENCE_STATUS" \
    --classification "$YUJIAN_CLASSIFICATION" \
    --failure-phase "$YUJIAN_FAILURE_PHASE" \
    --failure-reason "$YUJIAN_FAILURE_REASON" \
    --summary-file "$summary_file"
}
