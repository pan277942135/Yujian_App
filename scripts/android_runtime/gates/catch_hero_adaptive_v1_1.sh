#!/usr/bin/env bash

gate_test_classes() {
  printf '%s\n' 'com.yujian.ai.ui.components.CatchHeroAdaptiveRuntimeMatrixTest'
}

catch_hero_profile_capture_size() {
  local output="$1"
  "$YUJIAN_ADB_BIN" exec-out screencap -p > "$output"
  python3 - "$output" <<'PY'
from PIL import Image
import sys

with Image.open(sys.argv[1]) as image:
    print(f"{image.width}x{image.height}")
PY
}

gate_before_instrumentation() {
  local out="$YUJIAN_EVIDENCE_DIR/catch_hero_adaptive_v1_1"
  local probe="$YUJIAN_INFRA_DIR/catch_hero_preflight_1080x1920.png"
  mkdir -p "$out" "$YUJIAN_INFRA_DIR"
  "$YUJIAN_ADB_BIN" shell wm size 1080x1920 >/dev/null
  local actual
  actual="$(catch_hero_profile_capture_size "$probe")"
  printf 'CATCH_HERO_PROFILE requested=1080x1920 actual=%s\n' "$actual"
  if [[ "$actual" != "1080x1920" ]]; then
    python3 - "$out/profile_1080x1920.json" "$actual" <<'PY'
import json, sys
from pathlib import Path
Path(sys.argv[1]).write_text(json.dumps({
    "requested_profile": "1080x1920",
    "actual_profile": sys.argv[2],
    "status": "BLOCKED_INFRA",
    "reason": "DEVICE_CANNOT_CAPTURE_1080X1920",
}, indent=2) + "\n")
PY
    runtime_set_failure "PREFLIGHT" "DEVICE_CANNOT_CAPTURE_1080X1920"
    return "$EXIT_BLOCKED_INFRA"
  fi
  "$YUJIAN_ADB_BIN" shell rm -rf \
    "/sdcard/Android/data/$YUJIAN_APP_PACKAGE/files/catch_hero_adaptive_v1_1/matrix_1080x1920" \
    "/sdcard/Android/data/$YUJIAN_APP_PACKAGE/files/catch_hero_adaptive_v1_1/matrix_1080x2340"
  return "$EXIT_PASS"
}

catch_hero_verify_profile() {
  local profile="$1"
  local device_dir="/sdcard/Android/data/$YUJIAN_APP_PACKAGE/files/catch_hero_adaptive_v1_1/matrix_$profile"
  local local_dir="$YUJIAN_EVIDENCE_DIR/catch_hero_adaptive_v1_1/device_output_$profile"
  rm -rf "$local_dir"
  mkdir -p "$(dirname "$local_dir")"
  "$YUJIAN_ADB_BIN" pull "$device_dir" "$local_dir" >/dev/null || return 1
  local manifest
  manifest="$(find "$local_dir" -name android_runtime_manifest.json -print -quit)"
  [[ -n "$manifest" ]] || return 1
  python3 "$YUJIAN_REPO_ROOT/scripts/verify_catch_hero_adaptive_android_evidence.py" \
    --matrix-dir "$(dirname "$manifest")" \
    --git-head "$YUJIAN_BUILD_SHA" \
    --adb "$YUJIAN_ADB_BIN" \
    --expected-size "$profile" || return 1
  if [[ "$profile" == "1080x1920" ]]; then
    cp "$YUJIAN_EVIDENCE_DIR/instrumentation.log" "$(dirname "$manifest")/androidtest_1080x1920.log"
  fi
  return 0
}

