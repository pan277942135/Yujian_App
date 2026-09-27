#!/usr/bin/env bash

gate_test_classes() {
  printf '%s\n' 'com.yujian.ai.RecognitionFrozenFlowEmulatorTest'
}
gate_collect_evidence() {
  local output_dir="$YUJIAN_EVIDENCE_DIR/ui_rework_v1/recognition"
  mkdir -p "$output_dir"

  local name
  for name in \
    01_capture_transition.png \
    02_ai_understanding.png \
    03_fish_highlight.png \
    04_fish_identifying.png \
    05_result_high.png \
    06_result_medium.png \
    07_result_low.png \
    08_error_no_fish.png \
    09_error_image_quality.png
  do
    "${YUJIAN_ADB_BIN}" exec-out run-as "$YUJIAN_APP_PACKAGE" cat "cache/recognition-evidence/${name}" \
      > "$output_dir/$name" 2>/dev/null || true
    if [[ ! -s "$output_dir/$name" ]]; then
      rm -f "$output_dir/$name"
    fi
  done

  "${YUJIAN_ADB_BIN}" exec-out run-as "$YUJIAN_APP_PACKAGE" cat \
    "cache/recognition-evidence/recognition_processing_timing.txt" \
    > "$output_dir/recognition_processing_timing.txt" 2>/dev/null || true
  if [[ ! -s "$output_dir/recognition_processing_timing.txt" ]]; then
    rm -f "$output_dir/recognition_processing_timing.txt"
  fi

  "${YUJIAN_ADB_BIN}" pull /sdcard/recognition_processing_v1_1.mp4 \
    "$output_dir/recognition_processing_v1_1.mp4" >/dev/null 2>&1 || true
  if [[ ! -s "$output_dir/recognition_processing_v1_1.mp4" ]]; then
    rm -f "$output_dir/recognition_processing_v1_1.mp4"
  fi

  local missing=0
  for name in \
    01_capture_transition.png \
    02_ai_understanding.png \
    03_fish_highlight.png \
    04_fish_identifying.png \
    05_result_high.png \
    06_result_medium.png \
    07_result_low.png \
    08_error_no_fish.png \
    09_error_image_quality.png \
    recognition_processing_timing.txt \
    recognition_processing_v1_1.mp4
  do
    if [[ ! -s "$output_dir/$name" ]]; then
      printf 'MISSING_EVIDENCE=%s\n' "$name" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
      missing=1
    fi
  done

  if (( missing != 0 )); then
    runtime_set_failure "EVIDENCE" "RECOGNITION_EVIDENCE_MISSING"
    return "$EXIT_FAIL_EVIDENCE"
  fi
  return "$EXIT_PASS"
}
