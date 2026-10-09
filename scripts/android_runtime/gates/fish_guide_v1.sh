#!/usr/bin/env bash

gate_test_classes() {
  printf '%s\n' 'com.yujian.ai.FishGuideRuntimeTest,com.yujian.ai.FishKnowledgeContractTest'
}

gate_collect_evidence() {
  local out="$YUJIAN_EVIDENCE_DIR/fish-guide-v1"
  mkdir -p "$out"
  local remote="/sdcard/Android/data/$YUJIAN_APP_PACKAGE/files/fish_guide_v1"

  "$YUJIAN_ADB_BIN" shell ls -la "$remote" > "$out/device_files.txt" 2>&1 || {
    runtime_set_failure "EVIDENCE" "FISH_GUIDE_DEVICE_EVIDENCE_DIRECTORY_MISSING"
    return "$EXIT_FAIL_EVIDENCE"
  }
  "$YUJIAN_ADB_BIN" pull "$remote/." "$out/" > "$out/adb_pull.log" 2>&1 || {
    runtime_set_failure "EVIDENCE" "FISH_GUIDE_SCREENSHOT_PULL_FAILED"
    return "$EXIT_FAIL_EVIDENCE"
  }

  local missing=0 file
  for file in \
    fish_guide_lit.png \
    fish_guide_lit_app_surface.png \
    fish_guide_lit_metadata.json \
    fish_guide_unlit.png \
    fish_guide_unlit_app_surface.png \
    fish_guide_unlit_metadata.json \
    fish_guide_error.png \
    fish_species_detail.png \
    fish_species_detail_catch.png \
    fish_species_detail_zero_catch.png
  do
    if [[ ! -s "$out/$file" ]]; then
      printf 'MISSING_EVIDENCE=%s\n' "$out/$file" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
      missing=1
    fi
  done
  if (( missing != 0 )); then
    runtime_set_failure "EVIDENCE" "FISH_GUIDE_EVIDENCE_MISSING"
    return "$EXIT_FAIL_EVIDENCE"
  fi

  python3 "$YUJIAN_REPO_ROOT/scripts/android_runtime/gates/fish_guide_visual_gate.py" \
    --reference-dir "$YUJIAN_REPO_ROOT/design/system/core_visual_v1/reference" \
    --evidence-dir "$out"
  local rc=$?
  if (( rc != 0 )); then
    if (( rc == 2 )); then
      runtime_set_failure "EVIDENCE" "BLOCKED_MISSING_PUBLISHED_COVER_HERO"
    else
      runtime_set_failure "EVIDENCE" "FISH_GUIDE_VISUAL_PARITY_FAILED"
    fi
    return "$EXIT_FAIL_EVIDENCE"
  fi
  return "$EXIT_PASS"
}
