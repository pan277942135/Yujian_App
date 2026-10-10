#!/usr/bin/env bash

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNTIME_DIR="$SCRIPT_DIR"
export EVIDENCE_DIR="${EVIDENCE_DIR:-evidence/runtime/normal-home-brand-title-v1}"
export API_LEVEL="${API_LEVEL:-28}"
export BUILD_SHA="${BUILD_SHA:-${GITHUB_SHA:-unknown}}"
export NORMAL_HOME_EVIDENCE_SEED_METHOD="seedMultipleGuestCatches"
mkdir -p "$EVIDENCE_DIR/infra"

source "$RUNTIME_DIR/common.sh"
source "$RUNTIME_DIR/result.sh"
source "$RUNTIME_DIR/preflight.sh"
source "$RUNTIME_DIR/install_apks.sh"
source "$RUNTIME_DIR/diagnostics.sh"
source "$RUNTIME_DIR/gates/normal_home_v1.sh"

YUJIAN_GATE="normal-home-brand-title-validation-v1"
YUJIAN_APP_APK="${APP_APK:-build/runtime-apks/app-debug.apk}"
YUJIAN_TEST_APK="${TEST_APK:-build/runtime-apks/app-debug-androidTest.apk}"
YUJIAN_PREFLIGHT_STATUS="NOT_RUN"
YUJIAN_INSTALL_STATUS="NOT_RUN"
YUJIAN_INSTRUMENTATION_STATUS="NOT_RUN"
YUJIAN_EVIDENCE_STATUS="NOT_RUN"
YUJIAN_CLASSIFICATION="BLOCKED_INFRA"

TARGET_LOG="$EVIDENCE_DIR/targeted-instrumentation.log"
SUITE_LOG="$EVIDENCE_DIR/normal-home-suite-instrumentation.log"
METRICS="$EVIDENCE_DIR/device_metrics.json"

sha256_file() {
  sha256sum "$1" | awk '{print $1}'
}