gate_collect_evidence() {
  local out="$YUJIAN_EVIDENCE_DIR/catch_hero_adaptive_v1_1"
  local profile_log="$out/androidtest_1080x2340.log"
  local profile_probe="$YUJIAN_INFRA_DIR/catch_hero_preflight_1080x2340.png"
  local actual
  mkdir -p "$out"

  cp -R "$YUJIAN_REPO_ROOT/app/src/androidTest/assets/catch_hero_adaptive_v1_1" "$out/test_assets"
  cp "$YUJIAN_EVIDENCE_DIR/apk_metadata.json" "$out/apk_metadata.json"
  cp "$YUJIAN_EVIDENCE_DIR/SHA256SUMS" "$out/SHA256SUMS"

  catch_hero_verify_profile "1080x1920" || {
    runtime_set_failure "EVIDENCE" "CATCH_HERO_MATRIX_1080X1920_INVALID"
    return "$EXIT_FAIL_EVIDENCE"
  }
  python3 - "$out/profile_1080x1920.json" <<'PY'
import json, sys
from pathlib import Path
Path(sys.argv[1]).write_text(json.dumps({
    "requested_profile": "1080x1920",
    "actual_profile": "1080x1920",
    "status": "PASS",
    "instrumentation_log": "device_output_1080x1920/matrix_1080x1920/androidtest_1080x1920.log",
}, indent=2) + "\n")
PY

  "$YUJIAN_ADB_BIN" shell wm size 1080x2340 >/dev/null
  actual="$(catch_hero_profile_capture_size "$profile_probe")"
  printf 'CATCH_HERO_PROFILE requested=1080x2340 actual=%s\n' "$actual"
  if [[ "$actual" != "1080x2340" ]]; then
    python3 - "$out/profile_1080x2340.json" "$actual" <<'PY'
import json, sys
from pathlib import Path
Path(sys.argv[1]).write_text(json.dumps({
    "requested_profile": "1080x2340",
    "actual_profile": sys.argv[2],
    "status": "BLOCKED_INFRA",
    "reason": "DEVICE_CANNOT_CAPTURE_1080X2340",
}, indent=2) + "\n")
PY
    "$YUJIAN_ADB_BIN" shell wm size reset >/dev/null || true
    runtime_set_failure "PREFLIGHT" "DEVICE_CANNOT_CAPTURE_1080X2340"
    return "$EXIT_BLOCKED_INFRA"
  fi

  timeout 180s "$YUJIAN_ADB_BIN" shell am instrument -w -r \
    -e catch_hero_profile 1080x2340 \
    -e class com.yujian.ai.ui.components.CatchHeroAdaptiveRuntimeMatrixTest \
    "$YUJIAN_INSTRUMENTATION_TARGET" > "$profile_log" 2>&1
  local runner_rc=$?
  if (( runner_rc != 0 )) || grep -Eiq 'FAILURES!!!|INSTRUMENTATION_FAILED|Assertion(Error|FailedError)|Process (crashed|has died)' "$profile_log"; then
    cat "$profile_log" >&2
    python3 - "$out/profile_1080x2340.json" "$runner_rc" <<'PY'
import json, sys
from pathlib import Path
Path(sys.argv[1]).write_text(json.dumps({
    "requested_profile": "1080x2340",
    "actual_profile": "1080x2340",
    "status": "FAIL_TEST",
    "runner_exit_code": int(sys.argv[2]),
    "instrumentation_log": "androidtest_1080x2340.log",
}, indent=2) + "\n")
PY
    "$YUJIAN_ADB_BIN" shell wm size reset >/dev/null || true
    runtime_set_failure "INSTRUMENTATION" "CATCH_HERO_ADAPTIVE_1080X2340_TEST_FAILED"
    return "$EXIT_FAIL_TEST"
  fi
  if ! grep -Eq 'INSTRUMENTATION_CODE:[[:space:]]*0|OK \([0-9]+ test' "$profile_log"; then
    python3 - "$out/profile_1080x2340.json" <<'PY'
import json, sys
from pathlib import Path
Path(sys.argv[1]).write_text(json.dumps({
    "requested_profile": "1080x2340",
    "actual_profile": "1080x2340",
    "status": "BLOCKED_INFRA",
    "reason": "CATCH_HERO_ADAPTIVE_1080X2340_NO_COMPLETION",
    "instrumentation_log": "androidtest_1080x2340.log",
}, indent=2) + "\n")
PY
    "$YUJIAN_ADB_BIN" shell wm size reset >/dev/null || true
    runtime_set_failure "INSTRUMENTATION" "CATCH_HERO_ADAPTIVE_1080X2340_NO_COMPLETION"
    return "$EXIT_BLOCKED_INFRA"
  fi
  catch_hero_verify_profile "1080x2340" || {
    "$YUJIAN_ADB_BIN" shell wm size reset >/dev/null || true
    runtime_set_failure "EVIDENCE" "CATCH_HERO_MATRIX_1080X2340_INVALID"
    return "$EXIT_FAIL_EVIDENCE"
  }
  local profile_manifest
  profile_manifest="$(find "$out/device_output_1080x2340" -name android_runtime_manifest.json -print -quit)"
  cp "$profile_log" "$(dirname "$profile_manifest")/androidtest_1080x2340.log"
  "$YUJIAN_ADB_BIN" shell wm size reset >/dev/null

  python3 - "$out/profile_1080x2340.json" <<'PY'
import json, sys
from pathlib import Path
Path(sys.argv[1]).write_text(json.dumps({
    "requested_profile": "1080x2340",
    "actual_profile": "1080x2340",
    "status": "PASS",
    "instrumentation_log": "androidtest_1080x2340.log",
}, indent=2) + "\n")
PY
  return "$EXIT_PASS"
}
