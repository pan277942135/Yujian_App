#!/usr/bin/env bash

gate_test_classes() {
  printf '%s\n' 'com.yujian.ai.PresentationSanitizationRuntimeTest'
}
gate_collect_evidence() {
  local output_dir="$YUJIAN_EVIDENCE_DIR/ui_rework_v1/data_sanitization"
  local remote_dir="/sdcard/Android/data/${YUJIAN_APP_PACKAGE}/files/evidence/ui_rework_v1/data_sanitization"
  mkdir -p "$output_dir"
  "${YUJIAN_ADB_BIN}" pull "${remote_dir}/." "$output_dir/" >/dev/null 2>&1 || true

  local missing=0 name
  for name in \
    01_normal_home_null_clean.png \
    02_my_catches_null_clean.png \
    03_fish_record_detail_null_clean.png \
    04_valid_location_regression.png
  do
    if [[ ! -s "$output_dir/$name" ]]; then
      printf 'MISSING_EVIDENCE=%s\n' "$name" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
      missing=1
    fi
  done
  if (( missing != 0 )); then
    runtime_set_failure "EVIDENCE" "DATA_SANITIZATION_EVIDENCE_MISSING"
    return "$EXIT_FAIL_EVIDENCE"
  fi
  return "$EXIT_PASS"
}