write_result() {
  local target_rc="$1" suite_rc="$2" evidence_rc="$3" status="$4"
  python3 - "$EVIDENCE_DIR/normal_home_brand_title_result.json" "$BUILD_SHA" \
    "$target_rc" "$suite_rc" "$evidence_rc" "$status" <<'PY'
import json, pathlib, sys
path, build_sha, target_rc, suite_rc, evidence_rc, status = sys.argv[1:]
pathlib.Path(path).write_text(json.dumps({
    "build_sha": build_sha,
    "targeted_test": "com.yujian.ai.ui.home.NormalHomeDataParityTest#differentPortraitRatiosShareCoverViewportAndPreserveHomeDataAndRoutes",
    "targeted_exit_code": int(target_rc),
    "normal_home_suite_exit_code": int(suite_rc),
    "visual_evidence_exit_code": int(evidence_rc),
    "status": status,
}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY
}

if [[ ! -s "$YUJIAN_APP_APK" || ! -s "$YUJIAN_TEST_APK" ]]; then
  printf 'APK_MISSING app=%s test=%s\n' "$YUJIAN_APP_APK" "$YUJIAN_TEST_APK" > "$EVIDENCE_DIR/infra/preflight.log"
  write_result 10 10 10 BLOCKED_INFRA
  exit 10
fi

{
  printf 'BUILD_SHA=%s\nAPP_APK=%s\nAPP_APK_SHA256=%s\nAPP_APK_BYTES=%s\n' \
    "$BUILD_SHA" "$YUJIAN_APP_APK" "$(sha256_file "$YUJIAN_APP_APK")" "$(stat -c '%s' "$YUJIAN_APP_APK")"
  printf 'TEST_APK=%s\nTEST_APK_SHA256=%s\nTEST_APK_BYTES=%s\n' \
    "$YUJIAN_TEST_APK" "$(sha256_file "$YUJIAN_TEST_APK")" "$(stat -c '%s' "$YUJIAN_TEST_APK")"
} > "$EVIDENCE_DIR/apk_provenance.txt"
cat "$EVIDENCE_DIR/apk_provenance.txt"

android_runtime_preflight
preflight_rc=$?
if (( preflight_rc != EXIT_PASS )); then
  YUJIAN_PREFLIGHT_STATUS="FAIL"
  runtime_adb_state > "$EVIDENCE_DIR/infra/adb_state.txt" 2>&1 || true
  android_runtime_write_result
  write_result 10 10 10 BLOCKED_INFRA
  exit 10
fi
YUJIAN_PREFLIGHT_STATUS="PASS"
android_runtime_install_apks "$YUJIAN_APP_APK" "$YUJIAN_TEST_APK"
install_rc=$?
if (( install_rc != EXIT_PASS )); then
  YUJIAN_INSTALL_STATUS="FAIL"
  android_runtime_write_result
  write_result 10 10 10 BLOCKED_INFRA
  exit 10
fi
YUJIAN_INSTALL_STATUS="PASS"

adb_bin="$YUJIAN_ADB_BIN"
original_size="$("$adb_bin" shell wm size 2>&1 | tr -d '\r' | paste -sd ';' -)"
original_override="$("$adb_bin" shell wm size 2>&1 | awk -F': ' '/Override size:/ {print $2}' | tr -d '\r')"
restore_device_size() {
  if [[ -n "$original_override" ]]; then
    "$adb_bin" shell wm size "$original_override" >/dev/null 2>&1 || true
  else
    "$adb_bin" shell wm size reset >/dev/null 2>&1 || true
  fi
}
trap restore_device_size EXIT

"$adb_bin" shell wm size 1080x1920 > "$EVIDENCE_DIR/infra/wm_size_setup.log" 2>&1
device_serial="$("$adb_bin" get-serialno 2>&1 | tr -d '\r')"
device_api="$("$adb_bin" shell getprop ro.build.version.sdk 2>&1 | tr -d '\r')"
wm_size="$("$adb_bin" shell wm size 2>&1 | tr -d '\r' | paste -sd ';' -)"
wm_density="$("$adb_bin" shell wm density 2>&1 | tr -d '\r' | paste -sd ';' -)"
font_scale="$("$adb_bin" shell settings get system font_scale 2>&1 | tr -d '\r')"
python3 - "$METRICS" "$device_serial" "$device_api" "$wm_size" "$wm_density" "$font_scale" "$original_size" "$BUILD_SHA" <<'PY'
import json, pathlib, sys
path, serial, api, size, density, font_scale, original_size, build_sha = sys.argv[1:]
pathlib.Path(path).write_text(json.dumps({
    "build_sha": build_sha,
    "device_serial": serial,
    "api_level": api,
    "wm_size": size,
    "wm_density": density,
    "font_scale": font_scale,
    "original_wm_size": original_size,
}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY
cat "$METRICS"
python3 - "$YUJIAN_REPO_ROOT/app/src/main/assets/home_normal/fish_record/sample_recent_catch.jpg" \
  "$EVIDENCE_DIR/photo_provenance.json" <<'PY'
import hashlib, json, pathlib, sys
from PIL import Image
source = pathlib.Path(sys.argv[1])
with Image.open(source) as image:
    dimensions = list(image.size)
pathlib.Path(sys.argv[2]).write_text(json.dumps({
    "source_path": source.as_posix(),
    "sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
    "bytes": source.stat().st_size,
    "dimensions": dimensions,
}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY

if [[ "$device_api" != "$API_LEVEL" ]]; then
  printf 'API_MISMATCH expected=%s actual=%s\n' "$API_LEVEL" "$device_api" > "$EVIDENCE_DIR/infra/device_mismatch.log"
  runtime_set_failure "PREFLIGHT" "API_LEVEL_MISMATCH"
  YUJIAN_PREFLIGHT_STATUS="FAIL"
  YUJIAN_CLASSIFICATION="BLOCKED_INFRA"
  android_runtime_write_result
  write_result 10 10 10 BLOCKED_INFRA
  exit 10
fi

normal_home_out="$EVIDENCE_DIR/normal-home-v1"
mkdir -p "$normal_home_out"
seed_rc=0
normal_home_run_seed seedMultipleGuestCatches "$EVIDENCE_DIR/seed-real-photo-home.log" || seed_rc=$?
launch_rc=0
normal_home_launch_app > "$EVIDENCE_DIR/infra/app_launch.log" 2>&1 || launch_rc=$?
normal_home_capture_exact "$normal_home_out/current_head_normal_home.png" 1080 1920 \
  > "$EVIDENCE_DIR/infra/current_head_capture.log" 2>&1 || true
python3 - "$normal_home_out/current_head_normal_home.png" "$EVIDENCE_DIR/runtime_screenshot.json" <<'PY'
import hashlib, json, pathlib, sys
from PIL import Image
image_path, output = map(pathlib.Path, sys.argv[1:])
payload = {"path": image_path.as_posix(), "sha256": hashlib.sha256(image_path.read_bytes()).hexdigest(), "bytes": image_path.stat().st_size}
with Image.open(image_path) as image:
    payload["dimensions"] = list(image.size)
pathlib.Path(output).write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
PY
cat "$EVIDENCE_DIR/runtime_screenshot.json"
if (( seed_rc != 0 || launch_rc != 0 )); then
  printf 'SEED_EXIT_CODE=%s\nAPP_LAUNCH_EXIT_CODE=%s\n' "$seed_rc" "$launch_rc" > "$EVIDENCE_DIR/infra/screenshot_setup_failure.log"
fi

target_class='com.yujian.ai.ui.home.NormalHomeDataParityTest#differentPortraitRatiosShareCoverViewportAndPreserveHomeDataAndRoutes'
timeout 180s "$adb_bin" shell am instrument -w -r -e class "$target_class" \
  "$YUJIAN_INSTRUMENTATION_TARGET" > "$TARGET_LOG" 2>&1
target_rc=$?
if (( target_rc == 0 )); then YUJIAN_INSTRUMENTATION_STATUS="PASS"; else YUJIAN_INSTRUMENTATION_STATUS="FAIL"; fi
grep -E "^NORMAL_HOME_(TEXT_LAYOUT|WINDOW_METRICS)" "$TARGET_LOG" || true
if (( target_rc != 0 )); then
  printf 'NORMAL_HOME_TARGET_INSTRUMENTATION_LOG_BEGIN\n'
  tail -n 120 "$TARGET_LOG"
  printf 'NORMAL_HOME_TARGET_INSTRUMENTATION_LOG_END\n'
fi

suite_classes="$(gate_test_classes)"
timeout 240s "$adb_bin" shell am instrument -w -r -e class "$suite_classes" \
  "$YUJIAN_INSTRUMENTATION_TARGET" > "$SUITE_LOG" 2>&1
suite_rc=$?
if (( suite_rc != 0 )); then
  printf 'NORMAL_HOME_SUITE_INSTRUMENTATION_LOG_BEGIN\n'
  tail -n 120 "$SUITE_LOG"
  printf 'NORMAL_HOME_SUITE_INSTRUMENTATION_LOG_END\n'
fi

gate_collect_evidence > "$EVIDENCE_DIR/normal-home-evidence-collection.log" 2>&1
evidence_rc=$?
if (( evidence_rc == 0 )); then YUJIAN_EVIDENCE_STATUS="PASS"; else YUJIAN_EVIDENCE_STATUS="FAIL"; fi

if (( target_rc != 0 || suite_rc != 0 || evidence_rc != 0 )); then
  if (( target_rc != 0 || suite_rc != 0 )); then
    YUJIAN_CLASSIFICATION="FAIL_TEST"
    YUJIAN_FAILURE_PHASE="INSTRUMENTATION"
    YUJIAN_FAILURE_REASON="TEST_ASSERTION_FAILED"
    YUJIAN_INSTRUMENTATION_STATUS="FAIL"
  else
    YUJIAN_CLASSIFICATION="FAIL_EVIDENCE"
    YUJIAN_FAILURE_PHASE="EVIDENCE"
    YUJIAN_FAILURE_REASON="NORMAL_HOME_VISUAL_EVIDENCE_FAILED"
    YUJIAN_EVIDENCE_STATUS="FAIL"
  fi
  if (( target_rc == 10 || suite_rc == 10 || evidence_rc == 10 )); then YUJIAN_CLASSIFICATION="BLOCKED_INFRA"; fi
  android_runtime_collect_diagnostics || true
  cp "$YUJIAN_INFRA_DIR/logcat.txt" "$EVIDENCE_DIR/infra/logcat.txt" 2>/dev/null || true
else
  YUJIAN_CLASSIFICATION="PASS"
fi

status=PASS
if (( target_rc != 0 || suite_rc != 0 || evidence_rc != 0 )); then
  status=FAIL
fi
write_result "$target_rc" "$suite_rc" "$evidence_rc" "$status"
android_runtime_write_result
cat "$EVIDENCE_DIR/normal_home_brand_title_result.json"

python3 - "$EVIDENCE_DIR" <<'PY'
import hashlib, pathlib, sys
root = pathlib.Path(sys.argv[1])
out = root / "SHA256SUMS"
rows = []
for path in sorted(p for p in root.rglob("*") if p.is_file() and p != out):
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    rows.append(f"{digest}  {path.relative_to(root).as_posix()}")
out.write_text("\n".join(rows) + "\n", encoding="utf-8")
PY

if [[ "$status" != PASS ]]; then
  exit 30
fi
